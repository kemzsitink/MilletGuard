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
        assertNull(Whitelist.repaired("com.a,$gms", lastGood = "com.z", required = listOf(gms)))
    }

    @Test
    fun repairedKeepsEveryEntryAndAppendsRequired() {
        assertEquals("com.a,com.b,$gms", Whitelist.repaired("com.a, com.b,com.a", "com.z", listOf(gms)))
    }

    @Test
    fun repairedRebuildsAnEmptyListFromLastGoodThenSeed() {
        assertEquals("com.z,$gms", Whitelist.repaired("", "com.z", listOf(gms)))
        assertEquals("com.z,$gms", Whitelist.repaired(null, "com.z,$gms", listOf(gms)))
        assertEquals(
            Whitelist.join(Whitelist.SEED + gms),
            Whitelist.repaired(" ", lastGood = null, required = listOf(gms)),
        )
    }

    @Test
    fun repairedPutsBackPinnedPackagesAHyperOsRebuildDropped() {
        val required = listOf(gms, "app.self", "app.chat")
        // A rebuild kept GMS but dropped the pins: only they are appended, in pin order.
        assertEquals("com.a,$gms,app.self,app.chat", Whitelist.repaired("com.a,$gms", null, required))
        assertEquals("app.chat,com.a,$gms,app.self", Whitelist.repaired("app.chat,com.a", null, required))
        assertNull(Whitelist.repaired("app.chat, app.self,$gms", null, required))
    }

    @Test
    fun repairedNeverWritesWhenNothingIsRequired() {
        assertNull(Whitelist.repaired("com.a", null, listOf(" ", "")))
        assertNull(Whitelist.repaired(null, "com.z", emptyList()))
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
