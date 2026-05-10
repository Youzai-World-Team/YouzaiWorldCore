# 当 beginner_tutorial_status 值无效时执行
# 输出警告到日志、通知允许接收消息的管理
tellraw @a[scores={receive_YZWC_messages=1}] [{"translate":"youzaiworldcore.tellraw.format"},{"text":"[警告]玩家 ","color":"red"},{"selector":"@s"},{"text":" 的新手教程状态异常: ","color":"red"},{"score":{"name":"@s","objective":"beginner_tutorial_status"},"color":"red"},{"text":"，已重置为 0","color":"red"}]
scoreboard players set @s beginner_tutorial_status 0

# 通知玩家重新开始教程
tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"text":"系统检测到您的教程状态异常，已重置为初始状态。下一次进入服务器时，您将会被重新开始教程！","color":"red"}]
tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"text":"如果您认为这是个错误，请联系管理员。","color":"red"}]