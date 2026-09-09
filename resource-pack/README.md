# GuideLegowelt Pages And Images

This directory contains the standalone resource pack used by GuideLegowelt's
wiki dialogs. The plugin reads page definitions from `pages.yml`; Minecraft
loads page images as bitmap-font glyphs from this pack.

This pack is separate from the server datapack embedded at
`src/main/resources/wiki-launcher-pack/`. The embedded datapack adds the Wiki
button to the pause menu and ships inside the plugin JAR. It does not contain
or replace the image resources in this directory.

## Configuration Files

- `src/main/resources/pages.yml` is the default catalog included in the plugin
  JAR.
- `plugins/GuideLegowelt/pages.yml` is the live catalog on a Paper server.
- `assets/guidelegowelt/font/wiki.json` registers image glyphs.
- `assets/guidelegowelt/textures/font/` contains packed glyph textures.
- `source-images/` stores large originals and is excluded from the pack ZIP.

The plugin creates the live `pages.yml` only when it does not exist. It does not
overwrite later server edits. Change the live file and run `/wiki reload` when
updating an existing server. Change `src/main/resources/pages.yml` when the page
should also be part of future plugin builds.

## Use The Wiki Editor

Run the repository editor from the project root:

```powershell
.\gradlew.bat runWikiEditor
```

The loopback UI at `http://127.0.0.1:8130/` edits the bundled pages, the wiki
font definition, packed font textures, and source originals together. Its PNG
importer scales the original to the chosen rendered width, splits it into an
even grid of cells no larger than 256 by 256 pixels, allocates available wiki
glyphs, and attaches the generated layout to the selected page. The original
is retained in `source-images/` and is not included in the pack ZIP.

The editor does not modify a production server's live pages file. Build the
pack after editing and follow the normal publishing workflow below.

## Add A Guide Page

Add an entry under `pages` in `pages.yml`:

```yaml
pages:
  voting:
    title: '<green>Voting</green>'
    summary: '<gray>Learn how voting rewards work.</gray>'
    content:
      - '<white>Vote once per day on every listed server.</white>'
      - '<gold>Rewards:</gold> <white>Keys and server currency.</white>'
```

Each page requires a non-empty `title`, used as the page title and
navigation-button label. `summary` and `content` may be empty or omitted:

- Use `summary: ''` or omit `summary` for no navigation-button tooltip.
- Use `content: []`, `content:` or omit `content` for no body text.

A title-only page without images is valid. When supplied, `summary` must be
text and `content` must be a YAML list. MiniMessage formatting is supported in
titles, summaries, and content. Use `<page:page-id/>` for a link labeled with
the target page title, or `<page:page-id>custom label</page>` for different link
text. Both forms create a blue, bold, underlined `/wiki page-id` link, and the
referenced page must exist in the same catalog. Long entries may use YAML folded
scalars such as `- >-`; they still load as one content string.

Page IDs such as `voting` are also used by `/wiki voting`. IDs must match
`[a-z0-9][a-z0-9_-]{0,63}`. The ID `reload` is reserved. Page order in the YAML
controls navigation and command-completion order.

After changing the live server file, run:

```text
/wiki reload
```

The command requires `guidelegowelt.admin`, which is granted to operators by
default. If validation fails, the old catalog stays active and the reason is
written to the server console.

### Validate The Bundled Catalog

`WikiConfigurationLoaderTest.loadsBundledCatalog` verifies that the bundled
`src/main/resources/pages.yml` parses and passes catalog validation without
pinning individual page IDs, hierarchy, or order.

Run the focused test after changing the bundled catalog:

```powershell
.\gradlew.bat test --tests "net.lesimc.guide_legowelt.wiki.WikiConfigurationLoaderTest.loadsBundledCatalog"
```

## Add A Child Page

Pages without a parent appear in the main table of contents. Add `parent` to
place a page under another page:

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

The parent must exist, and parent relationships cannot form a cycle.

## Configure Backgrounds

Disable the background for the table of contents and every page with:

```yaml
wiki:
  background: false
```

To enable the packed background, use its glyph instead:

```yaml
wiki:
  background: "<font:guidelegowelt:wiki>\uE000</font>"
```

When the global glyph is enabled, a large image can use the full dialog area by
disabling the background only on that page:

```yaml
  large-chart:
    title: '<aqua>Large Chart</aqua>'
    summary: '<gray>View the complete chart.</gray>'
    background: false
    content: []
```

An overall `wiki.background: false` setting takes precedence because there is
no global background for individual pages to display.

## Add A Single Image

### 1. Prepare The Texture

Put the PNG in:

```text
resource-pack/assets/guidelegowelt/textures/font/
```

Each packed bitmap glyph must be no larger than 256 by 256 pixels. Keep the
full-size original under `resource-pack/source-images/`.

### 2. Register The Glyph

Add a provider to `assets/guidelegowelt/font/wiki.json`:

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

The file above maps to
`assets/guidelegowelt/textures/font/my_image.png`. The provider's `height`
controls the rendered image height; `ascent` controls vertical alignment.

Every texture needs a unique private-use character. Keep `\uE000` reserved for
the global background. The bundled page images currently occupy `\uE010`
through `\uE013`, making `\uE014` the next available character.

### 3. Reference It From A Page

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

- `width` is the available dialog-body width. It must be wide enough to prevent
  the glyph from wrapping.
- `height` reserves vertical dialog space for the image.
- Both values must be between 1 and 1024.
- The font provider's `height` controls the actual visual scale.

Images are displayed after the page's text. Multiple `images` entries are
rendered in list order.

## Add A Large Tiled Image

Split images larger than 256 by 256 into multiple textures. Register every tile
with a unique glyph, then assemble the glyphs into rows.

This example creates a two-by-two image from four glyphs:

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

Adjacent characters create a horizontal row. Each `glyphs` list item creates
another vertical row. Every tile in the grid should use the same texture
dimensions, provider height, and ascent.

For exact vertical row spacing, use a provider height divisible by 9 and set
the page image height to `provider height x number of rows`. The Mineral Chart
is a working example: four 256 by 233 textures, a provider height of 207, and
two rows with a declared height of 414.

### Test The Images

`ResourcePackImageTest` reads every packed font PNG and verifies that it is a
valid image no larger than 256 by 256 pixels:

```powershell
.\gradlew.bat test --tests "net.lesimc.guide_legowelt.wiki.ResourcePackImageTest"
```

Add a dedicated focused test when an image's glyph rows, width, height, or
background behavior are part of a stable bundled-guide contract.

Run the complete suite before publishing:

```powershell
.\gradlew.bat clean build
```

## Build And Publish The Pack

Build the standalone ZIP with the Gradle wrapper:

```powershell
.\gradlew.bat buildResourcePack
```

The output is:

```text
build/distributions/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip
```

Host the ZIP at a direct HTTPS URL and configure the Paper server:

```properties
resource-pack=https://example.com/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip
resource-pack-sha1=<lowercase-sha1>
require-resource-pack=true
```

Calculate the SHA-1 after every pack change:

```powershell
(Get-FileHash -Algorithm SHA1 build/distributions/GuideLegowelt-resource-pack-1.0-SNAPSHOT.zip).Hash.ToLowerInvariant()
```

After an image or font-provider change:

1. Rebuild and upload the ZIP.
2. Update `resource-pack-sha1` in `server.properties`.
3. Restart the server when `server.properties` changes.
4. Reconnect the Minecraft client to download the new pack.
5. Run `/wiki reload` when the live `pages.yml` also changed.

## Complete Test-Server Run

Use the all-in-one Gradle task during development:

```powershell
.\gradlew.bat runTestServer
```

It builds and tests the plugin, builds this resource pack, copies the bundled
`pages.yml` into the test server, updates the local pack URL and SHA-1, hosts the
ZIP at `http://127.0.0.1:8123/`, and starts Paper. The pack host stops together
with the Gradle run. The shared IntelliJ configuration **GuideLegowelt Test
Server** runs the same task.

The task overwrites `run/plugins/GuideLegowelt/pages.yml` from
`src/main/resources/pages.yml` on every run. It never edits or accepts
`run/eula.txt`.

Stop the managed Paper server and its pack host gracefully with:

```powershell
.\gradlew.bat stopTestServer
```

## Troubleshooting

- Missing-square glyph: verify that the client has the latest pack, the glyph
  character is correct, and the provider file path matches the texture.
- Old image: rebuild and upload the pack, update its SHA-1, and reconnect.
- Wrapped or clipped image: increase the configured `width` or `height`, or
  reduce the provider's rendered `height`.
- Gap between tiled rows: use identical tile settings and a provider height
  divisible by 9.
- Pack fails to load: confirm every packed glyph texture is at most 256 by 256
  pixels and that `wiki.json` is valid JSON.

See the repository-level `README.md` for build, installation, commands, and
development-server instructions.
