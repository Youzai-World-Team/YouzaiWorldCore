package top.csituka.youzaiworldcore.account.util;

import com.google.gson.Gson;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/**
 * 记录玩家最后的位置信息
 * 用于认证完成后恢复到原位置
 */
public class AuthLocationData {

    private static final Gson GSON = new Gson();

    /** 维度 */
    public ResourceKey<Level> dimension;

    /** 位置坐标 */
    public Vec3 position;

    /** 水平旋转角 (yaw) */
    public float yaw;

    /** 垂直旋转角 (pitch) */
    public float pitch;

    public AuthLocationData() {
    }

    public AuthLocationData(ResourceKey<Level> dimension, Vec3 position, float yaw, float pitch) {
        this.dimension = dimension;
        this.position = position;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    /** 序列化为 JSON 字符串（持久化存储用） */
    public String toJson() {
        return GSON.toJson(new Data(
                dimension != null ? dimension.identifier().toString() : "",
                position.x, position.y, position.z,
                yaw, pitch
        ));
    }

    /** 从 JSON 字符串反序列化 */
    public static AuthLocationData fromJson(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            Data d = GSON.fromJson(json, Data.class);
            if (d == null || d.dim.isEmpty()) return null;
            AuthLocationData loc = new AuthLocationData();
            loc.dimension = ResourceKey.create(Registries.DIMENSION, Identifier.parse(d.dim));
            loc.position = new Vec3(d.x, d.y, d.z);
            loc.yaw = d.yaw;
            loc.pitch = d.pitch;
            return loc;
        } catch (Exception e) {
            return null;
        }
    }

    /** 内部数据类，用于 Gson 序列化 */
    private record Data(String dim, double x, double y, double z, float yaw, float pitch) {}

    @Override
    public String toString() {
        return String.format("AuthLocationData{dimension=%s, position=%s, yaw=%s, pitch=%s}",
                dimension, position, yaw, pitch);
    }
}
