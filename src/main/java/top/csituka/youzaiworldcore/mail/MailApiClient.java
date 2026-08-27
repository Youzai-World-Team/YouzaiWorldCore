package top.csituka.youzaiworldcore.mail;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import top.csituka.youzaiworldcore.api.ApiHttp;
import top.csituka.youzaiworldcore.network.MailStreamCodecs;
import top.csituka.youzaiworldcore.util.DebugLogger;

import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 邮件系统 Api 客户端：邮件正文与每玩家收件箱由 Api 服务端（SQLite）权威保存，
 * 模组不再往 {@code yzwc/server/data/mail_module/} 写任何邮件数据。
 * <p>
 * 接口一览（全部经 {@link ApiHttp} 做 HMAC-SHA256 签名）：
 * </p>
 * <ul>
 *   <li>{@code GET  /api/game/mail/inbox}   —— 玩家收件箱 + 未读数</li>
 *   <li>{@code GET  /api/game/mail/unread}  —— 单人未读数</li>
 *   <li>{@code POST /api/game/mail/unread}  —— 批量未读数（群发后刷新在线玩家徽标）</li>
 *   <li>{@code GET  /api/game/mail/sent}    —— 已发送邮件摘要</li>
 *   <li>{@code GET  /api/game/mail/detail}  —— 单封详情 + 编辑前置判定</li>
 *   <li>{@code POST /api/game/mail}         —— 发布</li>
 *   <li>{@code PATCH /api/game/mail}        —— 编辑（含接收范围 diff）</li>
 *   <li>{@code DELETE /api/game/mail}       —— 撤回</li>
 *   <li>{@code POST /api/game/mail/hidden}  —— 编辑期间隐藏 / 恢复</li>
 *   <li>{@code POST /api/game/mail/action}  —— 已读 / 星标 / 取消星标 / 删除</li>
 *   <li>{@code POST /api/game/mail/claim}   —— 原子领取（Api 记账，模组发放）</li>
 *   <li>{@code POST /api/game/mail/purge}   —— 过期清理</li>
 *   <li>{@code DELETE /api/game/mail/box}   —— 注销时清空收件箱</li>
 * </ul>
 * <p>
 * 所有方法都是<b>阻塞</b>的 HTTP 调用，必须在异步线程里执行，
 * 处理结果时再通过 {@code server.execute(...)} 回到主线程（与账户系统一致）。
 * 任何失败都以 {@code success=false} 返回，不抛异常。
 * </p>
 */
@SuppressWarnings("null")
public final class MailApiClient {

    private static final String MODULE = "MailApiClient";
    private static final String BASE = "/api/game/mail";
    /** 与 Api 端 unread / refs 批量接口的上限保持一致。 */
    private static final int BATCH_LIMIT = 500;

    private MailApiClient() {
    }

    // ========================================================================
    // 返回类型
    // ========================================================================

    /** 收件箱：条目已排除隐藏中的邮件，未读数为 Api 权威值。 */
    public record InboxResult(boolean success, int statusCode, String message,
            List<MailStreamCodecs.MailRefAndMail> entries, int unread) {
    }

    public record UnreadResult(boolean success, int statusCode, String message, int unread) {
    }

    public record SentListResult(boolean success, int statusCode, String message,
            List<MailStreamCodecs.MailSummary> summaries) {
    }

    /** 编辑预填：{@code ref} 为查看者自己的引用，可能为 null。 */
    public record DetailResult(boolean success, int statusCode, String message, Mail mail, MailRef ref,
            boolean canEdit, boolean needHidden, String denyReason) {
    }

    public record SendResult(boolean success, int statusCode, String message, Mail mail, List<UUID> recipients) {
    }

    /** 编辑结果：{@code recipients} 为编辑后的收件人，{@code removed} 为被移出范围的收件人。 */
    public record EditResult(boolean success, int statusCode, String message, Mail mail,
            List<UUID> recipients, List<UUID> removed) {
    }

    public record RecallResult(boolean success, int statusCode, String message, List<UUID> recipients) {
    }

    public record HiddenResult(boolean success, int statusCode, String message, Mail mail, List<UUID> recipients) {
    }

    /** 玩家侧状态变更：删除操作后 {@code ref} 为 null。 */
    public record ActionResult(boolean success, int statusCode, String message, MailRef ref, int unread) {
    }

    public record ClaimResult(boolean success, int statusCode, String message, Mail mail, MailRef ref, int unread) {
    }

    public record PurgeResult(boolean success, int statusCode, String message, int removed, int prunedRefs,
            List<UUID> affected) {
    }

    // ========================================================================
    // 读取
    // ========================================================================

    /**
     * 拉取玩家收件箱。
     *
     * @param playerUuid 玩家 UUID
     * @return 收件箱条目与未读数；Api 不可达时 success=false
     */
    public static InboxResult fetchInbox(UUID playerUuid) {
        DebugLogger.entering(MODULE, "fetchInbox", "playerUuid=" + playerUuid);
        boolean keepStarred = MailSettings.get().isKeepStarredAfterExpire();
        HttpResponse<String> response = ApiHttp.request("GET",
                BASE + "/inbox?uuid=" + ApiHttp.encode(playerUuid.toString())
                        + "&keep_starred=" + keepStarred, null);
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "fetchInbox", "failed");
            return new InboxResult(false, status(response), message(response, root), List.of(), 0);
        }
        List<MailStreamCodecs.MailRefAndMail> entries = new ArrayList<>();
        for (JsonElement element : array(root, "entries")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject entry = element.getAsJsonObject();
            Mail mail = mailFromJson(object(entry, "mail"));
            MailRef ref = refFromJson(object(entry, "ref"));
            if (mail != null && ref != null) {
                entries.add(new MailStreamCodecs.MailRefAndMail(ref, mail));
            }
        }
        int unread = ApiHttp.intValue(root, "unread", 0);
        DebugLogger.exiting(MODULE, "fetchInbox", "entries=" + entries.size() + ", unread=" + unread);
        return new InboxResult(true, status(response), "", entries, unread);
    }

    /** 单个玩家的未读邮件数。 */
    public static UnreadResult fetchUnread(UUID playerUuid) {
        HttpResponse<String> response = ApiHttp.request("GET",
                BASE + "/unread?uuid=" + ApiHttp.encode(playerUuid.toString()), null);
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            return new UnreadResult(false, status(response), message(response, root), 0);
        }
        return new UnreadResult(true, status(response), "", ApiHttp.intValue(root, "unread", 0));
    }

    /**
     * 批量查询未读数。群发 / 撤回 / 清理之后要给所有在线收件人刷新徽标，
     * 逐人请求会打出几十次 HTTP，这里合成一次。
     *
     * @param playerUuids 待查询玩家
     * @return UUID → 未读数；失败时返回空表
     */
    public static Map<UUID, Integer> fetchUnreadBatch(Collection<UUID> playerUuids) {
        if (playerUuids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, Integer> counts = new HashMap<>();
        for (List<UUID> chunk : chunk(playerUuids)) {
            JsonObject request = new JsonObject();
            request.add("uuids", uuidArray(chunk));
            HttpResponse<String> response = ApiHttp.request("POST", BASE + "/unread", request.toString());
            JsonObject root = body(response);
            if (!ApiHttp.successful(response)) {
                DebugLogger.warn(MODULE, "批量查询未读数失败：%s", message(response, root));
                continue;
            }
            JsonObject raw = object(root, "counts");
            if (raw == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
                UUID uuid = parseUuid(entry.getKey());
                if (uuid == null) {
                    continue;
                }
                try {
                    counts.put(uuid, entry.getValue().getAsInt());
                } catch (RuntimeException ignored) {
                    // 单个计数格式异常时跳过，其余玩家仍然刷新。
                }
            }
        }
        return counts;
    }

    /**
     * 批量取某封邮件在指定玩家处的引用。
     * <p>编辑 / 取消编辑之后要给每个在线收件人推送带自身读、星标、领取状态的条目。</p>
     *
     * @param mailId      邮件 ID
     * @param playerUuids 待查询玩家
     * @return UUID → 引用；失败或无引用时返回空表
     */
    public static Map<UUID, MailRef> fetchRefs(UUID mailId, Collection<UUID> playerUuids) {
        if (playerUuids.isEmpty()) {
            return Map.of();
        }
        Map<UUID, MailRef> refs = new HashMap<>();
        for (List<UUID> group : chunk(playerUuids)) {
            JsonObject request = new JsonObject();
            request.addProperty("mail_id", mailId.toString());
            request.add("uuids", uuidArray(group));
            HttpResponse<String> response = ApiHttp.request("POST", BASE + "/refs", request.toString());
            JsonObject root = body(response);
            if (!ApiHttp.successful(response)) {
                DebugLogger.warn(MODULE, "批量查询邮件引用失败：%s", message(response, root));
                continue;
            }
            JsonObject raw = object(root, "refs");
            if (raw == null) {
                continue;
            }
            for (Map.Entry<String, JsonElement> entry : raw.entrySet()) {
                UUID uuid = parseUuid(entry.getKey());
                MailRef ref = entry.getValue().isJsonObject()
                        ? refFromJson(entry.getValue().getAsJsonObject())
                        : null;
                if (uuid != null && ref != null) {
                    refs.put(uuid, ref);
                }
            }
        }
        return refs;
    }

    /** 已发送邮件摘要列表（不含正文与附件）。 */
    public static SentListResult fetchSentList() {
        DebugLogger.entering(MODULE, "fetchSentList");
        HttpResponse<String> response = ApiHttp.request("GET", BASE + "/sent", null);
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "fetchSentList", "failed");
            return new SentListResult(false, status(response), message(response, root), List.of());
        }
        List<MailStreamCodecs.MailSummary> summaries = new ArrayList<>();
        for (JsonElement element : array(root, "mails")) {
            if (!element.isJsonObject()) {
                continue;
            }
            JsonObject json = element.getAsJsonObject();
            UUID id = parseUuid(ApiHttp.stringValue(json, "id"));
            if (id == null) {
                continue;
            }
            summaries.add(new MailStreamCodecs.MailSummary(
                    id,
                    parseMailType(ApiHttp.stringValue(json, "type")),
                    ApiHttp.stringValue(json, "title"),
                    ApiHttp.stringValue(json, "scope_summary"),
                    ApiHttp.longValue(json, "created_time", 0L),
                    optionalLong(json, "expire_time"),
                    ApiHttp.stringValue(json, "sender")));
        }
        DebugLogger.exiting(MODULE, "fetchSentList", "summaries=" + summaries.size());
        return new SentListResult(true, status(response), "", summaries);
    }

    /**
     * 拉取单封邮件详情与编辑前置判定。
     *
     * @param mailId 邮件 ID
     * @param viewer 查看者（用于带回其自己的读/星标/领取状态），可为 null
     */
    public static DetailResult fetchDetail(UUID mailId, UUID viewer) {
        DebugLogger.entering(MODULE, "fetchDetail", "mailId=" + mailId);
        String path = BASE + "/detail?id=" + ApiHttp.encode(mailId.toString())
                + (viewer == null ? "" : "&viewer=" + ApiHttp.encode(viewer.toString()));
        HttpResponse<String> response = ApiHttp.request("GET", path, null);
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "fetchDetail", "failed");
            return new DetailResult(false, status(response), message(response, root),
                    null, null, false, false, "");
        }
        Mail mail = mailFromJson(object(root, "mail"));
        if (mail == null) {
            return new DetailResult(false, status(response), "Api 邮件数据格式无效",
                    null, null, false, false, "");
        }
        DebugLogger.exiting(MODULE, "fetchDetail", "canEdit=" + ApiHttp.booleanValue(root, "can_edit", false));
        return new DetailResult(true, status(response), "", mail, refFromJson(object(root, "ref")),
                ApiHttp.booleanValue(root, "can_edit", false),
                ApiHttp.booleanValue(root, "need_hidden", false),
                ApiHttp.stringValue(root, "deny_reason"));
    }

    // ========================================================================
    // 写入
    // ========================================================================

    /**
     * 发布邮件。
     *
     * @param recipients 由 {@link MailManager#resolveTargets} 解析出的收件人（NONADMIN / ROLE 需要 LuckPerms）
     */
    public static SendResult send(String sender, MailType type, List<TargetSpec> targets, String scopeSummary,
            String title, String body, Long expireTime, List<MailAttachment> attachments,
            Collection<UUID> recipients) {
        DebugLogger.entering(MODULE, "send", "sender=" + sender + ", title=" + title
                + ", recipients=" + recipients.size());
        JsonObject request = mailFields(type, sender, targets, scopeSummary, title, body, expireTime, attachments);
        request.add("recipients", uuidArray(recipients));
        HttpResponse<String> response = ApiHttp.request("POST", BASE, request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "send", "failed");
            return new SendResult(false, status(response), message(response, root), null, List.of());
        }
        Mail mail = mailFromJson(object(root, "mail"));
        if (mail == null) {
            return new SendResult(false, status(response), "Api 邮件数据格式无效", null, List.of());
        }
        DebugLogger.exiting(MODULE, "send", "mailId=" + mail.getId());
        return new SendResult(true, status(response), "", mail, uuidList(root, "recipients"));
    }

    /**
     * 编辑已发布邮件；Api 会按新收件人列表 diff 收件箱引用。
     *
     * @param hidden 是否同时改写隐藏标记，null 表示保持不变
     */
    public static EditResult edit(UUID mailId, String sender, MailType type, List<TargetSpec> targets,
            String scopeSummary, String title, String body, Long expireTime,
            List<MailAttachment> attachments, Collection<UUID> recipients, Boolean hidden) {
        DebugLogger.entering(MODULE, "edit", "mailId=" + mailId + ", recipients=" + recipients.size());
        JsonObject request = mailFields(type, sender, targets, scopeSummary, title, body, expireTime, attachments);
        request.addProperty("id", mailId.toString());
        request.add("recipients", uuidArray(recipients));
        if (hidden != null) {
            request.addProperty("hidden", hidden);
        }
        HttpResponse<String> response = ApiHttp.request("PATCH", BASE, request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "edit", "failed");
            return new EditResult(false, status(response), message(response, root), null, List.of(), List.of());
        }
        DebugLogger.exiting(MODULE, "edit", "success");
        return new EditResult(true, status(response), "", mailFromJson(object(root, "mail")),
                uuidList(root, "recipients"), uuidList(root, "removed"));
    }

    /** 撤回邮件；返回原收件人以便向在线玩家推送移除。 */
    public static RecallResult recall(UUID mailId) {
        DebugLogger.entering(MODULE, "recall", "mailId=" + mailId);
        HttpResponse<String> response = ApiHttp.request("DELETE",
                BASE + "?id=" + ApiHttp.encode(mailId.toString()), null);
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "recall", "failed");
            return new RecallResult(false, status(response), message(response, root), List.of());
        }
        boolean removed = ApiHttp.booleanValue(root, "ok", false);
        DebugLogger.exiting(MODULE, "recall", "removed=" + removed);
        return new RecallResult(removed, status(response),
                removed ? "" : "邮件不存在或已撤回", uuidList(root, "recipients"));
    }

    /** 编辑期间隐藏 / 恢复邮件。 */
    public static HiddenResult setHidden(UUID mailId, boolean hidden) {
        DebugLogger.entering(MODULE, "setHidden", "mailId=" + mailId + ", hidden=" + hidden);
        JsonObject request = new JsonObject();
        request.addProperty("id", mailId.toString());
        request.addProperty("hidden", hidden);
        HttpResponse<String> response = ApiHttp.request("POST", BASE + "/hidden", request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "setHidden", "failed");
            return new HiddenResult(false, status(response), message(response, root), null, List.of());
        }
        DebugLogger.exiting(MODULE, "setHidden", "success");
        return new HiddenResult(true, status(response), "", mailFromJson(object(root, "mail")),
                uuidList(root, "recipients"));
    }

    /**
     * 玩家侧状态变更。
     *
     * @param action {@code read} / {@code star} / {@code unstar} / {@code delete}
     */
    public static ActionResult action(UUID playerUuid, UUID mailId, String action) {
        DebugLogger.entering(MODULE, "action", "playerUuid=" + playerUuid + ", mailId=" + mailId
                + ", action=" + action);
        JsonObject request = new JsonObject();
        request.addProperty("uuid", playerUuid.toString());
        request.addProperty("mail_id", mailId.toString());
        request.addProperty("action", action);
        HttpResponse<String> response = ApiHttp.request("POST", BASE + "/action", request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "action", "failed");
            return new ActionResult(false, status(response), message(response, root), null, 0);
        }
        DebugLogger.exiting(MODULE, "action", "success");
        return new ActionResult(true, status(response), "", refFromJson(object(root, "ref")),
                ApiHttp.intValue(root, "unread", 0));
    }

    /**
     * 原子领取奖励：Api 先记账再返回附件，随后由
     * {@link MailManager#applyAttachments} 在主线程实际发放。
     */
    public static ClaimResult claim(UUID playerUuid, UUID mailId) {
        DebugLogger.entering(MODULE, "claim", "playerUuid=" + playerUuid + ", mailId=" + mailId);
        JsonObject request = new JsonObject();
        request.addProperty("uuid", playerUuid.toString());
        request.addProperty("mail_id", mailId.toString());
        HttpResponse<String> response = ApiHttp.request("POST", BASE + "/claim", request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "claim", "failed");
            return new ClaimResult(false, status(response), message(response, root), null, null, 0);
        }
        Mail mail = mailFromJson(object(root, "mail"));
        if (mail == null) {
            return new ClaimResult(false, status(response), "Api 邮件数据格式无效", null, null, 0);
        }
        DebugLogger.exiting(MODULE, "claim", "success");
        return new ClaimResult(true, status(response), "", mail, refFromJson(object(root, "ref")),
                ApiHttp.intValue(root, "unread", 0));
    }

    /** 清理过期邮件。 */
    public static PurgeResult purge(boolean keepStarred) {
        DebugLogger.entering(MODULE, "purge", "keepStarred=" + keepStarred);
        JsonObject request = new JsonObject();
        request.addProperty("keep_starred", keepStarred);
        HttpResponse<String> response = ApiHttp.request("POST", BASE + "/purge", request.toString());
        JsonObject root = body(response);
        if (!ApiHttp.successful(response)) {
            DebugLogger.exiting(MODULE, "purge", "failed");
            return new PurgeResult(false, status(response), message(response, root), 0, 0, List.of());
        }
        int removed = ApiHttp.intValue(root, "removed", 0);
        int pruned = ApiHttp.intValue(root, "pruned_refs", 0);
        DebugLogger.exiting(MODULE, "purge", "removed=" + removed + ", prunedRefs=" + pruned);
        return new PurgeResult(true, status(response), "", removed, pruned, uuidList(root, "affected"));
    }

    /** 账户注销时清空该玩家收件箱。 */
    public static boolean deleteBox(UUID playerUuid) {
        DebugLogger.entering(MODULE, "deleteBox", "playerUuid=" + playerUuid);
        HttpResponse<String> response = ApiHttp.request("DELETE",
                BASE + "/box?uuid=" + ApiHttp.encode(playerUuid.toString()), null);
        boolean success = ApiHttp.successful(response);
        if (!success) {
            DebugLogger.warn(MODULE, "清空收件箱失败：playerUuid=%s, %s",
                    playerUuid, message(response, body(response)));
        }
        DebugLogger.exiting(MODULE, "deleteBox", "success=" + success);
        return success;
    }

    // ========================================================================
    // JSON 映射
    // ========================================================================

    /** 组装发布 / 编辑共用的邮件字段。 */
    private static JsonObject mailFields(MailType type, String sender, List<TargetSpec> targets,
            String scopeSummary, String title, String body, Long expireTime, List<MailAttachment> attachments) {
        JsonObject json = new JsonObject();
        json.addProperty("type", (type == null ? MailType.NOTICE : type).name());
        json.addProperty("sender", sender == null ? "" : sender);
        json.add("targets", targetsToJson(targets));
        json.addProperty("scope_summary", scopeSummary == null ? "" : scopeSummary);
        json.addProperty("title", title == null ? "" : title);
        json.addProperty("body", body == null ? "" : body);
        if (expireTime == null) {
            json.add("expire_time", JsonNull.INSTANCE);
        } else {
            json.addProperty("expire_time", expireTime);
        }
        json.add("attachments", attachmentsToJson(attachments));
        return json;
    }

    private static JsonArray targetsToJson(List<TargetSpec> targets) {
        JsonArray array = new JsonArray();
        if (targets == null) {
            return array;
        }
        for (TargetSpec spec : targets) {
            JsonObject json = new JsonObject();
            json.addProperty("scope", spec.scope());
            JsonArray args = new JsonArray();
            if (spec.args() != null) {
                for (String arg : spec.args()) {
                    args.add(arg);
                }
            }
            json.add("args", args);
            array.add(json);
        }
        return array;
    }

    private static List<TargetSpec> targetsFromJson(JsonElement element) {
        List<TargetSpec> targets = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return targets;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject json = item.getAsJsonObject();
            List<String> args = new ArrayList<>();
            JsonElement rawArgs = json.get("args");
            if (rawArgs != null && rawArgs.isJsonArray()) {
                for (JsonElement arg : rawArgs.getAsJsonArray()) {
                    try {
                        args.add(arg.getAsString());
                    } catch (RuntimeException ignored) {
                        // 单个参数异常时跳过，不影响其余接收范围。
                    }
                }
            }
            targets.add(new TargetSpec((byte) ApiHttp.intValue(json, "scope", 0), args));
        }
        return targets;
    }

    private static JsonArray attachmentsToJson(List<MailAttachment> attachments) {
        JsonArray array = new JsonArray();
        if (attachments == null) {
            return array;
        }
        for (MailAttachment attachment : attachments) {
            JsonObject json = new JsonObject();
            json.addProperty("type", attachment.type().name());
            json.addProperty("data", attachment.data() == null ? "" : attachment.data());
            json.addProperty("amount", attachment.amount());
            if (attachment.itemNbt() == null) {
                json.add("item_nbt", JsonNull.INSTANCE);
            } else {
                json.addProperty("item_nbt", attachment.itemNbt());
            }
            array.add(json);
        }
        return array;
    }

    private static List<MailAttachment> attachmentsFromJson(JsonElement element) {
        List<MailAttachment> attachments = new ArrayList<>();
        if (element == null || !element.isJsonArray()) {
            return attachments;
        }
        for (JsonElement item : element.getAsJsonArray()) {
            if (!item.isJsonObject()) {
                continue;
            }
            JsonObject json = item.getAsJsonObject();
            AttachmentType type;
            try {
                type = AttachmentType.valueOf(ApiHttp.stringValue(json, "type"));
            } catch (IllegalArgumentException e) {
                DebugLogger.warn(MODULE, "未知附件类型，已跳过：%s", ApiHttp.stringValue(json, "type"));
                continue;
            }
            String itemNbt = ApiHttp.stringValue(json, "item_nbt");
            attachments.add(new MailAttachment(type, ApiHttp.stringValue(json, "data"),
                    ApiHttp.intValue(json, "amount", 0), itemNbt.isEmpty() ? null : itemNbt));
        }
        return attachments;
    }

    /** 解析 Api 返回的邮件对象；格式无效时返回 null。 */
    private static Mail mailFromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        UUID id = parseUuid(ApiHttp.stringValue(json, "id"));
        if (id == null) {
            return null;
        }
        return new Mail(id,
                parseMailType(ApiHttp.stringValue(json, "type")),
                ApiHttp.stringValue(json, "sender"),
                targetsFromJson(json.get("targets")),
                ApiHttp.stringValue(json, "scope_summary"),
                ApiHttp.stringValue(json, "title"),
                ApiHttp.stringValue(json, "body"),
                ApiHttp.longValue(json, "created_time", 0L),
                optionalLong(json, "expire_time"),
                ApiHttp.booleanValue(json, "claimed", false),
                ApiHttp.booleanValue(json, "hidden", false),
                attachmentsFromJson(json.get("attachments")));
    }

    /** 解析 Api 返回的收件箱引用；格式无效或字段缺失时返回 null。 */
    private static MailRef refFromJson(JsonObject json) {
        if (json == null) {
            return null;
        }
        UUID mailId = parseUuid(ApiHttp.stringValue(json, "mail_id"));
        if (mailId == null) {
            return null;
        }
        return new MailRef(mailId,
                ApiHttp.booleanValue(json, "read", false),
                ApiHttp.booleanValue(json, "starred", false),
                ApiHttp.booleanValue(json, "claimed", false));
    }

    // ========================================================================
    // 工具方法
    // ========================================================================

    private static JsonObject body(HttpResponse<String> response) {
        return response == null ? new JsonObject() : ApiHttp.parse(response.body());
    }

    private static int status(HttpResponse<String> response) {
        return response == null ? 0 : response.statusCode();
    }

    private static String message(HttpResponse<String> response, JsonObject root) {
        return response == null ? ApiHttp.failureMessage() : ApiHttp.responseMessage(root);
    }

    private static JsonArray array(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : new JsonArray();
    }

    private static JsonObject object(JsonObject root, String key) {
        JsonElement element = root == null ? null : root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static Long optionalLong(JsonObject json, String key) {
        JsonElement element = json.get(key);
        if (element == null || element.isJsonNull()) {
            return null;
        }
        try {
            return element.getAsLong();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private static JsonArray uuidArray(Collection<UUID> uuids) {
        JsonArray array = new JsonArray();
        for (UUID uuid : uuids) {
            array.add(uuid.toString());
        }
        return array;
    }

    /** 批量接口单次最多 {@value #BATCH_LIMIT} 人，超出时切片分多次请求。 */
    private static List<List<UUID>> chunk(Collection<UUID> uuids) {
        List<List<UUID>> chunks = new ArrayList<>();
        List<UUID> current = new ArrayList<>(BATCH_LIMIT);
        for (UUID uuid : uuids) {
            current.add(uuid);
            if (current.size() == BATCH_LIMIT) {
                chunks.add(current);
                current = new ArrayList<>(BATCH_LIMIT);
            }
        }
        if (!current.isEmpty()) {
            chunks.add(current);
        }
        return chunks;
    }

    private static List<UUID> uuidList(JsonObject root, String key) {
        List<UUID> uuids = new ArrayList<>();
        for (JsonElement element : array(root, key)) {
            UUID uuid = parseUuid(element.isJsonPrimitive() ? element.getAsString() : "");
            if (uuid != null) {
                uuids.add(uuid);
            }
        }
        return uuids;
    }

    private static UUID parseUuid(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return UUID.fromString(value);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private static MailType parseMailType(String value) {
        try {
            return MailType.valueOf(value);
        } catch (IllegalArgumentException ignored) {
            DebugLogger.warn(MODULE, "未知邮件类型，按通知处理：%s", value);
            return MailType.NOTICE;
        }
    }
}
