package top.csituka.youzaiworldcore.skill;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.util.UUID;

/**
 * 玩家属性加点数据模型。
 * 每个字段对应一项可加点属性，值表示已投入的点数。
 * <p>
 * 特殊约束：等级未满 20 级时，仅允许投入 damageResistance，
 * 其余属性槽位上锁不可加点。
 */
@SuppressWarnings("null")
public class PlayerAttributeData {

    public static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .create();

    public UUID uuid;
    public String username;

    /** 可用技能点（升级获得，加点消耗） */
    public int skillPointsAvailable;

    // ---- 9 项属性 ----
    public int maxHealth;               // +1 生命值 / 点
    public int healingAmplification;    // +1% 倍率 / 点
    public int miningSpeed;             // +1% 倍率 / 点
    public int movementSpeed;           // +1% 倍率 / 点
    public int jumpAmplitude;           // +1% 倍率 / 点
    public int luck;                    // +1 幸运等级 / 点
    public int meleeDamage;             // +2% 倍率 / 点
    public int rangedDamage;            // +2% 倍率 / 点
    public int damageResistance;        // +2% 减伤 / 点

    public PlayerAttributeData() {
    }

    public PlayerAttributeData(UUID uuid, String username) {
        this.uuid = uuid;
        this.username = username;
    }

    /** 固定顺序的属性列表，用于 UI 渲染 */
    public static final String[] ATTRIBUTE_KEYS = {
            "maxHealth", "healingAmplification", "miningSpeed",
            "movementSpeed", "jumpAmplitude", "luck",
            "meleeDamage", "rangedDamage", "damageResistance"
    };

    /**
     * 获取指定 key 的当前点数。
     */
    public int get(String key) {
        return switch (key) {
            case "maxHealth" -> maxHealth;
            case "healingAmplification" -> healingAmplification;
            case "miningSpeed" -> miningSpeed;
            case "movementSpeed" -> movementSpeed;
            case "jumpAmplitude" -> jumpAmplitude;
            case "luck" -> luck;
            case "meleeDamage" -> meleeDamage;
            case "rangedDamage" -> rangedDamage;
            case "damageResistance" -> damageResistance;
            default -> 0;
        };
    }

    /**
     * 设置指定 key 的点数。
     */
    public void set(String key, int value) {
        switch (key) {
            case "maxHealth" -> maxHealth = value;
            case "healingAmplification" -> healingAmplification = value;
            case "miningSpeed" -> miningSpeed = value;
            case "movementSpeed" -> movementSpeed = value;
            case "jumpAmplitude" -> jumpAmplitude = value;
            case "luck" -> luck = value;
            case "meleeDamage" -> meleeDamage = value;
            case "rangedDamage" -> rangedDamage = value;
            case "damageResistance" -> damageResistance = value;
        }
    }
}
