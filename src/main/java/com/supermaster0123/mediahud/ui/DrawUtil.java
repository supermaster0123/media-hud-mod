package com.supermaster0123.mediahud.ui;

import net.minecraft.client.gui.GuiGraphicsExtractor;

/**
 * Pequeñas utilidades de dibujo hechas solo con rectángulos (graphics.fill).
 * Minecraft no trae "rectángulo redondeado" ni los íconos ⏮ ⏯ ⏭, así que los dibujamos nosotros.
 *
 * Recuerda: los colores son ARGB -> 0xAARRGGBB (AA = opacidad, FF = totalmente opaco).
 */
final class DrawUtil {

	private DrawUtil() {
	}

	/**
	 * Rectángulo con esquinas redondeadas.
	 * Truco: cada fila de las esquinas se "encoge" hacia dentro según un círculo.
	 */
	static void fillRounded(GuiGraphicsExtractor g, int x1, int y1, int x2, int y2, int radius, int color) {
		int r = Math.min(radius, Math.min((x2 - x1) / 2, (y2 - y1) / 2));
		for (int i = 0; i < r; i++) {
			double dy = r - i - 0.5;                       // distancia vertical al centro del círculo
			int inset = (int) Math.round(r - Math.sqrt(r * r - dy * dy));
			g.fill(x1 + inset, y1 + i, x2 - inset, y1 + i + 1, color);      // fila superior
			g.fill(x1 + inset, y2 - i - 1, x2 - inset, y2 - i, color);      // fila inferior
		}
		g.fill(x1, y1 + r, x2, y2 - r, color);                               // parte central
	}

	/** Triángulo apuntando a la derecha (▶). (x, y) = esquina superior izquierda. */
	static void triangleRight(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		for (int i = 0; i < width; i++) {
			int half = Math.max(1, (int) Math.round((height / 2.0) * (1.0 - (double) i / width)));
			g.fill(x + i, y + height / 2 - half, x + i + 1, y + height / 2 + half, color);
		}
	}

	/** Triángulo apuntando a la izquierda (◀). */
	static void triangleLeft(GuiGraphicsExtractor g, int x, int y, int width, int height, int color) {
		for (int i = 0; i < width; i++) {
			int half = Math.max(1, (int) Math.round((height / 2.0) * ((double) i / width)));
			g.fill(x + i, y + height / 2 - half, x + i + 1, y + height / 2 + half, color);
		}
	}

	/** Ícono ⏯ en modo "play" (▶). Centrado en (cx, cy). */
	static void iconPlay(GuiGraphicsExtractor g, int cx, int cy, int color) {
		triangleRight(g, cx - 4, cy - 6, 9, 12, color);
	}

	/** Ícono de pausa (▮▮). */
	static void iconPause(GuiGraphicsExtractor g, int cx, int cy, int color) {
		g.fill(cx - 4, cy - 6, cx - 1, cy + 6, color);
		g.fill(cx + 1, cy - 6, cx + 4, cy + 6, color);
	}

	/** Ícono ⏭ (siguiente): ▶▶ con una barrita. */
	static void iconNext(GuiGraphicsExtractor g, int cx, int cy, int color) {
		triangleRight(g, cx - 6, cy - 5, 6, 10, color);
		triangleRight(g, cx, cy - 5, 6, 10, color);
	}

	/** Ícono ⏮ (anterior): ◀◀. */
	static void iconPrevious(GuiGraphicsExtractor g, int cx, int cy, int color) {
		triangleLeft(g, cx - 6, cy - 5, 6, 10, color);
		triangleLeft(g, cx, cy - 5, 6, 10, color);
	}

	/** Ícono de altavoz. Si "muted" es true le dibuja una X. */
	static void iconVolume(GuiGraphicsExtractor g, int cx, int cy, boolean muted, int color) {
		g.fill(cx - 6, cy - 2, cx - 3, cy + 2, color);                   // cuerpo del altavoz
		triangleLeft(g, cx - 3, cy - 5, 5, 10, color);                    // cono (se ve hacia la derecha)
		if (muted) {
			for (int i = 0; i < 5; i++) {                                  // X sencilla
				g.fill(cx + 3 + i, cy - 3 + i, cx + 4 + i, cy - 2 + i, color);
				g.fill(cx + 3 + i, cy + 3 - i, cx + 4 + i, cy + 4 - i, color);
			}
		} else {
			g.fill(cx + 4, cy - 2, cx + 5, cy + 2, color);                // "ondas" de sonido
			g.fill(cx + 6, cy - 4, cx + 7, cy + 4, color);
		}
	}

	/** Formatea milisegundos como m:ss (ej. 0:02, 3:45). */
	static String formatTime(long millis) {
		long totalSeconds = Math.max(0, millis) / 1000;
		return (totalSeconds / 60) + ":" + String.format("%02d", totalSeconds % 60);
	}
}
