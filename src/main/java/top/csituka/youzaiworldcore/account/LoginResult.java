package top.csituka.youzaiworldcore.account;

/**
 * 登录结果枚举
 */
public enum LoginResult {
    /** 登录成功 */
    SUCCESS,
    /** 未注册 */
    NOT_REGISTERED,
    /** 密码错误 */
    WRONG_PASSWORD,
    /** 连续3次错误，踢出 */
    KICK,
    /** 连续5次错误或被阻止 */
    BLOCKED
}
