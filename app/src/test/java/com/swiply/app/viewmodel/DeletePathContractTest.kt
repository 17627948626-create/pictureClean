package com.swiply.app.viewmodel

import android.net.Uri
import com.swiply.app.data.Photo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeletePathContractTest {
    @Test
    fun `api 30 and above uses system confirmation delete strategy`() {
        assertEquals(DeleteStrategy.SystemConfirmation, PhotoViewModel.deleteStrategyForSdk(30))
        assertEquals(DeleteStrategy.SystemConfirmation, PhotoViewModel.deleteStrategyForSdk(35))
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
}
