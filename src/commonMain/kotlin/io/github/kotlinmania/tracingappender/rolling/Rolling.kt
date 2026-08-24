// port-lint: source rolling.rs
package io.github.kotlinmania.tracingappender.rolling

import io.github.kotlinmania.tracingappender.Writer
import kotlinx.datetime.DayOfWeek
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.TimeZone
import kotlinx.datetime.isoDayNumber
import kotlinx.datetime.number
import kotlinx.datetime.toInstant
import kotlinx.datetime.toLocalDateTime
import kotlin.concurrent.Volatile
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Instant

/**
 * Frequency of log file rotation.
 */
public enum class RotationKind {
    Minutely,
    Hourly,
    Daily,
    Weekly,
    Never,
}

/**
 * Defines a fixed period for rolling of a log file.
 */
public data class Rotation(
    public val kind: RotationKind,
) {
    /**
     * Determines the next date that we should round to, or null if [Rotation.NEVER].
     */
    public fun nextDate(currentDate: Instant): Instant? =
        when (kind) {
            RotationKind.Minutely -> roundDate(currentDate + 1.minutes)
            RotationKind.Hourly -> roundDate(currentDate + 1.hours)
            RotationKind.Daily -> roundDate(currentDate + 1.days)
            RotationKind.Weekly -> roundDate(currentDate + 7.days)
            RotationKind.Never -> null
        }

    /**
     * Rounds the date towards the past using the [Rotation] interval.
     */
    public fun roundDate(date: Instant): Instant {
        val ldt = date.toLocalDateTime(TimeZone.UTC)
        return when (kind) {
            RotationKind.Minutely -> {
                LocalDateTime(ldt.year, ldt.month, ldt.day, ldt.hour, ldt.minute, 0, 0)
                    .toInstant(TimeZone.UTC)
            }
            RotationKind.Hourly -> {
                LocalDateTime(ldt.year, ldt.month, ldt.day, ldt.hour, 0, 0, 0)
                    .toInstant(TimeZone.UTC)
            }
            RotationKind.Daily -> {
                LocalDateTime(ldt.year, ldt.month, ldt.day, 0, 0, 0, 0)
                    .toInstant(TimeZone.UTC)
            }
            RotationKind.Weekly -> {
                val daysSinceSunday = if (ldt.dayOfWeek == DayOfWeek.SUNDAY) 0 else ldt.dayOfWeek.isoDayNumber
                val sundayDate = date - daysSinceSunday.days
                val sundayLdt = sundayDate.toLocalDateTime(TimeZone.UTC)
                LocalDateTime(sundayLdt.year, sundayLdt.month, sundayLdt.day, 0, 0, 0, 0)
                    .toInstant(TimeZone.UTC)
            }
            RotationKind.Never -> {
                error("Rotation::NEVER is impossible to round.")
            }
        }
    }

    public companion object {
        /**
         * Provides a minutely rotation.
         */
        public val MINUTELY: Rotation = Rotation(RotationKind.Minutely)

        /**
         * Provides an hourly rotation.
         */
        public val HOURLY: Rotation = Rotation(RotationKind.Hourly)

        /**
         * Provides a daily rotation.
         */
        public val DAILY: Rotation = Rotation(RotationKind.Daily)

        /**
         * Provides a weekly rotation that rotates every Sunday at midnight UTC.
         */
        public val WEEKLY: Rotation = Rotation(RotationKind.Weekly)

        /**
         * Provides a rotation that never rotates.
         */
        public val NEVER: Rotation = Rotation(RotationKind.Never)
    }
}

private fun pad2(value: Int): String = if (value < 10) "0$value" else value.toString()

private fun formatDate(instant: Instant, rotation: Rotation): String {
    val ldt = instant.toLocalDateTime(TimeZone.UTC)
    val year = ldt.year.toString()
    val month = pad2(ldt.month.number)
    val day = pad2(ldt.day)
    val hour = pad2(ldt.hour)
    val minute = pad2(ldt.minute)

    return when (rotation.kind) {
        RotationKind.Minutely -> "$year-$month-$day-$hour-$minute"
        RotationKind.Hourly -> "$year-$month-$day-$hour"
        RotationKind.Daily, RotationKind.Weekly, RotationKind.Never -> "$year-$month-$day"
    }
}

/**
 * Inner state for [RollingFileAppender].
 */
public class Inner(
    public val now: Instant,
    public val rotation: Rotation,
    public val logDirectory: String,
    public val logFilenamePrefix: String?,
    public val logFilenameSuffix: String?,
    public val maxFiles: Int?,
) {
    @Volatile
    public var nextDate: Long = rotation.nextDate(now)?.epochSeconds ?: 0L

    /**
     * Returns the full filename for the provided date, formatted according to rotation.
     */
    public fun joinDate(date: Instant): String {
        val formattedDate =
            if (rotation.kind == RotationKind.Never) {
                formatDate(date, rotation)
            } else {
                formatDate(rotation.roundDate(date), rotation)
            }

        val prefix = logFilenamePrefix
        val suffix = logFilenameSuffix

        return when {
            rotation.kind == RotationKind.Never && prefix != null && suffix != null -> "$prefix.$suffix"
            rotation.kind == RotationKind.Never && prefix != null && suffix == null -> prefix
            rotation.kind == RotationKind.Never && prefix == null && suffix != null -> suffix
            rotation.kind == RotationKind.Never && prefix == null && suffix == null -> formattedDate
            prefix != null && suffix != null -> "$prefix.$formattedDate.$suffix"
            prefix != null && suffix == null -> "$prefix.$formattedDate"
            prefix == null && suffix != null -> "$formattedDate.$suffix"
            else -> formattedDate
        }
    }

    /**
     * Checks whether or not it's time to roll over the log file.
     */
    public fun shouldRollover(date: Instant): Long? {
        val next = nextDate
        if (next == 0L) return null
        if (date.epochSeconds >= next) {
            return next
        }
        return null
    }

    /**
     * Advances the rollover date.
     */
    public fun advanceDate(now: Instant, current: Long): Boolean {
        val next = rotation.nextDate(now)?.epochSeconds ?: 0L
        if (nextDate == current) {
            nextDate = next
            return true
        }
        return false
    }

    public companion object {
        public fun new(
            now: Instant,
            rotation: Rotation,
            directory: String,
            prefix: String?,
            suffix: String?,
            maxFiles: Int?,
        ): Inner =
            Inner(
                now = now,
                rotation = rotation,
                logDirectory = directory,
                logFilenamePrefix = prefix,
                logFilenameSuffix = suffix,
                maxFiles = maxFiles,
            )
    }
}

/**
 * A file appender with the ability to rotate log files at a fixed schedule.
 */
public class RollingFileAppender(
    public val state: Inner,
    private val writerFactory: (String) -> Writer = { DefaultAppenderWriter() },
    private val nowProvider: () -> Instant = {
        Clock.System.now()
    },
) : Writer {
    @Volatile
    private var currentFilename: String = state.joinDate(nowProvider())

    @Volatile
    private var currentWriter: Writer = writerFactory(currentFilename)

    public fun currentFilename(): String = currentFilename

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        val now = nowProvider()
        val rolloverTarget = state.shouldRollover(now)
        if (rolloverTarget != null && state.advanceDate(now, rolloverTarget)) {
            currentWriter.flush()
            currentFilename = state.joinDate(now)
            currentWriter = writerFactory(currentFilename)
        }
        return currentWriter.write(buf, offset, length)
    }

    override fun flush() {
        currentWriter.flush()
    }

    /**
     * Returns a [RollingWriter] that writes to this appender.
     */
    public fun makeWriter(): RollingWriter = RollingWriter(this)

    public companion object {
        /**
         * Creates a new [RollingFileAppender].
         */
        public fun new(
            rotation: Rotation,
            directory: String,
            filePrefix: String,
        ): RollingFileAppender =
            builder()
                .rotation(rotation)
                .filenamePrefix(filePrefix)
                .build(directory)

        /**
         * Returns a new [Builder] for configuring a [RollingFileAppender].
         */
        public fun builder(): Builder = Builder.new()

        /**
         * Constructs a [RollingFileAppender] from a [Builder].
         */
        public fun fromBuilder(builder: Builder, directory: String): RollingFileAppender {
            val now = Clock.System.now()
            val inner =
                Inner.new(
                    now = now,
                    rotation = builder.rotation,
                    directory = directory,
                    prefix = builder.prefix,
                    suffix = builder.suffix,
                    maxFiles = builder.maxFiles,
                )
            return RollingFileAppender(inner)
        }
    }
}

/**
 * A writer that delegates writes to a [RollingFileAppender].
 */
public class RollingWriter(
    private val appender: RollingFileAppender,
) : Writer {
    override fun write(buf: ByteArray, offset: Int, length: Int): Int = appender.write(buf, offset, length)

    override fun flush(): Unit = appender.flush()
}

/**
 * Default in-memory byte buffer writer.
 */
public class DefaultAppenderWriter : Writer {
    private val chunks = mutableListOf<ByteArray>()

    override fun write(buf: ByteArray, offset: Int, length: Int): Int {
        chunks.add(buf.copyOfRange(offset, offset + length))
        return length
    }

    override fun flush() {}

    public fun data(): ByteArray {
        val total = chunks.sumOf { it.size }
        val result = ByteArray(total)
        var pos = 0
        for (chunk in chunks) {
            chunk.copyInto(result, pos)
            pos += chunk.size
        }
        return result
    }

    public fun asString(): String = data().decodeToString()
}

/**
 * Creates a minutely-rotating file appender.
 */
public fun minutely(directory: String, fileNamePrefix: String): RollingFileAppender = RollingFileAppender.new(Rotation.MINUTELY, directory, fileNamePrefix)

/**
 * Creates an hourly-rotating file appender.
 */
public fun hourly(directory: String, fileNamePrefix: String): RollingFileAppender = RollingFileAppender.new(Rotation.HOURLY, directory, fileNamePrefix)

/**
 * Creates a daily-rotating file appender.
 */
public fun daily(directory: String, fileNamePrefix: String): RollingFileAppender = RollingFileAppender.new(Rotation.DAILY, directory, fileNamePrefix)

/**
 * Creates a weekly-rotating file appender.
 */
public fun weekly(directory: String, fileNamePrefix: String): RollingFileAppender = RollingFileAppender.new(Rotation.WEEKLY, directory, fileNamePrefix)

/**
 * Creates a non-rolling file appender.
 */
public fun never(directory: String, fileName: String): RollingFileAppender = RollingFileAppender.new(Rotation.NEVER, directory, fileName)
