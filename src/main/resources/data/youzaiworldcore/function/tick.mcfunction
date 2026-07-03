# 新手教程、区分玩家状态逻辑
# 检测重进玩家：leave_game > last_leave
#execute as @a if score @s leave_game > @s last_leave run function youzaiworldcore:beginner_tutorial
# 更新 last_leave
#execute as @a run scoreboard players operation @s last_leave = @s leave_game
