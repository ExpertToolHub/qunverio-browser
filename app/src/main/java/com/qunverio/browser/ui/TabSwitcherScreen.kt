package com.qunverio.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.qunverio.browser.web.BrowserTab
import com.qunverio.browser.web.TabManager
import com.qunverio.browser.web.hostOf

@Composable
fun TabSwitcherScreen(
    manager: TabManager,
    onSelect: (Long) -> Unit,
    onClose: (Long) -> Unit,
    onNewTab: () -> Unit,
    onCloseAll: () -> Unit,
    onBack: () -> Unit,
) {
    val count = manager.tabs.size
    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScreenHeader(title = if (count == 1) "1 tab" else "$count tabs", onBack = onBack) {
                TextButton(onClick = onCloseAll) { Text("Close all") }
                IconButton(onClick = onNewTab) {
                    Icon(Icons.Filled.Add, contentDescription = "New tab")
                }
            }
            Surface(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                color = MaterialTheme.colorScheme.background,
            ) {
                LazyVerticalGrid(
                    columns = GridCells.Fixed(2),
                    contentPadding = PaddingValues(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    items(manager.tabs, key = { it.id }) { tab ->
                        TabCard(
                            tab = tab,
                            isCurrent = tab.id == manager.currentId,
                            onSelect = { onSelect(tab.id) },
                            onClose = { onClose(tab.id) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TabCard(tab: BrowserTab, isCurrent: Boolean, onSelect: () -> Unit, onClose: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    val host = hostOf(tab.state.pageUrl)
    val title = tab.state.title.ifBlank { host ?: "New tab" }
    Column(
        modifier = Modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.primaryContainer)
            .border(
                width = if (isCurrent) 2.dp else 1.dp,
                color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = shape,
            )
            .clickable(onClick = onSelect)
            .padding(start = 14.dp, top = 6.dp, end = 6.dp, bottom = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onClose, modifier = Modifier.size(36.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Close tab",
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Text(
            host ?: "Start page",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(Modifier.height(40.dp))
    }
}
