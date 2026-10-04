package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.registry.ModSounds;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

/** Positional audio follows the same smoothed world-space flight path as the renderer. */
public final class AircraftEngineSound extends AbstractTickableSoundInstance {
    private final AirdropPlane plane;

    public AircraftEngineSound(AirdropPlane plane) {
        super(ModSounds.ENGINE.get(), SoundSource.AMBIENT, RandomSource.create());
        this.plane = plane;
        looping = true;
        delay = 0;
        attenuation = Attenuation.NONE;
        pitch = 1F;
        tick();
    }

    @Override public boolean canStartSilent() { return true; }

    @Override public void tick() {
        var client = Minecraft.getInstance();
        if (plane.isRemoved() || client.level != plane.level()) {
            stop();
            return;
        }
        var position = plane.visualPosition();
        x = position.x;
        y = position.y;
        z = position.z;
        if (client.getCameraEntity() == null) {
            volume = 0;
            return;
        }
        double distance = client.getCameraEntity().position().distanceTo(position);
        float proximity = (float) Math.max(0, 1 - distance / 384.0);
        volume = 0.8F * proximity * proximity;
    }
}
