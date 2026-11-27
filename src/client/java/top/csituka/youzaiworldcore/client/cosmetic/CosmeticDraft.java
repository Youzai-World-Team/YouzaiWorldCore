package top.csituka.youzaiworldcore.client.cosmetic;

import top.csituka.youzaiworldcore.cosmetic.CosmeticPngValidator;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Stages an explicit replacement of the supplied local slots. */
public record CosmeticDraft(byte[] skinWide, byte[] skinSlim, byte[] cloak,
                            boolean hasLocalFiles, boolean widePreferred) {
    private static final byte[] EMPTY = new byte[0];

    public static CosmeticDraft read(Path directory, byte[] savedWide, byte[] savedSlim, byte[] savedCloak)
            throws IOException {
        Path wideFile = directory.resolve("skin.png");
        Path slimFile = directory.resolve("skin_slim.png");
        if (Files.notExists(slimFile)) {
            slimFile = directory.resolve("skin_silm.png");
        }
        boolean useWide = !Files.notExists(wideFile);
        boolean useSlim = !Files.notExists(slimFile);
        Path cloakFile = directory.resolve("cloak.png");
        boolean useCloak = !Files.notExists(cloakFile);

        byte[] wide = useWide ? readImage(wideFile) : useSlim ? EMPTY : savedWide;
        byte[] slim = useWide ? EMPTY : useSlim ? readImage(slimFile) : savedSlim;
        byte[] cloak = useCloak ? readImage(cloakFile) : savedCloak;
        return new CosmeticDraft(wide, slim, cloak, useWide || useSlim || useCloak, useWide && useSlim);
    }

    private static byte[] readImage(Path file) throws IOException {
        try (var input = Files.newInputStream(file)) {
            byte[] data = input.readNBytes(CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES + 1);
            if (data.length == 0 || data.length > CosmeticPngValidator.ABSOLUTE_MAX_FILE_BYTES) {
                throw new IOException("Invalid cosmetic file size: " + file.getFileName());
            }
            return data;
        }
    }
}
