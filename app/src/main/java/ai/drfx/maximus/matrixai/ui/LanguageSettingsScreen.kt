package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Language
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import ai.drfx.maximus.matrixai.R
import ai.drfx.maximus.matrixai.ui.theme.AppLanguage

@Composable
fun LanguageSettingsScreen(
    selected: AppLanguage,
    onChange: (AppLanguage) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxSize().verticalScroll(rememberScrollState())
            .padding(horizontal = 18.dp, vertical = 22.dp)
            .testTag("language_settings_screen"),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(Icons.Default.Language, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
            Text(stringResource(R.string.language_title), style = MaterialTheme.typography.titleLarge)
        }
        Text(
            stringResource(R.string.language_description),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
        AppLanguage.entries.forEach { language ->
            val active = language == selected
            Surface(
                modifier = Modifier.fillMaxWidth()
                    .testTag("language_option_${language.tag}")
                    .clickable { if (!active) onChange(language) },
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface,
                border = BorderStroke(
                    if (active) 2.dp else 1.dp,
                    if (active) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.outlineVariant
                )
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 60.dp)
                        .padding(horizontal = 14.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    RadioButton(
                        selected = active,
                        onClick = { if (!active) onChange(language) },
                        modifier = Modifier.testTag("radio_${language.tag}")
                    )
                    Text(language.nativeName, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.bodyLarge)
                    if (active) Text(
                        stringResource(R.string.language_active),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            }
        }
        HorizontalDivider()
        Text(
            stringResource(R.string.language_change_effect),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
    }
}
