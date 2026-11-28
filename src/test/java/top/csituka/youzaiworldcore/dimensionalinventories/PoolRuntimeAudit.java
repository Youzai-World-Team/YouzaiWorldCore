package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.Connection;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundSetObjectivePacket;
import net.minecraft.network.protocol.game.ClientboundSetPlayerTeamPacket;
import net.minecraft.network.protocol.game.ServerboundClientCommandPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.server.players.NameAndId;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.AgeableMob;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.GoalSelector;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.animal.feline.Cat;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.storage.LevelData;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.ScoreHolder;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.block.ModBlocks;
import top.csituka.youzaiworldcore.block.TeleportAnchorBlock;
import top.csituka.youzaiworldcore.block.entity.TeleportAnchorBlockEntity;
import top.csituka.youzaiworldcore.data.TeleportAnchorManager;
import top.csituka.youzaiworldcore.mana.ManaManager;
import top.csituka.youzaiworldcore.pet.PetEntry;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.pet.PetMode;
import top.csituka.youzaiworldcore.mixin.PlayerEnchantmentSeedAccessor;
import top.csituka.youzaiworldcore.mixin.pet.PetShoulderAccessor;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.util.BackupArchive;
import top.csituka.youzaiworldcore.util.DebugLogger;
import top.csituka.youzaiworldcore.util.LoadedChunks;

public final class PoolRuntimeAudit implements DedicatedServerModInitializer {
        private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PoolRuntimeAudit");
        private static final String MAIN = "survival_world_pool";
        private static final String END = "test_pool";
        private static final BlockPos COLD_ANCHOR = new BlockPos(4098, 90, 4098);
        private static final BlockPos DEFERRED_ANCHOR = COLD_ANCHOR.offset(64, 0, 64);
        private static final UUID ID = UUID.fromString("c057244f-2bd3-4e87-a20f-512ef55a1c18");
        private static final UUID STANDING_PET = UUID.fromString("66ac221a-c50a-4b79-97e6-2b837c570ebc");
        private static final UUID SITTING_PET = UUID.fromString("3ac58bd6-3647-4281-8c46-bf4e3734c23a");
        private static final UUID END_PET = UUID.fromString("235c6a70-721b-4819-9b6e-cdbe5d36d6d2");
        private static final UUID UNLOADED_PET = UUID.fromString("79dc6671-646d-4e81-b785-f933e075f86c");
        private static final UUID RELEASED_PET = UUID.fromString("8ddce803-0eb9-492c-b61e-cac8f020ca9f");
        private static final UUID COLD_RELEASED_PET = UUID.fromString("e1301069-f245-4d09-8a1a-a2dc457000a3");
        private static final BlockPos COLD_RELEASE_POS = new BlockPos(32001, 190, 32001);
        private static final UUID DEAD_RELEASED_PET = UUID.fromString("db018dfd-d2f4-4401-a23d-a2f38ead686d");
        private static final BlockPos DEAD_RELEASE_POS = COLD_RELEASE_POS.offset(-64, 0, 0);
        private static final UUID RESTORE_OWNER = UUID.fromString("7ecb2181-07f0-458b-87e0-6f2f74f42093");
        private static final UUID RESTORE_SAVED = UUID.fromString("3cef5ff3-ec20-4dcb-ab3a-d83a028b6964");
        private static final UUID RESTORE_PENDING = UUID.fromString("686aac42-0ef4-4dca-a44e-1dd5f080d31d");
        private static final BlockPos RESTORE_SAVED_POS = COLD_RELEASE_POS.offset(64, 0, 0);
        private static final BlockPos RESTORE_PENDING_POS = COLD_RELEASE_POS.offset(128, 0, 0);

        @Override
        public void onInitializeServer() {
                ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                        try {
                                switch (System.getProperty("yzwc.pool.audit", "switch")) {
                                        case "switch" -> run(server);
                                        case "restart" -> restart(server);
                                        case "bad-scoreboard" -> badScoreboard(server);
                                        case "pet-backup-restore" -> verifyPetBackupRestore(server);
                                        default ->
                                                throw new IllegalArgumentException("Unknown dimension pool test phase");
                                }
                                LOGGER.notice("POOL AUDIT PASSED");
                        } catch (Throwable error) {
                                LOGGER.error("POOL AUDIT FAILED", error);
                        } finally {
                                for (ServerPlayer player : List.copyOf(server.getPlayerList().getPlayers())) {
                                        detach(player);
                                }
                        }
                });
        }

        private static void check(boolean pass, String message) {
                if (!pass)
                        throw new AssertionError(message);
        }

        @SuppressWarnings("null")
        private static void command(MinecraftServer server, String command) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        private static int score(ServerLevel level, String name, String objective) {
                var board = level.getScoreboard();
                @SuppressWarnings("null")
                var info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), board.getObjective(objective));
                return info == null ? -999 : info.value();
        }

        @SuppressWarnings("null")
        private static ServerPlayer connect(MinecraftServer server, ServerLevel level, UUID id, String name,
                        TestConnection connection, boolean load) throws Exception {
                var profile = new GameProfile(id, name);
                @SuppressWarnings("null")
                var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
                if (load) {
                        @SuppressWarnings("null")
                        var input = TagValueInput.create(ProblemReporter.DISCARDING, server.registryAccess(),
                                        NbtIo.readCompressed(ModPaths.vanillaPlayerData(server, id),
                                                        NbtAccounter.unlimitedHeap()));
                        player.load(input);
                } else {
                        player.snapTo(1.5, 85, 2.5, 31, 11);
                }
                new ServerGamePacketListenerImpl(server, connection, player,
                                CommonListenerCookie.createInitial(profile, false));
                AuthPlayerHelper.setAuthenticated(player, true);
                server.getPlayerList().getPlayers().add(player);
                server.getPlayerList().getPlayersByUUID().put(id, player);
                level.addNewPlayer(player);
                return player;
        }

        private static void detach(ServerPlayer player) {
                var list = player.level().getServer().getPlayerList();
                list.getPlayers().remove(player);
                list.getPlayersByUUID().remove(player.getUUID());
                player.level().removePlayerImmediately(player,
                                net.minecraft.world.entity.Entity.RemovalReason.UNLOADED_WITH_PLAYER);
        }

        private static void trackPetChunk(ServerLevel level, BlockPos pos) throws Exception {
                @SuppressWarnings("null")
                var chunk = ChunkPos.containing(pos);
                level.getChunk(chunk.x(), chunk.z());
                petEntities(level).updateChunkStatus(chunk, Visibility.TICKING);
        }

        private static PersistentEntitySectionManager<?> petEntities(ServerLevel level) throws Exception {
                var field = ServerLevel.class.getDeclaredField("entityManager");
                field.setAccessible(true);
                return (PersistentEntitySectionManager<?>) field.get(level);
        }

        @SuppressWarnings("null")
        private static void awaitPetChunk(ServerLevel level, BlockPos pos) throws Exception {
                trackPetChunk(level, pos);
                var entities = petEntities(level);
                var chunk = ChunkPos.containing(pos);
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                level.getServer().managedBlock(() -> {
                        entities.tick();
                        return level.areEntitiesActuallyLoadedAndTicking(chunk) || System.nanoTime() >= deadline;
                });
                check(level.areEntitiesActuallyLoadedAndTicking(chunk), "entity chunk is ready");
        }

        @SuppressWarnings("null")
        private static Wolf addPet(ServerPlayer player, UUID id, String name, boolean sitting) throws Exception {
                var level = player.level();
                trackPetChunk(level, player.blockPosition());
                var wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
                wolf.setUUID(id);
                wolf.snapTo(player.getX(), player.getY(), player.getZ(), 0, 0);
                wolf.setTame(true, true);
                wolf.setOwnerReference(EntityReference.of(player.getUUID()));
                wolf.setOrderedToSit(sitting);
                wolf.setHealth(13);
                PetGlobalState.get(level.getServer()).addPet(PetEntry.createNew(name, id, player.getUUID()));
                check(level.addFreshEntity(wolf) && level.getEntity(id) == wolf, "pet fixture is loaded");
                return wolf;
        }

        @SuppressWarnings("null")
        private static boolean isParked(MinecraftServer server, UUID id) throws Exception {
                var file = ModPaths.parkedPet(server, id);
                if (!Files.exists(file)) return false;
                var data = NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota());
                return data.contains("pet") || data.contains("wolf");
        }

        private static void awaitReturnedPetSave(MinecraftServer server, UUID id) {
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                server.managedBlock(() -> {
                        try {
                                return !isParked(server, id) || System.nanoTime() >= deadline;
                        } catch (Exception error) {
                                throw new IllegalStateException(error);
                        }
                });
        }

        @SuppressWarnings("null")
        private static Wolf reloadWolf(Wolf original) {
                var level = (ServerLevel) original.level();
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                original.saveWithoutId(output);
                original.discard();
                Wolf wolf = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
                wolf.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), output.buildResult()));
                check(level.addFreshEntity(wolf), "reloaded wolf enters the world");
                return wolf;
        }

        @SuppressWarnings("null")
        private static void petLifecycleAndCombat(MinecraftServer server) throws Exception {
                var level = server.overworld();
                var owner = connect(server, level, UUID.randomUUID(), "PetAuditOwner", new TestConnection(), false);
                var ally = connect(server, level, UUID.randomUUID(), "PetAuditAlly", new TestConnection(), false);
                server.services().nameToIdCache().add(new NameAndId(owner.getGameProfile()));
                server.services().nameToIdCache().add(new NameAndId(ally.getGameProfile()));
                owner.snapTo(65.5, 190, 65.5, 0, 0);
                ally.snapTo(65.5, 190, 65.5, 0, 0);
                var state = PetGlobalState.get(server);
                UUID wolfId = UUID.randomUUID();
                Wolf wolf = addPet(owner, wolfId, "AUDITMODE", false);
                var target = EntityTypes.SKELETON.create(level, EntitySpawnReason.LOAD);
                target.snapTo(67.5, 190, 65.5, 0, 0);
                check(level.addFreshEntity(target), "combat target enters the world");
                var field = Mob.class.getDeclaredField("targetSelector");
                field.setAccessible(true);
                var selector = (GoalSelector) field.get(wolf);
                var source = owner.createCommandSourceStack();
                var commands = server.getCommands();
                String command = "yzwc pet set AUDITMODE ";

                AuthPlayerHelper.setAuthenticated(owner, false);
                check(AuthPlayerHelper.shouldBlockActions(owner), "command test player requires login");
                commands.performPrefixedCommand(source, command + "mode attack");
                check(state.findByEntityUUID(wolfId).orElseThrow().mode() == PetMode.COMPANIONSHIP,
                                "unauthenticated player cannot change pet mode");
                AuthPlayerHelper.setAuthenticated(owner, true);
                commands.performPrefixedCommand(source, command + "trust add PetAuditAlly");
                check(state.findByEntityUUID(wolfId).orElseThrow().isTrustedOrOwner(ally.getUUID()),
                                "the original trust command admits the ally");
                wolf.setTarget(target);
                selector.tick();
                check(wolf.getTarget() == null && selector.getAvailableGoals().stream()
                                .noneMatch(goal -> goal.getGoal() instanceof NearestAttackableTargetGoal<?>),
                                "companion cannot target a skeleton even through vanilla target assignment");

                commands.performPrefixedCommand(source, command + "mode hunting");
                var wild = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
                var alert = HurtByTargetGoal.class.getDeclaredMethod("alertOther", Mob.class, LivingEntity.class);
                alert.setAccessible(true);
                alert.invoke(new HurtByTargetGoal(wild), wolf, target);
                check(wolf.getTarget() == null, "vanilla pack alerts do not bypass managed hunting");
                ally.snapTo(77.6, 190, 65.5, 0, 0);
                ally.setLastHurtMob(target);
                selector.tick();
                check(wolf.getTarget() == null, "hunting ignores an ally attack beyond ten blocks");
                ally.snapTo(77.5, 190, 65.5, 0, 0);
                selector.tick();
                check(wolf.getTarget() == target, "hunting responds to the trusted attack at ten blocks");
                ally.snapTo(77.6, 190, 65.5, 0, 0);
                selector.tick();
                check(wolf.getTarget() == null, "hunting stops beyond ten blocks");

                commands.performPrefixedCommand(source, command + "mode guard");
                ally.snapTo(65.5, 190, 65.5, 0, 0);
                selector.tick();
                check(wolf.getTarget() == null, "guard does not join an ally's attack");
                ally.setLastHurtByMob(target);
                selector.tick();
                check(wolf.getTarget() == target, "guard protects a trusted player");
                commands.performPrefixedCommand(source, command + "mode companionship");
                check(wolf.getTarget() == null && wolf.getPersistentAngerTarget() == null,
                                "mode switch immediately stops ongoing aggression");

                commands.performPrefixedCommand(source, command + "mode attack");
                ally.tickCount++;
                ally.setLastHurtMob(target);
                selector.tick();
                check(wolf.getTarget() == target && !wolf.canAttack(ally),
                                "attack mode joins a trusted player and protects the player from friendly fire");
                commands.performPrefixedCommand(source, command + "trust remove PetAuditAlly");
                selector.tick();
                check(wolf.getTarget() == null, "revoking trust ends the active response");

                commands.performPrefixedCommand(source, command + "transfer PetAuditAlly");
                check(wolf.getOwner() == ally && state.findByEntityUUID(wolfId).orElseThrow().isOwner(ally.getUUID()),
                                "loaded wolf follows the new registered owner");
                wolf.discard();
                Wolf unloaded = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
                unloaded.setUUID(wolfId);
                unloaded.setOwnerReference(EntityReference.of(ally.getUUID()));
                unloaded.setTame(true, true);
                unloaded.snapTo(65.5, 190, 65.5, 0, 0);
                commands.performPrefixedCommand(ally.createCommandSourceStack(), command + "transfer PetAuditOwner");
                check(level.addFreshEntity(unloaded) && unloaded.getOwner() == owner,
                                "loading a transferred wolf replaces the old native owner");
                var partner = addPet(owner, UUID.randomUUID(), "AUDITPARENT", false);
                unloaded.setInLove(ally);
                AgeableMob child = ((AgeableMob) unloaded).getBreedOffspring(level, partner);
                check(child instanceof Wolf baby && baby.getOwner() == ally
                                && state.findByEntityUUID(baby.getUUID()).orElseThrow().isOwner(ally.getUUID()),
                                "offspring uses the breeder for both native and registered owner");
                state.removePet(child.getUUID());
                partner.discard();
                state.removePet(partner.getUUID());

                var unloadedData = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                unloaded.saveWithoutId(unloadedData);
                unloaded.discard();
                commands.performPrefixedCommand(source, command + "release_life true");
                Wolf released = EntityTypes.WOLF.create(level, EntitySpawnReason.LOAD);
                released.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(),
                                unloadedData.buildResult()));
                check(level.addFreshEntity(released) && !released.isTame() && released.getOwnerReference() == null,
                                "offline release clears the native owner when the entity loads");
                released = reloadWolf(released);
                check(!released.isTame() && released.getOwnerReference() == null
                                && ((GoalSelector) field.get(released)).getAvailableGoals().stream()
                                        .anyMatch(goal -> goal.getGoal() instanceof NearestAttackableTargetGoal<?>),
                                "released wolf remains wild after NBT reload and gets vanilla targets back");
                released.discard();
                target.discard();
                detach(owner);
                detach(ally);
        }

        @SuppressWarnings("null")
        private static void run(MinecraftServer server) throws Exception {
                petLifecycleAndCombat(server);
                nativePetManagement(server);
                ServerLevel overworld = server.overworld();
                ServerLevel nether = server.getLevel(Level.NETHER);
                ServerLevel end = server.getLevel(Level.END);
                var socket = new TestConnection();
                var otherSocket = new TestConnection();
                var player = connect(server, overworld, ID, "PoolAudit", socket, false);
                var other = connect(server, end, UUID.randomUUID(), "PoolOther", otherSocket, false);
                coldCombatSight(other);
                // Same names, different scores, independent team settings and client audiences.
                command(server, "scoreboard objectives add audit dummy");
                command(server, "scoreboard players set Fake audit 11");
                command(server, "execute in minecraft:the_end run scoreboard objectives add audit dummy");
                command(server, "execute in minecraft:the_end run scoreboard players set Fake audit 22");
                command(server, "execute in minecraft:the_end if score Fake audit matches 22 run scoreboard players set Passed audit 1");
                command(server, "execute in minecraft:the_end run scoreboard players set Expected audit 22");
                command(server, "execute in minecraft:the_end if score Fake audit = Expected audit run scoreboard players set Passed2 audit 1");
                check(score(overworld, "Fake", "audit") == 11 && score(nether, "Fake", "audit") == 11
                                && score(end, "Fake", "audit") == 22 && score(end, "Passed", "audit") == 1
                                && score(end, "Passed2", "audit") == 1,
                                "command objective, score and execute in scope");
                command(server, "team add audit");
                command(server, "execute in minecraft:the_end run team add audit");
                command(server, "team modify audit color red");
                command(server, "execute in minecraft:the_end run team modify audit color blue");
                command(server, "team join audit PoolAudit");
                command(server, "execute in minecraft:the_end run team join audit PoolOther");
                check(overworld.getScoreboard().getPlayerTeam("audit") != end.getScoreboard().getPlayerTeam("audit")
                                && player.getTeam().getColor().isPresent()
                                && other.getTeam().getColor().isPresent()
                                && !other.getTeam().getColor().equals(player.getTeam().getColor()),
                                "team and membership scope");
                socket.packets.clear();
                otherSocket.packets.clear();
                command(server, "scoreboard objectives setdisplay sidebar audit");
                check(socket.packets.stream().anyMatch(ClientboundSetObjectivePacket.class::isInstance)
                                && otherSocket.packets.stream()
                                                .noneMatch(ClientboundSetObjectivePacket.class::isInstance),
                                "scoreboard updates stay within a pool");
                command(server, "tag @a add attached");
                check(player.entityTags().contains("attached") && !other.entityTags().contains("attached"),
                                "tag command is scoped");

                var account = new PlayerAccount("PoolAudit");
                account.uuid = ID.toString();
                account.inPlaceRespawnCount = 12;
                var cache = AccountDataStorage.class.getDeclaredMethod("cache", PlayerAccount.class);
                cache.setAccessible(true);
                cache.invoke(null, account);
                check(PoolRespawnCounts.get(player).orElseThrow() == 12, "legacy count belongs to survival");
                check(PoolRespawnCounts.increment(player) == 13, "count increments");
                var mana = ManaManager.getInstance();
                check(mana.consumeMana(overworld, ID, 60) && mana.getMana(nether, ID) == 40
                                && mana.getMana(end, ID) == 100, "mana: same-pool sharing and independent balances");
                ((PlayerEnchantmentSeedAccessor) player).youzaiworldcore$setEnchantmentSeed(12345);
                var death = GlobalPos.of(Level.OVERWORLD, new BlockPos(12, 80, -5));
                player.setLastDeathLocation(Optional.of(death));
                player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                player.getEnderChestInventory().setItem(0, new ItemStack(Items.GOLD_INGOT, 4));
                player.experienceLevel = 17;
                player.addEffect(new MobEffectInstance(MobEffects.HEALTH_BOOST, 1000, 1));
                player.setHealth(27);
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                                new net.minecraft.world.level.storage.LevelData.RespawnData(
                                                GlobalPos.of(Level.OVERWORLD, new BlockPos(1, 90, 2)), 31, 11),
                                true), false);

                check(player.teleportTo(nether, 2.5, 85, 3.5, Set.of(), 0, 0, true)
                                && player.entityTags().contains("attached")
                                && player.getInventory().getItem(0).getCount() == 3
                                && player.getRespawnConfig() != null && player.getHealth() == 27,
                                "travel inside one pool");
                check(player.teleportTo(overworld, 1.5, 85, 2.5, Set.of(), 31, 11, true),
                                "return inside the same pool");
                addPet(player, STANDING_PET, "AUDITSTAND", false);
                Wolf sitting = addPet(player, SITTING_PET, "AUDITSIT", true);
                PetGlobalState.get(server).addPet(PetEntry.createNew("AUDITUNLOADED", UNLOADED_PET, ID));
                trackPetChunk(nether, new BlockPos(2, 85, 3));
                check(player.teleportTo(nether, 2.5, 85, 3.5, Set.of(), 0, 0, true)
                                && nether.getEntity(STANDING_PET) instanceof Wolf
                                && overworld.getEntity(SITTING_PET) == sitting,
                                "an eligible pet still follows within a pool");
                check(player.teleportTo(overworld, 1.5, 85, 2.5, Set.of(), 31, 11, true),
                                "return with pet inside the same pool");
                @SuppressWarnings("null")
                Wolf standing = (Wolf) overworld.getEntity(STANDING_PET);
                addPet(player, RELEASED_PET, "AUDITRELEASE", false);

                check(DimensionPoolManager.teleportToPool(player, END), "first switch to End");
                check(overworld.getEntity(STANDING_PET) == null && end.getEntity(STANDING_PET) == null
                                && standing.isRemoved() && isParked(server, STANDING_PET)
                                && !PoolPets.canLoad(overworld, standing),
                                "standing pet is parked and its stale chunk copy is rejected");
                check(overworld.getEntity(SITTING_PET) == sitting && !isParked(server, SITTING_PET)
                                && !isParked(server, UNLOADED_PET), "sitting and unloaded pets are left alone");
                awaitPetChunk(overworld, standing.blockPosition());
                server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack().withLevel(end).withEntity(player),
                                "yzwc pet set AUDITRELEASE release_life true");
                check(overworld.getEntity(RELEASED_PET) instanceof Wolf released && !released.isTame()
                                && released.getOwnerReference() == null
                                && PetGlobalState.get(server).findByEntityUUID(RELEASED_PET).isEmpty()
                                && end.getEntity(RELEASED_PET) == null,
                                "releasing a parked pet returns it to its own dimension");
                addPet(player, END_PET, "AUDITEND", false);
                check(player.level() == end && player.gameMode() == net.minecraft.world.level.GameType.CREATIVE
                                && player.entityTags().isEmpty() && player.getEnchantmentSeed() != 12345
                                && player.getLastDeathLocation().isEmpty() && player.getInventory().getItem(0).isEmpty()
                                && player.getEnderChestInventory().getItem(0).isEmpty() && player.experienceLevel == 0
                                && player.getRespawnConfig() == null && PoolRespawnCounts.get(player).orElseThrow() == 0
                                && player.getTeam() == null, "fresh target starts independently");
                check(socket.packets.stream().anyMatch(ClientboundSetPlayerTeamPacket.class::isInstance),
                                "scoreboard team packets sent on switch");
                player.addTag("end_data");
                ((PlayerEnchantmentSeedAccessor) player).youzaiworldcore$setEnchantmentSeed(54321);
                player.setLastDeathLocation(Optional.of(GlobalPos.of(Level.END, new BlockPos(2, 70, 3))));
                player.getInventory().setItem(0, new ItemStack(Items.EMERALD, 2));
                check(PoolRespawnCounts.increment(player) == 1 && mana.consumeMana(end, ID, 30),
                                "target balance/count update");
                check(DimensionPoolManager.teleportToPool(player, MAIN), "return to old pool");
                check(overworld.getEntity(STANDING_PET) instanceof Wolf returned && returned.getHealth() == 13
                                && returned.getOwner() == player && returned.distanceToSqr(player) < 64
                                && end.getEntity(END_PET) == null && isParked(server, END_PET),
                                "return restores original pet near the owner");
                check(player.level() == overworld && player.getX() == 1.5 && player.getYRot() == 31
                                && player.entityTags().equals(Set.of("attached"))
                                && player.getEnchantmentSeed() == 12345
                                && player.getLastDeathLocation().orElseThrow().equals(death)
                                && player.getRespawnConfig() != null
                                && player.getInventory().getItem(0).getCount() == 3
                                && player.getEnderChestInventory().getItem(0).getCount() == 4
                                && player.getHealth() == 27 && player.hasEffect(MobEffects.HEALTH_BOOST)
                                && player.experienceLevel == 17 && PoolRespawnCounts.get(player).orElseThrow() == 13
                                && mana.getMana(overworld, ID) == 40 && mana.getMana(end, ID) == 70,
                                "return restores source state");

                var root = ModPaths.worldData(server, GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
                var target = root.resolve(END).resolve(ID + ".json");
                String good = Files.readString(target);
                var legacy = JsonParser.parseString(good).getAsJsonObject();
                legacy.remove("playerState");
                legacy.remove("gameMode");
                Files.writeString(target, legacy.toString());
                check(DimensionPoolManager.teleportToPool(player, END)
                                && player.getInventory().getItem(0).is(Items.EMERALD)
                                && player.entityTags().isEmpty() && player.getLastDeathLocation().isEmpty(),
                                "legacy player JSON");
                check(DimensionPoolManager.teleportToPool(player, MAIN), "return after loading legacy data");

                var recovery = root.resolve("_recovery").resolve(ID + ".json");
                var source = root.resolve(MAIN).resolve(ID + ".json");
                var preserved = source.resolveSibling("audit-source-backup.json");
                Files.move(source, preserved);
                Files.createDirectory(source);
                Files.writeString(source.resolve("blocked"), "force a write failure");
                try {
                        check(!DimensionPoolManager.teleportToPool(player, END) && player.level() == overworld
                                        && player.getInventory().getItem(0).getCount() == 3 && !Files.exists(recovery),
                                        "source write failure leaves the player intact");
                } finally {
                        Files.delete(source.resolve("blocked"));
                        Files.delete(source);
                        Files.move(preserved, source);
                }
                Files.writeString(target, "{}");
                check(!DimensionPoolManager.teleportToPool(player, END) && player.level() == overworld
                                && player.getInventory().getItem(0).is(Items.DIAMOND),
                                "partial JSON rejected before movement");
                var broken = JsonParser.parseString(good).getAsJsonObject();
                broken.getAsJsonArray("mainInventory").set(0, new com.google.gson.JsonPrimitive("invalid SNBT"));
                String malformedItem = broken.toString();
                Files.writeString(target, malformedItem);
                @SuppressWarnings("null")
                var petBeforeFailure = overworld.getEntity(STANDING_PET);
                check(!DimensionPoolManager.teleportToPool(player, END) && player.level() == overworld
                                && player.getInventory().getItem(0).getCount() == 3
                                && player.getEnchantmentSeed() == 12345
                                && player.entityTags().equals(Set.of("attached"))
                                && player.getLastDeathLocation().orElseThrow().equals(death)
                                && Files.readString(target).equals(malformedItem)
                                && !Files.exists(root.resolve("_recovery").resolve(ID + ".json")),
                                "late failure rolls back and preserves bad input");
                check(petBeforeFailure != null && overworld.getEntity(STANDING_PET) == petBeforeFailure
                                && end.getEntity(STANDING_PET) == null, "failed switch leaves pet unchanged");
                Files.writeString(target, good);
                check(DimensionPoolManager.teleportToPool(player, END)
                                && player.getInventory().getItem(0).is(Items.EMERALD)
                                && player.getEnchantmentSeed() == 54321
                                && player.entityTags().equals(Set.of("end_data"))
                                && PoolRespawnCounts.get(player).orElseThrow() == 1,
                                "fixed target can be entered again");
                check(DimensionPoolManager.teleportToPool(player, MAIN), "return before staging crash recovery");
                Files.createDirectories(recovery.getParent());
                Files.copy(source, recovery, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                server.saveEverything(false, true, true);
                awaitReturnedPetSave(server, STANDING_PET);
                check(!isParked(server, STANDING_PET) && isParked(server, END_PET),
                                "written chunk takes over the restored wolf while the parked wolf retains its archive");
                createColdAnchor(player, end, COLD_ANCHOR);
                createColdAnchor(player, end, DEFERRED_ANCHOR);
                stageColdRelease(player, end);
                stageDeadRelease(server, end);
                detach(player);
                detach(other);
        }

        @SuppressWarnings("null")
        private static void nativePetManagement(MinecraftServer server) throws Exception {
                var level = server.overworld();
                var player = connect(server, level, UUID.randomUUID(), "NativePetAudit", new TestConnection(), false);
                var ally = connect(server, level, UUID.randomUUID(), "NativePetAlly", new TestConnection(), false);
                server.services().nameToIdCache().add(new NameAndId(ally.getGameProfile()));
                var state = PetGlobalState.get(server);
                var commands = server.getCommands().getDispatcher();
                var source = player.createCommandSourceStack();
                trackPetChunk(level, player.blockPosition());

                var cat = EntityTypes.CAT.create(level, EntitySpawnReason.LOAD);
                var pony = EntityTypes.HORSE.create(level, EntitySpawnReason.LOAD);
                var parrot = EntityTypes.PARROT.create(level, EntitySpawnReason.LOAD);
                var nautilus = EntityTypes.NAUTILUS.create(level, EntitySpawnReason.LOAD);
                var donkey = EntityTypes.DONKEY.create(level, EntitySpawnReason.LOAD);
                var camel = EntityTypes.CAMEL.create(level, EntitySpawnReason.LOAD);
                for (Mob animal : List.of(cat, pony, parrot, nautilus, donkey, camel)) {
                        animal.snapTo(player.position(), 0, 0);
                }
                check(level.addFreshEntity(cat) && level.addFreshEntity(pony) && level.addFreshEntity(parrot)
                                && level.addFreshEntity(nautilus) && level.addFreshEntity(camel), "native pet fixtures load");
                String initialName = "狸".repeat(32);
                var originalTag = new ItemStack(Items.NAME_TAG);
                originalTag.set(DataComponents.CUSTOM_NAME, Component.literal(initialName + "花"));
                check(originalTag.getItem().interactLivingEntity(originalTag, player, cat, InteractionHand.MAIN_HAND)
                                == InteractionResult.SUCCESS, "name tags still work before pet registration");
                cat.tame(player);
                parrot.tame(player);
                nautilus.tame(player);
                check(pony.tameWithName(player), "vanilla horse tame succeeds");
                donkey.setOwner(player);
                donkey.setTamed(true);
                check(level.addFreshEntity(donkey), "previously tame donkey loads");
                check(state.findByEntityUUID(donkey.getUUID()).isEmpty(), "old non-wolf data is not imported");
                check(donkey.tameWithName(player), "new donkey tame is registered");
                check(state.findByEntityUUID(camel.getUUID()).isEmpty(), "ownerless camel stays outside the registry");
                check(state.findByOwner(player.getUUID()).size() == 5
                                && state.findByOwner(player.getUUID()).stream().noneMatch(PetEntry::hasCombatMode),
                                "newly tamed non-wolf animals are registered");

                var catEntry = state.findByEntityUUID(cat.getUUID()).orElseThrow();
                check(catEntry.detailKey().equals("entity.minecraft.cat")
                                && catEntry.displayName().equals(initialName) && cat.getName().getString().equals(initialName),
                                "registration truncates the existing name on both the record and the animal");
                var legacy = PetEntry.createNew("DOGLEGACY", UUID.randomUUID(), player.getUUID());
                var legacyNbt = (CompoundTag) PetEntry.CODEC.encodeStart(NbtOps.INSTANCE, legacy).getOrThrow();
                check(!legacyNbt.contains("entityType")
                                && PetEntry.CODEC.parse(NbtOps.INSTANCE, legacyNbt).getOrThrow().hasCombatMode(),
                                "wolf records without a species field remain readable");
                var catNbt = PetEntry.CODEC.encodeStart(NbtOps.INSTANCE, catEntry).getOrThrow();
                check(PetEntry.CODEC.parse(NbtOps.INSTANCE, catNbt).getOrThrow().equals(catEntry), "cat record round-trips");

                String setCat = "yzwc pet set " + catEntry.internalName() + " ";
                check(commands.execute(setCat + "mode attack", source) == 0
                                && commands.execute(setCat + "mode stationary", source) == 0 && cat.isTame(),
                                "cat retains its vanilla behavior and has no custom mode");
                check(commands.execute(setCat + "trust add NativePetAlly", source) == 1
                                && state.findByEntityUUID(cat.getUUID()).orElseThrow().isTrustedOrOwner(ally.getUUID())
                                && commands.execute(setCat + "trust remove NativePetAlly", source) == 1,
                                "trust management works for cats");
                check(commands.execute("yzwc pet highlight " + catEntry.internalName(), source) == 1
                                && cat.hasEffect(MobEffects.GLOWING), "highlight works on a loaded cat");
                String commandName = "X".repeat(31);
                check(commands.execute(setCat + "rename " + "\"" + commandName + "🐈tail\"", source) == 1
                                && cat.getName().getString().equals(commandName),
                                "rename uses the same limit without splitting a surrogate pair");
                var tag = new ItemStack(Items.NAME_TAG);
                tag.set(DataComponents.CUSTOM_NAME, Component.literal("TagName"));
                check(tag.getItem().interactLivingEntity(tag, player, cat, InteractionHand.MAIN_HAND)
                                == InteractionResult.FAIL && tag.getCount() == 1
                                && cat.getName().getString().equals(commandName)
                                && state.findByEntityUUID(cat.getUUID()).orElseThrow().displayName().equals(commandName),
                                "registered pets reject name tags without consuming them");

                var snapshot = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
                cat.saveWithoutId(snapshot);
                cat.discard();
                check(commands.execute(setCat + "rename PendingName", source) == 1
                                && commands.execute("yzwc pet highlight " + catEntry.internalName(), source) == 0
                                && level.getEntity(cat.getUUID()) == null, "unloaded rename only updates registry");
                var loaded = EntityTypes.CAT.create(level, EntitySpawnReason.LOAD);
                loaded.load(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), snapshot.buildResult()));
                check(level.addFreshEntity(loaded) && loaded.getOwner() == player
                                && loaded.getName().getString().equals("PendingName"), "loaded cat applies pending name");
                loaded.setHealth(6);
                var parrotId = parrot.getUUID();
                String setParrot = "yzwc pet set " + state.findByEntityUUID(parrotId).orElseThrow().internalName() + " ";
                var station = parrot.position();
                check(commands.execute(setParrot + "mode stationary", source) == 1
                                && state.findByEntityUUID(parrotId).orElseThrow().station().orElseThrow().equals(station)
                                && parrot.unableToMoveToOwner() && !parrot.setEntityOnShoulder(player),
                                "stationary mode has a persisted center and stops native owner following and perching");
                parrot.setPosRaw(station.x + 2, station.y, station.z);
                check(parrot.distanceToSqr(station) == 4, "stationary pets can roam");
                parrot.setPosRaw(station.x + 12, station.y, station.z);
                check(parrot.distanceToSqr(station) <= 25.000001
                                && !parrot.teleportTo(level, station.x + 20, station.y, station.z, Set.of(), 0, 0, true),
                                "movement and teleport cannot take a stationary pet outside the boundary");
                var stationed = state.findByEntityUUID(parrotId).orElseThrow();
                check(PetEntry.CODEC.parse(NbtOps.INSTANCE,
                                PetEntry.CODEC.encodeStart(NbtOps.INSTANCE, stationed).getOrThrow())
                                .getOrThrow().station().orElseThrow().equals(station), "stationary center survives storage");

                String setDonkey = "yzwc pet set " + state.findByEntityUUID(donkey.getUUID()).orElseThrow().internalName() + " ";
                check(commands.execute(setDonkey + "transfer NativePetAlly", source) == 1
                                && donkey.getOwner() == ally, "transfer updates mount's native owner");
                check(commands.execute(setDonkey + "release_life true", ally.createCommandSourceStack()) == 1
                                && !donkey.isTamed() && donkey.getOwnerReference() == null,
                                "release clears the mount's original ownership");

                var shoulderPet = EntityTypes.PARROT.create(level, EntitySpawnReason.LOAD);
                shoulderPet.snapTo(player.position(), 0, 0);
                check(level.addFreshEntity(shoulderPet), "companion parrot enters the world");
                shoulderPet.tame(player);
                player.setOnGround(true);
                check(shoulderPet.setEntityOnShoulder(player), "companion parrot rides on a shoulder");
                var oldShoulderData = player.getShoulderEntityLeft().copy();
                var sittingCat = EntityTypes.CAT.create(level, EntitySpawnReason.LOAD);
                sittingCat.snapTo(player.position(), 0, 0);
                check(level.addFreshEntity(sittingCat), "sitting cat enters the world");
                sittingCat.tame(player);
                sittingCat.setOrderedToSit(true);
                check(DimensionPoolManager.teleportToPool(player, END) && isParked(server, pony.getUUID())
                                && isParked(server, nautilus.getUUID()) && level.getEntity(parrotId) == parrot
                                && !isParked(server, parrotId)
                                && isParked(server, shoulderPet.getUUID()) && player.getShoulderEntityLeft().isEmpty()
                                && !PoolPets.canLoadShoulder(level, oldShoulderData),
                                "cross-pool parking honors stationary mode and saves shoulder pets without duplicates");
                check(isParked(server, loaded.getUUID()) && level.getEntity(loaded.getUUID()) == null
                                && player.level().getEntity(loaded.getUUID()) == null
                                && level.getEntity(sittingCat.getUUID()) == sittingCat
                                && !isParked(server, sittingCat.getUUID()),
                                "standing cats are parked across pools while sitting cats stay in the original pool");
                check(DimensionPoolManager.teleportToPool(player, MAIN), "original pool can be re-entered");
                awaitPetChunk(level, player.blockPosition());
                PoolPets.tickPendingRestores(server);
                check(level.getEntity(nautilus.getUUID()) == null && isParked(server, nautilus.getUUID()),
                                "nautilus waits for a suitable water destination");
                var water = player.blockPosition().offset(2, 0, 0);
                awaitPetChunk(level, water);
                level.setBlockAndUpdate(water, Blocks.WATER.defaultBlockState());
                level.setBlockAndUpdate(water.above(), Blocks.WATER.defaultBlockState());
                PoolPets.tickPendingRestores(server);
                check(level.getEntity(pony.getUUID()) instanceof net.minecraft.world.entity.animal.equine.Horse horse
                                && horse.getOwner() == player
                                && level.getEntity(nautilus.getUUID()) != null
                                && level.getEntity(shoulderPet.getUUID()) instanceof net.minecraft.world.entity.animal.parrot.Parrot,
                                "cross-pool recall restores the original species and owner");
                var recalledParrot = (net.minecraft.world.entity.animal.parrot.Parrot) level.getEntity(shoulderPet.getUUID());
                player.setOnGround(true);
                check(isParked(server, recalledParrot.getUUID()) && recalledParrot.setEntityOnShoulder(player),
                                "recalled parrot perches before its first entity write");
                var staleShoulder = player.getShoulderEntityLeft().copy();
                var parrotEntry = state.findByEntityUUID(recalledParrot.getUUID()).orElseThrow();
                var shoulderChunk = ChunkPos.containing(player.blockPosition());
                petEntities(level).updateChunkStatus(shoulderChunk, Visibility.TRACKED);
                check(commands.execute("yzwc pet set " + parrotEntry.internalName() + " release_life true",
                                player.createCommandSourceStack()) == 1
                                && state.findByEntityUUID(recalledParrot.getUUID()).isEmpty()
                                && player.getShoulderEntityLeft().isEmpty() && level.getEntity(recalledParrot.getUUID()) == null
                                && !PoolPets.canLoadShoulder(level, staleShoulder),
                                "shoulder release clears the slot, rejects the stale version and waits for entity readiness");
                ((PetShoulderAccessor) player).yzwc$setPetShoulderLeft(staleShoulder);
                DimensionPoolManager.onPlayerJoin(player);
                check(player.getShoulderEntityLeft().isEmpty(), "joining clears a stale player save's shoulder copy");
                awaitPetChunk(level, player.blockPosition());
                PoolPets.tickPendingReleases(server);
                check(level.getEntity(recalledParrot.getUUID()) instanceof net.minecraft.world.entity.animal.parrot.Parrot wild
                                && !wild.isTame() && wild.getOwnerReference() == null,
                                "the matching parrot is released into the original chunk as a wild animal");
                var returnedCat = (Cat) level.getEntity(loaded.getUUID());
                var returnedCatEntry = state.findByEntityUUID(loaded.getUUID()).orElseThrow();
                check(returnedCat != null && returnedCat.getOwner() == player && returnedCat.getHealth() == 6
                                && returnedCat.distanceToSqr(player) < 64
                                && returnedCat.getName().getString().equals("PendingName")
                                && returnedCatEntry.internalName().equals(catEntry.internalName())
                                && returnedCatEntry.availableModes().isEmpty(),
                                "cat recall preserves identity, ownership, health and name without a custom mode");
                returnedCat.hurtServer(level, returnedCat.damageSources().genericKill(), Float.MAX_VALUE);
                check(state.findByEntityUUID(returnedCat.getUUID()).isEmpty(), "dead cat is removed from the registry");
                check(commands.execute(setParrot + "mode companionship", player.createCommandSourceStack()) == 1
                                && state.findByEntityUUID(parrotId).orElseThrow().station().isEmpty(),
                                "companion mode clears the stationary restriction");
                for (Mob animal : List.of(pony, parrot, nautilus, donkey, camel, shoulderPet, sittingCat)) {
                        var active = level.getEntity(animal.getUUID());
                        if (active != null) active.discard();
                        state.removePet(animal.getUUID());
                }
                detach(ally);
                detach(player);
        }

        @SuppressWarnings("null")
        private static void coldCombatSight(ServerPlayer owner) throws Exception {
                var level = owner.level();
                var original = owner.position();
                owner.snapTo(16015.25, 190, 16015.75, 0, 0);
                var wolf = addPet(owner, UUID.randomUUID(), "AUDITCOLDLOS", false);
                var state = PetGlobalState.get(level.getServer());
                state.updatePet(state.findByEntityUUID(wolf.getUUID()).orElseThrow().withMode(PetMode.HUNTING));
                var target = EntityTypes.SKELETON.create(level, EntitySpawnReason.LOAD);
                target.snapTo(16016.75, 190, 16016.25, 0, 0);
                trackPetChunk(level, target.blockPosition());
                check(level.addFreshEntity(target), "cold sight target enters the world");
                var middle = new BlockPos(16015, 190, 16016);
                check(LoadedChunks.get(level, middle) == null, "diagonal line of sight starts with a cold chunk");
                var field = Mob.class.getDeclaredField("targetSelector");
                field.setAccessible(true);
                var selector = (GoalSelector) field.get(wolf);
                owner.setLastHurtMob(target);
                selector.tick();
                check(wolf.getTarget() == null && LoadedChunks.get(level, middle) == null,
                                "hunting treats the missing chunk as an obstruction and never loads it");
                trackPetChunk(level, middle);
                wolf.getSensing().tick();
                selector.tick();
                check(wolf.getTarget() == target, "hunting sees the target once the path is loaded");
                wolf.discard();
                state.removePet(wolf.getUUID());
                target.discard();
                owner.snapTo(original.x, original.y, original.z, 31, 11);
        }

        @SuppressWarnings("null")
        private static void stageColdRelease(ServerPlayer player, ServerLevel origin) throws Exception {
                var server = origin.getServer();
                var state = PetGlobalState.get(server);
                var entry = PetEntry.createNew("AUDITCOLDRELEASE", COLD_RELEASED_PET, player.getUUID());
                state.addPet(entry);
                var archived = EntityTypes.WOLF.create(origin, EntitySpawnReason.LOAD);
                archived.setUUID(COLD_RELEASED_PET);
                archived.setOwnerReference(EntityReference.of(player.getUUID()));
                archived.setTame(true, true);
                archived.snapTo(Vec3.atBottomCenterOf(COLD_RELEASE_POS), 0, 0);
                var output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, origin.registryAccess());
                archived.saveWithoutId(output);
                var root = new CompoundTag();
                root.putString("pool", END);
                root.putString("dimension", origin.dimension().identifier().toString());
                root.putString("token", UUID.randomUUID().toString());
                root.put("wolf", output.buildResult());
                var archive = ModPaths.parkedPet(server, COLD_RELEASED_PET);
                Files.createDirectories(archive.getParent());
                NbtIo.writeCompressed(root, archive);

                int result = server.getCommands().getDispatcher().execute(
                                "yzwc pet set AUDITCOLDRELEASE release_life true", player.createCommandSourceStack());
                PoolPets.tickPendingReleases(server);
                check(result == 1 && state.findByEntityUUID(COLD_RELEASED_PET).isEmpty()
                                && !petEntities(origin).isLoaded(COLD_RELEASED_PET)
                                && LoadedChunks.get(origin, COLD_RELEASE_POS) == null
                                && NbtIo.readCompressed(archive, NbtAccounter.uncompressedQuota()).contains("release"),
                                "cold release records the request without opening or inserting into the chunk");
                // Simulate interruption after the release was committed but before the old registry was written.
                state.addPet(entry);
                server.overworld().getDataStorage().saveAndJoin();
        }

        @SuppressWarnings("null")
        private static void finishColdRelease(MinecraftServer server, ServerLevel origin) throws Exception {
                var state = PetGlobalState.get(server);
                check(state.findByEntityUUID(COLD_RELEASED_PET).isEmpty() && isParked(server, COLD_RELEASED_PET)
                                && LoadedChunks.get(origin, COLD_RELEASE_POS) == null,
                                "restart replays the recorded release without opening a cold chunk");
                awaitPetChunk(origin, COLD_RELEASE_POS);
                check(origin.getEntity(COLD_RELEASED_PET) == null, "ready chunk has no old pet entity");
                PoolPets.tickPendingReleases(server);
                check(origin.getEntity(COLD_RELEASED_PET) instanceof Wolf wolf && !wolf.isTame()
                                && wolf.getOwnerReference() == null && wolf.blockPosition().equals(COLD_RELEASE_POS)
                                && isParked(server, COLD_RELEASED_PET),
                                "deferred release puts the same wild wolf back and retains the archive until the write");
                server.saveEverything(false, true, true);
                awaitReturnedPetSave(server, COLD_RELEASED_PET);
                check(!isParked(server, COLD_RELEASED_PET), "a durable entity write settles the release archive");
        }

        @SuppressWarnings("null")
        private static void stageDeadRelease(MinecraftServer server, ServerLevel origin) throws Exception {
                var owner = connect(server, origin, UUID.randomUUID(), "PetReleaseDeath", new TestConnection(), false);
                owner.snapTo(Vec3.atBottomCenterOf(DEAD_RELEASE_POS), 0, 0);
                awaitPetChunk(origin, DEAD_RELEASE_POS);
                addPet(owner, DEAD_RELEASED_PET, "AUDITRELEASEDEATH", false);
                check(DimensionPoolManager.teleportToPool(owner, MAIN) && isParked(server, DEAD_RELEASED_PET),
                                "wolf has a real parked snapshot");
                awaitPetChunk(origin, DEAD_RELEASE_POS);
                check(server.getCommands().getDispatcher().execute("yzwc pet set AUDITRELEASEDEATH release_life true",
                                owner.createCommandSourceStack()) == 1, "release the parked wolf");
                var wild = (Wolf) origin.getEntity(DEAD_RELEASED_PET);
                check(wild != null && !wild.isTame() && isParked(server, DEAD_RELEASED_PET),
                                "released wolf is still awaiting its first durable write");
                wild.kill(origin);
                check(!wild.isAlive() && !isParked(server, DEAD_RELEASED_PET) && !PoolPets.canLoad(origin, wild)
                                && NbtIo.readCompressed(ModPaths.parkedPet(server, DEAD_RELEASED_PET),
                                                NbtAccounter.uncompressedQuota()).contains("release"),
                                "death clears the snapshot and rejects older entity copies while keeping the receipt");
                wild.remove(net.minecraft.world.entity.Entity.RemovalReason.KILLED);
                detach(owner);
        }

        @SuppressWarnings("null")
        private static void createColdAnchor(ServerPlayer player, ServerLevel end, BlockPos pos) {
                end.getChunkAt(pos);
                check(end.setBlock(pos,
                                ModBlocks.TP_ANCHOR.defaultBlockState().setValue(TeleportAnchorBlock.ACTIVE, true),
                                Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE), "create future cold anchor");
                @SuppressWarnings("null")
                var blockEntity = (TeleportAnchorBlockEntity) end.getBlockEntity(pos);
                blockEntity.addActivator(player.getUUID());
                TeleportAnchorManager.get(end.getServer()).addPointWithName(player, pos,
                                end.dimension(), "Cold anchor", END);
        }

        @SuppressWarnings("null")
        private static void checkColdAnchor(ServerPlayer player, ServerLevel overworld, ServerLevel end) {
                var manager = TeleportAnchorManager.get(end.getServer());
                var point = manager.findPoint(player, COLD_ANCHOR, end.dimension());
                check(point != null && LoadedChunks.get(end, COLD_ANCHOR) == null,
                                "persisted anchor starts unloaded");
                check(!manager.getValidPointsForPlayer(player, overworld.dimension()).contains(point)
                                && manager.getValidPointsForPlayer(player, end.dimension()).contains(point)
                                && LoadedChunks.get(end, COLD_ANCHOR) == null,
                                "listing filters cross-pool anchors and keeps cold same-pool entries without loading");
                check(manager.findNearestActiveAnchorInDimension(player, end.dimension()).orElseThrow().equals(point)
                                && LoadedChunks.get(end, COLD_ANCHOR) == null,
                                "finding the nearest anchor defers chunk access");
                check(manager.removePointByPos(player, COLD_ANCHOR, end.dimension())
                                && manager.findPoint(player, COLD_ANCHOR, end.dimension()) == null
                                && LoadedChunks.get(end, COLD_ANCHOR) == null,
                                "deleting a cold anchor leaves its chunk unloaded");
                end.getChunkAt(COLD_ANCHOR);
                ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(end.getServer());
                @SuppressWarnings("null")
                var entity = (TeleportAnchorBlockEntity) end.getBlockEntity(COLD_ANCHOR);
                check(!entity.isActivatedBy(player.getUUID())
                                && !end.getBlockState(COLD_ANCHOR).getValue(TeleportAnchorBlock.ACTIVE),
                                "deactivation is reconciled when the chunk loads");
                check(LoadedChunks.get(end, DEFERRED_ANCHOR) == null
                                && manager.removePointByPos(player, DEFERRED_ANCHOR, end.dimension())
                                && LoadedChunks.get(end, DEFERRED_ANCHOR) == null,
                                "deferred deletion stays pending through server shutdown");
                boolean[] charged = { false };
                check(!manager.teleportToPoint(player, point, () -> charged[0] = true) && !charged[0],
                                "invalid anchor teleport leaves the source payment untouched");

                var edge = new BlockPos((COLD_ANCHOR.getX() & ~15) + 15, 220, COLD_ANCHOR.getZ());
                var adjacent = edge.east();
                check(LoadedChunks.get(end, adjacent) == null && !LoadedChunks.covers(end, edge, edge, 1),
                                "local writes reject a missing neighbor");
                end.setBlock(edge, Blocks.AIR.defaultBlockState(), Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE);
                var eye = Vec3.atCenterOf(edge);
                var hit = LoadedChunks.collisionView(end).clip(new ClipContext(eye, eye.add(3, 0, 0),
                                ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, player));
                check(hit.getType() == HitResult.Type.BLOCK && hit.getBlockPos().equals(adjacent)
                                && LoadedChunks.get(end, adjacent) == null,
                                "ray stops at the cold chunk boundary without loading it");
        }

        @SuppressWarnings("null")
        private static void restart(MinecraftServer server) throws Exception {
                ServerLevel overworld = server.overworld();
                ServerLevel end = server.getLevel(Level.END);
                check(score(overworld, "Fake", "audit") == 11 && score(end, "Fake", "audit") == 22,
                                "scoreboard survives restart");
                check(overworld.getScoreboard().getPlayersTeam("PoolAudit") != null
                                && end.getScoreboard().getPlayersTeam("PoolOther") != null, "teams survive restart");
                var root = ModPaths.worldData(server, GlobalSettings.DIMENSIONAL_INVENTORIES_MODULE);
                var recovery = root.resolve("_recovery").resolve(ID + ".json");
                check(Files.exists(recovery), "staged transaction survives restart");
                var player = connect(server, end, ID, "PoolAudit", new TestConnection(), true);
                trackPetChunk(overworld, new BlockPos(1, 85, 2));
                trackPetChunk(end, new BlockPos(0, 100, 0));
                DimensionPoolManager.onPlayerJoin(player);
                check(player.level() == overworld && !Files.exists(recovery)
                                && player.getInventory().getItem(0).is(Items.DIAMOND)
                                && player.getEnchantmentSeed() == 12345
                                && player.entityTags().equals(Set.of("attached"))
                                && PoolRespawnCounts.get(player).orElseThrow() == 13,
                                "reconnect recovers source state and clears journal");
                check(DimensionPoolManager.teleportToPool(player, END)
                                && PoolRespawnCounts.get(player).orElseThrow() == 1
                                && player.getEnchantmentSeed() == 54321
                                && ManaManager.getInstance().getMana(end, ID) == 100,
                                "persistent state and runtime mana semantics across restart");
                check(end.getEntity(END_PET) instanceof Wolf returned && returned.getOwner() == player
                                && returned.getHealth() == 13 && returned.distanceToSqr(player) < 64
                                && overworld.getEntity(END_PET) == null,
                                "parked pet survives restart and returns only in its pool");
                check(PoolRespawn.needsSurvivalReturn(player), "death without a pool spawn selects survival");
                checkColdAnchor(player, overworld, end);
                var coldSpawn = new BlockPos(8194, 230, 8194);
                player.setRespawnPosition(new ServerPlayer.RespawnConfig(
                                LevelData.RespawnData.of(end.dimension(), coldSpawn, 0, 0), false), false);
                check(LoadedChunks.get(end, coldSpawn) == null && !PoolRespawn.needsSurvivalReturn(player)
                                && LoadedChunks.get(end, coldSpawn) == null,
                                "death preview defers verification of a cold personal respawn point");
                var connection = player.connection;
                die(player);
                connection.handleClientCommand(new ServerboundClientCommandPacket(
                                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player = connection.player;
                check(player.level() == overworld && player.getInventory().getItem(0).is(Items.DIAMOND)
                                && player.getLastDeathLocation().orElseThrow().dimension() == Level.OVERWORLD,
                                "managed death restores the survival snapshot");
                int levelBefore = player.experienceLevel;
                int cost = InPlaceRespawnManager.getRequiredLevel(player).orElseThrow();
                die(player);
                InPlaceRespawnManager.handleRequest(player);
                connection.handleClientCommand(new ServerboundClientCommandPacket(
                                ServerboundClientCommandPacket.Action.PERFORM_RESPAWN));
                player = connection.player;
                check(player.level() == overworld && player.getInventory().getItem(0).is(Items.DIAMOND)
                                && player.experienceLevel == levelBefore - cost
                                && PoolRespawnCounts.get(player).orElseThrow() == 14,
                                "in-place respawn charges and increments the current pool");
                check(DimensionPoolManager.teleportToPool(player, END)
                                && PoolRespawnCounts.get(player).orElseThrow() == 1,
                                "in-place respawn leaves the other pool count alone");
                checkTransferPayment(server, overworld, end);
                coldPetReturn(server, end);
                loginHallPetReturn(server, end);
                finishColdRelease(server, end);
                awaitPetChunk(end, DEAD_RELEASE_POS);
                PoolPets.tickPendingReleases(server);
                check(end.getEntity(DEAD_RELEASED_PET) == null && !isParked(server, DEAD_RELEASED_PET),
                                "the dead released wolf is not revived after restart");
                stagePetBackup(server, end);
        }

        @SuppressWarnings("null")
        private static void stagePetBackup(MinecraftServer server, ServerLevel end) throws Exception {
                var owner = connect(server, end, RESTORE_OWNER, "PetBackupOwner", new TestConnection(), false);
                owner.snapTo(Vec3.atBottomCenterOf(RESTORE_SAVED_POS), 0, 0);
                awaitPetChunk(end, RESTORE_SAVED_POS);
                addPet(owner, RESTORE_SAVED, "AUDITBACKUPSAVED", true);
                owner.snapTo(Vec3.atBottomCenterOf(RESTORE_PENDING_POS), 0, 0);
                awaitPetChunk(end, RESTORE_PENDING_POS);
                addPet(owner, RESTORE_PENDING, "AUDITBACKUPPENDING", true);
                detach(owner);
                // 关服备份应保存登记，两只宠物的实体仍在各自的区块中。
        }

        @SuppressWarnings("null")
        private static void queuePetBackupRestore(MinecraftServer server, ServerLevel end) throws Exception {
                String moduleId = GlobalSettings.PET_MODULE;
                java.nio.file.Path backup;
                try (var files = Files.list(ModPaths.playerDataBackups(server, moduleId))) {
                        backup = files.filter(file -> file.getFileName().toString().startsWith("shutdown-"))
                                        .max(java.util.Comparator.naturalOrder()).orElseThrow();
                }
                var archive = JsonParser.parseString(BackupArchive.readFirstJson(backup)).getAsJsonObject();
                var parked = archive.getAsJsonObject("data").getAsJsonObject("parked_pets");
                check(!parked.has(RESTORE_SAVED + ".dat") && !parked.has(RESTORE_PENDING + ".dat"),
                                "the shutdown backup has no parked snapshots for the live pets");

                var owner = connect(server, server.overworld(), RESTORE_OWNER, "PetBackupOwner",
                                new TestConnection(), false);
                var state = PetGlobalState.get(server);
                awaitPetChunk(end, RESTORE_SAVED_POS);
                awaitPetChunk(end, RESTORE_PENDING_POS);
                var saved = (Wolf) end.getEntity(RESTORE_SAVED);
                var pending = (Wolf) end.getEntity(RESTORE_PENDING);
                check(saved != null && pending != null, "both backed-up pets were saved as world entities");
                saved.setOrderedToSit(false);
                pending.setOrderedToSit(false);
                saved.setInSittingPose(false);
                pending.setInSittingPose(false);
                PoolPets.afterTransfer(owner, END, List.of(saved, pending), end, pending.position());
                check(saved.isRemoved() && pending.isRemoved(), "both pets were parked before release");
                awaitPetChunk(end, RESTORE_SAVED_POS);
                var pendingChunk = ChunkPos.containing(RESTORE_PENDING_POS);
                petEntities(end).updateChunkStatus(pendingChunk, Visibility.TRACKED);
                check(!end.areEntitiesActuallyLoadedAndTicking(pendingChunk), "pending release chunk cannot tick");
                var commands = server.getCommands().getDispatcher();
                for (UUID id : List.of(RESTORE_SAVED, RESTORE_PENDING)) {
                        String name = state.findByEntityUUID(id).orElseThrow().internalName();
                        check(commands.execute("yzwc pet set " + name + " release_life true",
                                        owner.createCommandSourceStack()) == 1
                                        && NbtIo.readCompressed(ModPaths.parkedPet(server, id),
                                                        NbtAccounter.uncompressedQuota()).contains("release"),
                                        "the real release command writes a receipt for " + name);
                }
                check(end.getEntity(RESTORE_SAVED) instanceof Wolf wild && !wild.isTame(),
                                "the first release places a wild wolf in its ready chunk");
                check(end.getEntity(RESTORE_PENDING) == null, "the second release is waiting for the entity chunk");
                server.saveEverything(false, true, true);
                awaitReturnedPetSave(server, RESTORE_SAVED);
                check(!isParked(server, RESTORE_SAVED), "the first release was durably saved");
                check(isParked(server, RESTORE_PENDING), "the pending release still has its snapshot");
                check(commands.execute("yzwc backup restore " + moduleId + " " + archive.get("id").getAsString(),
                                server.createCommandSourceStack()) == 1, "queue the actual shutdown backup");
                detach(owner);
        }

        @SuppressWarnings("null")
        private static void verifyPetBackupRestore(MinecraftServer server) throws Exception {
                var end = server.getLevel(Level.END);
                var state = PetGlobalState.get(server);
                check(state.findByEntityUUID(RESTORE_SAVED).isPresent()
                                && state.findByEntityUUID(RESTORE_PENDING).isPresent(),
                                "explicit backup restore keeps both registrations despite later release receipts");
                check(LoadedChunks.get(end, RESTORE_SAVED_POS) == null
                                && LoadedChunks.get(end, RESTORE_PENDING_POS) == null,
                                "restoring the pet registry does not open the animals' chunks");
                check(NbtIo.readCompressed(ModPaths.parkedPet(server, COLD_RELEASED_PET),
                                NbtAccounter.uncompressedQuota()).contains("release")
                                && state.findByEntityUUID(COLD_RELEASED_PET).isEmpty(),
                                "a release absent from the restored registry remains effective");
                awaitPetChunk(end, RESTORE_SAVED_POS);
                check(end.getEntity(RESTORE_SAVED) instanceof Wolf saved && saved.isTame()
                                && saved.getOwnerReference().getUUID().equals(RESTORE_OWNER),
                                "the saved entity keeps its valid version and recovers its owner");
                var owner = connect(server, end, RESTORE_OWNER, "PetBackupOwner", new TestConnection(), false);
                owner.snapTo(Vec3.atBottomCenterOf(RESTORE_PENDING_POS), 0, 0);
                awaitPetChunk(end, RESTORE_PENDING_POS);
                check(end.getEntity(RESTORE_PENDING) == null, "the pending archive has no duplicate in the chunk");
                PoolPets.restore(owner);
                check(end.getEntity(RESTORE_PENDING) instanceof Wolf pending && pending.isTame()
                                && pending.getOwnerReference().getUUID().equals(RESTORE_OWNER),
                                "the still-parked snapshot returns as the restored owner's pet");
                detach(owner);
        }

        private static void checkTransferPayment(MinecraftServer server, ServerLevel origin, ServerLevel end)
                        throws Exception {
                var player = connect(server, origin, UUID.randomUUID(), "AnchorPayment", new TestConnection(), false);
                player.giveExperienceLevels(8);
                var destination = new Vec3(1.5, 100, 1.5);
                @SuppressWarnings("null")
                var transition = new TeleportTransition(end, destination, Vec3.ZERO, 0, 0,
                                TeleportTransition.DO_NOTHING);
                Runnable pay = () -> player.giveExperienceLevels(-2);
                check(DimensionPoolManager.onTeleport(player, transition, () -> null, pay) == null
                                && player.level() == origin && player.experienceLevel == 8,
                                "failed cross-pool move restores the source payment");
                check(DimensionPoolManager.onTeleport(player, transition, () -> player.teleport(transition),
                                pay) == player && player.level() == end && player.experienceLevel == 0,
                                "successful charged transfer applies the destination snapshot");
                check(DimensionPoolManager.teleportToPool(player, MAIN) && player.experienceLevel == 6,
                                "successful transfer persists the payment only in the source pool");
                detach(player);
        }

        @SuppressWarnings("null")
        private static void coldPetReturn(MinecraftServer server, ServerLevel end) throws Exception {
                UUID ownerId = UUID.randomUUID();
                UUID petId = UUID.randomUUID();
                var owner = connect(server, end, ownerId, "ColdPetOwner", new TestConnection(), false);
                addPet(owner, petId, "AUDITCOLD", false);
                check(DimensionPoolManager.teleportToPool(owner, MAIN) && isParked(server, petId),
                                "cold return fixture is parked");
                detach(owner);

                var returning = connect(server, end, ownerId, "ColdPetOwner", new TestConnection(), false);
                AuthPlayerHelper.setAuthenticated(returning, false);
                returning.snapTo(16_000.5, 85, 16_000.5, 0, 0);
                var pos = ChunkPos.containing(returning.blockPosition());
                var chunks = end.getChunkSource();
                check(chunks.getChunkNow(pos.x(), pos.z()) == null, "owner chunk starts unloaded");
                DimensionPoolManager.onPlayerJoin(returning);
                PoolPets.tickPendingRestores(server);
                check(end.getEntity(petId) == null && isParked(server, petId)
                                && chunks.getChunkNow(pos.x(), pos.z()) == null,
                                "recall waits without loading the owner chunk");

                trackPetChunk(end, returning.blockPosition());
                check(chunks.getChunkNow(pos.x() - 1, pos.z()) == null, "adjacent chunk starts unloaded");
                PoolPets.tickPendingRestores(server);
                check(end.getEntity(petId) == null && isParked(server, petId),
                                "recall still waits for authentication after the owner chunk loads");
                AuthPlayerHelper.setAuthenticated(returning, true);
                PoolPets.tickPendingRestores(server);
                check(end.getEntity(petId) instanceof Wolf wolf && wolf.getOwner() == returning
                                && wolf.distanceToSqr(returning) < 1
                                && chunks.getChunkNow(pos.x() - 1, pos.z()) == null,
                                "recall completes inside the loaded chunk without loading an adjacent chunk");
                detach(returning);
        }

        @SuppressWarnings("null")
        private static void loginHallPetReturn(MinecraftServer server, ServerLevel end) throws Exception {
                UUID ownerId = UUID.randomUUID();
                UUID petId = UUID.randomUUID();
                var owner = connect(server, end, ownerId, "HallPetOwner", new TestConnection(), false);
                addPet(owner, petId, "AUDITHALL", false);
                check(DimensionPoolManager.teleportToPool(owner, MAIN) && isParked(server, petId),
                                "login hall fixture is parked");
                detach(owner);

                var returning = connect(server, end, ownerId, "HallPetOwner", new TestConnection(), false);
                AuthPlayerHelper.setAuthenticated(returning, false);
                AuthPlayerHelper.saveLocation(returning);
                DimensionPoolManager.onPlayerJoin(returning);
                var hall = server.getLevel(AuthPlayerHelper.LOGIN_HALL_KEY);
                check(returning.teleportTo(hall, 0, 64, 0, Set.of(), 0, 0, true)
                                && returning.level() == hall, "owner enters the login hall");
                PoolPets.tickPendingRestores(server);
                AuthPlayerHelper.setAuthenticated(returning, true);
                PoolPets.tickPendingRestores(server);
                check(hall.getEntity(petId) == null && end.getEntity(petId) == null && isParked(server, petId),
                                "authentication alone does not restore a wolf in the login hall");

                AuthPlayerHelper.restoreLocation(returning);
                check(returning.level() == end, "login returns the owner to the previous pool");
                PoolPets.tickPendingRestores(server);
                check(end.getEntity(petId) instanceof Wolf wolf && wolf.getOwner() == returning,
                                "the pending restore survives a visit to the login hall");
                detach(returning);
        }

        @SuppressWarnings("null")
        private static void badScoreboard(MinecraftServer server) throws Exception {
                ServerLevel end = server.getLevel(Level.END);
                awaitPetChunk(end, COLD_RELEASE_POS);
                check(end.getEntity(COLD_RELEASED_PET) instanceof Wolf wolf && !wolf.isTame()
                                && wolf.getOwnerReference() == null
                                && PetGlobalState.get(server).findByEntityUUID(COLD_RELEASED_PET).isEmpty(),
                                "released wolf is loaded from its entity chunk after a second restart");
                check(LoadedChunks.get(end, DEFERRED_ANCHOR) == null,
                                "pending deactivation starts with its chunk unloaded after restart");
                end.getChunkAt(DEFERRED_ANCHOR);
                ServerTickEvents.END_SERVER_TICK.invoker().onEndTick(server);
                @SuppressWarnings("null")
                var deferredAnchor = (TeleportAnchorBlockEntity) end.getBlockEntity(DEFERRED_ANCHOR);
                check(!deferredAnchor.isActivatedBy(ID)
                                && !end.getBlockState(DEFERRED_ANCHOR).getValue(TeleportAnchorBlock.ACTIVE),
                                "pending deactivation survives server restart");
                var file = ModPaths.poolScoreboard(server, END);
                byte[] broken = Files.readAllBytes(file);
                var player = connect(server, server.overworld(), UUID.randomUUID(), "PoolBadBoard",
                                new TestConnection(), false);
                player.getInventory().setItem(0, new ItemStack(Items.DIAMOND, 3));
                check(!DimensionPoolManager.teleportToPool(player, END) && player.level() == server.overworld()
                                && player.getInventory().getItem(0).getCount() == 3,
                                "corrupt scoreboard cancels transfer");
                server.saveEverything(false, true, true);
                check(java.util.Arrays.equals(Files.readAllBytes(file), broken),
                                "saving preserves the corrupt scoreboard");
                queuePetBackupRestore(server, end);
        }

        private static void die(ServerPlayer player) {
                player.setHealth(0);
                player.die(player.damageSources().genericKill());
        }

        private static final class TestConnection extends Connection {
                final List<Packet<?>> packets = new ArrayList<>();

                TestConnection() {
                        super(PacketFlow.SERVERBOUND);
                }

                @Override
                public void send(@SuppressWarnings("null") Packet<?> packet,
                                @SuppressWarnings("null") ChannelFutureListener listener, boolean flush) {
                        packets.add(packet);
                }

                @Override
                public boolean isConnected() {
                        return true;
                }

                @Override
                public void flushChannel() {
                }

                @SuppressWarnings("null")
                @Override
                public SocketAddress getRemoteAddress() {
                        return new InetSocketAddress("127.0.0.1", 12345);
                }
        }
}
