package com.example.sleppify

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.TextUtils
import android.util.Log
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class YouTubeMusicWebSessionActivity : AppCompatActivity() {

    private var webView: WebView? = null
    private var btnFinish: Button? = null
    private var tvStatus: TextView? = null
    private var lastCookieHeader: String = ""
    private var lastAutoLoginClickAtMs: Long = 0L
    private var autoConnectTriggered: Boolean = false

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_youtube_web_session)

        webView = findViewById(R.id.webSessionView)
        btnFinish = findViewById(R.id.btnSessionFinish)
        val btnClose: Button = findViewById(R.id.btnSessionClose)
        tvStatus = findViewById(R.id.tvSessionStatus)

        btnFinish?.isEnabled = false
        btnFinish?.setOnClickListener { completeWithSession() }
        btnClose.setOnClickListener {
            setResult(RESULT_CANCELED)
            finish()
        }

        val cookieManager = CookieManager.getInstance()
        cookieManager.setAcceptCookie(true)
        webView?.let { cookieManager.setAcceptThirdPartyCookies(it, true) }

        val settings = webView?.settings
        settings?.javaScriptEnabled = true
        settings?.domStorageEnabled = true
        settings?.databaseEnabled = true
        settings?.setSupportMultipleWindows(false)
        settings?.loadsImagesAutomatically = true
        settings?.useWideViewPort = true
        settings?.loadWithOverviewMode = true
        settings?.mediaPlaybackRequiresUserGesture = false
        settings?.cacheMode = WebSettings.LOAD_DEFAULT
        webView?.setLayerType(View.LAYER_TYPE_HARDWARE, null)

        webView?.webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val uri = Uri.parse(url)
                val host = uri.host.orEmpty()
                // Keep navigation inside YouTube/Google login domains for predictable session capture.
                return !(host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("gstatic.com"))
            }

            override fun onPageFinished(view: WebView, url: String?) {
                super.onPageFinished(view, url)
                Log.d(TAG_STREAMING_WEB, "page_finished url=" + (url ?: ""))
                attemptAutoLoginClick()
                refreshSessionState()
            }
        }

        webView?.loadUrl("https://accounts.google.com/ServiceLogin?continue=https%3A%2F%2Fmusic.youtube.com%2F")
    }

    private fun refreshSessionState() {
        val currentWebView = webView ?: return
        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com/")

        if (cookie.isNullOrBlank()) {
            tvStatus?.text = "Inicia sesion en YouTube Music para continuar."
            btnFinish?.isEnabled = false
            lastCookieHeader = ""
            return
        }

        if (looksLikeAuthenticatedCookie(cookie)) {
            tvStatus?.text = "Sesion detectada. Conectando..."
            btnFinish?.isEnabled = true
            lastCookieHeader = cookie
            Log.i(TAG_STREAMING_WEB, "authenticated_cookie_detected length=" + cookie.length)
            if (!autoConnectTriggered) {
                autoConnectTriggered = true
                completeWithSession()
            }
            return
        }

        tvStatus?.text = "Sesion aun no validada. Completa el inicio de sesion."
        btnFinish?.isEnabled = false
        lastCookieHeader = ""
    }

    private fun attemptAutoLoginClick() {
        val currentWebView = webView ?: return

        val cookie = CookieManager.getInstance().getCookie("https://music.youtube.com/")
        if (!cookie.isNullOrBlank() && looksLikeAuthenticatedCookie(cookie)) {
            return
        }

        val now = System.currentTimeMillis()
        if ((now - lastAutoLoginClickAtMs) < 1200L) {
            return
        }
        lastAutoLoginClickAtMs = now

        val script = buildString {
            append("(function(){")
            append("try {")
            append("var nodes = Array.prototype.slice.call(document.querySelectorAll('a,button,tp-yt-paper-button,yt-formatted-string'));")
            append("for (var i = 0; i < nodes.length; i++) {")
            append("var el = nodes[i];")
            append("var txt = ((el.innerText || el.textContent || '') + '').toLowerCase();")
            append("var href = (el.getAttribute && el.getAttribute('href')) || '';")
            append("var maybeLogin = txt.indexOf('iniciar sesion') >= 0")
            append(" || txt.indexOf('iniciar sesión') >= 0")
            append(" || txt.indexOf('inicia sesion') >= 0")
            append(" || txt.indexOf('inicia sesión') >= 0")
            append(" || txt.indexOf('sign in') >= 0")
            append(" || txt.indexOf('acceder') >= 0")
            append(" || href.indexOf('ServiceLogin') >= 0")
            append(" || href.indexOf('signin') >= 0;")
            append("if (maybeLogin) { el.click(); return 'clicked'; }")
            append("}")
            append("return 'none';")
            append("} catch (e) { return 'error'; }")
            append("})();")
        }

        currentWebView.evaluateJavascript(script, null)
    }

    private fun looksLikeAuthenticatedCookie(cookieHeader: String): Boolean {
        val lower = cookieHeader.lowercase()
        return lower.contains("sapisid=")
            || lower.contains("__secure-3papisid=")
            || lower.contains("ssid=")
            || lower.contains("sid=")
    }

    private fun completeWithSession() {
        if (TextUtils.isEmpty(lastCookieHeader)) {
            refreshSessionState()
            if (TextUtils.isEmpty(lastCookieHeader)) {
                return
            }
        }

        val data = Intent()
        data.putExtra(EXTRA_SESSION_COOKIE_HEADER, lastCookieHeader)

        var userAgent = ""
        val settings = webView?.settings
        if (settings?.userAgentString != null) {
            userAgent = settings.userAgentString
        }

        Log.i(TAG_STREAMING_WEB, "session_completed cookieLength=" + lastCookieHeader.length)
        data.putExtra(EXTRA_SESSION_USER_AGENT, userAgent)
        setResult(RESULT_OK, data)
        finish()
    }

    override fun onDestroy() {
        webView?.stopLoading()
        webView?.destroy()
        super.onDestroy()
    }

    companion object {
        const val EXTRA_SESSION_COOKIE_HEADER = "extra_session_cookie_header"
        const val EXTRA_SESSION_USER_AGENT = "extra_session_user_agent"
        private const val TAG_STREAMING_WEB = "SleppifyStreamingWeb"
    }
}
