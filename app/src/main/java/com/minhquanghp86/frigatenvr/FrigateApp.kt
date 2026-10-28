package com.minhquanghp86.frigatenvr

import android.app.Application
import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStore
import com.minhquanghp86.frigatenvr.data.FrigateRepository

private val Context.sessionStore: DataStore<Preferences> by preferencesDataStore(name = "frigate_session")

class FrigateApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}

class AppContainer(context: Context) {
    val repository: FrigateRepository = FrigateRepository(context, context.sessionStore)
}
