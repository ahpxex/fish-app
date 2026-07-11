package com.fish.wellness.di

import android.content.Context
import androidx.room.Room
import com.fish.wellness.data.AppDatabase
import com.fish.wellness.data.dao.BlockedAppDao
import com.fish.wellness.data.dao.PolicyDao
import com.fish.wellness.data.dao.QuickBlockSessionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "fish_wellness.db")
            .addMigrations(AppDatabase.MIGRATION_1_3)
            .build()

    @Provides
    fun providePolicyDao(db: AppDatabase): PolicyDao = db.policyDao()

    @Provides
    fun provideBlockedAppDao(db: AppDatabase): BlockedAppDao = db.blockedAppDao()

    @Provides
    fun provideQuickBlockSessionDao(db: AppDatabase): QuickBlockSessionDao = db.quickBlockSessionDao()
}
