// 编译并运行实际公告解析／缓存类，不构建模组、不启动 Minecraft 或访问网络。
// 用法：node tools/verify-announcement.mjs [JDK25目录] [Gson.jar]
import assert from 'node:assert/strict'
import { mkdtempSync, mkdirSync, readdirSync, rmSync, writeFileSync } from 'node:fs'
import { homedir, tmpdir } from 'node:os'
import { delimiter, dirname, join } from 'node:path'
import { execFileSync } from 'node:child_process'
import { fileURLToPath } from 'node:url'

const root = fileURLToPath(new URL('../', import.meta.url))
const javaHome = process.argv[2] || process.env.JAVA_HOME
const binary = (name) => javaHome ? join(javaHome, 'bin', name) : name
const gsonCache = join(homedir(), '.gradle/caches/modules-2/files-2.1/com.google.code.gson/gson')
const findGson = () => {
  for (const version of readdirSync(gsonCache).sort().reverse()) {
    for (const hash of readdirSync(join(gsonCache, version))) {
      const filename = `gson-${version}.jar`
      if (readdirSync(join(gsonCache, version, hash)).includes(filename)) return join(gsonCache, version, hash, filename)
    }
  }
  throw new Error('未找到 Gson，请通过第三个参数提供 Gson.jar')
}
const gson = process.argv[3] || findGson()
const directory = mkdtempSync(join(tmpdir(), 'yzwc-announcement-java-'))
const packagePath = 'top/csituka/youzaiworldcore'
const write = (file, content) => {
  const target = join(directory, file)
  mkdirSync(dirname(target), { recursive: true })
  writeFileSync(target, content)
  return target
}

try {
  const logger = write(`${packagePath}/util/DebugLogger.java`, `
package top.csituka.youzaiworldcore.util;
public final class DebugLogger {
    public static void info(String module, String message, Object... args) { }
    public static void warn(String module, String message, Object... args) { }
}
`)
  const harness = write(`${packagePath}/client/update/AnnouncementRegression.java`, `
package top.csituka.youzaiworldcore.client.update;
import com.google.gson.Gson;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.*;
import static top.csituka.youzaiworldcore.client.update.ClientAnnouncementState.*;

public final class AnnouncementRegression {
    private static final Gson GSON = new Gson();
    private static int checks;
    private static final Map<String, Object> CONTENT = new HashMap<>(Map.of(
        "enabled", true, "title", "维护公告", "subtitle", "今晚维护",
        "body", List.of("第一行", "", "  保留空格  ", "<b>纯文本</b>")));
    private static void check(boolean condition, String name) {
        if (!condition) throw new AssertionError(name);
        checks++;
    }
    private static Map<String, Object> with(String field, Object value) {
        var content = new HashMap<>(CONTENT);
        content.put(field, value);
        return content;
    }
    private static void rejected(Object value) {
        try { parse(GSON.toJson(value)); throw new AssertionError("应拒绝非法公告：" + value); }
        catch (IOException | RuntimeException expected) { checks++; }
    }
    private static Field field(String name) throws Exception {
        Field field = ClientAnnouncementState.class.getDeclaredField(name);
        field.setAccessible(true);
        return field;
    }
    public static void main(String[] args) throws Exception {
        Announcement announcement = parse(GSON.toJson(CONTENT));
        check(announcement.title().equals("维护公告"), "解析标题");
        check(announcement.subtitle().equals("今晚维护"), "解析副标题");
        check(announcement.body().equals(CONTENT.get("body")), "保留空行与空格");
        try { announcement.body().add("变更"); throw new AssertionError("快照可被修改"); }
        catch (UnsupportedOperationException expected) { checks++; }
        check(parse("{\\"enabled\\":false}") == null, "停用响应回退");
        check(parse("{\\"enabled\\":false,\\"body\\":123}") == null, "停用不消费草稿");
        rejected(null); rejected(List.of()); rejected(Map.of());
        rejected(with("enabled", "true")); rejected(with("title", 123));
        rejected(with("title", " ")); rejected(with("title", "字".repeat(81)));
        rejected(with("title", "标题\\n换行")); rejected(with("subtitle", null));
        rejected(with("subtitle", "字".repeat(161)));
        rejected(with("body", List.of())); rejected(with("body", List.of(" ", "")));
        rejected(with("body", List.of(123))); rejected(with("body", List.of("字".repeat(501))));
        rejected(with("body", List.of("换\\n行"))); rejected(with("body", List.of("控制" + (char) 0)));
        rejected(with("body", Collections.nCopies(121, "正文")));
        rejected(with("body", Collections.nCopies(40, "字".repeat(500))));
        var boundary = new ArrayList<>(Collections.nCopies(39, "字".repeat(500)));
        boundary.add("字".repeat(461));
        var maximum = with("body", boundary);
        maximum.put("title", "字".repeat(80)); maximum.put("subtitle", "字".repeat(160));
        check(parse(GSON.toJson(maximum)).body().size() == 40, "接受两万字边界");

        Method complete = ClientAnnouncementState.class.getDeclaredMethod("completeRefresh", Announcement.class, Throwable.class);
        complete.setAccessible(true);
        field("loading").setBoolean(null, true);
        complete.invoke(null, announcement, null);
        check(get() == announcement && !field("loading").getBoolean(null), "成功更新缓存并释放请求锁");
        field("loading").setBoolean(null, true);
        complete.invoke(null, null, new IOException("断网"));
        check(get() == announcement && !field("loading").getBoolean(null), "失败保留缓存并允许稍后重试");
        complete.invoke(null, null, null);
        check(get() == null, "明确停用清空线上缓存");
        complete.invoke(null, null, new IOException("断网"));
        check(get() == null, "首次失败继续使用内置公告");

        field("loading").setBoolean(null, true);
        field("attempted").setBoolean(null, false);
        refreshIfDue();
        check(!field("attempted").getBoolean(null), "有请求时窗口重建不启动重复请求");
        field("loading").setBoolean(null, false);
        field("attempted").setBoolean(null, true);
        field("lastAttemptNanos").setLong(null, System.nanoTime());
        refreshIfDue();
        check(!field("loading").getBoolean(null), "逐帧调用受一分钟节流限制");
        check(API_URL.equals("https://api.mcyzw.top/api/announcement/core"), "公开协议地址一致");
        System.out.println("通过 " + checks + " 项 Core 公告解析／缓存／刷新检查。");
    }
}
`)
  execFileSync(binary('javac'), ['-encoding', 'UTF-8', '--release', '25', '-cp', gson, '-d', directory,
    logger, harness, join(root, 'src/client/java', packagePath, 'client/update/ClientAnnouncementState.java')], { stdio: 'inherit' })
  const result = execFileSync(binary('java'), ['-cp', `${directory}${delimiter}${gson}`,
    'top.csituka.youzaiworldcore.client.update.AnnouncementRegression'], { encoding: 'utf8' })
  assert.match(result, /通过 \d+ 项/)
  process.stdout.write(result)
} finally {
  rmSync(directory, { recursive: true, force: true })
}
