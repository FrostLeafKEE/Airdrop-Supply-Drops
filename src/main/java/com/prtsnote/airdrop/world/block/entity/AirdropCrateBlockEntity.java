package com.prtsnote.airdrop.world.block.entity;

import com.prtsnote.airdrop.registry.ModBlockEntities;
import com.prtsnote.airdrop.data.AirdropRules;
import com.prtsnote.airdrop.world.menu.AirdropCrateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.NonNullList;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.world.Container;
import net.minecraft.world.ContainerHelper;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.Containers;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import java.util.UUID;

public final class AirdropCrateBlockEntity extends BlockEntity implements net.minecraft.world.WorldlyContainer, MenuProvider {
    private static final int[] NO_AUTOMATION_SLOTS = new int[0];
    public static final int INVENTORY_SIZE = 27;
    public static final int SMOKE_DURATION_TICKS = 5 * 60 * 20;
    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private long smokeEndsAt = -1;
    private AirdropRules.Settings settings;
    private UUID eventId;
    public UUID eventId() { return eventId; }
    public void bindEvent(UUID id) { eventId = id; setChanged(); }
    private Component displayName = Component.translatable("container.airdrop_supply_drops.airdrop_crate");

    public AirdropCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIRDROP_CRATE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirdropCrateBlockEntity crate) {
        if (crate.smokeEndsAt < 0) {
            crate.smokeEndsAt = level.getServer().overworld().getGameTime() + SMOKE_DURATION_TICKS;
            crate.setChanged();
        }
        // Crate persistence and smoke lifetime are independent.
        if (!crate.emitsSmoke()) return;
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && level.getGameTime() % 4 == 0) {
            com.prtsnote.airdrop.server.AirdropSmoke.emit(serverLevel, pos.getX() + 0.5, pos.getY() + 1.1, pos.getZ() + 0.5, false);
        }
    }

    public void initialize(Component name) {
        initialize(name, settings == null ? AirdropRules.defaults() : settings);
    }

    public void initialize(Component name, AirdropRules.Settings settings) {
        this.settings = settings;
        displayName = name.copy();
        smokeEndsAt = level.getServer().overworld().getGameTime() + SMOKE_DURATION_TICKS;
        setChanged();
    }

    public boolean emitsSmoke() {
        return !isEmpty() && level != null && !level.isClientSide && smokeEndsAt >= 0
                && level.getServer().overworld().getGameTime() < smokeEndsAt;
    }

    public void dropContents(Level level, BlockPos pos) {
        if (level.isClientSide) {
            return;
        }

        for (int index = 0; index < items.size(); index++) {
            ItemStack stack = items.get(index);
            if (!stack.isEmpty()) {
                Containers.dropItemStack(level, pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D, stack.copy());
                items.set(index, ItemStack.EMPTY);
            }
        }
        setChanged();
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        ContainerHelper.saveAllItems(tag, items, registries);
        tag.putLong("smoke_ends_at", smokeEndsAt);
        if (settings != null) tag.put("airdrop_settings", settings.save());
        if (eventId != null) tag.putUUID("event_id", eventId);
        tag.put("display_name", ComponentSerialization.CODEC
                .encodeStart(registries.createSerializationContext(NbtOps.INSTANCE), displayName)
                .getOrThrow());
    }

    @Override
    protected void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        items.clear();
        ContainerHelper.loadAllItems(tag, items, registries);
        smokeEndsAt = tag.contains("smoke_ends_at") ? tag.getLong("smoke_ends_at") : -1;
        // Recover the landing time from an old deadline, only to limit smoke; never delete the crate.
        if (smokeEndsAt < 0 && tag.contains("expires_at") && tag.getLong("expires_at") >= 0) {
            var oldSettings = tag.getCompound("airdrop_settings");
            long seconds = oldSettings.contains("lifetime_seconds") ? Math.max(1, oldSettings.getInt("lifetime_seconds")) : 300;
            smokeEndsAt = Math.max(0, tag.getLong("expires_at") + SMOKE_DURATION_TICKS - seconds * 20L);
        }
        // Legacy crate expiration/session fields no longer control block or inventory lifetime.
        settings = tag.contains("airdrop_settings") ? AirdropRules.Settings.load(tag.getCompound("airdrop_settings")) : null;
        eventId = tag.hasUUID("event_id") ? tag.getUUID("event_id") : null;
        if (tag.contains("display_name")) {
            Component name = ComponentSerialization.CODEC
                    .parse(registries.createSerializationContext(NbtOps.INSTANCE), tag.get("display_name"))
                    .result().orElse(null);
            if (name != null) displayName = name;
        }
    }

    @Override
    public Component getDisplayName() {
        return displayName;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player player) {
        return new AirdropCrateMenu(containerId, inventory, this);
    }

    @Override
    public int getContainerSize() {
        return items.size();
    }

    @Override
    public boolean isEmpty() {
        for (ItemStack stack : items) {
            if (!stack.isEmpty()) {
                return false;
            }
        }
        return true;
    }

    @Override
    public ItemStack getItem(int index) {
        return items.get(index);
    }

    @Override
    public ItemStack removeItem(int index, int count) {
        ItemStack result = ContainerHelper.removeItem(items, index, count);
        if (!result.isEmpty()) {
            setChanged();
        }
        return result;
    }

    @Override
    public ItemStack removeItemNoUpdate(int index) {
        ItemStack result = items.get(index);
        items.set(index, ItemStack.EMPTY);
        return result;
    }

    @Override
    public void setItem(int index, ItemStack stack) {
        items.set(index, stack);
        if (!stack.isEmpty() && stack.getCount() > getMaxStackSize()) {
            stack.setCount(getMaxStackSize());
        }
        setChanged();
    }

    @Override
    public boolean stillValid(Player player) {
        if (isRemoved() || level == null || level.getBlockEntity(worldPosition) != this) {
            return false;
        }
        return player.distanceToSqr(
                worldPosition.getX() + 0.5D,
                worldPosition.getY() + 0.5D,
                worldPosition.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public boolean canPlaceItem(int index, ItemStack stack) {
        return false;
    }

    @Override public int[] getSlotsForFace(net.minecraft.core.Direction side) { return NO_AUTOMATION_SLOTS; }
    @Override public boolean canPlaceItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) { return false; }
    @Override public boolean canTakeItemThroughFace(int slot, ItemStack stack, net.minecraft.core.Direction side) { return false; }
    @Override public boolean canTakeItem(Container destination, int slot, ItemStack stack) { return false; }

    @Override
    public void clearContent() {
        for (int index = 0; index < items.size(); index++) {
            items.set(index, ItemStack.EMPTY);
        }
        setChanged();
    }
}
