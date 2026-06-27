package com.streamingtv.channelstv.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.streamingtv.channelstv.ui.theme.GreenAccent
import kotlinx.coroutines.delay

// ── Patrón de detección de streams ───────────────────────────────────────────
private val STREAM_URL_REGEX = Regex(
    """https?://[^\s"'<>]+\.(m3u8|mpd)(\?[^\s"'<>]*)?""",
    RegexOption.IGNORE_CASE
)

// ── Dominios de anuncios que se bloquean para mayor velocidad ────────────────
private val AD_DOMAINS = setOf(
    "doubleclick.net", "googlesyndication.com", "googletagmanager.com",
    "googletagservices.com", "adservice.google.com", "amazon-adsystem.com",
    "moatads.com", "scorecardresearch.com", "outbrain.com", "taboola.com",
    "adsymptotic.com", "adnxs.com", "rubiconproject.com", "pubmatic.com",
    "openx.net", "criteo.com", "quantserve.com", "ads.yahoo.com",
    "adtechus.com", "advertising.com", "trafficjunky.net", "exoclick.com",
    "popads.net", "popcash.net", "ero-advertising.com"
)

// ── Script JavaScript inyectado para auto-click en botones de play ────────────
private const val AUTO_PLAY_JS = """
(function() {
    // Intentar auto-play en videos HTML5
    var videos = document.querySelectorAll('video');
    videos.forEach(function(v) {
        v.muted = false;
        v.play().catch(function(){});
    });

    // Hacer click en iframes fullscreen o botones de play comunes
    var playBtns = document.querySelectorAll(
        '.play-button, .play-btn, .btn-play, [class*="play"], ' +
        '[id*="play"], .jw-icon-display, .fp-play, .vjs-big-play-button, ' +
        '[aria-label="Play"], [aria-label="Reproducir"]'
    );
    playBtns.forEach(function(btn) { btn.click(); });
})();
"""

/**
 * Estado del proceso de extracción.
 */
sealed class ExtractionState {
    object Loading : ExtractionState()
    data class Success(val streamUrl: String) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

/**
 * Composable que lanza un WebView oculto para extraer dinámicamente
 * la URL de stream (.m3u8 / .mpd) desde una página web de origen.
 *
 * @param scrapeUrl  URL de la página web que contiene el reproductor
 * @param channelName Nombre del canal para mostrar en el loading
 * @param onStreamFound Callback con la URL limpia del stream encontrado
 * @param onError   Callback si no se pudo extraer en el tiempo límite
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun StreamExtractorWebView(
    scrapeUrl: String,
    channelName: String,
    onStreamFound: (String) -> Unit,
    onError: (String) -> Unit
) {
    val context = LocalContext.current
    var state by remember { mutableStateOf<ExtractionState>(ExtractionState.Loading) }

    // Timeout: si en 25 segundos no encontramos nada, reportamos error
    LaunchedEffect(scrapeUrl) {
        delay(25_000)
        if (state is ExtractionState.Loading) {
            state = ExtractionState.Error("Tiempo de espera agotado. No se pudo extraer el stream.")
        }
    }

    // Reaccionar a cambios de estado
    LaunchedEffect(state) {
        when (val s = state) {
            is ExtractionState.Success -> onStreamFound(s.streamUrl)
            is ExtractionState.Error   -> onError(s.message)
            else -> { /* still loading */ }
        }
    }

    // WebView oculto (tamaño 1x1 para que sea invisible)
    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                mediaPlaybackRequiresUserGesture = false
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                userAgentString =
                    "Mozilla/5.0 (Linux; Android 14; Pixel 8) " +
                    "AppleWebKit/537.36 (KHTML, like Gecko) " +
                    "Chrome/124.0.0.0 Mobile Safari/537.36"
                cacheMode = WebSettings.LOAD_NO_CACHE
                allowContentAccess = true
                allowFileAccess = true
                loadWithOverviewMode = true
                useWideViewPort = true
            }

            // Interfaz JS → Kotlin para que el JavaScript nos informe URLs
            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onStreamUrl(url: String) {
                    android.util.Log.d("EXTRACTOR", "JS reportó stream: $url")
                    if (state is ExtractionState.Loading && isValidStreamUrl(url)) {
                        state = ExtractionState.Success(url)
                    }
                }
            }, "AndroidBridge")

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    android.util.Log.d("EXTRACTOR", "Página iniciando: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    android.util.Log.d("EXTRACTOR", "Página cargada: $url")
                    // Inyectar JS de auto-play
                    view?.evaluateJavascript(AUTO_PLAY_JS, null)
                    // Inyectar JS que escanea el DOM en busca de stream URLs
                    view?.evaluateJavascript(buildDomScannerJs(), null)
                }

                override fun shouldInterceptRequest(
                    view: WebView?,
                    request: WebResourceRequest?
                ): WebResourceResponse? {
                    val url = request?.url?.toString() ?: return null

                    // Bloquear anuncios
                    if (isAdUrl(url)) {
                        return WebResourceResponse("text/plain", "utf-8", null)
                    }

                    // Detectar stream URL en el tráfico de red
                    if (state is ExtractionState.Loading && isValidStreamUrl(url)) {
                        android.util.Log.d("EXTRACTOR", "Stream interceptado: $url")
                        state = ExtractionState.Success(url)
                    }

                    return null
                }
            }
        }
    }

    DisposableEffect(scrapeUrl) {
        webView.loadUrl(scrapeUrl)
        onDispose {
            webView.stopLoading()
            webView.destroy()
        }
    }

    // UI: Mostrar loading mientras se extrae
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(
                color = GreenAccent,
                modifier = Modifier.size(52.dp),
                strokeWidth = 3.dp
            )
            Spacer(modifier = Modifier.height(20.dp))
            Text(
                text = "🔍 Extrayendo stream…",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = channelName,
                style = MaterialTheme.typography.bodySmall,
                color = Color.Gray
            )
        }

        // WebView oculto (1x1 px, invisible al usuario)
        AndroidView(
            factory = { webView },
            modifier = Modifier.size(1.dp)
        )
    }
}

// ── Helpers ──────────────────────────────────────────────────────────────────

private fun isValidStreamUrl(url: String): Boolean {
    return STREAM_URL_REGEX.containsMatchIn(url) &&
           !url.contains("ad", ignoreCase = true).let { isAd ->
               isAd && AD_DOMAINS.any { url.contains(it, ignoreCase = true) }
           }
}

private fun isAdUrl(url: String): Boolean {
    return AD_DOMAINS.any { url.contains(it, ignoreCase = true) }
}

/**
 * Genera un script JS que escanea el DOM en busca de URLs de stream
 * en atributos src, data-src, etc. y las reporta al bridge nativo.
 */
private fun buildDomScannerJs(): String = """
(function() {
    var regex = /https?:\/\/[^\s"'<>]+\.(m3u8|mpd)(\?[^\s"'<>]*)?/gi;
    
    // Escanear todos los atributos del DOM
    var allElements = document.querySelectorAll('*');
    allElements.forEach(function(el) {
        var attrs = ['src', 'data-src', 'data-url', 'data-stream', 'data-hls', 
                     'data-video', 'href', 'data-file', 'file', 'source'];
        attrs.forEach(function(attr) {
            var val = el.getAttribute(attr);
            if (val && regex.test(val)) {
                AndroidBridge.onStreamUrl(val);
            }
        });
    });
    
    // Escanear el HTML completo en busca de URLs
    var bodyHtml = document.documentElement.innerHTML;
    var matches = bodyHtml.match(regex);
    if (matches && matches.length > 0) {
        AndroidBridge.onStreamUrl(matches[0]);
    }
    
    // Escuchar solicitudes XHR futuras
    var origOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        if (url && regex.test(url)) {
            AndroidBridge.onStreamUrl(url);
        }
        return origOpen.apply(this, arguments);
    };
})();
"""
