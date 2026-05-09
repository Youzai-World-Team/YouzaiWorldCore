# 定义记分板
scoreboard objectives add youzaiworld.death deathCount "被守护之心保留物品栏的次数"
scoreboard objectives add enter_number dummy "进入服务器次数"
scoreboard objectives add health health "生命值"
scoreboard objectives add beginner_tutorial_status dummy "新手教程状态"
# 显示生命值在玩家列表
scoreboard objectives setdisplay list health
# 设置游戏规则
gamerule keep_inventory true
gamerule respawn_radius 1
gamerule command_block_output false