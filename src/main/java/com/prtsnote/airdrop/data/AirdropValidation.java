package com.prtsnote.airdrop.data;

import com.google.gson.JsonElement;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tags.TagKey;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.HashSet;
import java.util.Set;

/** Validate references after vanilla loot tables and registry tags have finished reloading. */
public final class AirdropValidation {
    public static void validate(MinecraftServer server, AirdropTypes.Type type) {
        for (String dimension : type.conditions().dimensions()) {
            if (server.getLevel(ResourceKey.create(Registries.DIMENSION, new ResourceLocation(dimension))) == null) {
                throw new IllegalArgumentException("conditions.dimensions: unknown dimension " + dimension);
            }
        }
        var biomes = server.registryAccess().registryOrThrow(Registries.BIOME);
        for (String biome : type.conditions().biomes()) {
            if (biome.startsWith("#")) {
                if (biomes.getTag(TagKey.create(Registries.BIOME, new ResourceLocation(biome.substring(1))))
                        .map(tag -> tag.size() == 0).orElse(true)) throw new IllegalArgumentException("conditions.biomes: missing or empty tag " + biome);
            } else if (!biomes.containsKey(new ResourceLocation(biome))) throw new IllegalArgumentException("conditions.biomes: unknown biome " + biome);
        }
        loot(server, type.lootTable(), new HashSet<>(), new HashSet<>());
    }

    private static void loot(MinecraftServer server, ResourceLocation id, Set<ResourceLocation> active, Set<ResourceLocation> checked) {
        if (active.contains(id)) throw new IllegalArgumentException("loot_table: recursive reference " + id);
        if (!checked.add(id)) return;
        if (server.getLootData().getLootTable(id) == LootTable.EMPTY) throw new IllegalArgumentException("loot_table: missing or invalid " + id);
        active.add(id);
        var file = new ResourceLocation(id.getNamespace(), "loot_tables/" + id.getPath() + ".json");
        var resource = server.getResourceManager().getResource(file);
        // Programmatically supplied loot tables may have no JSON resource.
        if (resource.isPresent()) {
            try (var reader = resource.get().openAsReader()) {
                scan(server, GsonHelper.parse(reader), file.toString(), active, checked);
            } catch (java.io.IOException error) {
                throw new IllegalArgumentException(file + ": " + error.getMessage());
            }
        }
        active.remove(id);
    }

    private static void scan(MinecraftServer server, JsonElement element, String path,
                             Set<ResourceLocation> active, Set<ResourceLocation> checked) {
        if (element.isJsonArray()) {
            int index = 0;
            for (var child : element.getAsJsonArray()) scan(server, child, path + "[" + index++ + "]", active, checked);
        } else if (element.isJsonObject()) {
            var json = element.getAsJsonObject();
            if (json.has("type") && json.get("type").isJsonPrimitive() && json.has("name") && json.get("name").isJsonPrimitive()) {
                String entryType = json.get("type").getAsString();
                if (!entryType.contains(":")) entryType = "minecraft:" + entryType;
                if (Set.of("minecraft:item", "minecraft:tag", "minecraft:loot_table").contains(entryType)) {
                    var id = new ResourceLocation(json.get("name").getAsString());
                    if (entryType.equals("minecraft:item") && !net.minecraftforge.registries.ForgeRegistries.ITEMS.containsKey(id)) {
                        throw new IllegalArgumentException(path + ".name: unknown item " + id);
                    }
                    if (entryType.equals("minecraft:tag") && server.registryAccess().registryOrThrow(Registries.ITEM)
                            .getTag(TagKey.create(Registries.ITEM, id)).map(tag -> tag.size() == 0).orElse(true)) {
                        throw new IllegalArgumentException(path + ".name: missing or empty item tag " + id);
                    }
                    if (entryType.equals("minecraft:loot_table")) loot(server, id, active, checked);
                }
            }
            json.entrySet().forEach(entry -> scan(server, entry.getValue(), path + "." + entry.getKey(), active, checked));
        }
    }
}
