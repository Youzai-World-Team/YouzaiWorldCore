package top.csituka.youzaiworldcore.dimensionalinventories;

import com.google.gson.JsonParser;
import com.mojang.authlib.GameProfile;
import io.netty.channel.ChannelFutureListener;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import net.fabricmc.api.DedicatedServerModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.Connection;
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
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.entity.Visibility;
import net.minecraft.world.entity.EntityReference;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityTypes;
import net.minecraft.world.entity.animal.wolf.Wolf;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.scores.ScoreHolder;
import top.csituka.youzaiworldcore.account.data.AccountDataStorage;
import top.csituka.youzaiworldcore.account.data.PlayerAccount;
import top.csituka.youzaiworldcore.account.util.AuthPlayerHelper;
import top.csituka.youzaiworldcore.config.GlobalSettings;
import top.csituka.youzaiworldcore.config.ModPaths;
import top.csituka.youzaiworldcore.mana.ManaManager;
import top.csituka.youzaiworldcore.pet.PetEntry;
import top.csituka.youzaiworldcore.pet.PetGlobalState;
import top.csituka.youzaiworldcore.mixin.PlayerEnchantmentSeedAccessor;
import top.csituka.youzaiworldcore.respawn.InPlaceRespawnManager;
import top.csituka.youzaiworldcore.util.DebugLogger;

public final class PoolRuntimeAudit implements DedicatedServerModInitializer {
        private static final DebugLogger.ModuleLogger LOGGER = DebugLogger.SERVER.module("PoolRuntimeAudit");
        private static final String MAIN = "survival_world_pool";
        private static final String END = "test_pool";
        private static final UUID ID = UUID.fromString("c057244f-2bd3-4e87-a20f-512ef55a1c18");
        private static final UUID STANDING_PET = UUID.fromString("66ac221a-c50a-4b79-97e6-2b837c570ebc");
        private static final UUID SITTING_PET = UUID.fromString("3ac58bd6-3647-4281-8c46-bf4e3734c23a");
        private static final UUID END_PET = UUID.fromString("235c6a70-721b-4819-9b6e-cdbe5d36d6d2");
        private static final UUID UNLOADED_PET = UUID.fromString("79dc6671-646d-4e81-b785-f933e075f86c");
        private static final UUID RELEASED_PET = UUID.fromString("8ddce803-0eb9-492c-b61e-cac8f020ca9f");

        @Override
        public void onInitializeServer() {
                ServerLifecycleEvents.SERVER_STARTED.register(server -> {
                        try {
                                switch (System.getProperty("yzwc.pool.audit", "switch")) {
                                        case "switch" -> run(server);
                                        case "restart" -> restart(server);
                                        case "bad-scoreboard" -> badScoreboard(server);
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

        private static void command(MinecraftServer server, String command) {
                server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command);
        }

        private static int score(ServerLevel level, String name, String objective) {
                var board = level.getScoreboard();
                var info = board.getPlayerScoreInfo(ScoreHolder.forNameOnly(name), board.getObjective(objective));
                return info == null ? -999 : info.value();
        }

        private static ServerPlayer connect(MinecraftServer server, ServerLevel level, UUID id, String name,
                        TestConnection connection, boolean load) throws Exception {
                var profile = new GameProfile(id, name);
                var player = new ServerPlayer(server, level, profile, ClientInformation.createDefault());
                if (load) {
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
                var field = ServerLevel.class.getDeclaredField("entityManager");
                field.setAccessible(true);
                var entities = (PersistentEntitySectionManager<?>) field.get(level);
                var chunk = ChunkPos.containing(pos);
                level.getChunk(chunk.x(), chunk.z());
                entities.updateChunkStatus(chunk, Visibility.TICKING);
        }

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

        private static boolean isParked(MinecraftServer server, UUID id) throws Exception {
                var file = ModPaths.parkedPet(server, id);
                return Files.exists(file)
                                && NbtIo.readCompressed(file, NbtAccounter.uncompressedQuota()).contains("wolf");
        }

        private static void run(MinecraftServer server) throws Exception {
                ServerLevel overworld = server.overworld();
                ServerLevel nether = server.getLevel(Level.NETHER);
                ServerLevel end = server.getLevel(Level.END);
                var socket = new TestConnection();
                var otherSocket = new TestConnection();
                var player = connect(server, overworld, ID, "PoolAudit", socket, false);
                var other = connect(server, end, UUID.randomUUID(), "PoolOther", otherSocket, false);
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
                Wolf standing = (Wolf) overworld.getEntity(STANDING_PET);
                addPet(player, RELEASED_PET, "AUDITRELEASE", false);

                check(DimensionPoolManager.teleportToPool(player, END), "first switch to End");
                check(overworld.getEntity(STANDING_PET) == null && end.getEntity(STANDING_PET) == null
                                && standing.isRemoved() && isParked(server, STANDING_PET)
                                && !PoolPets.canLoad(overworld, standing),
                                "standing pet is parked and its stale chunk copy is rejected");
                check(overworld.getEntity(SITTING_PET) == sitting && !isParked(server, SITTING_PET)
                                && !isParked(server, UNLOADED_PET), "sitting and unloaded pets are left alone");
                server.getCommands().performPrefixedCommand(
                                server.createCommandSourceStack().withLevel(end).withEntity(player),
                                "yzwc pet set AUDITRELEASE release_life true");
                check(overworld.getEntity(RELEASED_PET) instanceof Wolf released && !released.isTame()
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
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                server.managedBlock(() -> {
                        try {
                                return !isParked(server, STANDING_PET) || System.nanoTime() >= deadline;
                        } catch (Exception error) {
                                throw new IllegalStateException(error);
                        }
                });
                check(!isParked(server, STANDING_PET) && isParked(server, END_PET),
                                "written chunk takes over the restored wolf while the parked wolf retains its archive");
                detach(player);
                detach(other);
        }

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
                coldPetReturn(server, end);
        }

        private static void coldPetReturn(MinecraftServer server, ServerLevel end) throws Exception {
                UUID ownerId = UUID.randomUUID();
                UUID petId = UUID.randomUUID();
                var owner = connect(server, end, ownerId, "ColdPetOwner", new TestConnection(), false);
                addPet(owner, petId, "AUDITCOLD", false);
                check(DimensionPoolManager.teleportToPool(owner, MAIN) && isParked(server, petId),
                                "cold return fixture is parked");
                detach(owner);

                var returning = connect(server, end, ownerId, "ColdPetOwner", new TestConnection(), false);
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
                check(end.getEntity(petId) instanceof Wolf wolf && wolf.getOwner() == returning
                                && wolf.distanceToSqr(returning) < 1
                                && chunks.getChunkNow(pos.x() - 1, pos.z()) == null,
                                "recall completes inside the loaded chunk without loading an adjacent chunk");
                detach(returning);
        }

        private static void badScoreboard(MinecraftServer server) throws Exception {
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
