package io.github.kotlinmania.tracingappender.rolling

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class BuilderTest {
    @Test
    fun testBuilderDefaults() {
        val builder = Builder.new()
        assertEquals(Rotation.NEVER, builder.rotation)
        assertNull(builder.prefix)
        assertNull(builder.suffix)
        assertNull(builder.maxFiles)
    }

    @Test
    fun testBuilderConfig() {
        val builder =
            Builder
                .new()
                .rotation(Rotation.HOURLY)
                .filenamePrefix("myapp.log")
                .filenameSuffix("txt")
                .maxLogFiles(5)

        assertEquals(Rotation.HOURLY, builder.rotation)
        assertEquals("myapp.log", builder.prefix)
        assertEquals("txt", builder.suffix)
        assertEquals(5, builder.maxFiles)

        val appender = builder.build("/var/log")
        assertEquals(Rotation.HOURLY, appender.state.rotation)
    }

    @Test
    fun testMaxLogFilesZeroDisables() {
        val builder = Builder.new().maxLogFiles(0)
        assertNull(builder.maxFiles)
    }

    @Test
    fun testBuilderDefault() {
        val builder = Builder.default()
        assertEquals(Rotation.NEVER, builder.rotation)
    }

    @Test
    fun testInitErrorCtx() {
        val errorFn = InitError.ctx("failed to initialize")
        val err = errorFn(IllegalArgumentException("bad path"))
        assertEquals("failed to initialize", err.context)
        assertEquals("failed to initialize: bad path", err.message)
    }
}
