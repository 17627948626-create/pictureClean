package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
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
import com.yihua.app.viewmodel.PhotoUiState
import com.yihua.app.viewmodel.PhotoViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private const val SwipeTriggerPx = 72f
private const val DirectionLockPx = 10f
private const val DirectionRatio = 1.15f

private enum class GestureDirection {
    Left,
    Right,
    Up,
    Down
}

private enum class OverlayMotion {
    FlyLeft,
    FlyUp,
    EnterFromLeft,
    EnterFromTop
}

private data class OverlayCardState(
    val photo: Photo,
    val motion: OverlayMotion,
    val startX: Float = 0f,
    val startY: Float = 0f
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel,
    onNavigateToConfirm: () -> Unit
) {
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
        it.permission == android.Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED && it.status.isGranted
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
    isPartialAccess: Boolean
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

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
                if (isPartialAccess) PartialAccessBanner()

                TopBar(
                    currentPhoto = state.currentPhoto,
                    deleteQueueSize = state.deleteQueue.size,
                    onTrashClick = onNavigateToConfirm
                )

                SwipeStage(
                    state = state,
                    onSwipeLeftToNext = viewModel::swipeRight,
                    onSwipeRightToPrevious = viewModel::swipeLeft,
                    onSwipeUpToDelete = viewModel::swipeUp,
                    onSwipeDownUndo = { viewModel.undoDelete() }
                )

                BottomSection(
                    photos = state.visiblePhotos,
                    currentIndex = state.currentIndex,
                    onThumbnailClick = viewModel::goToIndex
                )
            }
        }
    }
}

@Composable
private fun PartialAccessBanner() {
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

@Composable
private fun ColumnScope.SwipeStage(
    state: PhotoUiState,
    onSwipeLeftToNext: () -> Unit,
    onSwipeRightToPrevious: () -> Unit,
    onSwipeUpToDelete: () -> Unit,
    onSwipeDownUndo: () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val overlayProgress = remember { Animatable(0f) }

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var gestureDirection by remember { mutableStateOf<GestureDirection?>(null) }
    var handledGesture by remember { mutableStateOf(false) }
    var animationRunning by remember { mutableStateOf(false) }

    var stageWidth by remember { mutableFloatStateOf(0f) }
    var stageHeight by remember { mutableFloatStateOf(0f) }
    var overlayCard by remember { mutableStateOf<OverlayCardState?>(null) }
    var baseOverridePhoto by remember { mutableStateOf<Photo?>(null) }

    fun resetDrag() {
        dragX = 0f
        dragY = 0f
        gestureDirection = null
        handledGesture = false
    }

    fun lockDirection(totalX: Float, totalY: Float): GestureDirection? {
        val absX = abs(totalX)
        val absY = abs(totalY)
        if (absX <= DirectionLockPx && absY <= DirectionLockPx) return null
        return when {
            absX > absY * DirectionRatio && totalX < 0f -> GestureDirection.Left
            absX > absY * DirectionRatio && totalX > 0f -> GestureDirection.Right
            absY > absX * DirectionRatio && totalY < 0f -> GestureDirection.Up
            absY > absX * DirectionRatio && totalY > 0f -> GestureDirection.Down
            else -> null
        }
    }

    fun springBack() {
        val fromX = dragX
        val fromY = dragY
        scope.launch {
            animationRunning = true
            val xAnim = Animatable(fromX)
            val yAnim = Animatable(fromY)
            launch {
                xAnim.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { dragX = value }
            }
            yAnim.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { dragY = value }
            resetDrag()
            animationRunning = false
        }
    }

    fun startOutgoing(
        photo: Photo,
        motion: OverlayMotion,
        startX: Float,
        startY: Float,
        commitState: () -> Unit
    ) {
        scope.launch {
            animationRunning = true
            handledGesture = true
            overlayProgress.snapTo(0f)
            overlayCard = OverlayCardState(
                photo = photo,
                motion = motion,
                startX = startX,
                startY = startY
            )
            commitState()
            resetDrag()
            overlayProgress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 150,
                    easing = FastOutLinearInEasing
                )
            )
            overlayCard = null
            baseOverridePhoto = null
            overlayProgress.snapTo(0f)
            animationRunning = false
        }
    }

    fun startCoverIn(
        incomingPhoto: Photo,
        oldBasePhoto: Photo?,
        motion: OverlayMotion,
        commitState: () -> Boolean
    ) {
        scope.launch {
            animationRunning = true
            handledGesture = true
            overlayProgress.snapTo(0f)
            baseOverridePhoto = oldBasePhoto
            overlayCard = OverlayCardState(
                photo = incomingPhoto,
                motion = motion
            )
            val committed = commitState()
            if (committed) {
                resetDrag()
                overlayProgress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
            overlayCard = null
            baseOverridePhoto = null
            overlayProgress.snapTo(0f)
            animationRunning = false
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        if (!animationRunning) {
            resetDrag()
            overlayCard = null
            baseOverridePhoto = null
            overlayProgress.snapTo(0f)
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .onSizeChanged {
                stageWidth = it.width.toFloat()
                stageHeight = it.height.toFloat()
            }
            .pointerInput(
                state.currentPhoto?.id,
                state.currentIndex,
                state.visiblePhotos.size,
                state.canSwipeDownToUndo
            ) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        gestureDirection = null
                        handledGesture = false
                    },
                    onDragCancel = {
                        if (!handledGesture && (dragX != 0f || dragY != 0f)) springBack() else resetDrag()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (handledGesture || animationRunning) return@detectDragGestures

                        totalX += dragAmount.x
                        totalY += dragAmount.y

                        if (gestureDirection == null) {
                            gestureDirection = lockDirection(totalX, totalY)
                        }

                        when (gestureDirection) {
                            GestureDirection.Left -> {
                                dragX = if (state.currentIndex < state.visiblePhotos.lastIndex) totalX.coerceAtMost(0f) else 0f
                                dragY = 0f
                            }
                            GestureDirection.Up -> {
                                dragX = 0f
                                dragY = totalY.coerceAtMost(0f)
                            }
                            GestureDirection.Right,
                            GestureDirection.Down,
                            null -> {
                                dragX = 0f
                                dragY = 0f
                            }
                        }
                    },
                    onDragEnd = {
                        if (handledGesture || animationRunning) return@detectDragGestures

                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        val currentPhoto = state.currentPhoto

                        when (gestureDirection) {
                            GestureDirection.Left -> {
                                val canGoNext = state.currentIndex < state.visiblePhotos.lastIndex
                                val accepted = canGoNext && totalX < -SwipeTriggerPx && absX > absY * DirectionRatio
                                if (accepted && currentPhoto != null) {
                                    startOutgoing(
                                        photo = currentPhoto,
                                        motion = OverlayMotion.FlyLeft,
                                        startX = dragX,
                                        startY = 0f,
                                        commitState = onSwipeLeftToNext
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Up -> {
                                val accepted = totalY < -SwipeTriggerPx && absY > absX * DirectionRatio
                                if (accepted && currentPhoto != null) {
                                    startOutgoing(
                                        photo = currentPhoto,
                                        motion = OverlayMotion.FlyUp,
                                        startX = 0f,
                                        startY = dragY,
                                        commitState = onSwipeUpToDelete
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Right -> {
                                val canGoPrevious = state.currentIndex > 0
                                val incomingPhoto = state.visiblePhotos.getOrNull(state.currentIndex - 1)
                                val accepted = canGoPrevious && totalX > SwipeTriggerPx && absX > absY * DirectionRatio
                                if (accepted && incomingPhoto != null) {
                                    startCoverIn(
                                        incomingPhoto = incomingPhoto,
                                        oldBasePhoto = currentPhoto,
                                        motion = OverlayMotion.EnterFromLeft,
                                        commitState = {
                                            onSwipeRightToPrevious()
                                            true
                                        }
                                    )
                                } else {
                                    resetDrag()
                                }
                            }
                            GestureDirection.Down -> {
                                val incomingPhoto = state.deleteHistory.lastOrNull()?.photo
                                val accepted = state.canSwipeDownToUndo && totalY > SwipeTriggerPx && absY > absX * DirectionRatio
                                if (accepted && incomingPhoto != null) {
                                    startCoverIn(
                                        incomingPhoto = incomingPhoto,
                                        oldBasePhoto = currentPhoto,
                                        motion = OverlayMotion.EnterFromTop,
                                        commitState = onSwipeDownUndo
                                    )
                                } else {
                                    resetDrag()
                                }
                            }
                            null -> resetDrag()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val displayPhoto = baseOverridePhoto ?: state.currentPhoto
        val clampedDragY = dragY.coerceAtMost(0f)
        val deletePreviewProgress = (-clampedDragY / 220f).coerceIn(0f, 1f)
        val currentScale = 1f - deletePreviewProgress * 0.15f

        displayPhoto?.let { photo ->
            PhotoCard(
                photo = photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragX
                        translationY = clampedDragY
                        scaleX = currentScale
                        scaleY = currentScale
                    }
            )
        }

        overlayCard?.let { card ->
            PhotoCard(
                photo = card.photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val width = stageWidth.takeIf { it > 0f } ?: size.width
                        val height = stageHeight.takeIf { it > 0f } ?: size.height
                        val progress = overlayProgress.value
                        when (card.motion) {
                            OverlayMotion.FlyLeft -> {
                                val endX = -width * 1.1f
                                translationX = card.startX + (endX - card.startX) * progress
                                translationY = card.startY
                            }
                            OverlayMotion.FlyUp -> {
                                val endY = -height * 1.1f
                                translationX = card.startX
                                translationY = card.startY + (endY - card.startY) * progress
                            }
                            OverlayMotion.EnterFromLeft -> {
                                translationX = -width * (1f - progress)
                                translationY = 0f
                            }
                            OverlayMotion.EnterFromTop -> {
                                translationX = 0f
                                translationY = -height * (1f - progress)
                            }
                        }
                    }
            )
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
                SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()).format(Date(it.dateAdded * 1000))
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
