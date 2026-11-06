package com.davicolzer.zureader

import android.app.Application
import com.davicolzer.zureader.data.HistoryRepository
import com.davicolzer.zureader.data.LearnedGlyphsRepository
import com.davicolzer.zureader.data.SettingsRepository
import com.davicolzer.zureader.data.ZuDatabase

class ZuReaderApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val database by lazy { ZuDatabase.create(this) }

    val history: HistoryRepository by lazy { HistoryRepository(database.readings(), filesDir) }

    val learned: LearnedGlyphsRepository by lazy { LearnedGlyphsRepository(database.learnedGlyphs()) }
}
