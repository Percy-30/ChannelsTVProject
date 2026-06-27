package com.streamingtv.channelstv.ui.screens

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
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

// ── Script de auto-play ───────────────────────────────────────────────────────
private const val AUTO_PLAY_JS = """
(function() {
    // Intentar auto-play en videos HTML5
    var videos = document.querySelectorAll('video');
    videos.forEach(function(v) {
        v.muted = false;
        v.play().catch(function(){});
    });

    // Click en botones de play comunes
    var playBtns = document.querySelectorAll(
        '.play-button, .play-btn, .btn-play, [class*="play"], ' +
        '[id*="play"], .jw-icon-display, .fp-play, .vjs-big-play-button, ' +
        '[aria-label="Play"], [aria-label="Reproducir"]'
    );
    playBtns.forEach(function(btn) { btn.click(); });

    // Intentar reproducir video dentro de iframes accesibles
    var iframes = document.querySelectorAll('iframe');
    iframes.forEach(function(f) {
        var src = f.src || '';
        if (src && !src.includes('ads') && !src.includes('doubleclick')) {
            try {
                var vid = f.contentDocument && f.contentDocument.querySelector('video');
                if (vid) vid.play().catch(function(){});
            } catch(e) {}
        }
    });
})();
"""

// ── Estado del proceso de extracción ─────────────────────────────────────────
sealed class ExtractionState {
    object Loading : ExtractionState()
    data class Success(val streamUrl: String) : ExtractionState()
    data class Error(val message: String) : ExtractionState()
}

/**
 * Composable que lanza un WebView oculto para extraer dinámicamente
 * la URL de stream (.m3u8 / .mpd) desde una página web de origen.
 *
 * Estrategias de detección (en orden de prioridad):
 *  1. shouldInterceptRequest → captura TODA red del WebView, incluidos iframes
 *  2. XHR/fetch hook         → URLs dinámicas via JS
 *  3. HTMLMediaElement.src   → JWPlayer, dash.js, hls.js
 *  4. MutationObserver       → elementos añadidos tardíamente
 *  5. Escaneo DOM + rescans  → atributos src, data-src, etc.
 *
 * @param scrapeUrl   URL de la página que contiene el reproductor
 * @param channelName Nombre del canal para mostrar en pantalla de carga
 * @param onStreamFound Callback con la URL del stream encontrado
 * @param onError     Callback si no se extrajo en el tiempo límite
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

    // Timeout de 30 segundos
    LaunchedEffect(scrapeUrl) {
        delay(30_000)
        if (state is ExtractionState.Loading) {
            state = ExtractionState.Error("Tiempo de espera agotado. No se pudo extraer el stream.")
        }
    }

    LaunchedEffect(state) {
        when (val s = state) {
            is ExtractionState.Success -> onStreamFound(s.streamUrl)
            is ExtractionState.Error   -> onError(s.message)
            else -> { /* loading */ }
        }
    }

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

            // Bridge JS → Kotlin
            addJavascriptInterface(object : Any() {
                @JavascriptInterface
                fun onStreamUrl(url: String) {
                    android.util.Log.d("EXTRACTOR", "JS bridge reportó: $url")
                    if (state is ExtractionState.Loading && isValidStreamUrl(url)) {
                        state = ExtractionState.Success(url)
                    }
                }
            }, "AndroidBridge")

            // WebChromeClient es necesario para que iframes ejecuten JS correctamente
            webChromeClient = WebChromeClient()

            webViewClient = object : WebViewClient() {

                override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                    android.util.Log.d("EXTRACTOR", "Cargando: $url")
                }

                override fun onPageFinished(view: WebView?, url: String?) {
                    android.util.Log.d("EXTRACTOR", "Cargado: $url")
                    view?.evaluateJavascript(AUTO_PLAY_JS, null)
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

                    // ★ Captura a nivel de red: detecta streams de iframes, XHR, fetch, dash.js, etc.
                    if (state is ExtractionState.Loading && isValidStreamUrl(url)) {
                        android.util.Log.d("EXTRACTOR", "Stream en red: $url")
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

    // UI de carga
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

        // WebView invisible (1x1 dp)
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
 * Script JS inyectado en cada página cargada.
 * Detecta URLs .m3u8 / .mpd a través de múltiples estrategias y las
 * reporta al bridge nativo vía AndroidBridge.onStreamUrl().
 *
 * Nota: shouldInterceptRequest ya captura el tráfico de red incluyendo
 * iframes y workers, por lo que este script es una capa adicional para
 * URLs embebidas en el DOM o en variables JS.
 */
private fun buildDomScannerJs(): String = """
(function() {
    if (window.__channelsTvInjected) return;
    window.__channelsTvInjected = true;

    var regex = /https?:\/\/[^\s"'<>]+\.(m3u8|mpd)(\?[^\s"'<>]*)?/gi;

    function report(url) {
        if (!url || typeof url !== 'string') return;
        var re = /https?:\/\/[^\s"'<>]+\.(m3u8|mpd)(\?[^\s"'<>]*)?/i;
        if (re.test(url)) {
            try { AndroidBridge.onStreamUrl(url); } catch(e) {}
        }
    }

    // 1. Escanear DOM actual
    function scanDom() {
        var attrs = ['src','data-src','data-url','data-stream','data-hls',
                     'data-video','href','data-file','file','source','data-source',
                     'data-config','data-setup'];
        document.querySelectorAll('*').forEach(function(el) {
            attrs.forEach(function(a) {
                var v = el.getAttribute(a); if (v) report(v);
            });
            if (el.src) report(el.src);
            if (el.currentSrc) report(el.currentSrc);
        });
        // Buscar en el HTML completo por si la URL está inline en un script
        var html = document.documentElement.innerHTML;
        var m, re2 = /https?:\/\/[^\s"'<>\\\\]+\.(m3u8|mpd)(\?[^\s"'<>\\\\]*)?/gi;
        while ((m = re2.exec(html)) !== null) { report(m[0]); }
    }
    scanDom();

    // 2. Interceptar XMLHttpRequest
    var _xhrOpen = XMLHttpRequest.prototype.open;
    XMLHttpRequest.prototype.open = function(method, url) {
        report(url);
        return _xhrOpen.apply(this, arguments);
    };

    // 3. Interceptar fetch()
    if (window.fetch) {
        var _fetch = window.fetch;
        window.fetch = function(input, init) {
            try { report(typeof input === 'string' ? input : (input && input.url)); } catch(e) {}
            return _fetch.apply(this, arguments);
        };
    }

    // 4. Interceptar HTMLMediaElement.src (JWPlayer, dash.js, hls.js, Shaka)
    try {
        var desc = Object.getOwnPropertyDescriptor(HTMLMediaElement.prototype, 'src');
        if (desc && desc.set) {
            Object.defineProperty(HTMLMediaElement.prototype, 'src', {
                set: function(v) { report(v); desc.set.call(this, v); },
                get: desc.get,
                configurable: true
            });
        }
    } catch(e) {}

    // 5. MutationObserver para nodos añadidos dinámicamente
    new MutationObserver(function(mutations) {
        mutations.forEach(function(mut) {
            mut.addedNodes.forEach(function(node) {
                if (node.nodeType !== 1) return;
                ['src','data-src','data-url','data-stream'].forEach(function(a) {
                    var v = node.getAttribute && node.getAttribute(a); if (v) report(v);
                });
                if (node.src) report(node.src);
                node.querySelectorAll && node.querySelectorAll('[src],[data-src]').forEach(function(el) {
                    if (el.getAttribute('src')) report(el.getAttribute('src'));
                    if (el.getAttribute('data-src')) report(el.getAttribute('data-src'));
                    if (el.src) report(el.src);
                });
            });
        });
    }).observe(document.documentElement, {
        childList: true, subtree: true,
        attributes: true, attributeFilter: ['src','data-src','data-url','data-stream']
    });

    // 6. Re-escaneos retardados (para reproductores con carga tardía)
    setTimeout(scanDom, 2000);
    setTimeout(scanDom, 5000);
    setTimeout(scanDom, 10000);
})();
"""
