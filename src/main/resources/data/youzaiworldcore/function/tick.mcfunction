# 守护之心逻辑
tag @a remove keep_inventory
execute as @a if items entity @s container.* youzaiworldcore:heart_of_guardianship run tag @s add keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=!keep_inventory] at @s run function youzaiworldcore:keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run clear @s youzaiworldcore:heart_of_guardianship 1
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run tellraw @s [{"text":"物品栏已保留，消耗 1 守护之心!"}]
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run advancement grant @s only youzaiworldcore:youzaiworld/used_heart_of_guardianship
scoreboard players reset @a youzaiworld.death

# 守护之心数量提醒
# 非破坏性统计所有容器槽位中的守护之心总数
execute as @a store result score @s youzaiworld.heart_count if items entity @s container.* youzaiworldcore:heart_of_guardianship
# 在数量降至特定阈值时发送警告（仅当该阈值尚未被警告过）
execute as @a[scores={youzaiworld.heart_count=10}] unless score @s youzaiworld.heart_warned = @s youzaiworld.heart_count run tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"translate":"youzaiworldcore.heart_of_guardianship.warning.10"}]
execute as @a[scores={youzaiworld.heart_count=5}] unless score @s youzaiworld.heart_warned = @s youzaiworld.heart_count run tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"translate":"youzaiworldcore.heart_of_guardianship.warning.5"}]
execute as @a[scores={youzaiworld.heart_count=3}] unless score @s youzaiworld.heart_warned = @s youzaiworld.heart_count run tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"translate":"youzaiworldcore.heart_of_guardianship.warning.3"}]
execute as @a[scores={youzaiworld.heart_count=2}] unless score @s youzaiworld.heart_warned = @s youzaiworld.heart_count run tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"translate":"youzaiworldcore.heart_of_guardianship.warning.2"}]
execute as @a[scores={youzaiworld.heart_count=1}] unless score @s youzaiworld.heart_warned = @s youzaiworld.heart_count run tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"translate":"youzaiworldcore.heart_of_guardianship.warning.1"}]
# 更新警告等级标记，防止下一 tick 重复警告
execute as @a[scores={youzaiworld.heart_count=10}] run scoreboard players set @s youzaiworld.heart_warned 10
execute as @a[scores={youzaiworld.heart_count=5}] run scoreboard players set @s youzaiworld.heart_warned 5
execute as @a[scores={youzaiworld.heart_count=3}] run scoreboard players set @s youzaiworld.heart_warned 3
execute as @a[scores={youzaiworld.heart_count=2}] run scoreboard players set @s youzaiworld.heart_warned 2
execute as @a[scores={youzaiworld.heart_count=1}] run scoreboard players set @s youzaiworld.heart_warned 1
# 数量为 0 或高于所有阈值时重置标志
execute as @a if score @s youzaiworld.heart_count matches 0 run scoreboard players set @s youzaiworld.heart_warned 0
execute as @a if score @s youzaiworld.heart_count matches 11.. run scoreboard players set @s youzaiworld.heart_warned 0

# 新手教程、区分玩家状态逻辑
# 检测重进玩家：leave_game > last_leave
execute as @a if score @s leave_game > @s last_leave run function youzaiworldcore:beginner_tutorial
# 更新 last_leave
execute as @a run scoreboard players operation @s last_leave = @s leave_game

# Debug
title @a actionbar [{"text":"[Debug]新手教程状态:"},{"score":{"name":"@s","objective":"beginner_tutorial_status"}}]