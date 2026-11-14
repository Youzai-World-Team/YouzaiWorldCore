"""工作方块离线检查：核验真实 Minecraft 字节码的注入点、包装参数及屏幕接入。

只运行 javap 读取本地参考 JAR，不编译、不启动游戏、不访问网络。
运行示例：python tools/verify_yzui_workstations.py --java-home /path/to/jdk-25
"""

from pathlib import Path
import argparse
import json
import re
import shutil
import subprocess
import zipfile

ROOT = Path(__file__).resolve().parents[1]
CLIENT = ROOT / "src/client/java/top/csituka/youzaiworldcore"
SCREEN_PACKAGE = "net.minecraft.client.gui.screens.inventory."
SCREENS = ("Enchantment", "Loom", "CartographyTable", "Stonecutter", "Grindstone")
MIXINS = ("WorkstationScreenYzuiMixin", "EnchantmentScreenYzuiMixin",
          "StonecutterScreenYzuiMixin", "CartographyTableScreenYzuiMixin")


def source(path):
    return (CLIENT / path).read_text(encoding="utf-8")


def describe(javap, jar, name, parents=None):
    """按方法划分反汇编，防止把其他方法的同名调用误认为有效注入点。"""
    result = subprocess.run([str(javap), "-classpath", str(jar), "-p", "-c", "-s", name],
                            text=True, capture_output=True, check=True)
    if parents is not None:
        declaration = next(line for line in result.stdout.splitlines() if line.startswith("public ") and "class " in line)
        parent = re.search(r"\bextends ([\w.]+)", declaration)
        parents[name] = parent[1] if parent else None
    methods = {}
    parts = re.split(r"(?=^  (?:public|protected|private) [^\n]*\([^\n]*\);$)", result.stdout, flags=re.M)
    for part in parts[1:]:
        declaration = part.splitlines()[0]
        method = declaration.split("(", 1)[0].split()[-1]
        method = "<init>" if method == name else method
        descriptor = re.search(r"^    descriptor: (\([^\n]+)$", part, re.M)
        if descriptor:
            methods.setdefault(method, []).append((descriptor[1], part))
    return methods


def java_types(descriptor):
    """将 JVM 参数描述符转换为 Java 简名，核验 WrapOperation 的接收者和参数顺序。"""
    primitives = {"I": "int", "F": "float", "D": "double", "Z": "boolean", "J": "long",
                  "V": "void", "B": "byte", "S": "short", "C": "char"}
    tokens = re.findall(r"L([^;]+);|([IFDZJVBSC])", descriptor)
    return [name.rsplit("/", 1)[-1] if name else primitives[primitive] for name, primitive in tokens]


def validate_arguments(arguments, expected, ancestors, description):
    """按 Mixin 规则核验参数：父类参数也需要显式 @Coerce，且必须真实可赋值。"""
    actual = []
    for argument in arguments.split(","):
        argument = argument.strip()
        coerce = argument.startswith("@Coerce ")
        kind = re.sub(r"<[^>]+>", "", argument.removeprefix("@Coerce ").rsplit(" ", 1)[0])
        actual.append((kind, coerce))
    assert len(actual) == len(expected), f"包装参数数量不符：{description}"
    for index, ((kind, coerce), wanted) in enumerate(zip(actual, expected)):
        if kind == wanted:
            continue
        assert coerce and kind in ancestors.get(wanted, set()), (
            f"包装参数不符：{description} 参数 {index} 为 {kind}，期望 {wanted}；"
            "使用父类参数需要 @Coerce 且继承关系必须成立")


def check_receiver_regression(arguments, ancestors):
    """复现本次崩溃的缺失标记，并拒绝把无关屏幕作为父类的修复方式。"""
    rejected = 0
    for target in SCREENS:
        expected = [target + "Screen", "GuiGraphicsExtractor", "int", "int", "float", "Operation"]
        invalid = (arguments.replace("@Coerce ", ""),
                   arguments.replace("AbstractContainerScreen<?>", "BrewingStandScreen"))
        for candidate in invalid:
            try:
                validate_arguments(candidate, expected, ancestors, target)
            except AssertionError:
                rejected += 1
            else:
                raise AssertionError(f"回归检查未拦截错误的 super 调用接收者：{target}")
    print(f"通过：五种屏幕的 {rejected} 项接收者回归检查（缺失 @Coerce、无关类型）。")


def check_mixins(javap, jar):
    config = json.loads((ROOT / "src/client/resources/youzaiworldcore.client.mixins.json").read_text())
    parents = {}
    native = {name: describe(javap, jar, SCREEN_PACKAGE + name + "Screen", parents) for name in SCREENS}
    ancestors = {}
    for target in SCREENS:
        name = SCREEN_PACKAGE + target + "Screen"
        ancestor_names = set()
        parent = parents[name]
        while parent:
            ancestor_names.add(parent.rsplit(".", 1)[-1])
            if parent not in parents:
                describe(javap, jar, parent, parents)
            parent = parents[parent]
        ancestors[target + "Screen"] = ancestor_names
    wrappers = 0
    injection_checks = 0
    for name in MIXINS:
        assert config["client"].count(name) == 1, f"Mixin 注册缺失或重复：{name}"
        text = source(f"mixin/client/{name}.java")
        targets = re.findall(r"(\w+)Screen\.class", re.search(r"@Mixin\((.*?)\)", text, re.S)[1])
        assert targets and all(target in SCREENS for target in targets), f"本轮不应接管其他方块：{targets}"
        pattern = (r'@WrapOperation\(method = "([^"]+)", at = @At\(value = "INVOKE",\s*'
                   r'target = "L([^;]+);(\w+)(\([^\"]+)"\)\)\s*'
                   r'private (\w+) ([\w$]+)\((.*?)\) \{(.*?)(?=\n    \})')
        found = list(re.finditer(pattern, text, re.S))
        assert len(found) == text.count("@WrapOperation"), f"包装方法格式无法核验：{name}"
        for match in found:
            method, owner, call, descriptor, returns, handler, arguments, body = match.groups()
            assert returns == java_types(descriptor.split(")")[1])[0], f"包装返回类型错误：{handler}"
            assert "YzuiWorkstationScreen" in body and "original.call(" in body, f"缺少限定或原版回退：{handler}"
            for target in targets:
                entries = native[target].get(method, [])
                assert entries, f"Minecraft 缺少目标方法：{target}.{method}"
                reference = "// Method " + owner + "." + call + ":" + descriptor
                opcodes = [opcode for _, code in entries for opcode in re.findall(
                    r"^\s+\d+:\s+(invoke\w+)\s+[^\n]*" + re.escape(reference) + r"\s*$", code, re.M)]
                assert opcodes, f"注入调用不在指定方法中：{target}.{method} -> {reference}"
                for opcode in opcodes:
                    # MixinExtras 将 INVOKESPECIAL 接收者定义为被注入的类，而不是方法引用中的父类。
                    receiver = [] if opcode == "invokestatic" else [
                        target + "Screen" if opcode == "invokespecial" else owner.rsplit("/", 1)[-1]]
                    expected = [*receiver, *java_types(descriptor.split(")")[0]), "Operation"]
                    validate_arguments(arguments, expected, ancestors, f"{name}.{handler} -> {target}")
                injection_checks += 1
            if handler == "youzaiworldcore$skipDuplicateBackdrop":
                check_receiver_regression(arguments, ancestors)
            wrappers += 1
    print(f"通过：4 个 Mixin、{wrappers} 个包装方法的参数及返回类型、{injection_checks} 个真实方法内注入点。")
    return native


def check_screens(native, jar):
    route = source("mixin/client/YzuContainerScreenSwitchMixin.java")
    for name in SCREENS:
        text = source(f"client/screen/Yzu{name}Screen.java")
        assert f"extends {name}Screen implements YzuiWorkstationScreen" in text, name
        assert f"new Yzu{name}Screen(workstation.getMenu(), player.getInventory(), workstation.getTitle())" in route, name
        constructor = (f"(Lnet/minecraft/world/inventory/{name}Menu;"
                       "Lnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/network/chat/Component;)V")
        assert any(descriptor == constructor for descriptor, _ in native[name]["<init>"]), name
        assert text.index("super.extractBackground(") < text.index("super.extractRenderState("), name
        assert "this::onClose" in text and "hasClickedOutside(" in text, name
        assert not re.search(r"\b(?:imageWidth|imageHeight|leftPos|topPos)\s*=", text), f"改变原版命中坐标：{name}"
        assert not re.search(r"\b(?:mouseClicked|mouseDragged|mouseScrolled|containerTick|removed)\s*\(", text), name
    first_branch = route.index("if (newScreen instanceof ContainerScreen vanilla)")
    assert route.index("if (!ClientExternalSettings.isYzuiEnabled())") < first_branch
    assert route.index("if (newScreen instanceof YzuiWorkstationScreen)") < first_branch
    assert "new YzuBrewingStandScreen(menu, player.getInventory(), title)" in route
    style = source("client/render/YzuiWorkstationStyle.java")
    with zipfile.ZipFile(jar) as archive:
        resources = set(archive.namelist())
        for sprite in re.findall(r'"(container/[^\"]+)"', style):
            assert "assets/minecraft/textures/gui/sprites/" + sprite + ".png" in resources, sprite
    assert "default -> { return false; }" in style, "未知资源必须回退原版"
    print("通过：五种屏幕保留原版菜单、预览提取与交互坐标；关闭控件、路由开关及资源引用完整。")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--java-home", type=Path)
    args = parser.parse_args()
    version = re.search(r"^minecraft_version=(.+)$", (ROOT / "gradle.properties").read_text(), re.M)[1]
    jar = ROOT / f"minecraft_jar/{version}/{version}.jar"
    assert jar.is_file(), f"缺少本地参考 JAR：{jar}"
    javap = args.java_home / "bin/javap" if args.java_home else shutil.which("javap")
    assert javap, "未找到 javap；请用 --java-home 指定 JDK"
    check_screens(check_mixins(javap, jar), jar)
