package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.item.SignalTubeItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, AirdropMod.MOD_ID);
    public static final RegistryObject<SignalTubeItem> SIGNAL_TUBE = ITEMS.register("signal_tube",
            () -> new SignalTubeItem(new Item.Properties().stacksTo(16)));
    private ModItems() {}
}
