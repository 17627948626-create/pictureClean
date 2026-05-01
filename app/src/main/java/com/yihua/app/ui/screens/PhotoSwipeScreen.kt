package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.yihua.app.ui.theme.SwipeLeftColor
import com.yihua.app.ui.theme.SwipeRightColor
import com.yihua.app.ui.theme.SwipeUpColor
import com.yihua.app.viewmodel.PhotoViewModel
import kotlinx.coroutines.launch
import kotlin.math.abs

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel,
    onNavigateToConfirm: () -> Unit
) {
    val permissionName = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        android.Manifest.permission.READ_MEDIA_IMAGES
    } else {
        android.Manifest.permission.READ_EXTERNAL_STORAGE
    }

    val permissionState = rememberPermissionState(permissionName)

    LaunchedEffect(permissionState.status.isGranted) {
        if (permissionState.status.isGranted) {
            viewModel.loadPhotos()
        }
    }

    when {
        permissionState.status.isGranted -> {
            PhotoContent(viewModel = viewModel, onNavigateToConfirm = onNavigateToConfirm)
        }
        permissionState.status.shouldShowRationale -> {
            PermissionScreen(
                message = "一划需要读取您的照片，才能帮您整理相册。",
                buttonText = "授权访问",
                onRequest = { permissionState.launchPermissionRequest() }
            )
        }
        else -> {
            LaunchedEffect(Unit) {
                permissionState.launchPermissionRequest()
            }
            PermissionScreen(
                message = "请在弹窗中授权访问相册，让一划帮您轻松整理照片。",
                buttonText = "重新申请授权",
                onRequest = { permissionState.launchPermissionRequest() }
            )
        }
    }
}

@Composable
private fun PermissionScreen(
    message: String,
    buttonText: String,
    onRequest: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text(
                text = "📷",
                fontSize = 64.sp
            )
            Spacer(Modifier.height(24.dp))
            Text(
                text = "一划 · 相册瘦身",
                color = Color.White,
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = Color.White.copy(alpha = 0.7f),
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black
                )
            ) {
                Text(buttonText, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun PhotoContent(
    viewModel: PhotoViewModel,
    onNavigateToConfirm: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // 手势偏移动画
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    var swipeDirection by remember { mutableStateOf<SwipeDirection?>(null) }

    // 当 currentIndex 变化时，重置手势偏移
    LaunchedEffect(state.currentIndex) {
        offsetX.snapTo(0f)
        offsetY.snapTo(0f)
        swipeDirection = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        when {
            state.isLoading -> {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color.White
                )
            }
            state.isEmpty -> {
                Column(
                    modifier = Modifier.align(Alignment.Center),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text("🎉", fontSize = 64.sp)
                    Spacer(Modifier.height(16.dp))
                    Text(
                        "相册已整理完毕！",
                        color = Color.White,
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
            else -> {
                val photo = state.currentPhoto

                if (photo != null) {
                    // 主图：支持手势拖动
                    val rotation = offsetX.value / 20f
                    val alpha = 1f - (abs(offsetX.value) + abs(offsetY.value.coerceAtMost(0f))) / 1200f

                    AsyncImage(
                        model = photo.uri,
                        contentDescription = photo.displayName,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                translationX = offsetX.value
                                translationY = offsetY.value
                                rotationZ = rotation
                                this.alpha = alpha.coerceIn(0f, 1f)
                            }
                            .pointerInput(state.currentIndex) {
                                detectDragGestures(
                                    onDragEnd = {
                                        val x = offsetX.value
                                        val y = offsetY.value
                                        val horizontalAbs = abs(x)
                                        val verticalAbs = abs(y)
                                        val threshold = 120f

                                        scope.launch {
                                            when {
                                                // 上滑优先检测
                                                verticalAbs > horizontalAbs && y < -threshold -> {
                                                    offsetY.animateTo(
                                                        -size.height.toFloat(),
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                    )
                                                    viewModel.swipeUp()
                                                }
                                                // 右滑：下一张
                                                horizontalAbs > verticalAbs && x > threshold -> {
                                                    offsetX.animateTo(
                                                        size.width.toFloat(),
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                    )
                                                    viewModel.swipeRight()
                                                }
                                                // 左滑：上一张
                                                horizontalAbs > verticalAbs && x < -threshold -> {
                                                    offsetX.animateTo(
                                                        -size.width.toFloat(),
                                                        animationSpec = spring(stiffness = Spring.StiffnessMedium)
                                                    )
                                                    viewModel.swipeLeft()
                                                }
                                                // 未达到阈值，弹回
                                                else -> {
                                                    launch { offsetX.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                                    launch { offsetY.animateTo(0f, spring(dampingRatio = Spring.DampingRatioMediumBouncy)) }
                                                    swipeDirection = null
                                                }
                                            }
                                        }
                                    },
                                    onDragCancel = {
                                        scope.launch {
                                            launch { offsetX.animateTo(0f, spring()) }
                                            launch { offsetY.animateTo(0f, spring()) }
                                            swipeDirection = null
                                        }
                                    },
                                    onDrag = { change, dragAmount ->
                                        change.consume()
                                        scope.launch {
                                            offsetX.snapTo(offsetX.value + dragAmount.x)
                                            offsetY.snapTo(offsetY.value + dragAmount.y)
                                        }
                                        val x = offsetX.value
                                        val y = offsetY.value
                                        swipeDirection = when {
                                            abs(y) > abs(x) && y < -40f -> SwipeDirection.UP
                                            abs(x) > abs(y) && x > 40f -> SwipeDirection.RIGHT
                                            abs(x) > abs(y) && x < -40f -> SwipeDirection.LEFT
                                            else -> null
                                        }
                                    }
                                )
                            }
                    )

                    // 滑动提示标签
                    swipeDirection?.let { dir ->
                        SwipeHintLabel(
                            direction = dir,
                            modifier = Modifier.align(Alignment.Center)
                        )
                    }

                    // 当前照片已在队列中的标记
                    if (state.isCurrentMarkedForDelete) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .padding(16.dp)
                                .background(SwipeUpColor, RoundedCornerShape(8.dp))
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("待删除", color = Color.White, fontSize = 13.sp)
                        }
                    }
                }

                // 底部工具栏
                BottomBar(
                    currentIndex = state.currentIndex,
                    totalCount = state.photos.size,
                    deleteQueueSize = state.deleteQueue.size,
                    canUndo = state.canUndo,
                    onUndo = { viewModel.undo() },
                    onDeleteConfirm = onNavigateToConfirm,
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }
}

@Composable
private fun SwipeHintLabel(direction: SwipeDirection, modifier: Modifier = Modifier) {
    val (text, color) = when (direction) {
        SwipeDirection.RIGHT -> "下一张 →" to SwipeRightColor
        SwipeDirection.LEFT -> "← 上一张" to SwipeLeftColor
        SwipeDirection.UP -> "↑ 加入待删除" to SwipeUpColor
    }
    Box(
        modifier = modifier
            .background(color.copy(alpha = 0.85f), RoundedCornerShape(12.dp))
            .padding(horizontal = 20.dp, vertical = 10.dp)
    ) {
        Text(
            text = text,
            color = Color.White,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun BottomBar(
    currentIndex: Int,
    totalCount: Int,
    deleteQueueSize: Int,
    canUndo: Boolean,
    onUndo: () -> Unit,
    onDeleteConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(Color.Black.copy(alpha = 0.75f))
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度条
        if (totalCount > 0) {
            LinearProgressIndicator(
                progress = { (currentIndex + 1).toFloat() / totalCount },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = Color.White,
                trackColor = Color.White.copy(alpha = 0.2f)
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = "${currentIndex + 1} / $totalCount",
                color = Color.White.copy(alpha = 0.8f),
                fontSize = 13.sp
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 撤销按钮
            IconButton(
                onClick = onUndo,
                enabled = canUndo,
                modifier = Modifier
                    .size(44.dp)
                    .background(
                        if (canUndo) Color.White.copy(alpha = 0.15f) else Color.Transparent,
                        CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Undo,
                    contentDescription = "撤销",
                    tint = if (canUndo) Color.White else Color.White.copy(alpha = 0.3f)
                )
            }

            // 操作提示
            Text(
                text = buildString {
                    append("↑ 标删  ")
                    append("← →  切换")
                },
                color = Color.White.copy(alpha = 0.4f),
                fontSize = 11.sp
            )

            // 待删除队列按钮
            if (deleteQueueSize > 0) {
                Button(
                    onClick = onDeleteConfirm,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = SwipeUpColor,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text("待删除 $deleteQueueSize 张", fontSize = 13.sp)
                }
            } else {
                Spacer(Modifier.width(44.dp))
            }
        }
    }
}

enum class SwipeDirection { LEFT, RIGHT, UP }
