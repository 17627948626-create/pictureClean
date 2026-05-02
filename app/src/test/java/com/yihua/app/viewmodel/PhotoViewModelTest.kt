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
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

// ─── 测试辅助 ──────────────────────────────────────────────────────────────────

private class FakePhotoDataSource(private val photos: List<Photo>) : PhotoDataSource {
    override suspend fun loadPhotos(): List<Photo> = photos
}

// ─── 测试 ─────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34]) // compileSdk=35 超出 Robolectric 4.12 自动选择范围，显式固定到 34
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
        // 每次测试前清空 SharedPreferences，确保状态隔离
        app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    // ── 工具方法 ───────────────────────────────────────────────────────────────

    private fun makePhotos(count: Int): List<Photo> = (1L..count).map { id ->
        Photo(id = id, uri = mockk<Uri>(), displayName = "photo_$id.jpg", dateAdded = id, size = 1000L)
    }

    private fun makeViewModel(photos: List<Photo> = emptyList()): PhotoViewModel {
        val prefs = app.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return PhotoViewModel(app, FakePhotoDataSource(photos), prefs)
    }

    // ── 1. 加载后 currentPhoto 正确 ───────────────────────────────────────────

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

    // ── 2. 左右滑不越界 ────────────────────────────────────────────────────────

    @Test
    fun `swipeRight does not exceed last photo`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeRight() // index 0 → 1
        vm.swipeRight() // should stay at 1
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    @Test
    fun `swipeLeft does not go below first photo`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeLeft() // already at 0, should stay
        assertEquals(0, vm.uiState.value.currentIndex)
    }

    // ── 3. 上划后照片进入 deleteQueue ─────────────────────────────────────────

    @Test
    fun `swipeUp adds current photo to deleteQueue`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val queued = vm.uiState.value.currentPhoto!!
        vm.swipeUp()
        assertTrue(queued.id in vm.uiState.value.deleteQueueIds)
    }

    // ── 4. 上划后照片从 visiblePhotos 消失 ────────────────────────────────────

    @Test
    fun `swipeUp removes photo from visiblePhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val queued = vm.uiState.value.currentPhoto!!
        vm.swipeUp()
        assertFalse(vm.uiState.value.visiblePhotos.any { it.id == queued.id })
        assertEquals(2, vm.uiState.value.visiblePhotos.size)
    }

    // ── 5. 上划最后一张时 currentIndex 钳制正确 ───────────────────────────────

    @Test
    fun `swipeUp on last photo clamps currentIndex to new last`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeRight(); vm.swipeRight() // go to index 2
        vm.swipeUp()
        assertEquals(1, vm.uiState.value.currentIndex)
    }

    // ── 6. 撤销后照片恢复进 visiblePhotos ─────────────────────────────────────

    @Test
    fun `undoDelete restores photo to visiblePhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val deleted = vm.uiState.value.currentPhoto!!
        vm.swipeUp()

        val didUndo = vm.undoDelete()

        assertTrue(didUndo)
        assertTrue(vm.uiState.value.visiblePhotos.any { it.id == deleted.id })
    }

    // ── 7. 撤销后 currentIndex 回到删除位置 ───────────────────────────────────

    @Test
    fun `undoDelete restores currentIndex to pre-delete position`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        val prevIndex = vm.uiState.value.currentIndex // 0
        vm.swipeUp()
        vm.undoDelete()
        assertEquals(prevIndex, vm.uiState.value.currentIndex)
    }

    // ── 8. 全部照片上划 → AllQueuedForDelete ─────────────────────────────────

    @Test
    fun `all photos queued transitions to AllQueuedForDelete`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        repeat(3) { vm.swipeUp() }
        assertEquals(PhotoListState.AllQueuedForDelete, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.visiblePhotos.isEmpty())
        assertEquals(3, vm.uiState.value.deleteQueue.size)
    }

    // ── 9. 确认删除后队列清空，照片从 allPhotos 移除 ─────────────────────────

    @Test
    fun `onDeleteCompleted clears deleteQueue and removes photos from allPhotos`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp() // queue photos[0]
        vm.onDeleteCompleted()
        assertTrue(vm.uiState.value.deleteQueue.isEmpty())
        assertEquals(2, vm.uiState.value.allPhotos.size)
        assertEquals(PhotoListState.Reviewable, vm.uiState.value.screenState)
    }

    // ── 10. 位置不匹配时撤销无效 ──────────────────────────────────────────────

    @Test
    fun `undoDelete returns false when not at deletion position`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp()     // delete photos[0], now at new index 0 (photos[1])
        vm.swipeRight()  // move to index 1 — no longer at deletion position
        val didUndo = vm.undoDelete()
        assertFalse(didUndo)
        assertEquals(1, vm.uiState.value.deleteQueue.size)
    }

    // ── 11. removeFromDeleteQueue 同步清理 deleteHistory ──────────────────────

    @Test
    fun `removeFromDeleteQueue also removes entry from deleteHistory`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp() // queue photos[0]
        val queuedPhoto = vm.uiState.value.deleteQueue.first()
        vm.removeFromDeleteQueue(queuedPhoto)
        assertTrue(vm.uiState.value.deleteHistory.none { it.photo.id == queuedPhoto.id })
        assertTrue(vm.uiState.value.visiblePhotos.any { it.id == queuedPhoto.id })
    }

    // ── 12. 取消删除后队列保留 ────────────────────────────────────────────────

    @Test
    fun `deleteQueue is preserved when delete is not completed`() {
        val photos = makePhotos(3)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp()
        vm.swipeUp()
        // 假设用户取消了系统删除弹窗，不调用 onDeleteCompleted()
        assertEquals(2, vm.uiState.value.deleteQueue.size)
        assertEquals(2, vm.uiState.value.deleteHistory.size)
    }

    // ── 13. 多次确认删除后 allPhotos 持续缩减 ────────────────────────────────

    @Test
    fun `multiple onDeleteCompleted calls each reduce allPhotos correctly`() {
        val photos = makePhotos(4)
        val vm = makeViewModel(photos)
        vm.loadPhotos()

        vm.swipeUp() // queue photos[0]
        vm.onDeleteCompleted()
        assertEquals(3, vm.uiState.value.allPhotos.size)

        vm.swipeUp() // queue photos[1]
        vm.onDeleteCompleted()
        assertEquals(2, vm.uiState.value.allPhotos.size)
    }

    // ── 14. 删除全部后 onDeleteCompleted → EmptyLibrary ───────────────────────

    @Test
    fun `onDeleteCompleted all photos transitions to EmptyLibrary`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp()
        vm.swipeUp()
        vm.onDeleteCompleted()
        assertEquals(PhotoListState.EmptyLibrary, vm.uiState.value.screenState)
        assertTrue(vm.uiState.value.allPhotos.isEmpty())
    }

    // ── 15. removeFromDeleteQueue 恢复后 screenState 回到 Reviewable ──────────

    @Test
    fun `removeFromDeleteQueue when all queued restores screenState to Reviewable`() {
        val photos = makePhotos(2)
        val vm = makeViewModel(photos)
        vm.loadPhotos()
        vm.swipeUp()
        vm.swipeUp()
        assertEquals(PhotoListState.AllQueuedForDelete, vm.uiState.value.screenState)

        val photo = vm.uiState.value.deleteQueue.first()
        vm.removeFromDeleteQueue(photo)
        assertEquals(PhotoListState.Reviewable, vm.uiState.value.screenState)
        assertEquals(1, vm.uiState.value.visiblePhotos.size)
    }
}
