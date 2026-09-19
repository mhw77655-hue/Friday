package com.jarvis.app.ui.screens

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.jarvis.app.ui.components.StateChip
import com.jarvis.app.ui.components.pressScale
import com.jarvis.app.ui.theme.JarvisColors
import com.jarvis.app.ui.theme.JarvisType

private const val PREFS_NAME = "jarvis_settings"
private const val KEY_PERSONA = "persona_text"
private const val KEY_OFFLINE_MODE = "offline_mode"
private const val KEY_RECALL_LIMIT = "recall_limit"

/**
 * All three fields now persist to SharedPreferences (jarvis_settings),
 * a plain local file under the app's data dir -- no network, no new
 * dependency. Survives process death and app restart.
 *
 * NOTE: offlineMode is now saved and reloaded correctly, but nothing
 * else in the app reads it yet -- it doesn't actually force
 * JarvisBrainBridge/CloudVoiceClient into local-only mode. That's a
 * separate wiring job, not done here.
 *
 * PERMISSIONS section now does one real check: ObsidianSync.logTurn()
 * writes to /storage/emulated/0/JarvisSync/vault, which needs "All
 * files access" (MANAGE_EXTERNAL_STORAGE) on API 30+. That's a special
 * permission -- it can't be requested through a normal runtime
 * permission dialog, only by sending the user to a system Settings
 * screen (ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION). Shizuku/mic/
 * notifications below are still unwired, matching the existing note.
 */
@Composable
fun SettingsScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE) }

    var personaText by remember { mutableStateOf(prefs.getString(KEY_PERSONA, "") ?: "") }
    var offlineMode by remember { mutableStateOf(prefs.getBoolean(KEY_OFFLINE_MODE, true)) }
    var recallLimit by remember { mutableStateOf(prefs.getString(KEY_RECALL_LIMIT, "") ?: "") }

    // Bumped on ON_RESUME so the storage-permission row re-checks after
    // the user comes back from the system "All files access" screen --
    // isExternalStorageManager() is a plain call, not observable state,
    // so without this the row would show the stale pre-grant value
    // until some unrelated recomposition happened to occur.
    var resumeSignal by remember { mutableIntStateOf(0) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) resumeSignal++
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val hasStorageAccess = remember(resumeSignal) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            true // pre-API 30: legacy external storage, no special grant needed here
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(JarvisColors.SurfaceBase)
            .padding(16.dp)
    ) {
        SectionLabel("PERSONALITY")
        Text(
            text = "This changes how JARVIS answers, not what he knows.",
            style = JarvisType.Label,
            modifier = Modifier.padding(bottom = 8.dp)
        )
        OutlinedTextField(
            value = personaText,
            onValueChange = {
                personaText = it
                prefs.edit().putString(KEY_PERSONA, it).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Persona prompt", style = JarvisType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )

        SectionLabel("VOICE")
        Text(text = "TTS voice: en-GB (system)", style = JarvisType.Technical)

        SectionLabel("PERMISSIONS")
        Text(text = "Shizuku, mic, notifications — not yet wired to a real permission check", style = JarvisType.Label)

        StorageAccessRow(
            granted = hasStorageAccess,
            onTapGrant = { openAllFilesAccessSettings(context) }
        )

        SectionLabel("OFFLINE MODE")
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = "Force local-only", style = JarvisType.Body, modifier = Modifier.weight(1f))
            Switch(
                checked = offlineMode,
                onCheckedChange = {
                    offlineMode = it
                    prefs.edit().putBoolean(KEY_OFFLINE_MODE, it).apply()
                },
                colors = SwitchDefaults.colors(checkedTrackColor = JarvisColors.CoreIdle)
            )
        }

        SectionLabel("MEMORY RULES")
        OutlinedTextField(
            value = recallLimit,
            onValueChange = {
                recallLimit = it
                prefs.edit().putString(KEY_RECALL_LIMIT, it).apply()
            },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Recall limit", style = JarvisType.Body) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = JarvisColors.SurfaceHairline,
                unfocusedBorderColor = JarvisColors.SurfaceHairline
            )
        )
    }
}

@Composable
private fun StorageAccessRow(granted: Boolean, onTapGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .let { base -> if (granted) base else base.pressScale(onTapGrant) },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column {
            Text(text = "Storage (Obsidian vault)", style = JarvisType.Body)
            Text(
                text = if (granted) {
                    "All files access granted — JARVIS can write to /storage/emulated/0/JarvisSync/vault"
                } else {
                    "Not granted — tap to open system settings and allow All files access"
                },
                style = JarvisType.Label
            )
        }
        StateChip(
            label = if (granted) "granted" else "tap to grant",
            dotColor = if (granted) JarvisColors.CoreSuccess else JarvisColors.CoreWarning
        )
    }
}

/**
 * MANAGE_EXTERNAL_STORAGE can't be requested via a normal permission
 * dialog -- ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION with a
 * package: URI takes the user straight to this app's specific toggle
 * (not the general "All files access" list), one tap to grant. Falls
 * back to the general list screen if the app-specific intent isn't
 * resolvable on a given OEM's settings app.
 */
private fun openAllFilesAccessSettings(context: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return
    val appSpecific = Intent(
        Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
        Uri.parse("package:${context.packageName}")
    )
    val resolved = appSpecific.resolveActivity(context.packageManager) != null
    val intent = if (resolved) {
        appSpecific
    } else {
        Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
    }
    context.startActivity(intent)
}

@Composable
private fun SectionLabel(text: String) {
    Text(text = text, style = JarvisType.Label, modifier = Modifier.padding(top = 24.dp, bottom = 4.dp))
}
