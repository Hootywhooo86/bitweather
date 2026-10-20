package com.hootywhooo86.bitweather

import android.app.Application
import com.hootywhooo86.bitweather.data.Prefs
import com.hootywhooo86.bitweather.notify.Notifier
import com.hootywhooo86.bitweather.notify.SummaryAlarm
import com.hootywhooo86.bitweather.work.UpdateWorker

class BitweatherApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Prefs.init(this)
        Notifier.channels(this)
        runCatching { UpdateWorker.schedule(this) }
        SummaryAlarm.schedule(this)
    }
}
