package com.prtsnote.airdrop.world.entity;

import com.prtsnote.airdrop.config.AirdropConfig;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ItemSupplier;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/** Visual/combat projectile only: the item commits the delivery once, before the flare flies. */
public final class SignalFlare extends Projectile implements ItemSupplier {
    private double range = 48, travelled, radius = 3;
    private float damage = 3;
    private int burnSeconds = 5, life;
    private boolean burst;

    public SignalFlare(EntityType<? extends SignalFlare> type, Level level) {
        super(type, level);
        setNoGravity(true);
    }
    public void launch(Player player) {
        setOwner(player);
        range = AirdropConfig.SIGNAL_RANGE.get();
        radius = AirdropConfig.SIGNAL_RADIUS.get();
        damage = AirdropConfig.SIGNAL_DAMAGE.get().floatValue();
        burnSeconds = AirdropConfig.SIGNAL_BURN_SECONDS.get();
        Vec3 aim = player.getLookAngle();
        setPos(player.getEyePosition().add(aim.scale(.2)).add(0, -.1, 0));
        shoot(aim.x, aim.y, aim.z, AirdropConfig.SIGNAL_SPEED.get().floatValue(), 0);
    }
    @Override protected void defineSynchedData(SynchedEntityData.Builder builder) {}
    @Override public ItemStack getItem() { return new ItemStack(Items.FIRE_CHARGE); }
    @Override public boolean shouldRenderAtSqrDistance(double distance) { return distance < 256 * 256; }

    @Override public void tick() {
        super.tick();
        if (isRemoved()) return;
        life++;
        Vec3 start = position(), movement = getDeltaMovement();
        if (!level().isClientSide) {
            double remaining = range - travelled;
            if (remaining <= 0 || life > 1200 || movement.lengthSqr() < .000001) { detonate(); return; }
            if (movement.length() > remaining) {
                movement = movement.normalize().scale(remaining);
                setDeltaMovement(movement);
            }
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS && !net.minecraftforge.event.ForgeEventFactory.onProjectileImpact(this, hit)) {
                travelled += start.distanceTo(hit.getLocation());
                setPos(hit.getLocation());
                onHit(hit);
                return;
            }
        }
        setPos(start.add(movement));
        updateRotation();
        travelled += movement.length();
        if (level().isClientSide) {
            level().addParticle(ParticleTypes.FLAME, getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.SMOKE, getX(), getY(), getZ(), 0, .005, 0);
        } else if (travelled >= range - .00001) detonate();
    }
    @Override protected void onHit(HitResult hit) {
        super.onHit(hit);
        if (level().isClientSide) return;
        if (hit instanceof EntityHitResult impact && impact.getEntity() instanceof LivingEntity living && canAffect(living)) {
            living.igniteForSeconds(burnSeconds);
        }
        detonate();
    }
    private boolean canAffect(LivingEntity entity) {
        if (!entity.isAlive() || entity.isSpectator()) return false;
        if (entity instanceof Player victim && getOwner() instanceof Player attacker && victim != attacker) {
            return level().getServer().isPvpAllowed() && attacker.canHarmPlayer(victim);
        }
        return true;
    }
    public void detonate() {
        if (burst || !(level() instanceof ServerLevel server)) return;
        burst = true;
        for (LivingEntity target : server.getEntitiesOfClass(LivingEntity.class, getBoundingBox().inflate(radius), this::canAffect)) {
            var box = target.getBoundingBox();
            Vec3 closest = new Vec3(net.minecraft.util.Mth.clamp(getX(), box.minX, box.maxX),
                    net.minecraft.util.Mth.clamp(getY(), box.minY, box.maxY), net.minecraft.util.Mth.clamp(getZ(), box.minZ, box.maxZ));
            if (closest.distanceToSqr(position()) <= radius * radius
                    && net.minecraft.world.level.Explosion.getSeenPercent(position(), target) > 0) {
                target.hurt(damageSources().explosion(this, getOwner()), damage);
            }
        }
        for (var player : server.players()) {
            if (player.distanceToSqr(this) > 256 * 256) continue;
            server.sendParticles(player, ParticleTypes.FLASH, true, getX(), getY(), getZ(), 1, 0, 0, 0, 0);
            server.sendParticles(player, ParticleTypes.FLAME, true, getX(), getY(), getZ(), 24, .4, .4, .4, .08);
            server.sendParticles(player, ParticleTypes.SMOKE, true, getX(), getY(), getZ(), 10, .35, .35, .35, .03);
        }
        server.playSound(null, getX(), getY(), getZ(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 4, 1.1F);
        server.gameEvent(net.minecraft.world.level.gameevent.GameEvent.EXPLODE, position(), net.minecraft.world.level.gameevent.GameEvent.Context.of(this));
        discard();
    }
    @Override protected void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putDouble("signal_range", range); tag.putDouble("signal_travelled", travelled);
        tag.putDouble("signal_radius", radius); tag.putFloat("signal_damage", damage);
        tag.putInt("signal_burn_seconds", burnSeconds); tag.putInt("signal_life", life);
    }
    @Override protected void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        range = finite(tag, "signal_range", 48, 1, 256);
        travelled = finite(tag, "signal_travelled", 0, 0, 256);
        radius = finite(tag, "signal_radius", 3, .1, 8);
        damage = (float) finite(tag, "signal_damage", 3, 0, 100);
        burnSeconds = (int) finite(tag, "signal_burn_seconds", 5, 0, 60);
        life = (int) finite(tag, "signal_life", 0, 0, 1201);
    }
    private static double finite(CompoundTag tag, String key, double fallback, double min, double max) {
        double value = tag.contains(key) ? tag.getDouble(key) : fallback;
        return Double.isFinite(value) ? net.minecraft.util.Mth.clamp(value, min, max) : fallback;
    }
}
