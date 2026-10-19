package com.supermaster0123.mediahud.mixin;

import com.supermaster0123.mediahud.ui.MediaWidget;
import com.supermaster0123.mediahud.ui.WidgetPosition;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Mixin = "parche" que Fabric aplica a una clase del juego al cargarla.
 * Aquí parchamos PauseScreen (en Yarn se llamaba GameMenuScreen): el menú de pausa.
 *
 * Inyectamos código al FINAL de init() (TAIL), que es cuando el menú ya creó sus botones.
 * Así podemos medir dónde quedaron y colocar nuestra tarjeta sin tapar nada.
 */
@Mixin(PauseScreen.class)
public abstract class PauseScreenMixin extends Screen {

	private static final int MARGIN = 10;

	// Un Mixin que extiende Screen necesita este constructor (nunca se llama de verdad).
	protected PauseScreenMixin(Component title) {
		super(title);
	}

	@Inject(method = "init", at = @At("TAIL"))
	private void mediahud$addWidget(CallbackInfo ci) {
		// 1) Calcular el rectángulo que ocupan TODOS los botones del menú de pausa
		int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE;
		int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE;
		for (GuiEventListener child : this.children()) {
			if (child instanceof AbstractWidget widget) {
				minX = Math.min(minX, widget.getX());
				minY = Math.min(minY, widget.getY());
				maxX = Math.max(maxX, widget.getX() + widget.getWidth());
				maxY = Math.max(maxY, widget.getY() + widget.getHeight());
			}
		}

		int w = MediaWidget.WIDTH;
		int h = MediaWidget.HEIGHT;
		int x;
		int y;

		if (minX == Integer.MAX_VALUE) {
			// No encontramos botones (raro): esquina inferior izquierda
			x = MARGIN;
			y = this.height - h - MARGIN;
		} else if (minX - MARGIN - w >= MARGIN) {
			// Opción A: cabe a la IZQUIERDA del menú, alineada con su parte de arriba
			x = minX - MARGIN - w;
			y = clamp(minY, MARGIN, this.height - h - MARGIN);
		} else if (maxX + MARGIN + w + MARGIN <= this.width) {
			// Opción B: cabe a la DERECHA
			x = maxX + MARGIN;
			y = clamp(minY, MARGIN, this.height - h - MARGIN);
		} else if (maxY + MARGIN + h + MARGIN <= this.height) {
			// Opción C: ventana estrecha -> DEBAJO del menú, centrada
			x = (this.width - w) / 2;
			y = maxY + MARGIN;
		} else {
			// Opción D: ventana diminuta -> esquina inferior izquierda (puede solaparse un poco)
			x = MARGIN;
			y = this.height - h - MARGIN;
		}

		// 2) Si el jugador ya movió la tarjeta antes, usamos su posición guardada
		int autoX = x;
		int autoY = y;
		int[] saved = WidgetPosition.resolve(this.width, this.height, w, h);
		if (saved != null) {
			x = saved[0];
			y = saved[1];
		}

		// 3) Añadir la tarjeta a la pantalla: se dibuja, recibe clics y se puede arrastrar
		this.addRenderableWidget(new MediaWidget(x, y, autoX, autoY, this.width, this.height));
	}

	private static int clamp(int value, int min, int max) {
		return Math.max(min, Math.min(value, Math.max(min, max)));
	}
}
