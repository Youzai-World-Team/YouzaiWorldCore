package top.csituka.youzaiworldcore.account.data;

import com.google.gson.*;
import com.google.gson.annotations.Expose;
import com.google.gson.annotations.SerializedName;

import java.lang.reflect.Type;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 玩家账户数据模型
 * 使用 Gson 序列化/反序列化，存储在 JSON 文件中
 */
public class PlayerAccount {

    public static final Gson GSON = new GsonBuilder()
            .excludeFieldsWithoutExposeAnnotation()
            .registerTypeAdapter(ZonedDateTime.class, new ZonedDateTimeAdapter())
            .setPrettyPrinting()
            .create();

    /** 玩家名称 */
    @Expose
    public String username;

    /** 小写玩家名（用于不区分大小写的查找） */
    @Expose
    @SerializedName("username_lower")
    public String usernameLowerCase;

    /** 玩家 UUID（字符串形式） */
    @Expose
    public String uuid;

    /** BCrypt 哈希后的密码（空串表示未注册） */
    @Expose
    public String password = "";

    /** 最后登录 IP */
    @Expose
    @SerializedName("last_ip")
    public String lastIp = "";

    /** 最后认证时间 */
    @Expose
    @SerializedName("last_authenticated_date")
    public ZonedDateTime lastAuthenticatedDate = EPOCH;

    /** 注册时间 */
    @Expose
    @SerializedName("registration_date")
    public ZonedDateTime registrationDate = EPOCH;

    /** 登录尝试次数 */
    @Expose
    @SerializedName("login_tries")
    public int loginTries = 0;

    /** 上次因登录失败被踢出的时间 */
    @Expose
    @SerializedName("last_kicked_date")
    public ZonedDateTime lastKickedDate = EPOCH;

    /** 最后一次玩家的位置 JSON（用于恢复位置） */
    @Expose
    @SerializedName("last_position")
    public String lastPositionJson;

    private static final ZonedDateTime EPOCH = ZonedDateTime.of(1970, 1, 1, 0, 0, 0, 0, java.time.ZoneOffset.UTC);

    public PlayerAccount() {
    }

    /**
     * 从数据库 JSON 数据反序列化
     */
    public PlayerAccount(String username, String usernameLowerCase, String uuid, String jsonData) {
        PlayerAccount parsed = GSON.fromJson(jsonData, PlayerAccount.class);
        this.username = username;
        this.usernameLowerCase = usernameLowerCase;
        this.uuid = uuid;

        if (parsed != null) {
            this.password = parsed.password != null ? parsed.password : "";
            this.lastIp = parsed.lastIp != null ? parsed.lastIp : "";
            this.lastAuthenticatedDate = parsed.lastAuthenticatedDate != null ? parsed.lastAuthenticatedDate : EPOCH;
            this.registrationDate = parsed.registrationDate != null ? parsed.registrationDate : EPOCH;
            this.loginTries = parsed.loginTries;
            this.lastKickedDate = parsed.lastKickedDate != null ? parsed.lastKickedDate : EPOCH;
            this.lastPositionJson = parsed.lastPositionJson;
        }
    }

    /**
     * 创建新账户（仅名称）
     */
    public PlayerAccount(String username) {
        this.username = username;
        this.usernameLowerCase = username.toLowerCase(java.util.Locale.ENGLISH);
    }

    /**
     * 创建新账户（名称 + UUID）
     */
    public PlayerAccount(String username, java.util.UUID uuid) {
        this(username);
        this.uuid = uuid != null ? uuid.toString() : null;
    }

    /**
     * 序列化为 JSON
     */
    public String toJson() {
        return GSON.toJson(this);
    }

    /**
     * 是否已注册（有密码）
     */
    public boolean isRegistered() {
        return password != null && !password.isEmpty();
    }

    /**
     * Gson 序列化器/反序列化器 for ZonedDateTime
     */
    private static class ZonedDateTimeAdapter implements JsonSerializer<ZonedDateTime>, JsonDeserializer<ZonedDateTime> {
        private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ISO_ZONED_DATE_TIME;

        @Override
        public JsonElement serialize(ZonedDateTime src, Type typeOfSrc, JsonSerializationContext context) {
            return new JsonPrimitive(src.format(FORMATTER));
        }

        @Override
        public ZonedDateTime deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
            return ZonedDateTime.parse(json.getAsString(), FORMATTER);
        }
    }
}
