# 先传送到教程世界
# 在生产环境时需要替换为youzaiworld:tutorials
execute as @s in minecraft:overworld run tp @s 0 100 0 0 0

# 显示欢迎标题
title @s title [{"text":"你好，","color":"white"},{"selector":"@s","color":"gold"}]
title @s subtitle [{"text":"欢迎来到悠哉世界！","color":"yellow"}]
tellraw @s [{"translate":"youzaiworldcore.tellraw.format"},{"text":"欢迎你 ",color:"green"},{"entity":"@s",nbt:"name",color:"gold",bold:true},{"text":"，在正式开始之前，请先完成新手教程，了解服务器的基本玩法和特色功能！",color:"green"}]

# 播放点声音
playsound entity.player.levelup master @s
playsound ui.toast.challenge_complete master @s
playsound entity.firework_rocket.blast master @s
playsound entity.firework_rocket.large_blast master @s

# 延迟一下并执行下一部分，确保玩家有时间看到标题并传送完成
schedule function youzaiworldcore:beginner_tutorial/s0_p2 4s