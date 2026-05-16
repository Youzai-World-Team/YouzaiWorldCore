# 更新 last_leave，防止重复触发
scoreboard players operation @s last_leave = @s leave_game

# 调用主教程函数
function youzaiworldcore:beginner_tutorial