scoreboard objectives add youzaiworld.death deathCount
scoreboard objectives add player_enter minecraft.custom:minecraft.leave_game
scoreboard objectives add enter_number dummy "进入服务器次数"
scoreboard objectives add health health "生命值"
scoreboard objectives add has_sword_flag dummy "记录上一 tick 是否持有剑"
scoreboard objectives add has_sword_run dummy "临时存储当前检测结果"
gamerule keep_inventory true