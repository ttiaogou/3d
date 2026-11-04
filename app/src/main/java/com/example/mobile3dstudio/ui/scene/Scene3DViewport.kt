package com.example.mobile3dstudio.ui.scene

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
import io.github.sceneview.rememberCameraManipulator
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader

/**
 * Real 3D viewport used to validate SceneView/Filament integration.
 * The cube is a procedural test object, not a reconstruction of the imported photo.
 */
@Composable
fun Scene3DViewport(
    modifier: Modifier = Modifier,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float
) {
    val engine = rememberEngine()
    val materialLoader = rememberMaterialLoader(engine)
    val cubeMaterial = remember(materialLoader) {
        materialLoader.createColorInstance(Color(0xFF9CB8FF), metallic = 0.08f, roughness = 0.42f)
    }

    Box(modifier = modifier) {
        SceneView(
            modifier = Modifier.fillMaxSize(),
            engine = engine,
            cameraManipulator = rememberCameraManipulator()
        ) {
            CubeNode(
                size = Size(0.85f, 0.85f, 0.85f),
                position = Position(positionX, 0f, 0f),
                rotation = Rotation(rotationX, rotationY, 0f),
                scale = Scale(scaleFactor),
                materialInstance = cubeMaterial
            )
        }
    }
}
