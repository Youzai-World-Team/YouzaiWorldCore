package top.csituka.youzaiworldcore.client.skill;

import top.csituka.youzaiworldcore.network.AttributeSyncPayload;

/**
 * 客户端缓存的属性加点数据（由 AttributeSyncPayload 更新）。
 */
public class ClientAttributeData {

    private static int skillPointsAvailable;
    private static int maxHealth;
    private static int healingAmplification;
    private static int miningSpeed;
    private static int movementSpeed;
    private static int jumpAmplitude;
    private static int luck;
    private static int meleeDamage;
    private static int rangedDamage;
    private static int damageResistance;
    private static int playerLevel;

    public static void update(AttributeSyncPayload p) {
        skillPointsAvailable = p.skillPointsAvailable();
        maxHealth = p.maxHealth();
        healingAmplification = p.healingAmplification();
        miningSpeed = p.miningSpeed();
        movementSpeed = p.movementSpeed();
        jumpAmplitude = p.jumpAmplitude();
        luck = p.luck();
        meleeDamage = p.meleeDamage();
        rangedDamage = p.rangedDamage();
        damageResistance = p.damageResistance();
        playerLevel = p.playerLevel();
    }

    // ---- Getters ----

    public static int getSkillPointsAvailable() { return skillPointsAvailable; }
    public static int getMaxHealth() { return maxHealth; }
    public static int getHealingAmplification() { return healingAmplification; }
    public static int getMiningSpeed() { return miningSpeed; }
    public static int getMovementSpeed() { return movementSpeed; }
    public static int getJumpAmplitude() { return jumpAmplitude; }
    public static int getLuck() { return luck; }
    public static int getMeleeDamage() { return meleeDamage; }
    public static int getRangedDamage() { return rangedDamage; }
    public static int getDamageResistance() { return damageResistance; }
    public static int getPlayerLevel() { return playerLevel; }

    /** 根据属性 key 获取点数 */
    public static int get(String key) {
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
}
