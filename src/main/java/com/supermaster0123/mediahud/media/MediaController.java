package com.supermaster0123.mediahud.media;

import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Fachada única del reproductor: la interfaz gráfica solo habla con esta clase.
 *
 *  - Lectura: getState() / getCoverVersion() / getCoverPng() son baratos (solo leen variables).
 *  - Comandos: playPause(), next()... se ejecutan en un hilo aparte para no bloquear el juego.
 */
public final class MediaController {

	private static final MediaController INSTANCE = new MediaController();

	public static MediaController get() {
		return INSTANCE;
	}

	/** Este mod solo funciona en Windows. */
	public static final boolean IS_WINDOWS =
			System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");

	// "volatile" = lo escribe un hilo (lector) y lo lee otro (render) con seguridad.
	private volatile MediaState state = MediaState.NONE;
	private volatile byte[] coverPng = new byte[0];
	private volatile int coverVersion = 0;

	/** Hilo único para comandos: las pulsaciones se ejecutan en orden, sin tocar el hilo de render. */
	private final ExecutorService commandThread = Executors.newSingleThreadExecutor(runnable -> {
		Thread t = new Thread(runnable, "MediaHud-Commands");
		t.setDaemon(true);
		return t;
	});

	private GsmtcReader reader;
	private boolean started;

	private MediaController() {
	}

	/** Arranca el lector de metadatos (solo la primera vez que se llama). */
	public synchronized void ensureStarted() {
		if (started || !IS_WINDOWS) {
			return;
		}
		started = true;
		reader = new GsmtcReader(this::onUpdate);
		reader.start();
		// Al cerrar el juego, cerramos también el proceso de PowerShell.
		Runtime.getRuntime().addShutdownHook(new Thread(this::shutdown));
	}

	public synchronized void shutdown() {
		if (reader != null) {
			reader.stop();
		}
		commandThread.shutdownNow();
	}

	/** Llamado desde el hilo del lector cuando llegan datos nuevos. */
	private void onUpdate(MediaState newState, byte[] newCover) {
		this.state = newState;
		if (newCover != null) { // null = la portada no cambió
			this.coverPng = newCover;
			this.coverVersion++; // el widget compara esta versión para saber si debe recargar la textura
		}
	}

	// ---------- Lectura (hilo de render) ----------

	public MediaState getState() {
		return state;
	}

	/** Sube cada vez que cambia la portada (incluye "sin portada"). */
	public int getCoverVersion() {
		return coverVersion;
	}

	/** PNG de 64x64, o un array vacío si no hay portada. */
	public byte[] getCoverPng() {
		return coverPng;
	}

	// ---------- Comandos (se ejecutan fuera del hilo de render) ----------

	public void playPause() {
		run(MediaKeys::playPause);
	}

	public void next() {
		run(MediaKeys::next);
	}

	public void previous() {
		run(MediaKeys::previous);
	}

	public void toggleMute() {
		run(MediaKeys::toggleMute);
	}

	private void run(Runnable action) {
		if (!IS_WINDOWS) {
			return;
		}
		commandThread.execute(() -> {
			try {
				action.run();
			} catch (Throwable ignored) {
				// Si JNA falla no queremos tumbar el juego
			}
		});
	}
}
