package com.qunverio.browser.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.qunverio.browser.BuildConfig
import com.qunverio.browser.adblock.FilterManager
import com.qunverio.browser.data.SEARCH_ENGINES
import com.qunverio.browser.data.SettingsStore
import java.text.DateFormat
import java.util.Date

private val THEMES = listOf("system" to "System default", "light" to "Light", "dark" to "Dark")
private val ZOOMS = listOf(85 to "Small", 100 to "Default", 115 to "Large", 130 to "Larger", 150 to "Largest")

@Composable
fun SettingsScreen(
    settings: SettingsStore,
    filters: FilterManager,
    onBack: () -> Unit,
    onChanged: () -> Unit,
    onClearData: () -> Unit,
) {
    var dialog by remember { mutableStateOf<String?>(null) }

    Scaffold(containerColor = MaterialTheme.colorScheme.surface) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
        ) {
            ScreenHeader(title = "Settings", onBack = onBack)
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .verticalScroll(rememberScrollState()),
            ) {
                SectionHeader("General")
                SettingRow(
                    title = "Search engine",
                    subtitle = settings.searchEngineName,
                    onClick = { dialog = "search" },
                )
                SettingSwitch(
                    title = "Request desktop site by default",
                    subtitle = "New tabs open the desktop version of sites",
                    checked = settings.desktopSiteDefault,
                    onChange = { settings.setDesktopSiteDefault(it); onChanged() },
                )

                SectionHeader("Appearance")
                SettingRow(
                    title = "Theme",
                    subtitle = THEMES.firstOrNull { it.first == settings.themeMode }?.second ?: "System default",
                    onClick = { dialog = "theme" },
                )
                SettingRow(
                    title = "Text size",
                    subtitle = ZOOMS.firstOrNull { it.first == settings.textZoom }?.second ?: "${settings.textZoom}%",
                    onClick = { dialog = "zoom" },
                )

                SectionHeader("Privacy and security")
                SettingSwitch(
                    title = "Block ads and trackers",
                    subtitle = "${settings.filterRuleCount} host rules active",
                    checked = settings.adBlockEnabled,
                    onChange = { settings.setAdBlockEnabled(it); onChanged() },
                )
                SettingRow(
                    title = "Update filter lists",
                    subtitle = when {
                        filters.updating -> "Downloading…"
                        filters.status.isNotBlank() -> filters.status
                        settings.filterUpdatedAt == 0L -> "Using the small built-in list. Tap to download EasyList and EasyPrivacy."
                        else -> "Last updated " + DateFormat.getDateTimeInstance().format(Date(settings.filterUpdatedAt))
                    },
                    onClick = if (filters.updating) null else ({ filters.updateAsync() }),
                )
                SettingSwitch(
                    title = "Block third-party cookies",
                    subtitle = "Stops sites embedded in other pages from setting cookies. A few embedded logins may fail.",
                    checked = settings.blockThirdPartyCookies,
                    onChange = { settings.setBlockThirdPartyCookies(it); onChanged() },
                )
                SettingSwitch(
                    title = "Save browsing history",
                    subtitle = "Stored only on this device",
                    checked = settings.saveHistory,
                    onChange = { settings.setSaveHistory(it) },
                )
                SettingRow(
                    title = "Clear browsing data",
                    subtitle = "History, cookies, cache and site data",
                    onClick = { dialog = "clear" },
                )

                SectionHeader("Site settings")
                SettingSwitch(
                    title = "JavaScript",
                    subtitle = "Turning this off breaks most modern sites",
                    checked = settings.javascriptEnabled,
                    onChange = { settings.setJavascriptEnabled(it); onChanged() },
                )

                SectionHeader("About")
                SettingRow(
                    title = "Qunverio Browser",
                    subtitle = "Version ${BuildConfig.VERSION_NAME}. Uses your phone's Android System WebView, so keep WebView updated in Google Play.",
                )
            }
        }
    }

    when (dialog) {
        "search" -> ChoiceDialog(
            title = "Search engine",
            options = SEARCH_ENGINES.map { it.id to it.name },
            selected = settings.searchEngineId,
            onSelect = { settings.setSearchEngine(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "theme" -> ChoiceDialog(
            title = "Theme",
            options = THEMES,
            selected = settings.themeMode,
            onSelect = { settings.setThemeMode(it); dialog = null },
            onDismiss = { dialog = null },
        )
        "zoom" -> ChoiceDialog(
            title = "Text size",
            options = ZOOMS.map { it.first.toString() to it.second },
            selected = settings.textZoom.toString(),
            onSelect = { settings.setTextZoom(it.toInt()); onChanged(); dialog = null },
            onDismiss = { dialog = null },
        )
        "clear" -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Clear browsing data?") },
            text = { Text("This removes history, cookies, cached files and site data. You will be signed out of most sites.") },
            confirmButton = {
                TextButton(onClick = { onClearData(); dialog = null }) { Text("Clear") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun SettingRow(
    title: String,
    subtitle: String?,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge)
                if (subtitle != null) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            if (trailing != null) {
                Row(modifier = Modifier.width(56.dp), verticalAlignment = Alignment.CenterVertically) { trailing() }
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun SettingSwitch(title: String, subtitle: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    SettingRow(
        title = title,
        subtitle = subtitle,
        onClick = { onChange(!checked) },
        trailing = { Switch(checked = checked, onCheckedChange = null) },
    )
}

@Composable
private fun ChoiceDialog(
    title: String,
    options: List<Pair<String, String>>,
    selected: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column {
                options.forEach { (id, label) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(id) }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        RadioButton(selected = id == selected, onClick = null)
                        Text(label, modifier = Modifier.padding(start = 12.dp))
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
