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
                installEnhancements(view);
            }
        });
        webView.loadUrl("file:///android_asset/index.html");
        setContentView(webView);
    }

    private void installEnhancements(WebView view) {
        String js = "(function(){" +
            "function h(){try{if(window.Android&&Android.haptic)Android.haptic();}catch(e){}}" +
            "function say(t){var m=document.getElementById('message');if(!m)return;m.textContent=t;setTimeout(function(){if(m.textContent===t)m.textContent='';},2200);}" +
            "var pending=sessionStorage.getItem('peteSudokuEnhanceMessage');if(pending){sessionStorage.removeItem('peteSudokuEnhanceMessage');setTimeout(function(){say(pending);},120);}" +
            "var tools=document.querySelector('.tools');" +
            "if(tools){tools.style.gridTemplateColumns='repeat(4,1fr)';var b=document.getElementById('fastPencil');if(!b){b=document.createElement('button');b.className='tool';b.id='fastPencil';b.innerHTML='<span class=\"ico\">⚡</span>Fast pencil';var pencil=document.getElementById('pencil');pencil.parentNode.insertBefore(b,pencil.nextSibling);}" +
            "b.onclick=function(){var raw=localStorage.getItem('peteSudokuV2')||localStorage.getItem('peteSudoku'),s;try{s=JSON.parse(raw||'null');}catch(e){}if(!s||!s.current||s.current.length!==81||!s.puzzle||s.puzzle.length!==81){say('Start a puzzle first');return;}var vals=s.current.slice(),ns=Array.isArray(s.notes)?s.notes:[];while(ns.length<81)ns.push([]);function cand(idx){var r=Math.floor(idx/9),c=idx%9,u={};for(var x=0;x<9;x++){if(vals[r*9+x])u[vals[r*9+x]]=1;if(vals[x*9+c])u[vals[x*9+c]]=1;}var br=Math.floor(r/3)*3,bc=Math.floor(c/3)*3;for(var rr=br;rr<br+3;rr++)for(var cc=bc;cc<bc+3;cc++)if(vals[rr*9+cc])u[vals[rr*9+cc]]=1;var a=[];for(var n=1;n<=9;n++)if(!u[n])a.push(n);return a;}var cells=0,total=0;for(var i=0;i<81;i++)if(!s.puzzle[i]&&!vals[i]){ns[i]=cand(i);cells++;total+=ns[i].length;}if(!cells){say('No empty cells to pencil');return;}var tv=document.getElementById('timer');if(tv){var p=tv.textContent.split(':');if(p.length===2)s.seconds=(parseInt(p[0],10)||0)*60+(parseInt(p[1],10)||0);}s.notes=ns;localStorage.setItem('peteSudokuV2',JSON.stringify(s));sessionStorage.setItem('peteSudokuEnhanceMessage','Fast pencil filled '+cells+' empty cells');h();location.reload();};}" +
            "var wrap=document.querySelector('.board-wrap'),board=document.getElementById('board');if(!wrap||!board||wrap.dataset.zoomInstalled)return;wrap.dataset.zoomInstalled='1';wrap.style.position='relative';wrap.style.overflow='hidden';wrap.style.touchAction='none';board.style.touchAction='none';board.style.transformOrigin='0 0';board.style.willChange='transform';" +
            "var actions=document.querySelector('.header-actions'),zbtn=document.getElementById('zoomReset');if(actions&&!zbtn){zbtn=document.createElement('button');zbtn.id='zoomReset';zbtn.className='icon-btn';zbtn.textContent='100%';zbtn.style.fontSize='11px';zbtn.style.fontWeight='800';zbtn.style.width='50px';actions.insertBefore(zbtn,actions.firstChild);}" +
            "var z=1,px=0,py=0,g=null,moved=false,lastTap=0;function clamp(){var mx=Math.max(0,(z-1)*board.offsetWidth),my=Math.max(0,(z-1)*board.offsetHeight);px=Math.min(0,Math.max(-mx,px));py=Math.min(0,Math.max(-my,py));}function apply(){clamp();board.style.transform='translate('+px+'px,'+py+'px) scale('+z+')';if(zbtn)zbtn.textContent=Math.round(z*100)+'%';}function reset(){z=1;px=0;py=0;g=null;apply();}function dist(a,b){return Math.hypot(a.clientX-b.clientX,a.clientY-b.clientY);}function mid(a,b){var r=wrap.getBoundingClientRect();return{x:(a.clientX+b.clientX)/2-r.left,y:(a.clientY+b.clientY)/2-r.top};}" +
            "wrap.addEventListener('touchstart',function(e){moved=false;if(e.touches.length===2){e.preventDefault();var m=mid(e.touches[0],e.touches[1]);g={t:'pinch',d:dist(e.touches[0],e.touches[1]),z:z,px:px,py:py,mx:m.x,my:m.y};}else if(e.touches.length===1&&z>1){var t=e.touches[0];g={t:'pan',x:t.clientX,y:t.clientY,px:px,py:py};}},{passive:false});" +
            "wrap.addEventListener('touchmove',function(e){if(!g)return;if(g.t==='pinch'&&e.touches.length>=2){e.preventDefault();moved=true;var ratio=dist(e.touches[0],e.touches[1])/g.d,nz=Math.min(3,Math.max(1,g.z*ratio)),f=nz/g.z;z=nz;px=g.mx-(g.mx-g.px)*f;py=g.my-(g.my-g.py)*f;apply();}else if(g.t==='pan'&&e.touches.length===1){e.preventDefault();var t=e.touches[0],dx=t.clientX-g.x,dy=t.clientY-g.y;if(Math.abs(dx)+Math.abs(dy)>5)moved=true;px=g.px+dx;py=g.py+dy;apply();}},{passive:false});" +
            "wrap.addEventListener('touchend',function(e){if(e.touches.length===0){g=null;if(!moved){var now=Date.now();if(now-lastTap<320){reset();h();lastTap=0;}else lastTap=now;}}else if(e.touches.length===1&&z>1){var t=e.touches[0];g={t:'pan',x:t.clientX,y:t.clientY,px:px,py:py};}});if(zbtn)zbtn.onclick=function(){reset();h();};window.addEventListener('resize',apply);apply();" +
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
