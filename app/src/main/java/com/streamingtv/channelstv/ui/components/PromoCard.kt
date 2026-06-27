package com.streamingtv.channelstv.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.streamingtv.channelstv.ui.theme.PromoGreen
import com.streamingtv.channelstv.ui.theme.TextPrimary
import com.streamingtv.channelstv.ui.theme.TextSecondary

/**
 * "Premios Semanales" promo card shown at the bottom of channel categories.
 * Full-width green card with star icon, title, and subtitle.
 */
@Composable
fun PromoCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .height(130.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(PromoGreen)
            .clickable { onClick() }
            .padding(16.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Star,
            contentDescription = "Promo",
            tint = Color.White,
            modifier = Modifier.size(32.dp)
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = "Premios Semanales",
            style = MaterialTheme.typography.bodyLarge.copy(
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            ),
            color = TextPrimary
        )
        Text(
            text = "Clic para participar",
            style = MaterialTheme.typography.bodyMedium,
            color = TextSecondary
        )
    }
}
