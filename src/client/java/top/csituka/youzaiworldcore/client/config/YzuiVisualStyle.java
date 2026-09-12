package top.csituka.youzaiworldcore.client.config;

/** YZUI 视觉效果，保存于客户端 core_module.yzui_visual_style。 */
public enum YzuiVisualStyle {
    /** 半透明卡片、暗化背景和短过渡。 */
    STANDARD,
    /** 半透明卡片、原版背景模糊和较明显的过渡。 */
    FROSTED,
    /** 半透明卡片，关闭模糊、阴影和页面动画。 */
    MINIMAL;

    public float surfaceOpacity() { return this == FROSTED ? 0.82f : 0.86f; }
    public float layerOpacity() { return this == FROSTED ? 0.42f : 0.52f; }
    public float controlOpacity() { return this == FROSTED ? 0.78f : 0.86f; }

    /** HUD 底板和槽位分别着色，叠加后仍能看见世界；不影响图标和文字。 */
    public float hudPanelOpacity() {
        return switch (this) { case STANDARD -> 0.28f; case FROSTED -> 0.20f; case MINIMAL -> 0.32f; };
    }
    public float hudSlotOpacity() { return this == FROSTED ? 0.14f : 0.18f; }
    public float hudSelectionOpacity() { return this == FROSTED ? 0.18f : 0.24f; }

    /** 文字底衬与槽位一样通透，避免在 HUD 上形成明显的实色矩形。 */
    public float hudLabelOpacity() { return hudSlotOpacity(); }
}
