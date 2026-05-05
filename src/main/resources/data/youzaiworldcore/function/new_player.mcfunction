# 玩家第一次进入服务器逻辑
title @s title [{"text":"你好，","color":"white","bold":true},{"selector":"@s","color":"gold","bold":true},{"text":"！","color":"white","bold":true}]
title @s subtitle {"text":"欢迎来到悠哉世界！","color":"white","bold":true}
tellraw @s [{"text":"欢迎来到悠哉世界！这是一个充满冒险和乐趣的地方，祝你在这里玩得开心！","color":"yellow"}]
scoreboard players set @s enter_number 1
tellraw @s {translate:"resourcePack.testSuccess",fallback:"警告：服务器资源包未加载成功，可能会导致游戏出现问题（如物品丢失、方块消失等），请重新进入服务器以重试！",color:"red"}
tag @s add enter_again