package com.jsnyder.finndash;

import android.app.Activity;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

/**
 * Finn Dash - a full screen web view around the game.
 * The game itself is app/src/main/assets/finn-dash.html and runs completely offline.
 */
public class MainActivity extends Activity {

    private WebView web;
    private long lastBackPress = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        web = new WebView(this);
        WebSettings settings = web.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);          // the game saves players in localStorage
        settings.setSupportZoom(false);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setAllowFileAccess(true);
        settings.setMediaPlaybackRequiresUserGesture(false);
        settings.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient());
        web.setBackgroundColor(0xFF062447);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        web.setHapticFeedbackEnabled(false);
        web.setLongClickable(false);
        web.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                return true;                           // no text selection pop-ups mid-game
            }
        });

        setContentView(web);
        web.loadUrl("file:///android_asset/finn-dash.html");
        hideSystemBars();
    }

    private void hideSystemBars() {
        View decor = getWindow().getDecorView();
        decor.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                        | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                        | View.SYSTEM_UI_FLAG_FULLSCREEN
                        | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideSystemBars();
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (web != null) {
            // pause the run so nobody loses a swim to a stray home button
            web.evaluateJavascript(
                    "try { var b = document.getElementById('pauseBtn');"
                            + " if (b && document.getElementById('game').classList.contains('on')) b.click(); } catch (e) {}",
                    null);
            web.onPause();
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (web != null) web.onResume();
        hideSystemBars();
    }

    @Override
    public void onBackPressed() {
        // one tap pauses, a second tap within three seconds leaves the game
        long now = System.currentTimeMillis();
        if (now - lastBackPress < 3000) {
            super.onBackPressed();
            return;
        }
        lastBackPress = now;
        if (web != null) {
            web.evaluateJavascript(
                    "try { var b = document.getElementById('pauseBtn');"
                            + " if (b && document.getElementById('game').classList.contains('on')) b.click(); } catch (e) {}",
                    null);
        }
        Toast.makeText(this, "Press back again to exit Finn Dash", Toast.LENGTH_SHORT).show();
    }

    @Override
    protected void onDestroy() {
        if (web != null) {
            web.destroy();
            web = null;
        }
        super.onDestroy();
    }
}
