# GuideLegowelt

GuideLegowelt is a Paper 26.2 plugin that presents an in-game server guide
through Minecraft dialogs. Players open the guide with `/wiki` or `/guide`.
The pause menu opens the Wiki table of contents directly. Pages, navigation,
formatted text, and image placement are configured in `pages.yml`.

Images are supplied by a standalone resource pack. The plugin JAR does not
embed, host, or force that pack by itself. The small server datapack embedded
in the plugin JAR only adds the pause-menu entry; it is not the image
resource pack.

## Requirements

- Paper 26.2
- Java 25
- A Minecraft client that accepts the server resource pack when images are used

## Build

Use the included Gradle wrapper:

```powershell
.\gradlew.bat clean build
```

On Linux or macOS:

```bash
./gradlew clean build
```

The build produces:

- Plugin: `build/libs/guide_legowelt-1.0-SNAPSHOT.jar`
- Resource pack: `build/distributions/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip`

To build only the resource pack, run:

```powershell
.\gradlew.bat buildResourcePack
```

## Install

1. Put the plugin JAR in the Paper server's `plugins/` directory.
2. Start the server once. The plugin creates
   `plugins/GuideLegowelt/pages.yml` if it does not already exist.
3. Host the resource-pack ZIP at a direct download URL.
4. Configure that URL and the ZIP's SHA-1 in `server.properties`.

```properties
resource-pack=https://example.com/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip
resource-pack-sha1=<lowercase-sha1>
require-resource-pack=true
```

Calculate the SHA-1 again whenever the pack changes:

```powershell
(Get-FileHash -Algorithm SHA1 build/distributions/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip).Hash.ToLowerInvariant()
```

Players must reconnect after a pack URL or hash change so their clients load
the updated images.

## Commands

| Command | Description | Permission |
| --- | --- | --- |
| `/wiki` | Open the table of contents | `guidelegowelt.wiki` |
| `/wiki <page-id>` | Open a page directly | `guidelegowelt.wiki` |
| `/wiki reload` | Reload the live `pages.yml` | `guidelegowelt.admin` |
| `/guide` | Alias for `/wiki` | `guidelegowelt.wiki` |

The wiki permission is granted by default. The reload permission defaults to
server operators.

## Pause-Menu Wiki

The plugin registers the wiki table of contents during Paper's bootstrap phase
and adds it to `#minecraft:pause_screen_additions` through an embedded server
datapack. Selecting **Wiki** in the pause menu opens the table of contents
directly; there is no intermediate **Open Wiki** button.

On startup, the registered table of contents is built from the live
`plugins/GuideLegowelt/pages.yml`, or from the bundled catalog on a first
installation. Its topic buttons run `/wiki <page-id>`, so the selected page is
always opened from the currently loaded catalog.

- Edit `plugins/GuideLegowelt/pages.yml` and run `/wiki reload` for live page
  content changes. The `/wiki` command immediately uses the reloaded catalog.
- Restart Paper after adding, removing, reordering, renaming, or reparenting a
  root page, or after changing table-of-contents settings. Registry-backed
  pause-menu layout cannot be rebuilt by `/wiki reload`.
- Rebuild the plugin JAR and restart Paper after changing
  `GuideLegoweltBootstrap`, `paper-plugin.yml`, or `wiki-launcher-pack/`.
- Do not use Bukkit `/reload` or plugin hot-reload tools. Registry bootstrap
  changes require a normal server restart.
- The pause-menu tag uses `"replace": false`, so other datapacks and plugins
  can add their own pause-menu dialogs.

## Which pages.yml To Edit

There are two copies with different purposes:

- `src/main/resources/pages.yml` is the bundled default included in new plugin
  builds.
- `plugins/GuideLegowelt/pages.yml` is the live server configuration.

The plugin does not overwrite an existing live file. Edit the live file and run
`/wiki reload` to change a running server. Also update the bundled file when the
change should be included in future installations and plugin builds.

If a reload fails validation, the server keeps the previously loaded pages and
writes the reason to the console.

## Wiki Editor

Start the repository editor with:

```powershell
.\gradlew.bat runWikiEditor
```

Open `http://127.0.0.1:8130/` while the task is running. The editor provides
ordered page and hierarchy controls, MiniMessage formatting and previews,
table-of-contents settings, catalog validation, undo and redo, direct YAML
access, and a dialog preview. Valid changes are saved automatically; the Save
button writes immediately. Use the power button or Ctrl+C to stop the task.

The editor changes the bundled `src/main/resources/pages.yml`, not a running
server's `plugins/GuideLegowelt/pages.yml`. It also maintains the standalone
pack's `wiki.json`, packed font textures, and original source images when a PNG
is imported. Review the changes and rebuild the plugin and resource pack before
deployment.

Imported PNGs preserve their aspect ratio at the selected rendered width. The
editor divides larger images into an even glyph grid whose source cells are at
most 256 by 256 pixels, allocates unused glyphs from `U+E010`, updates
`wiki.json`, and adds the resulting image layout to the selected page. The
rendered page image must stay within the wiki's 1024 by 1024 limits.

To use another loopback port:

```powershell
.\gradlew.bat runWikiEditor -Pguidelegowelt.wikiEditorPort=8131
```

## Wiki Settings

The top-level `wiki` section controls the table of contents:

```yaml
wiki:
  title: '<gold><bold>Legowelt Wiki</bold></gold>'
  introduction: '<gray>Select a topic from the table of contents.</gray>'
  background: false
  columns: 2
```

- `title` is the dialog title.
- `introduction` is displayed above the page buttons.
- `background: false` disables the background for the entire guide. Omit the
  setting for the same result.
- To enable the resource-pack background, set `background` to
  `"<font:guidelegowelt:wiki>\uE000</font>"`.
- `columns` must be between 1 and 4.

Titles, summaries, content, backgrounds, and image glyphs support MiniMessage
formatting. Use `<page:page-id/>` for an internal link labeled with the target
page title. Use `<page:page-id>custom label</page>` when the link needs different
text. Both forms create a blue, bold, underlined `/wiki page-id` link, and the
referenced page must exist in the same catalog.

## Add A Page

Add a new entry under `pages`. A page ID must begin with a lowercase letter or
number and may contain lowercase letters, numbers, underscores, and hyphens.
The ID `reload` is reserved.

```yaml
pages:
  voting:
    title: '<green>Voting</green>'
    summary: '<gray>Learn how voting rewards work.</gray>'
    content:
      - '<white>Vote once per day on each listed server.</white>'
      - '<gold>Rewards:</gold> <white>Keys and server currency.</white>'
      - 'Return to <page:getting-started/>.'
```

Every page requires a non-empty `title`, which is shown at the top of the page
and on its navigation button. The other display fields are optional:

- `summary`: tooltip text for the navigation button. Use `summary: ''` or omit
  it to show no tooltip.
- `content`: a YAML list of body lines. Use `content: []`, `content:` or omit it
  to show no body text.

A page may contain no summary, content, or images. Such a page displays its
title, any configured background, child-page buttons, and its navigation
button.

```yaml
  title-only:
    title: '<gold>Title Only</gold>'
    summary: ''
    content: []
```

When present, `summary` must be text and `content` must be a YAML list. A scalar
such as `content: 'text'` is rejected; use `content: ['text']` instead.
Long list entries may use YAML folded scalars such as `- >-`; they still load as
one content string.

Page order in `pages.yml` controls table-of-contents, child-button, and command
completion order. Pages without a parent appear in the main table of contents.

After editing the live file, apply the change with:

```text
/wiki reload
```

### Validate The Bundled Catalog

`WikiConfigurationLoaderTest.loadsBundledCatalog` verifies that the bundled
`src/main/resources/pages.yml` parses and passes catalog validation. It does
not pin individual page IDs, hierarchy, or order.

Run the focused catalog test after editing pages:

```powershell
.\gradlew.bat test --tests "net.lesimc.guide_legowelt.wiki.WikiConfigurationLoaderTest.loadsBundledCatalog"
```

## Add A Child Page

Use `parent` to group related pages. The child is shown as a button on its
parent instead of in the main table of contents.

```yaml
pages:
  professions:
    title: '<aqua>Professions</aqua>'
    summary: '<gray>Browse all professions.</gray>'
    content:
      - '<white>Select a profession below.</white>'

  profession-miner:
    parent: professions
    title: '<aqua>Miner</aqua>'
    summary: '<gray>Learn about the Miner profession.</gray>'
    content:
      - '<white>Mine ores to earn profession experience.</white>'
```

The parent ID must exist. Parent relationships cannot form a cycle.

## Page Backgrounds

When `wiki.background` contains a glyph, pages display it by default. Hide it
on one page when a large page image needs the available space:

```yaml
  large-chart:
    title: '<aqua>Large Chart</aqua>'
    summary: '<gray>View the complete chart.</gray>'
    background: false
    content: []
```

This setting affects only that page. It does not remove the background from the
table of contents or other pages. Setting `wiki.background: false` disables the
background everywhere, regardless of page settings.

## Add A Single Image

Minecraft wiki images are bitmap-font glyphs. Adding one requires a PNG, a font
provider, and a page image entry.

### 1. Add The Texture

Put the packed texture here:

```text
resource-pack/assets/guidelegowelt/textures/font/my_image.png
```

Each bitmap glyph texture must be no larger than 256 by 256 pixels. Keep a
large original under `resource-pack/source-images/`; that directory is excluded
from the built ZIP.

### 2. Register A Glyph

Add a bitmap provider to
`resource-pack/assets/guidelegowelt/font/wiki.json`:

```json
{
  "type": "bitmap",
  "file": "guidelegowelt:font/my_image.png",
  "ascent": 7,
  "height": 176,
  "chars": [
    "\uE014"
  ]
}
```

- `file` maps to `assets/guidelegowelt/textures/font/my_image.png`.
- `height` controls the glyph's rendered height in GUI pixels.
- `ascent` controls its vertical alignment.
- `chars` assigns an unused private-use Unicode character to the image.

Keep `\uE000` reserved for the shared background. Page images currently use
`\uE010` through `\uE013`, so the next available glyph is `\uE014`. Never reuse
one character for two different images in the same font.

### 3. Add The Image To A Page

Reference the glyph from `pages.yml`:

```yaml
  example-image:
    title: '<yellow>Example Image</yellow>'
    summary: '<gray>View an example image.</gray>'
    content:
      - '<white>This text is displayed before the image.</white>'
    images:
      - glyph: "<font:guidelegowelt:wiki>\uE014</font>"
        width: 200
        height: 176
```

The image `width` is the dialog body's available width and must be wide enough
to prevent the glyph from wrapping. The image `height` reserves vertical dialog
space. Both values must be between 1 and 1024. The font provider's `height` is
what controls the visual glyph scale.

Multiple entries may be added to `images`; they are rendered in list order
after the page's text content.

## Add A Large Tiled Image

Images larger than 256 by 256 must be split into glyph-sized tiles. Register
each tile with its own character, then assemble adjacent tiles with `glyphs`.

For a two-by-two image, register four providers such as `\uE014` through
`\uE017`, then configure two glyph rows:

```yaml
  large-chart:
    title: '<aqua>Large Chart</aqua>'
    summary: '<gray>View the complete chart.</gray>'
    background: false
    content: []
    images:
      - glyphs:
          - "<font:guidelegowelt:wiki>\uE014\uE015</font>"
          - "<font:guidelegowelt:wiki>\uE016\uE017</font>"
        width: 464
        height: 414
```

Characters written next to each other form one horizontal row. Each item in
`glyphs` creates another vertical row. Use the same source dimensions, provider
height, and ascent for every tile in a grid.

For seamless vertical alignment, use a provider height divisible by 9 and set
the page image height to:

```text
provider height x number of rows
```

The bundled Mineral Chart is a working example: four 256 by 233 textures render
with a provider height of 207, producing two rows with a declared height of 414.

### Test Page Images

`ResourcePackImageTest` automatically opens every PNG under
`resource-pack/assets/guidelegowelt/textures/font/` and rejects unreadable files
or textures larger than 256 by 256 pixels. Run it after adding or replacing any
packed image:

```powershell
.\gradlew.bat test --tests "net.lesimc.guide_legowelt.wiki.ResourcePackImageTest"
```

Add a dedicated focused test when a particular bundled image layout needs a
stable glyph-row, dimension, or background contract.

Before deployment, run the complete verification suite:

```powershell
.\gradlew.bat clean build
```

## Test The Pause-Menu Wiki

`PauseMenuLauncherResourceTest` verifies that `paper-plugin.yml` names the
bootstrapper, the command permissions are present, the embedded datapack uses
Minecraft 26.2's data-pack format, and the pause-menu tag points at the direct
contents dialog registered by the bootstrap class:

```powershell
.\gradlew.bat test --tests "net.lesimc.guide_legowelt.PauseMenuLauncherResourceTest"
```

For the integration check, run `runTestServer` and confirm that the log lists
`GuideLegowelt` under Paper plugins and reports that
`GuideLegowelt/wiki-launcher` was loaded automatically. Join the server, press
Escape, select **Wiki**, and verify that the table of contents opens directly.
Open a page, run `/wiki reload` after changing its live content, and confirm
that the pause-menu topic button opens the updated page.

## Publish Image Changes

Changing `pages.yml` alone is enough only when all referenced glyphs already
exist in the currently installed pack. After adding or changing a PNG or font
provider:

1. Rebuild the resource pack with `buildResourcePack`.
2. Upload the new ZIP to its direct download URL.
3. Calculate and update `resource-pack-sha1` in `server.properties`.
4. Restart the server if `server.properties` changed.
5. Reconnect the Minecraft client to download the new pack.
6. Run `/wiki reload` if the live `pages.yml` also changed.

## Troubleshooting Images

- Missing-square glyph: the client does not have the updated pack, the font
  provider path is wrong, or the page uses the wrong character.
- Old image: rebuild and re-upload the ZIP, then change its SHA-1 and reconnect.
- Wrapped or clipped image: increase the page image `width` or `height`, or
  reduce the font provider's rendered `height`.
- Visible gap in a tiled image: make every tile use identical provider settings
  and use a provider height divisible by 9 for multi-row images.
- Atlas loading failure: verify every packed glyph texture is at most 256 by
  256 pixels.

## Development Server

Use the complete test-server workflow when developing:

```powershell
.\gradlew.bat runTestServer
```

The task performs the complete update in order:

1. Builds and tests the plugin.
2. Builds the standalone resource-pack ZIP.
3. Copies `src/main/resources/pages.yml` to the test server.
4. Updates the local resource-pack URL and SHA-1 in `run/server.properties`.
5. Hosts the pack at `http://127.0.0.1:8123/` inside the Gradle process.
6. Starts the Paper 26.2 test server.

The shared IntelliJ run configuration **GuideLegowelt Test Server** invokes the
same task. Stop that Gradle run to stop both Paper and the pack host. Port 8123
must be available. The address and port can be overridden when needed:

```powershell
.\gradlew.bat runTestServer -Pguidelegowelt.testPackAddress=127.0.0.1 -Pguidelegowelt.testPackPort=8124
```

Stop Paper gracefully from another terminal or with the shared IntelliJ
configuration **Stop GuideLegowelt Test Server**:

```powershell
.\gradlew.bat stopTestServer
```

The start workflow enables RCON on loopback solely for this stop command. When
customizing the server or RCON ports, pass the same Gradle properties to both
tasks: `guidelegowelt.testServerPort`, `guidelegowelt.testRconPort`, and
`guidelegowelt.testRconPassword`.

`runTestServer` intentionally replaces the test server's live `pages.yml` with
the bundled source file each time. It does not edit or accept `run/eula.txt`.
Review Mojang's EULA and set `eula=true` yourself before the server can run.

Do not distribute the generated `run/`, `build/`, or `.gradle/` directories.
