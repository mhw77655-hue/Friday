package ai.friday.assistant.runtime

import android.Manifest
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

/**
 * Friday's adapter for Termux's documented RUN_COMMAND interface.
 *
 * Termux owns the worker process and its private filesystem. Friday only sends
 * a fixed, audited bootstrap payload; no model-generated shell text is accepted.
 * The installed Termux:Boot script uses the separately authorized Shizuku rish
 * client to recover Friday when Friday's package has been force-stopped.
 */
object TermuxRuntimeBridge {
    const val TERMUX_PACKAGE = "com.termux"
    const val TERMUX_BOOT_PACKAGE = "com.termux.boot"
    const val RUN_COMMAND_PERMISSION = "com.termux.permission.RUN_COMMAND"

    private const val RUN_COMMAND_ACTION = "com.termux.RUN_COMMAND"
    private const val RUN_COMMAND_SERVICE = "com.termux.app.RunCommandService"
    private const val COMMAND_PATH = "/data/data/com.termux/files/usr/bin/sh"
    private const val TERMUX_HOME = "/data/data/com.termux/files/home"
    private const val BOOT_SCRIPT = "$TERMUX_HOME/.termux/boot/friday-guardian"

    enum class Availability {
        READY,
        TERMUX_NOT_INSTALLED,
        RUN_COMMAND_PERMISSION_MISSING,
        TERMUX_BOOT_NOT_INSTALLED
    }

    enum class RequestResult {
        REQUESTED,
        TERMUX_NOT_INSTALLED,
        RUN_COMMAND_PERMISSION_MISSING,
        START_REJECTED
    }

    fun availability(context: Context): Availability {
        if (!isInstalled(context, TERMUX_PACKAGE)) return Availability.TERMUX_NOT_INSTALLED
        if (ContextCompat.checkSelfPermission(context, RUN_COMMAND_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) return Availability.RUN_COMMAND_PERMISSION_MISSING
        if (!isInstalled(context, TERMUX_BOOT_PACKAGE)) return Availability.TERMUX_BOOT_NOT_INSTALLED
        return Availability.READY
    }

    fun isInstalled(context: Context, packageName: String): Boolean =
        runCatching {
            if (Build.VERSION.SDK_INT >= 33) {
                context.packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(packageName, 0)
            }
            true
        }.getOrDefault(false)

    /**
     * Installs and starts Friday's Termux-owned guardian.
     *
     * Preconditions: Termux RUN_COMMAND is enabled for Friday, Termux has
     * allow-external-apps=true, Termux:Boot has been opened once, and the owner
     * has exported Shizuku's official rish + rish_shizuku.dex into Termux HOME.
     * Termux must also be authorized as com.termux in Shizuku.
     */
    fun installGuardian(context: Context): RequestResult {
        if (!isInstalled(context, TERMUX_PACKAGE)) return RequestResult.TERMUX_NOT_INSTALLED
        if (ContextCompat.checkSelfPermission(context, RUN_COMMAND_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) return RequestResult.RUN_COMMAND_PERMISSION_MISSING

        return dispatch(context, INSTALL_AND_START_SCRIPT)
    }

    /** Pauses the running worker and removes its boot entry. */
    fun uninstallGuardian(context: Context): RequestResult {
        if (!isInstalled(context, TERMUX_PACKAGE)) return RequestResult.TERMUX_NOT_INSTALLED
        if (ContextCompat.checkSelfPermission(context, RUN_COMMAND_PERMISSION) !=
            PackageManager.PERMISSION_GRANTED
        ) return RequestResult.RUN_COMMAND_PERMISSION_MISSING
        return dispatch(context, PAUSE_AND_REMOVE_SCRIPT)
    }

    private fun dispatch(context: Context, script: String): RequestResult = try {
        val intent = Intent(RUN_COMMAND_ACTION).apply {
            component = ComponentName(TERMUX_PACKAGE, RUN_COMMAND_SERVICE)
            putExtra("com.termux.RUN_COMMAND_PATH", COMMAND_PATH)
            putExtra("com.termux.RUN_COMMAND_ARGUMENTS", arrayOf("-c", script))
            putExtra("com.termux.RUN_COMMAND_WORKDIR", TERMUX_HOME)
            putExtra("com.termux.RUN_COMMAND_BACKGROUND", true)
            putExtra("com.termux.RUN_COMMAND_SESSION_ACTION", "0")
            putExtra("com.termux.RUN_COMMAND_COMMAND_LABEL", "Friday recovery guardian")
            putExtra("com.termux.RUN_COMMAND_COMMAND_DESCRIPTION", "Install or manage Friday's independent recovery worker")
        }
        context.startService(intent)
        RequestResult.REQUESTED
    } catch (_: SecurityException) {
        RequestResult.RUN_COMMAND_PERMISSION_MISSING
    } catch (_: RuntimeException) {
        RequestResult.START_REJECTED
    }

    private val INSTALL_AND_START_SCRIPT = """
        set -eu
        mkdir -p "$TERMUX_HOME/.termux/boot"
        rm -f "$TERMUX_HOME/.friday-guardian.pause"
        cat > "$BOOT_SCRIPT" <<'FRIDAY_GUARDIAN_EOF'
        #!/data/data/com.termux/files/usr/bin/sh
        export RISH_APPLICATION_ID=com.termux
        RISH="$TERMUX_HOME/rish"
        LOG="$TERMUX_HOME/.friday-guardian.log"
        PAUSE="$TERMUX_HOME/.friday-guardian.pause"
        printf '%s guardian-started %s\n' "$(date -Iseconds)" "$$" >> "$LOG"
        command -v termux-wake-lock >/dev/null 2>&1 && termux-wake-lock
        while [ ! -f "$PAUSE" ]; do
            if [ ! -x "$RISH" ] || [ ! -r "$TERMUX_HOME/rish_shizuku.dex" ]; then
                echo "$(date -Iseconds) missing-rish-files" >> "$LOG"
                sleep 30
                continue
            fi
            ID_OUTPUT=$("$RISH" -c id 2>>"$LOG" || true)
            if ! printf '%s' "$ID_OUTPUT" | grep -Eq 'uid=(2000\(shell\)|0\(root\))'; then
                echo "$(date -Iseconds) shizuku-unavailable-or-unauthorized" >> "$LOG"
                sleep 30
                continue
            fi
            PACKAGE_STATE=$("$RISH" -c 'dumpsys package ai.friday.assistant' 2>>"$LOG" || true)
            if printf '%s\n' "$PACKAGE_STATE" | grep -Eq 'stopped=true'; then
                echo "$(date -Iseconds) friday-stopped-recovery-attempt" >> "$LOG"
                "$RISH" -c 'am start -n ai.friday.assistant/.MainActivity' >> "$LOG" 2>&1 || true
                sleep 8
                VERIFY=$("$RISH" -c 'dumpsys activity activities' 2>>"$LOG" || true)
                if printf '%s\n' "$VERIFY" | grep -E 'mResumedActivity|topResumedActivity' | grep -Fq 'ai.friday.assistant'; then
                    echo "$(date -Iseconds) friday-foreground-verified" >> "$LOG"
                else
                    echo "$(date -Iseconds) friday-launch-unverified" >> "$LOG"
                fi
            fi
            sleep 20
        done
        echo "$(date -Iseconds) guardian-paused" >> "$LOG"
        FRIDAY_GUARDIAN_EOF
        chmod 700 "$BOOT_SCRIPT"
        nohup "$BOOT_SCRIPT" >> "$TERMUX_HOME/.friday-guardian.log" 2>&1 </dev/null &
        echo "Friday guardian installed. Confirm rish authorization and Termux:Boot setup."
    """.trimIndent()

    private val PAUSE_AND_REMOVE_SCRIPT = """
        set -eu
        touch "$TERMUX_HOME/.friday-guardian.pause"
        rm -f "$BOOT_SCRIPT"
        echo "Friday guardian paused and boot entry removed."
    """.trimIndent()
}
