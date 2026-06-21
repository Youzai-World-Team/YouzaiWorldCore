package top.csituka.youzaiworldcore.account;

/**
 * 登录结果枚举。
 * <p>
 * 用于 {@link AccountManager#login(UUID, String)} 的返回值，
 * 表示登录校验的执行结果。
 * </p>
 *
 * <p>判定逻辑：</p>
 * <ul>
 *   <li>{@link #SUCCESS} — 密码正确，登录成功</li>
 *   <li>{@link #NOT_REGISTERED} — UUID 未注册任何账户</li>
 *   <li>{@link #WRONG_PASSWORD} — 密码错误（连续失败 &lt; 3 次）</li>
 *   <li>{@link #KICK} — 连续 3 次错误，应踢出服务器</li>
 *   <li>{@link #BLOCKED} — 连续 5 次错误或被管理员阻止，禁止再登入</li>
 * </ul>
 */
public enum LoginResult {
    /** 登录成功 */
    SUCCESS,
    /** 未注册 */
    NOT_REGISTERED,
    /** 密码错误 */
    WRONG_PASSWORD,
    /** 连续 3 次错误，踢出服务器 */
    KICK,
    /** 连续 5 次错误或被阻止，禁止登入 */
    BLOCKED
}
