# 从主世界传送到主城不需要执行
tellraw @s [{"text":"正在切换世界...","color":"gold","bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false}]
execute in youzaiworld:inventory_check run tp @s 0 100 0 0 0
data merge entity @s {respawn:{dimension:"youzaiworld:mian", pos:[I;0,120,0], yaw:0.0f, pitch:0.0f, forced:false}}
kill @s
tellraw @s [{"text":"成功来到 ","color":"green","bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false},{"text":"主城","color":"gold","bold":false,"italic":false,"underlined":false,"strikethrough":false,"obfuscated":false}]