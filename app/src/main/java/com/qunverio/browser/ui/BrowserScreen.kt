package com.qunverio.browser.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.qunverio.browser.adblock.hostInSet
import com.qunverio.browser.data.BookmarkStore
import com.qunverio.browser.data.SettingsStore
import com.qunverio.browser.web.BrowserState
import com.qunverio.browser.web.BrowserTab
import com.qunverio.browser.web.SecurityState
import com.qunverio.browser.web.TabManager
import com.qunverio.browser.web.hostOf

class BrowserActions(
    val onSubmit: (String) -> Unit,
    val onBack: () -> Unit,
    val onForward: () -> Unit,
    val onHome: () -> Unit,
    val onReloadOrStop: () -> Unit,
    val onRetry: () -> Unit,
    val onShare: () -> Unit,
    val onCopy: () -> Unit,
    val onToggleDesktop: () -> Unit,
    val onToggleSiteAdBlock: () -> Unit,
    val onToggleBookmark: () -> Unit,
    val onNewTab: () -> Unit,
    val onOpenTabs: () -> Unit,
    val onOpenSettings: () -> Unit,
    val onOpenBookmarks: () -> Unit,
    val onOpenHistory: () -> Unit,
    val onConfirmExternal: (String) -> Unit,
    val onDismissExternal: () -> Unit,
)

@Composable
fun BrowserScreen(
    manager: TabManager,
    settings: SettingsStore,
    bookmarks: BookmarkStore,
    actions: BrowserActions,
) {
    val tab = manager.current ?: return
    val state = tab.state
    val snackbarHostState = remember { SnackbarHostState() }

    // Back: step back in the page, else close the tab (if there are others), else leave the app.
    BackHandler(enabled = state.canGoBack || manager.tabs.size > 1) { actions.onBack() }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            state.message = null
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            TopBar(tab = tab, settings = settings, bookmarks = bookmarks, actions = actions)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                key(tab.id, tab.generation) {
                    AndroidView(
                        factory = { manager.viewFor(tab) },
                        modifier = Modifier.fillMaxSize(),
                    )
                }

                val error = state.errorMessage
                if (error != null) {
                    ErrorPage(message = error, onRetry = actions.onRetry)
                } else if (state.pageUrl == null) {
                    StartPage(onOpen = actions.onSubmit)
                }
            }

            BottomBar(state = state, tabCount = manager.tabs.size, actions = actions)
        }
    }

    state.pendingExternalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = actions.onDismissExternal,
            title = { Text("Open in another app?") },
            text = { Text("This page wants to open:\n${url.take(200)}") },
            confirmButton = {
                TextButton(onClick = { actions.onConfirmExternal(url) }) { Text("Open") }
            },
            dismissButton = {
                TextButton(onClick = actions.onDismissExternal) { Text("Cancel") }
            },
        )
    }
}

@Composable
private fun TopBar(
    tab: BrowserTab,
    settings: SettingsStore,
    bookmarks: BookmarkStore,
    actions: BrowserActions,
) {
    val state = tab.state
    var menuOpen by remember { mutableStateOf(false) }
    val hasPage = state.pageUrl != null
    val host = hostOf(state.pageUrl)
    val siteBlocking = host != null && !hostInSet(settings.adBlockAllowedHosts, host)
    val bookmarked = bookmarks.contains(state.pageUrl)

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            key(tab.id) {
                AddressPill(state = state, onSubmit = actions.onSubmit, modifier = Modifier.weight(1f))
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("New tab") },
                        onClick = { menuOpen = false; actions.onNewTab() },
                    )
                    DropdownMenuItem(
                        text = { Text("Bookmarks") },
                        onClick = { menuOpen = false; actions.onOpenBookmarks() },
                    )
                    DropdownMenuItem(
                        text = { Text("History") },
                        onClick = { menuOpen = false; actions.onOpenHistory() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text(if (bookmarked) "Remove bookmark" else "Add bookmark") },
                        enabled = hasPage,
                        onClick = { menuOpen = false; actions.onToggleBookmark() },
                    )
                    DropdownMenuItem(
                        text = { Text("Share link") },
                        enabled = hasPage,
                        onClick = { menuOpen = false; actions.onShare() },
                    )
                    DropdownMenuItem(
                        text = { Text("Copy link") },
                        enabled = hasPage,
                        onClick = { menuOpen = false; actions.onCopy() },
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Desktop site") },
                        trailingIcon = { Checkbox(checked = state.desktopSite, onCheckedChange = null) },
                        onClick = { menuOpen = false; actions.onToggleDesktop() },
                    )
                    DropdownMenuItem(
                        text = { Text("Block ads on this site") },
                        enabled = host != null && settings.adBlockEnabled,
                        trailingIcon = { Checkbox(checked = siteBlocking, onCheckedChange = null) },
                        onClick = { menuOpen = false; actions.onToggleSiteAdBlock() },
                    )
                    DropdownMenuItem(
                        text = { Text("Blocked on this page: ${state.blockedCount}") },
                        enabled = false,
                        onClick = {},
                    )
                    HorizontalDivider()
                    DropdownMenuItem(
                        text = { Text("Settings") },
                        onClick = { menuOpen = false; actions.onOpenSettings() },
                    )
                }
            }
        }

        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp),
            )
        } else {
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        }
    }
}

@Composable
private fun AddressPill(state: BrowserState, onSubmit: (String) -> Unit, modifier: Modifier = Modifier) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(state.pageUrl, focused) {
        if (!focused) field = TextFieldValue(state.pageUrl.orEmpty())
    }

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (focused) 2.dp else 1.dp,
            color = if (focused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline,
        ),
    ) {
        Row(
            modifier = Modifier
                .heightIn(min = 48.dp)
                .padding(start = 14.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SecurityIcon(state.security)
            Spacer(Modifier.width(10.dp))
            BasicTextField(
                value = field,
                onValueChange = { field = it },
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Go,
                ),
                keyboardActions = KeyboardActions(onGo = {
                    focusManager.clearFocus()
                    onSubmit(field.text)
                }),
                modifier = Modifier
                    .weight(1f)
                    .onFocusChanged { f ->
                        focused = f.isFocused
                        if (f.isFocused) field = field.copy(selection = TextRange(0, field.text.length))
                    },
                decorationBox = { inner ->
                    Box(contentAlignment = Alignment.CenterStart) {
                        if (field.text.isEmpty()) {
                            Text(
                                "Search or type address",
                                style = MaterialTheme.typography.bodyLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        inner()
                    }
                },
            )
            if (focused && field.text.isNotEmpty()) {
                IconButton(onClick = { field = TextFieldValue("") }) {
                    Icon(Icons.Filled.Close, contentDescription = "Clear address")
                }
            } else {
                Spacer(Modifier.width(10.dp))
            }
        }
    }
}

@Composable
private fun SecurityIcon(security: SecurityState) {
    val scheme = MaterialTheme.colorScheme
    val (icon, tint, description) = when (security) {
        SecurityState.SECURE -> Triple(Icons.Filled.Lock, scheme.primary, "Secure connection (HTTPS)")
        SecurityState.INSECURE -> Triple(Icons.Filled.Warning, scheme.error, "Not secure (HTTP)")
        SecurityState.ERROR -> Triple(Icons.Filled.Warning, scheme.error, "Connection problem")
        SecurityState.NONE -> Triple(Icons.Filled.Search, scheme.onSurfaceVariant, "Search")
    }
    Icon(icon, contentDescription = description, tint = tint, modifier = Modifier.size(20.dp))
}

@Composable
private fun BottomBar(state: BrowserState, tabCount: Int, actions: BrowserActions) {
    Column {
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onBack, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = actions.onForward, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = actions.onHome) {
                Icon(Icons.Filled.Home, contentDescription = "Home")
            }
            IconButton(onClick = actions.onReloadOrStop, enabled = state.pageUrl != null) {
                if (state.isLoading) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop loading")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            }
            IconButton(onClick = actions.onOpenTabs) {
                Box(
                    modifier = Modifier
                        .size(26.dp)
                        .border(2.dp, MaterialTheme.colorScheme.onSurface, RoundedCornerShape(7.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        tabCount.toString(),
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

@Composable
private fun ErrorPage(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Icon(
                Icons.Filled.Warning,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(48.dp),
            )
            Spacer(Modifier.height(16.dp))
            Text("Can't open this page", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(message, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
            Spacer(Modifier.height(24.dp))
            Button(onClick = onRetry) { Text("Try again") }
        }
    }
}

private val SHORTCUTS = listOf(
    "Google" to "https://www.google.com",
    "YouTube" to "https://www.youtube.com",
    "Wikipedia" to "https://www.wikipedia.org",
    "GitHub" to "https://github.com",
    "Gmail" to "https://mail.google.com",
    "Maps" to "https://maps.google.com",
)

@Composable
private fun StartPage(onOpen: (String) -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp, vertical = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                "Qunverio",
                style = MaterialTheme.typography.headlineLarge,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Fast, private browsing",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(32.dp))

            SHORTCUTS.chunked(2).forEach { rowItems ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    rowItems.forEach { (label, url) ->
                        ShortcutCard(label = label, modifier = Modifier.weight(1f)) { onOpen(url) }
                    }
                    if (rowItems.size == 1) Spacer(Modifier.weight(1f))
                }
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun ShortcutCard(label: String, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(vertical = 22.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.onPrimaryContainer,
        )
    }
}
