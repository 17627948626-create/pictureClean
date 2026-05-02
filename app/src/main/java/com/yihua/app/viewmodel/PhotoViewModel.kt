package com.yihua.app.viewmodel

import android.app.Application
import android.content.Context
import android.content.IntentSender
import android.content.SharedPreferences
import android.os.Build
import android.provider.MediaStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.yihua.app.data.Photo
import com.yihua.app.data.PhotoDataSource
import com.yihua.app.data.PhotoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

// ─── 屏幕状态机 ──────────────────────────────────────────────────────────────

enum class PhotoListState {
    Loading,
    EmptyLibrary,        // 本地相册本身没有照片
    AllQueuedForDelete,  // 所有照片均已加入待删除队列，visiblePhotos 为空
    Reviewable           // 正常浏览状态
}

// ─── 历史记录条目 ─────────────────────────────────────────────────────────────

data class DeleteHistoryEntry(
    val photo: Photo,
    val previousIndex: Int   // 该照片在删除前 visiblePhotos 中的索引
)

// ─── UI 状态 ──────────────────────────────────────────────────────────────────

data class PhotoUiState(
    val allPhotos: List<Photo> = emptyList(),
    /** 不含待删除照片的可见列表；始终与 allPhotos/deleteQueueIds 同步 */
    val visiblePhotos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val deleteQueue: List<Photo> = emptyList(),
    /** 快速查找集合，避免在 visiblePhotos 计算里重复构造 */
    val deleteQueueIds: Set<Long> = emptySet(),
    val deleteHistory: List<DeleteHistoryEntry> = emptyList(),
    val screenState: PhotoListState = PhotoListState.Loading
) {
    val currentPhoto: Photo? get() = visiblePhotos.getOrNull(currentIndex)

    /**
     * 当且仅当当前位置恰好是最近一次删除的发生位置时，才允许下滑撤销。
     * checkIdx = previousIndex.coerceAtMost(visiblePhotos.lastIndex)
     * 处理"删除的是末尾照片"时 currentIndex 被钳至新末尾的情况。
     */
    val canSwipeDownToUndo: Boolean get() {
        val last = deleteHistory.lastOrNull() ?: return false
        val checkIdx = last.previousIndex.coerceAtMost(visiblePhotos.lastIndex.coerceAtLeast(0))
        return currentIndex == checkIdx
    }
}

// ─── 唯一衍生字段计算入口 ─────────────────────────────────────────────────────
//
// 所有 _uiState 变更操作最终必须调用此函数，确保：
//   1. visiblePhotos 始终 = allPhotos.filter { id !in deleteQueueIds }
//   2. deleteQueueIds 始终 = deleteQueue.ids
//   3. screenState 始终反映当前数据
//   4. currentIndex 始终在 visiblePhotos.indices（或 0）内

private fun PhotoUiState.withRecomputedVisible(): PhotoUiState {
    val ids = deleteQueue.map { it.id }.toSet()
    val visible = allPhotos.filter { it.id !in ids }
    val screen = when {
        allPhotos.isEmpty() -> PhotoListState.EmptyLibrary
        visible.isEmpty()   -> PhotoListState.AllQueuedForDelete
        else                -> PhotoListState.Reviewable
    }
    return copy(
        visiblePhotos  = visible,
        deleteQueueIds = ids,
        screenState    = screen,
        currentIndex   = currentIndex.coerceIn(0, maxOf(0, visible.size - 1))
    )
}

// ─── ViewModel ────────────────────────────────────────────────────────────────

class PhotoViewModel(
    application: Application,
    private val repository: PhotoDataSource,
    private val prefs: SharedPreferences
) : AndroidViewModel(application) {

    /** 生产构造器：ViewModelProvider 调用此路径 */
    constructor(application: Application) : this(
        application = application,
        repository  = PhotoRepository(application),
        prefs       = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val PREFS_NAME        = "yihua_prefs"
        private const val KEY_CURRENT_INDEX = "current_index"
    }

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    private fun saveCurrentIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_INDEX, index).apply()
    }

    // ── 加载 ─────────────────────────────────────────────────────────────────

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(screenState = PhotoListState.Loading) }
            val photos = repository.loadPhotos()
            val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
                .coerceIn(0, maxOf(0, photos.size - 1))
            _uiState.update { state ->
                state.copy(
                    allPhotos     = photos,
                    currentIndex  = savedIndex,
                    deleteQueue   = emptyList(),
                    deleteHistory = emptyList()
                ).withRecomputedVisible()
            }
        }
    }

    // ── 导航 ─────────────────────────────────────────────────────────────────

    /** 下一张（在 visiblePhotos 中前进） */
    fun swipeRight() {
        _uiState.update { state ->
            val next = state.currentIndex + 1
            if (next >= state.visiblePhotos.size) return@update state
            state.copy(currentIndex = next)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /** 上一张（在 visiblePhotos 中后退） */
    fun swipeLeft() {
        _uiState.update { state ->
            val prev = state.currentIndex - 1
            if (prev < 0) return@update state
            state.copy(currentIndex = prev)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /** 跳转到指定索引（缩略图点击，索引基于 visiblePhotos） */
    fun goToIndex(index: Int) {
        _uiState.update { state ->
            state.copy(currentIndex = index.coerceIn(0, maxOf(0, state.visiblePhotos.size - 1)))
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    // ── 核心操作 ──────────────────────────────────────────────────────────────

    /**
     * 上滑：将当前照片加入待删除队列。
     * previousIndex 在 withRecomputedVisible() 钳制 currentIndex 之前记录，
     * 保证 canSwipeDownToUndo 检查逻辑正确。
     */
    fun swipeUp() {
        _uiState.update { state ->
            val photo = state.currentPhoto ?: return@update state
            if (photo.id in state.deleteQueueIds) return@update state
            val previousIndex = state.currentIndex
            state.copy(
                deleteQueue   = state.deleteQueue + photo,
                deleteHistory = state.deleteHistory + DeleteHistoryEntry(photo, previousIndex)
            ).withRecomputedVisible()
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    /**
     * 下滑撤销：仅当 canSwipeDownToUndo 为 true 时生效。
     * currentIndex 恢复到照片被删除前的位置（previousIndex），
     * withRecomputedVisible() 重新将照片插回 visiblePhotos 正确位置。
     * 返回是否发生了撤销。
     */
    fun undoDelete(): Boolean {
        var didUndo = false
        _uiState.update { state ->
            if (!state.canSwipeDownToUndo) return@update state
            val last = state.deleteHistory.lastOrNull() ?: return@update state
            didUndo = true
            state.copy(
                currentIndex  = last.previousIndex,
                deleteQueue   = state.deleteQueue.filter { it.id != last.photo.id },
                deleteHistory = state.deleteHistory.dropLast(1)
            ).withRecomputedVisible()
        }
        if (didUndo) saveCurrentIndex(_uiState.value.currentIndex)
        return didUndo
    }

    /**
     * 从待删除队列中手动移除单张照片（在确认页操作）。
     * 同步清理 deleteHistory，避免对已移出队列的照片留下残留撤销记录。
     * 移除后照片按 allPhotos 顺序自动归位到 visiblePhotos。
     */
    fun removeFromDeleteQueue(photo: Photo) {
        _uiState.update { state ->
            state.copy(
                deleteQueue   = state.deleteQueue.filter { it.id != photo.id },
                deleteHistory = state.deleteHistory.filter { it.photo.id != photo.id }
            ).withRecomputedVisible()
        }
    }

    // ── 系统删除 ──────────────────────────────────────────────────────────────

    /** 生成系统删除请求的 IntentSender（Android 11+）*/
    fun createDeleteRequest(): IntentSender? {
        val queue = _uiState.value.deleteQueue
        if (queue.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val uris = queue.map { it.uri }
        return MediaStore.createDeleteRequest(
            getApplication<Application>().contentResolver,
            uris
        ).intentSender
    }

    /** Android 10 直接删除（降级处理） */
    fun deleteDirectly(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) return false
        val queue = _uiState.value.deleteQueue
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
            state.copy(
                allPhotos     = state.allPhotos.filter { it.id !in state.deleteQueueIds },
                deleteQueue   = emptyList(),
                deleteHistory = emptyList()
            ).withRecomputedVisible()
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }
}
