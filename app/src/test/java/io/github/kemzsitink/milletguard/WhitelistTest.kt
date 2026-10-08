package io.github.kemzsitink.milletguard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WhitelistTest {
    private val gms = Packages.GMS

    @Test
    fun parseTrimsDropsBlanksAndDuplicatesKeepingOrder() {
        assertEquals(listOf("b", "a", "c"), Whitelist.parse(" b ,a,, b,\tc ,").toList())
        assertTrue(Whitelist.parse(null).isEmpty())
        assertTrue(Whitelist.parse(" , ").isEmpty())
    }

    @Test
    fun containsMatchesWholeEntriesOnly() {
        assertTrue(Whitelist.contains("com.a, $gms ,com.b", gms))
        assertTrue(Whitelist.contains(gms, " $gms "))
        assertFalse(Whitelist.contains("$gms.extra,com.a", gms))
        assertFalse(Whitelist.contains(null, gms))
        assertFalse(Whitelist.contains("com.a", " "))
    }

    @Test
    fun repairedIsNullWhenRequiredIsPresent() {
        assertNull(Whitelist.repaired("com.a,$gms", lastGood = "com.z", required = gms))
    }

    @Test
    fun repairedKeepsEveryEntryAndAppendsRequired() {
        assertEquals("com.a,com.b,$gms", Whitelist.repaired("com.a, com.b,com.a", "com.z", gms))
    }

    @Test
    fun repairedRebuildsAnEmptyListFromLastGoodThenSeed() {
        assertEquals("com.z,$gms", Whitelist.repaired("", "com.z", gms))
        assertEquals("com.z,$gms", Whitelist.repaired(null, "com.z,$gms", gms))
        assertEquals(
            Whitelist.join(Whitelist.SEED + gms),
            Whitelist.repaired(" ", lastGood = null, required = gms),
        )
    }

    @Test
    fun selectTouchesOnlyPackagesInScope() {
        val result = Whitelist.select(
            current = "$gms,com.old,com.keep",
            scope = listOf("com.old", "com.new", "com.absent"),
            selected = setOf("com.new"),
        )
        assertEquals("$gms,com.keep,com.new", result.value)
        assertEquals(2, result.changed)
    }

    @Test
    fun selectReportsNoChangeWhenListAlreadyMatches() {
        val result = Whitelist.select("$gms,com.a", scope = listOf("com.a", "com.b"), selected = setOf("com.a"))
        assertEquals(0, result.changed)
        assertEquals("$gms,com.a", result.value)
    }
}
