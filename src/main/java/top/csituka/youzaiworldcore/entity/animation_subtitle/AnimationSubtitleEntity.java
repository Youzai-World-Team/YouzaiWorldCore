package top.csituka.youzaiworldcore.entity.animation_subtitle;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.network.syncher.SynchedEntityData.Builder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.NonNull;
import top.csituka.youzaiworldcore.text.StyledTextUtil;
import top.csituka.youzaiworldcore.text.StyledTextUtil.GlyphSlot;

import java.util.ArrayList;
import java.util.List;

/**
 * 动画字幕实体 — 在 3D 世界中生成的可定制的浮动文字。
 *
 * <h2>双模式架构</h2>
 * <ul>
 *   <li><b>主字幕模式 (MODE_MAIN = 0)</b>：逐字弹出动画 → 保持显示 → 逐字掉落为碎片</li>
 *   <li><b>碎片模式 (MODE_SHARD = 1)</b>：物理下落 → 沉降 → 静止 → 缩小消失</li>
 * </ul>
 *
 * <h2>碎片状态机</h2>
 * <pre>{@code
 * FALLING (0) --接触地面--> SETTLING (1) --6tick--> RESTING (2) --90tick--> SHRINKING (3) --20tick--> discard
 * }</pre>
 */
@SuppressWarnings("null")
public class AnimationSubtitleEntity extends Entity {

    // ======================== 模式常量 ========================

    public static final int MODE_MAIN = 0;
    public static final int MODE_SHARD = 1;

    // ======================== 碎片状态常量 ========================

    public static final int SHARD_FALLING = 0;
    public static final int SHARD_SETTLING = 1;
    public static final int SHARD_RESTING = 2;
    public static final int SHARD_SHRINKING = 3;

    // ======================== 动画参数 ========================

    /** 主字幕每字符弹出间隔 (tick) */
    private static final int POP_INTERVAL = 4;
    /** 弹出动画持续时间 (tick) */
    private static final int POP_DURATION = 8;
    /** 弹出过冲系数 */
    private static final float POP_OVERSHOOT = 0.42F;
    /** 默认保持时间 (tick) — {@code 5.0 * 20} */
    public static final int DEFAULT_HOLD_TICKS = 100;
    /** 每字符掉落间隔 (tick) */
    private static final int DROP_INTERVAL = 1;
    /** 浮动动画幅度 */
    private static final float FLOAT_AMPLITUDE = 0.008F;
    /** 浮动动画速度 */
    private static final float FLOAT_SPEED = 0.08F;

    // ======================== 碎片物理参数 ========================

    private static final double GRAVITY = 0.018;
    private static final double GROUND_BOUNCE = 0.22;
    private static final double AIR_FRICTION_X = 0.88;
    private static final double AIR_FRICTION_Z = 0.88;
    private static final double GROUND_FRICTION_X = 0.72;
    private static final double GROUND_FRICTION_Z = 0.72;
    private static final int SETTLING_DURATION = 6;
    private static final int RESTING_DURATION = 90;
    private static final int SHRINKING_DURATION = 20;
    /** 碎片安全超时 (tick) */
    private static final int SHARD_TIMEOUT = 420;

    // ======================== 同步数据 ========================

    private static final EntityDataAccessor<String> DATA_TEXT =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.STRING);
    private static final EntityDataAccessor<Float> DATA_ROLL =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_MODE =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_BASE_SCALE =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_VISIBLE_COUNT =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_DROPPED_COUNT =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_HOLD_TICKS =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_SHARD_STATE =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> DATA_SHRINK_FACTOR =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Integer> DATA_ALPHA =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> DATA_RGB =
            SynchedEntityData.defineId(AnimationSubtitleEntity.class, EntityDataSerializers.INT);

    // ======================== 本地缓存（不同步） ========================

    private List<GlyphSlot> cachedGlyphs;
    private List<Float> cachedCharWidths;
    private float cachedTotalWidth;
    private float cachedTotalHeight;
    private int shardStateTimer;
    private int shardStartTick;

    // ======================== 构造 ========================

    public AnimationSubtitleEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(false);
    }

    // ======================== 同步数据定义 ========================

    @Override
    protected void defineSynchedData(@NonNull Builder builder) {
        builder.define(DATA_TEXT, "");
        builder.define(DATA_ROLL, 0.0F);
        builder.define(DATA_MODE, MODE_MAIN);
        builder.define(DATA_BASE_SCALE, 1.0F);
        builder.define(DATA_VISIBLE_COUNT, 0);
        builder.define(DATA_DROPPED_COUNT, 0);
        builder.define(DATA_HOLD_TICKS, DEFAULT_HOLD_TICKS);
        builder.define(DATA_SHARD_STATE, SHARD_FALLING);
        builder.define(DATA_SHRINK_FACTOR, 1.0F);
        builder.define(DATA_ALPHA, 255);
        builder.define(DATA_RGB, 0xFFFFFF);
    }

    // ======================== 公共构造方法 ========================

    /**
     * 创建主字幕实体。
     *
     * @param level      世界实例
     * @param pos        生成位置
     * @param yRot       Y 轴旋转角度
     * @param text       字幕文本（支持格式化代码）
     * @param baseScale  基础缩放
     * @param holdTicks  保持显示 tick 数
     * @return 创建好的主字幕实体
     */
    public static AnimationSubtitleEntity createMain(
            Level level, Vec3 pos, float yRot, String text, float baseScale, int holdTicks
    ) {
        AnimationSubtitleEntity entity = new AnimationSubtitleEntity(
                ModAnimationSubtitleEntities.ANIMATION_SUBTITLE, level);
        entity.setPos(pos);
        entity.setYRot(yRot);
        entity.getEntityData().set(DATA_TEXT, text);
        entity.getEntityData().set(DATA_MODE, MODE_MAIN);
        entity.getEntityData().set(DATA_BASE_SCALE, baseScale);
        entity.getEntityData().set(DATA_HOLD_TICKS, holdTicks);
        entity.getEntityData().set(DATA_RGB, 0xFFFFFF);
        entity.rebuildGlyphCache();
        return entity;
    }

    /**
     * 创建碎片实体（从主字幕掉落下来的单个字符）。
     *
     * @param level     世界实例
     * @param pos       生成位置
     * @param delta     初始速度
     * @param glyphText 字形文本（带格式前缀）
     * @param sizeScale 大小缩放
     * @param rgbColor  RGB 颜色
     * @return 创建好的碎片实体
     */
    public static AnimationSubtitleEntity createShard(
            Level level, Vec3 pos, Vec3 delta, String glyphText, float sizeScale, int rgbColor
    ) {
        AnimationSubtitleEntity entity = new AnimationSubtitleEntity(
                ModAnimationSubtitleEntities.ANIMATION_SUBTITLE, level);
        entity.setPos(pos);
        entity.setDeltaMovement(delta);
        entity.getEntityData().set(DATA_TEXT, glyphText);
        entity.getEntityData().set(DATA_MODE, MODE_SHARD);
        entity.getEntityData().set(DATA_BASE_SCALE, sizeScale);
        entity.getEntityData().set(DATA_SHARD_STATE, SHARD_FALLING);
        entity.getEntityData().set(DATA_ALPHA, 255);
        entity.getEntityData().set(DATA_RGB, rgbColor);
        entity.shardStateTimer = 0;
        entity.shardStartTick = entity.tickCount;
        return entity;
    }

    // ======================== Tick 逻辑 ========================

    @Override
    public void tick() {
        int mode = getEntityData().get(DATA_MODE);
        if (mode == MODE_MAIN) {
            tickMain();
        } else {
            tickShard();
        }
    }

    private void tickMain() {
        if (level().isClientSide()) {
            // 客户端仅处理动画状态
            return;
        }
        if (cachedGlyphs == null) {
            rebuildGlyphCache();
        }

        int visibleCount = getEntityData().get(DATA_VISIBLE_COUNT);
        int droppedCount = getEntityData().get(DATA_DROPPED_COUNT);
        int totalVisible = StyledTextUtil.countVisibleGlyphs(cachedGlyphs);

        // 阶段 1: 逐字弹出
        if (visibleCount < totalVisible) {
            if (tickCount % POP_INTERVAL == 0) {
                getEntityData().set(DATA_VISIBLE_COUNT, visibleCount + 1);
            }
            return;
        }

        // 阶段 2: 保持显示中
        int holdTicks = getEntityData().get(DATA_HOLD_TICKS);
        int holdElapsed = tickCount - (totalVisible * POP_INTERVAL);
        if (holdElapsed < holdTicks) {
            return;
        }

        // 阶段 3: 逐字掉落
        if (droppedCount < totalVisible) {
            if (tickCount % DROP_INTERVAL == 0) {
                dropCharAsShard(droppedCount);
                getEntityData().set(DATA_DROPPED_COUNT, droppedCount + 1);
            }
            return;
        }

        // 所有字符已掉落完毕
        discard();
    }

    private void dropCharAsShard(int visibleIndex) {
        GlyphSlot slot = StyledTextUtil.getVisibleGlyphAt(cachedGlyphs, visibleIndex);
        if (slot == null) {
            return;
        }

        Vec3 charPos = getMainCharWorldPos(visibleIndex);
        Vec3 initialVelocity = new Vec3(
                (random.nextDouble() - 0.5) * 0.05,
                0.02 + random.nextDouble() * 0.03,
                (random.nextDouble() - 0.5) * 0.05
        );

        AnimationSubtitleEntity shard = createShard(
                level(), charPos, initialVelocity,
                slot.styledText(), slot.sizeScale(), slot.rgbColor()
        );

        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.addFreshEntity(shard);
        }
    }

    private void tickShard() {
        int state = getEntityData().get(DATA_SHARD_STATE);

        // 安全超时
        if (tickCount - shardStartTick > SHARD_TIMEOUT) {
            discard();
            return;
        }

        switch (state) {
            case SHARD_FALLING -> tickShardFalling();
            case SHARD_SETTLING -> tickShardSettling();
            case SHARD_RESTING -> tickShardResting();
            case SHARD_SHRINKING -> tickShardShrinking();
        }
    }

    private void tickShardFalling() {
        if (level().isClientSide()) {
            return;
        }

        // 子步骤物理
        for (int step = 0; step < 2; step++) {
            Vec3 delta = getDeltaMovement();
            // 重力
            delta = delta.add(0.0, -GRAVITY, 0.0);
            setDeltaMovement(delta);

            // 移动
            Vec3 oldPos = position();
            move(MoverType.SELF, getDeltaMovement());

            // 地面碰撞
            if (onGround() || verticalCollision) {
                Vec3 newDelta = getDeltaMovement();
                newDelta = new Vec3(
                        Math.abs(newDelta.x) > 0.001 ? -newDelta.x * GROUND_BOUNCE : 0,
                        Math.abs(newDelta.y) > 0.001 ? -newDelta.y * GROUND_BOUNCE : 0,
                        Math.abs(newDelta.z) > 0.001 ? -newDelta.z * GROUND_BOUNCE : 0
                );

                // 速度过小则进入沉降
                if (Math.abs(newDelta.y) < 0.01 && Math.abs(newDelta.x) < 0.005 && Math.abs(newDelta.z) < 0.005) {
                    getEntityData().set(DATA_SHARD_STATE, SHARD_SETTLING);
                    shardStateTimer = 0;
                    // 微调 Y 坐标对齐地面
                    setPos(getX(), Math.floor(getY()) + 0.01, getZ());
                    setDeltaMovement(Vec3.ZERO);
                    return;
                }
                setDeltaMovement(newDelta);
            }

            // 水平摩擦
            Vec3 currDelta = getDeltaMovement();
            setDeltaMovement(new Vec3(
                    currDelta.x * (onGround() ? GROUND_FRICTION_X : AIR_FRICTION_X),
                    currDelta.y,
                    currDelta.z * (onGround() ? GROUND_FRICTION_Z : AIR_FRICTION_Z)
            ));
        }

        // 速度极低且在地面上时进入沉降
        if (onGround() && getDeltaMovement().lengthSqr() < 0.0001) {
            getEntityData().set(DATA_SHARD_STATE, SHARD_SETTLING);
            shardStateTimer = 0;
            setDeltaMovement(Vec3.ZERO);
        }
    }

    private void tickShardSettling() {
        shardStateTimer++;
        if (shardStateTimer >= SETTLING_DURATION) {
            getEntityData().set(DATA_SHARD_STATE, SHARD_RESTING);
            shardStateTimer = 0;
        }
    }

    private void tickShardResting() {
        shardStateTimer++;
        if (shardStateTimer >= RESTING_DURATION) {
            getEntityData().set(DATA_SHARD_STATE, SHARD_SHRINKING);
            shardStateTimer = 0;
        }
    }

    private void tickShardShrinking() {
        shardStateTimer++;
        float progress = (float) shardStateTimer / SHRINKING_DURATION;
        if (progress > 1.0F) {
            progress = 1.0F;
        }

        // 四次方缩小
        float shrink = 1.0F - (progress * progress * progress * progress);
        getEntityData().set(DATA_SHRINK_FACTOR, Math.max(0.0F, shrink));

        // 二次方淡出
        int alpha = (int) (255 * (1.0F - progress * progress));
        getEntityData().set(DATA_ALPHA, Math.max(0, alpha));

        if (progress >= 1.0F) {
            if (!level().isClientSide()) {
                discard();
            }
        }
    }

    @Override
    public void onSyncedDataUpdated(@NonNull EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (DATA_TEXT.equals(key)) {
            rebuildGlyphCache();
        }
    }

    // ======================== 字形缓存与布局计算 ========================

    public void rebuildGlyphCache() {
        String text = getEntityData().get(DATA_TEXT);
        cachedGlyphs = StyledTextUtil.splitGlyphSlotsCached(text, false);
        cachedCharWidths = new ArrayList<>();
        float totalW = 0;
        float totalH = 1; // 至少一行

        int lineWidth = 0;
        for (GlyphSlot slot : cachedGlyphs) {
            if (!slot.visible()) {
                cachedCharWidths.add(0.0F);
                continue;
            }
            String s = slot.styledText();
            if ("\n".equals(s)) {
                cachedCharWidths.add(0.0F);
                totalW = Math.max(totalW, lineWidth);
                lineWidth = 0;
                totalH++;
                continue;
            }
            // 提取样式码后的原始字符
            char rawChar = s.charAt(s.length() - 1);
            boolean bold = s.contains("\u00a7l");
            int w = StyledTextUtil.estimateCharWidth(rawChar, bold);
            cachedCharWidths.add((float) w);
            lineWidth += w;
        }
        totalW = Math.max(totalW, lineWidth);
        cachedTotalWidth = totalW;
        cachedTotalHeight = totalH;
    }

    /**
     * 计算指定可见字符索引在 3D 世界中的位置。
     */
    public Vec3 getMainCharWorldPos(int visibleIndex) {
        float baseScale = getEntityData().get(DATA_BASE_SCALE);
        float yRotRad = (float) Math.toRadians(getYRot());

        float totalH = cachedTotalHeight;

        // 计算各行可见字符数量
        float layoutX = 0;
        float layoutY = 0;
        int glyphsInCurrentLine = 0;

        List<Integer> lineGlyphCounts = new ArrayList<>();
        for (int i = 0; i < cachedGlyphs.size(); i++) {
            GlyphSlot slot = cachedGlyphs.get(i);
            if (!slot.visible()) {
                continue;
            }
            if ("\n".equals(slot.styledText())) {
                lineGlyphCounts.add(glyphsInCurrentLine);
                glyphsInCurrentLine = 0;
            } else {
                glyphsInCurrentLine++;
            }
        }
        lineGlyphCounts.add(glyphsInCurrentLine);

        // 找到目标字符所在行
        int targetLine = 0;
        int lineStartVisible = 0;
        int remaining = visibleIndex;
        for (int line = 0; line < lineGlyphCounts.size(); line++) {
            int count = lineGlyphCounts.get(line);
            if (remaining < count) {
                targetLine = line;
                lineStartVisible = 0;
                for (int l = 0; l < line; l++) {
                    lineStartVisible += lineGlyphCounts.get(l);
                }
                break;
            }
            remaining -= count;
        }

        // 计算该行宽度
        float lineWidth = 0;
        int vi = 0;
        int li = 0;
        for (GlyphSlot slot : cachedGlyphs) {
            if (!slot.visible()) continue;
            if ("\n".equals(slot.styledText())) {
                if (li == targetLine) break;
                lineWidth = 0;
                li++;
                continue;
            }
            if (li == targetLine) {
                if (vi == remaining) {
                    // 这是目标字符
                    break;
                }
                lineWidth += cachedCharWidths.get(vi + lineStartVisible - (vi > 0 && li > 0 ? 0 : 0));
            }
            vi++;
        }

        // 目标字符的本地偏移量
        float charOffset = 0;
        vi = 0;
        boolean found = false;
        for (int i = 0; i < cachedGlyphs.size(); i++) {
            GlyphSlot slot = cachedGlyphs.get(i);
            if (!slot.visible()) continue;
            if ("\n".equals(slot.styledText())) {
                if (found) break;
                continue;
            }
            if (vi == visibleIndex) {
                found = true;
                break;
            }
            charOffset += cachedCharWidths.get(i);
            vi++;
        }

        // 居中偏移
        float centerX = lineWidth / 2.0F;
        float centerY = totalH / 2.0F;

        layoutX = (charOffset - centerX) * baseScale * 0.05F;
        layoutY = (centerY - targetLine) * baseScale * 0.5F;

        // 根据朝向旋转
        float cos = (float) Math.cos(yRotRad);
        float sin = (float) Math.sin(yRotRad);
        float worldX = layoutX * cos;
        float worldZ = layoutX * sin;

        return new Vec3(getX() - worldX, getY() + layoutY, getZ() - worldZ);
    }

    // ======================== Getter ========================

    public int getMode() {
        return getEntityData().get(DATA_MODE);
    }

    public String getDisplayText() {
        return getEntityData().get(DATA_TEXT);
    }

    public float getBaseScale() {
        return getEntityData().get(DATA_BASE_SCALE);
    }

    public float getRoll() {
        return getEntityData().get(DATA_ROLL);
    }

    public int getVisibleCount() {
        return getEntityData().get(DATA_VISIBLE_COUNT);
    }

    public int getDroppedCount() {
        return getEntityData().get(DATA_DROPPED_COUNT);
    }

    public int getHoldTicks() {
        return getEntityData().get(DATA_HOLD_TICKS);
    }

    public int getShardState() {
        return getEntityData().get(DATA_SHARD_STATE);
    }

    public float getShrinkFactor() {
        return getEntityData().get(DATA_SHRINK_FACTOR);
    }

    public int getAlpha() {
        return getEntityData().get(DATA_ALPHA);
    }

    public int getRgb() {
        return getEntityData().get(DATA_RGB);
    }

    public List<GlyphSlot> getCachedGlyphs() {
        if (cachedGlyphs == null) {
            rebuildGlyphCache();
        }
        return cachedGlyphs;
    }

    public List<Float> getCachedCharWidths() {
        if (cachedCharWidths == null) {
            rebuildGlyphCache();
        }
        return cachedCharWidths;
    }

    public float getCachedTotalWidth() {
        return cachedTotalWidth;
    }

    public float getCachedTotalHeight() {
        return cachedTotalHeight;
    }

    /**
     * 计算单个字形的独立缩放动画（弹性过冲）。
     */
    public float getMainCharScale(int visibleIndex) {
        int visibleCount = getEntityData().get(DATA_VISIBLE_COUNT);
        if (visibleIndex >= visibleCount) {
            return 0.0F;
        }
        int charAge = tickCount - visibleIndex * POP_INTERVAL;
        if (charAge < 0) {
            return 0.0F;
        }
        if (charAge >= POP_DURATION) {
            return 1.0F;
        }
        float t = (float) charAge / POP_DURATION;
        float base = (float) Math.sin(t * Math.PI);
        return base + base * POP_OVERSHOOT;
    }

    // ======================== 辅助方法 ========================

    public boolean isOnGround() {
        return onGround() || verticalCollision;
    }

    // ======================== 物理 / 碰撞覆写 ========================

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean hurtServer(@NonNull ServerLevel level, @NonNull DamageSource damageSource, float amount) {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 256.0 * 256.0;
    }

    // ======================== NBT 持久化 ========================

    @Override
    protected void readAdditionalSaveData(@NonNull ValueInput input) {
        getEntityData().set(DATA_TEXT, input.getStringOr("text", ""));
        getEntityData().set(DATA_MODE, input.getIntOr("mode", MODE_MAIN));
        getEntityData().set(DATA_BASE_SCALE, input.getFloatOr("baseScale", 1.0F));
        getEntityData().set(DATA_VISIBLE_COUNT, input.getIntOr("visibleCount", 0));
        getEntityData().set(DATA_DROPPED_COUNT, input.getIntOr("droppedCount", 0));
        getEntityData().set(DATA_HOLD_TICKS, input.getIntOr("holdTicks", DEFAULT_HOLD_TICKS));
        getEntityData().set(DATA_SHARD_STATE, input.getIntOr("shardState", SHARD_FALLING));
        getEntityData().set(DATA_SHRINK_FACTOR, input.getFloatOr("shrinkFactor", 1.0F));
        getEntityData().set(DATA_ALPHA, input.getIntOr("alpha", 255));
        getEntityData().set(DATA_RGB, input.getIntOr("rgb", 0xFFFFFF));
        shardStateTimer = input.getIntOr("shardTimer", 0);
        shardStartTick = input.getIntOr("shardStartTick", 0);
        rebuildGlyphCache();
    }

    @Override
    protected void addAdditionalSaveData(@NonNull ValueOutput output) {
        output.putString("text", getEntityData().get(DATA_TEXT));
        output.putInt("mode", getEntityData().get(DATA_MODE));
        output.putFloat("baseScale", getEntityData().get(DATA_BASE_SCALE));
        output.putInt("visibleCount", getEntityData().get(DATA_VISIBLE_COUNT));
        output.putInt("droppedCount", getEntityData().get(DATA_DROPPED_COUNT));
        output.putInt("holdTicks", getEntityData().get(DATA_HOLD_TICKS));
        output.putInt("shardState", getEntityData().get(DATA_SHARD_STATE));
        output.putFloat("shrinkFactor", getEntityData().get(DATA_SHRINK_FACTOR));
        output.putInt("alpha", getEntityData().get(DATA_ALPHA));
        output.putInt("rgb", getEntityData().get(DATA_RGB));
        output.putInt("shardTimer", shardStateTimer);
        output.putInt("shardStartTick", shardStartTick);
    }
}
