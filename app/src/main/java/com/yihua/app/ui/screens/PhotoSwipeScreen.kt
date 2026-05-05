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

private enum class GestureDirection { Left, Right, Up, Down }

private enum class CardMotion {
    FlyToLeft,
    FlyToTop,
    CoverFromLeft,
    CoverFromTop
}

private data class AnimatedCard(
    val photo: Photo,
    val motion: CardMotion,
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

            PhotoListState.EmptyLibrary -> EmptyLibraryState()
            PhotoListState.AllQueuedForDelete -> AllQueuedForDeleteState(
                deleteQueueSize = state.deleteQueue.size,
                onNavigateToConfirm = onNavigateToConfirm
            )

            PhotoListState.Reviewable -> {
                if (isPartialAccess) PartialAccessBanner()

                TopBar(
                    currentPhoto = state.currentPhoto,
                    deleteQueueSize = state.deleteQueue.size,
                    onTrashClick = onNavigateToConfirm
                )

                SwipeStage(
                    state = state,
                    onGoToNextPhoto = viewModel::goToNextPhoto,
                    onGoToPreviousPhoto = viewModel::goToPreviousPhoto,
                    onQueueCurrentPhotoForDeletion = viewModel::queueCurrentPhotoForDeletion,
                    onRestoreLastDeletedPhoto = viewModel::restoreLastDeletedPhoto
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
private fun EmptyLibraryState() {
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

@Composable
private fun AllQueuedForDeleteState(
    deleteQueueSize: Int,
    onNavigateToConfirm: () -> Unit
) {
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
                "共 $deleteQueueSize 张，确认后将永久删除",
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
    onGoToNextPhoto: () -> Unit,
    onGoToPreviousPhoto: () -> Unit,
    onQueueCurrentPhotoForDeletion: () -> Unit,
    onRestoreLastDeletedPhoto: () -> Boolean
) {
    val scope = rememberCoroutineScope()
    val progress = remember { Animatable(0f) }

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var gestureDirection by remember { mutableStateOf<GestureDirection?>(null) }
    var gestureHandled by remember { mutableStateOf(false) }
    var animationRunning by remember { mutableStateOf(false) }

    var stageWidth by remember { mutableFloatStateOf(0f) }
    var stageHeight by remember { mutableFloatStateOf(0f) }
    var animatedCard by remember { mutableStateOf<AnimatedCard?>(null) }
    var basePhotoDuringCoverIn by remember { mutableStateOf<Photo?>(null) }

    fun resetGesture() {
        dragX = 0f
        dragY = 0f
        gestureDirection = null
        gestureHandled = false
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
            val x = Animatable(fromX)
            val y = Animatable(fromY)
            launch {
                x.animateTo(
                    targetValue = 0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioNoBouncy,
                        stiffness = Spring.StiffnessMediumLow
                    )
                ) { dragX = value }
            }
            y.animateTo(
                targetValue = 0f,
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) { dragY = value }
            resetGesture()
            animationRunning = false
        }
    }

    fun flyCurrentCardOut(
        photo: Photo,
        motion: CardMotion,
        startX: Float,
        startY: Float,
        updateState: () -> Unit
    ) {
        scope.launch {
            animationRunning = true
            gestureHandled = true
            progress.snapTo(0f)
            animatedCard = AnimatedCard(
                photo = photo,
                motion = motion,
                startX = startX,
                startY = startY
            )
            updateState()
            resetGesture()
            progress.animateTo(
                targetValue = 1f,
                animationSpec = tween(
                    durationMillis = 150,
                    easing = FastOutLinearInEasing
                )
            )
            animatedCard = null
            basePhotoDuringCoverIn = null
            progress.snapTo(0f)
            animationRunning = false
        }
    }

    fun coverCurrentCard(
        incomingPhoto: Photo,
        currentPhoto: Photo?,
        motion: CardMotion,
        updateState: () -> Boolean
    ) {
        scope.launch {
            animationRunning = true
            gestureHandled = true
            progress.snapTo(0f)
            basePhotoDuringCoverIn = currentPhoto
            animatedCard = AnimatedCard(photo = incomingPhoto, motion = motion)
            val updated = updateState()
            if (updated) {
                resetGesture()
                progress.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(
                        durationMillis = 200,
                        easing = LinearOutSlowInEasing
                    )
                )
            }
            animatedCard = null
            basePhotoDuringCoverIn = null
            progress.snapTo(0f)
            animationRunning = false
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        if (!animationRunning) {
            resetGesture()
            animatedCard = null
            basePhotoDuringCoverIn = null
            progress.snapTo(0f)
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
                state.canRestoreLastDeletedPhoto
            ) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        gestureDirection = null
                        gestureHandled = false
                    },
                    onDragCancel = {
                        if (!gestureHandled && (dragX != 0f || dragY != 0f)) springBack() else resetGesture()
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (gestureHandled || animationRunning) return@detectDragGestures

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
                        if (gestureHandled || animationRunning) return@detectDragGestures

                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        val currentPhoto = state.currentPhoto

                        when (gestureDirection) {
                            GestureDirection.Left -> {
                                val accepted = state.currentIndex < state.visiblePhotos.lastIndex &&
                                    totalX < -SwipeTriggerPx &&
                                    absX > absY * DirectionRatio
                                if (accepted && currentPhoto != null) {
                                    flyCurrentCardOut(
                                        photo = currentPhoto,
                                        motion = CardMotion.FlyToLeft,
                                        startX = dragX,
                                        startY = 0f,
                                        updateState = onGoToNextPhoto
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Right -> {
                                val previousPhoto = state.visiblePhotos.getOrNull(state.currentIndex - 1)
                                val accepted = previousPhoto != null &&
                                    totalX > SwipeTriggerPx &&
                                    absX > absY * DirectionRatio
                                if (accepted) {
                                    coverCurrentCard(
                                        incomingPhoto = previousPhoto,
                                        currentPhoto = currentPhoto,
                                        motion = CardMotion.CoverFromLeft,
                                        updateState = {
                                            onGoToPreviousPhoto()
                                            true
                                        }
                                    )
                                } else {
                                    resetGesture()
                                }
                            }
                            GestureDirection.Up -> {
                                val accepted = totalY < -SwipeTriggerPx && absY > absX * DirectionRatio
                                if (accepted && currentPhoto != null) {
                                    flyCurrentCardOut(
                                        photo = currentPhoto,
                                        motion = CardMotion.FlyToTop,
                                        startX = 0f,
                                        startY = dragY,
                                        updateState = onQueueCurrentPhotoForDeletion
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Down -> {
                                val restoredPhoto = state.deleteHistory.lastOrNull()?.photo
                                val accepted = state.canRestoreLastDeletedPhoto &&
                                    restoredPhoto != null &&
                                    totalY > SwipeTriggerPx &&
                                    absY > absX * DirectionRatio
                                if (accepted) {
                                    coverCurrentCard(
                                        incomingPhoto = restoredPhoto,
                                        currentPhoto = currentPhoto,
                                        motion = CardMotion.CoverFromTop,
                                        updateState = onRestoreLastDeletedPhoto
                                    )
                                } else {
                                    resetGesture()
                                }
                            }
                            null -> resetGesture()
                        }
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        val basePhoto = basePhotoDuringCoverIn ?: state.currentPhoto
        val deletePreviewProgress = (-dragY.coerceAtMost(0f) / 220f).coerceIn(0f, 1f)
        val currentScale = 1f - deletePreviewProgress * 0.15f

        basePhoto?.let { photo ->
            PhotoCard(
                photo = photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = dragX
                        translationY = dragY.coerceAtMost(0f)
                        scaleX = currentScale
                        scaleY = currentScale
                    }
            )
        }

        animatedCard?.let { card ->
            PhotoCard(
                photo = card.photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val width = stageWidth.takeIf { it > 0f } ?: size.width
                        val height = stageHeight.takeIf { it > 0f } ?: size.height
                        when (card.motion) {
                            CardMotion.FlyToLeft -> {
                                val endX = -width * 1.1f
                                translationX = card.startX + (endX - card.startX) * progress.value
                                translationY = card.startY
                            }
                            CardMotion.FlyToTop -> {
                                val endY = -height * 1.1f
                                translationX = card.startX
                                translationY = card.startY + (endY - card.startY) * progress.value
                            }
                            CardMotion.CoverFromLeft -> {
                                translationX = -width * (1f - progress.value)
                                translationY = 0f
                            }
                            CardMotion.CoverFromTop -> {
                                translationX = 0f
                                translationY = -height * (1f - progress.value)
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
