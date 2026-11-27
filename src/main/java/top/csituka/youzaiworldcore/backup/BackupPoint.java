package top.csituka.youzaiworldcore.backup;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.time.temporal.ChronoUnit;
import java.util.Locale;

record BackupPoint(String id, LocalDateTime time) {
    static final LocalTime DAILY_TIME = LocalTime.of(4, 0);
    private static final String DAILY_PREFIX = "daily-";
    private static final String SHUTDOWN_PREFIX = "shutdown-";
    private static final String CRASH_PREFIX = "crash-";
    private static final DateTimeFormatter TIMESTAMP = DateTimeFormatter
            .ofPattern("uuuu-MM-dd_HH-mm-ss_SSS", Locale.ROOT).withResolverStyle(ResolverStyle.STRICT);

    static BackupPoint daily(LocalDate date) {
        return new BackupPoint(date.toString(), date.atTime(DAILY_TIME));
    }

    static BackupPoint exit(boolean crashed, LocalDateTime now) {
        return new BackupPoint((crashed ? CRASH_PREFIX : SHUTDOWN_PREFIX) + TIMESTAMP.format(now),
                now.truncatedTo(ChronoUnit.MILLIS));
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
        return new BackupPoint(name, time);
    }

    LocalDate date() {
        return time.toLocalDate();
    }

    boolean daily() {
        return id.equals(date().toString());
    }

    String fileStem() {
        return daily() ? DAILY_PREFIX + id : id;
    }
}
