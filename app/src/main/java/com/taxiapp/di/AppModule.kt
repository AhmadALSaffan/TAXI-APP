package com.taxiapp.di

import android.content.Context
import com.google.android.libraries.places.api.Places
import com.google.android.libraries.places.api.net.PlacesClient
import dagger.hilt.android.qualifiers.ApplicationContext
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.storage.FirebaseStorage
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseDatabase(): FirebaseDatabase = FirebaseDatabase.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseStorage(): FirebaseStorage = FirebaseStorage.getInstance()

    @Provides
    @Singleton
    fun provideFirebaseFunctions(): FirebaseFunctions = FirebaseFunctions.getInstance()

    @Provides
    @Singleton
    fun providePlacesClient(@ApplicationContext context: Context): PlacesClient = Places.createClient(context)
}
