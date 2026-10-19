package com.supermaster0123.mediahud.ui;

import com.mojang.blaze3d.platform.NativeImage;
import com.supermaster0123.mediahud.MediaHudMod;
import com.supermaster0123.mediahud.media.MediaController;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.Identifier;

import java.io.ByteArrayInputStream;
import java.io.IOException;

/**
 * Convierte los bytes PNG de la portada en una textura de Minecraft "en memoria".
 *
 * Normalmente las texturas son archivos .png dentro del mod. Aquí la imagen cambia
 * en tiempo de ejecución, así que usamos DynamicTexture (en Yarn: NativeImageBackedTexture)
 * y la registramos en el TextureManager con un Identifier propio.
 *
 * Cada portada nueva usa un Identifier DISTINTO ("mediahud:cover/1", "cover/2"...).
 * Así evitamos que el juego siga usando una copia vieja en caché.
 *
 * Siempre se usa desde el hilo de render (donde se puede subir a la GPU).
 */
final class CoverTexture {

	private static DynamicTexture texture;
	private static Identifier currentId;
	private static int loadedVersion = -1;

	private CoverTexture() {
	}

	/**
	 * Llamar en cada fotograma: si la portada cambió, recarga la textura.
	 * @return el Identifier para dibujar, o null si no hay portada.
	 */
	static Identifier current(MediaController controller) {
		int version = controller.getCoverVersion();
		if (version != loadedVersion) {
			loadedVersion = version;
			load(controller.getCoverPng(), version);
		}
		return texture == null ? null : currentId;
	}

	private static void load(byte[] png, int version) {
		release();
		if (png == null || png.length == 0) {
			return;
		}
		try (ByteArrayInputStream in = new ByteArrayInputStream(png)) {
			NativeImage image = NativeImage.read(in);                        // PNG -> NativeImage (en RAM)
			DynamicTexture newTexture = new DynamicTexture(() -> "mediahud cover", image);
			newTexture.upload();                                             // asegura que los píxeles estén en la GPU
			Identifier id = Identifier.fromNamespaceAndPath("mediahud", "cover/" + version);
			Minecraft.getInstance().getTextureManager().register(id, newTexture);
			texture = newTexture;
			currentId = id;
			MediaHudMod.LOGGER.info("Textura de portada cargada: {}", id);
		} catch (IOException e) {
			MediaHudMod.LOGGER.warn("No se pudo leer la portada como PNG: {}", e.getMessage());
			texture = null;
			currentId = null;
		}
	}

	/** Libera la textura anterior (importante para no gastar memoria de video). */
	private static void release() {
		if (texture != null && currentId != null) {
			Minecraft.getInstance().getTextureManager().release(currentId);
		}
		texture = null;
		currentId = null;
	}
}
