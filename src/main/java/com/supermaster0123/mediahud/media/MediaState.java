package com.supermaster0123.mediahud.media;

/**
 * Una "foto" inmutable de lo que está sonando.
 * Es un "record" de Java: una clase de solo datos (el compilador crea
 * constructor, getters como title(), equals, etc. automáticamente).
 *
 * @param available  true si hay un reproductor con sesión activa
 * @param title      título de la pista
 * @param artist     artista
 * @param album      álbum
 * @param playing    true si está sonando (false = en pausa)
 * @param positionMs posición reportada por Windows, en milisegundos
 * @param durationMs duración total, en milisegundos (0 si es desconocida)
 * @param receivedAtNanos momento (System.nanoTime) en que llegó esta foto;
 *                        sirve para avanzar la barra de progreso entre actualizaciones
 */
public record MediaState(
		boolean available,
		String title,
		String artist,
		String album,
		boolean playing,
		long positionMs,
		long durationMs,
		long receivedAtNanos
) {
	/** Estado vacío: no hay nada reproduciéndose. */
	public static final MediaState NONE = new MediaState(false, "", "", "", false, 0, 0, 0);

	/**
	 * Posición "en vivo": la última reportada + el tiempo transcurrido si está sonando.
	 * Así la barra se mueve suave cada fotograma aunque Windows solo nos avise 1 vez por segundo.
	 */
	public long livePositionMs() {
		long position = positionMs;
		if (playing) {
			position += (System.nanoTime() - receivedAtNanos) / 1_000_000L;
		}
		if (durationMs > 0) {
			position = Math.min(position, durationMs);
		}
		return Math.max(0, position);
	}
}
