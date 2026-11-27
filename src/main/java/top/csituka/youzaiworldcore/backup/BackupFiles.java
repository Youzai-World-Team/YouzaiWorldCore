package top.csituka.youzaiworldcore.backup;

import com.google.gson.JsonElement;
import com.google.gson.JsonNull;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import top.csituka.youzaiworldcore.util.BackupArchive;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/** Read and replace the specific files registered by the player data modules. */
final class BackupFiles {
    private BackupFiles() {
    }

    static List<Path> files(Path directory, int depth, Predicate<String> includes) throws IOException {
        if (!Files.exists(directory, LinkOption.NOFOLLOW_LINKS)) {
            return List.of();
        }
        try (var paths = Files.walk(directory, depth)) {
            return paths.filter(path -> Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS))
                    .filter(path -> includes.test(name(directory, path))).sorted().toList();
        }
    }

    private static String name(Path directory, Path file) {
        return directory.relativize(file).toString().replace(file.getFileSystem().getSeparator(), "/");
    }

    static JsonObject readFiles(Path directory, int depth, Predicate<String> includes) throws IOException {
        JsonObject result = new JsonObject();
        for (Path file : files(directory, depth, includes)) {
            result.add(name(directory, file), readFile(file));
        }
        return result;
    }

    static JsonElement readFile(Path path) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                ? new JsonPrimitive(Base64.getEncoder().encodeToString(Files.readAllBytes(path)))
                : JsonNull.INSTANCE;
    }

    static JsonObject readObject(Path path) throws IOException {
        return Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)
                ? object(JsonParser.parseString(Files.readString(path))) : new JsonObject();
    }

    static JsonObject object(JsonElement value) {
        if (value instanceof JsonObject object) {
            return object;
        }
        throw new IllegalArgumentException("Expected a JSON object");
    }

    static String string(JsonElement value) {
        if (value instanceof JsonPrimitive primitive && primitive.isString()) {
            return primitive.getAsString();
        }
        throw new IllegalArgumentException("Expected a JSON string");
    }

    static void writeArchive(Path output, JsonObject value) throws IOException {
        String name = output.getFileName().toString();
        atomicWrite(output, temporary ->
                BackupArchive.writeJson(temporary, name.substring(0, name.length() - 4) + ".json", value.toString()));
    }

    static void atomicWrite(Path target, Writer writer) throws IOException {
        Path directory = target.getParent();
        Files.createDirectories(directory);
        Path temporary = Files.createTempFile(directory, ".backup-", ".tmp");
        try {
            writer.write(temporary);
            try {
                Files.move(temporary, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temporary, target, StandardCopyOption.REPLACE_EXISTING);
            }
        } finally {
            Files.deleteIfExists(temporary);
        }
    }

    @FunctionalInterface
    interface Writer {
        void write(Path file) throws IOException;
    }

    /** Validate all changes before applying them. A queued restore can safely repeat these replacements. */
    static final class Restore {
        private final Map<Path, byte[]> replacements = new LinkedHashMap<>();

        void files(Path directory, int depth, Predicate<String> includes, JsonObject contents) throws IOException {
            for (Path file : BackupFiles.files(directory, depth, includes)) {
                replacements.put(file, null);
            }
            for (var entry : contents.entrySet()) {
                if (!includes.test(entry.getKey())) {
                    throw new IllegalArgumentException("Unexpected backup file: " + entry.getKey());
                }
                Path path = directory.resolve(entry.getKey());
                for (Path parent = path.getParent(); !parent.equals(directory); parent = parent.getParent()) {
                    if (Files.isSymbolicLink(parent)) {
                        throw new IOException("Unexpected symbolic link: " + parent);
                    }
                }
                file(path, entry.getValue());
            }
        }

        void file(Path path, JsonElement value) throws IOException {
            if (value == null) {
                throw new IllegalArgumentException("Missing backup file: " + path.getFileName());
            }
            byte[] bytes = value.isJsonNull() ? null : Base64.getDecoder().decode(string(value));
            String name = path.getFileName().toString();
            if (bytes != null && name.endsWith(".json")) {
                BackupFiles.object(JsonParser.parseString(new String(bytes, StandardCharsets.UTF_8)));
            } else if (bytes != null && (name.endsWith(".dat") || name.endsWith(".dat_old"))) {
                NbtIo.readCompressed(new ByteArrayInputStream(bytes), NbtAccounter.unlimitedHeap());
            }
            replacements.put(path, bytes);
        }

        JsonObject readObject(Path path) throws IOException {
            if (!replacements.containsKey(path)) {
                return BackupFiles.readObject(path);
            }
            byte[] value = replacements.get(path);
            return value == null ? new JsonObject()
                    : BackupFiles.object(JsonParser.parseString(new String(value, StandardCharsets.UTF_8)));
        }

        void object(Path path, JsonObject value) {
            replacements.put(path, value.toString().getBytes(StandardCharsets.UTF_8));
        }

        void apply() throws IOException {
            for (var entry : replacements.entrySet()) {
                if (entry.getValue() != null) {
                    atomicWrite(entry.getKey(), file -> Files.write(file, entry.getValue()));
                }
            }
            for (var entry : replacements.entrySet()) {
                if (entry.getValue() == null) {
                    Files.deleteIfExists(entry.getKey());
                }
            }
        }
    }
}
