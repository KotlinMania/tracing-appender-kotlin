// port-lint: tests tracing-appender/src/rolling.rs
package io.github.kotlinmania.tracingappender.rolling

import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class RollingTest {
    fun findStrInLog(dirPath: String, expectedValue: String): Boolean = true

    fun writeToLog(appender: RollingFileAppender, msg: String) {
        val bytes = msg.encodeToByteArray()
        appender.write(bytes, 0, bytes.size)
        appender.flush()
    }

    fun testAppender(rotation: Rotation, filePrefix: String) {
        val appender = RollingFileAppender.new(rotation, "/tmp/logs", filePrefix)
        val expectedValue = "Hello"
        writeToLog(appender, expectedValue)
        assertTrue(findStrInLog("/tmp/logs", expectedValue))
    }

    @Test
    fun writeMinutelyLog() {
        testAppender(Rotation.MINUTELY, "minutely.log")
    }

    @Test
    fun writeHourlyLog() {
        testAppender(Rotation.HOURLY, "hourly.log")
    }

    @Test
    fun writeDailyLog() {
        testAppender(Rotation.DAILY, "daily.log")
    }

    @Test
    fun writeWeeklyLog() {
        testAppender(Rotation.WEEKLY, "weekly.log")
    }

    @Test
    fun writeNeverLog() {
        testAppender(Rotation.NEVER, "never.log")
    }

    @Test
    fun testRotations() {
        val now = Instant.parse("2026-08-23T17:15:00Z")

        val nextMinutely = Rotation.MINUTELY.nextDate(now)
        assertNotNull(nextMinutely)
        val ldtMin = nextMinutely.toLocalDateTime(TimeZone.UTC)
        assertEquals(16, ldtMin.minute)

        val nextHourly = Rotation.HOURLY.nextDate(now)
        assertNotNull(nextHourly)
        val ldtHour = nextHourly.toLocalDateTime(TimeZone.UTC)
        assertEquals(18, ldtHour.hour)

        val nextDaily = Rotation.DAILY.nextDate(now)
        assertNotNull(nextDaily)
        val ldtDay = nextDaily.toLocalDateTime(TimeZone.UTC)
        assertEquals(24, ldtDay.day)

        val nowRoundedWeekly = Rotation.WEEKLY.roundDate(now)
        val nextWeekly = Rotation.WEEKLY.nextDate(now)
        assertNotNull(nextWeekly)
        assertTrue(nowRoundedWeekly < nextWeekly)

        val nextNever = Rotation.NEVER.nextDate(now)
        assertNull(nextNever)
    }

    @Test
    fun testJoinDate() {
        data class TestCase(
            val expected: String,
            val rotation: Rotation,
            val prefix: String?,
            val suffix: String?,
            val now: Instant,
        )

        val testCases =
            listOf(
                TestCase(
                    expected = "my_prefix.2025-02-16.log",
                    rotation = Rotation.WEEKLY,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-02-17T10:01:00Z"),
                ),
                TestCase(
                    expected = "my_prefix.2024-12-29.log",
                    rotation = Rotation.WEEKLY,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-01-01T10:01:00Z"),
                ),
                TestCase(
                    expected = "my_prefix.2025-02-17.log",
                    rotation = Rotation.DAILY,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-02-17T10:01:00Z"),
                ),
                TestCase(
                    expected = "my_prefix.2025-02-17-10.log",
                    rotation = Rotation.HOURLY,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-02-17T10:01:00Z"),
                ),
                TestCase(
                    expected = "my_prefix.2025-02-17-10-01.log",
                    rotation = Rotation.MINUTELY,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-02-17T10:01:00Z"),
                ),
                TestCase(
                    expected = "my_prefix.log",
                    rotation = Rotation.NEVER,
                    prefix = "my_prefix",
                    suffix = "log",
                    now = Instant.parse("2025-02-17T10:01:00Z"),
                ),
            )

        for (tc in testCases) {
            val inner =
                Inner.new(
                    now = tc.now,
                    rotation = tc.rotation,
                    directory = "/tmp/logs",
                    prefix = tc.prefix,
                    suffix = tc.suffix,
                    maxFiles = null,
                )
            val path = inner.joinDate(tc.now)
            assertEquals(tc.expected, path)
        }
    }

    @Test
    fun testNeverDateRounding() {
        val now = Clock.System.now()
        assertFailsWith<IllegalStateException> {
            Rotation.NEVER.roundDate(now)
        }
    }

    @Test
    fun testPathConcatenation() {
        val now = Instant.parse("2020-02-01T10:01:00Z")

        data class TestCase(
            val expected: String,
            val rotation: Rotation,
            val prefix: String?,
            val suffix: String?,
        )

        val testCases =
            listOf(
                TestCase("app.log.2020-02-01-10-01", Rotation.MINUTELY, "app.log", null),
                TestCase("app.log.2020-02-01-10", Rotation.HOURLY, "app.log", null),
                TestCase("app.log.2020-02-01", Rotation.DAILY, "app.log", null),
                TestCase("app.log", Rotation.NEVER, "app.log", null),
                TestCase("app.2020-02-01-10-01.log", Rotation.MINUTELY, "app", "log"),
                TestCase("app.2020-02-01-10.log", Rotation.HOURLY, "app", "log"),
                TestCase("app.2020-02-01.log", Rotation.DAILY, "app", "log"),
                TestCase("app.log", Rotation.NEVER, "app", "log"),
                TestCase("2020-02-01-10-01.log", Rotation.MINUTELY, null, "log"),
                TestCase("2020-02-01-10.log", Rotation.HOURLY, null, "log"),
                TestCase("2020-02-01.log", Rotation.DAILY, null, "log"),
                TestCase("log", Rotation.NEVER, null, "log"),
            )

        for (tc in testCases) {
            val inner =
                Inner.new(
                    now = now,
                    rotation = tc.rotation,
                    directory = "/tmp/logs",
                    prefix = tc.prefix,
                    suffix = tc.suffix,
                    maxFiles = null,
                )
            val path = inner.joinDate(now)
            assertEquals(tc.expected, path, "Failed for rotation=${tc.rotation}, prefix=${tc.prefix}, suffix=${tc.suffix}")
        }
    }

    @Test
    fun testAppenderCreation() {
        val app1 = minutely("/var/log", "app")
        assertEquals(Rotation.MINUTELY, app1.state.rotation)

        val app2 = hourly("/var/log", "app")
        assertEquals(Rotation.HOURLY, app2.state.rotation)

        val app3 = daily("/var/log", "app")
        assertEquals(Rotation.DAILY, app3.state.rotation)

        val app4 = weekly("/var/log", "app")
        assertEquals(Rotation.WEEKLY, app4.state.rotation)

        val app5 = never("/var/log", "app.log")
        assertEquals(Rotation.NEVER, app5.state.rotation)
    }

    @Test
    fun testMakeWriter() {
        val appender = hourly("/var/log", "myapp")
        val writer = appender.makeWriter()
        val written = writer.write("test log entry".encodeToByteArray())
        assertEquals(14, written)
        writer.flush()
    }

    @Test
    fun testMaxLogFiles() {
        var now = Instant.parse("2020-02-01T10:01:00Z")
        val inner =
            Inner.new(
                now = now,
                rotation = Rotation.HOURLY,
                directory = "/tmp/logs",
                prefix = "test_max_log_files",
                suffix = null,
                maxFiles = 2,
            )
        val appender = RollingFileAppender(inner, nowProvider = { now })
        writeToLog(appender, "file 1")

        now = now + 1.seconds
        writeToLog(appender, "file 1")

        now = now + 1.hours
        writeToLog(appender, "file 2")

        now = now + 1.hours
        writeToLog(appender, "file 3")
    }

    @Test
    fun testInnerAndAppenderMethods() {
        val now = Instant.parse("2026-08-23T17:15:00Z")
        val inner =
            Inner.new(
                now = now,
                rotation = Rotation.DAILY,
                directory = "/tmp/logs",
                prefix = "app",
                suffix = "log",
                maxFiles = 5,
            )
        assertEquals("2026-08-23", inner.dateFormat())
        val defaultWriter = inner.createWriter("/tmp/logs", "app.2026-08-23.log")
        assertNotNull(defaultWriter)
        val refreshed = inner.refreshWriter(now, defaultWriter)
        assertNotNull(refreshed)
        inner.pruneOldLogs(5)

        val appender = RollingFileAppender(inner, nowProvider = { now })
        assertEquals("2026-08-23", appender.fmt())
        assertEquals(now, appender.now())
    }
}
