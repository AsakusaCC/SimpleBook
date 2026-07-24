package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.ui.booklist.BookListViewModel
import com.ebookreader.simplebook.ui.bookmark.BookmarkViewModel
import com.ebookreader.simplebook.ui.collection.CollectionViewModel
import com.ebookreader.simplebook.ui.note.NoteViewModel
import com.ebookreader.simplebook.ui.reader.ReaderViewModel
import com.ebookreader.simplebook.ui.settings.SettingsViewModel
import com.ebookreader.simplebook.ui.sync.SyncViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val appModule = module {
    viewModelOf(::BookListViewModel)
    viewModelOf(::BookmarkViewModel)
    viewModelOf(::CollectionViewModel)
    viewModelOf(::NoteViewModel)
    viewModelOf(::ReaderViewModel)
    viewModelOf(::SettingsViewModel)
    viewModelOf(::SyncViewModel)
}
