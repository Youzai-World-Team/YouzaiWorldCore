package top.csituka.youzaiworldcore.respawn;

import net.minecraft.server.level.ServerLevel;

/** 允许在玩家选择普通重生后补做死亡时暂缓的装备掉落。 */
public interface DeferredDeathDropAccess {

    void youzaiworldcore$dropDeferredEquipment(ServerLevel level);
}
