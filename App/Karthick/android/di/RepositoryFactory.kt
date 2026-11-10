package com.kashyap.wizlyai.di

import android.content.Context
import android.util.Log
import com.kashyap.wizlyai.data.repository.AndroidLocalDocumentStorage
import com.kashyap.wizlyai.data.repository.AppSettingsRepository
import com.kashyap.wizlyai.data.repository.LocalDocumentStorage
import com.kashyap.wizlyai.ui.screens.documents.AndroidDocumentProvider
import com.kashyap.wizlyai.ui.screens.documents.DocumentProvider
import com.kashyap.wizlyai.ui.viewmodel.LocalDocumentsViewModel
import com.kashyap.wizlyai.data.repository.createAppSettingsRepository as createAppSettingsRepositoryImpl

/**
 * Factory for creating repository and storage instances on Android
 * Provides easy access to platform-specific implementations
 */
object RepositoryFactory {
    /**
     * Create LocalDocumentStorage instance for Android
     */
    fun createLocalDocumentStorage(context: Context): LocalDocumentStorage {
        Log.d("RepositoryFactory", "🏭 Creating LocalDocumentStorage")
        val storage = AndroidLocalDocumentStorage(context.applicationContext)
        Log.d("RepositoryFactory", "✅ LocalDocumentStorage created")
        return storage
    }

    /**
     * Create DocumentProvider instance for Android
     */
    fun createDocumentProvider(context: Context): DocumentProvider {
        Log.d("RepositoryFactory", "🏭 Creating DocumentProvider")
        val provider = AndroidDocumentProvider(context.applicationContext)
        Log.d("RepositoryFactory", "✅ DocumentProvider created")
        return provider
    }

    /**
     * Create LocalDocumentsViewModel instance for Android
     */
    fun createLocalDocumentsViewModel(context: Context): LocalDocumentsViewModel {
        return LocalDocumentsViewModel(context.applicationContext)
    }

    /**
     * Create AppSettingsRepository instance for Android
     */
    fun createAppSettingsRepository(context: Context): AppSettingsRepository {
        Log.d("RepositoryFactory", "🏭 Creating AppSettingsRepository")
        val repository = createAppSettingsRepositoryImpl(context.applicationContext)
        Log.d("RepositoryFactory", "✅ AppSettingsRepository created")
        return repository
    }
}
