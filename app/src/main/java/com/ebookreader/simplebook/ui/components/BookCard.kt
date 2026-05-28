package com.ebookreader.simplebook.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AutoStories
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.ebookreader.simplebook.domain.model.Book
import java.io.File

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookCard(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    unknownAuthorText: String = "未知",
    percentage: Double = 0.0,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick
            )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(if (compact) 8.dp else 12.dp),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(3f / 4f)
            ) {
                val placeholderBg = MaterialTheme.colorScheme.primaryContainer
                val placeholderFg = MaterialTheme.colorScheme.onPrimaryContainer

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(placeholderBg),
                    contentAlignment = Alignment.Center
                ) {
                    if (book.coverPath != null) {
                        AsyncImage(
                            model = File(book.coverPath),
                            contentDescription = book.title,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    horizontal = if (compact) 6.dp else 16.dp,
                                    vertical = if (compact) 10.dp else 20.dp
                                )
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(if (compact) 20.dp else 32.dp),
                                tint = placeholderFg.copy(alpha = 0.5f)
                            )
                            if (!compact) {
                                Spacer(modifier = Modifier.height(10.dp))
                                Text(
                                    text = book.title,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = placeholderFg.copy(alpha = 0.65f),
                                    maxLines = 3,
                                    overflow = TextOverflow.Ellipsis,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }

                if (percentage > 0.0) {
                    LinearProgressIndicator(
                        progress = { percentage.toFloat() },
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .height(3.dp)
                            .clip(RoundedCornerShape(topStart = 0.dp, topEnd = 0.dp)),
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                        trackColor = Color.White.copy(alpha = 0.3f),
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = if (compact) 4.dp else 6.dp)
        ) {
            Text(
                text = book.title,
                style = if (compact) MaterialTheme.typography.bodySmall
                    else MaterialTheme.typography.titleSmall,
                maxLines = if (compact) 1 else 2,
                overflow = TextOverflow.Ellipsis
            )

            val authorText = book.author.ifBlank { unknownAuthorText }
            if (compact) {
                Text(
                    text = authorText,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 1.dp)
                )
            } else if (percentage > 0.0) {
                Text(
                    text = "$authorText · ${(percentage * 100).toInt()}% 已读",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            } else {
                Text(
                    text = authorText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun BookListItem(
    book: Book,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    unknownAuthorText: String = "未知",
    percentage: Double = 0.0,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = onLongClick
                )
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            Card(
                modifier = Modifier.width(80.dp),
                shape = RoundedCornerShape(8.dp),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Box(
                    modifier = Modifier
                        .width(80.dp)
                        .aspectRatio(3f / 4f)
                ) {
                    val placeholderBg = MaterialTheme.colorScheme.primaryContainer
                    val placeholderFg = MaterialTheme.colorScheme.onPrimaryContainer

                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(placeholderBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (book.coverPath != null) {
                            AsyncImage(
                                model = File(book.coverPath),
                                contentDescription = book.title,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.AutoStories,
                                contentDescription = null,
                                modifier = Modifier.size(24.dp),
                                tint = placeholderFg.copy(alpha = 0.5f)
                            )
                        }
                    }

                    if (percentage > 0.0) {
                        LinearProgressIndicator(
                            progress = { percentage.toFloat() },
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .height(2.dp),
                            color = MaterialTheme.colorScheme.primary.copy(alpha = 0.85f),
                            trackColor = Color.White.copy(alpha = 0.3f),
                        )
                    }
                }
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = book.title,
                    style = MaterialTheme.typography.titleLarge,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                val authorText = book.author.ifBlank { unknownAuthorText }
                Text(
                    text = authorText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 2.dp)
                )
                if (percentage > 0.0) {
                    Row(
                        modifier = Modifier
                            .padding(top = 10.dp)
                            .fillMaxWidth(0.7f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        LinearProgressIndicator(
                            progress = { percentage.toFloat() },
                            modifier = Modifier
                                .weight(1f)
                                .height(4.dp)
                                .clip(RoundedCornerShape(2.dp)),
                            color = MaterialTheme.colorScheme.primary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant,
                        )
                        Text(
                            text = "${(percentage * 100).toInt()}%",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f)
        )
    }
}
