package top.csituka.youzaiworldcore.client.inventory;

import net.minecraft.world.inventory.Slot;

/** 原版创造界面向共用容器渲染入口暴露分组格子；普通容器不实现此接口。 */
public interface CreativeItemGroupScreen {

    /** 返回实际创造展示槽位对应的分组条目，非展示槽位或越界时返回 null。 */
    CreativeItemGroups.Entry youzaiworldcore$getGroupEntry(Slot slot);
}
