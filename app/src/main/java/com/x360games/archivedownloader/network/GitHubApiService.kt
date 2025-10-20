package com.x360games.archivedownloader.network

import com.x360games.archivedownloader.data.X360Collection
import retrofit2.http.GET

interface GitHubApiService {
    @GET("deivid22srk/X360-Games/refs/heads/main/X360-Games.json")
    suspend fun getX360Collection(): X360Collection
}
