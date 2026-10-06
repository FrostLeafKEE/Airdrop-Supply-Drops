package com.prtsnote.airdrop;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.client.DescentWindSound;
import com.prtsnote.airdrop.client.SmokeHissSound;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.registry.ModSounds;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Fast opt-in audio lifecycle check in a copy of the development world. */
@Mod.EventBusSubscriber(modid = "airdrop_supply_drops", value = Dist.CLIENT)
public final class AirdropSoundCheck {
    private static final BlockPos GROUND = new BlockPos(0, 180, 0);
    private static boolean opening, prepared, finished;
    private static int timeout;
    private static volatile int landedAge = -1;
    private static volatile String failure;
    private static DescentWindSound wind;
    private static final java.util.List<SmokeHissSound> hisses = new java.util.ArrayList<>();
    private static final boolean[] checks = new boolean[7];

    @SubscribeEvent public static void sound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if (!Boolean.getBoolean("airdrop.soundCheck")) return;
        if (event.getSound() instanceof DescentWindSound sound) wind = sound;
        if (event.getSound() instanceof SmokeHissSound sound) hisses.add(sound);
    }

    @SubscribeEvent public static void serverTick(TickEvent.ServerTickEvent event) {
        if (!Boolean.getBoolean("airdrop.soundCheck") || finished || event.phase != TickEvent.Phase.END) return;
        var server = event.getServer();
        if (server.getPlayerList().getPlayers().isEmpty()) return;
        try {
            var level = server.overworld();
            if (!prepared) {
                var manager = AirdropEvents.get(server);
                for (var previous : manager.all()) manager.cancel(server, previous.id);
                level.setDayTime(6000);
                level.setWeatherParameters(6000, 0, false, false);
                level.setBlockAndUpdate(GROUND.below(), Blocks.STONE.defaultBlockState());
                for (int y = 0; y <= 40; y++) level.setBlockAndUpdate(GROUND.above(y), Blocks.AIR.defaultBlockState());
                var player = server.getPlayerList().getPlayers().get(0);
                player.setGameMode(GameType.SPECTATOR);
                player.teleportTo(level, 4.5, 184, 4.5, 0, 0);
                var drop = ModEntities.FALLING_AIRDROP.get().create(level);
                drop.setPos(.5, 190, .5);
                if (!drop.prepare(AirdropTypes.all().get(new ResourceLocation("airdrop_supply_drops:mineral")))) {
                    throw new IllegalStateException("Cannot prepare audio probe");
                }
                level.addFreshEntity(drop);
                prepared = true;
            }
            if (level.getBlockEntity(GROUND) instanceof AirdropCrateBlockEntity crate) {
                landedAge++;
                if (landedAge == 40) crate.clearContent();
                if (landedAge == 80 || landedAge == 160) {
                    crate.setItem(0, new net.minecraft.world.item.ItemStack(net.minecraft.world.item.Items.DIAMOND));
                    crate.initialize(net.minecraft.network.chat.Component.literal("Sound check"));
                }
                if (landedAge == 120) {
                    var tag = crate.saveWithFullMetadata();
                    tag.putLong("smoke_ends_at", server.overworld().getGameTime());
                    crate.load(tag);
                }
                if (landedAge == 200) level.destroyBlock(GROUND, false);
            } else if (landedAge >= 200) landedAge++;
        } catch (Exception error) { failure = error.toString(); }
    }

    @SubscribeEvent public static void clientTick(TickEvent.ClientTickEvent event) {
        if (!Boolean.getBoolean("airdrop.soundCheck") || finished || event.phase != TickEvent.Phase.END) return;
        var client = Minecraft.getInstance();
        client.options.pauseOnLostFocus = false;
        try {
            if (++timeout > 2400) throw new IllegalStateException("Audio check timed out: " + client.screen + "/" + landedAge);
            if (failure != null) throw new IllegalStateException(failure);
            if (!opening && client.getOverlay() == null && client.screen instanceof TitleScreen) {
                for (var source : new net.minecraft.sounds.SoundSource[]{net.minecraft.sounds.SoundSource.MASTER,
                        net.minecraft.sounds.SoundSource.AMBIENT, net.minecraft.sounds.SoundSource.BLOCKS}) {
                    if (client.options.getSoundSourceVolume(source) == 0) throw new IllegalStateException("Muted category " + source);
                }
                for (var entry : ModSounds.SOUNDS.getEntries()) {
                    var sounds = client.getSoundManager().getSoundEvent(entry.getId());
                    if (sounds == null || sounds.getWeight() == 0) throw new IllegalStateException("Missing sound " + entry.getId());
                    var sound = sounds.getSound(net.minecraft.util.RandomSource.create(0));
                    if (client.getResourceManager().getResource(sound.getPath()).isEmpty()) throw new IllegalStateException("Missing file " + sound.getPath());
                }
                opening = true;
                client.createWorldOpenFlows().loadLevel(new TitleScreen(), "airdrop-soundcheck");
            }
            if (client.level == null || client.player == null) return;
            if (landedAge < 0 && wind != null && wind.getVolume() > .05F) {
                require(hisses.isEmpty(), "No smoke hiss may play during descent");
                checks[0] = true;
            }
            int age = landedAge;
            if (age >= 25 && age < 40) {
                require(wind != null && wind.isStopped(), "Descent wind must stop on landing");
                checkHiss(client, 1, true); checks[1] = true;
            }
            if (age >= 60 && age < 80) { checkHiss(client, 1, false); checks[2] = true; }
            if (age >= 100 && age < 120) { checkHiss(client, 2, true); checks[3] = true; }
            if (age >= 140 && age < 160) { checkHiss(client, 2, false); checks[4] = true; }
            if (age >= 180 && age < 200) { checkHiss(client, 3, true); checks[5] = true; }
            if (age >= 220 && age < 240) { checkHiss(client, 3, false); checks[6] = true; }
            if (age >= 240) {
                for (int i = 0; i < checks.length; i++) require(checks[i], "Audio phase not observed: " + i);
                LogUtils.getLogger().info("AIRDROP_SOUND_CHECK_PASSED: six sounds resolve; wind during descent only; hiss starts at landing, stops on empty/expiry/destruction, restarts without duplicate loops");
                finished = true;
                client.stop();
            }
        } catch (Exception error) {
            LogUtils.getLogger().error("AIRDROP_SOUND_CHECK_FAILED", error);
            finished = true;
            client.stop();
        }
    }

    private static void checkHiss(Minecraft client, int count, boolean active) {
        require(hisses.size() == count, "Unexpected hiss loop count: " + hisses.size() + " expected " + count);
        var sound = hisses.get(count - 1);
        var crate = client.level.getBlockEntity(GROUND);
        require((crate instanceof AirdropCrateBlockEntity landed && landed.isClientSmoking()) == active,
                "Client smoke flag differs from server phase");
        require(sound.isStopped() != active, "Hiss lifetime differs from smoke lifetime");
        if (active) require(sound.getVolume() > .05F && client.getSoundManager().isActive(sound), "Hiss is not audible/active");
        for (int i = 0; i < count - 1; i++) require(hisses.get(i).isStopped(), "Old hiss loop survived");
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new IllegalStateException(message);
    }
}
