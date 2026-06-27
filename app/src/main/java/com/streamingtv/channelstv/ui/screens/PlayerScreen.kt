package com.streamingtv.channelstv.ui.screens

import android.app.Activity
import android.content.pm.ActivityInfo
import android.util.Base64
import android.view.ViewGroup
import androidx.annotation.OptIn
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.drm.DefaultDrmSessionManager
import androidx.media3.exoplayer.drm.FrameworkMediaDrm
import androidx.media3.exoplayer.drm.LocalMediaDrmCallback
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.ui.PlayerView
import com.streamingtv.channelstv.data.Channel
import com.streamingtv.channelstv.data.isScrapeable
import com.streamingtv.channelstv.ui.theme.GreenAccent
import com.streamingtv.channelstv.ui.theme.TextPrimary

// ── DRM Helpers ──────────────────────────────────────────────────────────────

private fun hexToBytes(hex: String): ByteArray {
    val clean = hex.replace("-", "").replace(" ", "")
    val len = clean.length
    val data = ByteArray(len / 2)
    var i = 0
    while (i < len) {
        data[i / 2] = ((Character.digit(clean[i], 16) shl 4) +
                Character.digit(clean[i + 1], 16)).toByte()
        i += 2
    }
    return data
}

private fun buildClearKeyJson(channel: Channel): String? {
    val keysList = mutableListOf<Pair<String, String>>()
    
    // 1. Agregar las llaves individuales si existen
    if (!channel.drmKeyId.isNullOrBlank() && !channel.drmKey.isNullOrBlank()) {
        keysList.add(channel.drmKeyId to channel.drmKey)
    }
    
    // 2. Parsear drmKeysJson que en Firebase viene como "keyId:key,keyId:key"
    if (!channel.drmKeysJson.isNullOrBlank()) {
        if (channel.drmKeysJson.trim().startsWith("{")) {
            // Si por casualidad sí es un JSON válido, retornarlo directo
            return channel.drmKeysJson
        }
        
        val pairs = channel.drmKeysJson.split(",")
        for (pair in pairs) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                keysList.add(parts[0].trim() to parts[1].trim())
            }
        }
    }
    
    if (keysList.isEmpty()) return null
    
    // 3. Construir el JWK Set válido para ExoPlayer
    val jsonKeys = keysList.mapNotNull { (keyId, key) ->
        try {
            val keyIdBytes = when {
                keyId.length == 32 && keyId.all { it.isLetterOrDigit() } -> hexToBytes(keyId)
                keyId.length == 36 && keyId.contains('-') -> hexToBytes(keyId)
                else -> Base64.decode(keyId, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val keyBytes = when {
                key.length == 32 && key.all { it.isLetterOrDigit() } -> hexToBytes(key)
                else -> Base64.decode(key, Base64.URL_SAFE or Base64.NO_WRAP)
            }
            val kidB64 = Base64.encodeToString(keyIdBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            val keyB64 = Base64.encodeToString(keyBytes, Base64.URL_SAFE or Base64.NO_PADDING or Base64.NO_WRAP)
            """{"kty":"oct","k":"$keyB64","kid":"$kidB64"}"""
        } catch (e: Exception) {
            null
        }
    }
    
    if (jsonKeys.isEmpty()) return null
    
    val jwkSet = """{"keys":[${jsonKeys.joinToString(",")}], "type":"temporary"}"""
    android.util.Log.d("CHANNELS_TV", "DRM JSON Built: $jwkSet")
    return jwkSet
}

// ── Player Screen ─────────────────────────────────────────────────────────────

@OptIn(UnstableApi::class)
@Composable
fun PlayerScreen(
    channel: Channel,
    onBack: () -> Unit
) {
    DisposableEffect(Unit) {
        val activity = LocalContext.current as? Activity
        val orig = activity?.requestedOrientation
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = orig ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    if (channel.isScrapeable) {
        // ── MODO SCRAPE: extraer URL dinámicamente y luego reproducir ─────────
        ScrapeThenPlayScreen(channel = channel, onBack = onBack)
    } else {
        // ── MODO DIRECTO: reproducir URL estática ────────────────────────────
        DirectPlayerScreen(channel = channel, onBack = onBack)
    }
}

// ── FASE 1: Extracción de stream via WebView ─────────────────────────────────

@Composable
private fun ScrapeThenPlayScreen(channel: Channel, onBack: () -> Unit) {
    var extractedUrl by remember { mutableStateOf<String?>(null) }
    var extractionError by remember { mutableStateOf<String?>(null) }
    var retryKey by remember { mutableIntStateOf(0) }

    val context = LocalContext.current

    when {
        extractedUrl != null -> {
            // URL extraída exitosamente, reproducir con ExoPlayer
            val playableChannel = channel.copy(url = extractedUrl!!)
            DirectPlayerScreen(channel = playableChannel, onBack = onBack)
        }
        extractionError != null -> {
            // Mostrar error de extracción
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
                    .padding(24.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("⚠️", style = MaterialTheme.typography.displaySmall)
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "No se pudo cargar\n${channel.name}",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = extractionError ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.Gray,
                        textAlign = TextAlign.Center
                    )
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = {
                            extractionError = null
                            extractedUrl = null
                            retryKey++
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                    ) {
                        Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Reintentar")
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onBack) {
                        Text("← Volver", color = Color.LightGray)
                    }
                }
            }
        }
        else -> {
            // Mostrar WebView extractor oculto con pantalla de loading
            key(retryKey) {
                StreamExtractorWebView(
                    scrapeUrl = channel.scrapeUrl!!,
                    channelName = channel.name,
                    onStreamFound = { url ->
                        android.util.Log.d("CHANNELS_TV", "Stream extraído: $url")
                        extractedUrl = url
                    },
                    onError = { msg ->
                        extractionError = msg
                    }
                )
            }
        }
    }
}

// ── FASE 2 / Modo Directo: Reproducción nativa con ExoPlayer ─────────────────

@OptIn(UnstableApi::class)
@Composable
private fun DirectPlayerScreen(
    channel: Channel,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    var isError    by remember { mutableStateOf(false) }
    var errorMsg   by remember { mutableStateOf("") }
    var isBuffering by remember { mutableStateOf(true) }
    var retryKey   by remember { mutableIntStateOf(0) }

    // Determine DRM JSON payload
    val drmJsonBytes = remember(channel) {
        buildClearKeyJson(channel)?.toByteArray(Charsets.UTF_8)
    }

    val exoPlayer = remember(retryKey, channel) {
        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent(
                "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
            )
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(20_000)
            .setReadTimeoutMs(20_000)
            .setDefaultRequestProperties(
                mapOf(
                    "Accept"          to "*/*",
                    "Accept-Language" to "es-ES,es;q=0.9,en;q=0.8",
                    "Origin"          to "https://www.canales-tv-en-vivo.tv",
                    "Referer"         to "https://www.canales-tv-en-vivo.tv/"
                )
            )

        val mediaSourceFactory = DefaultMediaSourceFactory(httpFactory)

        // Inject LocalMediaDrmCallback to handle ClearKey JSON directly without network requests
        if (drmJsonBytes != null) {
            val drmCallback = LocalMediaDrmCallback(drmJsonBytes)
            val drmSessionManager = DefaultDrmSessionManager.Builder()
                .setUuidAndExoMediaDrmProvider(C.CLEARKEY_UUID, FrameworkMediaDrm.DEFAULT_PROVIDER)
                .build(drmCallback)
            
            mediaSourceFactory.setDrmSessionManagerProvider { drmSessionManager }
        }

        ExoPlayer.Builder(context)
            .setMediaSourceFactory(mediaSourceFactory)
            .build()
    }

    DisposableEffect(exoPlayer) {
        isError     = false
        isBuffering = true
        errorMsg    = ""

        val listener = object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> isBuffering = true
                    Player.STATE_READY     -> { isBuffering = false; isError = false }
                    else                   -> isBuffering = false
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                android.util.Log.e("CHANNELS_TV", "Error ${error.errorCodeName}: ${error.message}", error)
                isError = true
                isBuffering = false
                errorMsg = when (error.errorCode) {
                    PlaybackException.ERROR_CODE_DRM_CONTENT_ERROR,
                    PlaybackException.ERROR_CODE_DRM_LICENSE_ACQUISITION_FAILED,
                    PlaybackException.ERROR_CODE_DRM_PROVISIONING_FAILED,
                    PlaybackException.ERROR_CODE_DRM_SYSTEM_ERROR ->
                        "Error DRM: llave de desencriptación incorrecta o expirada"
                    PlaybackException.ERROR_CODE_IO_BAD_HTTP_STATUS ->
                        "El servidor rechazó la solicitud (HTTP 403/404). Posible bloqueo geográfico."
                    PlaybackException.ERROR_CODE_IO_NETWORK_CONNECTION_FAILED ->
                        "Error de red o servidor caído."
                    PlaybackException.ERROR_CODE_TIMEOUT ->
                        "Tiempo de espera agotado. Canal posiblemente fuera del aire."
                    else -> "Error al reproducir (${error.errorCodeName})"
                }
            }
        }
        exoPlayer.addListener(listener)

        val mimeType = when {
            channel.url.contains(".m3u8", ignoreCase = true) -> MimeTypes.APPLICATION_M3U8
            channel.url.contains(".mpd",  ignoreCase = true) -> MimeTypes.APPLICATION_MPD
            else -> null
        }

        val mediaItemBuilder = MediaItem.Builder().setUri(channel.url)
        if (mimeType != null) mediaItemBuilder.setMimeType(mimeType)
        
        // Add DRM config to MediaItem just to signal it needs ClearKey (handled by our custom SessionManager)
        if (drmJsonBytes != null) {
            mediaItemBuilder.setDrmConfiguration(
                MediaItem.DrmConfiguration.Builder(C.CLEARKEY_UUID).build()
            )
            android.util.Log.d("CHANNELS_TV", "Playing: ${channel.name} | DRM=LOCAL_JSON | URL=${channel.url}")
        } else {
            android.util.Log.d("CHANNELS_TV", "Playing: ${channel.name} | DRM=NONE | URL=${channel.url}")
        }

        exoPlayer.setMediaItem(mediaItemBuilder.build())
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true

        onDispose {
            exoPlayer.removeListener(listener)
            exoPlayer.stop()
            exoPlayer.release()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
    ) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exoPlayer
                    useController = true
                    keepScreenOn = true
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT
                    )
                }
            },
            update = { it.player = exoPlayer },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.Black.copy(alpha = 0.55f))
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .align(Alignment.TopStart)
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Volver", tint = Color.White)
            }
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isBuffering && !isError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.align(Alignment.Center)
            ) {
                CircularProgressIndicator(color = GreenAccent)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "Cargando ${channel.name}…",
                    color = Color.White.copy(alpha = 0.8f),
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }

        if (isError) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.85f))
                    .padding(24.dp)
            ) {
                Text("⚠️", style = MaterialTheme.typography.displaySmall)
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = "No se puede reproducir\n${channel.name}",
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMsg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.Gray,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = { isError = false; isBuffering = true; retryKey++ },
                    colors = ButtonDefaults.buttonColors(containerColor = GreenAccent)
                ) {
                    Icon(Icons.Default.Refresh, null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Reintentar")
                }
                Spacer(modifier = Modifier.height(8.dp))
                TextButton(onClick = onBack) {
                    Text("← Volver", color = Color.LightGray)
                }
            }
        }
    }
}
