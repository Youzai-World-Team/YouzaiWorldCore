package top.csituka.youzaiworldcore.client.screen.widget;

import com.mojang.blaze3d.platform.InputConstants;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarratedElementType;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.player.PlayerSkin;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** 可拖动、可用方向键旋转的独立玩家模型，始终显示皮肤外层与可选披风。 */
@SuppressWarnings("null")
public final class CosmeticPreviewWidget extends AbstractWidget {
    private final Supplier<PlayerSkin> skin;
    private float yaw = 180f;
    private float pitch;

    public CosmeticPreviewWidget(int x, int y, int width, int height, Supplier<PlayerSkin> skin) {
        super(x, y, width, height, Component.translatable("screen.youzaiworldcore.cosmetic_preview.rotate"));
        this.skin = skin;
    }

    public void face(boolean back) {
        yaw = back ? 0f : 180f;
        pitch = 0f;
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
        // 每帧使用独立快照，延迟提交的 GUI 渲染不共享可变模型或真实玩家状态。
        AvatarRenderState state = new AvatarRenderState();
        state.entityType = EntityTypes.PLAYER;
        state.skin = skin.get();
        state.boundingBoxWidth = 0.6f;
        state.boundingBoxHeight = 1.8f;
        state.eyeHeight = 1.62f;
        state.bodyRot = yaw;
        float scale = Math.max(1f, Math.min((getHeight() - 8f) / 1.875f, (getWidth() - 8f) / 1.4f));
        Quaternionf tilt = new Quaternionf().rotateX((float) Math.toRadians(pitch));
        Quaternionf rotation = new Quaternionf().rotateZ((float) Math.PI).mul(tilt);
        g.enableScissor(getX(), getY(), getRight(), getBottom());
        try {
            g.entity(state, scale, new Vector3f(0f, 0.9625f, 0f), rotation, tilt,
                    getX(), getY(), getRight(), getBottom());
        } finally {
            g.disableScissor();
        }
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        yaw = (yaw + (float) dx * 2.5f) % 360f;
        pitch = Math.clamp(pitch - (float) dy * 2.5f, -30f, 30f);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        switch (event.key()) {
            case InputConstants.KEY_LEFT -> yaw -= 15f;
            case InputConstants.KEY_RIGHT -> yaw += 15f;
            case InputConstants.KEY_UP -> pitch = Math.max(-30f, pitch - 5f);
            case InputConstants.KEY_DOWN -> pitch = Math.min(30f, pitch + 5f);
            default -> { return super.keyPressed(event); }
        }
        return true;
    }

    @Override
    public void playDownSound(SoundManager soundManager) {
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        output.add(NarratedElementType.TITLE, getMessage());
        output.add(NarratedElementType.USAGE,
                Component.translatable("screen.youzaiworldcore.cosmetic_preview.rotate"));
    }
}
