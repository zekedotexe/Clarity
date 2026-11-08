package com.example.video_tts_p2.data.api

import com.example.video_tts_p2.data.model.WordEntryDto
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface DictionaryApiService {

    @GET("api/v2/entries/en/{word}")
    suspend fun getWordEntries(
        @Path("word") word: String
    ): Response<List<WordEntryDto>>

    companion object {
        private const val BASE_URL = "https://api.dictionaryapi.dev/"

        fun create(): DictionaryApiService {
            val logging = HttpLoggingInterceptor().apply {
                level = HttpLoggingInterceptor.Level.BASIC
            }

            val client = OkHttpClient.Builder()
                .addInterceptor(logging)
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()

            return Retrofit.Builder()
                .baseUrl(BASE_URL)
                .client(client)
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(DictionaryApiService::class.java)
        }
    }
}
