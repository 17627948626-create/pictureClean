package com.yihua.app.data

interface PhotoDataSource {
    suspend fun loadPhotos(): List<Photo>
}
