# AGENTS.md

## Scope

These instructions apply to the entire repository.

## Project Overview

- This is a Paper plugin for Minecraft 26.2, not a Fabric, Forge, or client mod.
- The project uses Java 25 and Gradle 9.7.1 through the Gradle wrapper.
- The main plugin class is `net.lesimc.guide_legowelt.Guide_legowelt`.
- The bootstrap class is `net.lesimc.guide_legowelt.GuideLegoweltBootstrap`.
- The plugin provides an in-game wiki through Paper dialogs and the `/wiki` command (`/guide` is an alias).
- Paper plugin metadata lives in `src/main/resources/paper-plugin.yml`.
- A registered table-of-contents dialog is exposed through the pause menu. Its additive server datapack tag lives under `src/main/resources/wiki-launcher-pack/` and is embedded in the plugin JAR.
- The default wiki catalog lives in `src/main/resources/pages.yml`.
- Standalone wiki resource-pack sources live under `resource-pack/`; the packaged ZIP is written to `build/distributions/`.
- Managed test-server Gradle logic lives in `buildSrc/src/main/kotlin/net/lesimc/build/TestServerTasks.kt`.
- The loopback wiki editor lives under `tools/wiki-editor/` and is served by `runWikiEditor` through `buildSrc/src/main/kotlin/net/lesimc/build/WikiEditorTask.kt`.
- Shared IntelliJ Gradle configurations for starting and stopping the managed test server live under `.run/`.
- Generated and local runtime directories such as `build/`, `.gradle/`, `.idea/`, and `run/` must not be committed.

## Build And Run

Use the checked-in Gradle wrapper. Do not depend on a globally installed Gradle version.

```powershell
.\gradlew.bat clean build
.\gradlew.bat buildResourcePack
.\gradlew.bat runServer
.\gradlew.bat runTestServer
.\gradlew.bat stopTestServer
.\gradlew.bat runWikiEditor
```

On Unix-like systems, use `./gradlew` instead.

- The built plugin JAR is written to `build/libs/`.
- The standalone resource-pack ZIP is written to `build/distributions/`.
- `runServer` downloads and starts the configured Paper 26.2 development server.
- `runTestServer` builds the plugin and standalone pack, copies the bundled pages to the test server, updates and hosts the test pack, and then starts Paper.
- `stopTestServer` gracefully stops a test server started through `runTestServer` and waits for its resource-pack host to stop.
- Prefer `runTestServer` for end-to-end local verification. It hosts the pack inside Gradle at `127.0.0.1:8123` and enables RCON only on loopback at port `25575` for `stopTestServer`.
- `runTestServer` intentionally overwrites `run/plugins/GuideLegowelt/pages.yml` with `src/main/resources/pages.yml` on every run.
- `runWikiEditor` edits the bundled `src/main/resources/pages.yml` and standalone resource-pack sources. It never edits a server's live pages file and only binds to a loopback address.
- Stop a running managed server with `stopTestServer` before running `clean`; Paper can otherwise retain a Windows file handle on the built plugin JAR.
- When overriding `guidelegowelt.testServerPort`, `guidelegowelt.testRconPort`, or `guidelegowelt.testRconPassword`, pass the same values to both the start and stop tasks.
- Do not change `run/eula.txt` or automatically accept Mojang's EULA unless the user has explicitly agreed to it.

## Source Conventions

- Keep production Java sources under `src/main/java/net/lesimc/guide_legowelt/`.
- Use standard Java naming for new types: `UpperCamelCase` classes and `lowerCamelCase` members.
- Preserve the existing `Guide_legowelt` entry point unless a rename is intentional. Any rename must also update the `main` value in `paper-plugin.yml`.
- Keep `Guide_legowelt` focused on plugin lifecycle and dependency wiring. Put commands, listeners, services, and domain logic in separate classes as they are added.
- Register Paper `BasicCommand` commands and listeners during `onEnable()` and release owned resources during `onDisable()`.
- Register datapacks and registry-backed dialogs through `GuideLegoweltBootstrap`; Bukkit `/reload` and plugin hot-reload tools are not supported for bootstrap registry changes.
- Use the Paper API where available. Avoid version-specific server internals unless the feature explicitly requires them.
- Use Adventure `Component` messages for player-facing text instead of legacy color-code strings.
- Do not perform blocking file, database, or network operations on the main server thread.
- Keep comments brief and limited to behavior that is not clear from the code itself.

## Dependencies And Resources

- Keep the Paper API as `compileOnly`; the server provides it at runtime.
- Prefer existing Paper and Java APIs before adding dependencies.
- If a runtime library is required, package and relocate it appropriately rather than expecting Paper to provide it.
- Keep `api-version: '26.2'` aligned with the Paper dependency and `runServer` version.
- Keep the plugin version sourced from Gradle through resource expansion. Do not hardcode it separately in `paper-plugin.yml`.
- Declare permissions, dependencies, and other plugin metadata in `paper-plugin.yml`. Paper plugins register commands in Java instead of YAML.
- Keep the image resource pack under `resource-pack/` standalone. Do not put image assets in `wiki-launcher-pack/`; that embedded server datapack only supplies the pause-menu dialog tag.

## Pause-Menu Wiki

- Keep the pause-menu dialog registry key aligned between `GuideLegoweltBootstrap.LAUNCHER_DIALOG_KEY` and `wiki-launcher-pack/data/minecraft/tags/dialog/pause_screen_additions.json`.
- Keep the tag additive with `"replace": false` so it interoperates with other pause-menu additions.
- Build the registered pause-menu table of contents from the live `pages.yml` during bootstrap, falling back to the bundled catalog on first installation or when the live catalog is invalid.
- Pause-menu topic actions must invoke `/wiki <page-id>` instead of capturing dynamic page dialogs. This keeps page content tied to the catalog loaded by `WikiDialogService`.
- `/wiki reload` updates dynamic `/wiki` dialogs and page content, but it cannot rebuild the registry-backed pause-menu table of contents. Root-page structure, titles, summaries, and table-of-contents settings require a normal Paper restart.
- Changes under `wiki-launcher-pack/`, to the registered dialog, or to `paper-plugin.yml` require a rebuilt plugin JAR and a normal Paper restart.
- Keep the embedded datapack format aligned with Minecraft 26.2. `PauseMenuLauncherResourceTest` fixes the expected `[107, 1]` format and launcher tag contract.

## Wiki Configuration

- Treat `src/main/resources/pages.yml` as the bundled default. On first startup it is copied to `plugins/GuideLegowelt/pages.yml`; existing server copies are not overwritten automatically.
- Use MiniMessage formatting for `wiki.title`, `wiki.introduction`, a configured `wiki.background` glyph, and every page's `title`, `summary`, and `content` entries. Use `<page:page-id/>` for an internal link labeled from the target title, or `<page:page-id>custom label</page>` for different link text.
- `wiki.background` accepts a MiniMessage glyph string or boolean `false`. Omit it or set it to `false` to disable the background for the entire guide. When a global glyph is configured, set `background: false` on an individual page to hide it there.
- Keep shared resource-pack glyphs in `resource-pack/assets/guidelegowelt/font/wiki.json`. Reserve `\uE000` for the global background and use `\uE010` and above for page-specific images.
- Add page-specific image glyphs through a page's `images` list. Each image requires either a MiniMessage `glyph` or a `glyphs` list of MiniMessage rows, plus `width` and `height` values between 1 and 1024; keep the matching PNGs and bitmap providers in the standalone resource pack.
- Keep each bitmap-font provider cell at or below 256 by 256 source pixels. Store larger originals in `resource-pack/source-images/`, which is not packaged.
- Keep the editor image importer aligned with the same 256 by 256 packed-cell, 1024 by 1024 dialog-image, and `U+E010` glyph-allocation contracts.
- Keep `wiki.columns` between 1 and 4 inclusive.
- Define at least one entry under `pages`. Page order in the YAML controls table-of-contents and tab-completion order.
- Page IDs must match `[a-z0-9][a-z0-9_-]{0,63}`. The ID `reload` is reserved for `/wiki reload` and must not be used as a page ID.
- Every page must have a non-empty `title`. `summary` may be blank or omitted and resolves to an empty tooltip. `content` may be an empty list, YAML null, or omitted and resolves to no body text. If present with a value, `summary` must be text and `content` must be a YAML list of strings. Title-only pages without images are valid.
- Use an optional `parent` page ID to create nested navigation. Only pages without a parent appear in the main table of contents; child pages appear as buttons on their parent page.
- Parent IDs must reference an existing page, and page hierarchies must not contain cycles.
- `/wiki reload` reads the runtime `plugins/GuideLegowelt/pages.yml`. If validation fails, the already-loaded catalog remains active.
- `WikiConfigurationLoaderTest.loadsBundledCatalog` verifies that the bundled catalog parses and passes validation without pinning page IDs, hierarchy, or order.
- For bundled image layouts with a stable contract, add a dedicated focused test for the glyph rows, dimensions, and background behavior.
- `ResourcePackImageTest` automatically checks every packed font PNG for readability and the 256 by 256 atlas limit. Run it after adding or replacing any packed image.

## Documentation

- Keep the repository-level `README.md` aligned with build, installation, commands, page configuration, images, tests, and managed test-server workflows.
- Keep `resource-pack/README.md` aligned with page authoring, glyph allocation, tiled images, image tests, and pack publishing.
- Update both README files when changing the page schema, image schema, resource-pack workflow, catalog test contract, or managed test-server tasks.
- Update the repository README and `AGENTS.md` when changing Paper metadata, the bootstrap launcher, its embedded datapack, or its tests.
- Document whether a command edits the bundled source, the live server copy, or generated runtime state. Do not imply that the plugin overwrites an existing production `plugins/GuideLegowelt/pages.yml`.

## Verification

Before finishing a change:

1. Stop the managed test server with `.\gradlew.bat stopTestServer` when it is running.
2. Run `.\gradlew.bat clean build`.
3. Add or update focused tests when behavior can be tested without a live server.
4. For bundled page changes, run the focused `WikiConfigurationLoaderTest.loadsBundledCatalog` smoke test.
5. For resource-pack image changes, run `ResourcePackImageTest`.
6. For launcher, Paper metadata, or embedded datapack changes, run `PauseMenuLauncherResourceTest`.
7. For lifecycle, command, event, resource-pack, or integration changes, use `.\gradlew.bat runTestServer` when the EULA has already been accepted by the user, then stop it with `stopTestServer`.
8. Confirm that Paper loads `GuideLegowelt` without errors and that `paper-plugin.yml`, `pages.yml`, and `wiki-launcher-pack/` are present in the built JAR.
9. For launcher changes, confirm Paper loads `GuideLegowelt/wiki-launcher` automatically and verify the Escape-menu button with a connected client.
10. Run `git diff --check` and leave unrelated user changes untouched.
