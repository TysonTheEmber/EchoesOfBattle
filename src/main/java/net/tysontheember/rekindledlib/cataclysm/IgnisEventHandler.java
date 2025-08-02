package net.tysontheember.rekindledlib.cataclysm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.lang.reflect.Method;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber
public class IgnisEventHandler {

    private static final ResourceLocation IGNIS_ID = new ResourceLocation("cataclysm:ignis");
    private static final ResourceLocation ALTAR_ID = new ResourceLocation("cataclysm", "altar_of_fire");
    private static final ResourceLocation SUMMON_ITEM_ID = new ResourceLocation("cataclysm", "burning_ashes");

    private static final Map<UUID, Integer> trackedPhases = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> delayedSpawnCommands = new ConcurrentHashMap<>();

    private static final Map<UUID, ServerLevel> scheduledSummonLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> scheduledSummonPositions = new ConcurrentHashMap<>();

    private static final double COMMAND_RADIUS = 32.0;
    private static final int SPAWN_COMMAND_DELAY_TICKS = 20;

    private static final Map<Integer, String> PHASE_COMMANDS = Map.of(
            1, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 5 I will kill you!",
            2, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 5 I will not fall here!",
            3, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0, obfuscate:0} 10 You are strong indeed..."
    );

    private static final UUID ALTAR_SUMMON_UUID = new UUID(0, 0);

    @SubscribeEvent
    public static void onIgnisSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();

        if (!level.isClientSide && entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID)) {
            delayedSpawnCommands.put(entity.getUUID(), SPAWN_COMMAND_DELAY_TICKS);
            trackedPhases.put(entity.getUUID(), getIgnisPhase(entity));
        }
    }

    @SubscribeEvent
    public static void onIgnisDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();

        if (!entity.level().isClientSide && entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID)) {
            runCommandNear((ServerLevel) entity.level(), entity, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0, obfuscate:0} 10 You are strong indeed...", COMMAND_RADIUS);
            trackedPhases.remove(entity.getUUID());
            delayedSpawnCommands.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        // Process delayed spawn commands
        List<UUID> toRemove = new ArrayList<>();
        delayedSpawnCommands.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                if (uuid.equals(ALTAR_SUMMON_UUID)) {
                    ServerLevel lvl = scheduledSummonLevels.remove(uuid);
                    BlockPos altarPos = scheduledSummonPositions.remove(uuid);
                    if (lvl != null && altarPos != null) {
                        runCommandNear(lvl, altarPos, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:gold, size:5, bold:1, y:150, typewriter:1, shake:1} 5 Prepare to die mortal fool!", COMMAND_RADIUS);
                    }
                } else {
                    for (ServerLevel level : server.getAllLevels()) {
                        StreamSupport.stream(level.getEntities().getAll().spliterator(), false)
                                .filter(e -> e.getUUID().equals(uuid))
                                .findFirst()
                                .ifPresent(ignis -> runCommandNear(level, ignis, "immersivemessages sendcustom @s {font:\"immersivemessages:norse\",bold:1,background:1,size:3,y:100,color:gold,obfuscate:1} 5 Ignis: Inferno Knight", COMMAND_RADIUS));
                    }
                }
                toRemove.add(uuid);
            } else {
                delayedSpawnCommands.put(uuid, ticksLeft - 1);
            }
        });
        toRemove.forEach(delayedSpawnCommands::remove);

        // Check phase transitions
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof Mob && entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID)) {
                    if (!entity.isAlive()) continue; // Skip dead Ignis

                    UUID uuid = entity.getUUID();
                    int currentPhase = getIgnisPhase(entity);
                    int lastKnown = trackedPhases.getOrDefault(uuid, -1);
                    if (currentPhase != lastKnown) {
                        trackedPhases.put(uuid, currentPhase);
                        String command = PHASE_COMMANDS.getOrDefault(currentPhase, "say Ignis entered unknown phase " + currentPhase + "!");
                        runCommandNear(level, entity, command, COMMAND_RADIUS);
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public static void onPlayerRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        BlockPos pos = event.getPos();
        BlockState state = level.getBlockState(pos);
        ItemStack heldItem = player.getItemInHand(event.getHand());

        ResourceLocation blockId = state.getBlock().builtInRegistryHolder().key().location();
        ResourceLocation itemId = heldItem.getItem().builtInRegistryHolder().key().location();

        if (blockId.equals(ALTAR_ID) && itemId.equals(SUMMON_ITEM_ID)) {
            delayedSpawnCommands.put(ALTAR_SUMMON_UUID, SPAWN_COMMAND_DELAY_TICKS);
            scheduledSummonLevels.put(ALTAR_SUMMON_UUID, level);
            scheduledSummonPositions.put(ALTAR_SUMMON_UUID, pos);
        }
    }

    private static int getIgnisPhase(Entity ignis) {
        try {
            Method m = ignis.getClass().getMethod("getBossPhase");
            Object result = m.invoke(ignis);
            if (result instanceof Integer phase) {
                return phase + 1;
            }
        } catch (Exception ignored) {}
        return -1;
    }

    private static void runCommandNear(ServerLevel level, Entity centerEntity, String command, double radius) {
        runCommandNear(level, centerEntity.position(), command, radius);
    }

    private static void runCommandNear(ServerLevel level, BlockPos pos, String command, double radius) {
        runCommandNear(level, Vec3.atCenterOf(pos), command, radius);
    }

    private static void runCommandNear(ServerLevel level, Vec3 center, String command, double radius) {
        AABB area = new AABB(center.subtract(radius, radius, radius), center.add(radius, radius, radius));
        List<ServerPlayer> players = level.getEntitiesOfClass(ServerPlayer.class, area);
        for (ServerPlayer player : players) {
            CommandSourceStack source = player.createCommandSourceStack()
                    .withPermission(4)
                    .withSuppressedOutput()
                    .withPosition(player.position());
            level.getServer().getCommands().performPrefixedCommand(source, command);
        }
    }
}
