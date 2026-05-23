# 完成所有教程之后每次进入服务器运行的函数


# 显示欢迎标题
title @s title [{"text":"欢迎回来！","color":"white"}]
title @s subtitle [{"selector":"@s","color":"gold"}]
tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"text":"欢迎回来！希望你在悠哉世界的冒险之旅继续充满乐趣！","color":"yellow"}]

# 播放点声音
playsound entity.player.levelup master @s