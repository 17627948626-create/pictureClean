package com.yihua.app.ui.screens

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutLinearInEasing
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
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import coil.size.Precision
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.yihua.app.R
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
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch

private const val SwipeTriggerPx = 72f
private const val DirectionLockPx = 10f
private const val DirectionRatio = 1.15f

private enum class GestureDirection { Left, Right, Up, Down }

private data class ThrowSnapshot(
    val front: Photo?,
    val back: Photo?,
    val direction: GestureDirection
)

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun PhotoSwipeScreen(
    viewModel: PhotoViewModel,
    onNavigateToConfirm: () -> Unit
) {
    val context = LocalContext.current
    var hasRequestedPermissions by remember { mutableStateOf(false) }
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

    fun requestPermissions() {
        hasRequestedPermissions = true
        permsState.launchMultiplePermissionRequest()
    }

    fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", context.packageName, null)
        }
        context.startActivity(intent)
    }

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
            message = stringResource(R.string.permission_rationale),
            buttonText = stringResource(R.string.permission_grant),
            onRequest = ::requestPermissions
        )
        !hasRequestedPermissions -> {
            LaunchedEffect(Unit) { requestPermissions() }
            PermissionScreen(
                message = stringResource(R.string.permission_initial_request),
                buttonText = stringResource(R.string.permission_grant),
                onRequest = ::requestPermissions
            )
        }
        else -> PermissionScreen(
            message = stringResource(R.string.permission_denied_message),
            buttonText = stringResource(R.string.permission_retry),
            onRequest = ::requestPermissions,
            settingsButtonText = stringResource(R.string.permission_open_settings),
            onOpenSettings = ::openAppSettings
        )
    }
}

@Composable
private fun PermissionScreen(
    message: String,
    buttonText: String,
    onRequest: () -> Unit,
    settingsButtonText: String? = null,
    onOpenSettings: (() -> Unit)? = null
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
                text = stringResource(R.string.permission_screen_title),
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
            if (settingsButtonText != null && onOpenSettings != null) {
                Spacer(Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onOpenSettings,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(settingsButtonText, fontWeight = FontWeight.Medium)
                }
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
    var keepReviewStageForAnimation by remember { mutableStateOf(false) }
    var lastReviewableState by remember { mutableStateOf<PhotoUiState?>(null) }

    if (state.screenState == PhotoListState.Reviewable) {
        lastReviewableState = state
    }

    val stageState = when {
        state.screenState == PhotoListState.Reviewable -> state
        state.screenState == PhotoListState.AllQueuedForDelete && keepReviewStageForAnimation -> lastReviewableState
        else -> null
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppleSystemGray6)
            .statusBarsPadding()
    ) {
        when {
            state.screenState == PhotoListState.Loading -> {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = LightGrayText)
                }
            }

            state.screenState == PhotoListState.LoadFailed -> LoadFailedState(
                message = state.errorMessage ?: stringResource(R.string.load_failed_message),
                onRetry = viewModel::loadPhotos
            )

            state.screenState == PhotoListState.EmptyLibrary -> EmptyLibraryState()

            stageState != null -> ReviewablePhotoContent(
                state = stageState,
                deleteQueueSize = state.deleteQueue.size,
                isPartialAccess = isPartialAccess,
                onNavigateToConfirm = onNavigateToConfirm,
                onGoToNextPhoto = viewModel::goToNextPhoto,
                onGoToPreviousPhoto = viewModel::goToPreviousPhoto,
                onQueueCurrentPhotoForDeletion = viewModel::queueCurrentPhotoForDeletion,
                onRestoreLastDeletedPhoto = viewModel::restoreLastDeletedPhoto,
                onAnimationRunningChange = { keepReviewStageForAnimation = it },
                onThumbnailClick = viewModel::goToIndex
            )

            state.screenState == PhotoListState.AllQueuedForDelete -> AllQueuedForDeleteState(
                deleteQueueSize = state.deleteQueue.size,
                onNavigateToConfirm = onNavigateToConfirm
            )
        }
    }
}

@Composable
private fun ColumnScope.ReviewablePhotoContent(
    state: PhotoUiState,
    deleteQueueSize: Int,
    isPartialAccess: Boolean,
    onNavigateToConfirm: () -> Unit,
    onGoToNextPhoto: () -> Unit,
    onGoToPreviousPhoto: () -> Unit,
    onQueueCurrentPhotoForDeletion: () -> Unit,
    onRestoreLastDeletedPhoto: () -> Boolean,
    onAnimationRunningChange: (Boolean) -> Unit,
    onThumbnailClick: (Int) -> Unit
) {
    if (isPartialAccess) PartialAccessBanner()

    TopBar(
        currentPhoto = state.currentPhoto,
        deleteQueueSize = deleteQueueSize,
        onTrashClick = onNavigateToConfirm
    )

    SwipeStage(
        state = state,
        onGoToNextPhoto = onGoToNextPhoto,
        onGoToPreviousPhoto = onGoToPreviousPhoto,
        onQueueCurrentPhotoForDeletion = onQueueCurrentPhotoForDeletion,
        onRestoreLastDeletedPhoto = onRestoreLastDeletedPhoto,
        onAnimationRunningChange = onAnimationRunningChange
    )

    BottomSection(
        photos = state.visiblePhotos,
        currentIndex = state.currentIndex,
        onThumbnailClick = onThumbnailClick
    )
}

@Composable
private fun EmptyLibraryState() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 64.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_library_title),
                color = Color(0xFF1C1C1E),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun LoadFailedState(
    message: String,
    onRetry: () -> Unit
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(32.dp)
        ) {
            Text("⚠️", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.load_failed_title),
                color = Color(0xFF1C1C1E),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = message,
                color = LightGrayText,
                fontSize = 14.sp,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))
            Button(
                onClick = onRetry,
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1C1C1E),
                    contentColor = Color.White
                )
            ) {
                Text(stringResource(R.string.retry), fontWeight = FontWeight.Medium)
            }
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
                stringResource(R.string.all_queued_title),
                color = Color(0xFF1C1C1E),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                stringResource(R.string.all_queued_message, deleteQueueSize),
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
                Text(stringResource(R.string.go_to_delete_confirm), fontWeight = FontWeight.Medium)
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
            text = stringResource(R.string.partial_access_banner),
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
    onRestoreLastDeletedPhoto: () -> Boolean,
    onAnimationRunningChange: (Boolean) -> Unit
) {
    val scope = rememberCoroutineScope()

    var dragX by remember { mutableFloatStateOf(0f) }
    var dragY by remember { mutableFloatStateOf(0f) }
    var gestureDirection by remember { mutableStateOf<GestureDirection?>(null) }
    var gestureHandled by remember { mutableStateOf(false) }
    var animationRunning by remember { mutableStateOf(false) }
    var stageWidth by remember { mutableFloatStateOf(0f) }
    var stageHeight by remember { mutableFloatStateOf(0f) }
    var throwSnapshot by remember { mutableStateOf<ThrowSnapshot?>(null) }

    fun setAnimationRunning(value: Boolean) {
        animationRunning = value
        onAnimationRunningChange(value)
    }

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
            setAnimationRunning(true)
            try {
                val xAnim = Animatable(fromX)
                val yAnim = Animatable(fromY)
                val spec = spring<Float>(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
                val xJob = launch { xAnim.animateTo(0f, spec) { dragX = value } }
                val yJob = launch { yAnim.animateTo(0f, spec) { dragY = value } }
                joinAll(xJob, yJob)
            } finally {
                resetGesture()
                setAnimationRunning(false)
            }
        }
    }

    fun throwAccepted(
        direction: GestureDirection,
        front: Photo?,
        back: Photo?,
        updateState: () -> Unit
    ) {
        scope.launch {
            setAnimationRunning(true)
            gestureHandled = true
            throwSnapshot = ThrowSnapshot(front = front, back = back, direction = direction)
            val targetX = when (direction) {
                GestureDirection.Left -> -stageWidth
                GestureDirection.Right -> stageWidth
                GestureDirection.Up, GestureDirection.Down -> 0f
            }
            val targetY = when (direction) {
                GestureDirection.Up -> -stageHeight
                GestureDirection.Down -> stageHeight
                GestureDirection.Left, GestureDirection.Right -> 0f
            }
            try {
                val xAnim = Animatable(dragX)
                val yAnim = Animatable(dragY)
                val spec = tween<Float>(durationMillis = 220, easing = FastOutLinearInEasing)
                val xJob = launch { xAnim.animateTo(targetX, spec) { dragX = value } }
                val yJob = launch { yAnim.animateTo(targetY, spec) { dragY = value } }
                joinAll(xJob, yJob)
                updateState()
            } finally {
                dragX = 0f
                dragY = 0f
                gestureDirection = null
                gestureHandled = false
                throwSnapshot = null
                setAnimationRunning(false)
            }
        }
    }

    LaunchedEffect(state.currentPhoto?.id) {
        if (!animationRunning) {
            resetGesture()
            throwSnapshot = null
        }
    }

    // ── front / back photo resolution ─────────────────────────────────────
    val (front, back) = throwSnapshot?.let { snap ->
        snap.front to snap.back
    } ?: when (gestureDirection) {
        GestureDirection.Left ->
            state.currentPhoto to state.visiblePhotos.getOrNull(state.currentIndex + 1)
        GestureDirection.Up ->
            state.currentPhoto to (
                state.visiblePhotos.getOrNull(state.currentIndex + 1)
                    ?: state.visiblePhotos.getOrNull(state.currentIndex - 1)
            )
        GestureDirection.Right ->
            state.visiblePhotos.getOrNull(state.currentIndex - 1) to state.currentPhoto
        GestureDirection.Down ->
            state.deleteHistory.lastOrNull()?.photo to state.currentPhoto
        null -> state.currentPhoto to null
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .weight(1f)
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
                                dragX = if (state.currentIndex < state.visiblePhotos.lastIndex) {
                                    totalX.coerceAtMost(0f)
                                } else {
                                    edgeResistedDrag(totalX.coerceAtMost(0f))
                                }
                                dragY = 0f
                            }
                            GestureDirection.Right -> {
                                dragX = if (state.currentIndex > 0) {
                                    totalX.coerceAtLeast(0f)
                                } else {
                                    edgeResistedDrag(totalX.coerceAtLeast(0f))
                                }
                                dragY = 0f
                            }
                            GestureDirection.Up -> {
                                dragX = 0f
                                dragY = totalY.coerceAtMost(0f)
                            }
                            GestureDirection.Down -> {
                                dragX = 0f
                                dragY = if (state.canRestoreLastDeletedPhoto) {
                                    totalY.coerceAtLeast(0f)
                                } else {
                                    edgeResistedDrag(totalY.coerceAtLeast(0f))
                                }
                            }
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

                        when (gestureDirection) {
                            GestureDirection.Left -> {
                                val accepted = state.currentIndex < state.visiblePhotos.lastIndex &&
                                    totalX < -SwipeTriggerPx && absX > absY * DirectionRatio
                                if (accepted) {
                                    throwAccepted(
                                        direction = GestureDirection.Left,
                                        front = state.currentPhoto,
                                        back = state.visiblePhotos.getOrNull(state.currentIndex + 1),
                                        updateState = onGoToNextPhoto
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Right -> {
                                val accepted = state.currentIndex > 0 &&
                                    totalX > SwipeTriggerPx && absX > absY * DirectionRatio
                                if (accepted) {
                                    throwAccepted(
                                        direction = GestureDirection.Right,
                                        front = state.visiblePhotos.getOrNull(state.currentIndex - 1),
                                        back = state.currentPhoto,
                                        updateState = onGoToPreviousPhoto
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Up -> {
                                val accepted = totalY < -SwipeTriggerPx && absY > absX * DirectionRatio
                                if (accepted) {
                                    throwAccepted(
                                        direction = GestureDirection.Up,
                                        front = state.currentPhoto,
                                        back = state.visiblePhotos.getOrNull(state.currentIndex + 1)
                                            ?: state.visiblePhotos.getOrNull(state.currentIndex - 1),
                                        updateState = onQueueCurrentPhotoForDeletion
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            GestureDirection.Down -> {
                                val accepted = state.canRestoreLastDeletedPhoto &&
                                    totalY > SwipeTriggerPx && absY > absX * DirectionRatio
                                if (accepted) {
                                    throwAccepted(
                                        direction = GestureDirection.Down,
                                        front = state.deleteHistory.lastOrNull()?.photo,
                                        back = state.currentPhoto,
                                        updateState = { onRestoreLastDeletedPhoto() }
                                    )
                                } else {
                                    springBack()
                                }
                            }
                            null -> resetGesture()
                        }
                    }
                )
            }
    ) {
        val activeDir = throwSnapshot?.direction ?: gestureDirection

        back?.let { photo ->
            PhotoCard(photo = photo, modifier = Modifier.fillMaxSize())
        }
        front?.let { photo ->
            PhotoCard(
                photo = photo,
                modifier = Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        when (activeDir) {
                            GestureDirection.Left -> {
                                translationX = dragX
                                translationY = 0f
                            }
                            GestureDirection.Up -> {
                                translationX = 0f
                                translationY = dragY
                            }
                            GestureDirection.Right -> {
                                translationX = -stageWidth + dragX
                                translationY = 0f
                            }
                            GestureDirection.Down -> {
                                translationX = 0f
                                translationY = -stageHeight + dragY
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
                    contentDescription = stringResource(R.string.delete_queue_content_description),
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
    val context = LocalContext.current
    val requestOptions = photoImageRequestOptions()
    AsyncImage(
        model = ImageRequest.Builder(context)
            .data(photo.uri)
            .memoryCacheKey("${requestOptions.memoryCacheKeyPrefix}-${photo.id}")
            .diskCacheKey("${requestOptions.memoryCacheKeyPrefix}-${photo.id}")
            .allowHardware(requestOptions.allowHardware)
            .precision(if (requestOptions.precisionInexact) Precision.INEXACT else Precision.EXACT)
            .scale(requestOptions.scale)
            .size(1440, 2560)
            .build(),
        contentDescription = photo.displayName,
        contentScale = ContentScale.Fit,
        modifier = modifier
    )
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
    val context = LocalContext.current
    val requestOptions = photoImageRequestOptions()

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
                model = ImageRequest.Builder(context)
                    .data(photo.uri)
                    .memoryCacheKey("${requestOptions.memoryCacheKeyPrefix}-thumb-${photo.id}")
                    .diskCacheKey("${requestOptions.memoryCacheKeyPrefix}-thumb-${photo.id}")
                    .allowHardware(requestOptions.allowHardware)
                    .precision(if (requestOptions.precisionInexact) Precision.INEXACT else Precision.EXACT)
                    .scale(requestOptions.scale)
                    .size(128, 128)
                    .build(),
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
