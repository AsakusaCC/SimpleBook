package com.ebookreader.simplebook.di

import android.content.Context
import androidx.room.Room
import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.FolderDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
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
    fun provideDatabase(
        @ApplicationContext context: Context
    ): SimpleBookDatabase =
        Room.databaseBuilder(
            context,
            SimpleBookDatabase::class.java,
            "simplebook.db"
        )
            .addMigrations(
                SimpleBookDatabase.MIGRATION_1_2,
                SimpleBookDatabase.MIGRATION_2_3,
                SimpleBookDatabase.MIGRATION_3_4
            )
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun provideBookDao(db: SimpleBookDatabase): BookDao = db.bookDao()

    @Provides
    fun provideReadingProgressDao(db: SimpleBookDatabase): ReadingProgressDao =
        db.readingProgressDao()

    @Provides
    fun provideBookmarkDao(db: SimpleBookDatabase): BookmarkDao = db.bookmarkDao()

    @Provides
    fun provideHighlightDao(db: SimpleBookDatabase): HighlightDao = db.highlightDao()

    @Provides
    fun provideNoteDao(db: SimpleBookDatabase): NoteDao = db.noteDao()

    @Provides
    fun provideSyncLogDao(db: SimpleBookDatabase): SyncLogDao = db.syncLogDao()

    @Provides
    fun provideFolderDao(db: SimpleBookDatabase): FolderDao = db.folderDao()
}
