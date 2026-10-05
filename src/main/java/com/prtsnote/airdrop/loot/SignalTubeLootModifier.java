package com.prtsnote.airdrop.loot;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.server.AirdropEvents;
import com.prtsnote.airdrop.world.item.SignalTubeItem;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootContext;
import net.minecraft.world.level.storage.loot.predicates.LootItemCondition;
import net.minecraftforge.common.loot.IGlobalLootModifier;
import net.minecraftforge.common.loot.LootModifier;

/** Appends a bonus; never replaces a vanilla chest's existing supplies. */
public final class SignalTubeLootModifier extends LootModifier {
    public static final Codec<SignalTubeLootModifier> CODEC = RecordCodecBuilder.create(instance ->
            codecStart(instance).apply(instance, SignalTubeLootModifier::new));
    public SignalTubeLootModifier(LootItemCondition[] conditions) { super(conditions); }
    @Override protected ObjectArrayList<ItemStack> doApply(ObjectArrayList<ItemStack> loot, LootContext context) {
        var id = context.getQueriedLootTableId();
        if (id == null || !id.getNamespace().equals("minecraft") || !id.getPath().startsWith("chests/")
                || context.getRandom().nextDouble() >= AirdropConfig.SIGNAL_LOOT_CHANCE.get()) return loot;
        if (context.getRandom().nextDouble() < AirdropConfig.SIGNAL_RANDOM_CHANCE.get()) {
            loot.add(SignalTubeItem.randomStack());
        } else {
            var type = AirdropEvents.chooseType(context.getLevel(), context.getRandom());
            loot.add(type == null ? SignalTubeItem.randomStack() : SignalTubeItem.boundStack(type));
        }
        return loot;
    }
    @Override public Codec<? extends IGlobalLootModifier> codec() { return CODEC; }
}
