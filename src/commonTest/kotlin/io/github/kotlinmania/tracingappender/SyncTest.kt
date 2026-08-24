package io.github.kotlinmania.tracingappender

import io.github.kotlinmania.tracingappender.sync.RwLock
import kotlin.test.Test
import kotlin.test.assertEquals

class SyncTest {
    @Test
    fun testRwLockReadWrite() {
        val lock = RwLock(42)
        assertEquals(42, lock.read())

        lock.write(100)
        assertEquals(100, lock.read())

        val result =
            lock.withWrite { current ->
                Pair(current + 5, "updated to ${current + 5}")
            }
        assertEquals("updated to 105", result)
        assertEquals(105, lock.read())

        val readVal = lock.withRead { it * 2 }
        assertEquals(210, readVal)
    }
}
