package fr.conichonhaa.nakivo.ui.web

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.net.Uri
import android.net.http.SslCertificate
import android.net.http.SslError
import android.os.Build
import android.webkit.CookieManager
import android.webkit.SslErrorHandler
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.FrameLayout
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import fr.conichonhaa.nakivo.data.CertUtils
import fr.conichonhaa.nakivo.ui.AppViewModel
import fr.conichonhaa.nakivo.ui.UiState
import java.io.ByteArrayInputStream
import java.security.cert.CertificateFactory
import java.security.cert.X509Certificate
import java.text.DateFormat
import kotlin.math.roundToInt

private class PendingSsl(
    val handler: SslErrorHandler,
    val fingerprint: String,
    val subject: String,
    val validUntil: String,
)

/** Largeurs de mise en page proposées (px CSS). 0 = largeur réelle du téléphone. */
private val WIDTH_PRESETS = listOf(
    0 to "Natif (largeur du téléphone)",
    700 to "Compact (700 px)",
    900 to "Standard (900 px)",
    1100 to "Large (1100 px)",
    1300 to "Très large (1300 px)",
)

/**
 * Adaptation mobile : l'interface NAKIVO est pensée pour un écran de PC.
 * La WebView est réellement dimensionnée à `virtualWidthDp` de large (et à la
 * hauteur correspondante), puis réduite par Android pour remplir l'écran.
 * La page voit donc une vraie fenêtre de bureau qui occupe toute la hauteur,
 * et les touchers sont convertis automatiquement par le système.
 */
private class ScaledWebContainer(context: Context, val webView: WebView) : FrameLayout(context) {

    var virtualWidthDp: Int = 900
        set(value) {
            if (field != value) {
                field = value
                requestLayout()
            }
        }

    init {
        clipChildren = true
        webView.pivotX = 0f
        webView.pivotY = 0f
        addView(webView, LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT))
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        val w = MeasureSpec.getSize(widthMeasureSpec)
        val h = MeasureSpec.getSize(heightMeasureSpec)
        setMeasuredDimension(w, h)

        val virtualPx = (virtualWidthDp * resources.displayMetrics.density).roundToInt()
        val scale = if (virtualWidthDp <= 0 || w <= 0 || virtualPx <= w) 1f else w.toFloat() / virtualPx
        val childW = if (scale == 1f) w else virtualPx
        val childH = if (scale == 1f) h else (h / scale).roundToInt()

        webView.measure(
            MeasureSpec.makeMeasureSpec(childW, MeasureSpec.EXACTLY),
            MeasureSpec.makeMeasureSpec(childH, MeasureSpec.EXACTLY),
        )
        webView.scaleX = scale
        webView.scaleY = scale
    }

    override fun onLayout(changed: Boolean, left: Int, top: Int, right: Int, bottom: Int) {
        webView.layout(0, 0, webView.measuredWidth, webView.measuredHeight)
    }
}

private fun x509Of(ssl: SslCertificate?): X509Certificate? {
    if (ssl == null) return null
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) return ssl.x509Certificate
    val bytes = SslCertificate.saveState(ssl).getByteArray("x509-certificate") ?: return null
    return runCatching {
        CertificateFactory.getInstance("X.509")
            .generateCertificate(ByteArrayInputStream(bytes)) as? X509Certificate
    }.getOrNull()
}

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun WebDashboardScreen(state: UiState, vm: AppViewModel) {
    val context = LocalContext.current
    val activity = context as? Activity

    val pinState = rememberUpdatedState(state.webPin)
    val serverHost = remember(state.webUrl) { Uri.parse(state.webUrl).host }

    var progress by remember { mutableIntStateOf(0) }
    var pageError by remember { mutableStateOf<String?>(null) }
    var pendingSsl by remember { mutableStateOf<PendingSsl?>(null) }
    var menuOpen by remember { mutableStateOf(false) }
    var displayMenuOpen by remember { mutableStateOf(false) }

    val webView = remember {
        WebView(context).apply {
            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                // 1 px CSS = 1 dp : la largeur vue par la page est celle de la WebView.
                useWideViewPort = false
                loadWithOverviewMode = false
                setSupportZoom(true)
                builtInZoomControls = true
                displayZoomControls = false
                mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
            }
            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            webChromeClient = object : WebChromeClient() {
                override fun onProgressChanged(view: WebView, newProgress: Int) {
                    progress = newProgress
                }
            }

            webViewClient = object : WebViewClient() {
                override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                    pageError = null
                }

                override fun onPageFinished(view: WebView, url: String?) {
                    CookieManager.getInstance().flush()
                }

                override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                    val host = request.url.host
                    // Liens externes (aide NAKIVO…) : navigateur du téléphone.
                    if (host != null && host != serverHost &&
                        (request.url.scheme == "http" || request.url.scheme == "https")
                    ) {
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, request.url)) }
                        return true
                    }
                    return false
                }

                override fun onReceivedSslError(view: WebView, handler: SslErrorHandler, error: SslError) {
                    val cert = x509Of(error.certificate)
                    if (cert == null) {
                        handler.cancel()
                        pageError = "Certificat du serveur illisible."
                        return
                    }
                    val fp = CertUtils.sha256(cert)
                    if (fp == pinState.value) {
                        handler.proceed()
                    } else {
                        pendingSsl = PendingSsl(
                            handler = handler,
                            fingerprint = fp,
                            subject = cert.subjectX500Principal.name,
                            validUntil = DateFormat.getDateInstance(DateFormat.MEDIUM).format(cert.notAfter),
                        )
                    }
                }

                override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                    if (request.isForMainFrame) {
                        pageError = "Impossible d'ouvrir le Director : ${error.description}"
                    }
                }
            }
            loadUrl(state.webUrl)
        }
    }

    val container = remember { ScaledWebContainer(context, webView) }

    // Verrouillage paysage optionnel.
    DisposableEffect(state.webLandscape) {
        activity?.requestedOrientation =
            if (state.webLandscape) ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }

    DisposableEffect(Unit) {
        onDispose {
            CookieManager.getInstance().flush()
            webView.destroy()
        }
    }

    BackHandler {
        if (webView.canGoBack()) webView.goBack() else activity?.moveTaskToBack(true)
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Barre compacte : on laisse un maximum de place à l'interface.
        Surface(tonalElevation = 2.dp) {
            Column {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(44.dp)
                        .padding(start = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "NAKIVO",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        serverHost ?: "",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                    )
                    IconButton(onClick = { webView.reload() }) {
                        Icon(Icons.Filled.Refresh, contentDescription = "Recharger")
                    }
                    Box {
                        IconButton(onClick = { displayMenuOpen = true }) {
                            Icon(Icons.Filled.Settings, contentDescription = "Affichage")
                        }
                        DropdownMenu(expanded = displayMenuOpen, onDismissRequest = { displayMenuOpen = false }) {
                            Text(
                                "Largeur de mise en page",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                            WIDTH_PRESETS.forEach { (w, label) ->
                                DropdownMenuItem(
                                    text = { Text(label) },
                                    leadingIcon = {
                                        if (state.webWidth == w) Icon(Icons.Filled.Check, contentDescription = null)
                                    },
                                    onClick = {
                                        displayMenuOpen = false
                                        vm.setWebWidth(w)
                                    },
                                )
                            }
                            HorizontalDivider()
                            DropdownMenuItem(
                                text = { Text("Bloquer en paysage") },
                                leadingIcon = {
                                    if (state.webLandscape) Icon(Icons.Filled.Check, contentDescription = null)
                                },
                                onClick = {
                                    displayMenuOpen = false
                                    vm.setWebLandscape(!state.webLandscape)
                                },
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { menuOpen = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Menu")
                        }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text("Page d'accueil du Director") },
                                onClick = { menuOpen = false; webView.loadUrl(state.webUrl) },
                            )
                            DropdownMenuItem(
                                text = { Text("Effacer la session") },
                                onClick = {
                                    menuOpen = false
                                    CookieManager.getInstance().removeAllCookies {
                                        webView.clearCache(true)
                                        webView.loadUrl(state.webUrl)
                                    }
                                },
                            )
                            DropdownMenuItem(
                                text = { Text("Changer de serveur / de mode") },
                                onClick = { menuOpen = false; vm.closeWeb() },
                            )
                        }
                    }
                }
                if (progress in 1..99) {
                    LinearProgressIndicator(
                        progress = { progress / 100f },
                        modifier = Modifier.fillMaxWidth().height(2.dp),
                    )
                }
            }
        }

        Box(Modifier.weight(1f).fillMaxWidth()) {
            AndroidView(
                factory = { container },
                update = { it.virtualWidthDp = state.webWidth },
                modifier = Modifier.fillMaxSize(),
            )

            pageError?.let { msg ->
                Card(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(24.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(msg, color = MaterialTheme.colorScheme.onErrorContainer)
                        Text(
                            "Vérifie que le téléphone est sur le réseau local ou connecté au VPN.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { pageError = null; webView.loadUrl(state.webUrl) }) { Text("Réessayer") }
                            TextButton(onClick = { vm.closeWeb() }) { Text("Changer d'adresse") }
                        }
                    }
                }
            }
        }
    }

    pendingSsl?.let { p ->
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Certificat non reconnu") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Le Director présente un certificat auto-signé. Compare l'empreinte avec celle " +
                            "affichée par ton navigateur sur PC avant de l'accepter. Elle sera mémorisée.",
                    )
                    Text("Sujet : ${p.subject}", style = MaterialTheme.typography.bodySmall)
                    Text("Valide jusqu'au : ${p.validUntil}", style = MaterialTheme.typography.bodySmall)
                    Text("SHA-256 :", style = MaterialTheme.typography.labelMedium)
                    Text(p.fingerprint, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    vm.trustWebCert(p.fingerprint)
                    p.handler.proceed()
                    pendingSsl = null
                }) { Text("Faire confiance") }
            },
            dismissButton = {
                TextButton(onClick = {
                    p.handler.cancel()
                    pendingSsl = null
                    pageError = "Certificat refusé."
                }) { Text("Refuser") }
            },
        )
    }
}
