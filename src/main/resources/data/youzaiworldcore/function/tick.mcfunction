# 守护之心逻辑
tag @a remove keep_inventory
execute as @a[nbt={Inventory:[{id:"youzaiworldcore:heart_of_guardianship"}]}] run tag @s add keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=!keep_inventory] at @s run function youzaiworldcore:keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run clear @s youzaiworldcore:heart_of_guardianship 1
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run tellraw @s [{"text":"物品栏已保留，消耗 1 守护之心!"}]
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run advancement grant @s only youzaiworldcore:youzaiworld/used_heart_of_guardianship
scoreboard players reset @a youzaiworld.death