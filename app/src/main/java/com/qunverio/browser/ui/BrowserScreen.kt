package com.qunverio.browser.ui

import android.webkit.WebView
import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.qunverio.browser.web.BrowserState
import com.qunverio.browser.web.SecurityState

@Composable
fun BrowserScreen(
    state: BrowserState,
    webView: WebView,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
    onRetry: () -> Unit,
    onConfirmExternal: (String) -> Unit,
    onDismissExternal: () -> Unit,
) {
    val snackbarHostState = remember { SnackbarHostState() }

    BackHandler(enabled = state.canGoBack) { onBack() }

    LaunchedEffect(state.message) {
        val text = state.message
        if (text != null) {
            snackbarHostState.showSnackbar(text)
            state.message = null
        }
    }

    Scaffold(snackbarHost = { SnackbarHost(snackbarHostState) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            AddressBar(state = state, onSubmit = onSubmit)

            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            ) {
                AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())

                val error = state.errorMessage
                if (error != null) {
                    ErrorPage(message = error, onRetry = onRetry)
                } else if (state.pageUrl == null) {
                    StartPage()
                }
            }

            BottomBar(
                state = state,
                onBack = onBack,
                onForward = onForward,
                onReloadOrStop = onReloadOrStop,
            )
        }
    }

    state.pendingExternalUrl?.let { url ->
        AlertDialog(
            onDismissRequest = onDismissExternal,
            title = { Text("Open in another app?") },
            text = { Text("This page wants to open:\n${url.take(200)}") },
            confirmButton = { TextButton(onClick = { onConfirmExternal(url) }) { Text("Open") } },
            dismissButton = { TextButton(onClick = onDismissExternal) { Text("Cancel") } },
        )
    }
}

@Composable
private fun AddressBar(state: BrowserState, onSubmit: (String) -> Unit) {
    val focusManager = LocalFocusManager.current
    var focused by remember { mutableStateOf(false) }
    var field by remember { mutableStateOf(TextFieldValue("")) }

    LaunchedEffect(state.pageUrl, focused) {
        if (!focused) field = TextFieldValue(state.pageUrl.orEmpty())
    }

    Column {
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 8.dp),
            shape = RoundedCornerShape(24.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
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
        if (state.isLoading) {
            LinearProgressIndicator(
                progress = { state.progress / 100f },
                modifier = Modifier.fillMaxWidth(),
            )
        } else {
            Spacer(Modifier.height(4.dp))
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
private fun BottomBar(
    state: BrowserState,
    onBack: () -> Unit,
    onForward: () -> Unit,
    onReloadOrStop: () -> Unit,
) {
    Surface(tonalElevation = 3.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            IconButton(onClick = onBack, enabled = state.canGoBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
            }
            IconButton(onClick = onForward, enabled = state.canGoForward) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Forward")
            }
            IconButton(onClick = onReloadOrStop, enabled = state.pageUrl != null) {
                if (state.isLoading) {
                    Icon(Icons.Filled.Close, contentDescription = "Stop loading")
                } else {
                    Icon(Icons.Filled.Refresh, contentDescription = "Reload")
                }
            }
        }
    }
}

@Composable
private fun ErrorPage(message: String, onRetry: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize()) {
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

@Composable
private fun StartPage() {
    Surface(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Text("Qunverio", style = MaterialTheme.typography.headlineLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                "Search or type an address to start.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
