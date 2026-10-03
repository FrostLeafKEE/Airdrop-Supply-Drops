package com.prtsnote.airdrop.world.entity;

import com.prtsnote.airdrop.server.AirdropEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import java.util.UUID;

/** Anchored in the landing chunk; the renderer projects the flight path. */
public final class AirdropPlane extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEADING = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.FLOAT);
    private UUID eventId;
    private final FlightAnimationClock animationClock = new FlightAnimationClock();
    public AirdropPlane(EntityType<? extends AirdropPlane> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) { builder.define(AGE, 0); builder.define(HEADING, 0F); }
    public int flightAge() { return entityData.get(AGE); }
    public float heading() { return entityData.get(HEADING); }
    public double visualFlightAge(float partialTick) {
        return level().isClientSide ? animationClock.sample(partialTick) : flightAge();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if (AGE.equals(key) && level().isClientSide) animationClock.synchronize(flightAge());
    }
    public Vec3 visualPosition() {
        double angle = Math.toRadians(heading());
        double offset = visualFlightAge(1) - AirdropEvents.RELEASE_TICK;
        return position().add(Math.sin(angle) * offset, 0, Math.cos(angle) * offset);
    }
    public void configure(UUID id, BlockPos ground, float heading, int age) {
        eventId = id;
        entityData.set(AGE, age);
        entityData.set(HEADING, heading);
        setPos(ground.getX() + 0.5, ground.getY() + 60, ground.getZ() + 0.5);
    }
    @Override public void tick() {
        super.tick();
        if (level().isClientSide) {
            animationClock.tick();
            return;
        }
        if (!(level() instanceof ServerLevel level)) return;
        var event = eventId == null ? null : AirdropEvents.get(level.getServer()).find(eventId);
        if (event == null || !event.planeId.equals(getUUID())) { discard(); return; }
        long age = AirdropEvents.now(level.getServer()) - event.started;
        if (age >= AirdropEvents.DEPARTURE_TICK) { discard(); return; }
        entityData.set(AGE, (int) age);
    }
    public static Vec3 visualPosition(AirdropEvents.Event event, double age) {
        double angle = Math.toRadians(event.heading);
        double offset = age - AirdropEvents.RELEASE_TICK;
        return new Vec3(event.ground.getX() + 0.5 + Math.sin(angle) * offset,
                event.ground.getY() + 60, event.ground.getZ() + 0.5 + Math.cos(angle) * offset);
    }
    public static void emitFlares(ServerLevel level, AirdropEvents.Event event, long age) {
        if (age % 2 != 0) return;
        for (int round = 0; round < event.flares; round++) {
            int emittedAt = 120 + round * 40;
            double elapsed = age - emittedAt;
            if (elapsed < 0 || elapsed > 38) continue;
            Vec3 origin = visualPosition(event, emittedAt);
            if (elapsed == 0) {
                level.playSound(null, origin.x, origin.y, origin.z,
                        com.prtsnote.airdrop.registry.ModSounds.FLARE.get(), net.minecraft.sounds.SoundSource.AMBIENT, 16F, 0.8F);
            }
            double angle = Math.toRadians(event.heading);
            for (int side : new int[]{-1, 1}) {
                for (int fan = 0; fan < 3; fan++) {
                    double outward = side * (2 + elapsed * (0.25 + fan * 0.12));
                    double x = origin.x + Math.cos(angle) * outward;
                    double z = origin.z - Math.sin(angle) * outward;
                    double y = origin.y - elapsed * 0.12 - elapsed * elapsed * 0.006;
                    for (var player : level.players()) {
                        if (player.distanceToSqr(x, y, z) > 512 * 512) continue;
                        level.sendParticles(player, ParticleTypes.FLAME, true, x, y, z, 2, 0.08, 0.08, 0.08, 0.005);
                        level.sendParticles(player, ParticleTypes.SMOKE, true, x, y + 0.15, z, 1, 0.12, 0.1, 0.12, 0.01);
                    }
                }
            }
        }
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        if (eventId != null) tag.putUUID("event_id", eventId);
        tag.putInt("flight_age", flightAge()); tag.putFloat("heading", heading());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        eventId = tag.hasUUID("event_id") ? tag.getUUID("event_id") : null;
        entityData.set(AGE, tag.getInt("flight_age")); entityData.set(HEADING, tag.getFloat("heading"));
    }
    // Vanilla entity tracking sends non-default synced data in a separate metadata packet
    // alongside the spawn packet; no custom spawn payload is needed.
}
