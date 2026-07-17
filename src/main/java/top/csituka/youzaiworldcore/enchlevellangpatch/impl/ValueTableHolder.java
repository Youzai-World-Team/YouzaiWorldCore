package top.csituka.youzaiworldcore.enchlevellangpatch.impl;

import org.jetbrains.annotations.NotNullByDefault;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

@NotNullByDefault
final class ValueTableHolder {
    static final String[] ROMAN = new String[3999];
    static final String[] CHINESE = new String[2 * 256];
    private ValueTableHolder() {}

    static {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(Objects.requireNonNull(
                ValueTableHolder.class.getResourceAsStream("ValueTable.txt"),
                "ValueTable.txt not found. This should not happen."
        ), StandardCharsets.UTF_8))) {
            for (int x = 0; x < 3999; x++) ROMAN[x] = reader.readLine();
            for (int x = 0; x < 2 * 256; x++) CHINESE[x] = reader.readLine();
        } catch (IOException ex) {
            throw new ExceptionInInitializerError(ex);
        }
    }
}
