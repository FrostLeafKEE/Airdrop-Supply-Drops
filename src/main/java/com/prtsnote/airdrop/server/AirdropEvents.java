package com.prtsnote.airdrop.server;

import com.mojang.logging.LogUtils;
import com.prtsnote.airdrop.config.AirdropConfig;
import com.prtsnote.airdrop.data.AirdropTypes;
import com.prtsnote.airdrop.data.AirdropValidation;
import com.prtsnote.airdrop.registry.ModEntities;
import com.prtsnote.airdrop.world.block.entity.AirdropCrateBlockEntity;
import com.prtsnote.airdrop.world.entity.FallingAirdrop;
import com.prtsnote.airdrop.world.entity.AirdropPlane;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraftforge.event.TickEvent;

import java.util.*;

/** Authoritative event state. Entity NBT is a snapshot, never a second source of loot. */
public final class AirdropEvents extends SavedData {
    // The visible aircraft travels one block per tick: 600 before and 400 after the drop.
    public static final int RELEASE_TICK = 600;
    public static final int DEPARTURE_TICK = 1000;
    public static final int FLARE_FIRST_TICK = RELEASE_TICK - 120;
    public static final int FLARE_INTERVAL = 40;
    public static final int FLIGHT_PATH_VERSION = 2;
    public static final int LEGACY_FLIGHT_AGE_OFFSET = RELEASE_TICK - 240;
    public enum Stage { FLYING, FALLING, LANDED }
    public static final class Event {
        public UUID id, planeId;
        public ResourceLocation dimension;
        public BlockPos ground;
        public long started, deadline;
        public int flares;
        public float heading;
        public Stage stage = Stage.FLYING;
        public CompoundTag cargo;
        public com.prtsnote.airdrop.data.AirdropRules.Settings settings;
        public UUID dropId() { return cargo.getUUID("UUID"); }
    }
    private static final TicketType<UUID> TICKET = TicketType.create("airdrop_event", UUID::compareTo);
    private final Map<UUID, Event> events = new LinkedHashMap<>();
    private final Set<UUID> tickets = new HashSet<>();
    private long nextEventAt;
    private long recoveryReadyAt;

    public static AirdropEvents get(MinecraftServer server) {
        return server.overworld().getDataStorage()
                .computeIfAbsent(new SavedData.Factory<>(AirdropEvents::new, AirdropEvents::load, null), "airdrop_supply_drops_events");
    }
    public Collection<Event> all() { return List.copyOf(events.values()); }
    public Event find(UUID id) { return events.get(id); }
    public long nextEventAt() { return nextEventAt; }
    public static long now(MinecraftServer server) { return server.overworld().getGameTime(); }
    private static ServerLevel level(MinecraftServer server, Event event) {
        return server.getLevel(ResourceKey.create(Registries.DIMENSION, event.dimension));
    }

    public void startSession(MinecraftServer server) {
        recoveryReadyAt = now(server) + 100;
        for (Event event : all()) {
            if (event.stage == Stage.LANDED && server.isSingleplayer() && event.settings.resetOnRejoin()) {
                event.deadline = now(server) + event.settings.lifetimeSeconds() * 20L;
            }
            if (event.stage != Stage.LANDED) hold(server, event);
        }
        setDirty();
    }

    public void stopSession(MinecraftServer server) {
        for (Event event : all()) releaseTicket(server, event);
    }

    private void hold(MinecraftServer server, Event event) {
        ServerLevel level = level(server, event);
        if (level != null && tickets.add(event.id)) {
            level.getChunkSource().addRegionTicket(TICKET, new ChunkPos(event.ground), 2, event.id);
        }
    }
    private void releaseTicket(MinecraftServer server, Event event) {
        ServerLevel level = level(server, event);
        if (tickets.remove(event.id) && level != null) {
            level.getChunkSource().removeRegionTicket(TICKET, new ChunkPos(event.ground), 2, event.id);
        }
    }

    public UUID begin(ServerLevel level, BlockPos ground, AirdropTypes.Type type) {
        if (events.size() >= AirdropConfig.MAX_ACTIVE_EVENTS.get()) return null;
        FallingAirdrop drop = ModEntities.FALLING_AIRDROP.get().create(level);
        if (drop == null) return null;
        drop.setPos(ground.getX() + 0.5, ground.getY() + 60, ground.getZ() + 0.5);
        if (!drop.prepare(type)) return null;
        Event event = new Event();
        event.id = UUID.randomUUID();
        event.planeId = UUID.randomUUID();
        event.dimension = level.dimension().location();
        event.ground = ground.immutable();
        event.started = now(level.getServer());
        event.deadline = event.started + 12000;
        event.heading = level.random.nextFloat() * 360;
        event.settings = type.settings().resolve();
        drop.bindEvent(event.id);
        event.cargo = drop.saveWithoutId(new CompoundTag());
        events.put(event.id, event);
        hold(level.getServer(), event);
        setDirty();
        spawnPlane(level, event);
        return event.id;
    }

    private void spawnPlane(ServerLevel level, Event event) {
        if (level.getEntity(event.planeId) != null) return;
        AirdropPlane plane = ModEntities.PLANE.get().create(level);
        if (plane == null) return;
        plane.setUUID(event.planeId);
        plane.configure(event.id, event.ground, event.heading, (int) (now(level.getServer()) - event.started));
        level.addFreshEntity(plane);
    }

    public boolean ownsDrop(UUID id, UUID entity) {
        Event event = find(id);
        return event != null && event.stage == Stage.FALLING && event.dropId().equals(entity);
    }
    public void landed(MinecraftServer server, UUID id, BlockPos pos) {
        Event event = find(id);
        if (event == null) return;
        releaseTicket(server, event);
        event.stage = Stage.LANDED;
        event.ground = pos.immutable();
        event.deadline = now(server) + event.settings.lifetimeSeconds() * 20L;
        setDirty();
    }

    public void cancel(MinecraftServer server, UUID id) {
        Event event = events.remove(id);
        if (event == null) return;
        releaseTicket(server, event);
        ServerLevel level = level(server, event);
        if (level != null) {
            var plane = level.getEntity(event.planeId);
            if (plane != null) plane.discard();
            var drop = level.getEntity(event.dropId());
            if (drop != null) drop.discard();
            if (event.stage == Stage.LANDED && level.hasChunkAt(event.ground)
                    && level.getBlockEntity(event.ground) instanceof AirdropCrateBlockEntity crate
                    && id.equals(crate.eventId())) level.removeBlock(event.ground, false);
        }
        setDirty();
    }

    public void forget(MinecraftServer server, UUID id) {
        Event event = events.remove(id);
        if (event != null) { releaseTicket(server, event); setDirty(); }
    }

    public static void onTick(TickEvent.ServerTickEvent tick) {
        if (tick.phase == TickEvent.Phase.END) get(tick.getServer()).tick(tick.getServer());
    }

    public void tick(MinecraftServer server) {
        long time = now(server);
        for (Event event : all()) {
            ServerLevel level = level(server, event);
            if (level == null || time >= event.deadline) {
                cancel(server, event.id);
                continue;
            }
            if (event.stage == Stage.LANDED) continue;
            hold(server, event);
            long age = time - event.started;
            if (event.stage == Stage.FLYING) {
                while (event.flares < 3 && age >= FLARE_FIRST_TICK + event.flares * FLARE_INTERVAL) {
                    event.flares++;
                    setDirty();
                }
                AirdropPlane.emitFlares(level, event, age);
                if (age >= RELEASE_TICK && event.flares == 3) {
                    // Commit the phase before spawning, so a reloaded plane cannot release twice.
                    event.stage = Stage.FALLING;
                    setDirty();
                    restoreDrop(level, event);
                } else if (time >= recoveryReadyAt && time % 20 == 0) spawnPlane(level, event);
            } else if (time % 20 == 0) {
                var drop = level.getEntity(event.dropId());
                if (drop instanceof FallingAirdrop) {
                    event.cargo = drop.saveWithoutId(new CompoundTag());
                    setDirty();
                } else if (time >= recoveryReadyAt) restoreDrop(level, event);
            }
        }
        schedule(server, time);
    }

    private void restoreDrop(ServerLevel level, Event event) {
        if (level.getEntity(event.dropId()) != null) return;
        var drop = ModEntities.FALLING_AIRDROP.get().create(level);
        if (drop == null) return;
        drop.load(event.cargo.copy());
        drop.bindEvent(event.id);
        if (!level.addFreshEntity(drop)) LogUtils.getLogger().warn("Could not restore airdrop entity {} for {}", event.dropId(), event.id);
    }

    public static AirdropTypes.Type chooseType(ServerLevel level) {
        return chooseType(level, AirdropTypes.all().values());
    }

    private static AirdropTypes.Type chooseType(ServerLevel level, Collection<AirdropTypes.Type> candidates) {
        var valid = candidates.stream()
                .filter(type -> AirdropValidation.lootTable(level.getServer(), type.lootTable()) != LootTable.EMPTY)
                .sorted(Comparator.comparing(type -> type.id().toString())).toList();
        long total = valid.stream().mapToLong(AirdropTypes.Type::weight).sum();
        if (total == 0) return null;
        long choice = Math.floorMod(level.random.nextLong(), total);
        for (var type : valid) {
            choice -= type.weight();
            if (choice < 0) return type;
        }
        return null;
    }

    public void schedule(MinecraftServer server, long time) {
        if (!AirdropConfig.ENABLED.get()) return;
        if (nextEventAt == 0) { nextEventAt = time + interval(server); setDirty(); return; }
        if (time < nextEventAt) return;
        // Capacity and no-player failures retry after a minute instead of searching every tick.
        nextEventAt = time + 1200;
        setDirty();
        if (events.size() >= AirdropConfig.MAX_ACTIVE_EVENTS.get()) return;
        var players = server.getPlayerList().getPlayers().stream().filter(player -> player.isAlive() && !player.isSpectator()
                && AirdropConfig.isDimensionAllowed(player.serverLevel())).toList();
        if (players.isEmpty()) return;
        var player = players.get(server.overworld().random.nextInt(players.size()));
        // Conditions are evaluated at the target player; a failed landing search does not reroll the type.
        var candidates = AirdropTypes.all().values().stream()
                .filter(type -> type.conditions().matches(player.serverLevel(), player.blockPosition())).toList();
        var type = chooseType(player.serverLevel(), candidates);
        if (type == null) return;
        var ground = AirdropServer.findLanding(player, type.settings().resolve());
        if (ground == null || begin(player.serverLevel(), ground, type) == null) return;
        nextEventAt = time + interval(server);
        setDirty();
        player.sendSystemMessage(net.minecraft.network.chat.Component.translatable("message.airdrop_supply_drops.approaching"));
    }
    private long interval(MinecraftServer server) {
        long min = AirdropConfig.INTERVAL_MIN_SECONDS.get();
        long max = AirdropConfig.INTERVAL_MAX_SECONDS.get();
        return (min + Math.floorMod(server.overworld().random.nextLong(), max - min + 1)) * 20L;
    }

    public static AirdropEvents load(CompoundTag tag, HolderLookup.Provider registries) {
        AirdropEvents data = new AirdropEvents();
        data.nextEventAt = tag.getLong("next_event_at");
        for (var value : tag.getList("events", 10)) {
            CompoundTag item = (CompoundTag) value;
            try {
                Event event = new Event();
                event.id = item.getUUID("id"); event.planeId = item.getUUID("plane_id");
                event.dimension = ResourceLocation.parse(item.getString("dimension"));
                event.ground = BlockPos.of(item.getLong("ground"));
                event.started = item.getLong("started"); event.deadline = item.getLong("deadline");
                event.stage = Stage.valueOf(item.getString("stage"));
                // Translate old elapsed ages without moving an aircraft or replaying its cargo.
                if (!tag.contains("flight_path_version") && event.stage != Stage.LANDED) {
                    event.started -= LEGACY_FLIGHT_AGE_OFFSET;
                }
                event.flares = item.getInt("flares"); event.heading = item.getFloat("heading");
                event.cargo = item.getCompound("cargo");
                event.settings = com.prtsnote.airdrop.data.AirdropRules.Settings.load(item.contains("airdrop_settings") ? item.getCompound("airdrop_settings") : event.cargo.getCompound("airdrop_settings"));
                if (!event.cargo.hasUUID("UUID")) throw new IllegalArgumentException("Missing cargo UUID");
                data.events.put(event.id, event);
            } catch (RuntimeException error) { LogUtils.getLogger().error("Invalid saved airdrop event", error); }
        }
        return data;
    }
    @Override public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putInt("flight_path_version", FLIGHT_PATH_VERSION);
        tag.putLong("next_event_at", nextEventAt);
        ListTag list = new ListTag();
        for (Event event : all()) {
            CompoundTag item = new CompoundTag();
            item.putUUID("id", event.id); item.putUUID("plane_id", event.planeId);
            item.putString("dimension", event.dimension.toString()); item.putLong("ground", event.ground.asLong());
            item.putLong("started", event.started); item.putLong("deadline", event.deadline);
            item.putString("stage", event.stage.name()); item.putInt("flares", event.flares);
            item.putFloat("heading", event.heading); item.put("cargo", event.cargo.copy());
            item.put("airdrop_settings", event.settings.save());
            list.add(item);
        }
        tag.put("events", list);
        return tag;
    }
}
