package com.ebookreader.simplebook.di

import android.content.Context
import androidx.room.Room
import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.ConflictDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
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
        ).addMigrations(SimpleBookDatabase.MIGRATION_1_2).build()

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
    fun provideConflictDao(db: SimpleBookDatabase): ConflictDao = db.conflictDao()
}
