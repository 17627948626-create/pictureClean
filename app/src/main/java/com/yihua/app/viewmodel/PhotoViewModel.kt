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

enum class PhotoListState {
    Loading,
    EmptyLibrary,
    AllQueuedForDelete,
    Reviewable
}

data class DeleteHistoryEntry(
    val photo: Photo,
    val previousIndex: Int
)

data class PhotoUiState(
    val allPhotos: List<Photo> = emptyList(),
    val visiblePhotos: List<Photo> = emptyList(),
    val currentIndex: Int = 0,
    val deleteQueue: List<Photo> = emptyList(),
    val deleteQueueIds: Set<Long> = emptySet(),
    val deleteHistory: List<DeleteHistoryEntry> = emptyList(),
    val screenState: PhotoListState = PhotoListState.Loading
) {
    val currentPhoto: Photo? get() = visiblePhotos.getOrNull(currentIndex)

    val canRestoreLastDeletedPhoto: Boolean get() {
        val lastDelete = deleteHistory.lastOrNull() ?: return false
        val restoreIndex = lastDelete.previousIndex.coerceAtMost(visiblePhotos.lastIndex.coerceAtLeast(0))
        return currentIndex == restoreIndex
    }
}

private fun PhotoUiState.recomputeDerivedState(): PhotoUiState {
    val queuedIds = deleteQueue.map { it.id }.toSet()
    val visible = allPhotos.filter { it.id !in queuedIds }
    val screen = when {
        allPhotos.isEmpty() -> PhotoListState.EmptyLibrary
        visible.isEmpty() -> PhotoListState.AllQueuedForDelete
        else -> PhotoListState.Reviewable
    }

    return copy(
        visiblePhotos = visible,
        deleteQueueIds = queuedIds,
        screenState = screen,
        currentIndex = currentIndex.coerceIn(0, maxOf(0, visible.size - 1))
    )
}

class PhotoViewModel(
    application: Application,
    private val repository: PhotoDataSource,
    private val prefs: SharedPreferences
) : AndroidViewModel(application) {

    constructor(application: Application) : this(
        application = application,
        repository = PhotoRepository(application),
        prefs = application.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    )

    companion object {
        private const val PREFS_NAME = "yihua_prefs"
        private const val KEY_CURRENT_INDEX = "current_index"
    }

    private val _uiState = MutableStateFlow(PhotoUiState())
    val uiState: StateFlow<PhotoUiState> = _uiState.asStateFlow()

    private fun saveCurrentIndex(index: Int) {
        prefs.edit().putInt(KEY_CURRENT_INDEX, index).apply()
    }

    fun loadPhotos() {
        viewModelScope.launch {
            _uiState.update { it.copy(screenState = PhotoListState.Loading) }

            val photos = repository.loadPhotos()
            val savedIndex = prefs.getInt(KEY_CURRENT_INDEX, 0)
                .coerceIn(0, maxOf(0, photos.size - 1))

            _uiState.update { state ->
                state.copy(
                    allPhotos = photos,
                    currentIndex = savedIndex,
                    deleteQueue = emptyList(),
                    deleteHistory = emptyList()
                ).recomputeDerivedState()
            }
        }
    }

    fun goToNextPhoto() {
        _uiState.update { state ->
            val nextIndex = state.currentIndex + 1
            if (nextIndex >= state.visiblePhotos.size) state else state.copy(currentIndex = nextIndex)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    fun goToPreviousPhoto() {
        _uiState.update { state ->
            val previousIndex = state.currentIndex - 1
            if (previousIndex < 0) state else state.copy(currentIndex = previousIndex)
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    fun goToIndex(index: Int) {
        _uiState.update { state ->
            state.copy(currentIndex = index.coerceIn(0, maxOf(0, state.visiblePhotos.size - 1)))
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    fun queueCurrentPhotoForDeletion() {
        _uiState.update { state ->
            val photo = state.currentPhoto ?: return@update state
            if (photo.id in state.deleteQueueIds) return@update state

            state.copy(
                deleteQueue = state.deleteQueue + photo,
                deleteHistory = state.deleteHistory + DeleteHistoryEntry(
                    photo = photo,
                    previousIndex = state.currentIndex
                )
            ).recomputeDerivedState()
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }

    fun restoreLastDeletedPhoto(): Boolean {
        var restored = false
        _uiState.update { state ->
            if (!state.canRestoreLastDeletedPhoto) return@update state
            val lastDelete = state.deleteHistory.lastOrNull() ?: return@update state

            restored = true
            state.copy(
                currentIndex = lastDelete.previousIndex,
                deleteQueue = state.deleteQueue.filter { it.id != lastDelete.photo.id },
                deleteHistory = state.deleteHistory.dropLast(1)
            ).recomputeDerivedState()
        }

        if (restored) saveCurrentIndex(_uiState.value.currentIndex)
        return restored
    }

    fun removeFromDeleteQueue(photo: Photo) {
        _uiState.update { state ->
            state.copy(
                deleteQueue = state.deleteQueue.filter { it.id != photo.id },
                deleteHistory = state.deleteHistory.filter { it.photo.id != photo.id }
            ).recomputeDerivedState()
        }
    }

    fun createDeleteRequest(): IntentSender? {
        val queue = _uiState.value.deleteQueue
        if (queue.isEmpty()) return null
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null

        return MediaStore.createDeleteRequest(
            getApplication<Application>().contentResolver,
            queue.map { it.uri }
        ).intentSender
    }

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

    fun onDeleteCompleted() {
        _uiState.update { state ->
            state.copy(
                allPhotos = state.allPhotos.filter { it.id !in state.deleteQueueIds },
                deleteQueue = emptyList(),
                deleteHistory = emptyList()
            ).recomputeDerivedState()
        }
        saveCurrentIndex(_uiState.value.currentIndex)
    }
}
