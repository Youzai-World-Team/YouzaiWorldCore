/*
 * Adapted from Higher Chat (https://github.com/MDLC01/higher-chat-mc)
 * Original author: MDLC01
 * Original license: Unlicense (Public Domain)
 *
 * This file is part of YouzaiWorldCore.
 * Licensed under Apache-2.0.
 */
package top.csituka.youzaiworldcore.client.higherchat;

import net.minecraft.client.Minecraft;

/**
 * Tracks the vertical position of HUD icons (armor, health, food, vehicle health)
 * to determine the optimal chat position — above the armor bar.
 *
 * <p>Adapted from Higher Chat by MDLC01.</p>
 */
public final class SharedStorage {

    /** Previously recorded no-mans-land height, used for smoothing. */
    private static int lastNoMansLandHeight = 0;

    /** The highest Y coordinate found among HUD icons in the current frame. */
    private static int maxBarHeight;

    private SharedStorage() {
        throw new UnsupportedOperationException("Utility class");
    }

    /**
     * Returns the current GUI scaled window height.
     */
    private static int getWindowHeight() {
        return Minecraft.getInstance().getWindow().getGuiScaledHeight();
    }

    /**
     * Resets tracking data at the start of each frame.
     * Initialises {@code maxBarHeight} to the window height so any icon
     * will be detected as higher.
     */
    public static void resetData() {
        maxBarHeight = getWindowHeight();
    }

    /**
     * Records the position of a HUD icon sprite.
     * If the icon is within the chat's horizontal bounds and is higher
     * (lower Y) than the current minimum, updates {@code maxBarHeight}.
     *
     * @param x the X coordinate of the icon
     * @param y the Y coordinate of the icon
     */
    public static void declareIconAt(int x, int y) {
        if (x < Minecraft.getInstance().gui.hud.getChat().getWidth() && y < maxBarHeight) {
            maxBarHeight = y;
        }
    }

    /**
     * Calculates the optimal bottom margin for the chat component so that
     * it sits above the highest HUD icon (typically the armor bar).
     *
     * <p>If there are queued chat lines the margin is increased slightly to
     * provide visual breathing room.</p>
     *
     * @return the bottom margin in scaled pixels (0 means no adjustment)
     */
    public static int getOptimalChatMargin() {
        boolean hasQueue = Minecraft.getInstance().gui.chatListener().queueSize() > 0L;

        // Smoothing: if the bar height changes by at most 2 pixels, keep the old value
        int noMansLandHeight = maxBarHeight;
        if (lastNoMansLandHeight > maxBarHeight && lastNoMansLandHeight - maxBarHeight <= 2) {
            noMansLandHeight = lastNoMansLandHeight;
        }
        lastNoMansLandHeight = noMansLandHeight;

        int optimalBottomPos = noMansLandHeight - (hasQueue ? 10 : 1);

        // If the chat would overflow the screen, fall back to the default margin (0).
        if (optimalBottomPos < Minecraft.getInstance().gui.hud.getChat().getHeight()) {
            return 0;
        }

        return getWindowHeight() - optimalBottomPos;
    }
}
