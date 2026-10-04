package com.prtsnote.airdrop;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Optional two-JVM client probe: persistent crates, contents and absolute smoke deadlines. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropRestartCheck {
    private static final BlockPos NEAR = new BlockPos(0, 180, 0), DEBUG = NEAR.east(3), FAR = new BlockPos(2048, 180, 0);
    private static final java.io.File PROOF = new java.io.File("restart-proof.nbt");
    private static boolean opening, prepared, checkedFar;
    private static volatile boolean done;
    private static volatile String failure;
    private static int clientTicks;
    private static CompoundTag proof;
    private static java.util.UUID id;
    private static long resumedAt;

    private static CompoundTag inventory(AirdropCrateBlockEntity crate) {
        var items = net.minecraft.core.NonNullList.withSize(27, net.minecraft.world.item.ItemStack.EMPTY);
        for (int i = 0; i < 27; i++) items.set(i, crate.getItem(i).copy());
        return net.minecraft.world.ContainerHelper.saveAllItems(new CompoundTag(), items);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent tick) {
        String phase = System.getProperty("airdrop.restartCheck");
        if (phase == null || done || failure != null || tick.phase != TickEvent.Phase.END) return;
        var server = tick.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            var level = server.overworld();
            var manager = AirdropEvents.get(server);
            long now = AirdropEvents.now(server);
            long elapsed = now - resumedAt;
            if (!prepared) {
                var player = server.getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 8, 185, 8, 0, 0);
                if (phase.equals("seed")) {
                    for (var previous : manager.all()) manager.cancel(server, previous.id);
                    var type = AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:mineral"));
                    for (var pos : new BlockPos[]{NEAR, DEBUG, FAR}) {
                        level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
                        level.setBlockAndUpdate(pos.below(), Blocks.STONE.defaultBlockState());
                        if (!AirdropServer.placeCrate(level, pos, type)) throw new IllegalStateException("Seed placement failed");
                    }
                    id = manager.begin(level, NEAR, type);
                    if (id == null) throw new IllegalStateException("Seed event failed");
                    manager.landed(server, id, NEAR);
                    var crate = (AirdropCrateBlockEntity) level.getBlockEntity(NEAR);
                    crate.bindEvent(id);
                    proof = new CompoundTag();
                    proof.putUUID("event", id);
                    proof.put("near", inventory(crate));
                    proof.put("debug", inventory((AirdropCrateBlockEntity) level.getBlockEntity(DEBUG)));
                    proof.put("far", inventory((AirdropCrateBlockEntity) level.getBlockEntity(FAR)));
                    proof.putLong("smoke_ends_at",crate.saveWithFullMetadata().getLong("smoke_ends_at"));
                    NbtIo.writeCompressed(proof, PROOF);
                    LogUtils.getLogger().info("AIRDROP_RESTART_SEED_READY: persistent crates saved; smoke ends at {}",proof.getLong("smoke_ends_at"));
                    done = true;
                } else {
                    proof = NbtIo.readCompressed(PROOF);
                    id = proof.getUUID("event");
                    if (level.hasChunkAt(FAR)) throw new IllegalStateException("Far crate must start unloaded");
                    resumedAt = now;
                    elapsed = 0;
                    LogUtils.getLogger().info("AIRDROP_RESTART_RESUMED: smoke must end at original tick {}",proof.getLong("smoke_ends_at"));
                }
                prepared = true;
            }
            if (phase.equals("seed")) return;
            for (var pos : new BlockPos[]{NEAR, DEBUG}) {
                if (!(level.getBlockEntity(pos) instanceof AirdropCrateBlockEntity crate)) throw new IllegalStateException("Persistent crate disappeared");
                if (!inventory(crate).equals(proof.getCompound(pos.equals(NEAR) ? "near" : "debug"))) throw new IllegalStateException("Inventory changed after restart");
                if (crate.saveWithFullMetadata().getLong("smoke_ends_at") != proof.getLong("smoke_ends_at")) throw new IllegalStateException("Restart reset smoke deadline");
            }
            if (!checkedFar && elapsed >= 1200) {
                var crate = (AirdropCrateBlockEntity) level.getBlockEntity(FAR);
                if (crate == null || !inventory(crate).equals(proof.getCompound("far"))) throw new IllegalStateException("Far inventory changed");
                // Keep the delayed chunk ticking to observe its persisted smoke deadline.
                level.getChunkSource().addRegionTicket(net.minecraft.server.level.TicketType.FORCED,
                        new net.minecraft.world.level.ChunkPos(FAR), 2, new net.minecraft.world.level.ChunkPos(FAR));
                checkedFar = true;
            }
            if (checkedFar && elapsed >= 1220) {
                var crate = (AirdropCrateBlockEntity) level.getBlockEntity(FAR);
                if (crate == null || crate.saveWithFullMetadata().getLong("smoke_ends_at") != proof.getLong("smoke_ends_at")) throw new IllegalStateException("Delayed chunk loading reset smoke deadline");
            }
            if (elapsed > 0 && elapsed % 1200 == 0) LogUtils.getLogger().info("AIRDROP_RESTART_PERSISTENCE: elapsed={}, smoke deadline={}",elapsed,proof.getLong("smoke_ends_at"));
            if (elapsed >= 6002) {
                for (var pos : new BlockPos[]{NEAR, DEBUG, FAR}) {
                    if (!(level.getBlockEntity(pos) instanceof AirdropCrateBlockEntity crate) || crate.emitsSmoke()) throw new IllegalStateException("Crate disappeared or smoke continued after five minutes");
                    if (!inventory(crate).equals(proof.getCompound(pos.equals(NEAR) ? "near" : pos.equals(DEBUG) ? "debug" : "far"))) throw new IllegalStateException("Permanent inventory changed");
                    if (!level.getEntitiesOfClass(ItemEntity.class,new AABB(pos).inflate(3)).isEmpty()) throw new IllegalStateException("Smoke stopping scattered loot");
                }
                if (manager.find(id) != null) throw new IllegalStateException("Completed event was not retired");
                LogUtils.getLogger().info("AIRDROP_RESTART_CHECK_PASSED: real process restart, persistent crates and inventories, original smoke deadlines, delayed chunk loading");
                done = true;
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_RESTART_CHECK_FAILED", error);
            failure = error.toString();
        }
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent tick) {
        if (System.getProperty("airdrop.restartCheck") == null || tick.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        if (++clientTicks > 16000) { LogUtils.getLogger().error("AIRDROP_RESTART_CHECK_FAILED: timeout"); client.stop(); return; }
        if (done || failure != null) { client.stop(); return; }
        if (!opening && client.getOverlay() == null && client.screen instanceof TitleScreen) {
            opening = true;
            client.createWorldOpenFlows().loadLevel(new TitleScreen(), "airdrop-restartcheck");
        }
    }
}
