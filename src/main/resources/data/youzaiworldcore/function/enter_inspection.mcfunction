# 初始化逻辑
tag @s[tag=!enter_again] add first_enter 

# 玩家再次进入服务器逻辑
scoreboard players reset @s player_enter
title @s[tag=enter_again,tag=!first_enter] title [{"text":"欢迎回来！","color":"white","bold":true}]
title @s[tag=enter_again,tag=!first_enter] subtitle {"selector":"@s","color":"gold","bold":true}
tellraw @s[tag=enter_again,tag=!first_enter] [{"text":"欢迎回来！希望你在悠哉世界的冒险之旅继续充满乐趣！","color":"yellow"}]

# 玩家第一次进入服务器逻辑
title @s[tag=first_enter,tag=!enter_again] title [{"text":"你好，","color":"white","bold":true},{"selector":"@s","color":"gold","bold":true},{"text":"！","color":"white","bold":true}]
title @s[tag=first_enter,tag=!enter_again] subtitle {"text":"欢迎来到悠哉世界！","color":"white","bold":true}
tellraw @s[tag=first_enter,tag=!enter_again] [{"text":"欢迎来到悠哉世界！这是一个充满冒险和乐趣的地方，祝你在这里玩得开心！","color":"yellow"}]
tag @s[tag=first_enter,tag=!enter_again] add enter_again
scoreboard players set @s enter_number 0

# 公共逻辑
scoreboard players add @s enter_number 1
tellraw @s {translate:"resourcePack.testSuccess",fallback:"警告：服务器资源包未加载成功，可能会导致游戏出现问题（如物品丢失、方块消失等），请重新进入服务器以重试！",color:"red"}