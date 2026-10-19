package com.supermaster0123.mediahud.ui;

import com.google.gson.Gson;
import com.supermaster0123.mediahud.MediaHudMod;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Guarda y recupera dónde dejó el jugador la tarjeta.
 *
 * Se guarda como FRACCIÓN de la pantalla (0.0 = borde izquierdo/superior, 1.0 = derecho/inferior),
 * no como píxeles. Así la posición se mantiene aunque cambies el tamaño de la ventana o la escala de la GUI.
 *
 * Archivo: <carpeta del juego>/config/mediahud.json
 */
public final class WidgetPosition {

	/** Forma del JSON: {"fx":0.25,"fy":0.8} */
	private static final class Data {
		double fx;
		double fy;
	}

	private static final Gson GSON = new Gson();

	private static Data data;      // null = el jugador nunca movió la tarjeta
	private static boolean loaded;

	private WidgetPosition() {
	}

	private static Path file() {
		return FabricLoader.getInstance().getConfigDir().resolve("mediahud.json");
	}

	/**
	 * Devuelve {x, y} en píxeles para esta pantalla, o null si no hay posición guardada.
	 */
	public static synchronized int[] resolve(int screenW, int screenH, int widgetW, int widgetH) {
		if (!loaded) {
			loaded = true;
			try {
				Path path = file();
				if (Files.exists(path)) {
					data = GSON.fromJson(Files.readString(path), Data.class);
				}
			} catch (Exception e) {
				data = null; // archivo dañado: lo ignoramos
			}
		}
		if (data == null) {
			return null;
		}
		int x = (int) Math.round(clamp01(data.fx) * Math.max(0, screenW - widgetW));
		int y = (int) Math.round(clamp01(data.fy) * Math.max(0, screenH - widgetH));
		return new int[]{x, y};
	}

	/** Recuerda la posición (en memoria al instante; a disco con save()). */
	static synchronized void remember(int x, int y, int screenW, int screenH, int widgetW, int widgetH) {
		loaded = true;
		Data d = new Data();
		d.fx = (screenW - widgetW) > 0 ? (double) x / (screenW - widgetW) : 0;
		d.fy = (screenH - widgetH) > 0 ? (double) y / (screenH - widgetH) : 0;
		data = d;
	}

	/** Escribe la posición en disco. */
	static synchronized void save() {
		if (data == null) {
			return;
		}
		try {
			Files.writeString(file(), GSON.toJson(data));
		} catch (Exception e) {
			MediaHudMod.LOGGER.warn("No se pudo guardar la posición: {}", e.getMessage());
		}
	}

	/** Olvida la posición personalizada (vuelve a la posición automática). */
	static synchronized void clear() {
		loaded = true;
		data = null;
		try {
			Files.deleteIfExists(file());
		} catch (Exception ignored) {
		}
	}

	private static double clamp01(double v) {
		return Math.max(0.0, Math.min(1.0, v));
	}
}
