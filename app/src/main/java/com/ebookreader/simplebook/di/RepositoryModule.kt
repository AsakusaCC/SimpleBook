package com.ebookreader.simplebook.di

import com.ebookreader.simplebook.data.repository.BookRepository
import com.ebookreader.simplebook.data.repository.BookRepositoryImpl
import com.ebookreader.simplebook.data.repository.FolderRepository
import com.ebookreader.simplebook.data.repository.FolderRepositoryImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    abstract fun bindBookRepository(impl: BookRepositoryImpl): BookRepository

    @Binds
    abstract fun bindFolderRepository(impl: FolderRepositoryImpl): FolderRepository
}
