package com.licode.li63050a6

import android.app.Application
import com.licode.li63050a6.di.AppContainer

class LicodeApp : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}