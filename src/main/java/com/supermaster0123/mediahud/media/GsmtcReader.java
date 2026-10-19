package com.supermaster0123.mediahud.media;

import com.google.gson.JsonObject;
import com.supermaster0123.mediahud.MediaHudMod;
import com.google.gson.JsonParser;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.function.BiConsumer;

/**
 * Lanza gsmtc.ps1 (PowerShell) como proceso hijo y lee sus líneas JSON.
 *
 * TODO esto corre en un hilo propio (daemon), NUNCA en el hilo de render
 * de Minecraft, así el juego no se congela mientras Windows responde.
 *
 * Cada vez que llega información nueva llama al "listener" con:
 *   - el nuevo MediaState
 *   - los bytes PNG de la portada (null = sin cambios; byte[0] = sin portada)
 */
final class GsmtcReader {

	/** Tamaño al que reducimos la portada (cuadrada) antes de subirla a la GPU. */
	static final int COVER_SIZE = 64;

	private final BiConsumer<MediaState, byte[]> listener;
	private volatile boolean running = true;
	private volatile Process process;
	private Thread thread;
	private String lastCoverError = "";

	GsmtcReader(BiConsumer<MediaState, byte[]> listener) {
		this.listener = listener;
	}

	void start() {
		thread = new Thread(this::runLoop, "MediaHud-GSMTC");
		thread.setDaemon(true); // no impide que el juego cierre
		thread.start();
	}

	void stop() {
		running = false;
		Process p = process;
		if (p != null) {
			p.destroyForcibly();
		}
	}

	private void runLoop() {
		while (running) {
			try {
				Path script = extractScript();
				ProcessBuilder builder = new ProcessBuilder(
						"powershell.exe", "-NoProfile", "-NonInteractive",
						"-ExecutionPolicy", "Bypass", "-File", script.toString());
				builder.redirectErrorStream(false);
				process = builder.start();

				try (BufferedReader reader = new BufferedReader(
						new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
					String line;
					while (running && (line = reader.readLine()) != null) {
						handleLine(line.trim());
					}
				}
			} catch (Exception e) {
				// Si algo falla (PowerShell no disponible, etc.) lo reintentamos más tarde.
			}
			listener.accept(MediaState.NONE, new byte[0]);
			sleep(5000);
		}
	}

	/** Copia gsmtc.ps1 del .jar a un archivo temporal (PowerShell necesita un archivo real). */
	private Path extractScript() throws Exception {
		try (InputStream in = GsmtcReader.class.getResourceAsStream("/assets/mediahud/gsmtc.ps1")) {
			if (in == null) {
				throw new IllegalStateException("No se encontró gsmtc.ps1 dentro del mod");
			}
			Path file = Files.createTempFile("mediahud-gsmtc", ".ps1");
			Files.copy(in, file, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
			file.toFile().deleteOnExit();
			return file;
		}
	}

	/** Convierte una línea JSON en MediaState (+ portada si vino una). */
	private void handleLine(String line) {
		if (line.isEmpty() || line.charAt(0) != '{') {
			return; // ignora basura (avisos de PowerShell, etc.)
		}
		JsonObject json = JsonParser.parseString(line).getAsJsonObject();

		if (!json.has("session") || !json.get("session").getAsBoolean()) {
			listener.accept(MediaState.NONE, new byte[0]);
			return;
		}

		MediaState state = new MediaState(
				true,
				str(json, "title"),
				str(json, "artist"),
				str(json, "album"),
				json.has("playing") && json.get("playing").getAsBoolean(),
				json.has("position") ? json.get("position").getAsLong() : 0,
				json.has("duration") ? json.get("duration").getAsLong() : 0,
				System.nanoTime());

		// Si PowerShell tuvo problemas con la portada, lo dejamos en el log (logs/latest.log)
		if (json.has("coverError")) {
			String error = json.get("coverError").getAsString();
			if (!error.equals(lastCoverError)) {
				lastCoverError = error;
				MediaHudMod.LOGGER.warn("Portada: {}", error);
			}
		}

		// "cover" solo viene cuando hay algo nuevo (portada encontrada o borrada)
		byte[] coverPng = null;
		if (json.has("cover")) {
			coverPng = decodeCover(json.get("cover").getAsString());
			MediaHudMod.LOGGER.info("Portada recibida: {} bytes (PNG 64x64)", coverPng.length);
		}
		listener.accept(state, coverPng);
	}

	/**
	 * Base64 -> imagen (JPEG/PNG/BMP...) -> recorte cuadrado -> 64x64 -> bytes PNG.
	 * Minecraft (NativeImage) solo sabe leer PNG, por eso reconvertimos con ImageIO.
	 */
	private static byte[] decodeCover(String base64) {
		if (base64 == null || base64.isEmpty()) {
			return new byte[0];
		}
		try {
			byte[] raw = Base64.getDecoder().decode(base64);
			BufferedImage source = ImageIO.read(new ByteArrayInputStream(raw));
			if (source == null) {
				return new byte[0];
			}
			// Recorte cuadrado centrado
			int side = Math.min(source.getWidth(), source.getHeight());
			BufferedImage square = source.getSubimage(
					(source.getWidth() - side) / 2, (source.getHeight() - side) / 2, side, side);

			BufferedImage small = new BufferedImage(COVER_SIZE, COVER_SIZE, BufferedImage.TYPE_INT_ARGB);
			Graphics2D g = small.createGraphics();
			g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
			g.drawImage(square, 0, 0, COVER_SIZE, COVER_SIZE, null);
			g.dispose();

			ByteArrayOutputStream out = new ByteArrayOutputStream();
			ImageIO.write(small, "png", out);
			return out.toByteArray();
		} catch (Exception e) {
			return new byte[0];
		}
	}

	private static String str(JsonObject json, String key) {
		return json.has(key) && !json.get(key).isJsonNull() ? json.get(key).getAsString() : "";
	}

	private static void sleep(long millis) {
		try {
			Thread.sleep(millis);
		} catch (InterruptedException ignored) {
			Thread.currentThread().interrupt();
		}
	}
}
