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
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.yihua.app.data.Photo
import com.yihua.app.ui.theme.AppleSystemGray6
import com.yihua.app.ui.theme.LightGrayText
import com.yihua.app.ui.theme.SwipeUpColor
import com.yihua.app.ui.theme.ThumbnailHighlight
import com.yihua.app.ui.theme.TrashBadgeColor
import com.yihua.app.viewmodel.PhotoListState
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
    // API 34+ 用户可选"部分照片"授权：READ_MEDIA_IMAGES 仍为 denied，
    // 但 READ_MEDIA_VISUAL_USER_SELECTED 为 granted。两者都要检查。
    val permissionsList = remember {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES,
                android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
            )
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
                android.Manifest.permission.READ_MEDIA_IMAGES
            )
            else -> listOf(android.Manifest.permission.READ_EXTERNAL_STORAGE)
        }
    }
    val permsState = rememberMultiplePermissionsState(permissionsList)

    val hasFullAccess = permsState.permissions.any {
        it.permission in listOf(
            android.Manifest.permission.READ_MEDIA_IMAGES,
            android.Manifest.permission.READ_EXTERNAL_STORAGE
        ) && it.status.isGranted
    }
    val hasPartialAccess = permsState.permissions.any {
        it.permission == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED &&
            it.status.isGranted
    }
    val hasAnyAccess = hasFullAccess || hasPartialAccess

    LaunchedEffect(hasAnyAccess) {
        if (hasAnyAccess) viewModel.loadPhotos()
    }

    when {
        hasAnyAccess -> PhotoContent(
            viewModel = viewModel,
            onNavigateToConfirm = onNavigateToConfirm,
            isPartialAccess = hasPartialAccess && !hasFullAccess
        )
        permsState.shouldShowRationale -> PermissionScreen(
            message = "一划需要读取您的照片，才能帮您整理相册。",
            buttonText = "授权访问",
            onRequest = { permsState.launchMultiplePermissionRequest() }
        )
        else -> {
            LaunchedEffect(Unit) { permsState.launchMultiplePermissionRequest() }
            PermissionScreen(
                message = "请在弹窗中授权访问相册，让一划帮您轻松整理照片。",
                buttonText = "重新申请授权",
                onRequest = { permsState.launchMultiplePermissionRequest() }
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
            Text(text = "📷", fontSize = 64.sp)
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
    onNavigateToConfirm: () -> Unit,
    isPartialAccess: Boolean = false
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()

    // --- 动画状态 ---
    var horizontalOffset by remember { mutableFloatStateOf(0f) }
    var verticalOffset by remember { mutableFloatStateOf(0f) }
    var cardScale by remember { mutableFloatStateOf(1f) }
    var cardAlpha by remember { mutableFloatStateOf(1f) }
    // 上滑时：下一张从右侧滑入的进度 0→1
    var swipeUpProgress by remember { mutableFloatStateOf(0f) }
    // 下滑撤销时：被"推走"那张向右滑出的进度 0→1
    var swipeDownProgress by remember { mutableFloatStateOf(0f) }
    // 下滑预览时：当前卡片向右偏移量（拖拽中实时更新）
    var currentCardRightShift by remember { mutableFloatStateOf(0f) }

    var gestureAxis by remember { mutableStateOf<GestureAxis?>(null) }
    var isUndoAnimating by remember { mutableStateOf(false) }
    var isAnimating by remember { mutableStateOf(false) }

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
        currentCardRightShift = 0f
    }

    // 当前卡片切换时重置变换（用照片 id 做 key，避免同 index 不同照片不触发）
    LaunchedEffect(state.currentPhoto?.id) {
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
        when (state.screenState) {
            PhotoListState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LightGrayText)
                }
            }
            PhotoListState.EmptyLibrary -> {
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
            PhotoListState.AllQueuedForDelete -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.padding(32.dp)
                    ) {
                        Text("🗑️", fontSize = 64.sp)
                        Spacer(Modifier.height(16.dp))
                        Text(
                            "所有照片已加入待删除队列",
                            color = Color(0xFF1C1C1E),
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "共 ${state.deleteQueue.size} 张，确认后将永久删除",
                            color = LightGrayText,
                            fontSize = 14.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(24.dp))
                        Button(
                            onClick = onNavigateToConfirm,
                            colors = ButtonDefaults.buttonColors(containerColor = SwipeUpColor)
                        ) {
                            Icon(Icons.Default.Delete, contentDescription = null)
                            Spacer(Modifier.width(8.dp))
                            Text("前往确认删除", fontWeight = FontWeight.Medium)
                        }
                    }
                }
            }
            PhotoListState.Reviewable -> {
                if (isPartialAccess) {
                    // Android 14+ 用户选择了"部分照片"，仅显示已授权的照片
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color(0xFFFFF3CD))
                            .padding(horizontal = 16.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = "仅显示已授权的照片，如需完整相册请在系统设置中授权",
                            color = Color(0xFF856404),
                            fontSize = 12.sp
                        )
                    }
                }

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
                        .pointerInput(state.currentIndex, state.visiblePhotos.size) {
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
                                    if (!isAnimating) {
                                        isAnimating = true
                                        scope.launch {
                                            try {
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
                                                            x < -threshold && state.currentIndex < state.visiblePhotos.size - 1 -> {
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
                                                        val y = totalDragY
                                                        when {
                                                            // 上滑：删除
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
                                                                val upProgressJob = launch {
                                                                    animateFloat(0f, 1f, spec) { swipeUpProgress = it }
                                                                }
                                                                yJob.join(); scaleJob.join(); alphaJob.join(); upProgressJob.join()
                                                                viewModel.swipeUp()
                                                                resetCardTransform()
                                                            }

                                                            // 下滑：撤销删除（仅当位置匹配时）
                                                            y > threshold && state.canSwipeDownToUndo -> {
                                                                isUndoAnimating = true
                                                                currentCardRightShift = 0f
                                                                val didUndo = viewModel.undoDelete()
                                                                if (didUndo) {
                                                                    // 恢复照片从顶部飞入；被推走那张向右让位
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
                                                                    val downProgressJob = launch {
                                                                        animateFloat(0f, 1f, spec) { swipeDownProgress = it }
                                                                    }
                                                                    yJob.join(); scaleJob.join(); alphaJob.join(); downProgressJob.join()
                                                                }
                                                                isUndoAnimating = false
                                                                resetCardTransform()
                                                            }

                                                            // 其余情况：弹回
                                                            else -> {
                                                                val shiftJob = launch {
                                                                    animateFloat(
                                                                        initialValue = currentCardRightShift,
                                                                        targetValue = 0f,
                                                                        animationSpec = spring(dampingRatio = Spring.DampingRatioLowBouncy)
                                                                    ) { currentCardRightShift = it }
                                                                }
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
                                                                shiftJob.join(); yJob.join(); scaleJob.join(); alphaJob.join()
                                                            }
                                                        }
                                                    }

                                                    null -> resetCardTransform()
                                                }
                                            } finally {
                                                isAnimating = false
                                            }
                                        }
                                    }
                                    clearDragState()
                                },
                                onDragCancel = {
                                    if (!isAnimating) {
                                        isAnimating = true
                                        scope.launch {
                                            try {
                                                val shiftJob = launch {
                                                    animateFloat(currentCardRightShift, 0f, spring()) { currentCardRightShift = it }
                                                }
                                                val xJob = launch {
                                                    animateFloat(horizontalOffset, 0f, spring()) { horizontalOffset = it }
                                                }
                                                val yJob = launch {
                                                    animateFloat(verticalOffset, 0f, spring()) { verticalOffset = it }
                                                }
                                                val scaleJob = launch {
                                                    animateFloat(cardScale, 1f, spring()) { cardScale = it }
                                                }
                                                val alphaJob = launch {
                                                    animateFloat(cardAlpha, 1f, spring()) { cardAlpha = it }
                                                }
                                                shiftJob.join(); xJob.join(); yJob.join(); scaleJob.join(); alphaJob.join()
                                            } finally {
                                                isAnimating = false
                                            }
                                        }
                                    }
                                    clearDragState()
                                },
                                onDrag = { change, dragAmount ->
                                    change.consume()
                                    if (!isAnimating) {
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
                                                currentCardRightShift = 0f
                                                cardScale = 1f
                                                cardAlpha = 1f
                                            }
                                            GestureAxis.VERTICAL -> {
                                                horizontalOffset = 0f
                                                if (totalDragY < 0) {
                                                    // 上滑：缩放淡出预览
                                                    verticalOffset = totalDragY
                                                    currentCardRightShift = 0f
                                                    val safeHeight = cardHeightPx.takeIf { it > 0f } ?: 1f
                                                    val progress = (-totalDragY / safeHeight).coerceIn(0f, 1f)
                                                    cardScale = 1f - progress * 0.5f
                                                    cardAlpha = 1f - progress * 0.8f
                                                } else {
                                                    // 下滑：当前卡片右移预览（仅当可撤销时才有视觉反馈）
                                                    verticalOffset = 0f
                                                    cardScale = 1f
                                                    cardAlpha = 1f
                                                    currentCardRightShift = if (state.canSwipeDownToUndo) {
                                                        (totalDragY * 0.4f).coerceAtLeast(0f)
                                                    } else 0f
                                                }
                                            }
                                            null -> Unit
                                        }
                                    }
                                }
                            )
                        }
                ) {
                    // 渲染前一张、当前、后一张（基于 visiblePhotos）
                    listOf(-1, 0, 1).forEach { offset ->
                        val index = state.currentIndex + offset
                        val photo = state.visiblePhotos.getOrNull(index)
                        if (photo != null) {
                            PhotoCard(
                                photo = photo,
                                modifier = Modifier
                                    .fillMaxSize()
                                    .graphicsLayer {
                                        val step = cardWidthPx + gapPx
                                        translationX = when {
                                            // 上滑：下一张从右侧滑入中间
                                            offset == 1 && swipeUpProgress > 0f ->
                                                step * (1f - swipeUpProgress)
                                            // 下滑撤销动画：被推走那张（原当前）从中间移到右侧
                                            offset == 1 && isUndoAnimating ->
                                                step * swipeDownProgress
                                            // 当前卡片：叠加右移预览偏移
                                            offset == 0 ->
                                                horizontalOffset + currentCardRightShift
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
                    photos = state.visiblePhotos,
                    currentIndex = state.currentIndex,
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
    }
}

@Composable
private fun BottomSection(
    photos: List<Photo>,
    currentIndex: Int,
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
        if (photos.isNotEmpty()) {
            Text(
                text = "${currentIndex + 1} / ${photos.size}",
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
