package net.tysontheember.echoesofbattle.cataclysm;

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
import net.tysontheember.echoesofbattle.EchoesOfBattle;
import net.tysontheember.echoesofbattle.sound.ModSounds;
import net.tysontheember.echoesofbattle.sound.SoundUtils;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber(modid = EchoesOfBattle.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class IgnisEventHandler {

    private static final String IGNIS_ID_STRING = "cataclysm:ignis";
    private static final ResourceLocation IGNIS_ID = new ResourceLocation(IGNIS_ID_STRING);
    private static final ResourceLocation ALTAR_ID = new ResourceLocation("cataclysm", "altar_of_fire");
    private static final ResourceLocation SUMMON_ITEM_ID = new ResourceLocation("cataclysm", "burning_ashes");

    // Health thresholds for phase changes
    private static final float PHASE_2_HEALTH_THRESHOLD = 2 / 3f; // ~66.6%
    private static final float PHASE_3_HEALTH_THRESHOLD = 1 / 3f; // ~33.3%

    // Delay ticks for phase commands and death voice line
    private static final int PHASE_2_COMMAND_DELAY_TICKS = 40;
    private static final int PHASE_3_COMMAND_DELAY_TICKS = 50;
    private static final int DEATH_COMMAND_DELAY_TICKS = 40;

    // Track current phase per Ignis entity (0=start,1=phase2,2=phase3)
    private static final Map<UUID, Integer> currentPhaseMap = new ConcurrentHashMap<>();

    // Scheduled commands delay: entity UUID -> ticks left before running
    private static final Map<UUID, Integer> scheduledPhaseCommandsDelay = new ConcurrentHashMap<>();
    private static final Map<UUID, Integer> scheduledPhaseCommandsType = new ConcurrentHashMap<>();

    // Delayed spawn commands for summoning Ignis
    private static final Map<UUID, Integer> delayedSpawnCommands = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> scheduledSummonLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> scheduledSummonPositions = new ConcurrentHashMap<>();

    // Delayed death commands: UUID -> ticks left
    private static final Map<UUID, Integer> scheduledDeathCommandsDelay = new ConcurrentHashMap<>();
    // Store death positions for running delayed commands
    private static final Map<UUID, Vec3> deathPositions = new ConcurrentHashMap<>();

    private static final double COMMAND_RADIUS = 32.0;
    private static final int SPAWN_COMMAND_DELAY_TICKS = 0;

    // Commands & sounds per phase type (1=phase2, 2=phase3)
    private static final Map<Integer, List<String>> PHASE_COMMANDS = Map.of(
    );

    private static final UUID ALTAR_SUMMON_UUID = new UUID(0, 0);

    @SubscribeEvent
    public static void onIgnisSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();

        if (!level.isClientSide && entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID)) {
            delayedSpawnCommands.put(entity.getUUID(), SPAWN_COMMAND_DELAY_TICKS);
            currentPhaseMap.put(entity.getUUID(), 0); // Start phase 1
        }
    }

    @SubscribeEvent
    public static void onIgnisDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Level lvl = entity.level();
        if (!(lvl instanceof ServerLevel level)) return;

        if (!lvl.isClientSide && entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID)) {
            // Store position for delayed death commands
            deathPositions.put(entity.getUUID(), entity.position());
            scheduledDeathCommandsDelay.put(entity.getUUID(), DEATH_COMMAND_DELAY_TICKS);

            // Cleanup phase & scheduled commands immediately
            currentPhaseMap.remove(entity.getUUID());
            scheduledPhaseCommandsDelay.remove(entity.getUUID());
            scheduledPhaseCommandsType.remove(entity.getUUID());
            delayedSpawnCommands.remove(entity.getUUID());
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        // Handle delayed spawn commands
        List<UUID> toRemoveSpawn = new ArrayList<>();
        delayedSpawnCommands.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                if (uuid.equals(ALTAR_SUMMON_UUID)) {
                    ServerLevel lvl = scheduledSummonLevels.remove(uuid);
                    BlockPos altarPos = scheduledSummonPositions.remove(uuid);
                    if (lvl != null && altarPos != null) {
                        runCommandNear(lvl, altarPos, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:gold, size:5, bold:1, y:150, typewriter:1, shake:1} 6 It seems I have a challenger", COMMAND_RADIUS);
                        SoundUtils.playLoudSound(lvl, Vec3.atCenterOf(altarPos), ModSounds.IGNIS_SUMMON.getId(), 3.0F, 1.0F);
                    }
                } else {
                    for (ServerLevel level : server.getAllLevels()) {
                        StreamSupport.stream(level.getEntities().getAll().spliterator(), false)
                                .filter(e -> e.getUUID().equals(uuid))
                                .findFirst()
                                .ifPresent(ignis -> runCommandNear(level, ignis, "immersivemessages sendcustom @s {font:\"immersivemessages:norse\",bold:1,background:1,size:3,y:100,color:gold,obfuscate:1} 5 Ignis: Inferno Knight", COMMAND_RADIUS));
                    }
                }
                toRemoveSpawn.add(uuid);
            } else {
                delayedSpawnCommands.put(uuid, ticksLeft - 1);
            }
        });
        toRemoveSpawn.forEach(delayedSpawnCommands::remove);

        // Handle scheduled phase commands with delay
        List<UUID> toRemovePhase = new ArrayList<>();
        scheduledPhaseCommandsDelay.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                int phaseType = scheduledPhaseCommandsType.getOrDefault(uuid, -1);
                if (phaseType != -1) {
                    boolean ran = false;
                    for (ServerLevel level : server.getAllLevels()) {
                        Optional<Entity> maybeIgnis = StreamSupport.stream(level.getEntities().getAll().spliterator(), false)
                                .filter(e -> e.getUUID().equals(uuid))
                                .findFirst();
                        if (maybeIgnis.isPresent()) {
                            Entity ignis = maybeIgnis.get();
                            List<String> commands = PHASE_COMMANDS.getOrDefault(phaseType, Collections.emptyList());
                            for (String command : commands) {
                                if (command.startsWith("sound:")) {
                                    String soundId = command.substring(6);
                                    switch (soundId) {
                                        case "ignis_phase1" -> SoundUtils.playLoudSound(level, ignis.position(), ModSounds.IGNIS_PHASE1.getId(), 3.0F, 1.0F);
                                        case "ignis_phase2" -> SoundUtils.playLoudSound(level, ignis.position(), ModSounds.IGNIS_PHASE2.getId(), 3.0F, 1.0F);
                                    }
                                } else {
                                    String parsedCommand = command.replace("~ ~ ~", String.format("%.2f %.2f %.2f", ignis.getX(), ignis.getY(), ignis.getZ()));
                                    runCommandNear(level, ignis, parsedCommand, COMMAND_RADIUS);
                                }
                            }
                            ran = true;
                            break;
                        }
                    }
                }
                toRemovePhase.add(uuid);
                scheduledPhaseCommandsType.remove(uuid);
            } else {
                scheduledPhaseCommandsDelay.put(uuid, ticksLeft - 1);
            }
        });
        toRemovePhase.forEach(scheduledPhaseCommandsDelay::remove);

        // Handle delayed death commands
        List<UUID> toRemoveDeath = new ArrayList<>();
        scheduledDeathCommandsDelay.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                Vec3 deathPos = deathPositions.remove(uuid);
                if (deathPos == null) deathPos = Vec3.ZERO;
                for (ServerLevel level : server.getAllLevels()) {
                    runCommandNear(level, deathPos, "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 8 You are strong indeed...", COMMAND_RADIUS);
                    SoundUtils.playLoudSound(level, deathPos, ModSounds.IGNIS_DEATH.getId(), 3.0F, 1.0F);
                }
                toRemoveDeath.add(uuid);
            } else {
                scheduledDeathCommandsDelay.put(uuid, ticksLeft - 1);
            }
        });
        toRemoveDeath.forEach(scheduledDeathCommandsDelay::remove);

        // Health-based phase detection logic
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getEntities().getAll()) {
                if (entity instanceof LivingEntity livingEntity &&
                        entity.getType().builtInRegistryHolder().key().location().equals(IGNIS_ID) &&
                        entity.isAlive()) {

                    UUID uuid = entity.getUUID();
                    float health = livingEntity.getHealth();
                    float maxHealth = livingEntity.getMaxHealth();
                    float healthRatio = health / maxHealth;

                    int oldPhase = currentPhaseMap.getOrDefault(uuid, 0);
                    int newPhase;

                    if (healthRatio <= PHASE_3_HEALTH_THRESHOLD) newPhase = 2;
                    else if (healthRatio <= PHASE_2_HEALTH_THRESHOLD) newPhase = 1;
                    else newPhase = 0;

                    if (newPhase != oldPhase) {
                        currentPhaseMap.put(uuid, newPhase);
                        switch (newPhase) {
                            case 1 -> {
                                scheduledPhaseCommandsDelay.put(uuid, PHASE_2_COMMAND_DELAY_TICKS);
                                scheduledPhaseCommandsType.put(uuid, 1);
                            }
                            case 2 -> {
                                scheduledPhaseCommandsDelay.put(uuid, PHASE_3_COMMAND_DELAY_TICKS);
                                scheduledPhaseCommandsType.put(uuid, 2);
                            }
                        }
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
            delayedSpawnCommands.put(ALTAR_SUMMON_UUID, 20);
            scheduledSummonLevels.put(ALTAR_SUMMON_UUID, level);
            scheduledSummonPositions.put(ALTAR_SUMMON_UUID, pos);
        }
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
