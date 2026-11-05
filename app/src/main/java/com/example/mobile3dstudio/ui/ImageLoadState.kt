package com.example.mobile3dstudio.ui

import android.graphics.Bitmap

sealed interface ImageLoadState {
    data object Empty : ImageLoadState
    data object Loading : ImageLoadState
    data class Success(val bitmap: Bitmap) : ImageLoadState
    data class Failure(val message: String) : ImageLoadState
}
