package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.local.SimpleBookDatabase
import com.ebookreader.simplebook.data.local.getRoomDatabaseBuilder
import com.ebookreader.simplebook.data.local.dao.BookmarkDao
import com.ebookreader.simplebook.data.local.dao.FolderDao
import com.ebookreader.simplebook.data.local.dao.HighlightDao
import com.ebookreader.simplebook.data.local.dao.NoteDao
import com.ebookreader.simplebook.data.local.dao.ReadingProgressDao
import com.ebookreader.simplebook.data.local.dao.SyncLogDao
import com.ebookreader.simplebook.data.local.dao.BookDao
import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookRepositoryImpl
import com.ebookreader.simplebook.data.repository.BookmarkRepository
import com.ebookreader.simplebook.data.repository.FolderRepository
import com.ebookreader.simplebook.data.repository.FolderRepositoryImpl
import com.ebookreader.simplebook.data.repository.HighlightRepository
import com.ebookreader.simplebook.data.repository.NoteRepository
import com.ebookreader.simplebook.data.repository.ReadingProgressRepository
import com.ebookreader.simplebook.data.parser.EpubParser
import com.ebookreader.simplebook.data.parser.TxtParser
import com.ebookreader.simplebook.domain.service.BookService
import com.ebookreader.simplebook.domain.service.BookmarkService
import com.ebookreader.simplebook.domain.service.FolderService
import com.ebookreader.simplebook.domain.service.HighlightService
import com.ebookreader.simplebook.domain.service.NoteService
import com.ebookreader.simplebook.domain.service.ReadingService
import com.ebookreader.simplebook.domain.service.SyncService
import com.ebookreader.simplebook.domain.service.FileImportService
import com.google.gson.Gson
import com.google.gson.GsonBuilder
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.module

val dataModule = module {
    // Database (通过 expect/actual getRoomDatabaseBuilder)
    single { getRoomDatabaseBuilder().fallbackToDestructiveMigration(dropAllTables = true).build() }

    // DAOs
    single<BookDao> { get<SimpleBookDatabase>().bookDao() }
    single<ReadingProgressDao> { get<SimpleBookDatabase>().readingProgressDao() }
    single<BookmarkDao> { get<SimpleBookDatabase>().bookmarkDao() }
    single<HighlightDao> { get<SimpleBookDatabase>().highlightDao() }
    single<NoteDao> { get<SimpleBookDatabase>().noteDao() }
    single<SyncLogDao> { get<SimpleBookDatabase>().syncLogDao() }
    single<FolderDao> { get<SimpleBookDatabase>().folderDao() }

    // Repositories (interface -> impl binding)
    singleOf(::BookRepositoryImpl)
    single<BookRepository> { get<BookRepositoryImpl>() }
    singleOf(::FolderRepositoryImpl)
    single<FolderRepository> { get<FolderRepositoryImpl>() }

    // Repositories (concrete classes)
    singleOf(::BookmarkRepository)
    singleOf(::HighlightRepository)
    singleOf(::NoteRepository)
    singleOf(::ReadingProgressRepository)

    // Parsers
    singleOf(::EpubParser)
    singleOf(::TxtParser)

    // Gson
    single<Gson> { GsonBuilder().create() }

    // Domain Services
    singleOf(::BookService)
    singleOf(::BookmarkService)
    singleOf(::FolderService)
    singleOf(::HighlightService)
    singleOf(::NoteService)
    singleOf(::ReadingService)
    singleOf(::SyncService)
    // FileImportService — cross-platform core; sources (AndroidUriSource/DesktopFileSource)
    // are constructed at the call site (SAF / drag-and-drop), not injected.
    singleOf(::FileImportService)
}
