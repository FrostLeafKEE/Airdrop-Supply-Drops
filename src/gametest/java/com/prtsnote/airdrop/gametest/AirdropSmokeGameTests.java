package com.prtsnote.airdrop.gametest;

import com.mojang.authlib.GameProfile;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.particle.SmokeParticleOptions;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundLevelParticlesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.block.Blocks;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.UUID;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropSmokeGameTests {
    @GameTest(template = "airdrop_supply_drops:empty", timeoutTicks = 40)
    public static void serverControlsSmokeVisibilityAndPacketColor(GameTestHelper helper) {
        helper.succeedWhen(() -> {
            var level = helper.getLevel();
            // The real crate ticker emits once every four world ticks.
            helper.assertTrue(level.getGameTime() % 4 == 0, "Waiting for the smoke emission tick");
            var previousColor = AirdropConfig.SMOKE_COLOR.get();
            var previousAirborne = AirdropConfig.AIRBORNE_SMOKE_ENABLED.get();
            var observer = new net.minecraft.server.level.ServerPlayer(level.getServer(), level,
                    new GameProfile(UUID.randomUUID(), "SmokeProbe"), net.minecraft.server.level.ClientInformation.createDefault());
            var previousConnection = observer.connection;
            var packets = new ArrayList<ClientboundLevelParticlesPacket>();
            observer.connection = new ServerGamePacketListenerImpl(level.getServer(), new Connection(PacketFlow.SERVERBOUND),
                    observer, CommonListenerCookie.createInitial(observer.getGameProfile(), false)) {
                @Override public void send(Packet<?> packet) {
                    if (packet instanceof ClientboundLevelParticlesPacket particle && particle.getParticle() instanceof SmokeParticleOptions) {
                        packets.add(particle);
                    }
                }
            };
            level.players().add(observer);
            try {
                helper.assertTrue(!AirdropConfig.AIRBORNE_SMOKE_ENABLED.getDefault(), "Airborne smoke must default to disabled");
                helper.assertTrue(AirdropConfig.SMOKE_COLOR.getDefault().equals("#FF0000"), "Default smoke must remain red");
                AirdropConfig.SMOKE_COLOR.set("#00ff00");
                AirdropConfig.AIRBORNE_SMOKE_ENABLED.set(false);
                var pos = helper.absolutePos(new BlockPos(2, 2, 2));
                observer.setPos(pos.getX(), pos.getY(), pos.getZ());
                var type = AirdropTypes.all().get(ResourceLocation.parse("airdrop_supply_drops:mineral"));
                var drop = ModEntities.FALLING_AIRDROP.get().create(level);
                helper.assertTrue(drop.prepare(type), "Probe cargo must prepare");
                drop.setPos(pos.getX(), pos.getY() + 40, pos.getZ());
                drop.tickCount = 4;
                drop.tick();
                helper.assertTrue(packets.isEmpty(), "Descending crates must emit no smoke by default");

                AirdropConfig.AIRBORNE_SMOKE_ENABLED.set(true);
                drop.tickCount = 8;
                drop.tick();
                helper.assertTrue(packets.size() == 1, "Enabling airborne smoke must update an existing descent");
                var greenPacket = packets.get(0);
                helper.assertTrue(greenPacket.getCount() == 6, "Smoke density must be preserved");

                // Decode the complete vanilla network packet, including the registered custom particle payload.
                var buffer = new RegistryFriendlyByteBuf(Unpooled.buffer(), level.registryAccess());
                try {
                    ClientboundLevelParticlesPacket.STREAM_CODEC.encode(buffer, greenPacket);
                    var decoded = ClientboundLevelParticlesPacket.STREAM_CODEC.decode(buffer);
                    var color = (SmokeParticleOptions) decoded.getParticle();
                    helper.assertTrue(color.color() == 0x00FF00 && color.red() == 0 && color.green() == 1 && color.blue() == 0,
                            "The receiving client must obtain the server's green RGB color");
                } finally { buffer.release(); }

                AirdropConfig.SMOKE_COLOR.set("#0000FF");
                drop.tickCount = 12;
                drop.tick();
                helper.assertTrue(packets.size() == 2 && ((SmokeParticleOptions) packets.get(1).getParticle()).color() == 0x0000FF,
                        "New particles must use changed server settings without restarting the delivery");
                helper.assertTrue(((SmokeParticleOptions) greenPacket.getParticle()).color() == 0x00FF00,
                        "Particles already sent must retain their color");

                AirdropConfig.AIRBORNE_SMOKE_ENABLED.set(false);
                helper.setBlock(new BlockPos(2, 1, 2), Blocks.STONE);
                helper.assertTrue(AirdropServer.placeCrate(level, pos, type), "Probe crate must place");
                var crate = (AirdropCrateBlockEntity) level.getBlockEntity(pos);
                packets.clear();
                AirdropCrateBlockEntity.serverTick(level, pos, crate.getBlockState(), crate);
                helper.assertTrue(packets.size() == 1 && ((SmokeParticleOptions) packets.get(0).getParticle()).color() == 0x0000FF,
                        "Landed crates must keep emitting the server-selected color with airborne smoke disabled");
                var landedPacket = packets.get(0);
                helper.assertTrue(landedPacket.getXDist() < 0.1F && landedPacket.getZDist() < 0.1F && landedPacket.getMaxSpeed() == 0,
                        "The server must keep landed smoke close to the crate instead of scattering it sideways");
                var saved = crate.saveWithFullMetadata(level.registryAccess());
                helper.assertTrue(saved.getLong("smoke_ends_at") == level.getServer().overworld().getGameTime() + 6000,
                        "A newly landed crate must receive a five-minute smoke deadline");
                saved.putLong("smoke_ends_at", level.getServer().overworld().getGameTime());
                crate.loadWithComponents(saved, level.registryAccess());
                packets.clear();
                AirdropCrateBlockEntity.serverTick(level,pos,crate.getBlockState(),crate);
                helper.assertTrue(packets.isEmpty() && !crate.isEmpty() && level.getBlockEntity(pos) == crate,
                        "Five minutes after landing, particle packets must stop while supplies and crate remain");
                for (int slot = 0; slot < crate.getContainerSize(); slot++) crate.removeItemNoUpdate(slot);
                packets.clear();
                AirdropCrateBlockEntity.serverTick(level, pos, crate.getBlockState(), crate);
                helper.assertTrue(packets.isEmpty() && level.getBlockEntity(pos) == crate, "An emptied crate must stop smoke and remain");
            } finally {
                level.players().remove(observer);
                observer.connection = previousConnection;
                AirdropConfig.SMOKE_COLOR.set(previousColor);
                AirdropConfig.AIRBORNE_SMOKE_ENABLED.set(previousAirborne);
            }
        });
    }
}
