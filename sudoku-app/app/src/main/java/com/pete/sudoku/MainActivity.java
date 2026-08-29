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

public class MainActivity extends Activity {
    private WebView webView;

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
                installFastPencil(view);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private void installFastPencil(WebView view) {
        String js = "(function(){" +
            "if(document.getElementById('fastPencil'))return;" +
            "var tools=document.querySelector('.tools');if(!tools)return;" +
            "tools.style.gridTemplateColumns='repeat(4,1fr)';" +
            "var b=document.createElement('button');b.className='tool';b.id='fastPencil';" +
            "b.innerHTML='<span class=\"ico\">⚡</span>Fast pencil';" +
            "var pencil=document.getElementById('pencil');pencil.parentNode.insertBefore(b,pencil.nextSibling);" +
            "b.onclick=function(){" +
              "var cells=[].slice.call(document.querySelectorAll('.cell'));" +
              "var idx=cells.findIndex(function(c){return c.classList.contains('selected');});" +
              "var msg=document.getElementById('message');" +
              "function say(t){msg.textContent=t;setTimeout(function(){if(msg.textContent===t)msg.textContent='';},1800);}" +
              "if(idx<0){say('Select an empty square first');return;}" +
              "var cell=cells[idx];if(cell.classList.contains('given')||cell.classList.contains('user')){say('Fast pencil works on an empty square');return;}" +
              "var vals=cells.map(function(c){if(c.querySelector('.notes'))return 0;var n=parseInt(c.textContent,10);return isNaN(n)?0:n;});" +
              "var r=Math.floor(idx/9),c=idx%9,used={};" +
              "for(var i=0;i<9;i++){used[vals[r*9+i]]=1;used[vals[i*9+c]]=1;}" +
              "var br=Math.floor(r/3)*3,bc=Math.floor(c/3)*3;" +
              "for(var rr=br;rr<br+3;rr++)for(var cc=bc;cc<bc+3;cc++)used[vals[rr*9+cc]]=1;" +
              "document.getElementById('erase').click();" +
              "if(!pencil.classList.contains('active'))pencil.click();" +
              "var pad=document.querySelectorAll('.num'),count=0;" +
              "for(var n=1;n<=9;n++)if(!used[n]){pad[n-1].click();count++;}" +
              "try{if(window.Android&&Android.haptic)Android.haptic();}catch(e){}" +
              "say('Added '+count+' pencil candidates');" +
            "};" +
          "})();";
        view.evaluateJavascript(js, null);
    }

    private class NativeBridge {
        @JavascriptInterface
        public void haptic() {
            runOnUiThread(() -> webView.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP));
        }
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
