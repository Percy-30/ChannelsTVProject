package com.streamingtv.channelstv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.streamingtv.channelstv.data.Event
import com.streamingtv.channelstv.ui.theme.GreenAccent
import com.streamingtv.channelstv.ui.theme.SurfaceDark
import com.streamingtv.channelstv.ui.theme.TextPrimary
import com.streamingtv.channelstv.ui.theme.TextSecondary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/**
 * Event row card shown in the Agenda tab.
 * Shows: time | logo | title + subtitle
 */
@Composable
fun EventCard(
    event: Event,
    onClick: (Event) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(SurfaceDark)
            .clickable { onClick(event) }
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        // ── Time column ───────────────────────────────────────────────────────
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.width(56.dp)
        ) {
            Text(
                text = formatTime(event.timestamp),
                style = MaterialTheme.typography.labelLarge,
                color = GreenAccent
            )
            Text(
                text = "HORA",
                style = MaterialTheme.typography.labelSmall,
                color = TextSecondary
            )
        }

        Spacer(modifier = Modifier.width(12.dp))

        // ── Logo ──────────────────────────────────────────────────────────────
        if (!event.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = event.logoUrl,
                contentDescription = event.title,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(56.dp)
            )
            Spacer(modifier = Modifier.width(12.dp))
        }

        // ── Title + Subtitle ──────────────────────────────────────────────────
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.title,
                style = MaterialTheme.typography.bodyLarge,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (event.subtitle.isNotBlank()) {
                Text(
                    text = event.subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun formatTime(timestamp: Long): String {
    return if (timestamp == 0L) {
        "--:--"
    } else {
        val sdf = SimpleDateFormat("HH:mm", Locale.getDefault())
        sdf.format(Date(timestamp))
    }
}
