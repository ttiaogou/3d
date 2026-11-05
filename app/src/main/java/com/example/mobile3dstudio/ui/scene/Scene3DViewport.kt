package com.example.mobile3dstudio.ui.scene

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.ImageNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine

/** Displays the imported bitmap as a correctly proportioned textured plane in 3D space. */
@Composable
fun Scene3DViewport(
    modifier: Modifier = Modifier,
    imageBitmap: Bitmap,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    rotationX: Float,
    rotationY: Float,
    rotationZ: Float
) {
    val engine = rememberEngine()
    val cameraManipulator = rememberCameraManipulator()

    // ImageNode's longest edge is one world unit. Specify both dimensions explicitly
    // so portrait and landscape images always retain the decoded bitmap aspect ratio.
    val aspectRatio = imageBitmap.width.toFloat() / imageBitmap.height.toFloat()
    val planeWidth = if (aspectRatio >= 1f) 1f else aspectRatio
    val planeHeight = if (aspectRatio >= 1f) 1f / aspectRatio else 1f

    Box(modifier = modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            cameraManipulator = cameraManipulator
        ) {
            ImageNode(
                bitmap = imageBitmap,
                size = Size(planeWidth, planeHeight),
                position = Position(imageOffsetX * 1.5f, imageOffsetY * 1.5f, 0f),
                rotation = Rotation(rotationX, rotationY, rotationZ),
                scale = Scale(imageScale)
            )
        }
    }
}
