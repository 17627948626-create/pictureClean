package com.yihua.app.ui.screens

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
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
import com.yihua.app.R
import com.yihua.app.data.Photo
import com.yihua.app.ui.theme.AppleSystemGray6
import com.yihua.app.ui.theme.LightGrayText
import com.yihua.app.ui.theme.SwipeUpColor
import com.yihua.app.viewmodel.DeleteResult
import com.yihua.app.viewmodel.PhotoViewModel

private enum class DeleteDialogState {
    None,
    Confirm,
    EmptyQueue,
    Cancelled,
    RequestFailed,
    DeleteFailed,
    PartialFailure
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmScreen(
    viewModel: PhotoViewModel,
    onNavigateBack: () -> Unit
) {
    val context = LocalContext.current
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var dialogState by remember { mutableStateOf(DeleteDialogState.None) }
    var partialFailureMessage by remember { mutableStateOf("") }
    var completeDeleteOnSystemResult by remember { mutableStateOf(false) }

    fun handleTerminalDeleteResult(result: DeleteResult) {
        when (result) {
            DeleteResult.EmptyQueue -> dialogState = DeleteDialogState.EmptyQueue
            is DeleteResult.RequiresUserConfirmation -> dialogState = DeleteDialogState.RequestFailed
            is DeleteResult.Success -> onNavigateBack()
            is DeleteResult.PartialFailure -> {
                partialFailureMessage = context.resources.getString(
                    R.string.delete_partial_failure_message,
                    result.deletedCount,
                    result.failedCount
                )
                dialogState = DeleteDialogState.PartialFailure
            }
            is DeleteResult.Failure -> dialogState = DeleteDialogState.DeleteFailed
        }
    }

    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            if (completeDeleteOnSystemResult) {
                completeDeleteOnSystemResult = false
                viewModel.onDeleteCompleted()
                onNavigateBack()
            } else {
                completeDeleteOnSystemResult = false
                handleTerminalDeleteResult(viewModel.requestDeleteQueuedPhotos())
            }
        } else {
            completeDeleteOnSystemResult = false
            dialogState = DeleteDialogState.Cancelled
        }
    }

    fun handleDeleteResult(result: DeleteResult) {
        when (result) {
            DeleteResult.EmptyQueue -> dialogState = DeleteDialogState.EmptyQueue
            is DeleteResult.RequiresUserConfirmation -> {
                completeDeleteOnSystemResult = result.completeOnResult
                try {
                    deleteLauncher.launch(
                        IntentSenderRequest.Builder(result.intentSender).build()
                    )
                } catch (_: Exception) {
                    completeDeleteOnSystemResult = false
                    dialogState = DeleteDialogState.RequestFailed
                }
            }
            is DeleteResult.Success -> onNavigateBack()
            is DeleteResult.PartialFailure -> {
                partialFailureMessage = context.resources.getString(
                    R.string.delete_partial_failure_message,
                    result.deletedCount,
                    result.failedCount
                )
                dialogState = DeleteDialogState.PartialFailure
            }
            is DeleteResult.Failure -> dialogState = DeleteDialogState.DeleteFailed
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(R.string.delete_confirm_title, state.deleteQueue.size),
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppleSystemGray6,
                    titleContentColor = Color(0xFF1C1C1E),
                    navigationIconContentColor = Color(0xFF1C1C1E)
                )
            )
        },
        bottomBar = {
            BottomDeleteBar(
                count = state.deleteQueue.size,
                onConfirmClick = {
                    if (state.deleteQueue.isNotEmpty()) {
                        dialogState = DeleteDialogState.Confirm
                    }
                }
            )
        }
    ) { paddingValues ->
        if (state.deleteQueue.isEmpty()) {
            EmptyQueueContent(
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppleSystemGray6)
                    .padding(paddingValues)
            )
        } else {
            PhotoGrid(
                photos = state.deleteQueue,
                onRemove = { viewModel.removeFromDeleteQueue(it) },
                modifier = Modifier
                    .fillMaxSize()
                    .background(AppleSystemGray6)
                    .padding(paddingValues)
            )
        }
    }

    when (dialogState) {
        DeleteDialogState.None -> Unit
        DeleteDialogState.Confirm -> ConfirmDeleteDialog(
            count = state.deleteQueue.size,
            onDismiss = { dialogState = DeleteDialogState.None },
            onConfirm = {
                dialogState = DeleteDialogState.None
                handleDeleteResult(viewModel.requestDeleteQueuedPhotos())
            }
        )
        DeleteDialogState.EmptyQueue -> InfoDialog(
            title = stringResource(R.string.delete_empty_queue_title),
            message = stringResource(R.string.delete_empty_queue_message),
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.Cancelled -> InfoDialog(
            title = stringResource(R.string.delete_cancelled_title),
            message = stringResource(R.string.delete_cancelled_message),
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.RequestFailed -> InfoDialog(
            title = stringResource(R.string.delete_request_failed_title),
            message = stringResource(R.string.delete_request_failed_message),
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.DeleteFailed -> InfoDialog(
            title = stringResource(R.string.delete_failed_title),
            message = stringResource(R.string.delete_failed_message),
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.PartialFailure -> InfoDialog(
            title = stringResource(R.string.delete_partial_failure_title),
            message = partialFailureMessage,
            onDismiss = { dialogState = DeleteDialogState.None }
        )
    }
}

@Composable
private fun ConfirmDeleteDialog(
    count: Int,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                Icons.Default.Delete,
                contentDescription = null,
                tint = SwipeUpColor,
                modifier = Modifier.size(32.dp)
            )
        },
        title = { Text(stringResource(R.string.delete_confirm_dialog_title), fontWeight = FontWeight.Bold) },
        text = {
            Text(
                stringResource(R.string.delete_confirm_dialog_message, count),
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SwipeUpColor)
            ) {
                Text(stringResource(R.string.delete_confirm_action), color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        }
    )
}

@Composable
private fun InfoDialog(
    title: String,
    message: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title, fontWeight = FontWeight.Bold) },
        text = { Text(message) },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.ok)) }
        }
    )
}

@Composable
private fun BottomDeleteBar(
    count: Int,
    onConfirmClick: () -> Unit
) {
    Surface(shadowElevation = 8.dp) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp)
        ) {
            Button(
                onClick = onConfirmClick,
                modifier = Modifier.fillMaxWidth(),
                enabled = count > 0,
                colors = ButtonDefaults.buttonColors(
                    containerColor = SwipeUpColor,
                    disabledContainerColor = SwipeUpColor.copy(alpha = 0.4f)
                ),
                contentPadding = PaddingValues(vertical = 16.dp)
            ) {
                Icon(Icons.Default.Delete, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(
                    stringResource(R.string.delete_button, count),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun EmptyQueueContent(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("🎉", fontSize = 56.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                stringResource(R.string.empty_delete_queue_title),
                style = MaterialTheme.typography.titleMedium,
                color = LightGrayText
            )
        }
    }
}

@Composable
private fun PhotoGrid(
    photos: List<Photo>,
    onRemove: (Photo) -> Unit,
    modifier: Modifier = Modifier
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(3),
        modifier = modifier,
        contentPadding = PaddingValues(4.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp)
    ) {
        items(photos, key = { it.id }) { photo ->
            PhotoThumbnailItem(
                photo = photo,
                onRemove = { onRemove(photo) }
            )
        }
    }
}

@Composable
private fun PhotoThumbnailItem(
    photo: Photo,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val requestOptions = photoImageRequestOptions()

    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(photo.uri)
                .memoryCacheKey("${requestOptions.memoryCacheKeyPrefix}-confirm-${photo.id}")
                .diskCacheKey("${requestOptions.memoryCacheKeyPrefix}-confirm-${photo.id}")
                .allowHardware(requestOptions.allowHardware)
                .precision(if (requestOptions.precisionInexact) Precision.INEXACT else Precision.EXACT)
                .scale(requestOptions.scale)
                .size(256, 256)
                .build(),
            contentDescription = photo.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier.fillMaxSize()
        )
        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(4.dp)
                .size(24.dp)
                .background(Color.Black.copy(alpha = 0.6f), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = stringResource(R.string.remove_from_delete_queue),
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
