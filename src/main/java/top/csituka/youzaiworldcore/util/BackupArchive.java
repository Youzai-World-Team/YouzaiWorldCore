package top.csituka.youzaiworldcore.util;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * 备份压缩包读写工具。
 * <p>
 * 本项目约定：所有备份文件都是 {@code .zip} 压缩包，统一放在
 * {@code <gameDir>/yzwc/server/backup/<模块名>/} 或
 * {@code <world_name>/data/yzwc/backup/<模块名>/} 下。
 * 压缩包内部放一个同名的 {@code .json} 条目，便于人工解压查看。
 * </p>
 */
public final class BackupArchive {

    private BackupArchive() {
    }

    /**
     * 把一段 JSON 文本写成 zip 备份。
     *
     * @param zipFile   目标压缩包路径（含 {@code .zip} 后缀）
     * @param entryName 压缩包内的条目名，形如 {@code pet_backup_20260812_101500.json}
     * @param json      要写入的 JSON 文本
     * @throws IOException 写盘失败
     */
    public static void writeJson(Path zipFile, String entryName, String json) throws IOException {
        Path parent = zipFile.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            zos.putNextEntry(new ZipEntry(entryName));
            zos.write(json.getBytes(StandardCharsets.UTF_8));
            zos.closeEntry();
        }
    }

    /**
     * 把一个已存在的文件打包成 zip 备份。
     *
     * @param zipFile    目标压缩包路径（含 {@code .zip} 后缀）
     * @param sourceFile 要打包的源文件
     * @param entryName  压缩包内的条目名
     * @throws IOException 读写失败
     */
    public static void writeFile(Path zipFile, Path sourceFile, String entryName) throws IOException {
        writeJson(zipFile, entryName, Files.readString(sourceFile));
    }

    /**
     * 读取 zip 备份里的第一个 {@code .json} 条目。
     *
     * @param zipFile 压缩包路径
     * @return 该条目的文本内容；压缩包内没有 {@code .json} 条目时返回 null
     * @throws IOException 读盘失败
     */
    public static String readFirstJson(Path zipFile) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory() || !entry.getName().endsWith(".json")) {
                    zis.closeEntry();
                    continue;
                }
                String text = readAll(zis);
                zis.closeEntry();
                return text;
            }
        }
        return null;
    }

    /** 读完当前 zip 条目的全部内容（UTF-8）。 */
    private static String readAll(InputStream in) throws IOException {
        return new String(in.readAllBytes(), StandardCharsets.UTF_8);
    }
}
