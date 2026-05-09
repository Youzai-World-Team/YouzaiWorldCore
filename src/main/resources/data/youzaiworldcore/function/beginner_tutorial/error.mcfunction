# 当 beginner_tutorial_status 值无效时执行
# 输出警告到日志（或者通知 OP）
tellraw @a[tag=op] [{"text":"[警告] 玩家 ","color":"red"},{"selector":"@s"},{"text":" 的新手教程状态异常: ","color":"red"},{"score":{"name":"@s","objective":"beginner_tutorial_status"},"color":"red"},{"text":"，已重置为 0","color":"red"}]

# 重置为默认状态 0
scoreboard players set @s beginner_tutorial_status 0

# 可选：也可以在这里通知玩家重新开始教程
tellraw @s [{"text":"系统检测到您的教程状态异常，已重置为初始状态。","color":"yellow"}]