package com.yihua.app.viewmodel

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import com.yihua.app.data.Photo
import com.yihua.app.data.PhotoDataSource
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class DeletePathFakePhotoDataSource(private val photos: List<Photo>) : PhotoDataSource {
    override suspend fun loadPhotos(): List<Photo> = photos
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DeletePathContractTest {
    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var app: Application

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

    @Test
    @Config(sdk = [30])
    fun `api 30 delete requests system confirmation and keeps queued photos until result returns`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.queueCurrentPhotoForDeletion()

        val result = vm.requestDeleteQueuedPhotos()

        assertTrue(result is DeleteResult.RequiresUserConfirmation)
        assertEquals(1, vm.uiState.value.deleteQueue.size)
        assertEquals(2, vm.uiState.value.allPhotos.size)
    }

    @Test
    fun `failed direct delete keeps every queued photo`() {
        val photos = makePhotos(2)
        val state = loadedStateWithQueuedPhotos(photos)

        val result = PhotoViewModel.applyDirectDeleteOutcome(
            state = state,
            deletedIds = emptySet()
        )

        assertTrue(result.deleteResult is DeleteResult.Failure)
        assertEquals(photos, result.state.allPhotos)
        assertEquals(photos, result.state.deleteQueue)
    }

    @Test
    fun `partial direct delete removes successful photos and keeps failed photos queued`() {
        val photos = makePhotos(3)
        val state = loadedStateWithQueuedPhotos(photos)

        val result = PhotoViewModel.applyDirectDeleteOutcome(
            state = state,
            deletedIds = setOf(photos[0].id, photos[2].id)
        )

        assertEquals(DeleteResult.PartialFailure(deletedCount = 2, failedCount = 1), result.deleteResult)
        assertEquals(listOf(photos[1]), result.state.allPhotos)
        assertEquals(listOf(photos[1]), result.state.deleteQueue)
        assertEquals(PhotoListState.AllQueuedForDelete, result.state.screenState)
    }

    private fun makeViewModel(photos: List<Photo>): PhotoViewModel {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PhotoViewModel(app, DeletePathFakePhotoDataSource(photos), prefs)
    }

    private fun loadedStateWithQueuedPhotos(photos: List<Photo>): PhotoUiState {
        return PhotoUiState(
            allPhotos = photos,
            visiblePhotos = emptyList(),
            deleteQueue = photos,
            deleteQueueIds = photos.map { it.id }.toSet(),
            screenState = PhotoListState.AllQueuedForDelete
        )
    }

    private fun makePhotos(count: Int): List<Photo> = (1L..count).map { id ->
        Photo(
            id = id,
            uri = Uri.parse("content://media/external/images/media/$id"),
            displayName = "photo_$id.jpg",
            dateAdded = id,
            size = 1000L
        )
    }

    companion object {
        private const val PREFS_NAME = "delete_path_contract_test_prefs"
    }
}
