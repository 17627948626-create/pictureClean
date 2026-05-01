package com.yihua.app.viewmodel

import android.app.Application
import android.content.IntentSender
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yihua.app.data.Photo
import com.yihua.app.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// 操作历史，用于撤销
sealed class SwipeAction {
    data class Skipped(val previousIndex: Int) : SwipeAction()
    data class MarkedForDelete(val photo: Photo, val previousIndex: Int) : SwipeAction()
}

data class PhotoUiState(
    val photos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val deleteQueue: List<Photo> = emptyList(),
    val actionHistory: List<SwipeAction> = emptyList(),
    val isLoading: Boolean = true,
    val isEmpty: Boolean = false
) {
    val currentPhoto: Photo? get() = photos.getOrNull(currentIndex)
    val canUndo: Boolean get() = actionHistory.isNotEmpty()
    val isCurrentMarkedForDelete: Boolean
        get() = currentPhoto != null && deleteQueue.any { it.id == currentPhoto!!.id }
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = PhotoRepository(application)

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val photos = repository.loadPhotos()
            _uiState.update {
                it.copy(
                    photos = photos,
                    isLoading = false,
                    isEmpty = photos.isEmpty(),
                    currentIndex = 0,
                    deleteQueue = emptyList(),
                    actionHistory = emptyList()
                )
            }
        }
    }

    /** 右滑 → 下一张 */
    fun swipeRight() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            if (nextIndex >= state.photos.size) return@update state
            state.copy(
                currentIndex = nextIndex,
                actionHistory = state.actionHistory + SwipeAction.Skipped(state.currentIndex)
            )
        }
    }

    /** 左滑 → 上一张 */
    fun swipeLeft() {
        _uiState.update { state ->
            val prevIndex = state.currentIndex - 1
            if (prevIndex < 0) return@update state
            state.copy(
                currentIndex = prevIndex,
                actionHistory = state.actionHistory + SwipeAction.Skipped(state.currentIndex)
            )
        }
    }

    /** 上滑 → 加入待删除队列，并自动前进到下一张 */
    fun swipeUp() {
        _uiState.update { state ->
            val photo = state.currentPhoto ?: return@update state
            // 已在队列中则跳过重复添加
            val newQueue = if (state.deleteQueue.any { it.id == photo.id }) {
                state.deleteQueue
            } else {
                state.deleteQueue + photo
            }
            val nextIndex = minOf(state.currentIndex + 1, state.photos.size - 1)
            state.copy(
                currentIndex = nextIndex,
                deleteQueue = newQueue,
                actionHistory = state.actionHistory + SwipeAction.MarkedForDelete(photo, state.currentIndex)
            )
        }
    }

    /** 撤销上一步操作 */
    fun undo() {
        _uiState.update { state ->
            val last = state.actionHistory.lastOrNull() ?: return@update state
            when (last) {
                is SwipeAction.Skipped -> {
                    state.copy(
                        currentIndex = last.previousIndex,
                        actionHistory = state.actionHistory.dropLast(1)
                    )
                }
                is SwipeAction.MarkedForDelete -> {
                    state.copy(
                        currentIndex = last.previousIndex,
                        deleteQueue = state.deleteQueue.filter { it.id != last.photo.id },
                        actionHistory = state.actionHistory.dropLast(1)
                    )
                }
            }
        }
    }

    /** 从待删除队列中移除单张照片 */
    fun removeFromDeleteQueue(photo: Photo) {
        _uiState.update { state ->
            state.copy(deleteQueue = state.deleteQueue.filter { it.id != photo.id })
        }
    }

    /** 生成系统删除请求的 IntentSender（Android 11+）*/
    fun createDeleteRequest(): IntentSender? {
        val queue = _uiState.value.deleteQueue
        if (queue.isEmpty()) return null
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val uris = queue.map { it.uri }
            MediaStore.createDeleteRequest(
                getApplication<Application>().contentResolver,
                uris
            ).intentSender
        } else {
            null
        }
    }

    /** Android 10 直接删除（降级处理） */
    fun deleteDirectly(): Boolean {
        val queue = _uiState.value.deleteQueue
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        var allSuccess = true
        for (photo in queue) {
            try {
                getApplication<Application>().contentResolver.delete(photo.uri, null, null)
            } catch (e: Exception) {
                allSuccess = false
            }
        }
        if (allSuccess) onDeleteCompleted()
        return allSuccess
    }

    /** 删除完成后刷新列表 */
    fun onDeleteCompleted() {
        _uiState.update { state ->
            val deletedIds = state.deleteQueue.map { it.id }.toSet()
            val updatedPhotos = state.photos.filter { it.id !in deletedIds }
            val safeIndex = minOf(state.currentIndex, maxOf(0, updatedPhotos.size - 1))
            state.copy(
                photos = updatedPhotos,
                currentIndex = safeIndex,
                deleteQueue = emptyList(),
                actionHistory = emptyList(),
                isEmpty = updatedPhotos.isEmpty()
            )
        }
    }
}
