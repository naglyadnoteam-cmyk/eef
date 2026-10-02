package com.naglyadno.nedra.sound;

import com.naglyadno.nedra.NedraMod;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import net.minecraft.registry.entry.RegistryEntry;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Собственные звуки мода (синтезированы специально для "Недр", см. assets/nedra/sounds). */
public final class ModSounds {

	private ModSounds() {
	}

	public static final SoundEvent GEOPHONE_PING = register("item.geophone.ping");
	public static final SoundEvent GEOPHONE_RETURN = register("item.geophone.return");
	public static final SoundEvent ECHO_ORE_CHIME = register("block.echo_ore.chime");
	public static final SoundEvent PRESSURE_GROAN = register("ambient.pressure.groan");
	public static final SoundEvent ROCKFALL_WARNING = register("event.rockfall.warning");
	public static final SoundEvent ROCKFALL_COLLAPSE = register("event.rockfall.collapse");
	public static final SoundEvent VENT_GUST = register("block.current_vent.gust");
	public static final SoundEvent MAGNETITE_BUZZ = register("item.compass.magnetized");
	public static final RegistryEntry.Reference<SoundEvent> TABLET_SWALLOW = registerReference("item.pressure_tablet.swallow");

	private static SoundEvent register(String path) {
		Identifier id = Identifier.of(NedraMod.MOD_ID, path);
		return Registry.register(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	private static RegistryEntry.Reference<SoundEvent> registerReference(String path) {
		Identifier id = Identifier.of(NedraMod.MOD_ID, path);
		return Registry.registerReference(Registries.SOUND_EVENT, id, SoundEvent.of(id));
	}

	public static void init() {
		// регистрация выполняется в статических полях
	}
}
