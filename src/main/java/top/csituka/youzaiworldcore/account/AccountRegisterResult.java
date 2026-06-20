package top.csituka.youzaiworldcore.account;

/**
 * 账户注册结果枚举
 */
public enum AccountRegisterResult {
    /** 注册成功 */
    SUCCESS,
    /** 该玩家已经注册过了 */
    ALREADY_REGISTERED,
    /** 该玩家代号已被使用 */
    NAME_TAKEN
}
