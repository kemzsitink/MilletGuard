package io.github.kemzsitink.milletguard

import android.annotation.SuppressLint
import android.app.AppOpsManager
import android.content.Context

/**
 * Best-effort, read-only probe for Xiaomi/HyperOS Autostart AppOps.
 *
 * This object never changes AppOps. HyperOS may block these vendor-specific queries on
 * some builds; in that case callers must present [Status.UNKNOWN] rather than guessing.
 */
object AutostartStatusReader {
    private const val OP_MIUI_AUTOSTART = 10008
    private const val OP_MIUI_AUTOSTART_SWITCH = 10053

    enum class Status {
        ENABLED,
        PARTIAL,
        DISABLED,
        UNKNOWN,
    }

    fun check(context: Context, packageName: String): Status = try {
        val info = context.packageManager.getApplicationInfo(packageName, 0)
        val primary = checkOp(context, OP_MIUI_AUTOSTART, info.uid, packageName)
        val switchOp = checkOp(context, OP_MIUI_AUTOSTART_SWITCH, info.uid, packageName)

        when {
            isAllowed(primary) && isAllowed(switchOp) -> Status.ENABLED
            isIgnored(primary) && isIgnored(switchOp) -> Status.DISABLED
            (isAllowed(primary) && isIgnored(switchOp)) ||
                (isIgnored(primary) && isAllowed(switchOp)) -> Status.PARTIAL
            else -> Status.UNKNOWN
        }
    } catch (ignored: Throwable) {
        Status.UNKNOWN
    }

    private fun isAllowed(mode: Int?): Boolean = mode == AppOpsManager.MODE_ALLOWED

    private fun isIgnored(mode: Int?): Boolean = mode == AppOpsManager.MODE_IGNORED

    /**
     * Xiaomi keeps these vendor AppOps outside the public SDK constants and they are only
     * reachable through the hidden `checkOpNoThrow`. Reflection keeps the app
     * compileSdk-clean while preserving a strictly read-only path.
     */
    @SuppressLint("DiscouragedPrivateApi", "SoonBlockedPrivateApi")
    private fun checkOp(context: Context, op: Int, uid: Int, packageName: String): Int? {
        val manager = context.getSystemService(Context.APP_OPS_SERVICE) as? AppOpsManager
            ?: return null

        try {
            val method = AppOpsManager::class.java.getMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                String::class.java,
            )
            return method.invoke(manager, op, uid, packageName) as? Int
        } catch (ignored: Throwable) {
        }

        return try {
            val method = AppOpsManager::class.java.getDeclaredMethod(
                "checkOpNoThrow", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType,
                String::class.java,
            )
            method.isAccessible = true
            method.invoke(manager, op, uid, packageName) as? Int
        } catch (ignored: Throwable) {
            null
        }
    }
}
