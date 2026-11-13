package com.jsnyder.finndash;

import android.app.Activity;
import android.content.Context;
import android.graphics.Color;
import android.media.AudioAttributes;
import android.media.AudioFocusRequest;
import android.media.AudioManager;
import android.os.Build;
import android.os.Bundle;
import android.view.DisplayCutout;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.window.OnBackInvokedCallback;
import android.window.OnBackInvokedDispatcher;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PendingPurchasesParams;
import com.android.billingclient.api.ProductDetails;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.QueryProductDetailsParams;
import com.android.billingclient.api.QueryPurchasesParams;

import org.json.JSONObject;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Collections;
import java.util.Date;
import java.util.List;

/**
 * Finn Dash for Android (v5): the whole game is assets/finn-dash.html, shown full screen in a web view.
 *
 * This wrapper adds the things a web page can't do by itself:
 *  - full screen, screen kept awake, play area kept clear of the camera hole
 *  - the Back button / back swipe goes to the game (Android 16 style), which pauses, closes menus, and asks
 *    before leaving
 *  - the game pauses and goes quiet when another app needs the sound (a phone call, the alarm)
 *  - Tip Finn: the Golden Narwhal, bought once through Google Play Billing ("golden_narwhal")
 *  - Play Age Signals (beta), checked only when a grown-up opens the Support Finn card, so the app can respect
 *    a parent's purchase settings. Nothing from it is stored.
 *
 * The game talks to this file through window.FinnPlay (below); this file answers through window.onFinnPlay(json).
 */
public class MainActivity extends Activity implements PurchasesUpdatedListener {

    // The saves live in this page's local storage, so this address must never change.
    private static final String PAGE = "file:///android_asset/finn-dash.html";
    private static final String NARWHAL = "golden_narwhal";

    private WebView web;
    private boolean jsReady = false;

    // what the game is told about the store
    private BillingClient billing;
    private String store = "connecting";        // connecting | ok | unavailable
    private ProductDetails narwhal;
    private String price = null;
    private Boolean owned = null;               // null until Google Play has answered
    private boolean pending = false;

    private AudioManager audio;
    private Object focusRequest;                // an AudioFocusRequest on Android 8+
    private int[] insets = {0, 0, 0, 0};

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        if (Build.VERSION.SDK_INT >= 28) {
            WindowManager.LayoutParams lp = getWindow().getAttributes();
            lp.layoutInDisplayCutoutMode = Build.VERSION.SDK_INT >= 30
                    ? WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
                    : WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(lp);
        }
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);

        web = new WebView(this);
        web.setBackgroundColor(Color.rgb(6, 36, 71));
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);                    // the saved players and coins
        s.setMediaPlaybackRequiresUserGesture(false);
        s.setTextZoom(100);                              // a big system font size mustn't break the layout
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        web.addJavascriptInterface(new Bridge(), "FinnPlay");
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                // the game never leaves its own page (and the app has no internet anyway)
                return !request.getUrl().toString().startsWith("file:///android_asset/");
            }

            @Override
            public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
                // the web view's engine was stopped (low memory): start fresh instead of crashing
                recreate();
                return true;
            }
        });
        web.setOnApplyWindowInsetsListener((v, wi) -> {
            readInsets(wi);
            return v.onApplyWindowInsets(wi);
        });
        setContentView(web);
        web.loadUrl(PAGE);

        audio = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        if (Build.VERSION.SDK_INT >= 33) Api33.registerBack(this);
        startBilling();
    }

    @Override
    protected void onResume() {
        super.onResume();
        web.onResume();
        hideBars();
        requestFocus();
        if ("ok".equals(store)) checkPurchases();       // catches refunds and finished "pending" payments
    }

    @Override
    protected void onPause() {
        web.onPause();
        abandonFocus();
        super.onPause();
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideBars();
    }

    @Override
    protected void onDestroy() {
        if (billing != null) billing.endConnection();
        if (web != null) web.destroy();
        super.onDestroy();
    }

    /* ---------------- Back ---------------- */

    // Android 12 and older. (From Android 13 on, Api33 registers the modern callback, and with
    // enableOnBackInvokedCallback in the manifest this method is no longer called at all.)
    @Override
    @SuppressWarnings("deprecation")
    public void onBackPressed() {
        goBack();
    }

    void goBack() {
        if (!jsReady) { finish(); return; }
        // the game decides: close a menu, pause, go back to the main menu, or ask "Leave Finn Dash?"
        web.evaluateJavascript("(window.finnBack ? finnBack() : 'exit')", v -> {
            if (v != null && v.contains("exit")) finish();
        });
    }

    static class Api33 {
        static void registerBack(MainActivity a) {
            OnBackInvokedCallback cb = a::goBack;
            a.getOnBackInvokedDispatcher().registerOnBackInvokedCallback(OnBackInvokedDispatcher.PRIORITY_DEFAULT, cb);
        }
    }

    /* ---------------- Screen ---------------- */

    @SuppressWarnings("deprecation")
    private void hideBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) {
                c.hide(WindowInsets.Type.systemBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN);
        }
    }

    // the camera hole, in the web page's own pixels, so the game can keep its buttons clear of it
    private void readInsets(WindowInsets wi) {
        int t = 0, r = 0, b = 0, l = 0;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets i = wi.getInsets(WindowInsets.Type.displayCutout());
            t = i.top; r = i.right; b = i.bottom; l = i.left;
        } else if (Build.VERSION.SDK_INT >= 28) {
            DisplayCutout dc = wi.getDisplayCutout();
            if (dc != null) { t = dc.getSafeInsetTop(); r = dc.getSafeInsetRight(); b = dc.getSafeInsetBottom(); l = dc.getSafeInsetLeft(); }
        }
        float d = getResources().getDisplayMetrics().density;
        insets = new int[]{Math.round(t / d), Math.round(r / d), Math.round(b / d), Math.round(l / d)};
        sendInsets();
    }

    private void sendInsets() {
        js("window.finnInsets && finnInsets(" + insets[0] + "," + insets[1] + "," + insets[2] + "," + insets[3] + ")");
    }

    /* ---------------- Sound ---------------- */

    private final AudioManager.OnAudioFocusChangeListener focusListener = change -> {
        if (change == AudioManager.AUDIOFOCUS_LOSS || change == AudioManager.AUDIOFOCUS_LOSS_TRANSIENT) {
            js("window.finnAudioFocus && finnAudioFocus(false)");     // pause and go quiet
        } else if (change == AudioManager.AUDIOFOCUS_GAIN) {
            js("window.finnAudioFocus && finnAudioFocus(true)");
        }
        // AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK (a notification ping): Android turns us down by itself
    };

    @SuppressWarnings("deprecation")
    private void requestFocus() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26) focusRequest = Api26.request(audio, focusListener);
        else audio.requestAudioFocus(focusListener, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
    }

    @SuppressWarnings("deprecation")
    private void abandonFocus() {
        if (audio == null) return;
        if (Build.VERSION.SDK_INT >= 26) { if (focusRequest != null) Api26.abandon(audio, focusRequest); }
        else audio.abandonAudioFocus(focusListener);
    }

    static class Api26 {
        static Object request(AudioManager am, AudioManager.OnAudioFocusChangeListener l) {
            AudioFocusRequest r = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                    .setAudioAttributes(new AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
                    .setOnAudioFocusChangeListener(l)
                    .setWillPauseWhenDucked(false)
                    .build();
            am.requestAudioFocus(r);
            return r;
        }

        static void abandon(AudioManager am, Object r) {
            am.abandonAudioFocusRequest((AudioFocusRequest) r);
        }
    }

    /* ---------------- Talking to the game ---------------- */

    private void js(String code) {
        runOnUiThread(() -> { if (web != null && jsReady) web.evaluateJavascript(code, null); });
    }

    private void send(JSONObject o) {
        js("window.onFinnPlay && onFinnPlay(" + o.toString() + ")");
    }

    // everything the game needs to know about the store, in one message; "event" says what just happened
    private void sendState(String event) {
        try {
            JSONObject o = new JSONObject();
            o.put("type", "state");
            o.put("store", store);
            o.put("price", price == null ? JSONObject.NULL : price);
            o.put("product", narwhal != null);
            o.put("owned", owned == null ? JSONObject.NULL : owned);
            o.put("pending", pending);
            if (event != null) o.put("event", event);
            send(o);
        } catch (Exception ignored) { }
    }

    /** What the game can call. These run on a background thread, so anything real hops to the UI thread. */
    class Bridge {
        @JavascriptInterface
        public void hello() {
            runOnUiThread(() -> { jsReady = true; sendInsets(); sendState(null); });
        }

        @JavascriptInterface
        public void buyNarwhal() { runOnUiThread(MainActivity.this::buy); }

        @JavascriptInterface
        public void restore() {
            runOnUiThread(() -> {
                if ("ok".equals(store)) checkPurchases(); else sendState("unavailable");
            });
        }

        @JavascriptInterface
        public void ageCheck() { runOnUiThread(MainActivity.this::ageCheckNow); }

        @JavascriptInterface
        public void exit() { runOnUiThread(MainActivity.this::finish); }
    }

    /* ---------------- Google Play Billing: the Golden Narwhal ---------------- */

    private void startBilling() {
        try {
            billing = BillingClient.newBuilder(this)
                    .setListener(this)
                    .enablePendingPurchases(PendingPurchasesParams.newBuilder().enableOneTimeProducts().build())
                    .enableAutoServiceReconnection()
                    .build();
            billing.startConnection(new BillingClientStateListener() {
                @Override
                public void onBillingSetupFinished(BillingResult r) {
                    if (r.getResponseCode() == BillingClient.BillingResponseCode.OK) {
                        store = "ok";
                        sendState(null);
                        loadProduct();
                        checkPurchases();
                    } else {
                        store = "unavailable";
                        sendState(null);
                    }
                }

                @Override
                public void onBillingServiceDisconnected() {
                    // the library reconnects by itself (enableAutoServiceReconnection)
                }
            });
        } catch (Throwable t) {
            store = "unavailable";          // e.g. a tablet without Google Play
        }
    }

    private void loadProduct() {
        QueryProductDetailsParams params = QueryProductDetailsParams.newBuilder()
                .setProductList(Collections.singletonList(QueryProductDetailsParams.Product.newBuilder()
                        .setProductId(NARWHAL)
                        .setProductType(BillingClient.ProductType.INAPP)
                        .build()))
                .build();
        billing.queryProductDetailsAsync(params, (r, result) -> {
            List<ProductDetails> list = result == null ? null : result.getProductDetailsList();
            if (r.getResponseCode() == BillingClient.BillingResponseCode.OK && list != null && !list.isEmpty()) {
                narwhal = list.get(0);
                ProductDetails.OneTimePurchaseOfferDetails offer = narwhal.getOneTimePurchaseOfferDetails();
                if (offer != null) price = offer.getFormattedPrice();
            }
            sendState(null);
        });
    }

    private void buy() {
        if (!"ok".equals(store) || narwhal == null) { sendState("unavailable"); return; }
        BillingFlowParams flow = BillingFlowParams.newBuilder()
                .setProductDetailsParamsList(Collections.singletonList(
                        BillingFlowParams.ProductDetailsParams.newBuilder().setProductDetails(narwhal).build()))
                .build();
        BillingResult r = billing.launchBillingFlow(this, flow);
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) checkPurchases();
        else if (code != BillingClient.BillingResponseCode.OK) sendState("error");
    }

    @Override
    public void onPurchasesUpdated(BillingResult r, List<Purchase> purchases) {
        int code = r.getResponseCode();
        if (code == BillingClient.BillingResponseCode.OK && purchases != null) {
            String event = null;
            for (Purchase p : purchases) {
                if (!p.getProducts().contains(NARWHAL)) continue;
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledge(p);
                    owned = true; pending = false; event = "bought";
                } else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) {
                    pending = true; event = "pending";
                }
            }
            sendState(event);
        } else if (code == BillingClient.BillingResponseCode.USER_CANCELED) {
            sendState("cancel");
        } else if (code == BillingClient.BillingResponseCode.ITEM_ALREADY_OWNED) {
            checkPurchases();
        } else {
            sendState("error");
        }
    }

    // Play gives the money back if a purchase isn't acknowledged within 3 days
    private void acknowledge(Purchase p) {
        if (p.isAcknowledged()) return;
        billing.acknowledgePurchase(AcknowledgePurchaseParams.newBuilder()
                .setPurchaseToken(p.getPurchaseToken()).build(), br -> { });
    }

    // what this Google account owns right now: restores the Narwhal on a new tablet, and takes it away after a refund
    private void checkPurchases() {
        billing.queryPurchasesAsync(QueryPurchasesParams.newBuilder()
                .setProductType(BillingClient.ProductType.INAPP).build(), (r, list) -> {
            if (r.getResponseCode() != BillingClient.BillingResponseCode.OK || list == null) return;
            boolean has = false, wait = false;
            for (Purchase p : list) {
                if (!p.getProducts().contains(NARWHAL)) continue;
                if (p.getPurchaseState() == Purchase.PurchaseState.PURCHASED) { has = true; acknowledge(p); }
                else if (p.getPurchaseState() == Purchase.PurchaseState.PENDING) wait = true;
            }
            owned = has; pending = wait;
            sendState("checked");
        });
    }

    /* ---------------- Play Age Signals (beta), by reflection ----------------
       Used only when a grown-up opens the Support Finn card. The library is still in beta, so it is
       reached by name at run time: if it is missing or its shape changes, the game hears "unknown"
       and Google Play's own purchase approval still applies. The answer is passed to the game and
       not kept anywhere. */

    private static final String[] AGE_PKGS = {"com.google.android.play.agesignals.", "com.google.android.play.agesignals.model."};

    private static Class<?> ageClass(String name) throws ClassNotFoundException {
        for (String p : AGE_PKGS) {
            try { return Class.forName(p + name); } catch (ClassNotFoundException ignored) { }
        }
        throw new ClassNotFoundException(name);
    }

    private static Object call(Object o, String name, Object... args) throws Exception {
        for (Class<?> c = o.getClass(); c != null; c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                    m.setAccessible(true);
                    return m.invoke(o, args);
                }
            }
        }
        for (Method m : o.getClass().getMethods()) {
            if (m.getName().equals(name) && m.getParameterTypes().length == args.length) {
                m.setAccessible(true);
                return m.invoke(o, args);
            }
        }
        throw new NoSuchMethodException(name);
    }

    interface Done { void run(Object result) throws Exception; }

    // a Google Play Services "Task": call back when it finishes (null on failure)
    private void whenDone(Object task, Done done) throws Exception {
        Class<?> lis = Class.forName("com.google.android.gms.tasks.OnCompleteListener");
        InvocationHandler h = (proxy, m, a) -> {
            switch (m.getName()) {
                case "onComplete":
                    Object t = a[0];
                    Object res = null;
                    try { if (Boolean.TRUE.equals(call(t, "isSuccessful"))) res = call(t, "getResult"); } catch (Exception ignored) { }
                    try { done.run(res); } catch (Throwable e) { sendAge(null); }
                    return null;
                case "hashCode": return System.identityHashCode(proxy);
                case "equals": return proxy == a[0];
                case "toString": return "FinnAgeTask";
                default: return null;
            }
        };
        Object listener = Proxy.newProxyInstance(lis.getClassLoader(), new Class<?>[]{lis}, h);
        call(task, "addOnCompleteListener", listener);
    }

    private void ageCheckNow() {
        try {
            Object mgr = ageClass("AgeSignalsManagerFactory").getMethod("create", Context.class).invoke(null, getApplicationContext());
            Object accessReq = null;
            try {
                Object b = ageClass("AgeSignalsAccessRequest").getMethod("builder").invoke(null);
                call(b, "setActivity", this);
                accessReq = call(b, "build");
            } catch (Throwable ignored) { /* an older library without the access step */ }
            if (accessReq == null) { ageQuery(mgr, null); return; }
            whenDone(call(mgr, "requestAgeSignalsAccess", accessReq), res -> {
                Object status = res == null ? null : call(res, "ageSignalsStatus");
                Object shared = null;
                try { shared = ageClass("AgeSignalsStatus").getField("SHARED").get(null); } catch (Throwable ignored) { }
                if (status != null && shared != null && !status.equals(shared)) {
                    JSONObject o = new JSONObject();
                    o.put("available", true);
                    o.put("shared", false);
                    o.put("access", String.valueOf(status));
                    sendAge(o);
                } else ageQuery(mgr, status);
            });
        } catch (Throwable t) {
            sendAge(null);
        }
    }

    private void ageQuery(Object mgr, Object access) {
        try {
            Object req = call(ageClass("AgeSignalsRequest").getMethod("builder").invoke(null), "build");
            whenDone(call(mgr, "checkAgeSignals", req), res -> {
                JSONObject o = new JSONObject();
                o.put("available", res != null);
                if (access != null) o.put("access", String.valueOf(access));
                if (res != null) {
                    // every simple answer the result offers (ageLower, ageUpper, userStatus, ...), by name
                    for (Method m : res.getClass().getMethods()) {
                        if (m.getParameterTypes().length != 0 || m.getDeclaringClass() == Object.class) continue;
                        String n = m.getName();
                        if (n.equals("installId") || n.equals("toBuilder") || n.equals("hashCode") || n.equals("toString")) continue;
                        try {
                            m.setAccessible(true);
                            Object v = m.invoke(res);
                            if (v == null) o.put(n, JSONObject.NULL);
                            else if (v instanceof Number || v instanceof Boolean || v instanceof String) o.put(n, v);
                            else if (v instanceof Date) o.put(n, ((Date) v).getTime());
                            else o.put(n, String.valueOf(v));
                        } catch (Throwable ignored) { }
                    }
                }
                sendAge(o);
            });
        } catch (Throwable t) {
            sendAge(null);
        }
    }

    private void sendAge(JSONObject o) {
        try {
            if (o == null) { o = new JSONObject(); o.put("available", false); }
            o.put("type", "age");
            send(o);
        } catch (Exception ignored) { }
    }
}
