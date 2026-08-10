package top.csituka.youzaiworldcore.client.render;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * 圆角矩形绘制工具（行扫描实现）。
 *
 * <h3>为什么需要这个类</h3>
 * <p>
 * 项目里原先有 9+ 份互相复制的圆角矩形实现，全部采用「逐像素填充」：
 * 遍历 {@code r×r} 的角落方格，对每个满足 {@code i²+j² < r²} 的像素调用一次
 * {@code fill()}。26.2 的 GUI 是延迟渲染架构，每次 {@code fill()} 都会分配一个
 * render-state 条目入队并参与后续排序合批，于是：
 * </p>
 * <pre>
 *   r=3 → 39 次 fill    r=4 → 63 次 fill    r=6 → 135 次 fill
 * </pre>
 * <p>
 * 仅热键栏一帧就要提交约 690 次。本类改为「按行扫描」：圆角区域的每一行都是一段
 * 连续像素，一次 {@code fill()} 即可覆盖，把开销降到 {@code 1 + 2r} 次
 * （r=6 时 135 → 13，降约 10 倍）。
 * </p>
 *
 * <h3>像素等价性</h3>
 * <p>
 * 输出与原逐像素实现<b>完全一致</b>。原实现对固定的 {@code j}，满足
 * {@code i² + j² < r²} 的 {@code i} 恒为 {@code 0..k-1} 的连续区间，
 * 因而每一行本就是一段连续像素——本类只是把「一行 k 次 1px 绘制」合并成
 * 「一行 1 次 k px 绘制」，点亮的像素集合逐一对应。
 * </p>
 * <p>
 * 同时保证每个像素<b>恰好绘制一次</b>：面板色普遍带 alpha（如 {@code 0x80FFFFFF}），
 * 重叠绘制会二次混合导致颜色变深，因此中段与圆角行严格互不重叠。
 * </p>
 *
 * <h3>半径错配问题</h3>
 * <p>
 * 半径由调用方直接传入，跨度表按<b>实际半径</b>查表缓存。原先
 * {@code InventoryHudRenderer} / {@code ArmorHudRenderer} 里
 * 「偏移表按 r=6 预建、实际却传入 GUI 缩放后的半径」的错配不复存在。
 * </p>
 */
public final class RoundedRect {

    /** 跨度表缓存上限。UI 半径都是小整数，用数组直接索引避免装箱与哈希。 */
    private static final int MAX_CACHED_RADIUS = 64;

    /**
     * 半径 → 每行跨度表。
     * <p>
     * {@code SPAN_CACHE[r][j]} = 圆角第 {@code j} 行（从最外侧那行往内数）
     * 需要在中段两侧各延伸出去的像素数。
     * </p>
     */
    private static final int[][] SPAN_CACHE = new int[MAX_CACHED_RADIUS + 1][];

    private RoundedRect() {
    }

    /**
     * 绘制实心圆角矩形。
     *
     * @param g     绘制上下文
     * @param x     左上角 X
     * @param y     左上角 Y
     * @param w     宽度
     * @param h     高度
     * @param r     圆角半径
     * @param color ARGB 颜色
     */
    public static void fill(GuiGraphicsExtractor g,
            int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) {
            return;
        }

        // 半径钳制到 min(w, h) / 2，与 MailUi.roundedRect / MainMenuElements.drawRoundedRect /
        // YzuInventoryScreen.fillRoundedRect 的既有语义一致。
        //
        // 另有几处旧实现（HotbarRenderer / ConfirmationDialog / ToggleButton / DropdownButton）
        // 在 w <= 2r 时回退为直角矩形、且完全不校验高度（h <= 2r 时中段与圆角行重叠，
        // 半透明色被混合两次形成假边框）。经逐一核对，这些调用点的实际尺寸均满足
        // w > 2r 且 h > 2r，退化分支从未被触发，故统一为钳制不改变任何既有观感。
        int clamped = Math.min(w, h) / 2;
        if (r > clamped) {
            r = clamped;
        }
        if (r <= 0) {
            g.fill(x, y, x + w, y + h, color);
            return;
        }

        int[] spans = spansFor(r);

        // 中段：整幅宽度，纵向排除上下各 r 行的圆角区域。
        // 等价于原实现的「左侧竖条 + 中间主体（中段行部分）+ 右侧竖条」。
        g.fill(x, y + r, x + w, y + h - r, color);

        // 圆角行：每行一次 fill，横向跨度 = 中段宽度 + 两侧各 spans[j]。
        // 等价于原实现的「中间主体（圆角行部分）+ 四角逐像素」的并集。
        for (int j = 0; j < r; j++) {
            int extend = spans[j];
            int left = x + r - extend;
            int right = x + w - r + extend;
            g.fill(left, y + r - 1 - j, right, y + r - j, color);          // 顶部第 j 行
            g.fill(left, y + h - r + j, right, y + h - r + j + 1, color);  // 底部第 j 行
        }
    }

    /**
     * 绘制「外框 + 内填充」的圆角矩形（1px 描边效果）。
     * <p>
     * 语义等同于连续两次 {@link #fill}，仅作为可读性糖，不改变绘制结果。
     * </p>
     *
     * @param g           绘制上下文
     * @param x           外框左上角 X
     * @param y           外框左上角 Y
     * @param w           外框宽度
     * @param h           外框高度
     * @param outerRadius 外框圆角半径
     * @param innerRadius 内填充圆角半径
     * @param borderColor 外框颜色
     * @param fillColor   内填充颜色
     */
    public static void fillWithBorder(GuiGraphicsExtractor g,
            int x, int y, int w, int h,
            int outerRadius, int innerRadius,
            int borderColor, int fillColor) {
        fill(g, x, y, w, h, outerRadius, borderColor);
        fill(g, x + 1, y + 1, w - 2, h - 2, innerRadius, fillColor);
    }

    // ===== 内部：跨度表 =====

    /** 取得（并按需构建）指定半径的每行跨度表。 */
    private static int[] spansFor(int r) {
        if (r > MAX_CACHED_RADIUS) {
            return buildSpans(r);
        }
        int[] cached = SPAN_CACHE[r];
        if (cached == null) {
            cached = buildSpans(r);
            SPAN_CACHE[r] = cached;
        }
        return cached;
    }

    /**
     * 构建跨度表：逐行统计满足 {@code i² + j² < r²} 的 {@code i} 的个数。
     * <p>
     * {@code i} 递增时 {@code i²+j²} 单调递增，首次不满足即可停。
     * </p>
     */
    private static int[] buildSpans(int r) {
        int[] spans = new int[r];
        int rr = r * r;
        for (int j = 0; j < r; j++) {
            int count = 0;
            for (int i = 0; i < r; i++) {
                if (i * i + j * j < rr) {
                    count++;
                } else {
                    break;
                }
            }
            spans[j] = count;
        }
        return spans;
    }
}
