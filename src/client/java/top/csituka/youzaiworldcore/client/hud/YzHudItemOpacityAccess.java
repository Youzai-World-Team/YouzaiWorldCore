package top.csituka.youzaiworldcore.client.hud;

/**
 * 为 26.2 延迟提交的 GUI 物品渲染状态附加 YZHUD 透明度。
 */
public interface YzHudItemOpacityAccess {

    /** 设置该物品图标的最终透明度。 */
    void youzaiworldcore$setOpacity(float opacity);

    /** @return 该物品图标的最终透明度 */
    float youzaiworldcore$getOpacity();
}
