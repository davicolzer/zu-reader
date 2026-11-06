package com.davicolzer.zureader

import android.app.Application
import com.davicolzer.zureader.data.HistoryRepository
import com.davicolzer.zureader.data.ZuDatabase

class ZuReaderApplication : Application() {
    val history: HistoryRepository by lazy {
        HistoryRepository(ZuDatabase.create(this).readings(), filesDir)
    }
}
