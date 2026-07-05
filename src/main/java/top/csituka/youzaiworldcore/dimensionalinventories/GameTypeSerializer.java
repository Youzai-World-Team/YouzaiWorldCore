package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.*;
import net.minecraft.world.level.GameType;

import java.lang.reflect.Type;

/**
 * Gson 自定义序列化器，用于在 JSON 中读写 GameType。
 */
public final class GameTypeSerializer implements JsonSerializer<GameType>, JsonDeserializer<GameType> {

    @Override
    public JsonElement serialize(GameType src, Type typeOfSrc, JsonSerializationContext context) {
        return new JsonPrimitive(src.getName());
    }

    @Override
    public GameType deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        String name = json.getAsString();
        GameType gameType = GameType.byName(name);
        if (gameType == null) {
            throw new JsonParseException("未知的游戏模式: " + name);
        }
        return gameType;
    }
}
