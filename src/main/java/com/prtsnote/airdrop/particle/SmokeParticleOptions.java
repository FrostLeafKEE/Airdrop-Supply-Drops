package com.prtsnote.airdrop.particle;

import com.mojang.serialization.Codec;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.prtsnote.airdrop.registry.ModParticles;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.network.FriendlyByteBuf;

/** RGB color carried in the server's particle packet, independent of client configuration. */
public record SmokeParticleOptions(int color) implements ParticleOptions {
    public static final Codec<SmokeParticleOptions> CODEC = Codec.intRange(0, 0xFFFFFF)
            .fieldOf("color").xmap(SmokeParticleOptions::new, SmokeParticleOptions::color).codec();
    public static final ParticleOptions.Deserializer<SmokeParticleOptions> DESERIALIZER = new ParticleOptions.Deserializer<>() {
        @Override public SmokeParticleOptions fromCommand(ParticleType<SmokeParticleOptions> type, StringReader reader) throws CommandSyntaxException {
            reader.expect(' ');
            return new SmokeParticleOptions(IntegerArgumentType.integer(0, 0xFFFFFF).parse(reader));
        }
        @Override public SmokeParticleOptions fromNetwork(ParticleType<SmokeParticleOptions> type, FriendlyByteBuf buffer) {
            return new SmokeParticleOptions(buffer.readInt());
        }
    };

    public SmokeParticleOptions {
        if (color < 0 || color > 0xFFFFFF) throw new IllegalArgumentException("Smoke color must be a 24-bit RGB value");
    }

    public float red() { return ((color >> 16) & 255) / 255F; }
    public float green() { return ((color >> 8) & 255) / 255F; }
    public float blue() { return (color & 255) / 255F; }

    @Override public ParticleType<SmokeParticleOptions> getType() { return ModParticles.RED_SMOKE.get(); }
    @Override public void writeToNetwork(FriendlyByteBuf buffer) { buffer.writeInt(color); }
    @Override public String writeToString() { return net.minecraftforge.registries.ForgeRegistries.PARTICLE_TYPES.getKey(getType()) + " " + color; }
}
