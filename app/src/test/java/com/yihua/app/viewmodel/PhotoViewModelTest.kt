package com.yihua.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yihua.app.data.Photo
import com.yihua.app.data.PhotoDataSource
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class FakePhotoDataSource(private val photos: List<Photo>) : PhotoDataSource {
    override suspend fun loadPhotos(): List<Photo> = photos
}

private class FailingPhotoDataSource : PhotoDataSource {
    override suspend fun loadPhotos(): List<Photo> {
        error("MediaStore read failed")
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PhotoViewModelTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

    companion object {
        private const val PREFS_NAME = "test_prefs"
    }

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        app = ApplicationProvider.getApplicationContext()
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun makePhotos(count: Int): List<Photo> = (1L..count).map { id ->
        Photo(id = id, uri = mockk<Uri>(), displayName = "photo_$id.jpg", dateAdded = id, size = 1000L)
    }

    private fun makeViewModel(photos: List<Photo> = emptyList()): PhotoViewModel {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PhotoViewModel(app, FakePhotoDataSource(photos), prefs)
    }

    private fun makeFailingViewModel(): PhotoViewModel {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PhotoViewModel(app, FailingPhotoDataSource(), prefs)
    }

    @Test
    fun `loadPhotos sets currentPhoto to first photo`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        assertEquals(photos.first(), vm.uiState.value.currentPhoto)
    }

    @Test
    fun `loadPhotos on empty library transitions to EmptyLibrary`() {
        val vm = makeViewModel(emptyList())
        vm.loadPhotos()
        assertEquals(PhotoListState.EmptyLibrary, vm.uiState.value.screenState)
    }

    @Test
    fun `loadPhotos failure transitions to LoadFailed`() {
        val vm = makeFailingViewModel()
        vm.loadPhotos()
        val state = vm.uiState.value
        assertEquals(PhotoListState.LoadFailed, state.screenState)
        assertTrue(state.allPhotos.isEmpty())
        assertTrue(state.visiblePhotos.isEmpty())
        assertTrue(state.deleteQueue.isEmpty())
        assertNotNull(state.errorMessage)
    }

    @Test
    fun `goToNextPhoto does not exceed last photo`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.goToNextPhoto()
        vm.goToNextPhoto()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun `goToPreviousPhoto does not go below first photo`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.goToPreviousPhoto()
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    @Test
    fun `queueCurrentPhotoForDeletion adds current photo to deleteQueue`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val queued = vm.uiState.value.currentPhoto!!
        vm.queueCurrentPhotoForDeletion()
        assertTrue(queued.id in vm.uiState.value.deleteQueueIds)
    }

    @Test
    fun `queueCurrentPhotoForDeletion removes photo from visiblePhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val queued = vm.uiState.value.currentPhoto!!
        vm.queueCurrentPhotoForDeletion()
        assertFalse(vm.uiState.value.visiblePhotos.any { it.id == queued.id })
        assertEquals(2, vm.uiState.value.visiblePhotos.size)
    }

    @Test
    fun `queueCurrentPhotoForDeletion on last photo clamps currentIndex to new last`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.goToNextPhoto()
        vm.goToNextPhoto()
        vm.queueCurrentPhotoForDeletion()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun `restoreLastDeletedPhoto restores photo to visiblePhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val deleted = vm.uiState.value.currentPhoto!!
        vm.queueCurrentPhotoForDeletion()

        val restored = vm.restoreLastDeletedPhoto()

        assertTrue(restored)
        assertTrue(vm.uiState.value.visiblePhotos.any { it.id == deleted.id })
    }

    @Test
    fun `restoreLastDeletedPhoto restores currentIndex to deletion position`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val previousIndex = vm.uiState.value.currentIndex
        vm.queueCurrentPhotoForDeletion()
        vm.restoreLastDeletedPhoto()
        assertEquals(previousIndex, vm.uiState.value.currentIndex)
    }

    @Test
    fun `all photos queued transitions to AllQueuedForDelete`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        repeat(3) { vm.queueCurrentPhotoForDeletion() }
        assertEquals(PhotoListState.AllQueuedForDelete, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.visiblePhotos.isEmpty())
        assertEquals(3, vm.uiState.value.deleteQueue.size)
    }

    @Test
    fun `onDeleteCompleted clears deleteQueue and removes photos from allPhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        vm.onDeleteCompleted()
        assertTrue(vm.uiState.value.deleteQueue.isEmpty())
        assertEquals(2, vm.uiState.value.allPhotos.size)
        assertEquals(PhotoListState.Reviewable, vm.uiState.value.screenState)
    }

    @Test
    fun `restoreLastDeletedPhoto returns false when current index is not deletion position`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        vm.goToNextPhoto()

        val restored = vm.restoreLastDeletedPhoto()

        assertFalse(restored)
        assertEquals(1, vm.uiState.value.deleteQueue.size)
    }

    @Test
    fun `removeFromDeleteQueue also removes entry from deleteHistory`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        val queuedPhoto = vm.uiState.value.deleteQueue.first()
        vm.removeFromDeleteQueue(queuedPhoto)
        assertTrue(vm.uiState.value.deleteHistory.none { it.photo.id == queuedPhoto.id })
        assertTrue(vm.uiState.value.visiblePhotos.any { it.id == queuedPhoto.id })
    }

    @Test
    fun `deleteQueue is preserved when delete is not completed`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        vm.queueCurrentPhotoForDeletion()
        assertEquals(2, vm.uiState.value.deleteQueue.size)
        assertEquals(2, vm.uiState.value.deleteHistory.size)
    }

    @Test
    fun `multiple onDeleteCompleted calls each reduce allPhotos correctly`() {
        val photos = makePhotos(4)
        val vm = makeViewModel(photos)
        vm.loadPhotos()

        vm.queueCurrentPhotoForDeletion()
        vm.onDeleteCompleted()
        assertEquals(3, vm.uiState.value.allPhotos.size)

        vm.queueCurrentPhotoForDeletion()
        vm.onDeleteCompleted()
        assertEquals(2, vm.uiState.value.allPhotos.size)
    }

    @Test
    fun `onDeleteCompleted all photos transitions to EmptyLibrary`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        vm.queueCurrentPhotoForDeletion()
        vm.onDeleteCompleted()
        assertEquals(PhotoListState.EmptyLibrary, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.allPhotos.isEmpty())
    }

    @Test
    fun `removeFromDeleteQueue when all queued restores screenState to Reviewable`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()
        vm.queueCurrentPhotoForDeletion()
        assertEquals(PhotoListState.AllQueuedForDelete, vm.uiState.value.screenState)

        val photo = vm.uiState.value.deleteQueue.first()
        vm.removeFromDeleteQueue(photo)
        assertEquals(PhotoListState.Reviewable, vm.uiState.value.screenState)
        assertEquals(1, vm.uiState.value.visiblePhotos.size)
    }
}
