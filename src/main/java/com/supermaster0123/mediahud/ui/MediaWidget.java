package com.supermaster0123.mediahud.ui;

import com.supermaster0123.mediahud.media.MediaController;
import com.supermaster0123.mediahud.media.MediaState;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

/**
 * La tarjeta del reproductor multimedia (el widget principal del mod).
 *
 * Extiende AbstractWidget, que ya sabe:
 *   - guardar posición y tamaño (getX(), getY(), width, height)
 *   - detectar si el ratón está encima
 *   - recibir clics (llamando a onClick)
 * Nosotros solo dibujamos (extractWidgetRenderState) y reaccionamos al clic (onClick).
 *
 * NOTA: desde Minecraft 26.1 "DrawContext" se llama GuiGraphicsExtractor y el método
 * "renderWidget" pasó a llamarse "extractWidgetRenderState". La idea es la misma.
 */
public class MediaWidget extends AbstractWidget {

	// ---- Tamaño de la tarjeta (en píxeles de GUI) ----
	public static final int WIDTH = 190;
	public static final int HEIGHT = 90;

	// ---- Colores ARGB (0xAARRGGBB) ----
	private static final int COLOR_BACKGROUND = 0xD0202024;   // oscuro translúcido
	private static final int COLOR_COVER_EMPTY = 0xFF3A3A3F;
	private static final int COLOR_TITLE = 0xFFFFFFFF;
	private static final int COLOR_SUBTITLE = 0xFFB4B4BC;
	private static final int COLOR_BAR_BACK = 0x55FFFFFF;
	private static final int COLOR_BAR_FRONT = 0xFFFFFFFF;
	private static final int COLOR_ICON = 0xFFFFFFFF;
	private static final int COLOR_HOVER = 0x33FFFFFF;
	private static final int COLOR_GRIP = 0x66FFFFFF;

	private static final int PADDING = 8;
	private static final int COVER_SIZE = 40;

	/** Área de clic de un botón (relativa a la pantalla). */
	private record Hitbox(int x, int y, int w, int h) {
		boolean contains(double px, double py) {
			return px >= x && px < x + w && py >= y && py < y + h;
		}
	}

	private final MediaController controller = MediaController.get();

	/** Solo sabemos lo que hacemos nosotros: Windows no nos dice si el volumen está en mute. */
	private boolean mutedGuess = false;

	// ---- Arrastre ----
	private final int autoX;          // posición "de fábrica" (calculada por el Mixin) para el doble clic de reinicio
	private final int autoY;
	private final int screenWidth;    // tamaño de la pantalla, para no sacar la tarjeta de ella
	private final int screenHeight;
	private boolean draggingCard = false;
	private double grabOffsetX;       // dónde dentro de la tarjeta agarró el jugador
	private double grabOffsetY;
	private boolean positionDirty = false;
	private long lastDragNanos = 0;

	/**
	 * @param x,y            posición inicial (la guardada o la automática)
	 * @param autoX,autoY    posición automática, a la que se vuelve con doble clic
	 * @param screenWidth,screenHeight  tamaño de la pantalla (en píxeles de GUI)
	 */
	public MediaWidget(int x, int y, int autoX, int autoY, int screenWidth, int screenHeight) {
		super(x, y, WIDTH, HEIGHT, Component.literal("Media HUD"));
		this.autoX = autoX;
		this.autoY = autoY;
		this.screenWidth = screenWidth;
		this.screenHeight = screenHeight;
		controller.ensureStarted(); // arranca el lector de metadatos si aún no estaba activo
	}

	// ------------------------------------------------------------------
	//  Áreas de clic (se calculan a partir de la posición actual del widget)
	// ------------------------------------------------------------------
	private int controlsCenterY() {
		return getY() + HEIGHT - 17;
	}

	private Hitbox volumeBox() {
		return new Hitbox(getX() + PADDING, controlsCenterY() - 8, 20, 16);
	}

	private Hitbox previousBox() {
		return new Hitbox(getX() + WIDTH / 2 - 52, controlsCenterY() - 8, 24, 16);
	}

	private Hitbox playBox() {
		return new Hitbox(getX() + WIDTH / 2 - 12, controlsCenterY() - 8, 24, 16);
	}

	private Hitbox nextBox() {
		return new Hitbox(getX() + WIDTH / 2 + 28, controlsCenterY() - 8, 24, 16);
	}

	// ------------------------------------------------------------------
	//  DIBUJO (se llama cada fotograma)
	// ------------------------------------------------------------------
	@Override
	protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
		Font font = Minecraft.getInstance().font;
		MediaState state = controller.getState();
		int x = getX();
		int y = getY();

		// 1) Fondo redondeado
		DrawUtil.fillRounded(g, x, y, x + WIDTH, y + HEIGHT, 6, COLOR_BACKGROUND);

		// 2) Portada (cuadrado arriba a la izquierda)
		int coverX = x + PADDING;
		int coverY = y + PADDING;
		Identifier cover = MediaController.IS_WINDOWS ? CoverTexture.current(controller) : null;
		if (cover != null && state.available()) {
			// x, y, u, v, ancho en pantalla, alto en pantalla, ancho de la región, alto de la región, tamaño textura
			g.blit(RenderPipelines.GUI_TEXTURED, cover, coverX, coverY, 0, 0,
					COVER_SIZE, COVER_SIZE, 64, 64, 64, 64);
		} else {
			DrawUtil.fillRounded(g, coverX, coverY, coverX + COVER_SIZE, coverY + COVER_SIZE, 3, COLOR_COVER_EMPTY);
		}

		// 3) Título (negrita) y "Artista — Álbum" con truncado
		int textX = coverX + COVER_SIZE + 8;
		int textWidth = x + WIDTH - PADDING - textX;

		String title;
		String subtitle;
		if (!MediaController.IS_WINDOWS) {
			title = "Solo disponible en Windows";
			subtitle = "";
		} else if (!state.available()) {
			title = "Nada en reproducción";
			subtitle = "Pon algo a sonar en tu reproductor";
		} else {
			title = state.title().isEmpty() ? "Sin título" : state.title();
			subtitle = joinArtistAlbum(state.artist(), state.album());
		}

		Component titleText = Component.literal(ellipsize(font, title, textWidth, true)).withStyle(ChatFormatting.BOLD);
		g.text(font, titleText, textX, coverY + 7, COLOR_TITLE, false);
		g.text(font, ellipsize(font, subtitle, textWidth, false), textX, coverY + 7 + font.lineHeight + 3, COLOR_SUBTITLE, false);

		// 4) Barra de progreso + tiempos
		long position = state.available() ? state.livePositionMs() : 0;
		long duration = state.available() ? state.durationMs() : 0;

		int barX1 = x + PADDING;
		int barX2 = x + WIDTH - PADDING;
		int barY = y + 54;
		g.fill(barX1, barY, barX2, barY + 3, COLOR_BAR_BACK);
		if (duration > 0) {
			int filled = (int) ((barX2 - barX1) * Math.min(1.0, (double) position / duration));
			g.fill(barX1, barY, barX1 + filled, barY + 3, COLOR_BAR_FRONT);
		}

		String left = DrawUtil.formatTime(position);
		String right = duration > 0 ? "-" + DrawUtil.formatTime(duration - position) : "-:--";
		g.text(font, left, barX1, barY + 6, COLOR_SUBTITLE, false);
		g.text(font, right, barX2 - font.width(right), barY + 6, COLOR_SUBTITLE, false);

		// 5) Fila de controles (con resaltado al pasar el ratón)
		int cy = controlsCenterY();
		drawHover(g, volumeBox(), mouseX, mouseY);
		drawHover(g, previousBox(), mouseX, mouseY);
		drawHover(g, playBox(), mouseX, mouseY);
		drawHover(g, nextBox(), mouseX, mouseY);

		DrawUtil.iconVolume(g, volumeBox().x() + 10, cy, mutedGuess, COLOR_ICON);
		DrawUtil.iconPrevious(g, previousBox().x() + 12, cy, COLOR_ICON);
		if (state.playing()) {
			DrawUtil.iconPause(g, playBox().x() + 12, cy, COLOR_ICON);
		} else {
			DrawUtil.iconPlay(g, playBox().x() + 12, cy, COLOR_ICON);
		}
		DrawUtil.iconNext(g, nextBox().x() + 12, cy, COLOR_ICON);

		// 6) "Agarradera" arriba a la derecha (6 puntitos): indica que la tarjeta se puede mover
		for (int row = 0; row < 2; row++) {
			for (int col = 0; col < 3; col++) {
				g.fill(x + WIDTH - 17 + col * 4, y + 5 + row * 4, x + WIDTH - 15 + col * 4, y + 7 + row * 4, COLOR_GRIP);
			}
		}

		// 7) Guardar en disco la posición, un instante DESPUÉS de dejar de arrastrar
		if (positionDirty && System.nanoTime() - lastDragNanos > 300_000_000L) {
			positionDirty = false;
			WidgetPosition.save();
		}
	}

	private void drawHover(GuiGraphicsExtractor g, Hitbox box, int mouseX, int mouseY) {
		if (box.contains(mouseX, mouseY)) {
			DrawUtil.fillRounded(g, box.x(), box.y(), box.x() + box.w(), box.y() + box.h(), 4, COLOR_HOVER);
		}
	}

	// ------------------------------------------------------------------
	//  CLIC (AbstractWidget lo llama solo cuando el clic cae DENTRO de la tarjeta)
	// ------------------------------------------------------------------
	@Override
	public void onClick(MouseButtonEvent event, boolean doubleClick) {
		double mx = event.x();
		double my = event.y();
		draggingCard = false;

		if (playBox().contains(mx, my)) {
			controller.playPause();
		} else if (nextBox().contains(mx, my)) {
			controller.next();
		} else if (previousBox().contains(mx, my)) {
			controller.previous();
		} else if (volumeBox().contains(mx, my)) {
			controller.toggleMute();
			mutedGuess = !mutedGuess;
		} else if (doubleClick) {
			// Doble clic en el fondo de la tarjeta: volver a la posición automática
			setX(autoX);
			setY(autoY);
			WidgetPosition.clear();
		} else {
			// Clic en el fondo: empezamos a arrastrar. Guardamos dónde agarró la tarjeta.
			draggingCard = true;
			grabOffsetX = mx - getX();
			grabOffsetY = my - getY();
		}
	}

	/**
	 * Se llama mientras el jugador mantiene el botón apretado y mueve el ratón.
	 * Colocamos la tarjeta bajo el cursor (respetando dónde la agarró) y la mantenemos dentro de la pantalla.
	 */
	@Override
	public void onDrag(MouseButtonEvent event, double dragX, double dragY) {
		if (!draggingCard) {
			return;
		}
		int newX = (int) Math.round(event.x() - grabOffsetX);
		int newY = (int) Math.round(event.y() - grabOffsetY);
		newX = Math.max(0, Math.min(newX, screenWidth - WIDTH));
		newY = Math.max(0, Math.min(newY, screenHeight - HEIGHT));
		setX(newX);
		setY(newY);

		WidgetPosition.remember(newX, newY, screenWidth, screenHeight, WIDTH, HEIGHT);
		positionDirty = true;
		lastDragNanos = System.nanoTime();
	}

	@Override
	protected void updateWidgetNarration(NarrationElementOutput output) {
		// Accesibilidad (lector de pantalla): lo dejamos vacío por simplicidad.
	}

	// ------------------------------------------------------------------
	//  Helpers de texto
	// ------------------------------------------------------------------
	private static String joinArtistAlbum(String artist, String album) {
		if (!artist.isEmpty() && !album.isEmpty()) {
			return artist + " — " + album;   // — = guion largo "—"
		}
		return artist.isEmpty() ? album : artist;
	}

	/** Recorta el texto y le añade "..." si no cabe en maxWidth píxeles. */
	private static String ellipsize(Font font, String text, int maxWidth, boolean bold) {
		if (widthOf(font, text, bold) <= maxWidth) {
			return text;
		}
		String ellipsis = "...";
		int end = text.length();
		while (end > 0 && widthOf(font, text.substring(0, end) + ellipsis, bold) > maxWidth) {
			end--;
		}
		return text.substring(0, end) + ellipsis;
	}

	private static int widthOf(Font font, String text, boolean bold) {
		if (bold) {
			return font.width(Component.literal(text).withStyle(ChatFormatting.BOLD));
		}
		return font.width(text);
	}
}
