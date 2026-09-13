package com.naglyadno.speedhunt.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/** Чисто визуальные настройки клиента (не влияют на геймплей других игроков). */
public class ClientSettings {

	private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
	private static final Path PATH = FabricLoader.getInstance().getConfigDir().resolve("speedhunt_client.json");

	private static ClientSettings instance;

	public boolean hudEnabled = true;
	public boolean trackerHudEnabled = true;
	public float hudScale = 1.0f;

	public static ClientSettings get() {
		if (instance == null) {
			instance = load();
		}
		return instance;
	}

	public void save() {
		try {
			Files.createDirectories(PATH.getParent());
			try (Writer writer = Files.newBufferedWriter(PATH)) {
				GSON.toJson(this, writer);
			}
		} catch (IOException ignored) {
			// не критично, просто не сохранится между запусками
		}
	}

	private static ClientSettings load() {
		try {
			if (Files.exists(PATH)) {
				try (Reader reader = Files.newBufferedReader(PATH)) {
					ClientSettings loaded = GSON.fromJson(reader, ClientSettings.class);
					if (loaded != null) {
						return loaded;
					}
				}
			}
		} catch (IOException ignored) {
			// используем значения по умолчанию
		}
		return new ClientSettings();
	}
}
