# patrickszcustoms 1.21.11 (Fabric) - vibecoded

Custom crosshair manager, animated menu transitions, a Spotify HUD and a colorful in-game settings hub.

## Project structure
```
build.gradle / gradle.properties / settings.gradle   (Loom 1.14, Yarn 1.21.11+build.4, Fabric API 0.141.3+1.21.11)
src/main/resources/   fabric.mod.json, patrickszcustoms.mixins.json, lang/{en_us,ro_ro,hu_hu,es_es,ja_jp,zh_cn}.json
src/main/java/ro/patrickszcustoms/
  PatrickszCustomsClient   entrypoint, keybinds, HUD overlay element
  config/     ModConfig, ConfigManager, CrosshairType, UiTheme, HudPos, Language
  i18n/       Tr (in-mod language switching, English fallback)
  render/     CrosshairManager (PNG loading + dynamics), CrosshairRenderer
  animation/  ScreenAnimator, AnimationType
  spotify/    SpotifyController (media control), SpotifyHud (in-game overlay)
  gui/        ConfigScreen, FancyButton, FancySlider, Ui, UiSounds, AnimationTestScreen
  mixin/      InGameHudMixin, MinecraftClientMixin, ScreenMixin, DrawContextMixin
examples/crosshairs/   2 sample PNG crosshairs
```

## Build (get the .jar)
Easiest options:
- **Windows:** double-click `build.bat`  |  **macOS/Linux:** run `./build.sh`  (needs JDK 21 + Gradle 9.2+ once)
- **No install at all:** upload this folder to a GitHub repo; the included workflow (`.github/workflows/build.yml`) builds the JAR online. Download it from Actions -> build -> Artifacts.

Manual steps:
1. Install JDK 21 and Gradle 9.2+ (only needed once, to create the wrapper).
2. `gradle wrapper --gradle-version 9.2.0`
3. `./gradlew build` -> `build/libs/patrickszcustoms-1.21.11.jar` (not the `-sources` jar)
4. Quick test in a dev client: `./gradlew runClient`

## Install
Fabric Loader 0.18+, Fabric API 0.141.3+1.21.11, and the mod JAR in `.minecraft/mods/`.

## Using the mod
Press **O** to open the settings hub (rebindable). Sections:
- **Cursor Edit** - crosshair type (Vanilla, Dot, Circle, Classic precision, Cross + dot, Square, T-shape, X-shape, Ring + dot, Custom PNG), color, size, opacity, dynamic mode, live preview.
- **Crosshair Style** - thickness, gap, dark outline, hit marker (flashes when you hit an entity), rainbow color, sprint-grow and attack-shrink strength.
- **Presets & Share** - 5 one-click presets, plus copy / paste crosshair codes (`PC1:...`) to share your crosshair with friends. Only crosshair settings are shared.
- **Aim Assist** - soft aim pull toward the nearest visible mob inside a view cone: strength, range, cone angle, **any entity**: "All entities" switch or a per-type picker with live search (name or id), select/clear shown results, hostile-only shortcut; plus "only while attacking", vertical assist, with a live cone diagram. **Single-player only**: it is hard-disabled on servers and on worlds opened to LAN. Optional toggle key (unbound by default) in Keybinds.
- **Menu Animations** - Fade, Slide (4 directions), Zoom-in / Zoom-out, 6 easing curves (Linear, Smooth, Cubic, Overshoot, Elastic, Bounce), duration slider, test screen.
- **Spotify HUD** - previous / play-pause / next buttons, in-game "now playing" pill with animated equalizer (position, X/Y offset, scale, opacity, only-while-playing).
- **Lyrics HUD** - synced lyrics bar (clock | equalizer + current lyric with karaoke word highlight | song time), slides when the line changes, resizes to the text. Off by default; lyrics come from lrclib.net (the current artist + title are sent there). Position, scale, offsets, clock / song time / word-highlight toggles.
- **Theme & Colors** - 6 themes, rainbow accent, click sounds toggle.
- **Language** - English, Romanian (Română), Hungarian (Magyar), Spanish (Español), Japanese (日本語), Chinese (简体中文), or Auto (follows the game language). Switches the mod's menus and HUD instantly.
- **Keybinds** - rebind the menu key and the three Spotify keys (click a button, then press a key; ESC cancels). Spotify keys are unbound by default and work in-game without opening the menu.

### Custom crosshair PNGs
Put `.png` files in `.minecraft/config/patrickszcustoms/crosshairs/`, then click "Reload PNGs" and pick the file under "Texture".
PNGs are drawn as colored squares (max 48x48 cells, 32x32 or smaller recommended). The R/G/B sliders tint the image (white keeps the original colors).
Advanced values (`sprintScale`, `attackScale`) are in `config/patrickszcustoms.json`.

### Spotify
Controls the Spotify desktop app running in the background. No account, token or API key is used.
- Windows: media keys via PowerShell; the track name comes from the Spotify window title (not available while minimized to the tray, but the buttons still work).
- macOS: AppleScript (asks for "Automation" permission on first use).
- Linux: requires `playerctl`.

Only fixed commands are run, on a background thread. Buttons are ignored while Spotify is not running. The HUD is off by default because it queries Spotify about every 5 seconds.

### Sounds
Menu buttons use the vanilla click sound. Sidebar sections play a soft chime on a pentatonic scale, sliders tick while dragged, and "Done" has a lower click. They use the game's own sounds, so they follow the volume settings. Disable them under Theme & Colors -> "Click sounds".

## Limitations (1.21.11)
- Fade: `RenderSystem.setShaderColor` no longer exists, so alpha is applied by `DrawContextMixin` on fills, textured quads and text. Item icons and some widget labels may stay opaque during a fade. Slide and zoom are not affected.
- If a `DrawContextMixin` signature does not match, the game still starts (`require = 0`); only the fade gets weaker.
- Required mixin targets: `InGameHud#renderCrosshair`, `Screen#renderWithTooltip`, `MinecraftClient#setScreen`. If startup fails, run `./gradlew genSources` and check those names.
- The custom-drawn buttons subclass `ButtonWidget` / `SliderWidget`; a compile error there means a constructor or method name differs in 1.21.11.

### Adding another language
Copy `lang/en_us.json` to e.g. `lang/de_de.json`, translate the values, add an entry to the `Language` enum
(`GERMAN("de_de", "Deutsch")`). It will show up in the Language section automatically.

## Aim Assist notes
- Runs once per rendered frame and moves the camera the same way the mouse does, so your own aiming always wins.
- Only targets mobs you can actually see (ray-cast), never through walls.
- Pauses while the HUD is hidden (F1), because it is driven from the HUD overlay.
- Uses `MinecraftClient#isInSingleplayer()`; on multiplayer servers and LAN-opened worlds it does nothing.

## Lyrics notes
- Needs the Spotify **desktop app**. Song position comes from the Windows media session (PowerShell, one process, 1 line per second, stopped ~20 s after the lyrics/HUD are no longer drawn), AppleScript on macOS, `playerctl` on Linux.
- Lyrics are line-synced (LRC). The per-word highlight is interpolated across the line, so it is an approximation, not true word timing.
- If no synced lyrics exist for a song, the bar shows "Artist - Title" instead.
- Requires internet access for the lyrics lookup only (lrclib.net). Results are cached in memory for the session.
