package top.csituka.youzaiworldcore.client.event;

import net.fabricmc.fabric.api.event.player.UseEntityCallback;
import net.minecraft.client.gui.screens.ConfirmLinkScreen;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.decoration.painting.Painting;
import net.minecraft.world.entity.decoration.painting.PaintingVariant;

import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 监听玩家右键点击 meme_12（派蒙）画作的事件。
 * <p>
 * 当玩家右键点击 variant 为 {@code youzaiworldcore:meme_12} 的画时，
 * 弹出确认对话框，确认后通过 {@link ConfirmLinkScreen#confirmLinkNow} 在系统浏览器打开预配置的链接。
 * </p>
 *
 * <p>
 * URL 可在此类中修改 —— 见 {@link #PAIMON_LINK}。
 * </p>
 */
public class MemePaintingClickHandler {

    /** 右键 meme_12 画时打开的链接 */
    private static final String PAIMON_LINK = "https://ys.mihoyo.com/cloud";

    /**
     * 在 {@code Client.onInitializeClient()} 中调用一次即可。
     */
    public static void register() {
        UseEntityCallback.EVENT.register((player, world, hand, entity, hitResult) -> {
            if (!world.isClientSide()) {
                return InteractionResult.PASS;
            }
            if (!(entity instanceof Painting painting)) {
                return InteractionResult.PASS;
            }

            Holder<PaintingVariant> variant = painting.getVariant();
            var keyOpt = variant.unwrapKey();

            if (keyOpt.isEmpty()) {
                return InteractionResult.PASS;
            }

            Identifier id = keyOpt.get().identifier();
            if (!"youzaiworldcore".equals(id.getNamespace())
                    || !"meme_12".equals(id.getPath())) {
                return InteractionResult.PASS;
            }

            DebugLogger.info("MemePaintingClickHandler",
                    "玩家右键点击 meme_12 画，弹出链接确认: %s".formatted(PAIMON_LINK));

            ConfirmLinkScreen.confirmLinkNow(
                    null,
                    PAIMON_LINK);

            return InteractionResult.SUCCESS;
        });
    }
}
