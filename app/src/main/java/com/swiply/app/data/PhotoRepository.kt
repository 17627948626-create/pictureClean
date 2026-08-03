package com.swiply.app.data

import android.content.ContentUris
import android.content.Context
import android.provider.MediaStore
import com.swiply.app.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) : PhotoDataSource {

    override suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        // 保守路径：查询和单张图片 URI 都使用 EXTERNAL_CONTENT_URI。
        // 先保证真机删除链路可用；多 volume 支持后续必须基于真机验证再做。
        val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI

        val projection = arrayOf(
            MediaStore.Images.Media._ID,
            MediaStore.Images.Media.DISPLAY_NAME,
            MediaStore.Images.Media.DATE_ADDED,
            MediaStore.Images.Media.SIZE
        )

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: context.getString(R.string.unknown_photo_name)
                val date = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val uri = ContentUris.withAppendedId(collection, id)
                photos.add(Photo(id, uri, name, date, size))
            }
        }

        photos
    }
}
