package com.prtsnote.airdrop.server;

import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.registry.ModBlocks;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

public final class AirdropServer {
    public record Session(UUID id, long startedAt) {}
    private static final Map<MinecraftServer, Session> SESSIONS = new WeakHashMap<>();

    public static Session session(MinecraftServer server) {
        return SESSIONS.computeIfAbsent(server, key -> new Session(UUID.randomUUID(), key.overworld().getGameTime()));
    }

    public static void onStarted(ServerStartedEvent event) {
        validateTypes(event.getServer());
        session(event.getServer());
        AirdropEvents.get(event.getServer()).startSession(event.getServer());
    }
    private static void validateTypes(MinecraftServer server) {
        for (String error : AirdropTypes.validate(server)) com.mojang.logging.LogUtils.getLogger().error("Invalid airdrop configuration: {}", error);
    }
    public static void onDatapackSync(net.minecraftforge.event.OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) validateTypes(event.getPlayerList().getServer());
    }
    public static void onStopped(ServerStoppedEvent event) {
        SESSIONS.remove(event.getServer());
        AirdropTypes.clear();
    }
    public static void onReload(AddReloadListenerEvent event) { event.addListener(new AirdropTypes()); }

    public static void onCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("airdrop_supply_drops").requires(source -> source.hasPermission(2))
                .then(Commands.literal("signal").executes(context -> giveSignal(context.getSource(), null))
                        .then(Commands.argument("type", ResourceLocationArgument.id())
                                .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AirdropTypes.all().keySet(), builder))
                                .executes(context -> giveSignal(context.getSource(), ResourceLocationArgument.getId(context, "type")))))
                .then(Commands.literal("validate").executes(context -> {
                    var errors = AirdropTypes.validate(context.getSource().getServer());
                    for (String error : errors) context.getSource().sendFailure(Component.literal(error));
                    context.getSource().sendSuccess(() -> Component.literal("Airdrop validation: " + AirdropTypes.all().size()
                            + " valid types, " + errors.size() + " errors"), false);
                    return errors.isEmpty() ? 1 : 0;
                }))
                .then(Commands.literal("status").executes(context -> {
                    var manager = AirdropEvents.get(context.getSource().getServer());
                    context.getSource().sendSuccess(() -> Component.literal("Aircraft in flight: " + manager.hasFlightInProgress(context.getSource().getServer())
                            + "; pending deliveries: " + manager.all().size()
                            + "; next check in ticks: " + Math.max(0, manager.nextEventAt() - AirdropEvents.now(context.getSource().getServer()))), false);
                    for (var active : manager.all()) context.getSource().sendSuccess(() -> Component.literal(active.id + " "
                            + active.stage + " flares=" + active.flares + " " + active.dimension + " " + active.ground.toShortString()), false);
                    return manager.all().size();
                }))
                .then(Commands.literal("cancel").then(Commands.argument("id", net.minecraft.commands.arguments.UuidArgument.uuid())
                        .executes(context -> {
                            var manager = AirdropEvents.get(context.getSource().getServer());
                            UUID id = net.minecraft.commands.arguments.UuidArgument.getUuid(context, "id");
                            if (manager.find(id) == null) { context.getSource().sendFailure(Component.literal("Unknown event")); return 0; }
                            manager.cancel(context.getSource().getServer(), id);
                            context.getSource().sendSuccess(() -> Component.literal("Airdrop cancelled: " + id), true);
                            return 1;
                        })))
                .then(Commands.literal("list").executes(context -> {
                    context.getSource().sendSuccess(() -> Component.literal(String.join(", ",
                            AirdropTypes.all().keySet().stream().map(Object::toString).sorted().toList())), false);
                    return AirdropTypes.all().size();
                }))
                .then(Commands.literal("spawn").then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AirdropTypes.all().keySet(), builder))
                        .executes(context -> {
                            var source = context.getSource();
                            var type = AirdropTypes.all().get(ResourceLocationArgument.getId(context, "type"));
                            if (type == null) {
                                source.sendFailure(Component.literal("Unknown airdrop type"));
                                return 0;
                            }
                            var player = source.getPlayerOrException();
                            ServerLevel level = source.getLevel();
                            var manager = AirdropEvents.get(level.getServer());
                            if (manager.hasFlightInProgress(level.getServer())) {
                                source.sendFailure(Component.translatable("message.airdrop_supply_drops.limit_reached"));
                                return 0;
                            }
                            if (level.getServer().getLootData().getLootTable(type.lootTable()) == LootTable.EMPTY) {
                                source.sendFailure(Component.translatable("message.airdrop_supply_drops.invalid_loot", type.lootTable().toString()));
                                return 0;
                            }
                            var settings = type.settings().resolve();
                            BlockPos ground = findLanding(player, settings);
                            if (ground == null) {
                                source.sendFailure(Component.translatable("message.airdrop_supply_drops.no_landing", settings.minDistance(), settings.maxDistance()));
                                return 0;
                            }
                            UUID id = manager.begin(level, ground, type);
                            if (id == null) {
                                source.sendFailure(Component.translatable("message.airdrop_supply_drops.start_failed"));
                                return 0;
                            }
                            source.sendSuccess(() -> Component.literal("Airdrop approaching " + ground.toShortString() + "; event " + id), true);
                            return 1;
                        })))
                .then(Commands.literal("crate").then(Commands.argument("type", ResourceLocationArgument.id())
                        .suggests((context, builder) -> SharedSuggestionProvider.suggestResource(AirdropTypes.all().keySet(), builder))
                        .executes(context -> {
                            var source = context.getSource();
                            var type = AirdropTypes.all().get(ResourceLocationArgument.getId(context, "type"));
                            if (type == null) {
                                source.sendFailure(Component.literal("Unknown airdrop type"));
                                return 0;
                            }
                            var player = source.getPlayerOrException();
                            BlockPos pos = player.blockPosition().relative(player.getDirection(), 2);
                            if (!placeCrate(source.getLevel(), pos, type)) {
                                source.sendFailure(Component.literal("Cannot place crate: need clear space, solid ground and a valid loot table"));
                                return 0;
                            }
                            source.sendSuccess(() -> Component.literal("Airdrop crate placed at " + pos.toShortString()), true);
                            return 1;
                        }))));
    }

    private static int giveSignal(net.minecraft.commands.CommandSourceStack source, net.minecraft.resources.ResourceLocation id)
            throws com.mojang.brigadier.exceptions.CommandSyntaxException {
        var type = id == null ? null : AirdropTypes.all().get(id);
        if (id != null && type == null) {
            source.sendFailure(Component.translatable("message.airdrop_supply_drops.signal_unknown_type"));
            return 0;
        }
        var stack = type == null ? com.prtsnote.airdrop.world.item.SignalTubeItem.randomStack()
                : com.prtsnote.airdrop.world.item.SignalTubeItem.boundStack(type);
        var player = source.getPlayerOrException();
        var name = stack.getHoverName();
        if (!player.getInventory().add(stack)) player.drop(stack, false);
        source.sendSuccess(() -> Component.translatable("message.airdrop_supply_drops.signal_given", name), true);
        return 1;
    }

    public static BlockPos findLanding(net.minecraft.server.level.ServerPlayer player) {
        return findLanding(player, com.prtsnote.airdrop.data.AirdropRules.defaults());
    }

    public static BlockPos findLanding(net.minecraft.server.level.ServerPlayer player, com.prtsnote.airdrop.data.AirdropRules.Settings settings) {
        ServerLevel level = player.serverLevel();
        int min = settings.minDistance(), max = settings.maxDistance();
        for (int attempt = 0; attempt < 128; attempt++) {
            double angle = level.random.nextDouble() * Math.PI * 2;
            double radius = min + level.random.nextDouble() * (max - min);
            BlockPos column = BlockPos.containing(player.getX() + Math.cos(angle) * radius,
                    player.getY(), player.getZ() + Math.sin(angle) * radius);
            double dx = column.getX() + 0.5 - player.getX(), dz = column.getZ() + 0.5 - player.getZ();
            if (dx * dx + dz * dz < min * min || dx * dx + dz * dz > max * max || !level.hasChunkAt(column)) continue;
            BlockPos ground = level.getHeightmapPos(net.minecraft.world.level.levelgen.Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, column);
            if (!settings.allowLiquidLanding() && !level.getFluidState(ground.below()).isEmpty()) continue;
            if (!level.getWorldBorder().isWithinBounds(ground)
                    || (level.getFluidState(ground.below()).isEmpty()
                    && !level.getBlockState(ground.below()).isFaceSturdy(level, ground.below(), Direction.UP))
                    || ground.getY() + 64 >= level.getMaxBuildHeight()) continue;
            boolean clear = true;
            for (int y = 0; y < 64; y++) if (!level.getBlockState(ground.above(y)).isAir()) { clear = false; break; }
            if (clear) return ground;
        }
        return null;
    }

    public static boolean placeCrate(ServerLevel level, BlockPos pos, AirdropTypes.Type type) {
        var settings = type.settings().resolve();
        if (!settings.allowLiquidLanding() && !level.getFluidState(pos.below()).isEmpty()) return false;
        if (!level.hasChunkAt(pos) || !level.getWorldBorder().isWithinBounds(pos)
                || level.isOutsideBuildHeight(pos) || !level.getBlockState(pos).canBeReplaced()
                || !level.getFluidState(pos).isEmpty() || level.getBlockEntity(pos) != null
                || (level.getFluidState(pos.below()).isEmpty()
                && !level.getBlockState(pos.below()).isFaceSturdy(level, pos.below(), Direction.UP))) return false;
        LootTable table = level.getServer().getLootData().getLootTable(type.lootTable());
        if (table == LootTable.EMPTY) return false;
        if (!level.setBlock(pos, ModBlocks.AIRDROP_CRATE.get().defaultBlockState()
                .setValue(com.prtsnote.airdrop.world.block.AirdropCrateBlock.FOOD, type.appearance().equals("food")), 3)) return false;
        if (!(level.getBlockEntity(pos) instanceof AirdropCrateBlockEntity crate)) return false;
        table.fill(crate, new LootParams.Builder(level)
                .withParameter(LootContextParams.ORIGIN, Vec3.atCenterOf(pos))
                .create(LootContextParamSets.CHEST), level.random.nextLong());
        crate.initialize(type.name(), settings);
        return true;
    }
}
