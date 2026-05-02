package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.animateFloatAsState
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

private enum class GestureAxis { Horizontal, Vertical }

private enum class OverlayMotion {
    DeleteUp,
    RestoreMoveRight
}

private enum class EntryMotion {
    None,
    FromRight,
    FromTop
}

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
                    onSwipeLeft = viewModel::swipeLeft,
                    onSwipeRight = viewModel::swipeRight,
                    onSwipeUp = viewModel::swipeUp,
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
    onSwipeLeft: () -> Unit,
    onSwipeRight: () -> Unit,
    onSwipeUp: () -> Unit,
    onSwipeDownUndo: () -> Boolean
) {
    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var gestureAxis by remember { mutableStateOf<GestureAxis?>(null) }
    var handledGesture by remember { mutableStateOf(false) }

    var stageWidth by remember { mutableFloatStateOf(0f) }
    var stageHeight by remember { mutableFloatStateOf(0f) }
    var pageSettling by remember { mutableStateOf(false) }
    var pageTarget by remember { mutableFloatStateOf(0f) }
    var pendingPageMove by remember { mutableStateOf<Int?>(null) }

    var overlayPhoto by remember { mutableStateOf<Photo?>(null) }
    var overlayMotion by remember { mutableStateOf<OverlayMotion?>(null) }
    var overlayTarget by remember { mutableFloatStateOf(0f) }

    var entryMotion by remember { mutableStateOf(EntryMotion.None) }
    var cardEntered by remember(state.currentPhoto?.id) { mutableStateOf(false) }

    val pageProgress by animateFloatAsState(
        targetValue = pageTarget,
        animationSpec = tween(durationMillis = 220),
        label = "photo-page-snap",
        finishedListener = {
            val move = pendingPageMove
            if (pageSettling && move != null) {
                if (move < 0) onSwipeRight() else onSwipeLeft()
            }
            pageSettling = false
            pendingPageMove = null
            pageTarget = 0f
            dragX = 0f
        }
    )

    val overlayProgress by animateFloatAsState(
        targetValue = overlayTarget,
        animationSpec = tween(durationMillis = 200),
        label = "photo-overlay",
        finishedListener = { value ->
            if (value >= 1f) {
                overlayPhoto = null
                overlayMotion = null
                overlayTarget = 0f
            }
        }
    )

    val entryProgress by animateFloatAsState(
        targetValue = if (cardEntered) 1f else 0f,
        animationSpec = tween(durationMillis = 170),
        label = "photo-entry",
        finishedListener = { value ->
            if (value >= 1f) entryMotion = EntryMotion.None
        }
    )

    fun resetDrag() {
        dragX = 0f
        dragY = 0f
        gestureAxis = null
        handledGesture = false
    }

    fun startOverlay(photo: Photo, motion: OverlayMotion) {
        overlayPhoto = photo
        overlayMotion = motion
        overlayTarget = 0f
        overlayTarget = 1f
    }

    fun settleBack() {
        pageSettling = true
        pendingPageMove = null
        pageTarget = 0f
    }

    fun settleToPage(direction: Int) {
        pageSettling = true
        pendingPageMove = direction
        pageTarget = direction * (stageWidth.takeIf { it > 0f } ?: 1f)
    }

    LaunchedEffect(state.currentPhoto?.id) {
        resetDrag()
        pageSettling = false
        pendingPageMove = null
        pageTarget = 0f
        cardEntered = true
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
            .pointerInput(state.currentPhoto?.id, state.visiblePhotos.size, state.canSwipeDownToUndo) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        gestureAxis = null
                        handledGesture = false
                    },
                    onDragCancel = {
                        if (gestureAxis == GestureAxis.Horizontal) settleBack() else resetDrag()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (handledGesture || pageSettling) return@detectDragGestures

                        totalX += dragAmount.x
                        totalY += dragAmount.y

                        if (gestureAxis == null) {
                            val absX = abs(totalX)
                            val absY = abs(totalY)
                            if (absX > 10f || absY > 10f) {
                                gestureAxis = if (absX > absY * 1.15f) GestureAxis.Horizontal else GestureAxis.Vertical
                            }
                        }

                        when (gestureAxis) {
                            GestureAxis.Horizontal -> {
                                val canMoveRight = state.currentIndex > 0
                                val canMoveLeft = state.currentIndex < state.visiblePhotos.lastIndex
                                dragX = when {
                                    totalX > 0f && !canMoveRight -> totalX * 0.28f
                                    totalX < 0f && !canMoveLeft -> totalX * 0.28f
                                    else -> totalX
                                }
                                dragY = 0f
                            }
                            GestureAxis.Vertical -> {
                                dragX = 0f
                                dragY = totalY

                                val absX = abs(totalX)
                                val absY = abs(totalY)
                                val trigger = 72f

                                // 业务状态先发生：上滑一旦被接受，立刻进入待删除队列。
                                if (totalY < -trigger && absY > absX * 1.15f) {
                                    val exiting = state.currentPhoto
                                    handledGesture = true
                                    if (exiting != null) {
                                        startOverlay(exiting, OverlayMotion.DeleteUp)
                                        entryMotion = EntryMotion.FromRight
                                    }
                                    onSwipeUp()
                                    resetDrag()
                                    return@detectDragGestures
                                }
                            }
                            null -> Unit
                        }
                    },
                    onDragEnd = {
                        if (!handledGesture && !pageSettling) {
                            val absX = abs(totalX)
                            val absY = abs(totalY)
                            val trigger = 72f
                            when (gestureAxis) {
                                GestureAxis.Horizontal -> {
                                    val pageWidth = stageWidth.takeIf { it > 0f } ?: 1f
                                    val shouldPage = abs(dragX) > minOf(pageWidth * 0.22f, 120f)
                                    when {
                                        shouldPage && dragX < 0f && state.currentIndex < state.visiblePhotos.lastIndex -> settleToPage(-1)
                                        shouldPage && dragX > 0f && state.currentIndex > 0 -> settleToPage(1)
                                        else -> settleBack()
                                    }
                                }
                                GestureAxis.Vertical -> {
                                    when {
                                        totalY < -trigger && absY > absX * 1.15f -> {
                                            val exiting = state.currentPhoto
                                            if (exiting != null) {
                                                startOverlay(exiting, OverlayMotion.DeleteUp)
                                                entryMotion = EntryMotion.FromRight
                                            }
                                            onSwipeUp()
                                            resetDrag()
                                        }
                                        totalY > trigger && absY > absX * 1.15f && state.canSwipeDownToUndo -> {
                                            val displaced = state.currentPhoto
                                            val didUndo = onSwipeDownUndo()
                                            if (didUndo && displaced != null) {
                                                startOverlay(displaced, OverlayMotion.RestoreMoveRight)
                                                entryMotion = EntryMotion.FromTop
                                            }
                                            resetDrag()
                                        }
                                        else -> resetDrag()
                                    }
                                }
                                null -> resetDrag()
                            }
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val pageOffset = if (pageSettling) pageProgress else dragX
        listOf(-1, 0, 1).forEach { offset ->
            val index = state.currentIndex + offset
            val photo = state.visiblePhotos.getOrNull(index)
            if (photo != null) {
                PhotoCard(
                    photo = photo,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val pageStep = stageWidth.takeIf { it > 0f } ?: size.width
                            val baseX = offset * pageStep + pageOffset
                            val isCurrent = offset == 0
                            val clampedY = dragY.coerceIn(-240f, 140f)
                            val deleteProgress = if (isCurrent) (-clampedY / 220f).coerceIn(0f, 1f) else 0f
                            val enterScale = if (isCurrent) 0.98f + entryProgress * 0.02f else 1f
                            val dragScale = if (isCurrent) 1f - deleteProgress * 0.16f else 1f
                            val entryX = if (isCurrent && entryMotion == EntryMotion.FromRight) {
                                (1f - entryProgress) * pageStep * 0.28f
                            } else 0f
                            val entryY = when {
                                isCurrent && entryMotion == EntryMotion.FromTop -> -(1f - entryProgress) * stageHeight * 0.28f
                                isCurrent && entryMotion != EntryMotion.None -> (1f - entryProgress) * 10f
                                else -> 0f
                            }

                            translationX = baseX + entryX
                            translationY = if (isCurrent) clampedY + entryY else 0f
                            rotationZ = 0f
                            scaleX = enterScale * dragScale
                            scaleY = enterScale * dragScale
                            alpha = if (isCurrent) {
                                (0.78f + entryProgress * 0.22f) * (1f - deleteProgress * 0.32f)
                            } else {
                                1f
                            }
                        }
                )
            }
        }

        overlayPhoto?.let { photo ->
            PhotoCard(
                photo = photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (overlayMotion) {
                            OverlayMotion.DeleteUp -> {
                                val flyDistance = (stageHeight.takeIf { it > 0f } ?: size.height) * 0.85f
                                translationY = -overlayProgress * flyDistance
                                scaleX = 1f - overlayProgress * 0.18f
                                scaleY = 1f - overlayProgress * 0.18f
                                alpha = 1f - overlayProgress
                            }
                            OverlayMotion.RestoreMoveRight -> {
                                val moveDistance = (stageWidth.takeIf { it > 0f } ?: size.width) * 0.38f
                                translationX = overlayProgress * moveDistance
                                rotationZ = overlayProgress * 3f
                                scaleX = 1f - overlayProgress * 0.04f
                                scaleY = 1f - overlayProgress * 0.04f
                                alpha = 1f - overlayProgress * 0.55f
                            }
                            null -> Unit
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
