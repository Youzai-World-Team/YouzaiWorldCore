# 定义记分板
scoreboard objectives add youzaiworld.death deathCount "被守护之心保留物品栏的次数"
scoreboard objectives add enter_number dummy "进入服务器次数"
scoreboard objectives add health health "生命值"
scoreboard objectives add beginner_tutorial_status dummy "新手教程状态"
scoreboard objectives add leave_game minecraft.custom:minecraft.leave_game "离开游戏次数"
scoreboard objectives add last_leave dummy "上次离开游戏数量"
scoreboard objectives add triggered_session dummy "本次会话是否已触发过教程函数"
scoreboard objectives add first_triggered dummy "是否已触发过首次进入教程"

# 显示生命值在玩家列表
scoreboard objectives setdisplay list health

# 设置游戏规则
gamerule keep_inventory true
gamerule respawn_radius 1
gamerule command_block_output false

# 初始化在线玩家
execute as @a unless score @s beginner_tutorial_status = @s beginner_tutorial_status run scoreboard players set @s beginner_tutorial_status 0
execute as @a run scoreboard players operation @s last_leave = @s leave_game
execute as @a unless score @s first_triggered = @s first_triggered run scoreboard players set @s first_triggered 0

# 对于 status=0 且从未触发过首次的玩家，立即调用教程函数
execute as @a[scores={beginner_tutorial_status=0,first_triggered=0}] run function youzaiworldcore:on_first_join