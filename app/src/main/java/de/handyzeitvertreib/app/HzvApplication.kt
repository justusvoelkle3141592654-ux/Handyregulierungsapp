package de.handyzeitvertreib.app

import android.app.Application
import de.handyzeitvertreib.app.enforcement.LimitCheckWorker

open class HzvApplication : Application() {
    lateinit var container: AppContainer

    override fun onCreate() {
        super.onCreate()
        container = createContainer()
        startBackgroundWork()
    }

    /** Overridden in tests to inject fakes. */
    protected open fun createContainer(): AppContainer = AppContainer.create(this)

    protected open fun startBackgroundWork() {
        container.limitNotifier.ensureChannel()
        LimitCheckWorker.schedule(this)
    }
}
