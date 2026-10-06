package com.prtsnote.airdrop.client;

import com.prtsnote.airdrop.registry.ModSounds;
import com.prtsnote.airdrop.world.entity.FallingAirdrop;
import net.minecraft.client.Minecraft;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;

public final class DescentWindSound extends AbstractTickableSoundInstance {
    private final FallingAirdrop drop;
    public DescentWindSound(FallingAirdrop drop) {
        super(ModSounds.WIND.get(), SoundSource.AMBIENT, RandomSource.create());
        this.drop = drop;
        looping = true;
        delay = 0;
        attenuation = Attenuation.NONE;
        tick();
    }
    @Override public boolean canStartSilent() { return true; }
    @Override public void tick() {
        var client = Minecraft.getInstance();
        if (drop.isRemoved() || client.level != drop.level()) { stop(); return; }
        x = drop.getX(); y = drop.getY(); z = drop.getZ();
        if (client.getCameraEntity() == null) { volume = 0; return; }
        double distance = client.getCameraEntity().position().distanceTo(drop.position());
        float proximity = (float) Math.max(0, 1 - distance / 96);
        float opening = Math.max(0, Math.min(1, (drop.deploymentTicks() - 10) / 20F));
        volume = 0.6F * proximity * proximity * opening;
    }
}
