package com.prtsnote.airdrop.world.entity;

import com.prtsnote.airdrop.server.AirdropEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import java.util.UUID;

/** The tracked entity follows the flight path; clients smooth the same world-space path. */
public final class AirdropPlane extends Entity {
    private static final EntityDataAccessor<Integer> AGE = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> HEADING = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<BlockPos> GROUND = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.BLOCK_POS);
    private static final EntityDataAccessor<Boolean> CONFIGURED = SynchedEntityData.defineId(AirdropPlane.class, EntityDataSerializers.BOOLEAN);
    private UUID eventId;
    private final FlightAnimationClock animationClock = new FlightAnimationClock();
    public AirdropPlane(EntityType<? extends AirdropPlane> type, Level level) {
        super(type, level);
        noPhysics = true;
        setNoGravity(true);
    }
    @Override protected void defineSynchedData() {
        entityData.define(AGE, 0); entityData.define(HEADING, 0F);
        entityData.define(GROUND, BlockPos.ZERO); entityData.define(CONFIGURED, false);
    }
    public int flightAge() { return entityData.get(AGE); }
    public float heading() { return entityData.get(HEADING); }
    public double visualFlightAge(float partialTick) {
        return level().isClientSide ? animationClock.sample(partialTick) : flightAge();
    }
    @Override public void onSyncedDataUpdated(EntityDataAccessor<?> key) {
        super.onSyncedDataUpdated(key);
        if ((AGE.equals(key) || CONFIGURED.equals(key)) && level().isClientSide) animationClock.synchronize(flightAge());
    }
    public Vec3 visualPosition() {
        return visualPosition(1);
    }
    public Vec3 visualPosition(float partialTick) {
        if (!entityData.get(CONFIGURED)) return getPosition(partialTick);
        return flightPosition(visualFlightAge(partialTick));
    }
    private Vec3 flightPosition(double age) {
        var ground = entityData.get(GROUND);
        double angle = Math.toRadians(heading());
        double offset = age - AirdropEvents.RELEASE_TICK;
        return new Vec3(ground.getX() + 0.5 + Math.sin(angle) * offset,
                ground.getY() + 60, ground.getZ() + 0.5 + Math.cos(angle) * offset);
    }
    public void configure(UUID id, BlockPos ground, float heading, int age) {
        eventId = id;
        entityData.set(GROUND, ground.immutable());
        entityData.set(AGE, age);
        entityData.set(HEADING, heading);
        entityData.set(CONFIGURED, true);
        setPos(flightPosition(age));
    }
    public void advanceTo(int age) {
        entityData.set(AGE, age);
        setPos(flightPosition(age));
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
        advanceTo((int) age);
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
            int emittedAt = AirdropEvents.FLARE_FIRST_TICK + round * AirdropEvents.FLARE_INTERVAL;
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
        tag.putInt("flight_path_version", AirdropEvents.FLIGHT_PATH_VERSION);
        tag.putLong("flight_origin", entityData.get(GROUND).asLong());
        tag.putInt("flight_age", flightAge()); tag.putFloat("heading", heading());
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        eventId = tag.hasUUID("event_id") ? tag.getUUID("event_id") : null;
        int age = tag.getInt("flight_age");
        if (!tag.contains("flight_path_version")) age += AirdropEvents.LEGACY_FLIGHT_AGE_OFFSET;
        // Before 1.0.11 the saved entity stayed above the landing point, not on the visible path.
        entityData.set(GROUND, tag.contains("flight_origin") ? BlockPos.of(tag.getLong("flight_origin"))
                : BlockPos.containing(getX(), getY() - 60, getZ()));
        entityData.set(AGE, age); entityData.set(HEADING, tag.getFloat("heading"));
        entityData.set(CONFIGURED, true);
        setPos(flightPosition(age));
    }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }
}
