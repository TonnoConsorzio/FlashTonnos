package com.example.di

import android.content.Context
import androidx.room.Room
import com.example.data.github.GithubApiService
import com.example.data.local.AppDatabase
import com.example.data.local.CardDao
import com.example.data.local.DeepDiveDao
import com.example.data.local.IndexEntryDao
import com.example.data.preferences.AppPreferences
import com.example.domain.repository.FlashcardRepository
import com.example.domain.repository.SyncRepository
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.moshi.MoshiConverterFactory

class AppContainer(private val context: Context) {

    val appPreferences = AppPreferences(context)

    val database: AppDatabase by lazy {
        Room.databaseBuilder(context, AppDatabase::class.java, "flashtonnos.db")
            .fallbackToDestructiveMigration()
            .build()
    }

    val cardDao: CardDao by lazy { database.cardDao() }
    val deepDiveDao: DeepDiveDao by lazy { database.deepDiveDao() }
    val indexEntryDao: IndexEntryDao by lazy { database.indexEntryDao() }

    private val moshi: Moshi = Moshi.Builder()
        .add(KotlinJsonAdapterFactory())
        .build()

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .writeTimeout(180, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    val githubApiService: GithubApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.github.com/")
            .client(okHttpClient)
            .addConverterFactory(MoshiConverterFactory.create(moshi))
            .build()
            .create(GithubApiService::class.java)
    }

    val flashcardRepository: FlashcardRepository by lazy {
        FlashcardRepository(
            githubApiService,
            cardDao,
            deepDiveDao,
            appPreferences,
            context
        )
    }

    val syncRepository: SyncRepository by lazy {
        SyncRepository(
            githubApiService,
            cardDao,
            deepDiveDao,
            indexEntryDao,
            appPreferences
        )
    }
}
