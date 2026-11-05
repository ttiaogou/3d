package com.example.mobile3dstudio.ui

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTransformGestures
import com.example.mobile3dstudio.ui.scene.Scene3DViewport

@Composable
fun EditorScreen(
    snackbarHostState: SnackbarHostState,
    hasImage: Boolean,
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    onImageTransform: (scale: Float, offsetX: Float, offsetY: Float, rotation: Float) -> Unit,
    onExport: (String) -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            EditorTopBar(onExport = onExport)

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .clip(RoundedCornerShape(22.dp))
            ) {
                Scene3DViewport(
                    modifier = Modifier.fillMaxSize(),
                    imageBitmap = imageBitmap,
                    imageScale = imageScale,
                    imageOffsetX = imageOffsetX,
                    imageOffsetY = imageOffsetY,
                    imageRotation = imageRotation
                )

                if (imageBitmap != null) {
                    ImageTransformInput(
                        imageBitmap = imageBitmap,
                        imageScale = imageScale,
                        imageOffsetX = imageOffsetX,
                        imageOffsetY = imageOffsetY,
                        imageRotation = imageRotation,
                        onImageTransform = onImageTransform,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    EmptyWorkspaceHint(
                        onImportImage = onImportImage,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                WorkspaceImageMenu(
                    hasImage = hasImage,
                    onImportImage = onImportImage,
                    onRemoveImage = onRemoveImage,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(8.dp)
                )
            }
        }
    }
}

@Composable
private fun EditorTopBar(
    onExport: (String) -> Unit
) {
    var exportExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box {
            OutlinedButton(onClick = { exportExpanded = true }) {
                Text("导出")
            }
            DropdownMenu(
                expanded = exportExpanded,
                onDismissRequest = { exportExpanded = false }
            ) {
                listOf("GLB", "PNG", "JPG").forEach { format ->
                    DropdownMenuItem(
                        text = { Text(format) },
                        onClick = {
                            exportExpanded = false
                            onExport(format)
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ImageTransformInput(
    imageBitmap: Bitmap,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float,
    onImageTransform: (Float, Float, Float, Float) -> Unit,
    modifier: Modifier = Modifier
) {
    // The image is a textured plane in the Filament scene. A one-finger drag pans
    // the plane, a pinch changes its size, and a two-finger twist rotates it in-plane.
    Box(
        modifier = modifier.pointerInput(imageBitmap) {
            var currentScale = imageScale
            var currentOffsetX = imageOffsetX
            var currentOffsetY = imageOffsetY
            var currentRotation = imageRotation

            detectTransformGestures { _, pan, zoom, rotation ->
                currentScale = (currentScale * zoom).coerceIn(0.12f, 8f)
                currentOffsetX += pan.x / 320f
                currentOffsetY -= pan.y / 320f
                currentRotation += rotation
                onImageTransform(
                    currentScale,
                    currentOffsetX,
                    currentOffsetY,
                    currentRotation
                )
            }
        }
    )
}

@Composable
private fun EmptyWorkspaceHint(
    onImportImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f),
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 3.dp
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("＋", style = MaterialTheme.typography.displaySmall)
            OutlinedButton(onClick = onImportImage) {
                Text("导入图片")
            }
        }
    }
}

@Composable
private fun WorkspaceImageMenu(
    hasImage: Boolean,
    onImportImage: () -> Unit,
    onRemoveImage: () -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        Surface(
            shape = RoundedCornerShape(50),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.90f)
        ) {
            IconButton(onClick = { expanded = true }) {
                Text("⋮", style = MaterialTheme.typography.titleLarge)
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (hasImage) {
                DropdownMenuItem(
                    text = { Text("更换图片") },
                    onClick = {
                        expanded = false
                        onImportImage()
                    }
                )
                DropdownMenuItem(
                    text = { Text("移除图片") },
                    onClick = {
                        expanded = false
                        onRemoveImage()
                    }
                )
            } else {
                DropdownMenuItem(
                    text = { Text("导入图片") },
                    onClick = {
                        expanded = false
                        onImportImage()
                    }
                )
            }
        }
    }
}
