package net.lesimc.guide_legowelt.wiki;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;

class ResourcePackImageTest {

    private static final int MAX_GLYPH_TEXTURE_SIZE = 256;

    @Test
    void fontTexturesFitTheMinecraftGlyphAtlas() throws Exception {
        Path textures = Path.of("resource-pack", "assets", "guidelegowelt", "textures", "font");
        List<Path> textureFiles;
        try (var paths = Files.list(textures)) {
            textureFiles = paths.filter(path -> path.getFileName().toString().endsWith(".png")).toList();
        }

        for (Path textureFile : textureFiles) {
            BufferedImage image = ImageIO.read(textureFile.toFile());
            assertNotNull(image, () -> "Could not read " + textureFile);
            assertTrue(
                image.getWidth() <= MAX_GLYPH_TEXTURE_SIZE && image.getHeight() <= MAX_GLYPH_TEXTURE_SIZE,
                () -> textureFile + " is " + image.getWidth() + "x" + image.getHeight()
                    + "; font glyph textures must not exceed 256x256"
            );
        }
    }
}
