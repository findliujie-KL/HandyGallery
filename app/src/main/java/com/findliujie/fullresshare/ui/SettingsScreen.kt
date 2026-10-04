package com.findliujie.fullresshare.ui

import android.os.Build
import android.provider.MediaStore
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.findliujie.fullresshare.AppLocale
import com.findliujie.fullresshare.R
import com.findliujie.fullresshare.Store
import com.findliujie.fullresshare.ThemeMode
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(store: Store, onBack: () -> Unit, onLanguage: (String) -> Unit, onManageMedia: () -> Unit) {
    val context = LocalContext.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).verticalScroll(rememberScrollState())) {
            Section(stringResource(R.string.theme))
            val themes = listOf(
                ThemeMode.System to R.string.theme_system,
                ThemeMode.Light to R.string.theme_light,
                ThemeMode.Dark to R.string.theme_dark,
            )
            themes.forEach { (mode, label) -> Choice(stringResource(label), store.theme == mode) { store.chooseTheme(mode) } }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(stringResource(R.string.language))
            AppLocale.choices.forEach { tag ->
                // Each language is listed under its own name so it can be found whatever the current one is.
                val name = if (tag.isEmpty()) stringResource(R.string.language_system) else Locale.forLanguageTag(tag).let { locale ->
                    locale.getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
                }
                Choice(name, store.language == tag) { if (store.language != tag) onLanguage(tag) }
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            Section(stringResource(R.string.editor))
            Toggle(stringResource(R.string.start_in_crop), stringResource(R.string.start_in_crop_desc), store.startInCrop, store::chooseStartInCrop)
            Toggle(stringResource(R.string.confirm_exit), stringResource(R.string.confirm_exit_desc), store.confirmExit, store::chooseConfirmExit)

            HorizontalDivider(Modifier.padding(vertical = 8.dp))
            val supported = Build.VERSION.SDK_INT >= 31
            val granted = supported && MediaStore.canManageMedia(context)
            Column(Modifier.fillMaxWidth().clickable(supported, onClick = onManageMedia).padding(horizontal = 20.dp, vertical = 14.dp)) {
                Text(stringResource(R.string.skip_prompts), style = MaterialTheme.typography.titleMedium)
                Text(
                    stringResource(
                        when {
                            !supported -> R.string.skip_prompts_unsupported
                            granted -> R.string.skip_prompts_on
                            else -> R.string.skip_prompts_off
                        },
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun Section(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 4.dp),
    )
}

@Composable
private fun Toggle(title: String, detail: String, on: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onChange(!on) }.padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = on, onCheckedChange = onChange)
    }
}

@Composable
private fun Choice(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(label, style = MaterialTheme.typography.bodyLarge)
    }
}
