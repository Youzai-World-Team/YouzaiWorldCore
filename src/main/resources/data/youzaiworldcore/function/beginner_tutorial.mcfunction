# 公共部分
scoreboard players add @s enter_number 1
tellraw @s [{"translate":"yzw.r.test.tellraw","fallback":"警告：服务器资源包未加载成功，可能会导致游戏出现问题（如物品丢失、方块消失等），请重新进入服务器以重试！",color:"red"}]
tellraw @s [{"translate":"youzaiworldcore.tellraw.format.indev.warning"},{"text":"正在使用 Indev 版本的功能，可能会存在不稳定和未完成的内容，请谨慎使用！",color:"yellow"}]
scoreboard players list @s 

# 根据 beginner_tutorial_status 的值调用对应的子函数
# 值范围：0 ~ 11
execute if score @s beginner_tutorial_status matches 0 run function youzaiworldcore:beginner_tutorial/s0
execute if score @s beginner_tutorial_status matches 1 run function youzaiworldcore:beginner_tutorial/s1
execute if score @s beginner_tutorial_status matches 2 run function youzaiworldcore:beginner_tutorial/s2
execute if score @s beginner_tutorial_status matches 3 run function youzaiworldcore:beginner_tutorial/s3
execute if score @s beginner_tutorial_status matches 4 run function youzaiworldcore:beginner_tutorial/s4
execute if score @s beginner_tutorial_status matches 5 run function youzaiworldcore:beginner_tutorial/s5
execute if score @s beginner_tutorial_status matches 6 run function youzaiworldcore:beginner_tutorial/s6
execute if score @s beginner_tutorial_status matches 7 run function youzaiworldcore:beginner_tutorial/s7
execute if score @s beginner_tutorial_status matches 8 run function youzaiworldcore:beginner_tutorial/s8
execute if score @s beginner_tutorial_status matches 9 run function youzaiworldcore:beginner_tutorial/s9
execute if score @s beginner_tutorial_status matches 10 run function youzaiworldcore:beginner_tutorial/s10
execute if score @s beginner_tutorial_status matches 11 run function youzaiworldcore:beginner_tutorial/s11

# 兜底：检查 beginner_tutorial_status 是否在 0~11 范围内
# 如果不在，执行错误处理并重置为 0
execute unless score @s beginner_tutorial_status matches 0..11 run function youzaiworldcore:beginner_tutorial/error
