package com.prtsnote.airdrop.data;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.data.ModelProperty;

/** The server stores only an ID; clients resolve their own resource-pack assets. */
public final class AirdropAppearance {
    public static final ResourceLocation MINERAL = ResourceLocation.parse("airdrop_supply_drops:mineral");
    public static final ResourceLocation FOOD = ResourceLocation.parse("airdrop_supply_drops:food");
    public static final ModelProperty<ResourceLocation> MODEL_PROPERTY = new ModelProperty<>();

    private AirdropAppearance() {}

    public static ResourceLocation parse(String value) {
        if (value.equals("mineral")) return MINERAL;
        if (value.equals("food")) return FOOD;
        var id = ResourceLocation.tryParse(value);
        if (!value.contains(":") || id == null || value.startsWith(":") || value.endsWith(":")) {
            throw new IllegalArgumentException("appearance must be a namespace:path ID, mineral or food");
        }
        return id;
    }

    public static ResourceLocation saved(String value, boolean legacyFood) {
        try { return parse(value); }
        catch (RuntimeException ignored) { return legacyFood ? FOOD : MINERAL; }
    }
}
