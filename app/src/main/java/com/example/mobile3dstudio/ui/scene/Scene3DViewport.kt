package com.example.mobile3dstudio.ui.scene

import android.graphics.Bitmap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import io.github.sceneview.SceneView
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.math.Scale
import io.github.sceneview.math.Size
import io.github.sceneview.node.CubeNode
import io.github.sceneview.node.ImageNode
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/** A single 3D workspace. Imported images are placed in the scene as textured planes. */
@Composable
fun Scene3DViewport(
    modifier: Modifier = Modifier,
    imageBitmap: Bitmap?,
    imageScale: Float,
    imageOffsetX: Float,
    imageOffsetY: Float,
    imageRotation: Float
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cubeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(
            Color(0xFF9CB8FF),
            metallic = 0.08f,
            roughness = 0.42f
        )
    }
    val cameraManipulator = rememberCameraManipulator()

    Box(modifier = modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            cameraManipulator = if (imageBitmap == null) cameraManipulator else null
        ) {
            if (imageBitmap != null) {
                ImageNode(
                    bitmap = imageBitmap,
                    position = Position(imageOffsetX, imageOffsetY, 0f),
                    rotation = Rotation(0f, 0f, imageRotation),
                    scale = Scale(imageScale)
                )
            } else {
                CubeNode(
                    size = Size(0.85f, 0.85f, 0.85f),
                    rotation = Rotation(18f, 25f, 0f),
                    materialInstance = cubeMaterial
                )
            }
        }
    }
}
