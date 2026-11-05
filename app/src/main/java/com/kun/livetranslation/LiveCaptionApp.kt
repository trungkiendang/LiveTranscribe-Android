package com.kun.livetranslation

import android.app.Application
import com.kun.livetranslation.di.AppContainer
import com.kun.livetranslation.update.UpdateCheckWorker
import com.google.firebase.FirebaseApp
import com.google.firebase.analytics.ktx.analytics
import com.google.firebase.crashlytics.ktx.crashlytics
import com.google.firebase.ktx.Firebase
import com.google.firebase.perf.ktx.performance
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

        FirebaseApp.initializeApp(this)
        // Don't pollute the Crashlytics dashboard or skew Analytics with developer
        // builds. Performance Monitoring follows the same toggle.
        val collectInProd = !BuildConfig.DEBUG
        Firebase.crashlytics.isCrashlyticsCollectionEnabled = collectInProd
        Firebase.analytics.setAnalyticsCollectionEnabled(collectInProd)
        Firebase.performance.isPerformanceCollectionEnabled = collectInProd

    }
}
