package top.csituka.youzaiworldcore.account;

/**
 * 账户注册结果枚举。
 * <p>
 * 用于 {@link AccountManager#register(UUID, String, String)} 的返回值，
 * 表示注册操作的执行结果。
 * </p>
 */
public enum AccountRegisterResult {
    /** 注册成功 */
    SUCCESS,
    /** 该玩家（UUID）已经注册过了 */
    ALREADY_REGISTERED,
    /** 该玩家代号已被其他账户使用 */
    NAME_TAKEN
}
