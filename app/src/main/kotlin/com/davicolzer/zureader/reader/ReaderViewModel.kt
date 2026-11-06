package com.davicolzer.zureader.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.vision.ZuImageReader
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ReaderUiState {
    data object Home : ReaderUiState
    data object Reading : ReaderUiState
    data class Result(val image: Bitmap, val result: ImageReading) : ReaderUiState
    data object CouldNotOpen : ReaderUiState
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val imageReader = ZuImageReader()
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Home)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    fun read(uri: Uri) {
        _state.value = ReaderUiState.Reading
        viewModelScope.launch {
            _state.value = try {
                val bitmap = withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) }
                val result = withContext(Dispatchers.Default) { imageReader.read(ImageLoading.toGray(bitmap)) }
                ReaderUiState.Result(bitmap, result)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderUiState.CouldNotOpen
            }
        }
    }

    fun goHome() {
        _state.value = ReaderUiState.Home
    }
}
