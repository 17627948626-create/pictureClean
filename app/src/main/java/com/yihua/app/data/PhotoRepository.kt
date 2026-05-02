package com.yihua.app.data

import android.content.ContentUris
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PhotoRepository(private val context: Context) : PhotoDataSource {

    override suspend fun loadPhotos(): List<Photo> = withContext(Dispatchers.IO) {
        val photos = mutableListOf<Photo>()

        // API 30+ 可以从 VOLUME_EXTERNAL 合并视图读取所有外部存储卷；
        // 单张图片 URI 必须再按每行的 VOLUME_NAME 构造，避免 SD 卡/多 volume 场景下
        // 用 primary external URI 错指非 primary volume 的图片。
        // API 29 没有 image 单行 volume-aware URI helper，因此查询和 URI 都收敛到 primary。
        val collection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        } else {
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI
        }

        val projection = buildList {
            add(MediaStore.Images.Media._ID)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                add(MediaStore.Images.Media.VOLUME_NAME)
            }
            add(MediaStore.Images.Media.DISPLAY_NAME)
            add(MediaStore.Images.Media.DATE_ADDED)
            add(MediaStore.Images.Media.SIZE)
        }.toTypedArray()

        val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"

        context.contentResolver.query(
            collection,
            projection,
            null,
            null,
            sortOrder
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
            val volumeNameColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                cursor.getColumnIndexOrThrow(MediaStore.Images.Media.VOLUME_NAME)
            } else {
                -1
            }
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME)
            val dateColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATE_ADDED)
            val sizeColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.SIZE)

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "未知"
                val date = cursor.getLong(dateColumn)
                val size = cursor.getLong(sizeColumn)
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val volumeName = cursor.getString(volumeNameColumn)
                    MediaStore.Images.Media.getContentUri(volumeName, id)
                } else {
                    ContentUris.withAppendedId(collection, id)
                }
                photos.add(Photo(id, uri, name, date, size))
            }
        }

        photos
    }
}
