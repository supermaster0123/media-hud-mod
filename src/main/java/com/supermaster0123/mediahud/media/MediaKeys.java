package com.supermaster0123.mediahud.media;

import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.win32.StdCallLibrary;

/**
 * Envía teclas multimedia "globales" a Windows usando JNA.
 *
 * Es como si pulsaras las teclas ⏮ ⏯ ⏭ de tu teclado: Windows se las entrega
 * a la sesión multimedia activa (Apple Music, Spotify, YouTube en el navegador, etc.).
 *
 * JNA (Java Native Access) nos deja llamar funciones de DLLs de Windows
 * sin escribir código C. Solo declaramos la firma de la función.
 */
public final class MediaKeys {

	/** Declaración de la función keybd_event de user32.dll. */
	private interface User32 extends StdCallLibrary {
		// Carga user32.dll la primera vez que se usa esta interfaz.
		User32 INSTANCE = Native.load("user32", User32.class);

		// void keybd_event(BYTE bVk, BYTE bScan, DWORD dwFlags, ULONG_PTR dwExtraInfo);
		void keybd_event(byte virtualKey, byte scanCode, int flags, Pointer extraInfo);
	}

	// Códigos de tecla virtual de Windows (VK_*)
	private static final int VK_VOLUME_MUTE = 0xAD;
	private static final int VK_VOLUME_DOWN = 0xAE;
	private static final int VK_VOLUME_UP = 0xAF;
	private static final int VK_MEDIA_NEXT_TRACK = 0xB0;
	private static final int VK_MEDIA_PREV_TRACK = 0xB1;
	private static final int VK_MEDIA_PLAY_PAUSE = 0xB3;

	private static final int KEYEVENTF_KEYUP = 0x0002;

	private MediaKeys() {
	}

	public static void playPause() {
		press(VK_MEDIA_PLAY_PAUSE);
	}

	public static void next() {
		press(VK_MEDIA_NEXT_TRACK);
	}

	public static void previous() {
		press(VK_MEDIA_PREV_TRACK);
	}

	public static void toggleMute() {
		press(VK_VOLUME_MUTE);
	}

	public static void volumeUp() {
		press(VK_VOLUME_UP);
	}

	public static void volumeDown() {
		press(VK_VOLUME_DOWN);
	}

	/** Simula "tecla abajo" y luego "tecla arriba". */
	private static void press(int virtualKey) {
		User32.INSTANCE.keybd_event((byte) virtualKey, (byte) 0, 0, null);
		User32.INSTANCE.keybd_event((byte) virtualKey, (byte) 0, KEYEVENTF_KEYUP, null);
	}
}
