package com.davicolzer.zureader.reader

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.davicolzer.zureader.vision.GrayImage
import com.davicolzer.zureader.vision.ImageReading
import com.davicolzer.zureader.vision.Perspective
import com.davicolzer.zureader.vision.Quad
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
    data object Camera : ReaderUiState
    data class Crop(val image: Bitmap, val gray: GrayImage, val suggestion: Quad) : ReaderUiState
    data object Reading : ReaderUiState
    data class Result(val image: Bitmap, val result: ImageReading) : ReaderUiState
    data object CouldNotOpen : ReaderUiState
}

class ReaderViewModel(application: Application) : AndroidViewModel(application) {
    private val imageReader = ZuImageReader()
    private val _state = MutableStateFlow<ReaderUiState>(ReaderUiState.Home)
    val state: StateFlow<ReaderUiState> = _state.asStateFlow()

    /** Imagem da galeria: lê direto. */
    fun read(uri: Uri) = work {
        val bitmap = withContext(Dispatchers.IO) { ImageLoading.decode(getApplication(), uri) }
        val result = withContext(Dispatchers.Default) { imageReader.read(ImageLoading.toGray(bitmap)) }
        ReaderUiState.Result(bitmap, result)
    }

    fun openCamera() {
        _state.value = ReaderUiState.Camera
    }

    /** Foto da câmera: sugere o recorte em volta do texto encontrado. */
    fun onCaptured(photo: Bitmap) = work {
        withContext(Dispatchers.Default) {
            val gray = ImageLoading.toGray(photo)
            val quick = imageReader.read(gray, deskew = false)
            val suggestion = quick.suggestedCrop() ?: Quad.inset(gray.width, gray.height, 0.08f)
            ReaderUiState.Crop(photo, gray, suggestion)
        }
    }

    /** Endireita o quadrilátero escolhido e lê. */
    fun confirmCrop(quad: Quad) {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work {
            withContext(Dispatchers.Default) {
                val rectified = Perspective.rectify(crop.gray, quad)
                ReaderUiState.Result(ImageLoading.toBitmap(rectified), imageReader.read(rectified))
            }
        }
    }

    fun useWholePhoto() {
        val crop = _state.value as? ReaderUiState.Crop ?: return
        work { withContext(Dispatchers.Default) { ReaderUiState.Result(crop.image, imageReader.read(crop.gray)) } }
    }

    fun goHome() {
        _state.value = ReaderUiState.Home
    }

    private fun work(block: suspend () -> ReaderUiState) {
        _state.value = ReaderUiState.Reading
        viewModelScope.launch {
            _state.value = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                ReaderUiState.CouldNotOpen
            }
        }
    }
}
