package com.yihua.app.viewmodel

import android.app.Application
import android.content.Context
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

data class DeleteHistoryEntry(val photo: Photo, val previousIndex: Int)

data class PhotoUiState(
    val allPhotos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val deleteQueue: List<Photo> = emptyList(),
    val deleteHistory: List<DeleteHistoryEntry> = emptyList(),
    val isLoading: Boolean = true
) {
    private val deleteQueueIds: Set<Long> get() = deleteQueue.map { it.id }.toSet()

    /** 不含待删除照片的可见列表，currentIndex 指向此列表 */
    val visiblePhotos: List<Photo> get() = allPhotos.filter { it.id !in deleteQueueIds }

    val currentPhoto: Photo? get() = visiblePhotos.getOrNull(currentIndex)

    val isEmpty: Boolean get() = !isLoading && allPhotos.isEmpty()

    /**
     * 当且仅当当前位置恰好是最近一次删除发生的位置时，才允许下滑撤销。
     * 位置匹配条件：currentIndex == previousIndex（若 previousIndex 超出新列表则钳至末尾）
     */
    val canSwipeDownToUndo: Boolean get() {
        val last = deleteHistory.lastOrNull() ?: return false
        val checkIdx = last.previousIndex.coerceAtMost(visiblePhotos.lastIndex.coerceAtLeast(0))
        return currentIndex == checkIdx
    }
}

class PhotoViewModel(application: Application) : AndroidViewModel(application) {

    companion object {
        private const val PREFS_NAME = "yihua_prefs"
        private const val KEY_CURRENT_INDEX = "current_index"
    }

    private val repository = PhotoRepository(application)
    private val prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    private fun saveCurrentIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_INDEX, index).apply()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            val photos = repository.loadPhotos()
            val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
                .coerceIn(0, maxOf(0, photos.size - 1))
            _uiState.update {
                it.copy(
                    allPhotos = photos,
                    isLoading = false,
                    currentIndex = savedIndex,
                    deleteQueue = emptyList(),
                    deleteHistory = emptyList()
                )
            }
        }
    }

    /** 下一张（在 visiblePhotos 中前进） */
    fun swipeRight() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            if (nextIndex >= state.visiblePhotos.size) return@update state
            state.copy(currentIndex = nextIndex)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /** 上一张（在 visiblePhotos 中后退） */
    fun swipeLeft() {
        _uiState.update { state ->
            val prevIndex = state.currentIndex - 1
            if (prevIndex < 0) return@update state
            state.copy(currentIndex = prevIndex)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /** 上滑：将当前照片加入待删除队列，visiblePhotos 收缩，currentIndex 自动指向下一张 */
    fun swipeUp() {
        _uiState.update { state ->
            val photo = state.currentPhoto ?: return@update state
            if (state.deleteQueue.any { it.id == photo.id }) return@update state
            val previousIndex = state.currentIndex
            val newQueue = state.deleteQueue + photo
            // visiblePhotos 删除一张后大小减 1，currentIndex 保持不变（滑向下一张）
            // 若已是末尾则钳至新末尾
            val newVisibleSize = state.visiblePhotos.size - 1
            val newIndex = previousIndex.coerceAtMost(maxOf(0, newVisibleSize - 1))
            state.copy(
                currentIndex = newIndex,
                deleteQueue = newQueue,
                deleteHistory = state.deleteHistory + DeleteHistoryEntry(photo, previousIndex)
            )
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /**
     * 下滑撤销：仅当 canSwipeDownToUndo 为 true 时生效。
     * 恢复照片后 currentIndex 回到照片在 visiblePhotos 中的原位置。
     * 返回是否发生了撤销。
     */
    fun undoDelete(): Boolean {
        var didUndo = false
        _uiState.update { state ->
            if (!state.canSwipeDownToUndo) return@update state
            val last = state.deleteHistory.lastOrNull() ?: return@update state
            didUndo = true
            state.copy(
                currentIndex = last.previousIndex,
                deleteQueue = state.deleteQueue.filter { it.id != last.photo.id },
                deleteHistory = state.deleteHistory.dropLast(1)
            )
        }
        if (didUndo) saveCurrentIndex(_uiState.value.currentIndex)
        return didUndo
    }

    /** 跳转到指定索引（缩略图点击，索引基于 visiblePhotos） */
    fun goToIndex(index: Int) {
        _uiState.update { state ->
            val safeIndex = index.coerceIn(0, maxOf(0, state.visiblePhotos.size - 1))
            state.copy(currentIndex = safeIndex)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /** 从待删除队列中移除单张照片，同时清理对应的历史记录 */
    fun removeFromDeleteQueue(photo: Photo) {
        _uiState.update { state ->
            state.copy(
                deleteQueue = state.deleteQueue.filter { it.id != photo.id },
                deleteHistory = state.deleteHistory.filter { it.photo.id != photo.id }
            )
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

    /** 删除完成后从 allPhotos 中移除已删照片，刷新列表 */
    fun onDeleteCompleted() {
        _uiState.update { state ->
            val deletedIds = state.deleteQueue.map { it.id }.toSet()
            val updatedAllPhotos = state.allPhotos.filter { it.id !in deletedIds }
            val safeIndex = minOf(state.currentIndex, maxOf(0, updatedAllPhotos.size - 1))
            state.copy(
                allPhotos = updatedAllPhotos,
                currentIndex = safeIndex,
                deleteQueue = emptyList(),
                deleteHistory = emptyList()
            )
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }
}
