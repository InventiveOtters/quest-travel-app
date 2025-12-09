package com.inotter.onthegovr

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * Application class for OnTheGoVR.
 * Initializes Hilt for dependency injection and configures WorkManager with HiltWorkerFactory.
 */
@HiltAndroidApp
class OnTheGoVRApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}

