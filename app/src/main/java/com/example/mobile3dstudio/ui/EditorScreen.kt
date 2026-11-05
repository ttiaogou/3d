package com.example.mobile3dstudio.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.mobile3dstudio.core.model.EditorMode
import com.example.mobile3dstudio.ui.scene.Scene3DViewport

/**
 * Main editor screen.
 * Header, mode selector, and workspace stay fixed. Only the parameter panel scrolls.
 */
@Composable
fun EditorScreen(
    snackbarHostState: SnackbarHostState,
    hasImage: Boolean,
    imageBitmap: ImageBitmap?,
    mode: EditorMode,
    onImport: () -> Unit,
    onModeSelect: (EditorMode) -> Unit,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float,
    onPositionChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetTransforms: () -> Unit,
    onTwoDAction: (String) -> Unit,
    onTwoPointFiveDAction: () -> Unit,
    onExport: () -> Unit
) {
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Leave room for the fixed header and workspace while keeping three
            // complete transform controls visible on normal phone-sized screens.
            val parameterPanelHeight = when {
                maxHeight >= 680.dp -> 276.dp
                maxHeight >= 560.dp -> 260.dp
                maxHeight >= 440.dp -> 232.dp
                else -> maxHeight * 0.46f
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Header(hasImage = hasImage, onImport = onImport)
                ModeSelector(selected = mode, onSelect = onModeSelect)

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                ) {
                    when (mode) {
                        EditorMode.TWO_D -> TwoDWorkspace(
                            imageBitmap = imageBitmap,
                            onAction = onTwoDAction
                        )

                        EditorMode.TWO_POINT_FIVE_D -> TwoPointFiveDWorkspace(
                            imageBitmap = imageBitmap,
                            onAction = onTwoPointFiveDAction
                        )

                        EditorMode.THREE_D -> ThreeDWorkspace(
                            positionX = positionX,
                            rotationX = rotationX,
                            rotationY = rotationY,
                            scaleFactor = scaleFactor
                        )
                    }
                }

                ParameterPanel(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(parameterPanelHeight),
                    mode = mode,
                    positionX = positionX,
                    rotationX = rotationX,
                    rotationY = rotationY,
                    scaleFactor = scaleFactor,
                    onPositionChange = onPositionChange,
                    onRotationXChange = onRotationXChange,
                    onRotationYChange = onRotationYChange,
                    onScaleChange = onScaleChange,
                    onResetTransforms = onResetTransforms,
                    onExport = onExport
                )
            }
        }
    }
}

@Composable
private fun Header(hasImage: Boolean, onImport: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(Modifier.weight(1f))
        Button(onClick = onImport) {
            Text(if (hasImage) "更换图片" else "导入图片")
        }
    }
}

@Composable
private fun ModeSelector(
    selected: EditorMode,
    onSelect: (EditorMode) -> Unit
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        EditorMode.entries.forEach { mode ->
            FilterChip(
                selected = selected == mode,
                onClick = { onSelect(mode) },
                label = {
                    Column(verticalArrangement = Arrangement.spacedBy(1.dp)) {
                        Text(mode.title, fontWeight = FontWeight.SemiBold)
                        Text(mode.subtitle, style = MaterialTheme.typography.labelSmall)
                    }
                }
            )
        }
    }
}

@Composable
private fun TwoDWorkspace(imageBitmap: ImageBitmap?, onAction: (String) -> Unit) {
    WorkspaceCard(
        title = "2D 图片工作区",
        subtitle = "原图预览与基础图片处理入口"
    ) {
        ImagePreview(
            imageBitmap = imageBitmap,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { onAction("裁剪") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("裁剪")
            }
            OutlinedButton(
                onClick = { onAction("抠图") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("抠图")
            }
            OutlinedButton(
                onClick = { onAction("分层") },
                modifier = Modifier.weight(1f),
                enabled = imageBitmap != null
            ) {
                Text("分层")
            }
        }
        Text(
            "图片导入已实现。裁剪、主体抠图和图层编辑目前是功能入口，尚未执行实际图像处理。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TwoPointFiveDWorkspace(imageBitmap: ImageBitmap?, onAction: () -> Unit) {
    WorkspaceCard(
        title = "2.5D 深度工作区",
        subtitle = "为图片建立前后空间关系"
    ) {
        ImagePreview(
            imageBitmap = imageBitmap,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text("深度图", fontWeight = FontWeight.SemiBold)
                Text(
                    "未生成",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall
                )
            }
            Text(
                "待接入",
                color = MaterialTheme.colorScheme.secondary,
                style = MaterialTheme.typography.labelMedium
            )
        }
        Button(
            onClick = onAction,
            enabled = imageBitmap != null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("生成深度信息")
        }
        Text(
            "此处不会用假深度图模拟结果。后续接入本地深度估计模型后，再生成视差网格和可调视角。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThreeDWorkspace(
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float
) {
    WorkspaceCard(title = "3D 模型工作区", subtitle = null) {
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            Scene3DViewport(
                modifier = Modifier.fillMaxSize(),
                positionX = positionX,
                rotationX = rotationX,
                rotationY = rotationY,
                scaleFactor = scaleFactor
            )
            Surface(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(10.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f),
                shape = CircleShape
            ) {
                Text(
                    "测试几何体 · 非图片重建结果",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
        }
        Text(
            "当前立方体由程序直接创建，证明真实三维视口和基本变换控制已接通。导入图片到真实网格重建、模型文件加载和 GLB 写出仍待开发。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ParameterPanel(
    modifier: Modifier = Modifier,
    mode: EditorMode,
    positionX: Float,
    rotationX: Float,
    rotationY: Float,
    scaleFactor: Float,
    onPositionChange: (Float) -> Unit,
    onRotationXChange: (Float) -> Unit,
    onRotationYChange: (Float) -> Unit,
    onScaleChange: (Float) -> Unit,
    onResetTransforms: () -> Unit,
    onExport: () -> Unit
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(20.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 14.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                "参数调整",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )

            // A fresh list state per mode starts at the first parameter after switching modes.
            key(mode) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    state = rememberLazyListState(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    when (mode) {
                        EditorMode.THREE_D -> {
                            item(key = "position-x") {
                                TransformSlider("位置 X", positionX, -1f..1f, onPositionChange)
                            }
                            item(key = "rotation-x") {
                                TransformSlider("旋转 X", rotationX, 0f..360f, onRotationXChange)
                            }
                            item(key = "rotation-y") {
                                TransformSlider("旋转 Y", rotationY, 0f..360f, onRotationYChange)
                            }
                            item(key = "scale") {
                                TransformSlider("缩放", scaleFactor, 0.35f..1.8f, onScaleChange)
                            }
                            item(key = "transform-actions") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    OutlinedButton(
                                        onClick = onResetTransforms,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("重置变换")
                                    }
                                    Button(
                                        onClick = onExport,
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("导出 GLB")
                                    }
                                }
                            }
                        }

                        EditorMode.TWO_D,
                        EditorMode.TWO_POINT_FIVE_D -> {
                            item(key = "empty-parameters") {
                                Text(
                                    "当前模式暂无可调参数，相关功能接入后会显示在这里。",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    item(key = "pipeline-status") {
                        PipelineStatus()
                    }
                }
            }
        }
    }
}

@Composable
private fun TransformSlider(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodySmall)
            Text(
                "${"%.2f".format(value)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.secondary
            )
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun ImagePreview(
    imageBitmap: ImageBitmap?,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(18.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        if (imageBitmap != null) {
            Image(
                bitmap = imageBitmap,
                contentDescription = "导入的图片",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Fit
            )
        } else {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    "＋",
                    style = MaterialTheme.typography.displayMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Text("尚未导入图片", fontWeight = FontWeight.SemiBold)
                Text(
                    "点击右上角「导入图片」选择相册照片",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkspaceCard(
    title: String,
    subtitle: String?,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = Modifier.fillMaxSize(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                subtitle?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            content()
        }
    }
}

@Composable
private fun PipelineStatus() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp)
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text("处理流程", fontWeight = FontWeight.Bold)
            val steps = listOf(
                "图片导入" to true,
                "主体识别" to false,
                "主体分割" to false,
                "深度估计" to false,
                "真实 3D 重建" to false,
                "GLB 导出" to false
            )
            steps.forEachIndexed { index, step ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(
                                if (step.second) MaterialTheme.colorScheme.secondary
                                else MaterialTheme.colorScheme.outline
                            )
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        step.first,
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodySmall
                    )
                    Text(
                        if (step.second) "已接通" else "待开发",
                        style = MaterialTheme.typography.labelSmall,
                        color = if (step.second) MaterialTheme.colorScheme.secondary
                        else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (index != steps.lastIndex) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
                    )
                }
            }
        }
    }
}
