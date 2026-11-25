package com.thirdvision.app

import android.app.AppOpsManager
import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.Debug
import android.os.Process
import android.provider.Settings
import java.io.File
import java.net.InetSocketAddress
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class Sev(val points: Int) { INFO(0), WARN(10), HIGH(30) }
data class Finding(val category: String, val title: String, val detail: String, val sev: Sev)
data class ScanResult(val findings: List<Finding>, val score: Int, val days: Int) {
    val verdict get() = when { score >= 40 -> "HIGH RISK"; score >= 10 -> "SUSPICIOUS"; else -> "CLEAN" }
}

object Scanner {
    private val highKeys = listOf(
        "gameguardian", "game guardian", "lucky patcher", "luckypatcher", "lackypatch", "xposed",
        "lsposed", "edxposed", "magisk", "kernelsu", "apatch", "injector", "modmenu", "mod menu",
        "gamekiller", "game killer", "gamehack", "game hacker", "cheat", "hack"
    )
    private val warnKeys = listOf(
        "parallel space", "dual space", "vmos", "virtual", "clone", "auto clicker", "autoclicker",
        "auto tap", "macro", "freedom", "speed hack", "location spoof", "fake gps", "mock location"
    )
    private val stores = setOf(
        "com.android.vending", "com.sec.android.app.samsungapps", "com.amazon.venezia",
        "com.huawei.appmarket", "com.xiaomi.mipicks", "com.heytap.market"
    )
    private val suPaths = listOf(
        "/system/bin/su", "/system/xbin/su", "/sbin/su", "/su/bin/su", "/data/local/bin/su",
        "/data/local/xbin/su", "/system/app/Superuser.apk", "/sbin/.magisk", "/data/adb/magisk"
    )
    private val fmt = SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault())
    private fun t(ms: Long) = fmt.format(Date(ms))

    fun hasUsageAccess(ctx: Context): Boolean {
        val ops = ctx.getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return ops.checkOpNoThrow(AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), ctx.packageName) ==
            AppOpsManager.MODE_ALLOWED
    }

    private fun level(s: String): Sev? {
        val x = s.lowercase()
        return when {
            highKeys.any { x.contains(it) } -> Sev.HIGH
            warnKeys.any { x.contains(it) } -> Sev.WARN
            else -> null
        }
    }

    @Suppress("DEPRECATION")
    private fun installer(pm: PackageManager, pkg: String): String? = try {
        if (Build.VERSION.SDK_INT >= 30) pm.getInstallSourceInfo(pkg).installingPackageName
        else pm.getInstallerPackageName(pkg)
    } catch (e: Exception) { null }

    fun scan(ctx: Context, days: Int): ScanResult {
        val out = mutableListOf<Finding>()
        val now = System.currentTimeMillis()
        val from = now - days * 24L * 3600_000
        val pm = ctx.packageManager
        val installed = pm.getInstalledPackages(0)
        val names = installed.map { it.packageName }.toSet()
        val user = installed.filter { (it.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_SYSTEM == 0 }

        // 1) Recent installs / updates
        for (p in user) {
            val label = p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName
            val src = installer(pm, p.packageName)
            val side = src == null || src !in stores
            if (p.firstInstallTime >= from) {
                out += Finding("Installed recently", label,
                    "${p.packageName}\n${t(p.firstInstallTime)} • " + if (side) "outside app store (${src ?: "unknown"})" else "from store",
                    if (side) Sev.WARN else Sev.INFO)
            } else if (p.lastUpdateTime >= from) {
                out += Finding("Updated recently", label, "${p.packageName}\n${t(p.lastUpdateTime)}", Sev.INFO)
            }
        }

        // 2) Hack / cheat tool apps (installed now)
        for (p in user) {
            val label = p.applicationInfo?.loadLabel(pm)?.toString() ?: p.packageName
            val lv = level(label) ?: level(p.packageName) ?: continue
            out += Finding("Suspicious apps", label, "${p.packageName} matches a known hack/cheat/clone pattern", lv)
        }

        // 3) Removed apps (trace from usage events)
        if (hasUsageAccess(ctx)) {
            val usm = ctx.getSystemService(Context.USAGE_STATS_SERVICE) as UsageStatsManager
            val ev = usm.queryEvents(from, now)
            val e = UsageEvents.Event()
            val last = linkedMapOf<String, Long>()
            while (ev.hasNextEvent()) {
                ev.getNextEvent(e)
                if (e.packageName !in names) last[e.packageName] = e.timeStamp
            }
            for ((pkg, ts) in last) {
                out += Finding("Removed apps (trace)", pkg, "Used until ${t(ts)}, now not installed", level(pkg) ?: Sev.WARN)
            }
        } else out += Finding("Removed apps (trace)", "Usage access not granted", "Grant it to detect uninstalled apps", Sev.INFO)

        // 4) Device integrity (Duck Detector style)
        val hit = suPaths.filter { File(it).exists() } +
            (System.getenv("PATH") ?: "").split(":").filter { it.isNotEmpty() && File(it, "su").exists() }.map { "$it/su" }
        if (hit.isNotEmpty()) out += Finding("Device integrity", "Root traces found", hit.joinToString(), Sev.HIGH)
        if (Build.TAGS?.contains("test-keys") == true) out += Finding("Device integrity", "Test-keys build (custom ROM)", Build.FINGERPRINT, Sev.WARN)
        if (isEmulator()) out += Finding("Device integrity", "Emulator signs", "${Build.MODEL} / ${Build.HARDWARE}", Sev.HIGH)
        val maps = try { File("/proc/self/maps").readText().lowercase() } catch (e: Exception) { "" }
        if (listOf("xposed", "lsposed", "edxposed", "frida", "substrate").any { maps.contains(it) } || xposedClass())
            out += Finding("Device integrity", "Hooking framework detected", "Xposed/Frida/Substrate trace in this process", Sev.HIGH)
        if (fridaPort()) out += Finding("Device integrity", "Frida server port open", "127.0.0.1:27042 reachable", Sev.HIGH)
        if (Debug.isDebuggerConnected() || tracerPid() > 0) out += Finding("Device integrity", "Debugger/tracer attached", "TracerPid=${tracerPid()}", Sev.HIGH)
        val fp = ctx.filesDir.absolutePath
        if (!fp.startsWith("/data/user/0/") && !fp.startsWith("/data/data/"))
            out += Finding("Device integrity", "Running in clone/virtual space?", fp, Sev.WARN)
        val cr = ctx.contentResolver
        if (Settings.Global.getInt(cr, Settings.Global.ADB_ENABLED, 0) == 1)
            out += Finding("Device integrity", "USB debugging ON", "Developer option enabled", Sev.WARN)
        else if (Settings.Global.getInt(cr, Settings.Global.DEVELOPMENT_SETTINGS_ENABLED, 0) == 1)
            out += Finding("Device integrity", "Developer options ON", "", Sev.INFO)
        val acc = Settings.Secure.getString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
        if (!acc.isNullOrBlank()) out += Finding("Device integrity", "Accessibility services active",
            acc.split(":").joinToString("\n") { it.substringBefore('/') }, Sev.WARN)

        val score = minOf(100, out.sumOf { it.sev.points })
        return ScanResult(out.sortedByDescending { it.sev.points }, score, days)
    }

    private fun isEmulator() = Build.FINGERPRINT.startsWith("generic") || Build.HARDWARE.contains("goldfish") ||
        Build.HARDWARE.contains("ranchu") || Build.MODEL.contains("Emulator") || Build.PRODUCT.contains("sdk_gphone")

    private fun xposedClass() = try { Class.forName("de.robv.android.xposed.XposedBridge"); true } catch (e: Throwable) { false }

    private fun fridaPort() = try {
        Socket().use { it.connect(InetSocketAddress("127.0.0.1", 27042), 150); true }
    } catch (e: Exception) { false }

    private fun tracerPid(): Int = try {
        File("/proc/self/status").readLines().first { it.startsWith("TracerPid") }.substringAfter(":").trim().toInt()
    } catch (e: Exception) { 0 }
}
