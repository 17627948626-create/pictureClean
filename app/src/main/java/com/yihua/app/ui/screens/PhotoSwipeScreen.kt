package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animate
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

private suspend fun animateFloat(
    initialValue: Float,
    targetValue: Float,
    animationSpec: AnimationSpec<Float>,
    onValue: (Float) -> Unit
) {
    animate(
        initialValue = initialValue,
        targetValue = targetValue,
        animationSpec = animationSpec
    ) { value, _ ->
        onValue(value)
    }
}

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

    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var cardScale by remember { mutableFloatStateOf(1f) }
    var cardAlpha by remember { mutableFloatStateOf(1f) }
    // 上滑联动：下一张从右滑入的进度 0→1
    var swipeUpProgress by remember { mutableFloatStateOf(0f) }
    // 下滑联动：当前张向右让位的进度 0→1
    var swipeDownProgress by remember { mutableFloatStateOf(0f) }
    var gestureAxis by remember { mutableStateOf<GestureAxis?>(null) }
    var isUndoAnimating by remember { mutableStateOf(false) }

    var cardWidthPx by remember { mutableFloatStateOf(0f) }
    var cardHeightPx by remember { mutableFloatStateOf(0f) }
    val gapPx = with(LocalDensity.current) { 16.dp.toPx() }

    fun resetCardTransform() {
        horizontalOffset = 0f
        verticalOffset = 0f
        cardScale = 1f
        cardAlpha = 1f
        swipeUpProgress = 0f
        swipeDownProgress = 0f
    }

    LaunchedEffect(state.currentIndex) {
        if (!isUndoAnimating) {
            resetCardTransform()
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
                TopBar(
                    currentPhoto = state.currentPhoto,
                    deleteQueueSize = state.deleteQueue.size,
                    onTrashClick = onNavigateToConfirm
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                        .onSizeChanged {
                            cardWidthPx = it.width.toFloat()
                            cardHeightPx = it.height.toFloat()
                        }
                        .pointerInput(state.currentIndex, state.photos.size) {
                            var lockedAxis: GestureAxis? = null
                            var totalDragX = 0f
                            var totalDragY = 0f

                            fun clearDragState() {
                                lockedAxis = null
                                totalDragX = 0f
                                totalDragY = 0f
                                gestureAxis = null
                            }

                            detectDragGestures(
                                onDragEnd = {
                                    val threshold = 100f
                                    val dragAxis = lockedAxis

                                    scope.launch {
                                        when (dragAxis) {
                                            GestureAxis.HORIZONTAL -> {
                                                val x = horizontalOffset
                                                when {
                                                    x > threshold && state.currentIndex > 0 -> {
                                                        animateFloat(
                                                            initialValue = horizontalOffset,
                                                            targetValue = cardWidthPx + gapPx,
                                                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                                                        ) { horizontalOffset = it }
                                                        viewModel.swipeLeft()
                                                        resetCardTransform()
                                                    }
                                                    x < -threshold && state.currentIndex < state.photos.size - 1 -> {
                                                        animateFloat(
                                                            initialValue = horizontalOffset,
                                                            targetValue = -(cardWidthPx + gapPx),
                                                            animationSpec = tween(260, easing = FastOutSlowInEasing)
                                                        ) { horizontalOffset = it }
                                                        viewModel.swipeRight()
                                                        resetCardTransform()
                                                    }
                                                    else -> {
                                                        animateFloat(
                                                            initialValue = horizontalOffset,
                                                            targetValue = 0f,
                                                            animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                        ) { horizontalOffset = it }
                                                    }
                                                }
                                            }
                                            GestureAxis.VERTICAL -> {
                                                val y = verticalOffset
                                                when {
                                                    y < -threshold -> {
                                                        val spec = tween<Float>(300, easing = FastOutSlowInEasing)
                                                        val yJob = launch {
                                                            animateFloat(verticalOffset, -cardHeightPx, spec) { verticalOffset = it }
                                                        }
                                                        val scaleJob = launch {
                                                            animateFloat(cardScale, 0.5f, spec) { cardScale = it }
                                                        }
                                                        val alphaJob = launch {
                                                            animateFloat(cardAlpha, 0f, spec) { cardAlpha = it }
                                                        }
                                                        // 下一张同步从右侧滑入
                                                        val upProgressJob = launch {
                                                            animateFloat(0f, 1f, spec) { swipeUpProgress = it }
                                                        }
                                                        yJob.join(); scaleJob.join(); alphaJob.join(); upProgressJob.join()
                                                        viewModel.swipeUp()
                                                        resetCardTransform()
                                                    }
                                                    y > threshold -> {
                                                        isUndoAnimating = true
                                                        val didUndo = viewModel.undoDelete()
                                                        if (didUndo) {
                                                            // 恢复的照片从上方飞入初始状态
                                                            verticalOffset = -cardHeightPx
                                                            cardScale = 0.5f
                                                            cardAlpha = 0f
                                                            swipeDownProgress = 0f

                                                            val spec = tween<Float>(300, easing = FastOutSlowInEasing)
                                                            val yJob = launch {
                                                                animateFloat(-cardHeightPx, 0f, spec) { verticalOffset = it }
                                                            }
                                                            val scaleJob = launch {
                                                                animateFloat(0.5f, 1f, spec) { cardScale = it }
                                                            }
                                                            val alphaJob = launch {
                                                                animateFloat(0f, 1f, spec) { cardAlpha = it }
                                                            }
                                                            // 之前那张同步向右让位
                                                            val downProgressJob = launch {
                                                                animateFloat(0f, 1f, spec) { swipeDownProgress = it }
                                                            }
                                                            yJob.join(); scaleJob.join(); alphaJob.join(); downProgressJob.join()
                                                        } else {
                                                            animateFloat(
                                                                initialValue = verticalOffset,
                                                                targetValue = 0f,
                                                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                            ) { verticalOffset = it }
                                                        }
                                                        isUndoAnimating = false
                                                        resetCardTransform()
                                                    }
                                                    else -> {
                                                        val yJob = launch {
                                                            animateFloat(
                                                                initialValue = verticalOffset,
                                                                targetValue = 0f,
                                                                animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                            ) { verticalOffset = it }
                                                        }
                                                        val scaleJob = launch {
                                                            animateFloat(
                                                                initialValue = cardScale,
                                                                targetValue = 1f,
                                                                animationSpec = spring()
                                                            ) { cardScale = it }
                                                        }
                                                        val alphaJob = launch {
                                                            animateFloat(
                                                                initialValue = cardAlpha,
                                                                targetValue = 1f,
                                                                animationSpec = spring()
                                                            ) { cardAlpha = it }
                                                        }
                                                        yJob.join()
                                                        scaleJob.join()
                                                        alphaJob.join()
                                                    }
                                                }
                                            }
                                            null -> resetCardTransform()
                                        }
                                    }
                                    clearDragState()
                                },
                                onDragCancel = {
                                    scope.launch {
                                        val xJob = launch {
                                            animateFloat(
                                                initialValue = horizontalOffset,
                                                targetValue = 0f,
                                                animationSpec = spring()
                                            ) { horizontalOffset = it }
                                        }
                                        val yJob = launch {
                                            animateFloat(
                                                initialValue = verticalOffset,
                                                targetValue = 0f,
                                                animationSpec = spring()
                                            ) { verticalOffset = it }
                                        }
                                        val scaleJob = launch {
                                            animateFloat(
                                                initialValue = cardScale,
                                                targetValue = 1f,
                                                animationSpec = spring()
                                            ) { cardScale = it }
                                        }
                                        val alphaJob = launch {
                                            animateFloat(
                                                initialValue = cardAlpha,
                                                targetValue = 1f,
                                                animationSpec = spring()
                                            ) { cardAlpha = it }
                                        }
                                        xJob.join()
                                        yJob.join()
                                        scaleJob.join()
                                        alphaJob.join()
                                    }
                                    clearDragState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()

                                    totalDragX += dragAmount.x
                                    totalDragY += dragAmount.y

                                    if (lockedAxis == null) {
                                        val absX = abs(totalDragX)
                                        val absY = abs(totalDragY)
                                        if (absX > 15f || absY > 15f) {
                                            lockedAxis = if (absX > absY) GestureAxis.HORIZONTAL else GestureAxis.VERTICAL
                                            gestureAxis = lockedAxis
                                        }
                                    }

                                    when (lockedAxis) {
                                        GestureAxis.HORIZONTAL -> {
                                            horizontalOffset = totalDragX
                                            verticalOffset = 0f
                                            cardScale = 1f
                                            cardAlpha = 1f
                                        }
                                        GestureAxis.VERTICAL -> {
                                            horizontalOffset = 0f
                                            verticalOffset = totalDragY
                                            val safeHeight = cardHeightPx.takeIf { it > 0f } ?: 1f
                                            val progress = (abs(verticalOffset) / safeHeight).coerceIn(0f, 1f)
                                            cardScale = 1f - progress * 0.5f
                                            cardAlpha = 1f - progress * 0.8f
                                        }
                                        null -> Unit
                                    }
                                }
                            )
                        }
                ) {
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
                                        val step = cardWidthPx + gapPx
                                        translationX = when {
                                            // 上滑：下一张从右侧滑入中间
                                            offset == 1 && swipeUpProgress > 0f ->
                                                step * (1f - swipeUpProgress)
                                            // 下滑撤销：之前那张从中间让位到右侧
                                            offset == 1 && isUndoAnimating ->
                                                step * swipeDownProgress
                                            else ->
                                                horizontalOffset + offset * step
                                        }
                                        if (offset == 0) {
                                            translationY = verticalOffset
                                            scaleX = cardScale
                                            scaleY = cardScale
                                            alpha = cardAlpha
                                        }
                                    }
                            )
                        }
                    }
                }

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
        Text(
            text = currentPhoto?.let {
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
                    .format(Date(it.dateAdded * 1000))
            } ?: "",
            color = LightGrayText,
            fontSize = 13.sp,
            modifier = Modifier.weight(1f)
        )

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
        if (totalCount > 0) {
            Text(
                text = "${currentIndex + 1} / $totalCount",
                color = LightGrayText,
                fontSize = 12.sp,
                modifier = Modifier.padding(bottom = 6.dp)
            )
        }
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
