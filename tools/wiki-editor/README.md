# GuideLegowelt Wiki Editor

This directory contains the browser UI served by the Gradle `runWikiEditor`
task. The task binds to loopback only and exposes the repository files needed
by the editor through a small local API.

Start it from the repository root:

```powershell
.\gradlew.bat runWikiEditor
```

Then open `http://127.0.0.1:8130/`. Use the power button in the editor or press
Ctrl+C in the Gradle terminal to stop it.

The editor writes these source files directly:

- `src/main/resources/pages.yml`
- `resource-pack/assets/guidelegowelt/font/wiki.json`
- `resource-pack/assets/guidelegowelt/textures/font/*.png`
- `resource-pack/source-images/*.png`

It does not edit a server's live `plugins/GuideLegowelt/pages.yml`. Build and
deploy the plugin and resource pack after reviewing an editor change.

Undo and redo cover catalog and font-definition edits. Importing or deleting a
PNG creates a new history checkpoint because those operations write physical
resource-pack files immediately.

The UI is intentionally dependency-free at runtime. Pinned browser builds of
`js-yaml` and `lucide` are committed under `vendor/` with their licenses so the
editor also works offline.
