package com.hootywhooo86.bitweather.ui

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.border
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.hootywhooo86.bitweather.data.LocationHelper
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.data.Repo
import com.hootywhooo86.bitweather.notify.Notifier
import com.hootywhooo86.bitweather.widget.Widgets
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface Screen {
    data object Home : Screen
    data object Places : Screen
    data object Settings : Screen
    data class Day(val index: Int) : Screen
    data class Alerts(val index: Int) : Screen
}

/** UI state. [tick] is bumped whenever data or settings change so screens re-read Prefs/Repo. */
object AppModel {
    var tick by mutableIntStateOf(0)
    var loading by mutableStateOf(false)
    var toast by mutableStateOf<String?>(null)
    var screen by mutableStateOf<Screen>(Screen.Home)
    var editing by mutableStateOf(false)
    fun bump() { tick++ }
    fun say(s: String) { toast = s }
}

class MainActivity : ComponentActivity() {

    private val locationPerms = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { res ->
        if (res.values.none { it } && res.keys.any { it.contains("LOCATION") }) {
            if (Prefs.firstRun) { Prefs.firstRun = false; Prefs.save() }
            AppModel.say("Location is off — tap ☰ to add places")
        }
        refresh(force = true)
    }
    private val singlePerm = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        AppModel.bump(); refresh(force = true)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Prefs.init(this)
        // open on where you are
        if (Prefs.followGps && Prefs.places.any { it.gps }) Prefs.activeId = "gps"
        setContent { Root(this) }
        if (Prefs.firstRun) askFirstRun()
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.RESUMED) {
                while (isActive) {
                    refresh(force = false)
                    delay(Repo.UPDATE_MS)
                }
            }
        }
    }

    private fun askFirstRun() {
        val list = mutableListOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION)
        if (Build.VERSION.SDK_INT >= 33) list += Manifest.permission.POST_NOTIFICATIONS
        locationPerms.launch(list.toTypedArray())
    }

    fun requestLocation() {
        if (LocationHelper.hasPermission(this)) { refresh(true); return }
        locationPerms.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
    }

    /** "Allow all the time" so widgets can follow you while the app is closed. */
    fun requestBackgroundLocation() {
        if (!LocationHelper.hasPermission(this)) { requestLocation(); return }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) singlePerm.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
    }

    fun requestNotifications() {
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            singlePerm.launch(Manifest.permission.POST_NOTIFICATIONS); return
        }
        // already granted at the permission level but switched off in system settings
        startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
    }

    fun openAppSettings() {
        startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", packageName, null)))
    }

    fun openUrl(url: String) = runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }

    fun refresh(force: Boolean) {
        lifecycleScope.launch {
            AppModel.loading = true
            try {
                val r = Repo.cycle(this@MainActivity, force, foreground = true)
                if (r == null && Repo.cached(this@MainActivity, Prefs.active().id) == null) AppModel.say("Could not load weather — check your connection")
                else if (r?.stale == true && force) AppModel.say("Offline — showing saved forecast")
            } finally {
                AppModel.loading = false
                AppModel.bump()
            }
        }
    }

    /** Called after settings/layout changes: persist, redraw widgets. */
    fun changed(refetch: Boolean = false) {
        Prefs.save()
        AppModel.bump()
        Widgets.updateAll(this)
        if (refetch) refresh(false)
    }
}

@Composable
fun Root(act: MainActivity) {
    val screen = AppModel.screen
    BackHandler(enabled = screen != Screen.Home || AppModel.editing) {
        if (AppModel.editing) AppModel.editing = false else AppModel.screen = Screen.Home
    }
    Box(Modifier.fillMaxSize().background(C.bg).safeDrawingPadding()) {
        when (screen) {
            Screen.Home -> HomeScreen(act)
            Screen.Places -> PlacesScreen(act)
            Screen.Settings -> SettingsScreen(act)
            is Screen.Day -> DayScreen(screen.index)
            is Screen.Alerts -> AlertsScreen(screen.index)
        }
        AppModel.toast?.let { msg ->
            LaunchedEffect(msg) { delay(3200); AppModel.toast = null }
            Box(
                Modifier.align(Alignment.BottomCenter).padding(24.dp).widthIn(max = 420.dp)
                    .background(C.panel).border(3.dp, C.orange, RoundedCornerShape(2.dp)).padding(12.dp),
            ) { T(msg, 8.sp, C.orange, lh = 14.sp) }
        }
    }
}
