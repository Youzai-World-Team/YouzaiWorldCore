scoreboard objectives add ender_dragon.kills minecraft.killed:minecraft.ender_dragon
#scoreboard objectives setdisplay sidebar ender_dragon.kills
advancement grant @p[scores={ender_dragon.kills=1..}] only youzaiworld:boss/ender_dragon/ender_dragon_kill


scoreboard objectives add wither.kills minecraft.killed:minecraft.wither
#scoreboard objectives setdisplay sidebar wither.kills
advancement grant @p[scores={wither.kills=1..}] only youzaiworld:boss/wither/wither_kill

