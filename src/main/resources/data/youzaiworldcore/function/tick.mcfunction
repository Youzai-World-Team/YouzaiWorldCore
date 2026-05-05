# 守护之心逻辑
tag @a remove keep_inventory
execute as @a[nbt={Inventory:[{id:"youzaiworldcore:heart_of_guardianship"}]}] run tag @s add keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=!keep_inventory] at @s run function youzaiworldcore:keep_inventory
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run clear @s youzaiworldcore:heart_of_guardianship 1
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run tellraw @s [{"text":"物品栏已保留，消耗 1 守护之心!"}]
execute as @a[scores={youzaiworld.death=1..},tag=keep_inventory] run advancement grant @s only youzaiworldcore:youzaiworld/used_heart_of_guardianship
scoreboard players reset @a youzaiworld.death

# 对所有在线且没有会话标签的玩家，执行进服函数
execute as @a[tag=!youzaiworld.joined_this_session] run function youzaiworldcore:enter_inspection

# 执行完后立即给这些玩家打上标签（注意：上面的函数执行完后，这里的标签才会添加）
tag @a[tag=!youzaiworld.joined_this_session] add youzaiworld.joined_this_session