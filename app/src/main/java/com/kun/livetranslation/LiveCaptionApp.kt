package com.kun.livetranslation

import android.app.Application
import com.kun.livetranslation.di.AppContainer
import com.kun.livetranslation.update.UpdateCheckWorker
import kotlinx.coroutines.launch

class LiveCaptionApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        // WorkManager.getInstance()/enqueue touch WorkManager's Room database; keep that
        // off the main-thread startup path.
        container.appScope.launch { UpdateCheckWorker.schedule(this@LiveCaptionApp) }

    }
}
