package com.example.sleppify;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.webkit.CookieManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

public class YouTubeMusicWebSessionActivity extends AppCompatActivity {

    public static final String EXTRA_SESSION_COOKIE_HEADER = "extra_session_cookie_header";
    public static final String EXTRA_SESSION_USER_AGENT = "extra_session_user_agent";

    private WebView webView;
    private Button btnFinish;
    private TextView tvStatus;
    private String lastCookieHeader = "";
    private long lastAutoLoginClickAtMs;
    private boolean autoConnectTriggered;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_youtube_web_session);

        webView = findViewById(R.id.webSessionView);
        btnFinish = findViewById(R.id.btnSessionFinish);
        Button btnClose = findViewById(R.id.btnSessionClose);
        tvStatus = findViewById(R.id.tvSessionStatus);

        btnFinish.setEnabled(false);
        btnFinish.setOnClickListener(v -> completeWithSession());
        btnClose.setOnClickListener(v -> {
            setResult(RESULT_CANCELED);
            finish();
        });

        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        cookieManager.setAcceptThirdPartyCookies(webView, true);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setSupportMultipleWindows(false);
        settings.setLoadsImagesAutomatically(true);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                if (url == null) {
                    return false;
                }
                Uri uri = Uri.parse(url);
                String host = uri.getHost() == null ? "" : uri.getHost();
                // Keep navigation inside YouTube/Google login domains for predictable session capture.
                if (host.endsWith("youtube.com") || host.endsWith("google.com") || host.endsWith("gstatic.com")) {
                    return false;
                }
                return true;
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                attemptAutoLoginClick();
                refreshSessionState();
            }
        });

        webView.loadUrl("https://music.youtube.com/");
    }

    private void refreshSessionState() {
        if (webView == null) {
            return;
        }

        String cookie = CookieManager.getInstance().getCookie("https://music.youtube.com/");
        if (TextUtils.isEmpty(cookie)) {
            tvStatus.setText("Inicia sesion en YouTube Music para continuar.");
            btnFinish.setEnabled(false);
            lastCookieHeader = "";
            return;
        }

        if (looksLikeAuthenticatedCookie(cookie)) {
            tvStatus.setText("Sesion detectada. Conectando...");
            btnFinish.setEnabled(true);
            lastCookieHeader = cookie;
            if (!autoConnectTriggered) {
                autoConnectTriggered = true;
                completeWithSession();
            }
            return;
        }

        tvStatus.setText("Sesion aun no validada. Completa el inicio de sesion.");
        btnFinish.setEnabled(false);
        lastCookieHeader = "";
    }

    private void attemptAutoLoginClick() {
        if (webView == null) {
            return;
        }

        String cookie = CookieManager.getInstance().getCookie("https://music.youtube.com/");
        if (!TextUtils.isEmpty(cookie) && looksLikeAuthenticatedCookie(cookie)) {
            return;
        }

        long now = System.currentTimeMillis();
        if ((now - lastAutoLoginClickAtMs) < 1200L) {
            return;
        }
        lastAutoLoginClickAtMs = now;

        String script = "(function(){"
                + "try {"
                + "var nodes = Array.prototype.slice.call(document.querySelectorAll('a,button,tp-yt-paper-button,yt-formatted-string'));"
                + "for (var i = 0; i < nodes.length; i++) {"
                + "var el = nodes[i];"
                + "var txt = ((el.innerText || el.textContent || '') + '').toLowerCase();"
                + "var href = (el.getAttribute && el.getAttribute('href')) || '';"
                + "var maybeLogin = txt.indexOf('iniciar sesion') >= 0"
                + " || txt.indexOf('iniciar sesi\u00f3n') >= 0"
                + " || txt.indexOf('inicia sesion') >= 0"
                + " || txt.indexOf('inicia sesi\u00f3n') >= 0"
                + " || txt.indexOf('sign in') >= 0"
                + " || txt.indexOf('acceder') >= 0"
                + " || href.indexOf('ServiceLogin') >= 0"
                + " || href.indexOf('signin') >= 0;"
                + "if (maybeLogin) { el.click(); return 'clicked'; }"
                + "}"
                + "return 'none';"
                + "} catch (e) { return 'error'; }"
                + "})();";

        webView.evaluateJavascript(script, null);
    }

    private boolean looksLikeAuthenticatedCookie(@NonNull String cookieHeader) {
        String lower = cookieHeader.toLowerCase();
        return lower.contains("sapisid=")
                || lower.contains("__secure-3papisid=")
                || lower.contains("ssid=")
                || lower.contains("sid=");
    }

    private void completeWithSession() {
        if (TextUtils.isEmpty(lastCookieHeader)) {
            refreshSessionState();
            if (TextUtils.isEmpty(lastCookieHeader)) {
                return;
            }
        }

        Intent data = new Intent();
        data.putExtra(EXTRA_SESSION_COOKIE_HEADER, lastCookieHeader);
        String userAgent = "";
        if (webView != null && webView.getSettings() != null && webView.getSettings().getUserAgentString() != null) {
            userAgent = webView.getSettings().getUserAgentString();
        }
        data.putExtra(EXTRA_SESSION_USER_AGENT, userAgent);
        setResult(RESULT_OK, data);
        finish();
    }

    @Override
    protected void onDestroy() {
        if (webView != null) {
            webView.stopLoading();
            webView.setWebViewClient(null);
            webView.destroy();
        }
        super.onDestroy();
    }
}
