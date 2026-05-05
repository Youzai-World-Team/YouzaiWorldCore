# 检测当前玩家背包中是否有 youzaiworldcore:yz_sword
execute store success score @s has_sword_run if items entity @s inventory.* youzaiworldcore:yz_sword

# 如果上一刻有剑，此刻无剑 -> 触发丢剑事件
execute if score @s has_sword_flag matches 1 if score @s has_sword_run matches 0 run function youzaiworldcore:on_sword_lost

# 更新 flag 为当前状态
execute store result score @s has_sword_flag run scoreboard players get @s has_sword_run