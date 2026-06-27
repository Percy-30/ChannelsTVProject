package com.streamingtv.channelstv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.ads.MobileAds
import com.streamingtv.channelstv.data.Channel
import com.streamingtv.channelstv.ui.screens.HomeScreen
import com.streamingtv.channelstv.ui.screens.PlayerScreen
import com.streamingtv.channelstv.ui.theme.ChannelsTVTheme

/**
 * Single-activity app entry point.
 * Navigation: Home ↔ Player
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Initialize AdMob SDK
        MobileAds.initialize(this)

        setContent {
            ChannelsTVTheme {
                ChannelsTVApp()
            }
        }
    }
}

@Composable
private fun ChannelsTVApp() {
    val navController = rememberNavController()

    // Hold the selected channel in a state so PlayerScreen can receive it
    var selectedChannel by remember { mutableStateOf<Channel?>(null) }

    NavHost(
        navController = navController,
        startDestination = "home"
    ) {
        composable("home") {
            HomeScreen(
                onChannelClick = { channel ->
                    selectedChannel = channel
                    navController.navigate("player")
                }
            )
        }

        composable("player") {
            val channel = selectedChannel
            if (channel != null) {
                PlayerScreen(
                    channel = channel,
                    onBack = { navController.popBackStack() }
                )
            } else {
                navController.popBackStack()
            }
        }
    }
}
