package top.csituka.youzaiworldcore.startup;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.AbstractInsnNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 为 Fabric Loader 0.19.3 的入口点调用循环注入启动进度回调。
 * <p>
 * 实现思路参考 Mod Loading Screen，并只保留本项目需要的 Fabric 入口点追踪。
 * </p>
 */
final class StartupEntrypointTransformer {

    static final String TARGET_CLASS = "net/fabricmc/loader/impl/FabricLoaderImpl";
    static final String BRIDGE_CLASS =
            "top/csituka/youzaiworldcore/startup/StartupEntrypointBridge";

    private static final String INVOKE_ENTRYPOINTS_DESCRIPTOR =
            "(Ljava/lang/String;Ljava/lang/Class;Ljava/util/function/Consumer;)V";

    private StartupEntrypointTransformer() {
    }

    /** 转换目标 Loader 类；其它类返回 {@code null} 表示保持原样。 */
    static byte[] transform(String className, byte[] classBytes) {
        if (!TARGET_CLASS.equals(className)) {
            return null;
        }

        try {
            ClassReader reader = new ClassReader(classBytes);
            org.objectweb.asm.tree.ClassNode classNode = new org.objectweb.asm.tree.ClassNode();
            reader.accept(classNode, 0);

            MethodNode method = classNode.methods.stream()
                    .filter(candidate -> "invokeEntrypoints".equals(candidate.name))
                    .filter(candidate -> INVOKE_ENTRYPOINTS_DESCRIPTOR.equals(candidate.desc))
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException(
                            "FabricLoaderImpl.invokeEntrypoints 签名不匹配"
                    ));

            AbstractInsnNode entrypointListStore = findVariableStore(method, 5);
            AbstractInsnNode containerStore = findVariableStore(method, 7);
            AbstractInsnNode finalReturn = findFinalReturn(method);

            method.instructions.insert(entrypointListStore, createEntrypointTypeHook());
            method.instructions.insert(containerStore, createSingleEntrypointHook());
            method.instructions.insertBefore(finalReturn, createEntrypointTypeCompleteHook());

            ClassWriter writer = new ClassWriter(
                    reader,
                    ClassWriter.COMPUTE_MAXS | ClassWriter.COMPUTE_FRAMES
            );
            classNode.accept(writer);
            DebugLogger.info("StartupLoading", "已接入 Fabric Loader 模组入口点进度");
            return writer.toByteArray();
        } catch (Throwable throwable) {
            DebugLogger.exception("StartupLoading", "转换 Fabric Loader 入口点调用", throwable);
            return null;
        }
    }

    private static AbstractInsnNode findVariableStore(MethodNode method, int variableIndex) {
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction instanceof VarInsnNode variableInstruction
                    && instruction.getOpcode() == Opcodes.ASTORE
                    && variableInstruction.var == variableIndex) {
                return instruction;
            }
        }
        throw new IllegalStateException("未找到 Fabric Loader 局部变量：" + variableIndex);
    }

    private static AbstractInsnNode findFinalReturn(MethodNode method) {
        AbstractInsnNode result = null;
        for (AbstractInsnNode instruction : method.instructions) {
            if (instruction.getOpcode() == Opcodes.RETURN) {
                result = instruction;
            }
        }
        if (result == null) {
            throw new IllegalStateException("未找到 Fabric Loader 入口点方法返回指令");
        }
        return result;
    }

    private static InsnList createEntrypointTypeHook() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 5));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "java/util/List",
                "size",
                "()I",
                true
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BRIDGE_CLASS,
                "beforeEntrypointType",
                "(Ljava/lang/String;Ljava/lang/Class;I)V",
                false
        ));
        return instructions;
    }

    private static InsnList createSingleEntrypointHook() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 2));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEVIRTUAL,
                "java/lang/Class",
                "getSimpleName",
                "()Ljava/lang/String;",
                false
        ));
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 7));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "net/fabricmc/loader/api/entrypoint/EntrypointContainer",
                "getProvider",
                "()Lnet/fabricmc/loader/api/ModContainer;",
                true
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "net/fabricmc/loader/api/ModContainer",
                "getMetadata",
                "()Lnet/fabricmc/loader/api/metadata/ModMetadata;",
                true
        ));
        instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.DUP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "net/fabricmc/loader/api/metadata/ModMetadata",
                "getId",
                "()Ljava/lang/String;",
                true
        ));
        instructions.add(new org.objectweb.asm.tree.InsnNode(Opcodes.SWAP));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKEINTERFACE,
                "net/fabricmc/loader/api/metadata/ModMetadata",
                "getName",
                "()Ljava/lang/String;",
                true
        ));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BRIDGE_CLASS,
                "beforeSingleEntrypoint",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;)V",
                false
        ));
        return instructions;
    }

    private static InsnList createEntrypointTypeCompleteHook() {
        InsnList instructions = new InsnList();
        instructions.add(new VarInsnNode(Opcodes.ALOAD, 1));
        instructions.add(new MethodInsnNode(
                Opcodes.INVOKESTATIC,
                BRIDGE_CLASS,
                "afterEntrypointType",
                "(Ljava/lang/String;)V",
                false
        ));
        return instructions;
    }
}
