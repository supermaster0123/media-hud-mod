package com.supermaster0123.mediahud;

import net.fabricmc.api.ClientModInitializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Punto de entrada del mod (solo cliente).
 * Fabric lo ejecuta una vez al iniciar el juego, según "entrypoints" en fabric.mod.json.
 *
 * Aquí casi no hay lógica: el trabajo real ocurre cuando se abre el menú de pausa
 * (ver PauseScreenMixin) y el reproductor arranca la primera vez que se crea el widget.
 */
public class MediaHudMod implements ClientModInitializer {

	public static final String MOD_ID = "mediahud";
	public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

	@Override
	public void onInitializeClient() {
		LOGGER.info("Media HUD cargado (solo funciona en Windows).");
	}
}
