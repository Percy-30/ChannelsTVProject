package com.streamingtv.channelstv.ui.screens

import android.app.Activity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.streamingtv.channelstv.AdsManager
import com.streamingtv.channelstv.data.Channel
import com.streamingtv.channelstv.data.Event
import com.streamingtv.channelstv.ui.Category
import com.streamingtv.channelstv.ui.MainViewModel
import com.streamingtv.channelstv.ui.components.ChannelCard
import com.streamingtv.channelstv.ui.components.EventCard
import com.streamingtv.channelstv.ui.components.PromoCard
import com.streamingtv.channelstv.ui.theme.*

/**
 * Main home screen with:
 * - Top bar: "BIENVENID@ A CHANNELS TV" + send button
 * - Horizontal scrollable category tabs
 * - Content area: Agenda list or channel grid depending on selected tab
 */
@Composable
fun HomeScreen(
    onChannelClick: (Channel) -> Unit,
    viewModel: MainViewModel = viewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    // Pre-load ads when screen opens
    LaunchedEffect(Unit) {
        AdsManager.loadInterstitial(context)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundDark)
    ) {
        // ── Top Bar ───────────────────────────────────────────────────────────
        TopBar(
            onSendClick = {
                AdsManager.showAdsterra(context)
            }
        )

        // ── Category Tabs ─────────────────────────────────────────────────────
        CategoryTabRow(
            selectedCategory = uiState.selectedCategory,
            onCategorySelected = { viewModel.selectCategory(it) }
        )

        // ── Content ───────────────────────────────────────────────────────────
        if (uiState.isLoading) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = GreenAccent)
            }
        } else {
            when (uiState.selectedCategory) {
                Category.AGENDA -> AgendaContent(
                    events = uiState.events,
                    onEventClick = { event ->
                        // Find channel associated with the event and navigate directly
                        val channel = uiState.channels.find { it.id == event.channelId }
                        if (channel != null) {
                            onChannelClick(channel)
                        }
                    }
                )
                else -> ChannelGridContent(
                    channels = viewModel.getChannelsForCategory(uiState.selectedCategory),
                    categoryName = uiState.selectedCategory.displayName,
                    onChannelClick = { channel ->
                        // Navigate directly to player without blocking on ads
                        onChannelClick(channel)
                    },
                    onPromoClick = {
                        val activity = context as? Activity
                        if (activity != null) {
                            AdsManager.showAdsterra(activity)
                        }
                    }
                )
            }
        }
    }
}

// ── Top Bar ───────────────────────────────────────────────────────────────────

@Composable
private fun TopBar(onSendClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp)
    ) {
        Text(
            text = "BIENVENID@ A\nCHANNELS TV",
            style = MaterialTheme.typography.displayLarge,
            color = TextPrimary,
            modifier = Modifier.weight(1f)
        )
        IconButton(
            onClick = onSendClick,
            modifier = Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(BlueButton)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.Send,
                contentDescription = "Send",
                tint = Color.White,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

// ── Category Tabs ─────────────────────────────────────────────────────────────

@Composable
private fun CategoryTabRow(
    selectedCategory: Category,
    onCategorySelected: (Category) -> Unit
) {
    val categories = Category.entries.filter { it != Category.VARIADOS }

    ScrollableTabRow(
        selectedTabIndex = categories.indexOf(selectedCategory).coerceAtLeast(0),
        containerColor = BackgroundDark,
        contentColor = GreenAccent,
        edgePadding = 16.dp,
        divider = {},
        indicator = {},
        modifier = Modifier.fillMaxWidth()
    ) {
        categories.forEach { category ->
            val isSelected = category == selectedCategory
            Tab(
                selected = isSelected,
                onClick = { onCategorySelected(category) },
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 8.dp)
            ) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .background(if (isSelected) GreenAccent else SurfaceDark)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = category.displayName,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            fontSize = 14.sp
                        ),
                        color = if (isSelected) Color.White else TextSecondary
                    )
                }
            }
        }
    }
}

// ── Agenda Content ────────────────────────────────────────────────────────────

@Composable
private fun AgendaContent(
    events: List<Event>,
    onEventClick: (Event) -> Unit
) {
    LazyColumn(
        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Section header
        item {
            SectionHeader(title = "Eventos de Hoy")
        }

        if (events.isEmpty()) {
            item {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp)
                ) {
                    Text(
                        text = "No hay eventos programados hoy",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(events, key = { it.id }) { event ->
                EventCard(event = event, onClick = onEventClick)
            }
        }
    }
}

// ── Channel Grid Content ──────────────────────────────────────────────────────

@Composable
private fun ChannelGridContent(
    channels: List<Channel>,
    categoryName: String,
    onChannelClick: (Channel) -> Unit,
    onPromoClick: () -> Unit
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        // Section header (full width)
        item(span = { GridItemSpan(2) }) {
            SectionHeader(
                title = categoryName,
                modifier = Modifier.padding(start = 4.dp, bottom = 4.dp)
            )
        }

        if (channels.isEmpty()) {
            item(span = { GridItemSpan(2) }) {
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(40.dp)
                ) {
                    Text(
                        text = "No hay canales disponibles",
                        color = TextSecondary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        } else {
            items(channels, key = { it.id }) { channel ->
                ChannelCard(channel = channel, onClick = onChannelClick)
            }
        }

        // Promo card (full width at bottom)
        item(span = { GridItemSpan(2) }) {
            PromoCard(onClick = onPromoClick)
        }
    }
}

// ── Shared Section Header ─────────────────────────────────────────────────────

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(vertical = 8.dp)
    ) {
        // Green left bar
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(22.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(GreenAccent)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = TextPrimary
        )
    }
}
