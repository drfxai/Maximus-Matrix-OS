package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared two-line identity for Brain, Chat, Work and Hub. No remote data is implied. */
@Composable
internal fun WorkspaceBrand(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 5.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text("MAXIMUS", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 3.sp,
            maxLines = 1)
        Text("M A T R I X   O S", color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 8.sp, letterSpacing = 1.1.sp, maxLines = 1)
    }
}
