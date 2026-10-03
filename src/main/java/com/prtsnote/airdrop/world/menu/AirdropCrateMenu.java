package com.prtsnote.airdrop.world.menu;

import com.prtsnote.airdrop.registry.ModMenuTypes;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

public final class AirdropCrateMenu extends AbstractContainerMenu {
    public static final int CRATE_SLOT_COUNT = 27;
    private final Container container;

    public AirdropCrateMenu(int containerId, Inventory inventory) {
        this(containerId, inventory, new SimpleContainer(CRATE_SLOT_COUNT));
    }

    public AirdropCrateMenu(int containerId, Inventory inventory, Container container) {
        super(ModMenuTypes.AIRDROP_CRATE.get(), containerId);
        checkContainerSize(container, CRATE_SLOT_COUNT);
        this.container = container;
        container.startOpen(inventory.player);

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new OutputSlot(container, column + row * 9, 8 + column * 18, 18 + row * 18));
            }
        }

        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new Slot(inventory, column + row * 9 + 9, 8 + column * 18, 84 + row * 18));
            }
        }

        for (int column = 0; column < 9; column++) {
            addSlot(new Slot(inventory, column, 8 + column * 18, 142));
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (!stillValid(player)) return ItemStack.EMPTY;
        if (index < 0 || index >= slots.size() || index >= CRATE_SLOT_COUNT) {
            return ItemStack.EMPTY;
        }

        Slot slot = slots.get(index);
        if (!slot.hasItem()) {
            return ItemStack.EMPTY;
        }

        ItemStack source = slot.getItem();
        ItemStack copy = source.copy();
        if (!moveItemStackTo(source, CRATE_SLOT_COUNT, slots.size(), true)) {
            return ItemStack.EMPTY;
        }

        if (source.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return copy;
    }

    @Override
    public void clicked(int slot, int button, net.minecraft.world.inventory.ClickType type, Player player) {
        if (stillValid(player)) super.clicked(slot, button, type, player);
    }

    @Override
    public boolean stillValid(Player player) {
        return container.stillValid(player);
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        container.stopOpen(player);
    }

    private static final class OutputSlot extends Slot {
        private OutputSlot(Container container, int index, int x, int y) {
            super(container, index, x, y);
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }
    }
}
