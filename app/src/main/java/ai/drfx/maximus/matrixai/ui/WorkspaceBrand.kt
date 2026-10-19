package ai.drfx.maximus.matrixai.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Shared compact wordmark for Brain, Chat, Work and Hub. */
@Composable
internal fun WorkspaceBrand(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)) {
        Text("MAXIMUS", color = MaximusReference.gold,
            fontSize = 20.sp,
            fontWeight = FontWeight.ExtraBold, letterSpacing = 3.5.sp,
            maxLines = 1)
    }
}
