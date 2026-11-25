package com.willian.cacadorprecos

import android.app.Application
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.work.Notifier
import com.willian.cacadorprecos.work.Scheduler

class App : Application() {
    companion object {
        lateinit var instance: App
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        Repo.start(this)
        Notifier.ensureChannel(this)
        Scheduler.schedule(this, Repo.state.value.settings.intervalMin)
    }
}
