tag @a remove youzaiworld
execute as @a[nbt={Inventory:[{components:{"minecraft:custom_data":{Tags:["keep"]}}}]}] run tag @s add youzaiworld
execute as @a[scores={youzaiworld.death=1..},tag=!youzaiworld] at @s run function youzaiworld:run
#--------------------------------------------------------------------------------------------------------------------------------
execute as @a[scores={youzaiworld.death=1..},tag=youzaiworld] run clear @s minecraft:heart_of_the_sea[minecraft:custom_data={Tags:["keep"]}] 1
execute as @a[scores={youzaiworld.death=1..},tag=youzaiworld] run tellraw @s [{"text":"物品栏已保留，消耗 1 守护之心!"}]
#--------------------------------------------------------------------------------------------------------------------------------
scoreboard players reset @a youzaiworld.death





function youzaiworld:boss_kills
function youzaiworld:hostile_kills
function youzaiworld:neutral_kills
function youzaiworld:passive_kills