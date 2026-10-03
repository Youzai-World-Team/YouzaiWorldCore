package top.csituka.youzaiworldcore.map;

import java.util.Map;

/** SQLite 设置的强类型访问，不再依赖全局 JSON 配置。 */
public final class MapSqlSettings {
    private final MapDatabase database;
    private final String scope;
    private final Map<String, String> values;
    public MapSqlSettings(MapDatabase database, String scope) {
        this.database = database; this.scope = scope; values = database.settings(scope);
    }
    public String getString(String key, String fallback) { return values.getOrDefault(key, fallback); }
    public boolean getBoolean(String key, boolean fallback) {
        String value = values.get(key);
        if (value == null) return fallback;
        if (!value.equals("true") && !value.equals("false")) throw invalid(key);
        return Boolean.parseBoolean(value);
    }
    public int getInt(String key, int fallback, int min, int max) {
        try { int value = Integer.parseInt(getString(key, Integer.toString(fallback))); if (value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        throw invalid(key);
    }
    public double getDouble(String key, double fallback, double min, double max) {
        try { double value = Double.parseDouble(getString(key, Double.toString(fallback))); if (Double.isFinite(value) && value >= min && value <= max) return value; }
        catch (NumberFormatException ignored) { }
        throw invalid(key);
    }
    public <T extends Enum<T>> T getEnum(String key, T fallback, Class<T> type) {
        try { return Enum.valueOf(type, getString(key, fallback.name())); }
        catch (IllegalArgumentException error) { throw invalid(key); }
    }
    public void set(String key, Object value) { values.put(key, value instanceof Enum<?> e ? e.name() : value.toString()); }
    public void save() { database.settings(scope, values); }
    private IllegalArgumentException invalid(String key) { return new IllegalArgumentException("地图 SQLite 设置无效：" + scope + "/" + key); }
}
