package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.registry.ModSounds;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** A local loop whose lifetime follows the server's smoke state. */
public final class SmokeHissSound extends AbstractTickableSoundInstance {
    private final AirdropCrateBlockEntity crate;

    public SmokeHissSound(AirdropCrateBlockEntity crate) {
        super(ModSounds.SMOKE.get(), SoundSource.BLOCKS, RandomSource.create());
        this.crate = crate;
        looping = true;
        delay = 0;
        attenuation = Attenuation.NONE;
        x = crate.getBlockPos().getX() + .5;
        y = crate.getBlockPos().getY() + 1.1;
        z = crate.getBlockPos().getZ() + .5;
        tick();
    }

    public boolean belongsTo(AirdropCrateBlockEntity candidate) { return crate == candidate; }

    public boolean shouldContinue() {
        var client = Minecraft.getInstance();
        if (crate.isRemoved() || !crate.isClientSmoking() || client.level == null
                || client.level != crate.getLevel() || client.level.getBlockEntity(crate.getBlockPos()) != crate) return false;
        var camera = client.getCameraEntity();
        return camera == null || camera.distanceToSqr(x, y, z) <= 48 * 48;
    }

    public void cancel() { stop(); }
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        if (!shouldContinue()) { stop(); return; }
        var camera = Minecraft.getInstance().getCameraEntity();
        if (camera == null) { volume = 0; return; }
        float proximity = (float) Math.max(0, 1 - Math.sqrt(camera.distanceToSqr(x, y, z)) / 32);
        volume = .35F * proximity * proximity;
    }
}
