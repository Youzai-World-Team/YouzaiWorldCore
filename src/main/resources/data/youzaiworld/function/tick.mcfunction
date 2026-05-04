tag @a remove keep_inventory
execute as @a[nbt={Inventory:[{id:"youzaiworldcore:heart_of_guardianship"}]}] run tag @s add keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=!keep_inventory] at @s run function youzaiworld:keep_inventory
#--------------------------------------------------------------------------------------------------------------------------------
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run clear @s youzaiworldcore:heart_of_guardianship 1
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run tellraw @s [{"text":"物品栏已保留，消耗 1 守护之心!"}]
#--------------------------------------------------------------------------------------------------------------------------------
scoreboard players reset @a youzaiworld.death





function youzaiworld:boss_kills
function youzaiworld:hostile_kills
function youzaiworld:neutral_kills
function youzaiworld:passive_kills