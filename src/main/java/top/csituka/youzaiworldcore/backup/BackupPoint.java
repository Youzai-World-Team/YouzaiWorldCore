package top.csituka.youzaiworldcore.backup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Locale;

record BackupPoint(String id, LocalDate date) {
    private static final String DAILY_PREFIX = "daily-";
    private static final String SHUTDOWN_PREFIX = "shutdown-";
    private static final String CRASH_PREFIX = "crash-";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH-mm-ss_SSS", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    static BackupPoint daily(LocalDate date) {
        return new BackupPoint(date.toString(), date);
    }

    static BackupPoint exit(boolean crashed, LocalDateTime now) {
        return new BackupPoint((crashed ? CRASH_PREFIX : SHUTDOWN_PREFIX) + TIMESTAMP.format(now),
                now.toLocalDate());
    }

    static BackupPoint parseFileStem(String name) {
        if (name.startsWith(DAILY_PREFIX)) {
            return daily(LocalDate.parse(name.substring(DAILY_PREFIX.length())));
        }
        String prefix = name.startsWith(CRASH_PREFIX) ? CRASH_PREFIX : SHUTDOWN_PREFIX;
        if (!name.startsWith(prefix)) {
            throw new IllegalArgumentException("Unknown backup archive: " + name);
        }
        LocalDateTime time = LocalDateTime.parse(name.substring(prefix.length()), TIMESTAMP);
        return new BackupPoint(name, time.toLocalDate());
    }

    boolean daily() {
        return id.equals(date.toString());
    }

    String fileStem() {
        return daily() ? DAILY_PREFIX + id : id;
    }
}
