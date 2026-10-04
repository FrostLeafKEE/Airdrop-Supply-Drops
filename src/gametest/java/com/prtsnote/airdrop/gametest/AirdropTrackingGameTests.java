package com.prtsnote.airdrop.gametest;

import com.mojang.authlib.GameProfile;
import com.prtsnote.airdrop.registry.ModEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.server.level.ChunkTrackingView;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraftforge.gametest.GameTestHolder;

import java.util.ArrayList;
import java.util.Set;
import java.util.UUID;

@GameTestHolder("airdrop_supply_drops")
public final class AirdropTrackingGameTests {
    @GameTest(template = "airdrop_supply_drops:empty")
    public static void vanillaTrackerKeepsNearAircraftPaired(GameTestHelper helper) throws Exception {
        var level = helper.getLevel();
        var plane = ModEntities.PLANE.get().create(level);
        plane.configure(UUID.randomUUID(),helper.absolutePos(new BlockPos(2,2,2)),90,0);
        var observer = new ServerPlayer(level.getServer(),level,new GameProfile(UUID.randomUUID(),"AircraftProbe"),
                new net.minecraft.server.level.ClientInformation("en_us",32,ChatVisiblity.FULL,true,0,HumanoidArm.RIGHT,false,true));
        var packets = new ArrayList<Packet<?>>();
        observer.connection = new ServerGamePacketListenerImpl(level.getServer(),new Connection(PacketFlow.SERVERBOUND),
                observer,CommonListenerCookie.createInitial(observer.getGameProfile(),false)) {
            @Override public void send(Packet<?> packet) { packets.add(packet); }
        };
        // Exercise the actual vanilla tracking decision and pairing packets, rather than
        // duplicating its range calculation. Reflection is confined to this test-only probe.
        var type = Class.forName("net.minecraft.server.level.ChunkMap$TrackedEntity");
        var constructor = type.getDeclaredConstructors()[0];
        constructor.setAccessible(true);
        var tracker = constructor.newInstance(level.getChunkSource().chunkMap,plane,512,3,false);
        var update = type.getDeclaredMethod("updatePlayer",ServerPlayer.class);
        update.setAccessible(true);
        var recipients = type.getDeclaredField("seenBy");
        recipients.setAccessible(true);
        try {
            // GameTest uses a short server view distance; these hover distances remain
            // inside it, while the old stationary anchor would still be 600 blocks away.
            for (int distance : new int[]{24,8,4,1,4,8,24}) {
                observer.setPos(plane.visualPosition().add(distance,-4,0));
                observer.setLastSectionPos(SectionPos.of(observer.blockPosition()));
                observer.setChunkTrackingView(ChunkTrackingView.of(new ChunkPos(observer.blockPosition()),32));
                update.invoke(tracker,observer);
                helper.assertTrue(((Set<?>) recipients.get(tracker)).contains(observer.connection),
                        "Approaching the visible aircraft must retain vanilla entity tracking at distance " + distance);
            }
            helper.assertTrue(packets.stream().noneMatch(packet -> packet instanceof ClientboundRemoveEntitiesPacket),
                    "Hovering near the aircraft must not send a removal packet that also stops engine sound");
            observer.setPos(plane.visualPosition().add(700,0,0));
            observer.setLastSectionPos(SectionPos.of(observer.blockPosition()));
            observer.setChunkTrackingView(ChunkTrackingView.of(new ChunkPos(observer.blockPosition()),32));
            update.invoke(tracker,observer);
            helper.assertTrue(((Set<?>) recipients.get(tracker)).isEmpty()
                            && packets.stream().anyMatch(packet -> packet instanceof ClientboundRemoveEntitiesPacket),
                    "Tracking must still end when a player really leaves the aircraft's range");
        } finally {
            var remove = type.getDeclaredMethod("removePlayer",ServerPlayer.class);
            remove.setAccessible(true);
            remove.invoke(tracker,observer);
        }
        helper.succeed();
    }
}
