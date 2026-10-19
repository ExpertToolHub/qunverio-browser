package com.qunverio.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qunverio.browser.data.BookmarkStore
import com.qunverio.browser.data.HistoryStore

/** Bookmarks (tab 0) and History (tab 1). */
@Composable
fun LibraryScreen(
    initialTab: Int,
    bookmarks: BookmarkStore,
    history: HistoryStore,
    onOpen: (String) -> Unit,
    onBack: () -> Unit,
) {
    var tab by remember { mutableIntStateOf(initialTab) }
    var query by remember { mutableStateOf("") }
    var confirmClear by remember { mutableStateOf(false) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScreenHeader(title = if (tab == 0) "Bookmarks" else "History", onBack = onBack) {
                if (tab == 1 && history.entries.isNotEmpty()) {
                    TextButton(onClick = { confirmClear = true }) { Text("Clear all") }
                }
            }
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("Bookmarks") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("History") })
            }
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background),
            ) {
                if (tab == 0) {
                    if (bookmarks.items.isEmpty()) {
                        EmptyText("No bookmarks yet. Open a page and choose “Add bookmark” from the menu.")
                    } else {
                        LazyColumn {
                            items(bookmarks.items, key = { it.url }) { b ->
                                EntryRow(
                                    title = b.title,
                                    subtitle = b.url,
                                    onClick = { onOpen(b.url) },
                                    onDelete = { bookmarks.remove(b) },
                                )
                            }
                        }
                    }
                } else {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        placeholder = { Text("Search history") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                    )
                    val shown = history.entries.filter {
                        query.isBlank() ||
                            it.title.contains(query, ignoreCase = true) ||
                            it.url.contains(query, ignoreCase = true)
                    }
                    if (shown.isEmpty()) {
                        EmptyText(if (history.entries.isEmpty()) "No history yet." else "No matches.")
                    } else {
                        LazyColumn {
                            items(shown, key = { "${it.time}|${it.url}" }) { e ->
                                EntryRow(
                                    title = e.title,
                                    subtitle = e.url,
                                    onClick = { onOpen(e.url) },
                                    onDelete = { history.remove(e) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    if (confirmClear) {
        AlertDialog(
            onDismissRequest = { confirmClear = false },
            title = { Text("Clear history?") },
            text = { Text("All visited pages will be removed from this device.") },
            confirmButton = {
                TextButton(onClick = { history.clear(); confirmClear = false }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { confirmClear = false }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun EmptyText(text: String) {
    Box(modifier = Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun EntryRow(title: String, subtitle: String, onClick: () -> Unit, onDelete: () -> Unit) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 16.dp, top = 4.dp, bottom = 4.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.Delete, contentDescription = "Delete")
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}
