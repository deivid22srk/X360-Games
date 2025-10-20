package com.x360games.archivedownloader.network

import com.x360games.archivedownloader.data.ArchiveMetadataResponse
import okhttp3.ResponseBody
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Header
import retrofit2.http.Path
import retrofit2.http.Streaming
import retrofit2.http.Url

interface InternetArchiveService {
    @GET("metadata/{identifier}")
    suspend fun getMetadata(@Path("identifier") identifier: String): ArchiveMetadataResponse
    
    @Streaming
    @GET
    suspend fun downloadFile(
        @Url fileUrl: String,
        @Header("Cookie") cookie: String? = null
    ): Response<ResponseBody>
    
    @Streaming
    @GET
    suspend fun downloadFileWithRange(
        @Url fileUrl: String,
        @Header("Range") range: String,
        @Header("Cookie") cookie: String? = null
    ): Response<ResponseBody>
}
