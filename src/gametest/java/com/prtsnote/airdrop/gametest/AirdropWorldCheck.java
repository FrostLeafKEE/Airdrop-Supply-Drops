package com.prtsnote.airdrop.gametest;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.client.AircraftEngineSound;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModSounds;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import com.prtsnote.airdrop.world.entity.FallingAirdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Runs only in a copied development world, never packaged into the release jar. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropWorldCheck {
    private static boolean opening, prepared;
    private static volatile boolean finished;
    private static int ticks, pictures;
    private static volatile String failure;
    private static volatile boolean landed;
    private static java.util.UUID eventId;
    private static AircraftEngineSound engine;
    private static int flareSounds, parachuteSounds, landingSounds;
    private static final BlockPos GROUND = new BlockPos(0, 180, 0);

    @SubscribeEvent public static void onSound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if (!Boolean.getBoolean("airdrop.worldCheck") || event.getSound() == null) return;
        if (event.getSound() instanceof AircraftEngineSound sound) engine = sound;
        var id = event.getSound().getLocation();
        if (id.equals(ModSounds.FLARE.getId())) flareSounds++;
        if (id.equals(ModSounds.PARACHUTE.getId())) parachuteSounds++;
        if (id.equals(ModSounds.LANDING.getId())) landingSounds++;
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent tick) {
        if (!Boolean.getBoolean("airdrop.worldCheck") || finished || tick.phase != TickEvent.Phase.END) return;
        var server = tick.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            var level = server.overworld();
            var manager = AirdropEvents.get(server);
            if (!prepared) {
                for (var previous : manager.all()) manager.cancel(server, previous.id);
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                for (int x = -8; x <= 8; x++) for (int z = -8; z <= 8; z++) {
                    level.setBlockAndUpdate(GROUND.offset(x, -1, z), Blocks.STONE.defaultBlockState());
                }
                for (int y = 0; y <= 64; y++) level.setBlockAndUpdate(GROUND.above(y), Blocks.AIR.defaultBlockState());
                var player = server.getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 15, 245, 15, 0, 0);
                eventId = manager.begin(level, GROUND, AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:mineral")));
                if (eventId == null) throw new IllegalStateException("Cannot start preview event");
                prepared = true;
            }
            var event = manager.find(eventId);
            if (event == null) throw new IllegalStateException("Preview event vanished");
            long age = AirdropEvents.now(server) - event.started;
            if (age == 205) {
                var camera = AirdropPlane.visualPosition(event, 210).add(14, 6, 14);
                server.getPlayerList().getPlayers().get(0).teleportTo(level, camera.x, camera.y, camera.z, 0, 0);
            }
            if (age == 235) server.getPlayerList().getPlayers().get(0).teleportTo(level, 8, 244, 8, 0, 0);
            if (age == 450) server.getPlayerList().getPlayers().get(0).teleportTo(level, 4, 183, 4, 0, 0);
            if (age >= 800) {
                if (event.stage != AirdropEvents.Stage.LANDED || level.getBlockEntity(GROUND) == null) {
                    throw new IllegalStateException("Crate did not land");
                }
                landed = true;
            }
        } catch (Exception error) { failure = error.toString(); }
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent tick) {
        if (!Boolean.getBoolean("airdrop.worldCheck") || finished || tick.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        try {
            if (++ticks > 6000) throw new IllegalStateException("World check timed out: " + client.screen);
            if (failure != null) throw new IllegalStateException(failure);
            if (!opening && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                if (client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.MASTER) == 0
                        || client.options.getSoundSourceVolume(net.minecraft.sounds.SoundSource.AMBIENT) == 0) {
                    throw new IllegalStateException("World sound check requires non-zero master and ambient volume");
                }
                for (var entry : ModSounds.SOUNDS.getEntries()) {
                    var sounds = client.getSoundManager().getSoundEvent(entry.getId());
                    if (sounds == null || sounds.getWeight() == 0) throw new IllegalStateException("Missing sound " + entry.getId());
                    var sound = sounds.getSound(net.minecraft.util.RandomSource.create(0));
                    if (client.getResourceManager().getResource(sound.getPath()).isEmpty()) throw new IllegalStateException("Missing audio file " + sound.getPath());
                }
                opening = true;
                client.createWorldOpenFlows().openWorld("airdrop-worldcheck", () -> client.setScreen(new TitleScreen()));
            }
            if (client.level == null || client.player == null) return;
            client.options.hideGui = true;
            Vec3 target = Vec3.atCenterOf(GROUND);
            AirdropPlane plane = null;
            FallingAirdrop drop = null;
            for (var entity : client.level.entitiesForRendering()) {
                if (entity instanceof AirdropPlane found) plane = found;
                if (entity instanceof FallingAirdrop found) drop = found;
            }
            if (drop != null) target = drop.position().add(0, 1.5, 0);
            else if (plane != null) target = plane.visualPosition();
            client.player.lookAt(EntityAnchorArgument.Anchor.EYES, target);
            if (pictures == 0 && plane != null && plane.flightAge() >= 210 && plane.flightAge() < 240) {
                snapshot(client, "airdrop-flight"); pictures++;
            } else if (pictures == 1 && drop != null && drop.deploymentTicks() >= 30) {
                snapshot(client, "airdrop-descent"); pictures++;
            }
            if (landed) {
                if (pictures != 2) throw new IllegalStateException("Missing flight/descent screenshots");
                if (engine == null || !engine.isStopped()) throw new IllegalStateException("Aircraft engine did not start or stop");
                if (flareSounds != 3 || parachuteSounds != 1 || landingSounds != 1) {
                    throw new IllegalStateException("Unexpected sound counts: " + flareSounds + "/" + parachuteSounds + "/" + landingSounds);
                }
                snapshot(client, "airdrop-landed");
                LogUtils.getLogger().info("AIRDROP_WORLD_CHECK_PASSED: sounds resolve; flight, descent and landing rendered; engine stopped after departure");
                finished = true;
                client.stop();
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_WORLD_CHECK_FAILED", error);
            finished = true;
            client.stop();
        }
    }

    private static void snapshot(Minecraft client, String name) throws java.io.IOException {
        try (var screenshot = Screenshot.takeScreenshot(client.getMainRenderTarget())) {
            screenshot.writeToFile(client.gameDirectory.toPath().resolve(name + ".png"));
        }
    }
}
