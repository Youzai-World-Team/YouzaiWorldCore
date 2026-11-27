package top.csituka.youzaiworldcore.client.screen.widget;

import com.mojang.blaze3d.Blaze3D;
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

/** 原地行走并缓慢旋转的独立玩家模型，支持拖动和方向键查看装扮。 */
@SuppressWarnings("null")
public final class CosmeticPreviewWidget extends AbstractWidget {
    private static final double AUTO_ROTATION_SPEED = 30.0;
    private static final double MANUAL_VIEW_SECONDS = 4.0;
    private static final double WALK_CYCLE = 2.0 * Math.PI;
    private final Supplier<PlayerSkin> skin;
    private float yaw = 180f;
    private float pitch;
    private double lastFrameAt = Blaze3D.getTime();
    private double autoRotateAfter;
    private double walkPhase;

    public CosmeticPreviewWidget(int x, int y, int width, int height, Supplier<PlayerSkin> skin) {
        super(x, y, width, height, Component.translatable("screen.youzaiworldcore.cosmetic_preview.rotate"));
        this.skin = skin;
    }

    public void face(boolean back) {
        yaw = back ? 0f : 180f;
        pitch = 0f;
        holdView();
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
        updateMotion(state);
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

    private void updateMotion(AvatarRenderState state) {
        double now = Blaze3D.getTime();
        // 从长时间未绘制的页面返回时，限制首帧的步长以避免模型突然转向。
        double elapsed = Math.clamp(now - lastFrameAt, 0.0, 0.1);
        lastFrameAt = now;
        double rotationTime = Math.min(elapsed, Math.max(0.0, now - autoRotateAfter));
        yaw = (yaw + (float) (rotationTime * AUTO_ROTATION_SPEED)) % 360f;
        walkPhase = (walkPhase + elapsed * WALK_CYCLE) % WALK_CYCLE;
        // 原版玩家骨骼用 walkAnimationPos * 0.6662 作为摆臂和迈步的相位。
        state.walkAnimationPos = (float) (walkPhase / 0.6662);
        state.walkAnimationSpeed = 0.55f;
        state.capeLean = 18f;
        state.capeFlap = (float) (3.0 + 2.0 * Math.sin(walkPhase * 2.0));
    }

    private void holdView() {
        autoRotateAfter = Blaze3D.getTime() + MANUAL_VIEW_SECONDS;
    }

    @Override
    protected void onDrag(MouseButtonEvent event, double dx, double dy) {
        yaw = (yaw + (float) dx * 2.5f) % 360f;
        pitch = Math.clamp(pitch - (float) dy * 2.5f, -30f, 30f);
        holdView();
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
        holdView();
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
