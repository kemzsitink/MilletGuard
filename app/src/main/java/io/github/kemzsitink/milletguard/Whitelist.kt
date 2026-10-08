package io.github.kemzsitink.milletguard

/**
 * Pure rules for the comma-separated package list stored in `MILLET_NO_RESTRICT_APP`.
 *
 * Nothing here touches Android, so the repair and checklist rules are covered by plain JVM
 * unit tests. [SettingsGuard] owns the reads and writes around them.
 */
internal object Whitelist {
    /** Used only when neither the live list nor a remembered one has any entries. */
    val SEED = listOf("com.tencent.mm", Packages.PLAY_STORE)

    /** Entries in list order, trimmed, without blanks or duplicates. */
    fun parse(value: String?): LinkedHashSet<String> {
        val out = LinkedHashSet<String>()
        value?.split(',')?.forEach { part ->
            val p = part.javaTrim()
            if (p.isNotEmpty()) out.add(p)
        }
        return out
    }

    fun join(items: Collection<String>): String = items.joinToString(",")

    /** True when [item] is a whole entry of [value]; a blank [item] is never present. */
    fun contains(value: String?, item: String): Boolean {
        val wanted = item.javaTrim()
        return wanted.isNotEmpty() && wanted in parse(value)
    }

    /**
     * The list to write so that [required] is present, or null when [current] already has it.
     * Every existing entry is kept. An empty list is rebuilt from [lastGood], then from [SEED],
     * so a wiped setting never ends up holding only [required].
     */
    fun repaired(current: String?, lastGood: String?, required: String): String? {
        if (contains(current, required)) return null
        val packages = parse(current)
        if (packages.isEmpty()) packages.addAll(parse(lastGood))
        if (packages.isEmpty()) packages.addAll(SEED)
        required.javaTrim().takeIf { it.isNotEmpty() }?.let(packages::add)
        return join(packages)
    }

    class Selection(val value: String, val changed: Int)

    /**
     * Applies a checklist choice: packages of [scope] that are in [selected] join the list,
     * the rest of [scope] leaves it, and entries outside [scope] are kept untouched.
     */
    fun select(current: String?, scope: Collection<String>, selected: Set<String>): Selection {
        val all = parse(current)
        var changed = 0
        for (pkg in scope) {
            val changedHere = if (pkg in selected) all.add(pkg) else all.remove(pkg)
            if (changedHere) changed++
        }
        return Selection(join(all), changed)
    }
}

/** java.lang.String.trim() semantics (strips chars <= U+0020), kept for exact parity. */
@Suppress("TrimLambda") // Kotlin's trim() also strips Unicode spaces, which Java's does not.
internal fun String.javaTrim(): String = trim { it <= ' ' }
