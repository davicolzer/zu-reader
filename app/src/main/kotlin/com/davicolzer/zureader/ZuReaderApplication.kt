package com.davicolzer.zureader

import android.app.Application
import com.davicolzer.zureader.data.HistoryRepository
import com.davicolzer.zureader.data.LearnedGlyphsRepository
import com.davicolzer.zureader.data.SettingsRepository
import com.davicolzer.zureader.data.ZuDatabase
import com.davicolzer.zureader.data.backup.BackupManager

class ZuReaderApplication : Application() {
    val settings: SettingsRepository by lazy { SettingsRepository(this) }

    private val database by lazy { ZuDatabase.create(this) }

    /** Backup no Google Drive: monta e restaura a coleção. */
    val backup: BackupManager by lazy { BackupManager(this) { database } }

    val history: HistoryRepository by lazy { HistoryRepository(database.readings(), filesDir) }

    val learned: LearnedGlyphsRepository by lazy { LearnedGlyphsRepository(database.learnedGlyphs()) }
}
