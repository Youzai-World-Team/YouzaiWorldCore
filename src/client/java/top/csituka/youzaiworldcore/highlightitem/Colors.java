package top.csituka.youzaiworldcore.highlightitem;

import com.google.gson.JsonObject;
import net.minecraft.util.ARGB;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * 颜色工具类（参考 HighLightItem，适配 YouzaiWorldCore 26.2）。
 * <p>
 * 提供预设高亮色 {@link HighLightColor} 以及自定义 RGBA 颜色的 JSON 编解码。
 * RGBA 各分量范围 0..1（着色器约定），对外存储/比较时使用 0..255 的整数。
 */
public class Colors {
    private static final Logger LOGGER = LoggerFactory.getLogger("YouzaiWorldCore/HighlightItem");

    /** 默认高亮色对应的 ARGB 整数（与 {@link HighLightColor#DEFAULT} 一致）。 */
    public static int getDefaultColorARGB() {
        return HighLightColor.DEFAULT.colorInteger();
    }

    /** 从 JSON 读取自定义 RGBA（含 "custom" 标记）。 */
    public static float[] customFromJson(JsonObject json) {
        if (json.has("custom")) {
            try {
                return new float[]{
                        json.get("red").getAsFloat(),
                        json.get("green").getAsFloat(),
                        json.get("blue").getAsFloat(),
                        json.get("alpha").getAsFloat()
                };
            } catch (IllegalStateException | UnsupportedOperationException e) {
                LOGGER.error("[HighlightItem] 无法将 json 转换为 rgba 颜色，请勿手动修改配置文件！实际 json: {}", json, e);
                return HighLightColor.DEFAULT.getShaderColor();
            }
        } else {
            LOGGER.error("[HighlightItem] 无法将 json 转换为颜色，请勿手动修改配置文件！实际 json: {}", json);
        }
        return HighLightColor.DEFAULT.getShaderColor();
    }

    /** 将 RGBA（0..1）编码为带 "custom" 标记的 JSON。 */
    public static JsonObject customToJson(float[] rgba) {
        var json = new JsonObject();
        json.addProperty("custom", "");
        json.addProperty("red", rgba[0]);
        json.addProperty("green", rgba[1]);
        json.addProperty("blue", rgba[2]);
        json.addProperty("alpha", rgba[3]);
        return json;
    }

    /** 预设高亮色（半透明，叠在物品槽上形成“着色”效果）。 */
    public enum HighLightColor {
        DEFAULT(new float[]{1.0f, 1.0f, 1.0f, 0.45f}),
        BLUE(new float[]{0.5f, 1.0f, 1.0f, 0.45f}),
        YELLOW(new float[]{1.0f, 1.0f, 0.0f, 0.45f}),
        RED(new float[]{1.0f, 0.0f, 0.0f, 0.45f}),
        GREEN(new float[]{0.0f, 1.0f, 0.0f, 0.45f});

        private final float[] shaderColor;

        HighLightColor(float[] shaderColor) {
            this.shaderColor = shaderColor;
        }

        public float[] getShaderColor() {
            return shaderColor;
        }

        JsonObject json() {
            JsonObject json = new JsonObject();
            json.addProperty("default", name());
            return json;
        }

        public static HighLightColor fromJson(JsonObject json) {
            try {
                var name = json.get("default").getAsString();
                return HighLightColor.valueOf(name);
            } catch (IllegalStateException | UnsupportedOperationException e) {
                LOGGER.error("[HighlightItem] 无法将 json 转换为 HighLightColor，请勿手动修改配置文件！实际 json: {}", json, e);
                return DEFAULT;
            }
        }

        /** 将着色器 RGBA（0..1）转换为 ARGB 整数（0..255）。 */
        public int colorInteger() {
            return ARGB.color(
                    (int) (shaderColor[3] * 255f),
                    (int) (shaderColor[0] * 255f),
                    (int) (shaderColor[1] * 255f),
                    (int) (shaderColor[2] * 255f)
            );
        }
    }
}
