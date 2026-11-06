package com.davicolzer.zureader

import android.app.Application
import com.davicolzer.zureader.data.HistoryRepository
import com.davicolzer.zureader.data.SettingsRepository
import com.davicolzer.zureader.data.ZuDatabase

class ZuReaderApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    val history: HistoryRepository by lazy {
        HistoryRepository(ZuDatabase.create(this).readings(), filesDir)
    }
}
