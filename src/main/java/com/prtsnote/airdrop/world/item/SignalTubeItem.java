package com.prtsnote.airdrop.world.item;

import com.google.gson.JsonParser;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.registry.ModItems;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.server.AirdropServer;
import net.minecraft.ChatFormatting;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import java.util.List;

/** Binding is item data, never the editable display name. One successful shot consumes one tube. */
public final class SignalTubeItem extends Item {
    public static final String TYPE_KEY = "airdrop_type";
    public static final String LABEL_KEY = "airdrop_label";
    public SignalTubeItem(Properties properties) { super(properties); }

    public static CompoundTag data(ItemStack stack) {
        return stack.hasTag() ? stack.getTag().copy() : new CompoundTag();
    }
    public static boolean hasBinding(ItemStack stack) { return data(stack).contains(TYPE_KEY); }
    public static ResourceLocation binding(ItemStack stack) {
        String text = data(stack).getString(TYPE_KEY);
        return text.length() <= 256 ? ResourceLocation.tryParse(text) : null;
    }
    public static ItemStack randomStack() { return new ItemStack(ModItems.SIGNAL_TUBE.get()); }
    public static ItemStack boundStack(ResourceLocation id, Component name) {
        ItemStack stack = randomStack();
        CompoundTag tag = data(stack);
        tag.putString(TYPE_KEY, id.toString());
        tag.putString(LABEL_KEY, Component.Serializer.toJson(name));
        stack.setTag(tag);
        return stack;
    }
    public static ItemStack boundStack(AirdropTypes.Type type) { return boundStack(type.id(), type.name()); }

    private static Component label(ItemStack stack, ResourceLocation id) {
        try {
            String json = data(stack).getString(LABEL_KEY);
            if (!json.isEmpty() && json.length() <= 4096) {
                var name = Component.Serializer.fromJson(json);
                if (name != null) return name;
            }
        } catch (RuntimeException ignored) { /* Hand-authored item data may omit or invalidate the label. */ }
        return Component.literal(id.toString());
    }
    @Override public Component getName(ItemStack stack) {
        if (!hasBinding(stack)) return super.getName(stack);
        ResourceLocation id = binding(stack);
        return Component.translatable("item.airdrop_supply_drops.signal_tube.typed",
                id == null ? Component.translatable("item.airdrop_supply_drops.signal_tube.invalid") : label(stack, id));
    }
    @Override public void appendHoverText(ItemStack stack, Level context, List<Component> lines, TooltipFlag flag) {
        ResourceLocation id = binding(stack);
        lines.add(Component.translatable(hasBinding(stack) ? "tooltip.airdrop_supply_drops.signal_tube.typed"
                : "tooltip.airdrop_supply_drops.signal_tube.random", id == null ? "?" : id.toString()).withStyle(ChatFormatting.GRAY));
        lines.add(Component.translatable("tooltip.airdrop_supply_drops.signal_tube.use").withStyle(ChatFormatting.GRAY));
    }

    @Override public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (level.isClientSide) return InteractionResultHolder.success(stack);
        if (!(player instanceof ServerPlayer serverPlayer) || !player.isAlive() || player.isSpectator()
                || player.getCooldowns().isOnCooldown(this)) return InteractionResultHolder.fail(stack);
        var manager = AirdropEvents.get(serverPlayer.serverLevel().getServer());
        if (manager.hasFlightInProgress(serverPlayer.serverLevel().getServer())) {
            return failure(serverPlayer, stack, "message.airdrop_supply_drops.limit_reached");
        }
        if (!AirdropConfig.isDimensionAllowed(serverPlayer.serverLevel())) {
            return failure(serverPlayer, stack, "message.airdrop_supply_drops.signal_dimension");
        }
        ResourceLocation requested = binding(stack);
        if (hasBinding(stack) && requested == null) return failure(serverPlayer, stack, "message.airdrop_supply_drops.signal_unknown_type");
        var type = hasBinding(stack) ? AirdropTypes.all().get(requested) : AirdropEvents.chooseType(serverPlayer.serverLevel());
        if (type == null) return failure(serverPlayer, stack, "message.airdrop_supply_drops.signal_unknown_type");
        var settings = type.settings().resolve();
        var ground = AirdropServer.findLanding(serverPlayer, settings);
        if (ground == null) return failure(serverPlayer, stack, "message.airdrop_supply_drops.no_landing", settings.minDistance(), settings.maxDistance());
        var flare = ModEntities.SIGNAL_FLARE.get().create(serverPlayer.serverLevel());
        if (flare == null) return failure(serverPlayer, stack, "message.airdrop_supply_drops.start_failed");
        flare.launch(serverPlayer);
        if (!level.addFreshEntity(flare)) return failure(serverPlayer, stack, "message.airdrop_supply_drops.start_failed");
        if (manager.begin(serverPlayer.serverLevel(), ground, type) == null) {
            flare.discard();
            return failure(serverPlayer, stack, "message.airdrop_supply_drops.start_failed");
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        player.getCooldowns().addCooldown(this, 20);
        player.awardStat(net.minecraft.stats.Stats.ITEM_USED.get(this));
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.FIREWORK_ROCKET_LAUNCH, SoundSource.PLAYERS, 1, .9F);
        player.displayClientMessage(Component.translatable("message.airdrop_supply_drops.signal_requested", type.name()), true);
        return InteractionResultHolder.consume(stack);
    }
    private static InteractionResultHolder<ItemStack> failure(ServerPlayer player, ItemStack stack, String key, Object... args) {
        player.displayClientMessage(Component.translatable(key, args), true);
        return InteractionResultHolder.fail(stack);
    }
}
