package com.pete.sudoku;

import android.app.Activity;
import android.graphics.Color;
import android.os.Bundle;
import android.view.HapticFeedbackConstants;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private WebView webView;
    private volatile boolean suppressHaptic = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setStatusBarColor(Color.rgb(13, 17, 23));
        getWindow().setNavigationBarColor(Color.rgb(13, 17, 23));

        webView = new WebView(this);
        webView.setBackgroundColor(Color.rgb(13, 17, 23));
        webView.setOverScrollMode(View.OVER_SCROLL_NEVER);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setTextZoom(100);

        webView.addJavascriptInterface(new NativeBridge(), "Android");
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                super.onPageFinished(view, url);
                String js24 = readAssetText("upgrade-v24.js");
                String js25 = readAssetText("upgrade-v25.js");
                if (js24 != null && !js24.isEmpty()) {
                    view.evaluateJavascript(js24, value -> {
                        if (js25 != null && !js25.isEmpty()) view.evaluateJavascript(js25, null);
                    });
                } else if (js25 != null && !js25.isEmpty()) {
                    view.evaluateJavascript(js25, null);
                }
            }
        });

        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private String readAssetText(String name) {
        try (InputStream in = getAssets().open(name);
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[4096];
            int read;
            while ((read = in.read(buffer)) != -1) out.write(buffer, 0, read);
            return out.toString(StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            return null;
        }
    }

    private class NativeBridge {
        @JavascriptInterface
        public void haptic() {
            if (suppressHaptic) return;
            runOnUiThread(() -> webView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
        }

        @JavascriptInterface
        public void setBulk(boolean enabled) {
            suppressHaptic = enabled;
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
