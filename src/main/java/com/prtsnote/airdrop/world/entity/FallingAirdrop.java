package com.prtsnote.airdrop.world.entity;

import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import com.prtsnote.airdrop.world.block.AirdropCrateBlock;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;

public final class FallingAirdrop extends Entity {
    private static final double GRAVITY = 0.04;
    private static final double FREE_FALL_SPEED = 0.28;
    private static final double PARACHUTE_SPEED = 0.12;
    private final DescentInterpolation interpolation = new DescentInterpolation();
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }
    private static final EntityDataAccessor<Boolean> FOOD = SynchedEntityData.defineId(FallingAirdrop.class, EntityDataSerializers.BOOLEAN);
    private static final EntityDataAccessor<Integer> DEPLOYMENT = SynchedEntityData.defineId(FallingAirdrop.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<String> APPEARANCE = SynchedEntityData.defineId(FallingAirdrop.class, EntityDataSerializers.STRING);
    public net.minecraft.resources.ResourceLocation appearance() {
        return com.prtsnote.airdrop.data.AirdropAppearance.saved(entityData.get(APPEARANCE), entityData.get(FOOD));
    }
    public boolean isFood() { return appearance().equals(com.prtsnote.airdrop.data.AirdropAppearance.FOOD); }
    public int deploymentTicks() { return entityData.get(DEPLOYMENT); }
    private static final TicketType<java.util.UUID> TICKET = TicketType.create("airdrop_descent", java.util.UUID::compareTo);
    private final NonNullList<ItemStack> contents = NonNullList.withSize(27, ItemStack.EMPTY);
    private Component title = Component.translatable("container.airdrop_supply_drops.airdrop_crate");
    private long expiresAt;
    private com.prtsnote.airdrop.data.AirdropRules.Settings settings;
    private ChunkPos ticketChunk;
    private java.util.UUID eventId;
    public void bindEvent(java.util.UUID id) { eventId = id; }

    public FallingAirdrop(EntityType<? extends FallingAirdrop> type, Level level) {
        super(type, level);
        setNoGravity(false);
    }

    @Override public void lerpTo(double x, double y, double z, float yaw, float pitch, int steps) {
        if (!level().isClientSide) {
            super.lerpTo(x, y, z, yaw, pitch, steps);
            return;
        }
        interpolation.synchronize(new Vec3(x, y, z), steps);
        setRot(yaw, pitch);
    }

    @Override public double lerpTargetX() { return interpolation.target(position()).x; }
    @Override public double lerpTargetY() { return interpolation.target(position()).y; }
    @Override public double lerpTargetZ() { return interpolation.target(position()).z; }

    public boolean prepare(AirdropTypes.Type type) {
        ServerLevel level = (ServerLevel) level();
        LootTable table = com.prtsnote.airdrop.data.AirdropValidation.lootTable(level.getServer(), type.lootTable());
        if (table == LootTable.EMPTY) return false;
        // Freeze the loot at release; /reload must not change an existing drop.
        var inventory = new net.minecraft.world.SimpleContainer(27);
        table.fill(inventory, new LootParams.Builder(level).withParameter(LootContextParams.ORIGIN, position())
                .create(LootContextParamSets.CHEST), random.nextLong());
        for (int i = 0; i < 27; i++) contents.set(i, inventory.getItem(i).copy());
        title = type.name().copy();
        settings = type.settings().resolve();
        entityData.set(FOOD, type.appearance().equals(com.prtsnote.airdrop.data.AirdropAppearance.FOOD));
        entityData.set(APPEARANCE, type.appearance().toString());
        expiresAt = level.getServer().overworld().getGameTime() + 12000;
        return true;
    }

    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(FOOD, false);
        builder.define(DEPLOYMENT, 0);
        builder.define(APPEARANCE, com.prtsnote.airdrop.data.AirdropAppearance.MINERAL.toString());
    }

    @Override
    public void onAddedToWorld() {
        super.onAddedToWorld();
        if (level() instanceof ServerLevel level) {
            ticketChunk = chunkPosition();
            level.getChunkSource().addRegionTicket(TICKET, ticketChunk, 2, getUUID());
        }
    }

    @Override
    public void remove(RemovalReason reason) {
        if (eventId != null && level() instanceof ServerLevel level
                && (reason == RemovalReason.KILLED || reason == RemovalReason.DISCARDED)
                && AirdropEvents.get(level.getServer()).ownsDrop(eventId, getUUID())) {
            AirdropEvents.get(level.getServer()).forget(level.getServer(), eventId);
        }
        if (ticketChunk != null && level() instanceof ServerLevel level) {
            level.getChunkSource().removeRegionTicket(TICKET, ticketChunk, 2, getUUID());
            ticketChunk = null;
        }
        super.remove(reason);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            Vec3 position = interpolation.tick(position());
            setPos(position.x, position.y, position.z);
            return;
        }
        if (!(level() instanceof ServerLevel level)) return;
        if (eventId != null && !AirdropEvents.get(level.getServer()).ownsDrop(eventId, getUUID())) {
            discard();
            return;
        }
        if (level.getServer().overworld().getGameTime() >= expiresAt || getY() < level.getMinBuildHeight()) {
            discard();
            return;
        }
        int deployment = Math.min(30, deploymentTicks() + 1);
        entityData.set(DEPLOYMENT, deployment);
        if (deployment == 11) {
            level.playSound(null, getX(), getY(), getZ(), com.prtsnote.airdrop.registry.ModSounds.PARACHUTE.get(),
                    net.minecraft.sounds.SoundSource.AMBIENT, 5F, 1F);
        }
        double opening = Math.max(0, deployment - 10) / 20.0;
        double speedLimit = FREE_FALL_SPEED + (PARACHUTE_SPEED - FREE_FALL_SPEED) * opening;
        double fallingSpeed = Math.max(-speedLimit, getDeltaMovement().y - GRAVITY);
        setDeltaMovement(0, fallingSpeed, 0);
        move(MoverType.SELF, getDeltaMovement());
        if (tickCount % 4 == 0) {
            com.prtsnote.airdrop.server.AirdropSmoke.emit(level, getX(), getY() + 0.6, getZ(), true);
        }
        BlockPos pos = BlockPos.containing(getX(), getY(), getZ());
        // If restored inside a liquid column, surface above it without replacing liquid blocks.
        while (!level.isOutsideBuildHeight(pos) && !level.getFluidState(pos).isEmpty()) pos = pos.above();
        boolean fluidLanding = !level.getFluidState(pos.below()).isEmpty();
        if (!onGround() && !fluidLanding) return;
        if (fluidLanding && settings != null && !settings.allowLiquidLanding()) { discard(); return; }
        if (!level.isOutsideBuildHeight(pos) && level.getWorldBorder().isWithinBounds(pos)
                && level.getFluidState(pos).isEmpty() && level.getBlockState(pos).canBeReplaced()
                && level.getBlockEntity(pos) == null
                && (fluidLanding || (level.getFluidState(pos).isEmpty()
                && level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP)))
                && level.setBlock(pos, ModBlocks.AIRDROP_CRATE.get().defaultBlockState().setValue(AirdropCrateBlock.FOOD, isFood()), 3)
                && level.getBlockEntity(pos) instanceof AirdropCrateBlockEntity crate) {
            for (int i = 0; i < 27; i++) crate.setItem(i, contents.get(i).copy());
            crate.setAppearance(appearance());
            crate.initialize(title, settings == null ? com.prtsnote.airdrop.data.AirdropRules.defaults() : settings);
            level.playSound(null, pos, com.prtsnote.airdrop.registry.ModSounds.LANDING.get(),
                    net.minecraft.sounds.SoundSource.BLOCKS, 3F, 1F);
            if (eventId != null) {
                AirdropEvents.get(level.getServer()).landed(level.getServer(), eventId, pos);
                crate.bindEvent(eventId);
            }
        }
        // An obstructed landing is cancelled, never overwrites an existing container.
        discard();
    }

    private HolderLookup.Provider registries() {
        return level().registryAccess();
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        ContainerHelper.saveAllItems(tag, contents, registries());
        tag.put("title", ComponentSerialization.CODEC
                .encodeStart(registries().createSerializationContext(NbtOps.INSTANCE), title).getOrThrow());
        tag.putLong("expires_at", expiresAt);
        tag.putBoolean("food", isFood());
        tag.putString("appearance", appearance().toString());
        tag.putInt("deployment", deploymentTicks());
        if (settings != null) tag.put("airdrop_settings", settings.save());
        if (eventId != null) tag.putUUID("event_id", eventId);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        HolderLookup.Provider registries = registries();
        contents.clear();
        ContainerHelper.loadAllItems(tag, contents, registries);
        Component savedTitle = ComponentSerialization.CODEC
                .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("title"))
                .result().orElse(null);
        if (savedTitle != null) title = savedTitle;
        expiresAt = tag.getLong("expires_at");
        if (!level().isClientSide) settings = com.prtsnote.airdrop.data.AirdropRules.Settings.load(tag.getCompound("airdrop_settings"));
        entityData.set(FOOD, tag.getBoolean("food"));
        entityData.set(APPEARANCE, com.prtsnote.airdrop.data.AirdropAppearance.saved(
                tag.getString("appearance"), tag.getBoolean("food")).toString());
        entityData.set(DEPLOYMENT, Math.max(0, Math.min(30, tag.getInt("deployment"))));
        // Earlier builds saved NoGravity=true; resumed crates now use the capped gravity motion too.
        setNoGravity(false);
        eventId = tag.hasUUID("event_id") ? tag.getUUID("event_id") : null;
    }
}
