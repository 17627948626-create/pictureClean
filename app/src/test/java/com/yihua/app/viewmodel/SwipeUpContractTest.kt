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
import org.junit.Assert.assertNotEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

private class QueueDeleteFakePhotoDataSource(private val photos: List<Photo>) : PhotoDataSource {
    override suspend fun loadPhotos(): List<Photo> = photos
}

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class DeleteQueueContractTest {
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
    fun `queueCurrentPhotoForDeletion immediately queues current photo and advances visible list`() {
        val photos = listOf(
            Photo(1L, mockk<Uri>(), "photo_1.jpg", 1L, 1000L),
            Photo(2L, mockk<Uri>(), "photo_2.jpg", 2L, 1000L),
            Photo(3L, mockk<Uri>(), "photo_3.jpg", 3L, 1000L)
        )
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val vm = PhotoViewModel(app, QueueDeleteFakePhotoDataSource(photos), prefs)

        vm.loadPhotos()
        val queued = vm.uiState.value.currentPhoto!!
        vm.queueCurrentPhotoForDeletion()
        val state = vm.uiState.value

        assertEquals(listOf(queued), state.deleteQueue)
        assertFalse(state.visiblePhotos.any { it.id == queued.id })
        assertNotEquals(queued.id, state.currentPhoto?.id)
        assertEquals(PhotoListState.Reviewable, state.screenState)
    }

    companion object {
        private const val PREFS_NAME = "delete_queue_contract_test_prefs"
    }
}
