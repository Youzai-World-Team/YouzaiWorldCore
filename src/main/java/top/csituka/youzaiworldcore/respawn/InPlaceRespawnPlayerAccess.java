package top.csituka.youzaiworldcore.respawn;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

/** 由服务端玩家 Mixin 实现的原地重生临时状态访问接口。 */
public interface InPlaceRespawnPlayerAccess {

    boolean youzaiworldcore$isInPlaceRespawnEnabled();

    boolean youzaiworldcore$isInPlaceRespawnSelected();

    void youzaiworldcore$selectInPlaceRespawn(int requiredLevel);

    int youzaiworldcore$getInPlaceRespawnCost();

    int youzaiworldcore$getDeferredExperienceReward();

    ResourceKey<Level> youzaiworldcore$getDeathDimension();

    Vec3 youzaiworldcore$getDeathPosition();

    float youzaiworldcore$getDeathYaw();

    float youzaiworldcore$getDeathPitch();
}
