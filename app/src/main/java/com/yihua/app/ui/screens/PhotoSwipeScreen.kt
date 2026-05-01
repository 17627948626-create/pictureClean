package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
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
import com.yihua.app.data.Photo
import com.yihua.app.ui.theme.AppleSystemGray6
import com.yihua.app.ui.theme.LightGrayText
import com.yihua.app.ui.theme.SwipeUpColor
import com.yihua.app.ui.theme.ThumbnailHighlight
import com.yihua.app.ui.theme.TrashBadgeColor
import com.yihua.app.viewmodel.PhotoViewModel
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

private enum class GestureAxis { HORIZONTAL, VERTICAL }

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
            .background(AppleSystemGray6),
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
                color = Color(0xFF1C1C1E),
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = message,
                color = LightGrayText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(32.dp))
            Button(
                onClick = onRequest,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
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

    // 手势动画
    val horizontalOffset = remember { Animatable(0f) }
    val verticalOffset = remember { Animatable(0f) }
    val cardScale = remember { Animatable(1f) }
    val cardAlpha = remember { Animatable(1f) }
    var gestureAxis by remember { mutableStateOf<GestureAxis?>(null) }
    var isUndoAnimating by remember { mutableStateOf(false) }

    // 卡片尺寸
    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { 16.dp.toPx() }

    // currentIndex 变化时重置动画
    LaunchedEffect(state.currentIndex) {
        if (!isUndoAnimating) {
            horizontalOffset.snapTo(0f)
            verticalOffset.snapTo(0f)
            cardScale.snapTo(1f)
            cardAlpha.snapTo(1f)
        }
        gestureAxis = null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleSystemGray6)
            .statusBarsPadding()
    ) {
        when {
            state.isLoading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LightGrayText)
                }
            }
            state.isEmpty -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🎉", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "相册已整理完毕！",
                            color = Color(0xFF1C1C1E),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            else -> {
                // 顶栏
                TopBar(
                    currentPhoto = state.currentPhoto,
                    currentIndex = state.currentIndex,
                    totalCount = state.photos.size,
                    deleteQueueSize = state.deleteQueue.size,
                    onTrashClick = onNavigateToConfirm
                )

                // 照片卡片区域
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onSizeChanged {
                            cardWidthPx = it.width.toFloat()
                            cardHeightPx = it.height.toFloat()
                        }
                        .pointerInput(state.currentIndex) {
                            var axisDetermined = false
                            var lockedAxis: GestureAxis? = null

                            detectDragGestures(
                                onDragEnd = {
                                    val threshold = 100f
                                    scope.launch {
                                        when (lockedAxis) {
                                            GestureAxis.HORIZONTAL -> {
                                                val x = horizontalOffset.value
                                                when {
                                                    // 右划：上一张
                                                    x > threshold && state.currentIndex > 0 -> {
                                                        horizontalOffset.animateTo(
                                                            cardWidthPx + gapPx,
                                                            tween(300, easing = FastOutSlowInEasing)
                                                        )
                                                        viewModel.swipeLeft()
                                                    }
                                                    // 左划：下一张
                                                    x < -threshold && state.currentIndex < state.photos.size - 1 -> {
                                                        horizontalOffset.animateTo(
                                                            -(cardWidthPx + gapPx),
                                                            tween(300, easing = FastOutSlowInEasing)
                                                        )
                                                        viewModel.swipeRight()
                                                    }
                                                    else -> {
                                                        horizontalOffset.animateTo(
                                                            0f,
                                                            spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                        )
                                                    }
                                                }
                                            }
                                            GestureAxis.VERTICAL -> {
                                                val y = verticalOffset.value
                                                when {
                                                    // 上滑：标记删除
                                                    y < -threshold -> {
                                                        launch { verticalOffset.animateTo(-cardHeightPx, tween(350, easing = FastOutSlowInEasing)) }
                                                        launch { cardScale.animateTo(0.5f, tween(350, easing = FastOutSlowInEasing)) }
                                                        launch { cardAlpha.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                                        viewModel.swipeUp()
                                                    }
                                                    // 下滑：撤销删除
                                                    y > threshold -> {
                                                        val didUndo = viewModel.undoDelete()
                                                        if (didUndo) {
                                                            isUndoAnimating = true
                                                            // 先设置到飞走状态
                                                            verticalOffset.snapTo(-cardHeightPx)
                                                            cardScale.snapTo(0.5f)
                                                            cardAlpha.snapTo(0f)
                                                            // 反向飞回
                                                            launch { verticalOffset.animateTo(0f, tween(350, easing = FastOutSlowInEasing)) }
                                                            launch { cardScale.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                                                            launch { cardAlpha.animateTo(1f, tween(350, easing = FastOutSlowInEasing)) }
                                                            isUndoAnimating = false
                                                        } else {
                                                            verticalOffset.animateTo(
                                                                0f,
                                                                spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                            )
                                                        }
                                                    }
                                                    else -> {
                                                        launch { verticalOffset.animateTo(0f, spring(dampingRatio = Spring.DampingRatioLowBouncy)) }
                                                        launch { cardScale.animateTo(1f, spring()) }
                                                        launch { cardAlpha.animateTo(1f, spring()) }
                                                    }
                                                }
                                            }
                                            null -> {}
                                        }
                                    }
                                    axisDetermined = false
                                    lockedAxis = null
                                    gestureAxis = null
                                },
                                onDragCancel = {
                                    scope.launch {
                                        launch { horizontalOffset.animateTo(0f, spring()) }
                                        launch { verticalOffset.animateTo(0f, spring()) }
                                        launch { cardScale.animateTo(1f, spring()) }
                                        launch { cardAlpha.animateTo(1f, spring()) }
                                    }
                                    axisDetermined = false
                                    lockedAxis = null
                                    gestureAxis = null
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    // 轴锁定：前 15px 确定方向
                                    if (!axisDetermined) {
                                        val totalX = abs(horizontalOffset.value + dragAmount.x)
                                        val totalY = abs(verticalOffset.value + dragAmount.y)
                                        if (totalX > 15f || totalY > 15f) {
                                            lockedAxis = if (totalX > totalY) GestureAxis.HORIZONTAL else GestureAxis.VERTICAL
                                            axisDetermined = true
                                            gestureAxis = lockedAxis
                                        }
                                    }

                                    scope.launch {
                                        when (lockedAxis) {
                                            GestureAxis.HORIZONTAL -> {
                                                horizontalOffset.snapTo(horizontalOffset.value + dragAmount.x)
                                            }
                                            GestureAxis.VERTICAL -> {
                                                verticalOffset.snapTo(verticalOffset.value + dragAmount.y)
                                                // 上滑时缩小+淡出
                                                val progress = abs(verticalOffset.value) / cardHeightPx
                                                val clampedProgress = progress.coerceIn(0f, 1f)
                                                cardScale.snapTo(1f - clampedProgress * 0.5f)
                                                cardAlpha.snapTo(1f - clampedProgress * 0.8f)
                                            }
                                            null -> {
                                                horizontalOffset.snapTo(horizontalOffset.value + dragAmount.x)
                                                verticalOffset.snapTo(verticalOffset.value + dragAmount.y)
                                            }
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // 渲染前一张、当前、后一张
                    listOf(-1, 0, 1).forEach { offset ->
                        val index = state.currentIndex + offset
                        val photo = state.photos.getOrNull(index)
                        if (photo != null) {
                            PhotoCard(
                                photo = photo,
                                isMarkedForDelete = state.deleteQueue.any { it.id == photo.id },
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        translationX = horizontalOffset.value + offset * (cardWidthPx + gapPx)
                                        if (offset == 0) {
                                            translationY = verticalOffset.value
                                            scaleX = cardScale.value
                                            scaleY = cardScale.value
                                            alpha = cardAlpha.value
                                        }
                                    }
                            )
                        }
                    }
                }

                // 底部：进度 + 缩略图
                BottomSection(
                    photos = state.photos,
                    currentIndex = state.currentIndex,
                    totalCount = state.photos.size,
                    onThumbnailClick = { viewModel.goToIndex(it) }
                )
            }
        }
    }
}

@Composable
private fun TopBar(
    currentPhoto: Photo?,
    currentIndex: Int,
    totalCount: Int,
    deleteQueueSize: Int,
    onTrashClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 左：日期
        Text(
            text = currentPhoto?.let {
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                    .format(Date(it.dateAdded * 1000))
            } ?: "",
            color = LightGrayText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

        // 中：进度
        Text(
            text = if (totalCount > 0) "${currentIndex + 1} / $totalCount" else "",
            color = Color(0xFF1C1C1E),
            fontSize = 15.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f),
            textAlign = TextAlign.Center
        )

        // 右：垃圾桶
        Box(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.CenterEnd
        ) {
            IconButton(onClick = onTrashClick) {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = "待删除列表",
                    tint = if (deleteQueueSize > 0) TrashBadgeColor else LightGrayText
                )
            }
            if (deleteQueueSize > 0) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-4).dp, y = 4.dp)
                        .size(18.dp)
                        .background(TrashBadgeColor, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (deleteQueueSize > 99) "99+" else "$deleteQueueSize",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PhotoCard(
    photo: Photo,
    isMarkedForDelete: Boolean,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .shadow(4.dp, RoundedCornerShape(16.dp))
            .clip(RoundedCornerShape(16.dp))
            .background(Color.White)
    ) {
        AsyncImage(
            model = photo.uri,
            contentDescription = photo.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(16.dp))
        )

        // 待删除标记
        if (isMarkedForDelete) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp)
                    .background(SwipeUpColor.copy(alpha = 0.9f), RoundedCornerShape(8.dp))
                    .padding(horizontal = 10.dp, vertical = 4.dp)
            ) {
                Text("待删除", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun BottomSection(
    photos: List<Photo>,
    currentIndex: Int,
    totalCount: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(bottom = 8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 进度文字
        if (totalCount > 0) {
            Text(
                text = "${currentIndex + 1} / $totalCount",
                color = LightGrayText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }

        // 缩略图条
        ThumbnailStrip(
            photos = photos,
            currentIndex = currentIndex,
            onThumbnailClick = onThumbnailClick
        )
    }
}

@Composable
private fun ThumbnailStrip(
    photos: List<Photo>,
    currentIndex: Int,
    onThumbnailClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex) {
        val targetIndex = maxOf(0, currentIndex - 3)
        listState.animateScrollToItem(targetIndex)
    }

    LazyRow(
        state = listState,
        modifier = modifier
            .fillMaxWidth()
            .height(50.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        itemsIndexed(photos) { index, photo ->
            val isSelected = index == currentIndex
            AsyncImage(
                model = photo.uri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(if (isSelected) 46.dp else 38.dp)
                    .clip(RoundedCornerShape(4.dp))
                    .then(
                        if (isSelected) Modifier.border(2.dp, ThumbnailHighlight, RoundedCornerShape(4.dp))
                        else Modifier
                    )
                    .clickable { onThumbnailClick(index) }
            )
        }
    }
}
