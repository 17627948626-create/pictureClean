package com.yihua.app.ui.screens

import android.app.Activity
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
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
import com.yihua.app.viewmodel.PhotoViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DeleteConfirmScreen(
    viewModel: PhotoViewModel,
    onNavigateBack: () -> Unit
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showConfirmDialog by remember { mutableStateOf(false) }

    // 系统删除结果回调
    val deleteLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartIntentSenderForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.onDeleteCompleted()
            onNavigateBack()
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
                        showConfirmDialog = true
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

    // 二次确认弹窗
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            icon = {
                Icon(
                    Icons.Default.Delete,
                    contentDescription = null,
                    tint = SwipeUpColor,
                    modifier = Modifier.size(32.dp)
                )
            },
            title = {
                Text("确认删除？", fontWeight = FontWeight.Bold)
            },
            text = {
                Text(
                    "将永久删除 ${state.deleteQueue.size} 张照片，此操作不可恢复。",
                    textAlign = TextAlign.Center
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showConfirmDialog = false
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                            // Android 11+：使用系统删除确认弹窗
                            val intentSender = viewModel.createDeleteRequest()
                            if (intentSender != null) {
                                deleteLauncher.launch(
                                    IntentSenderRequest.Builder(intentSender).build()
                                )
                            }
                        } else {
                            // Android 10：直接删除
                            viewModel.deleteDirectly()
                            onNavigateBack()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = SwipeUpColor)
                ) {
                    Text("确认删除", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
private fun BottomDeleteBar(
    count: Int,
    onConfirmClick: () -> Unit
) {
    Surface(
        shadowElevation = 8.dp
    ) {
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
        // 删除角标
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
