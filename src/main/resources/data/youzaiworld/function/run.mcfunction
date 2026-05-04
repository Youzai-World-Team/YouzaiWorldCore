summon minecraft:chest_minecart ~ ~ ~ {Tags:["youzaiworld.minecart","youzaiworld.minecart1"]}
summon minecraft:chest_minecart ~ ~ ~ {Tags:["youzaiworld.minecart","youzaiworld.minecart2"]}
execute run data modify entity @e[limit=1,tag=youzaiworld.minecart1] Items set from entity @s Inventory
item replace entity @e[tag=youzaiworld.minecart2] container.9 from entity @s container.27
item replace entity @e[tag=youzaiworld.minecart2] container.10 from entity @s container.28
item replace entity @e[tag=youzaiworld.minecart2] container.11 from entity @s container.29
item replace entity @e[tag=youzaiworld.minecart2] container.12 from entity @s container.30
item replace entity @e[tag=youzaiworld.minecart2] container.13 from entity @s container.31
item replace entity @e[tag=youzaiworld.minecart2] container.14 from entity @s container.32
item replace entity @e[tag=youzaiworld.minecart2] container.15 from entity @s container.33
item replace entity @e[tag=youzaiworld.minecart2] container.16 from entity @s container.34
item replace entity @e[tag=youzaiworld.minecart2] container.17 from entity @s container.35
item replace entity @e[tag=youzaiworld.minecart2] container.18 from entity @s armor.chest
item replace entity @e[tag=youzaiworld.minecart2] container.19 from entity @s armor.feet
item replace entity @e[tag=youzaiworld.minecart2] container.20 from entity @s armor.head
item replace entity @e[tag=youzaiworld.minecart2] container.21 from entity @s armor.legs
item replace entity @e[tag=youzaiworld.minecart2] container.22 from entity @s weapon.offhand
clear @s
kill @e[type=minecraft:chest_minecart,tag=youzaiworld.minecart]
kill @e[type=minecraft:item,nbt={Item:{components:{"minecraft:custom_data":{Tags:["youzaiworld.clear"]}}}}]