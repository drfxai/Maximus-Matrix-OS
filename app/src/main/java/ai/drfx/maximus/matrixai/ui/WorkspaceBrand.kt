package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Hub
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun WorkspaceBrand(modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center) {
        Text("MAXIMUS", color = MaterialTheme.colorScheme.primary,
            style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, letterSpacing = 3.sp)
    }
}
