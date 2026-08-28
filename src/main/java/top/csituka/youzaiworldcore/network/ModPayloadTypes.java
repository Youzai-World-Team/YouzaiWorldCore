package top.csituka.youzaiworldcore.network;

import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import top.csituka.youzaiworldcore.dimensionalinventories.WorldPoolTeleportPayload;
import top.csituka.youzaiworldcore.util.DebugLogger;

/**
 * 注册客户端与服务端共同使用的网络 Payload 编解码类型。
 * <p>
 * 本类只依赖 Payload record，不注册任何服务端接收器，也不读取账户、邮件或 Api 配置。
 * 因此物理客户端可以安全加载本类，而不会触发服务端权威逻辑。
 * </p>
 */
public final class ModPayloadTypes {

        private static final String MODULE = "ModPayloadTypes";

        private ModPayloadTypes() {
        }

        /** 注册全部 C2S 与 S2C Payload 类型。 */
        @SuppressWarnings("null")
        public static void initialize() {
                DebugLogger.entering(MODULE, "initialize");

                // ===== C2S =====
                PayloadTypeRegistry.serverboundPlay().register(DecomposeItemPayload.ID,
                                DecomposeItemPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(AuthRequestPayload.ID, AuthRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                RegistrationEmailRequestPayload.ID, RegistrationEmailRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                PasswordResetRequestPayload.ID, PasswordResetRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                AccountManagementRequestPayload.ID, AccountManagementRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(FlyBeaconActivePayload.ID,
                                FlyBeaconActivePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(WorldPoolTeleportPayload.ID,
                                WorldPoolTeleportPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                InPlaceRespawnRequestPayload.ID, InPlaceRespawnRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(LargeSignSetTextPayload.TYPE,
                                LargeSignSetTextPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                WirelessRedstoneSetChannelPayload.TYPE, WirelessRedstoneSetChannelPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TeleportAnchorTeleportPayload.TYPE, TeleportAnchorTeleportPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TeleportAnchorDeletePayload.TYPE, TeleportAnchorDeletePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TeleportAnchorRenamePayload.TYPE, TeleportAnchorRenamePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TeleportAnchorActivatePayload.TYPE, TeleportAnchorActivatePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TeleportAnchorReorderPayload.TYPE, TeleportAnchorReorderPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(DoubleDoorsTogglePayload.ID,
                                DoubleDoorsTogglePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(InvisibilityPayload.ID,
                                InvisibilityPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(PetCommandPayload.ID, PetCommandPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(TrinketInteractPayload.ID,
                                TrinketInteractPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(InventoryCollectPayload.ID,
                                InventoryCollectPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(AfkHeartbeatPayload.ID,
                                AfkHeartbeatPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(CosmeticUploadPayload.ID,
                                CosmeticUploadPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(CosmeticRequestPayload.ID,
                                CosmeticRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MojangProfileRequestPayload.ID,
                                MojangProfileRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(AttributeUpgradePayload.TYPE,
                                AttributeUpgradePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailComposeOpenPayload.ID,
                                MailComposeOpenPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailOpenPayload.ID, MailOpenPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                MailSentListRequestPayload.ID, MailSentListRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailRecallPayload.ID, MailRecallPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailPurgePayload.ID, MailPurgePayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailListRequestPayload.ID,
                                MailListRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailFetchPayload.ID, MailFetchPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailActionPayload.ID, MailActionPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailAdminSendPayload.ID,
                                MailAdminSendPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(MailAdminEditPayload.ID,
                                MailAdminEditPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                MailPlayerListRequestPayload.ID, MailPlayerListRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(
                                TitleStateRequestPayload.ID, TitleStateRequestPayload.STREAM_CODEC);
                PayloadTypeRegistry.serverboundPlay().register(TitleEquipPayload.ID, TitleEquipPayload.STREAM_CODEC);

                // ===== S2C =====
                PayloadTypeRegistry.clientboundPlay().register(OpenMenuPayload.ID, OpenMenuPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(ManaSyncPayload.ID, ManaSyncPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(OpenAuthScreenPayload.ID,
                                OpenAuthScreenPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                RegistrationEmailStatePayload.ID, RegistrationEmailStatePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                PasswordResetStatePayload.ID, PasswordResetStatePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                AccountManagementStatePayload.ID, AccountManagementStatePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                InPlaceRespawnInfoPayload.ID, InPlaceRespawnInfoPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                InPlaceRespawnResultPayload.ID, InPlaceRespawnResultPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                TeleportAnchorListPayload.TYPE, TeleportAnchorListPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                TeleportAnchorOpenNamePayload.TYPE, TeleportAnchorOpenNamePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                LargeSignOpenEditPayload.TYPE, LargeSignOpenEditPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                WirelessRedstoneOpenChannelPayload.TYPE,
                                WirelessRedstoneOpenChannelPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                TeleportStoneInterruptPayload.TYPE, TeleportStoneInterruptPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(LevelExpSyncPayload.ID,
                                LevelExpSyncPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(DamageNumberPayload.ID,
                                DamageNumberPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                FunctionToggleSyncPayload.TYPE, FunctionToggleSyncPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                LaowuMemeTriggerPayload.ID, LaowuMemeTriggerPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(LaowuMemeStopPayload.ID,
                                LaowuMemeStopPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(CosmeticReadyPayload.ID,
                                CosmeticReadyPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MojangAuthChallengePayload.ID,
                                MojangAuthChallengePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(CosmeticInfoPayload.ID,
                                CosmeticInfoPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(CosmeticDataPayload.ID,
                                CosmeticDataPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MojangSkinPayload.ID,
                                MojangSkinPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(
                                CosmeticUploadResultPayload.ID, CosmeticUploadResultPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(AttributeSyncPayload.TYPE,
                                AttributeSyncPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(OpenMailComposePayload.ID,
                                OpenMailComposePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailListPayload.ID, MailListPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailSentListPayload.ID,
                                MailSentListPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailUpdatePayload.ID, MailUpdatePayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailOpResultPayload.ID,
                                MailOpResultPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailUnreadCountPayload.ID,
                                MailUnreadCountPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(MailPlayerListPayload.ID,
                                MailPlayerListPayload.STREAM_CODEC);
                PayloadTypeRegistry.clientboundPlay().register(TitleStatePayload.ID, TitleStatePayload.STREAM_CODEC);

                DebugLogger.info(MODULE, "全部网络 Payload 类型已注册");
                DebugLogger.exiting(MODULE, "initialize");
        }
}
