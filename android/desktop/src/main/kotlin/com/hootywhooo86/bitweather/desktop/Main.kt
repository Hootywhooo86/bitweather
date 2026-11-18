package com.hootywhooo86.bitweather.desktop

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Notification
import androidx.compose.ui.window.Tray
import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberTrayState
import androidx.compose.ui.window.rememberWindowState
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Bitweather for Windows: a tray app with pixel-art desktop widgets and a main window.
 * Closing the window keeps it running in the tray so the widgets stay up to date; quit from the tray.
 */
fun main(args: Array<String>) {
    Model.load()
    if (args.contains("--tray")) Model.mainOpen = false
    application {
        val tray = rememberTrayState()
        val scope = rememberCoroutineScope()
        fun refresh(force: Boolean) = scope.launch {
            Model.cycle(force = force, relocate = force).forEach { (title, msg, alert) ->
                tray.sendNotification(Notification(title, msg, if (alert) Notification.Type.Warning else Notification.Type.Info))
            }
        }
        // animation clock (8 frames a second) for icons, landscape, rain and snow
        LaunchedEffect(Unit) { while (true) { delay(125); if (Model.animate) Model.frame++ } }
        // the 10-minute update: location, weather, widgets, alerts
        LaunchedEffect(Unit) {
            while (true) {
                Model.cycle(force = false, relocate = true).forEach { (title, msg, alert) ->
                    tray.sendNotification(Notification(title, msg, if (alert) Notification.Type.Warning else Notification.Type.Info))
                }
                delay(Model.UPDATE_MS)
            }
        }

        Tray(
            icon = painterResource("tray.png"), state = tray, tooltip = "Bitweather",
            onAction = { Model.mainOpen = true },
            menu = {
                Item("Open Bitweather", onClick = { Model.mainOpen = true })
                Item("Refresh now", onClick = { refresh(true) })
                Separator()
                Menu("Add desktop widget") {
                    WIDGET_TYPES.forEach { (type, name) ->
                        Item(name, onClick = {
                            val n = Model.widgets.size
                            Model.widgets += DeskWidget("w" + System.currentTimeMillis().toString(36), type, "M", 80 + n * 30, 80 + n * 30)
                            Model.save()
                        })
                    }
                }
                Separator()
                Item("Quit Bitweather", onClick = { Model.save(); exitApplication() })
            },
        )

        if (Model.mainOpen) {
            Window(
                onCloseRequest = { Model.mainOpen = false }, title = "Bitweather", icon = painterResource("icon.png"),
                state = rememberWindowState(size = DpSize(480.dp, 860.dp)),
            ) { MainScreen { refresh(it) } }
        }

        for (w in Model.widgets) key(w.id) { DesktopWidget(w) }
    }
}
