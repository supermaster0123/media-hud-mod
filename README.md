# Media HUD

Un mod de Minecraft (Fabric) que muestra en el **menú de pausa** lo que estás escuchando en tu computadora y te deja controlarlo sin salir del juego.

Funciona con cualquier reproductor de Windows: Apple Music, Spotify, YouTube en el navegador, VLC y más.

## Qué hace

- Muestra la **portada**, el **título**, el **artista** y el **álbum** de la canción actual.
- Barra de **progreso** con el tiempo transcurrido y el restante.
- Botones para **pausar/reanudar**, pasar a la **siguiente** o volver a la **anterior**, y para **silenciar**.
- Puedes **arrastrar** la tarjeta a donde quieras y el mod recuerda su posición. Doble clic en el fondo la devuelve a su lugar original.

## Requisitos

- **Windows** (no funciona en macOS ni Linux).
- Minecraft **26.3** con [Fabric Loader](https://fabricmc.net/use/) y [Fabric API](https://modrinth.com/mod/fabric-api).
- Java 25.

## Instalación

1. Instala Fabric Loader y Fabric API para Minecraft 26.3.
2. Copia el archivo `.jar` del mod a la carpeta `mods` de tu juego.
3. Pon música a sonar, abre Minecraft y pulsa **Esc**.

## Cómo usarlo

Abre el menú de pausa y verás la tarjeta junto a los botones. Los controles actúan sobre el reproductor que Windows tenga activo en ese momento (el mismo que aparece en el control de volumen de la barra de tareas).

## Limitaciones

- Si tienes varios reproductores abiertos, los botones controlan el que Windows considere activo.
- El botón de silencio alterna el volumen **del sistema** y su ícono es aproximado, porque Windows no avisa si ya estaba silenciado.
- La barra de progreso solo informa: no permite saltar a otro punto de la canción.

## Licencia

[MIT](LICENSE) © supermaster0123
