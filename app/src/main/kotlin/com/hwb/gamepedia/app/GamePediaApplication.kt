package com.hwb.gamepedia.app

import android.app.Application

class GamePediaApplication : Application() {

    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer()
    }
}
