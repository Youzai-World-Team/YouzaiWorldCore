# 测试用代码，正式版删除
tellraw @s [{"text":"✦ ","color":"gray"},{"text":"ᴍ","color":"dark_red"},{"text":"ɪ","color":"red"},{"text":"ɴ","color":"gold"},{"text":"ᴇ","color":"yellow"},{"text":"ᴄ","color":"green"},{"text":"ʀ","color":"aqua"},{"text":"ᴀ","color":"dark_aqua"},{"text":"ғ","color":"light_purple"},{"text":"ᴛ","color":"light_purple","underlined":true},{"text":" - ","color":"gray"},{"text":"悠哉世界","color":"yellow"},{"text":" ✦ ","color":"gray"},{"text":"YouzaiWorldCore Message Test","color":"white"}]
tellraw @s "§r§7✦ §4ᴍ§cɪ§6ɴ§eᴇ§aᴄ§bʀ§3ᴀ§dғ§uᴛ §7- §e悠哉世界 §7✦ §r服务器消息样式测试"

# 公共部分
scoreboard players set @s triggered_session 0
tellraw @s [{"translate":"resourcePack.serverTest",fallback:"§r§7✦ §4ᴍ§cɪ§6ɴ§eᴇ§aᴄ§bʀ§3ᴀ§dғ§uᴛ §7- §e悠哉世界 §7✦ §4警告：服务器资源包未加载成功，可能会导致游戏出现问题（如物品丢失、方块消失等），请重新进入服务器以重试！"}]

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
