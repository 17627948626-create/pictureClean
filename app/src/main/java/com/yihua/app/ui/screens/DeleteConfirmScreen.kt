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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
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
                partialFailureMessage = "已删除 ${result.deletedCount} 张，${result.failedCount} 张删除失败，失败照片仍保留在待删除队列。"
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
                partialFailureMessage = "已删除 ${result.deletedCount} 张，${result.failedCount} 张删除失败，失败照片仍保留在待删除队列。"
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
                        "待删除照片（${state.deleteQueue.size} 张）",
                        fontWeight = FontWeight.Medium
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "返回"
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
            title = "没有待删除照片",
            message = "待删除队列为空。",
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.Cancelled -> InfoDialog(
            title = "已取消删除",
            message = "照片未被删除，待删除队列已保留。",
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.RequestFailed -> InfoDialog(
            title = "无法发起删除请求",
            message = "无法发起系统删除请求，请重试。待删除队列已保留。",
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.DeleteFailed -> InfoDialog(
            title = "删除失败",
            message = "照片删除失败，请检查权限后重试。待删除队列已保留。",
            onDismiss = { dialogState = DeleteDialogState.None }
        )
        DeleteDialogState.PartialFailure -> InfoDialog(
            title = "部分照片删除失败",
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
        title = { Text("确认删除？", fontWeight = FontWeight.Bold) },
        text = {
            Text(
                "将永久删除 $count 张照片，此操作不可恢复。",
                textAlign = TextAlign.Center
            )
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                colors = ButtonDefaults.buttonColors(containerColor = SwipeUpColor)
            ) {
                Text("确认删除", color = Color.White)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("取消") }
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
            TextButton(onClick = onDismiss) { Text("知道了") }
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
                    "删除 $count 张照片",
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
                "没有待删除的照片",
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
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .clip(RoundedCornerShape(4.dp))
    ) {
        AsyncImage(
            model = photo.uri,
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
                    contentDescription = "从队列移除",
                    tint = Color.White,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}
