package com.cleanbrowser.app;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.DownloadManager;
import android.app.SearchManager;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.net.http.SslError;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.os.Message;
import android.os.SystemClock;
import android.text.InputType;
import android.text.TextUtils;
import android.view.ContextMenu;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.webkit.CookieManager;
import android.webkit.GeolocationPermissions;
import android.webkit.JsResult;
import android.webkit.PermissionRequest;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.ServiceWorkerClient;
import android.webkit.ServiceWorkerController;
import android.webkit.SslErrorHandler;
import android.webkit.URLUtil;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.GridLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {

    private static final int REQ_FILE = 11;
    private static final int REQ_STORAGE = 12;
    private static final String ALLOW_SCHEME = "cleanbrowser-allow";
    private static final int WIDE_DP = 600;

    // Views
    private View toolbar;
    private FrameLayout content, webContainer, fullscreen;
    private ScrollView home;
    private GridLayout favGrid;
    private EditText address;
    private ImageButton btnBack, btnForward, btnReload, btnHome, btnStar, btnShield, btnMenu;
    private TextView shieldCount, homeStats, popupText;
    private View popupBar;
    private Button popupOpen;
    private ProgressBar progress;
    private CursorView cursor;
    private WebView web;

    // State
    private final Handler ui = new Handler(Looper.getMainLooper());
    private AdBlocker blocker;
    private boolean isTv;
    private boolean cursorEnabled;
    private boolean contentMode;
    private boolean webVisible;
    private boolean blockAds, blockPopups, hideElements, youtubeAds, desktopMode;
    private volatile String pageHost;
    private volatile boolean pageAllowed;
    private final AtomicInteger pageBlocked = new AtomicInteger();
    private int pageBlockedSaved;
    private final Set<String> tempAllowedHosts = Collections.synchronizedSet(new HashSet<>());
    private String mobileUa, desktopUa;
    private Bitmap lastIcon;
    private String lastIconHost;
    private View customView;
    private WebChromeClient.CustomViewCallback customCallback;
    private int orientationBeforeFullscreen = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private ValueCallback<Uri[]> fileCallback;
    private Runnable pendingDownload;
    private String popupUrl;
    private long lastBackPress;
    private long lastUserAction;
    private boolean touchDown;
    private boolean jsScrollPending;
    private boolean lastJsScrollMoved = true;
    private int pendingJsDx, pendingJsDy;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.arabic(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blocker = AdBlocker.get(this);
        isTv = Prefs.isTv(this);
        setContentView(R.layout.activity_main);
        findViewById(R.id.root).setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        bindViews();
        createWebView();
        readSettings();
        applyWidth();
        showHome();
        handleIntent(getIntent());
    }

    // ------------------------------------------------------------------ views

    private void bindViews() {
        toolbar = findViewById(R.id.toolbar);
        content = findViewById(R.id.content);
        webContainer = findViewById(R.id.web_container);
        fullscreen = findViewById(R.id.fullscreen);
        home = findViewById(R.id.home);
        favGrid = findViewById(R.id.fav_grid);
        address = findViewById(R.id.address);
        btnBack = findViewById(R.id.btn_back);
        btnForward = findViewById(R.id.btn_forward);
        btnReload = findViewById(R.id.btn_reload);
        btnHome = findViewById(R.id.btn_home);
        btnStar = findViewById(R.id.btn_star);
        btnShield = findViewById(R.id.btn_shield);
        btnMenu = findViewById(R.id.btn_menu);
        shieldCount = findViewById(R.id.shield_count);
        homeStats = findViewById(R.id.home_stats);
        popupBar = findViewById(R.id.popup_bar);
        popupText = findViewById(R.id.popup_text);
        popupOpen = findViewById(R.id.popup_open);
        progress = findViewById(R.id.progress);
        cursor = findViewById(R.id.cursor);

        btnBack.setOnClickListener(v -> goBack());
        btnForward.setOnClickListener(v -> {
            if (web.canGoForward()) {
                showWeb();
                web.goForward();
            }
        });
        btnReload.setOnClickListener(v -> {
            if (webVisible) web.reload();
        });
        btnHome.setOnClickListener(v -> showHome());
        btnStar.setOnClickListener(v -> toggleFavorite());
        btnShield.setOnClickListener(v -> showShieldDialog());
        btnMenu.setOnClickListener(this::showMenu);
        findViewById(R.id.popup_close).setOnClickListener(v -> hidePopupBar());
        popupOpen.setOnClickListener(v -> {
            String url = popupUrl;
            hidePopupBar();
            if (url != null) {
                String h = Uri.parse(url).getHost();
                if (h != null) tempAllowedHosts.add(h);
                load(url);
            }
        });

        address.setOnEditorActionListener((v, actionId, event) -> {
            boolean enter = event != null && event.getKeyCode() == KeyEvent.KEYCODE_ENTER
                    && event.getAction() == KeyEvent.ACTION_DOWN;
            if (actionId == EditorInfo.IME_ACTION_GO || actionId == EditorInfo.IME_ACTION_DONE
                    || actionId == EditorInfo.IME_ACTION_SEARCH || enter) {
                String text = address.getText().toString();
                hideKeyboard(address);
                address.clearFocus();
                navigate(text);
                return true;
            }
            return false;
        });
        address.setOnFocusChangeListener((v, has) -> {
            if (!has) showUrlInAddress();
        });
        if (isTv) {
            // Editing inside the toolbar is awkward with a remote: open a dialog instead.
            address.setInputType(InputType.TYPE_NULL);
            address.setKeyListener(null);
            address.setMovementMethod(null);
            address.setFocusable(true);
            address.setClickable(true);
            address.setCursorVisible(false);
            address.setOnClickListener(v -> showAddressDialog());
        }

        cursor.setListener(new CursorView.Listener() {
            @Override
            public boolean onEdgeScroll(float x, float y, int dx, int dy) {
                return scrollPage(x, y, dx, dy);
            }

            @Override
            public void onLeaveTop() {
                focusToolbar(address);
            }

            @Override
            public void onDrag(float x, float y) {
                if (touchDown) sendTouch(MotionEvent.ACTION_MOVE, x, y);
            }
        });
    }

    /** Back / forward buttons are hidden on narrow phone screens, where the system back exists. */
    private void applyWidth() {
        boolean wide = screenWidthDp() >= WIDE_DP || isTv;
        btnBack.setVisibility(wide ? View.VISIBLE : View.GONE);
        btnForward.setVisibility(wide ? View.VISIBLE : View.GONE);
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        applyWidth();
        buildFavorites();
    }

    // ---------------------------------------------------------------- WebView

    @SuppressLint("SetJavaScriptEnabled")
    private void createWebView() {
        web = new WebView(this);
        web.setLayoutParams(new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        web.setBackgroundColor(Color.WHITE);
        web.setFocusable(true);
        web.setFocusableInTouchMode(true);
        webContainer.addView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(true);
        s.setBuiltInZoomControls(true);
        s.setDisplayZoomControls(false);
        s.setJavaScriptCanOpenWindowsAutomatically(false);
        s.setSupportMultipleWindows(true);
        s.setMediaPlaybackRequiresUserGesture(true);
        s.setAllowFileAccess(false);
        s.setGeolocationEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);

        // Look like normal Chrome: some sites refuse or degrade the WebView user agent.
        String ua = s.getUserAgentString();
        mobileUa = ua.replace("; wv)", ")").replaceAll("Version/\\d+(\\.\\d+)* ", "");
        String chrome = "120.0.0.0";
        Matcher m = Pattern.compile("Chrome/([\\d.]+)").matcher(ua);
        if (m.find()) chrome = m.group(1);
        desktopUa = "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/"
                + chrome + " Safari/537.36";

        CookieManager.getInstance().setAcceptCookie(true);

        web.setWebViewClient(new Client());
        web.setWebChromeClient(new Chrome());
        web.setDownloadListener(this::onDownload);
        registerForContextMenu(web);

        if (Build.VERSION.SDK_INT >= 24) {
            try {
                ServiceWorkerController.getInstance().setServiceWorkerClient(new ServiceWorkerClient() {
                    @Override
                    public WebResourceResponse shouldInterceptRequest(WebResourceRequest request) {
                        return interceptSubresource(request.getUrl());
                    }
                });
            } catch (Throwable ignored) {
            }
        }
    }

    private void readSettings() {
        blockAds = Prefs.blockAds(this);
        blockPopups = Prefs.blockPopups(this);
        hideElements = Prefs.hideElements(this);
        youtubeAds = Prefs.youtubeAds(this);
        cursorEnabled = Prefs.cursorMode(this);
        cursor.setSpeed(Prefs.cursorSpeed(this));
        boolean desktop = Prefs.desktopMode(this);
        WebSettings s = web.getSettings();
        s.setTextZoom(Prefs.textZoom(this));
        if (desktop != desktopMode || s.getUserAgentString() == null
                || !(s.getUserAgentString().equals(mobileUa) || s.getUserAgentString().equals(desktopUa))) {
            desktopMode = desktop;
            s.setUserAgentString(desktop ? desktopUa : mobileUa);
        }
        if (!cursorEnabled) setContentMode(false);
    }

    private void recreateWebView() {
        try {
            webContainer.removeView(web);
            web.destroy();
        } catch (Throwable ignored) {
        }
        createWebView();
        readSettings();
        web.getSettings().setUserAgentString(desktopMode ? desktopUa : mobileUa);
        showHome();
    }

    // ------------------------------------------------------------- navigation

    private void handleIntent(Intent intent) {
        if (intent == null) return;
        if (Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData() != null) {
            String url = intent.getDataString();
            if (url != null && (url.startsWith("http://") || url.startsWith("https://"))) load(url);
        } else if (Intent.ACTION_WEB_SEARCH.equals(intent.getAction())) {
            String q = intent.getStringExtra(SearchManager.QUERY);
            if (q != null) navigate(q);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        handleIntent(intent);
    }

    /** Turns what the user typed into a URL, or a search. */
    private void navigate(String input) {
        String s = input == null ? "" : input.trim();
        if (s.isEmpty()) return;
        String url;
        String lower = s.toLowerCase(Locale.ROOT);
        if (lower.startsWith("http://") || lower.startsWith("https://") || lower.startsWith("about:")) {
            url = s;
        } else if (!s.contains(" ") && looksLikeHost(s)) {
            String host = s.split("[/:?#]")[0];
            url = (BlockList.isIpAddress(host) || host.equals("localhost") ? "http://" : "https://") + s;
        } else {
            url = Prefs.searchUrl(this) + Uri.encode(s);
        }
        load(url);
    }

    private static boolean looksLikeHost(String s) {
        String host = s.split("[/:?#]")[0];
        if (host.equals("localhost")) return true;
        int dot = host.lastIndexOf('.');
        if (dot <= 0 || dot == host.length() - 1) return false;
        String tld = host.substring(dot + 1);
        return tld.matches("[a-zA-Z]{2,24}|\\d{1,3}") && host.matches("[\\p{L}\\p{N}.-]+");
    }

    private void load(String url) {
        hidePopupBar();
        showWeb();
        web.loadUrl(url);
        if (cursorEnabled) setContentMode(true);
    }

    private void goBack() {
        if (customView != null) {
            exitFullscreen();
        } else if (webVisible && web.canGoBack()) {
            web.goBack();
        } else if (webVisible) {
            showHome();
        }
    }

    private void showWeb() {
        if (!webVisible) {
            webVisible = true;
            home.setVisibility(View.GONE);
            webContainer.setVisibility(View.VISIBLE);
            web.onResume();
        }
        showUrlInAddress();
        updateButtons();
    }

    private void showHome() {
        hidePopupBar();
        if (webVisible) {
            webVisible = false;
            web.onPause();
        }
        setContentMode(false);
        webContainer.setVisibility(View.GONE);
        home.setVisibility(View.VISIBLE);
        progress.setVisibility(View.INVISIBLE);
        buildFavorites();
        showUrlInAddress();
        updateButtons();
        home.scrollTo(0, 0);
        if (isTv) {
            View first = favGrid.getChildAt(0);
            if (first != null) first.requestFocus();
        }
    }

    private void showUrlInAddress() {
        if (address.hasFocus() && !isTv) return;
        String url = webVisible ? web.getUrl() : null;
        address.setText(url == null || url.startsWith("data:") ? "" : url);
    }

    private void updateButtons() {
        boolean back = webVisible;
        btnBack.setEnabled(back);
        btnBack.setAlpha(back ? 1f : 0.35f);
        boolean fwd = web.canGoForward();
        btnForward.setEnabled(fwd);
        btnForward.setAlpha(fwd ? 1f : 0.35f);
        btnReload.setEnabled(webVisible);
        btnReload.setAlpha(webVisible ? 1f : 0.35f);
        String url = webVisible ? web.getUrl() : null;
        boolean fav = url != null && Favorites.contains(this, url);
        btnStar.setImageResource(fav ? R.drawable.ic_star : R.drawable.ic_star_border);
        btnStar.setContentDescription(getString(fav ? R.string.favorite_remove : R.string.favorite_add));
        btnStar.setEnabled(webVisible);
        btnStar.setAlpha(webVisible ? 1f : 0.35f);
        updateShield();
    }

    private void updateShield() {
        boolean off = !blockAds || (webVisible && pageAllowed);
        btnShield.setImageResource(off ? R.drawable.ic_shield_off : R.drawable.ic_shield_on);
        int n = webVisible ? pageBlocked.get() : 0;
        if (n > 0 && !off) {
            shieldCount.setVisibility(View.VISIBLE);
            shieldCount.setText(n > 99 ? "99+" : String.valueOf(n));
        } else {
            shieldCount.setVisibility(View.GONE);
        }
    }

    private final Runnable shieldUpdater = this::updateShield;

    private void countBlocked() {
        pageBlocked.incrementAndGet();
        ui.removeCallbacks(shieldUpdater);
        ui.postDelayed(shieldUpdater, 150);
    }

    private void saveBlockedCount() {
        int now = pageBlocked.get();
        int delta = now - pageBlockedSaved;
        if (delta > 0) blocker.addToTotal(delta);
        pageBlockedSaved = now;
    }

    // --------------------------------------------------------- ad blocking

    private static WebResourceResponse empty() {
        return new WebResourceResponse("text/plain", "utf-8", new ByteArrayInputStream(new byte[0]));
    }

    private boolean blockingOn() {
        return blockAds && !pageAllowed;
    }

    /** Called on a WebView network thread. */
    private WebResourceResponse interceptSubresource(Uri u) {
        if (!blockingOn() || u == null) return null;
        String scheme = u.getScheme();
        if (!"http".equals(scheme) && !"https".equals(scheme)) return null;
        String host = u.getHost();
        if (host == null || tempAllowedHosts.contains(host)) return null;
        if (blocker.isBlockedHost(host)) {
            countBlocked();
            return empty();
        }
        return null;
    }

    private WebResourceResponse blockedPage(String url, String host) {
        String allow = ALLOW_SCHEME + "://open?u=" + Uri.encode(url);
        String html = Scripts.blockedPage(host, allow, getString(R.string.blocked_page_title),
                getString(R.string.blocked_page_body), getString(R.string.blocked_page_back),
                getString(R.string.blocked_page_open));
        return new WebResourceResponse("text/html", "utf-8",
                new ByteArrayInputStream(html.getBytes(StandardCharsets.UTF_8)));
    }

    private void injectPageScripts(WebView v) {
        if (!blockingOn()) return;
        String host = hostOf(v.getUrl());
        String js = Scripts.pageScript(hideElements, blockPopups, youtubeAds && Scripts.isYouTube(host));
        if (!js.isEmpty()) {
            try {
                v.evaluateJavascript(js, null);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean recentUserAction() {
        return SystemClock.uptimeMillis() - lastUserAction < 1500;
    }

    /** Main frame navigation started by the page. Returns true to cancel it. */
    private boolean handleNavigation(String url, boolean gesture) {
        if (url == null) return false;
        Uri u = Uri.parse(url);
        String scheme = u.getScheme() == null ? "" : u.getScheme().toLowerCase(Locale.ROOT);

        if (ALLOW_SCHEME.equals(scheme)) {
            String target = u.getQueryParameter("u");
            if (target != null) {
                String h = Uri.parse(target).getHost();
                if (h != null) tempAllowedHosts.add(h);
                web.loadUrl(target);
            }
            return true;
        }
        if (scheme.equals("http") || scheme.equals("https")) {
            String host = u.getHost();
            if (blockingOn() && host != null && !tempAllowedHosts.contains(host) && blocker.isBlockedHost(host)) {
                countBlocked();
                showPopupBar(getString(gesture ? R.string.blocked_link : R.string.blocked_redirect, host), url);
                return true;
            }
            return false;
        }
        if (scheme.equals("about") || scheme.equals("data") || scheme.equals("blob") || scheme.equals("javascript")) {
            return false;
        }
        // Another app (Play Store, WhatsApp, phone...). Ads love to do this without a tap.
        if (!gesture && blockPopups) {
            countBlocked();
            showPopupBar(getString(R.string.blocked_app), null);
            return true;
        }
        openExternal(url);
        return true;
    }

    private void openExternal(String url) {
        Intent intent;
        try {
            if (url.startsWith("intent:")) {
                intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME);
                intent.addCategory(Intent.CATEGORY_BROWSABLE);
                intent.setComponent(null);
                intent.setSelector(null);
            } else {
                intent = new Intent(Intent.ACTION_VIEW, Uri.parse(url));
            }
        } catch (Exception e) {
            toast(R.string.cannot_open);
            return;
        }
        final Intent target = intent;
        String fallback = intent.getStringExtra("browser_fallback_url");
        String scheme = Uri.parse(url).getScheme();
        boolean quiet = "tel".equals(scheme) || "mailto".equals(scheme) || "sms".equals(scheme)
                || "whatsapp".equals(scheme) || "geo".equals(scheme);
        Runnable start = () -> {
            try {
                startActivity(target);
            } catch (ActivityNotFoundException e) {
                if (fallback != null && (fallback.startsWith("http://") || fallback.startsWith("https://"))) {
                    load(fallback);
                } else {
                    toast(R.string.cannot_open);
                }
            } catch (Exception e) {
                toast(R.string.cannot_open);
            }
        };
        if (quiet) {
            start.run();
            return;
        }
        String label = intent.getPackage() != null ? intent.getPackage() : url;
        if (label.length() > 120) label = label.substring(0, 120) + "…";
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.external_title)
                .setMessage(getString(R.string.external_body, label))
                .setPositiveButton(R.string.open, (d, w) -> start.run())
                .setNegativeButton(R.string.cancel, null));
    }

    // ------------------------------------------------------------------ popups

    /**
     * A page asked for a new window. The URL is not known yet, so a throwaway
     * WebView receives it, reports the address, and is destroyed without loading.
     */
    private boolean onPopup(WebView view, boolean userGesture, Message resultMsg) {
        String linkUrl = null;
        WebView.HitTestResult hit = view.getHitTestResult();
        if (hit != null && hit.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            linkUrl = hit.getExtra();
        } else if (hit != null && hit.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Message href = ui.obtainMessage();
            view.requestFocusNodeHref(href);
            linkUrl = href.getData().getString("url");
        }
        final String clickedLink = linkUrl;
        final WebView temp = new WebView(this);
        final boolean[] done = {false};
        temp.getSettings().setJavaScriptEnabled(false);
        temp.setWebViewClient(new WebViewClient() {
            private void got(String url) {
                if (url == null || url.isEmpty() || url.equals("about:blank")) return;
                ui.post(() -> {
                    if (done[0]) return;
                    done[0] = true;
                    destroyLater(temp);
                    decidePopup(url, userGesture, clickedLink);
                });
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                if (r.isForMainFrame()) got(r.getUrl().toString());
                return empty();
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                got(r.getUrl().toString());
                return true;
            }

            @SuppressWarnings("deprecation")
            @Override
            public boolean shouldOverrideUrlLoading(WebView v, String url) {
                got(url);
                return true;
            }

            @Override
            public void onPageStarted(WebView v, String url, Bitmap favicon) {
                got(url);
            }
        });
        WebView.WebViewTransport transport = (WebView.WebViewTransport) resultMsg.obj;
        transport.setWebView(temp);
        resultMsg.sendToTarget();
        ui.postDelayed(() -> {
            if (!done[0]) {
                done[0] = true;
                destroyLater(temp);
                if (blockPopups && !userGesture) countBlocked();
            }
        }, 5000);
        return true;
    }

    private void destroyLater(WebView w) {
        ui.post(() -> {
            try {
                w.stopLoading();
                w.destroy();
            } catch (Throwable ignored) {
            }
        });
    }

    private void decidePopup(String url, boolean gesture, String clickedLink) {
        String host = hostOf(url);
        boolean allowAll = !blockPopups || pageAllowed;
        if (allowAll && (gesture || !blockPopups)) {
            load(url);
            return;
        }
        if (url.startsWith("intent:") || url.startsWith("market:")) {
            if (gesture && !blocker.isBlockedUrl(url)) {
                openExternal(url);
            } else {
                countBlocked();
                showPopupBar(getString(R.string.blocked_app), null);
            }
            return;
        }
        if (blockAds && host != null && blocker.isBlockedHost(host)) {
            countBlocked();
            showPopupBar(getString(R.string.blocked_popup, host), url);
            return;
        }
        if (gesture && clickedLink != null && Favorites.normalize(clickedLink).equals(Favorites.normalize(url))) {
            // A normal link with target="_blank": open it here, there are no tabs.
            load(url);
            return;
        }
        if (gesture && host != null && pageHost != null && BlockList.sameSite(host, pageHost)) {
            load(url);
            return;
        }
        countBlocked();
        showPopupBar(getString(R.string.blocked_popup, host == null ? url : host), url);
    }

    private final Runnable hidePopupBarRunnable = this::hidePopupBar;

    private void showPopupBar(String text, String url) {
        popupUrl = url;
        popupText.setText(text);
        popupOpen.setVisibility(url != null ? View.VISIBLE : View.GONE);
        popupBar.setVisibility(View.VISIBLE);
        ui.removeCallbacks(hidePopupBarRunnable);
        ui.postDelayed(hidePopupBarRunnable, 6000);
    }

    private void hidePopupBar() {
        ui.removeCallbacks(hidePopupBarRunnable);
        if (popupBar != null) popupBar.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------ fullscreen

    private void enterFullscreen(View view, WebChromeClient.CustomViewCallback cb) {
        if (customView != null) {
            cb.onCustomViewHidden();
            return;
        }
        customView = view;
        customCallback = cb;
        fullscreen.addView(view, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        fullscreen.setVisibility(View.VISIBLE);
        toolbar.setVisibility(View.GONE);
        progress.setVisibility(View.GONE);
        hidePopupBar();
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars(true);
        if (!isTv) {
            orientationBeforeFullscreen = getRequestedOrientation();
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE);
        }
    }

    private void exitFullscreen() {
        if (customView == null) return;
        fullscreen.removeView(customView);
        fullscreen.setVisibility(View.GONE);
        customView = null;
        try {
            if (customCallback != null) customCallback.onCustomViewHidden();
        } catch (Throwable ignored) {
        }
        customCallback = null;
        toolbar.setVisibility(View.VISIBLE);
        progress.setVisibility(View.INVISIBLE);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hideSystemBars(false);
        if (!isTv) setRequestedOrientation(orientationBeforeFullscreen);
    }

    @SuppressWarnings("deprecation")
    private void hideSystemBars(boolean on) {
        View decor = getWindow().getDecorView();
        if (on) {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        } else {
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_VISIBLE);
        }
    }

    // ------------------------------------------------------------- clients

    private class Client extends WebViewClient {

        @Override
        public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest request) {
            Uri u = request.getUrl();
            if (request.isForMainFrame()) {
                String host = u.getHost();
                String scheme = u.getScheme();
                if (host == null || !("http".equals(scheme) || "https".equals(scheme))) return null;
                pageHost = host;
                pageAllowed = blocker.isSiteAllowed(host);
                if (!blockingOn() || tempAllowedHosts.contains(host)) return null;
                if (blocker.isBlockedHost(host)) {
                    countBlocked();
                    return blockedPage(u.toString(), host);
                }
                return null;
            }
            return interceptSubresource(u);
        }

        @Override
        public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
            String url = request.getUrl().toString();
            if (!request.isForMainFrame()) {
                // Frames may load web pages, but never jump to other apps.
                String scheme = request.getUrl().getScheme();
                return !("http".equals(scheme) || "https".equals(scheme) || "about".equals(scheme)
                        || "data".equals(scheme) || "blob".equals(scheme));
            }
            boolean gesture = Build.VERSION.SDK_INT >= 24 ? request.hasGesture() : recentUserAction();
            return handleNavigation(url, gesture);
        }

        @SuppressWarnings("deprecation")
        @Override
        public boolean shouldOverrideUrlLoading(WebView view, String url) {
            return handleNavigation(url, recentUserAction());
        }

        @Override
        public void onPageStarted(WebView view, String url, Bitmap favicon) {
            saveBlockedCount();
            pageBlocked.set(0);
            pageBlockedSaved = 0;
            String host = hostOf(url);
            if (host != null) {
                pageHost = host;
                pageAllowed = blocker.isSiteAllowed(host);
            }
            lastIcon = null;
            lastIconHost = null;
            if (webVisible) progress.setVisibility(View.VISIBLE);
            showUrlInAddress();
            updateButtons();
        }

        @Override
        public void onPageCommitVisible(WebView view, String url) {
            injectPageScripts(view);
        }

        @Override
        public void onPageFinished(WebView view, String url) {
            injectPageScripts(view);
            progress.setVisibility(View.INVISIBLE);
            saveBlockedCount();
            showUrlInAddress();
            updateButtons();
        }

        @Override
        public void doUpdateVisitedHistory(WebView view, String url, boolean isReload) {
            showUrlInAddress();
            updateButtons();
        }

        @Override
        public void onReceivedSslError(WebView view, SslErrorHandler handler, SslError error) {
            Ui.show(new AlertDialog.Builder(MainActivity.this)
                    .setTitle(R.string.ssl_title)
                    .setMessage(getString(R.string.ssl_body, error.getUrl()))
                    .setPositiveButton(R.string.ssl_back, (d, w) -> handler.cancel())
                    .setNegativeButton(R.string.ssl_continue, (d, w) -> handler.proceed())
                    .setOnCancelListener(d -> handler.cancel()));
        }

        @Override
        public boolean onRenderProcessGone(WebView view, RenderProcessGoneDetail detail) {
            // Without this the whole app would close when a heavy page crashes.
            if (view == web) {
                ui.post(() -> {
                    exitFullscreen();
                    recreateWebView();
                    toast(R.string.page_crashed);
                });
            } else {
                try {
                    view.destroy();
                } catch (Throwable ignored) {
                }
            }
            return true;
        }
    }

    private class Chrome extends WebChromeClient {

        @Override
        public void onProgressChanged(WebView view, int newProgress) {
            progress.setProgress(newProgress);
            if (newProgress >= 100) {
                progress.setVisibility(View.INVISIBLE);
            } else if (webVisible && customView == null) {
                progress.setVisibility(View.VISIBLE);
            }
            if (newProgress >= 30) injectPageScripts(view);
        }

        @Override
        public void onReceivedTitle(WebView view, String title) {
            updateButtons();
        }

        @Override
        public void onReceivedIcon(WebView view, Bitmap icon) {
            String host = hostOf(view.getUrl());
            lastIcon = icon;
            lastIconHost = host;
            if (host != null && isFavoriteHost(host)) Favorites.saveIcon(MainActivity.this, host, icon);
        }

        @Override
        public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, Message resultMsg) {
            return onPopup(view, isUserGesture, resultMsg);
        }

        @Override
        public void onShowCustomView(View view, CustomViewCallback callback) {
            enterFullscreen(view, callback);
        }

        @Override
        public void onHideCustomView() {
            exitFullscreen();
        }

        @Override
        public void onGeolocationPermissionsShowPrompt(String origin, GeolocationPermissions.Callback callback) {
            callback.invoke(origin, false, false);
        }

        @Override
        public void onPermissionRequest(PermissionRequest request) {
            // Allow protected (DRM) video only. Camera and microphone stay off.
            for (String r : request.getResources()) {
                if (PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID.equals(r)) {
                    request.grant(new String[]{PermissionRequest.RESOURCE_PROTECTED_MEDIA_ID});
                    return;
                }
            }
            request.deny();
        }

        @Override
        public boolean onShowFileChooser(WebView view, ValueCallback<Uri[]> callback, FileChooserParams params) {
            if (fileCallback != null) fileCallback.onReceiveValue(null);
            fileCallback = callback;
            try {
                startActivityForResult(params.createIntent(), REQ_FILE);
                return true;
            } catch (Exception e) {
                fileCallback = null;
                toast(R.string.file_chooser_missing);
                return false;
            }
        }

        @Override
        public boolean onJsBeforeUnload(WebView view, String url, String message, JsResult result) {
            // No "are you sure you want to leave" traps.
            result.confirm();
            return true;
        }

        @Override
        public Bitmap getDefaultVideoPoster() {
            return Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888);
        }
    }

    // ----------------------------------------------------------- favorites

    private boolean isFavoriteHost(String host) {
        for (Favorites.Item it : Favorites.load(this)) {
            String h = hostOf(it.url);
            if (h != null && BlockList.siteOf(h).equals(BlockList.siteOf(host))) return true;
        }
        return false;
    }

    private void toggleFavorite() {
        if (!webVisible) return;
        String url = web.getUrl();
        if (url == null || url.startsWith("data:")) return;
        if (Favorites.contains(this, url)) {
            Favorites.remove(this, url);
            toast(R.string.favorite_removed);
        } else {
            String title = web.getTitle();
            if (title == null || title.trim().isEmpty() || title.startsWith("http")) title = Favorites.hostOf(url);
            if (title.length() > 40) title = title.substring(0, 40).trim();
            Favorites.add(this, title, url);
            String host = hostOf(url);
            if (lastIcon != null && host != null && host.equals(lastIconHost)) {
                Favorites.saveIcon(this, host, lastIcon);
            }
            toast(R.string.favorite_added);
        }
        updateButtons();
    }

    private void buildFavorites() {
        if (favGrid == null) return;
        favGrid.removeAllViews();
        homeStats.setText(getString(R.string.home_stats, AdBlocker.format(blocker.totalBlocked())));

        int widthDp = screenWidthDp();
        int gapDp = isTv ? 16 : 8;
        int minTileDp = isTv ? 140 : 80;
        int availDp = Math.max(minTileDp, widthDp - 32);
        int cols = Math.max(3, Math.min(8, (availDp + gapDp) / (minTileDp + gapDp)));
        int tileDp = Math.min(isTv ? 200 : 120, (availDp - (cols - 1) * gapDp) / cols);
        favGrid.setColumnCount(cols);

        List<Favorites.Item> items = Favorites.load(this);
        for (int i = 0; i < items.size(); i++) {
            Favorites.Item it = items.get(i);
            final int index = i;
            View tile = makeTile(it.title, hostOf(it.url), tileDp, gapDp, false);
            tile.setOnClickListener(v -> load(it.url));
            tile.setOnLongClickListener(v -> {
                showFavoriteOptions(index);
                return true;
            });
            favGrid.addView(tile);
        }
        View add = makeTile(getString(R.string.add_favorite_tile), null, tileDp, gapDp, true);
        add.setOnClickListener(v -> showFavoriteEditor(-1));
        favGrid.addView(add);
    }

    private View makeTile(String title, String host, int tileDp, int gapDp, boolean isAdd) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackgroundResource(R.drawable.tile_bg);
        tile.setFocusable(true);
        tile.setClickable(true);
        int pad = Ui.dp(this, 8);
        tile.setPadding(pad, pad, pad, pad);

        int iconDp = isTv ? 52 : 40;
        int iconPx = Ui.dp(this, iconDp);
        Bitmap icon = (!isAdd && host != null) ? Favorites.loadIcon(this, host) : null;
        if (isAdd) {
            ImageView iv = new ImageView(this);
            iv.setImageResource(R.drawable.ic_add);
            iv.setPadding(pad, pad, pad, pad);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(0xFF2A3245);
            iv.setBackground(circle);
            tile.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        } else if (icon != null && icon.getWidth() >= 16) {
            ImageView iv = new ImageView(this);
            iv.setImageBitmap(icon);
            iv.setScaleType(ImageView.ScaleType.FIT_CENTER);
            tile.addView(iv, new LinearLayout.LayoutParams(iconPx, iconPx));
        } else {
            TextView letter = new TextView(this);
            String t = title == null ? "?" : title.trim();
            letter.setText(t.isEmpty() ? "?" : t.substring(0, t.offsetByCodePoints(0, 1)).toUpperCase(Locale.ROOT));
            letter.setGravity(Gravity.CENTER);
            letter.setTextColor(Color.WHITE);
            letter.setTextSize(isTv ? 24 : 20);
            letter.setTypeface(letter.getTypeface(), android.graphics.Typeface.BOLD);
            GradientDrawable circle = new GradientDrawable();
            circle.setShape(GradientDrawable.OVAL);
            circle.setColor(colorFor(host == null ? t : host));
            letter.setBackground(circle);
            tile.addView(letter, new LinearLayout.LayoutParams(iconPx, iconPx));
        }

        TextView label = new TextView(this);
        label.setText(title);
        label.setTextColor(Ui.color(this, R.color.text));
        label.setTextSize(isTv ? 15 : 12);
        label.setGravity(Gravity.CENTER);
        label.setMaxLines(2);
        label.setEllipsize(TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = Ui.dp(this, 6);
        tile.addView(label, lp);

        GridLayout.LayoutParams glp = new GridLayout.LayoutParams();
        glp.width = Ui.dp(this, tileDp);
        glp.height = Ui.dp(this, isTv ? 130 : 96);
        int half = Ui.dp(this, gapDp / 2f);
        glp.setMargins(half, half, half, half);
        tile.setLayoutParams(glp);

        if (isTv) {
            tile.setOnFocusChangeListener((v, has) -> v.animate().scaleX(has ? 1.07f : 1f)
                    .scaleY(has ? 1.07f : 1f).setDuration(120).start());
        }
        return tile;
    }

    private static int colorFor(String key) {
        int[] colors = {0xFF2F6FE4, 0xFF1E9E5A, 0xFFE0533D, 0xFF8E57D9, 0xFFE0A030, 0xFF1AA3B8, 0xFFD94C8A};
        return colors[(key.hashCode() & 0x7fffffff) % colors.length];
    }

    private void showFavoriteOptions(int index) {
        List<Favorites.Item> items = Favorites.load(this);
        if (index < 0 || index >= items.size()) return;
        Favorites.Item it = items.get(index);
        String[] options = {getString(R.string.open), getString(R.string.edit), getString(R.string.move_earlier),
                getString(R.string.move_later), getString(R.string.delete)};
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(it.title)
                .setItems(options, (d, which) -> {
                    switch (which) {
                        case 0:
                            load(it.url);
                            break;
                        case 1:
                            showFavoriteEditor(index);
                            break;
                        case 2:
                        case 3: {
                            int to = which == 2 ? index - 1 : index + 1;
                            if (to >= 0 && to < items.size()) {
                                Collections.swap(items, index, to);
                                Favorites.save(this, items);
                                buildFavorites();
                                View v = favGrid.getChildAt(to);
                                if (v != null) v.requestFocus();
                            }
                            break;
                        }
                        default:
                            Ui.show(new AlertDialog.Builder(this)
                                    .setMessage(getString(R.string.favorite_delete_confirm, it.title))
                                    .setPositiveButton(R.string.delete, (d2, w2) -> {
                                        Favorites.remove(this, it.url);
                                        buildFavorites();
                                        updateButtons();
                                    })
                                    .setNegativeButton(R.string.cancel, null));
                            break;
                    }
                }));
    }

    /** index -1 adds a new favorite. */
    private void showFavoriteEditor(int index) {
        List<Favorites.Item> items = Favorites.load(this);
        Favorites.Item existing = index >= 0 && index < items.size() ? items.get(index) : null;

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        EditText name = field(getString(R.string.favorite_name), InputType.TYPE_CLASS_TEXT, false);
        EditText url = field(getString(R.string.favorite_url), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        if (existing != null) {
            name.setText(existing.title);
            url.setText(existing.url);
        } else if (webVisible && web.getUrl() != null && !web.getUrl().startsWith("data:")) {
            name.setText(web.getTitle());
            url.setText(web.getUrl());
        } else {
            url.setText("https://");
        }
        box.addView(name);
        box.addView(url);

        AlertDialog d = Ui.show(new AlertDialog.Builder(this)
                .setTitle(existing == null ? R.string.favorite_new : R.string.favorite_edit)
                .setView(box)
                .setPositiveButton(R.string.save, null)
                .setNegativeButton(R.string.cancel, null));
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> {
            String u = url.getText().toString().trim();
            if (!u.matches("(?i)^https?://.*")) u = "https://" + u;
            if (!URLUtil.isNetworkUrl(u) || hostOf(u) == null || !hostOf(u).contains(".")) {
                url.setError(getString(R.string.invalid_url));
                return;
            }
            String t = name.getText().toString().trim();
            if (t.isEmpty()) t = Favorites.hostOf(u);
            List<Favorites.Item> list = Favorites.load(this);
            if (existing != null && index < list.size()) {
                list.get(index).title = t;
                list.get(index).url = u;
            } else if (Favorites.indexOf(list, u) < 0) {
                list.add(new Favorites.Item(t, u));
            }
            Favorites.save(this, list);
            d.dismiss();
            buildFavorites();
            updateButtons();
        });
    }

    private EditText field(String hint, int type, boolean ltr) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setInputType(type);
        e.setSingleLine(true);
        e.setTextColor(Ui.color(this, R.color.text));
        e.setHintTextColor(Ui.color(this, R.color.text_muted));
        if (ltr) e.setTextDirection(View.TEXT_DIRECTION_LTR);
        return e;
    }

    // ------------------------------------------------------- dialogs, menu

    private void showAddressDialog() {
        EditText e = field(getString(R.string.address_hint), InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI, true);
        e.setImeOptions(EditorInfo.IME_ACTION_GO | EditorInfo.IME_FLAG_NO_EXTRACT_UI);
        String current = webVisible ? web.getUrl() : null;
        if (current != null && !current.startsWith("data:")) {
            e.setText(current);
            e.selectAll();
        }
        LinearLayout box = new LinearLayout(this);
        int pad = Ui.dp(this, 20);
        box.setPadding(pad, Ui.dp(this, 8), pad, 0);
        box.addView(e, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        AlertDialog d = Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.address_hint)
                .setView(box)
                .setPositiveButton(R.string.go, null)
                .setNegativeButton(R.string.cancel, null));
        Runnable go = () -> {
            String text = e.getText().toString();
            d.dismiss();
            navigate(text);
        };
        d.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v -> go.run());
        e.setOnEditorActionListener((v, actionId, event) -> {
            go.run();
            return true;
        });
        if (d.getWindow() != null) {
            d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
        }
        e.requestFocus();
    }

    private void showShieldDialog() {
        String host = webVisible ? hostOf(web.getUrl()) : null;
        StringBuilder msg = new StringBuilder();
        if (!blockAds) {
            msg.append(getString(R.string.shield_disabled_global)).append("\n\n");
        } else if (host != null) {
            msg.append(getString(pageAllowed ? R.string.shield_off_site : R.string.shield_on_site,
                    BlockList.siteOf(host))).append("\n");
            msg.append(getString(R.string.shield_page, AdBlocker.format(pageBlocked.get()))).append("\n");
        }
        msg.append(getString(R.string.shield_total, AdBlocker.format(blocker.totalBlocked() + pageBlocked.get() - pageBlockedSaved)))
                .append("\n");
        msg.append(getString(R.string.shield_list, AdBlocker.format(blocker.size())));

        AlertDialog.Builder b = new AlertDialog.Builder(this)
                .setTitle(R.string.shield)
                .setMessage(msg.toString())
                .setNeutralButton(R.string.settings, (d, w) -> openSettings())
                .setNegativeButton(R.string.close, null);
        if (blockAds && host != null) {
            boolean allowed = pageAllowed;
            b.setPositiveButton(allowed ? R.string.shield_enable_site : R.string.shield_disable_site, (d, w) -> {
                blocker.setSiteAllowed(host, !allowed);
                pageAllowed = !allowed;
                updateShield();
                web.reload();
            });
        }
        Ui.show(b);
    }

    private void showMenu(View anchor) {
        PopupMenu pm = new PopupMenu(this, anchor);
        Menu m = pm.getMenu();
        boolean narrow = btnForward.getVisibility() != View.VISIBLE;
        m.add(0, 1, 0, R.string.menu_favorites);
        if (narrow && web.canGoForward()) m.add(0, 2, 1, R.string.forward);
        String host = webVisible ? hostOf(web.getUrl()) : null;
        if (webVisible && host != null && blockAds) {
            m.add(0, 3, 2, pageAllowed ? R.string.shield_enable_site : R.string.shield_disable_site);
        }
        if (webVisible) {
            MenuItem desk = m.add(0, 4, 3, R.string.menu_desktop);
            desk.setCheckable(true);
            desk.setChecked(desktopMode);
            m.add(0, 5, 4, R.string.menu_zoom_in);
            m.add(0, 6, 5, R.string.menu_zoom_out);
            if (!isTv) m.add(0, 7, 6, R.string.menu_share);
        }
        m.add(0, 8, 7, R.string.settings);
        if (isTv) m.add(0, 9, 8, R.string.menu_exit);
        pm.setOnMenuItemClickListener(item -> {
            switch (item.getItemId()) {
                case 1:
                    showHome();
                    return true;
                case 2:
                    showWeb();
                    web.goForward();
                    return true;
                case 3:
                    if (host != null) {
                        blocker.setSiteAllowed(host, !pageAllowed);
                        pageAllowed = !pageAllowed;
                        updateShield();
                        web.reload();
                    }
                    return true;
                case 4:
                    desktopMode = !desktopMode;
                    web.getSettings().setUserAgentString(desktopMode ? desktopUa : mobileUa);
                    web.reload();
                    return true;
                case 5:
                    web.zoomBy(1.25f);
                    return true;
                case 6:
                    web.zoomBy(0.8f);
                    return true;
                case 7: {
                    Intent share = new Intent(Intent.ACTION_SEND);
                    share.setType("text/plain");
                    share.putExtra(Intent.EXTRA_TEXT, web.getUrl());
                    try {
                        startActivity(Intent.createChooser(share, getString(R.string.menu_share)));
                    } catch (Exception ignored) {
                    }
                    return true;
                }
                case 8:
                    openSettings();
                    return true;
                case 9:
                    finish();
                    return true;
                default:
                    return false;
            }
        });
        pm.show();
    }

    private void openSettings() {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        if (v != web) return;
        WebView.HitTestResult hit = web.getHitTestResult();
        if (hit == null) return;
        String link = null;
        if (hit.getType() == WebView.HitTestResult.SRC_ANCHOR_TYPE) {
            link = hit.getExtra();
        } else if (hit.getType() == WebView.HitTestResult.SRC_IMAGE_ANCHOR_TYPE) {
            Message href = ui.obtainMessage();
            web.requestFocusNodeHref(href);
            link = href.getData().getString("url");
        }
        if (link == null || !(link.startsWith("http://") || link.startsWith("https://"))) return;
        final String url = link;
        menu.setHeaderTitle(url.length() > 80 ? url.substring(0, 80) + "…" : url);
        menu.add(0, 1, 0, R.string.ctx_open).setOnMenuItemClickListener(i -> {
            load(url);
            return true;
        });
        menu.add(0, 2, 1, R.string.ctx_add_favorite).setOnMenuItemClickListener(i -> {
            Favorites.add(this, Favorites.hostOf(url), url);
            toast(R.string.favorite_added);
            return true;
        });
        if (!isTv) {
            menu.add(0, 3, 2, R.string.ctx_copy).setOnMenuItemClickListener(i -> {
                ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
                if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("url", url));
                toast(R.string.copied);
                return true;
            });
        }
    }

    // ------------------------------------------------------------- downloads

    private void onDownload(String url, String userAgent, String disposition, String mime, long length) {
        if (url == null || url.startsWith("blob:") || url.startsWith("data:")) {
            toast(R.string.download_unsupported);
            return;
        }
        String name = URLUtil.guessFileName(url, disposition, mime);
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.download_title)
                .setMessage(getString(R.string.download_body, name))
                .setPositiveButton(R.string.download_start, (d, w) -> startDownload(url, userAgent, mime, name))
                .setNegativeButton(R.string.cancel, null));
    }

    private void startDownload(String url, String userAgent, String mime, String name) {
        if (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT <= 28
                && checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
            pendingDownload = () -> startDownload(url, userAgent, mime, name);
            requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQ_STORAGE);
            return;
        }
        try {
            DownloadManager.Request r = new DownloadManager.Request(Uri.parse(url));
            if (mime != null) r.setMimeType(mime);
            r.addRequestHeader("User-Agent", userAgent);
            String cookies = CookieManager.getInstance().getCookie(url);
            if (cookies != null) r.addRequestHeader("Cookie", cookies);
            r.setTitle(name);
            r.setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED);
            try {
                r.setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, name);
            } catch (Exception e) {
                r.setDestinationInExternalFilesDir(this, Environment.DIRECTORY_DOWNLOADS, name);
            }
            DownloadManager dm = (DownloadManager) getSystemService(DOWNLOAD_SERVICE);
            if (dm == null) throw new IllegalStateException("No DownloadManager");
            dm.enqueue(r);
            toast(R.string.download_started);
        } catch (Exception e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
            } catch (Exception e2) {
                toast(R.string.download_failed);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQ_STORAGE) {
            Runnable r = pendingDownload;
            pendingDownload = null;
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                if (r != null) r.run();
            } else {
                toast(R.string.storage_denied);
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_FILE && fileCallback != null) {
            fileCallback.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
            fileCallback = null;
        }
    }

    // ------------------------------------------------------ remote control

    private boolean cursorActive() {
        return cursorEnabled && contentMode && (webVisible || customView != null);
    }

    private void setContentMode(boolean on) {
        contentMode = on && cursorEnabled;
        cursor.setActive(contentMode);
        if (contentMode) {
            if (customView == null && !web.hasFocus()) web.requestFocus();
            if (isTv && !Prefs.flag(this, Prefs.TV_HINT_SHOWN, false)) {
                Prefs.get(this).edit().putBoolean(Prefs.TV_HINT_SHOWN, true).apply();
                Ui.show(new AlertDialog.Builder(this)
                        .setTitle(R.string.tv_hint_title)
                        .setMessage(R.string.tv_hint_body)
                        .setPositiveButton(R.string.tv_hint_ok, null));
            }
        } else if (touchDown) {
            sendTouch(MotionEvent.ACTION_CANCEL, cursor.getCursorX(), cursor.getCursorY());
        }
    }

    private void focusToolbar(View target) {
        setContentMode(false);
        View v = target != null && target.getVisibility() == View.VISIBLE ? target : address;
        v.requestFocus();
    }

    private boolean toolbarHasFocus() {
        View f = getCurrentFocus();
        while (f != null) {
            if (f == toolbar) return true;
            if (!(f.getParent() instanceof View)) return false;
            f = (View) f.getParent();
        }
        return false;
    }

    private long touchDownTime;

    /** Sends a touch event at a pointer position (content coordinates) into the views below it. */
    private void sendTouch(int action, float x, float y) {
        long now = SystemClock.uptimeMillis();
        if (action == MotionEvent.ACTION_DOWN) touchDownTime = now;
        MotionEvent.PointerProperties[] props = {new MotionEvent.PointerProperties()};
        props[0].id = 0;
        props[0].toolType = MotionEvent.TOOL_TYPE_FINGER;
        MotionEvent.PointerCoords[] coords = {new MotionEvent.PointerCoords()};
        coords[0].x = x;
        coords[0].y = y;
        coords[0].pressure = 1f;
        coords[0].size = 1f;
        MotionEvent ev = MotionEvent.obtain(touchDownTime, now, action, 1, props, coords, 0, 0, 1f, 1f, 0, 0,
                InputDevice.SOURCE_TOUCHSCREEN, 0);
        lastUserAction = now;
        touchDown = action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE;
        cursor.setDragging(touchDown);
        content.dispatchTouchEvent(ev);
        ev.recycle();
    }

    /** Scroll requested by the pointer at the screen edge. Returns false when nothing can move. */
    private boolean scrollPage(float x, float y, int dx, int dy) {
        if (customView != null || !webVisible) return false;
        boolean nativeOk = (dy == 0 || web.canScrollVertically(dy)) && (dx == 0 || web.canScrollHorizontally(dx));
        if (nativeOk) {
            web.scrollBy(dx, dy);
            return true;
        }
        // The page itself is at its end; maybe an inner box (or a page that scrolls its body) can move.
        pendingJsDx += dx;
        pendingJsDy += dy;
        if (!jsScrollPending) {
            jsScrollPending = true;
            int sx = pendingJsDx, sy = pendingJsDy;
            pendingJsDx = 0;
            pendingJsDy = 0;
            web.evaluateJavascript(Scripts.scrollAt(x, y, web.getWidth(), sx, sy), value -> {
                jsScrollPending = false;
                lastJsScrollMoved = "1".equals(value);
            });
        }
        return lastJsScrollMoved;
    }

    private static int direction(int keyCode) {
        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_LEFT:
                return CursorView.LEFT;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
                return CursorView.RIGHT;
            case KeyEvent.KEYCODE_DPAD_UP:
                return CursorView.UP;
            case KeyEvent.KEYCODE_DPAD_DOWN:
                return CursorView.DOWN;
            default:
                return 0;
        }
    }

    private static boolean isClickKey(int keyCode) {
        return keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER
                || keyCode == KeyEvent.KEYCODE_NUMPAD_ENTER || keyCode == KeyEvent.KEYCODE_BUTTON_A;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent ev) {
        lastUserAction = SystemClock.uptimeMillis();
        return super.dispatchTouchEvent(ev);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent e) {
        int code = e.getKeyCode();
        boolean down = e.getAction() == KeyEvent.ACTION_DOWN;
        lastUserAction = SystemClock.uptimeMillis();

        // Keys sent by an on-screen keyboard belong to the text field, not to the pointer.
        boolean fromIme = (e.getFlags() & KeyEvent.FLAG_SOFT_KEYBOARD) != 0;

        if (cursorActive() && !fromIme) {
            int dir = direction(code);
            if (dir != 0) {
                if (down) {
                    if (e.getRepeatCount() == 0) cursor.press(dir);
                } else {
                    cursor.release(dir);
                }
                return true;
            }
            if (isClickKey(code)) {
                if (down && e.getRepeatCount() == 0) {
                    cursor.wake();
                    sendTouch(MotionEvent.ACTION_DOWN, cursor.getCursorX(), cursor.getCursorY());
                } else if (!down && touchDown) {
                    sendTouch(MotionEvent.ACTION_UP, cursor.getCursorX(), cursor.getCursorY());
                }
                return true;
            }
            if (code == KeyEvent.KEYCODE_MENU && customView == null) {
                if (down) focusToolbar(btnMenu);
                return true;
            }
        } else if (cursorEnabled && webVisible && customView == null && code == KeyEvent.KEYCODE_DPAD_DOWN
                && toolbarHasFocus()) {
            if (down) setContentMode(true);
            return true;
        }

        if (webVisible && down && e.getRepeatCount() == 0) {
            switch (code) {
                case KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE:
                case KeyEvent.KEYCODE_MEDIA_PLAY:
                case KeyEvent.KEYCODE_MEDIA_PAUSE:
                    web.evaluateJavascript(Scripts.TOGGLE_VIDEO, null);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_FAST_FORWARD:
                    web.evaluateJavascript(Scripts.seekVideo(10), null);
                    return true;
                case KeyEvent.KEYCODE_MEDIA_REWIND:
                    web.evaluateJavascript(Scripts.seekVideo(-10), null);
                    return true;
                case KeyEvent.KEYCODE_PAGE_DOWN:
                case KeyEvent.KEYCODE_CHANNEL_DOWN:
                    web.pageDown(false);
                    return true;
                case KeyEvent.KEYCODE_PAGE_UP:
                case KeyEvent.KEYCODE_CHANNEL_UP:
                    web.pageUp(false);
                    return true;
                default:
                    break;
            }
        }
        return super.dispatchKeyEvent(e);
    }

    @SuppressWarnings("deprecation")
    @Override
    public void onBackPressed() {
        if (customView != null) {
            exitFullscreen();
            return;
        }
        if (popupBar.getVisibility() == View.VISIBLE) hidePopupBar();
        if (!isTv && address.hasFocus()) {
            hideKeyboard(address);
            address.clearFocus();
            showUrlInAddress();
            return;
        }
        if (webVisible) {
            if (web.canGoBack()) {
                web.goBack();
            } else {
                showHome();
            }
            if (cursorEnabled && webVisible) setContentMode(true);
            return;
        }
        long now = SystemClock.uptimeMillis();
        if (now - lastBackPress < 2000) {
            finish();
            return;
        }
        lastBackPress = now;
        toast(R.string.press_back_again);
    }

    // ------------------------------------------------------------ lifecycle

    @Override
    protected void onResume() {
        super.onResume();
        readSettings();
        if (webVisible) {
            web.onResume();
            if (cursorEnabled && !toolbarHasFocus()) setContentMode(true);
        }
        web.resumeTimers();
        if (!webVisible) buildFavorites();
        if (Prefs.flag(this, "clear_history", false)) {
            Prefs.get(this).edit().putBoolean("clear_history", false).apply();
            web.clearHistory();
            web.clearCache(true);
            if (webVisible) showHome();
        }
        updateButtons();
    }

    @Override
    protected void onPause() {
        super.onPause();
        saveBlockedCount();
        if (touchDown) sendTouch(MotionEvent.ACTION_CANCEL, cursor.getCursorX(), cursor.getCursorY());
        cursor.releaseAll();
        exitFullscreen();
        web.onPause();
        web.pauseTimers();
        CookieManager.getInstance().flush();
    }

    @Override
    protected void onDestroy() {
        ui.removeCallbacksAndMessages(null);
        if (web != null) {
            webContainer.removeView(web);
            web.destroy();
        }
        super.onDestroy();
    }

    // --------------------------------------------------------------- helpers

    /** Current window width in dp, read from the display so it follows rotation. */
    @SuppressWarnings("deprecation")
    private int screenWidthDp() {
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        return Math.round(dm.widthPixels / dm.density);
    }

    private static String hostOf(String url) {
        if (url == null) return null;
        try {
            return Uri.parse(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void toast(int res) {
        Toast.makeText(this, res, Toast.LENGTH_SHORT).show();
    }
}
