package com.willian.cacadorprecos

import android.app.Application
import com.willian.cacadorprecos.data.Repo
import com.willian.cacadorprecos.work.Notifier
import com.willian.cacadorprecos.work.Scheduler

class App : Application() {
    override fun onCreate() {
        super.onCreate()
        Repo.start(this)
        Notifier.ensureChannel(this)
        Scheduler.schedule(this, Repo.state.value.settings.intervalMin)
    }
}
