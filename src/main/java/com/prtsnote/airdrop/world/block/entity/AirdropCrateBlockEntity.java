package com.prtsnote.airdrop.world.block.entity;

import com.prtsnote.airdrop.registry.ModBlockEntities;
import com.prtsnote.airdrop.data.AirdropRules;
import com.prtsnote.airdrop.server.AirdropServer;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.menu.AirdropCrateMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.network.chat.Component;
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
import net.minecraft.nbt.CompoundTag;
import java.util.UUID;

public final class AirdropCrateBlockEntity extends BlockEntity implements net.minecraft.world.WorldlyContainer, MenuProvider {
    private static final int[] NO_AUTOMATION_SLOTS = new int[0];
    public static final int INVENTORY_SIZE = 27;
    public static final int LIFETIME_TICKS = 6000;

    private final NonNullList<ItemStack> items = NonNullList.withSize(INVENTORY_SIZE, ItemStack.EMPTY);
    private int remainingTicks = LIFETIME_TICKS;
    private boolean timerStarted;
    private long expiresAt = -1;
    private AirdropRules.Settings settings;
    private UUID sessionId;
    private UUID eventId;
    public UUID eventId() { return eventId; }
    public void bindEvent(UUID id) { eventId = id; setChanged(); }
    private Component displayName = Component.translatable("container.airdrop_supply_drops.airdrop_crate");

    public AirdropCrateBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.AIRDROP_CRATE.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, AirdropCrateBlockEntity crate) {
        if (!crate.timerStarted) {
            crate.initialize(crate.displayName);
        }

        if (crate.isEmpty() || crate.isExpired()) {
            if (crate.eventId != null) AirdropEvents.get(level.getServer()).forget(level.getServer(), crate.eventId);
            level.removeBlock(pos, false);
            return;
        }
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel && level.getGameTime() % 4 == 0) {
            double x = pos.getX() + 0.5, y = pos.getY() + 1.1, z = pos.getZ() + 0.5;
            for (var player : serverLevel.players()) {
                if (player.distanceToSqr(x, y, z) <= 256 * 256) {
                    serverLevel.sendParticles(player, com.prtsnote.airdrop.registry.ModParticles.RED_SMOKE.get(),
                            true, x, y, z, 6, 0.18, 0.1, 0.18, 0);
                }
            }
        }
    }

    private long now() { return level.getServer().overworld().getGameTime(); }

    public void initialize(Component name) {
        initialize(name, settings == null ? AirdropRules.defaults() : settings);
    }

    public void initialize(Component name, AirdropRules.Settings settings) {
        this.settings = settings;
        displayName = name.copy();
        timerStarted = true;
        expiresAt = now() + settings.lifetimeSeconds() * 20L;
        sessionId = AirdropServer.session(level.getServer()).id();
        setChanged();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level == null || level.isClientSide || !timerStarted) return;
        if (settings == null) settings = AirdropRules.defaults();
        var session = AirdropServer.session(level.getServer());
        if (level.getServer().isSingleplayer() && settings.resetOnRejoin() && !session.id().equals(sessionId)) {
            expiresAt = session.startedAt() + settings.lifetimeSeconds() * 20L;
        } else if (expiresAt < 0) {
            expiresAt = now() + remainingTicks;
        }
        sessionId = session.id();
        setChanged();
    }

    public boolean isExpired() {
        if (eventId != null && level != null && !level.isClientSide) {
            var event = AirdropEvents.get(level.getServer()).find(eventId);
            return event == null || event.stage != AirdropEvents.Stage.LANDED || now() >= event.deadline;
        }
        return timerStarted && level != null && !level.isClientSide && expiresAt >= 0 && now() >= expiresAt;
    }

    public int getRemainingTicks() {
        if (eventId != null && level != null && !level.isClientSide) {
            var event = AirdropEvents.get(level.getServer()).find(eventId);
            return event == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, event.deadline - now()));
        }
        return level == null || level.isClientSide || expiresAt < 0 ? remainingTicks
                : (int) Math.min(Integer.MAX_VALUE, Math.max(0L, expiresAt - now()));
    }

    public void setRemainingTicks(int remainingTicks) {
        this.remainingTicks = Math.max(0, remainingTicks);
        this.expiresAt = level == null || level.isClientSide ? -1 : now() + this.remainingTicks;
        this.timerStarted = true;
        setChanged();
    }

    public void dropContents(Level level, BlockPos pos) {
        if (level.isClientSide || isExpired()) {
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
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        ContainerHelper.saveAllItems(tag, items);
        tag.putInt("remaining_ticks", getRemainingTicks());
        tag.putBoolean("timer_started", timerStarted);
        tag.putLong("expires_at", expiresAt);
        if (settings != null) tag.put("airdrop_settings", settings.save());
        if (sessionId != null) tag.putUUID("session_id", sessionId);
        if (eventId != null) tag.putUUID("event_id", eventId);
        tag.putString("display_name", Component.Serializer.toJson(displayName));
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        items.clear();
        ContainerHelper.loadAllItems(tag, items);
        remainingTicks = tag.contains("remaining_ticks") ? tag.getInt("remaining_ticks") : LIFETIME_TICKS;
        timerStarted = tag.getBoolean("timer_started");
        expiresAt = tag.contains("expires_at") ? tag.getLong("expires_at") : -1;
        settings = tag.contains("airdrop_settings") ? AirdropRules.Settings.load(tag.getCompound("airdrop_settings")) : null;
        sessionId = tag.hasUUID("session_id") ? tag.getUUID("session_id") : null;
        eventId = tag.hasUUID("event_id") ? tag.getUUID("event_id") : null;
        if (tag.contains("display_name")) {
            Component name = Component.Serializer.fromJson(tag.getString("display_name"));
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
        if (isRemoved() || isExpired() || level == null || level.getBlockEntity(worldPosition) != this) {
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
