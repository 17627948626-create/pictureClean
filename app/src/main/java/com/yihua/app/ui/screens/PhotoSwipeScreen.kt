package com.yihua.app.ui.screens

import android.os.Build
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
    var handledGesture by remember { mutableStateOf(false) }

    val motionSpec = spring<Float>(
        dampingRatio = Spring.DampingRatioNoBouncy,
        stiffness = Spring.StiffnessMediumLow
    )
    val animatedDragX by animateFloatAsState(
        targetValue = dragX,
        animationSpec = motionSpec,
        label = "photo-drag-x"
    )
    val animatedDragY by animateFloatAsState(
        targetValue = dragY,
        animationSpec = motionSpec,
        label = "photo-drag-y"
    )

    fun resetDrag() {
        dragX = 0f
        dragY = 0f
        handledGesture = false
    }

    LaunchedEffect(state.currentPhoto?.id) {
        resetDrag()
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .pointerInput(state.currentPhoto?.id, state.visiblePhotos.size) {
                var totalX = 0f
                var totalY = 0f
                detectDragGestures(
                    onDragStart = {
                        totalX = 0f
                        totalY = 0f
                        handledGesture = false
                    },
                    onDragCancel = { resetDrag() },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (handledGesture) return@detectDragGestures

                        totalX += dragAmount.x
                        totalY += dragAmount.y
                        dragX = totalX
                        dragY = totalY

                        val absX = abs(totalX)
                        val absY = abs(totalY)
                        val trigger = 72f

                        // 关键修复：一旦上滑超过阈值，立即更新业务状态。
                        // 不等待动画、不等待手指抬起，避免 pointerInput 取消/动画中断导致没入队。
                        if (totalY < -trigger && absY > absX * 1.15f) {
                            handledGesture = true
                            onSwipeUp()
                            resetDrag()
                            return@detectDragGestures
                        }
                    },
                    onDragEnd = {
                        if (!handledGesture) {
                            val absX = abs(totalX)
                            val absY = abs(totalY)
                            val trigger = 72f
                            when {
                                totalY < -trigger && absY > absX * 1.15f -> onSwipeUp()
                                totalY > trigger && absY > absX * 1.15f && state.canSwipeDownToUndo -> onSwipeDownUndo()
                                totalX > trigger && absX > absY * 1.15f -> onSwipeLeft()
                                totalX < -trigger && absX > absY * 1.15f -> onSwipeRight()
                            }
                        }
                        resetDrag()
                    }
                )
            },
        contentAlignment = Alignment.Center
    ) {
        state.currentPhoto?.let { photo ->
            PhotoCard(
                photo = photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        translationX = animatedDragX.coerceIn(-160f, 160f)
                        translationY = animatedDragY.coerceIn(-220f, 120f)
                        val progress = (-animatedDragY / 360f).coerceIn(0f, 1f)
                        scaleX = 1f - progress * 0.18f
                        scaleY = 1f - progress * 0.18f
                        alpha = 1f - progress * 0.35f
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
