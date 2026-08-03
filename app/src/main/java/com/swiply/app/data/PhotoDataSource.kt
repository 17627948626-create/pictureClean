package com.swiply.app.data

interface PhotoDataSource {
    suspend fun loadPhotos(): List<Photo>
}
