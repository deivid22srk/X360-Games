package com.x360games.archivedownloader.network

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit

object RetrofitClient {
    private const val GITHUB_BASE_URL = "https://raw.githubusercontent.com/"
    private const val ARCHIVE_BASE_URL = "https://archive.org/"
    
    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BASIC
    }
    
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()
    
    private val githubRetrofit = Retrofit.Builder()
        .baseUrl(GITHUB_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    private val archiveRetrofit = Retrofit.Builder()
        .baseUrl(ARCHIVE_BASE_URL)
        .client(okHttpClient)
        .addConverterFactory(GsonConverterFactory.create())
        .build()
    
    val githubApi: GitHubApiService = githubRetrofit.create(GitHubApiService::class.java)
    val archiveApi: InternetArchiveService = archiveRetrofit.create(InternetArchiveService::class.java)
}
