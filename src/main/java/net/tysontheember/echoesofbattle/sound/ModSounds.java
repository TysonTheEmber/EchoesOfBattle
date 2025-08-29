package net.tysontheember.echoesofbattle.sound;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;
import net.tysontheember.echoesofbattle.EchoesOfBattle;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, EchoesOfBattle.MODID);

    public static final RegistryObject<SoundEvent> IGNIS_SUMMON = register("ignis_summon");
    public static final RegistryObject<SoundEvent> IGNIS_PHASE1 = register("ignis_phase1");
    public static final RegistryObject<SoundEvent> IGNIS_PHASE2 = register("ignis_phase2");
    public static final RegistryObject<SoundEvent> IGNIS_DEATH = register("ignis_death");

    public static final RegistryObject<SoundEvent> SCYLLA_SUMMON = register("scylla_summon");
    public static final RegistryObject<SoundEvent> SCYLLA_PHASE1 = register("scylla_phase1");
    public static final RegistryObject<SoundEvent> SCYLLA_PHASE2 = register("scylla_phase2");
    public static final RegistryObject<SoundEvent> SCYLLA_DEATH = register("scylla_death");

    private static RegistryObject<SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name,
                () -> SoundEvent.createVariableRangeEvent(new ResourceLocation(EchoesOfBattle.MODID, name)));
    }

    public static void register(IEventBus eventBus) {
        SOUND_EVENTS.register(eventBus);
    }
}
