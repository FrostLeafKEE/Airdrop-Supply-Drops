package com.prtsnote.airdrop.data;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagLoader;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.WeakHashMap;

/** Dimension-ID groups resolved against the running worlds, after preset dimensions are baked. */
public final class AirdropDimensionTags {
    private static final Map<MinecraftServer, Map<ResourceLocation, Set<ResourceLocation>>> TAGS = new WeakHashMap<>();

    private AirdropDimensionTags() {}

    public static void refresh(MinecraftServer server) {
        // The new-world screen loads tags before vanilla preset dimensions exist. Its failed/empty
        // LEVEL_STEM bindings are also discarded when the chosen dimensions are baked. Use the
        // actual server levels and vanilla tag merging instead of those provisional holder tags.
        var loader = new TagLoader<ResourceLocation>(id -> server.getLevel(ResourceKey.create(Registries.DIMENSION, id)) == null
                ? Optional.empty() : Optional.of(id), "tags/dimension");
        Map<ResourceLocation, Set<ResourceLocation>> resolved = new HashMap<>();
        loader.loadAndBuild(server.getResourceManager()).forEach((id, values) -> resolved.put(id, Set.copyOf(values)));
        TAGS.put(server, Map.copyOf(resolved));
    }

    public static boolean exists(MinecraftServer server, ResourceLocation tag) {
        return !tags(server).getOrDefault(tag, Set.of()).isEmpty();
    }

    public static boolean contains(MinecraftServer server, ResourceLocation tag, ResourceLocation dimension) {
        return tags(server).getOrDefault(tag, Set.of()).contains(dimension);
    }

    public static void clear() { TAGS.clear(); }

    private static Map<ResourceLocation, Set<ResourceLocation>> tags(MinecraftServer server) {
        if (!TAGS.containsKey(server)) refresh(server);
        return TAGS.get(server);
    }
}
