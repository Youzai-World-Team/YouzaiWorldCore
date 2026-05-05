# 玩家再次进入服务器逻辑
title @s[tag=enter_again,tag=!first_enter] title [{"text":"欢迎回来！","color":"white","bold":true}]
title @s[tag=enter_again,tag=!first_enter] subtitle {"selector":"@s","color":"gold","bold":true}
tellraw @s[tag=enter_again,tag=!first_enter] [{"text":"欢迎回来！希望你在悠哉世界的冒险之旅继续充满乐趣！","color":"yellow"}]
scoreboard players add @s enter_number 1
tellraw @s {translate:"resourcePack.testSuccess",fallback:"警告：服务器资源包未加载成功，可能会导致游戏出现问题（如物品丢失、方块消失等），请重新进入服务器以重试！",color:"red"}