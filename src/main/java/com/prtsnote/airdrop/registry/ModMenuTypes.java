package com.prtsnote.airdrop.registry;

import com.prtsnote.airdrop.AirdropMod;
import com.prtsnote.airdrop.world.menu.AirdropCrateMenu;
import net.minecraft.world.inventory.MenuType;
import net.minecraftforge.common.extensions.IForgeMenuType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModMenuTypes {
    public static final DeferredRegister<MenuType<?>> MENUS = DeferredRegister.create(ForgeRegistries.MENU_TYPES, AirdropMod.MOD_ID);

    public static final RegistryObject<MenuType<AirdropCrateMenu>> AIRDROP_CRATE = MENUS.register(
            "airdrop_crate",
            () -> IForgeMenuType.create((windowId, inventory, data) -> new AirdropCrateMenu(windowId, inventory)));

    private ModMenuTypes() {
    }
}
