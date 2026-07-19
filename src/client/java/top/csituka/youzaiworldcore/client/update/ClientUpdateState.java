package top.csituka.youzaiworldcore.client.update;

import top.csituka.youzaiworldcore.update.UpdateResult;

/**
 * 客户端更新检查结果持有者。
 * <p>
 * 由客户端启动时异步 {@link top.csituka.youzaiworldcore.update.UpdateChecker#checkAsync(String)}
 * 填充，供 {@code TitleScreenMixin} 在每一帧读取以决定标题界面右侧面板是否显示更新信息。
 * 使用 {@code volatile} 保证异步写入对渲染线程的可见性。
 * </p>
 */
public final class ClientUpdateState {

    /** 最近一次检查结果；未检查或检查失败时为 null */
    private static volatile UpdateResult current = null;

    private ClientUpdateState() {
    }

    /** 写入最新检查结果（由异步线程调用） */
    public static void set(UpdateResult result) {
        current = result;
    }

    /** @return 最近一次检查结果，可能为 null */
    public static UpdateResult get() {
        return current;
    }
}
