package net.tysontheember.echoesofbattle.cataclysm;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.tysontheember.echoesofbattle.EchoesOfBattle;
import net.tysontheember.echoesofbattle.sound.ModSounds;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.StreamSupport;

@Mod.EventBusSubscriber(modid = EchoesOfBattle.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public class ScyllaEventHandler {

    private static final ResourceLocation SCYLLA_ID = new ResourceLocation("cataclysm", "scylla");
    private static final ResourceLocation BOSS_RESPAWNER_ID = new ResourceLocation("cataclysm", "boss_respawner");

    private static final ResourceLocation ITEM_STORM_EYE = new ResourceLocation("cataclysm", "storm_eye");
    private static final ResourceLocation ITEM_EYE_OF_THE_STORM = new ResourceLocation("cataclysm", "eye_of_the_storm");
    private static final ResourceLocation ITEM_EYE_OF_STORM = new ResourceLocation("cataclysm", "eye_of_storm");

    private static final int DEATH_COMMAND_DELAY_TICKS = 40;
    private static final int SPAWN_COMMAND_DELAY_TICKS = 0;

    private static final double COMMAND_RADIUS = 32.0;

    private static final UUID STRUCTURE_SUMMON_UUID = new UUID(0, 0);

    private static final Set<UUID> SCYLLA_STARTED = ConcurrentHashMap.newKeySet();
    private static final String NBT_EOB_SCYLLA_STARTED = "eob_scylla_started";

    private static final Map<UUID, Integer> delayedSpawnCommands = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> scheduledSummonLevels = new ConcurrentHashMap<>();
    private static final Map<UUID, BlockPos> scheduledSummonPositions = new ConcurrentHashMap<>();

    private static final Map<UUID, Integer> scheduledDeathCommandsDelay = new ConcurrentHashMap<>();
    private static final Map<UUID, Vec3> deathPositions = new ConcurrentHashMap<>();
    private static final Map<UUID, ServerLevel> deathLevels = new ConcurrentHashMap<>();

    @SubscribeEvent
    public static void onScyllaSpawn(EntityJoinLevelEvent event) {
        Entity entity = event.getEntity();
        Level level = event.getLevel();

        if (!level.isClientSide && Objects.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), SCYLLA_ID)) {
            delayedSpawnCommands.put(entity.getUUID(), SPAWN_COMMAND_DELAY_TICKS);

            if (entity instanceof LivingEntity le) {
                if (le.getPersistentData().getBoolean(NBT_EOB_SCYLLA_STARTED)) {
                    SCYLLA_STARTED.add(entity.getUUID());
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScyllaDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        Level lvl = entity.level();
        if (!(lvl instanceof ServerLevel level)) return;

        if (!lvl.isClientSide && Objects.equals(ForgeRegistries.ENTITY_TYPES.getKey(entity.getType()), SCYLLA_ID)) {
            deathPositions.put(entity.getUUID(), entity.position());
            deathLevels.put(entity.getUUID(), level);
            scheduledDeathCommandsDelay.put(entity.getUUID(), DEATH_COMMAND_DELAY_TICKS);

            delayedSpawnCommands.remove(entity.getUUID());

            SCYLLA_STARTED.remove(entity.getUUID());
            entity.getPersistentData().remove(NBT_EOB_SCYLLA_STARTED);
        }
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        MinecraftServer server = event.getServer();

        List<UUID> toRemoveSpawn = new ArrayList<>();
        delayedSpawnCommands.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                if (uuid.equals(STRUCTURE_SUMMON_UUID)) {
                    ServerLevel lvl = scheduledSummonLevels.remove(uuid);
                    BlockPos pos = scheduledSummonPositions.remove(uuid);
                    if (lvl != null && pos != null) {
                        runCommandNear(lvl, pos,
                                "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:1, shake:1} 6 How dare you trample upon my palace!",
                                COMMAND_RADIUS);

                        lvl.playSound(null, pos, ModSounds.SCYLLA_SUMMON.get(), SoundSource.HOSTILE, 3.0F, 1.0F);
                    }
                } else {
                    for (ServerLevel level : server.getAllLevels()) {
                        StreamSupport.stream(level.getEntities().getAll().spliterator(), false)
                                .filter(e -> e.getUUID().equals(uuid))
                                .findFirst()
                                .ifPresent(scylla -> runCommandNear(level, scylla,
                                        "immersivemessages sendcustom @s {font:\"immersivemessages:norse\",bold:1,background:1,size:3,y:100,color:aqua,obfuscate:0} 5 Scylla: Storm Empress",
                                        COMMAND_RADIUS));
                    }
                }
                toRemoveSpawn.add(uuid);
            } else {
                delayedSpawnCommands.put(uuid, ticksLeft - 1);
            }
        });
        toRemoveSpawn.forEach(delayedSpawnCommands::remove);

        List<UUID> toRemoveDeath = new ArrayList<>();
        scheduledDeathCommandsDelay.forEach((uuid, ticksLeft) -> {
            if (ticksLeft <= 0) {
                Vec3 deathPos = deathPositions.remove(uuid);
                ServerLevel level = deathLevels.remove(uuid);
                if (deathPos == null || level == null) {
                    toRemoveDeath.add(uuid);
                    return;
                }

                runCommandNear(level, deathPos,
                        "immersivemessages sendcustom @s {font:'immersivemessages:norse', color:aqua, size:5, bold:1, y:150, typewriter:0, shake:0} 8 I will not let you...",
                        COMMAND_RADIUS);

                level.playSound(null, BlockPos.containing(deathPos), ModSounds.SCYLLA_DEATH.get(), SoundSource.HOSTILE, 3.0F, 1.0F);

                toRemoveDeath.add(uuid);
            } else {
                scheduledDeathCommandsDelay.put(uuid, ticksLeft - 1);
            }
        });
        toRemoveDeath.forEach(scheduledDeathCommandsDelay::remove);
    }

    @SubscribeEvent
    public static void onPlayerRightClickEntity(PlayerInteractEvent.EntityInteract event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        if (!(player.level() instanceof ServerLevel level)) return;
        if (event.getHand() != InteractionHand.MAIN_HAND) return;

        Entity target = event.getTarget();
        if (!(target instanceof LivingEntity scylla)) return;

        ResourceLocation entityId = ForgeRegistries.ENTITY_TYPES.getKey(target.getType());
        if (!SCYLLA_ID.equals(entityId)) return;

        UUID id = target.getUUID();
        boolean alreadyStarted = SCYLLA_STARTED.contains(id) || scylla.getPersistentData().getBoolean(NBT_EOB_SCYLLA_STARTED);
        if (alreadyStarted) return;

        if (!isAtFullHealth(scylla)) return;

        SCYLLA_STARTED.add(id);
        scylla.getPersistentData().putBoolean(NBT_EOB_SCYLLA_STARTED, true);

        delayedSpawnCommands.put(STRUCTURE_SUMMON_UUID, 20);
        scheduledSummonLevels.put(STRUCTURE_SUMMON_UUID, level);
        scheduledSummonPositions.put(STRUCTURE_SUMMON_UUID, target.blockPosition());
    }


    private static boolean isAtFullHealth(LivingEntity entity) {
        return entity.getHealth() >= entity.getMaxHealth() - 0.0001f;
    }

    private static boolean holdsStormEyeMainHand(ServerPlayer player) {
        return isStormEye(itemId(player.getMainHandItem()));
    }

    private static ResourceLocation itemId(ItemStack stack) {
        return (stack == null || stack.isEmpty())
                ? null
                : ForgeRegistries.ITEMS.getKey(stack.getItem());
    }

    private static boolean isStormEye(ResourceLocation id) {
        if (id == null) return false;
        if (!"cataclysm".equals(id.getNamespace())) return false;
        return id.equals(ITEM_STORM_EYE) || id.equals(ITEM_EYE_OF_THE_STORM) || id.equals(ITEM_EYE_OF_STORM);
    }

    private static boolean isBossRespawner(ResourceLocation blockId) {
        if (blockId.equals(BOSS_RESPAWNER_ID)) return true;
        return "cataclysm".equals(blockId.getNamespace()) && blockId.getPath().contains("respawn");
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
