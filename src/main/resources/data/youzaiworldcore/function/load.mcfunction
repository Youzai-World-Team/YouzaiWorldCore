scoreboard objectives add youzaiworld.death deathCount
scoreboard objectives add enter_number dummy "进入服务器次数"
scoreboard objectives add health health "生命值"
scoreboard objectives setdisplay list health
gamerule keep_inventory true
scoreboard objectives add leave_game custom:leave_game
execute as @a unless score @s leave_game matches 0 run tag @s add veteran