package com.cleanbrowser.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Loads the block lists, answers "should this request be blocked" from the
 * WebView network thread, and downloads fresh lists in the background.
 */
public final class AdBlocker {

    private static final String TAG = "AdBlocker";

    public static final String URL_STEVENBLACK = "https://raw.githubusercontent.com/StevenBlack/hosts/master/hosts";
    public static final String URL_ADGUARD = "https://adguardteam.github.io/AdGuardSDNSFilter/Filters/filter.txt";

    private static final String ASSET_LIST = "blocklist.txt";
    private static final String ASSET_EXTRA = "extra-blocklist.txt";
    private static final String DOWNLOADED_LIST = "blocklist.txt";

    private static final long UPDATE_EVERY_MS = 7L * 24 * 60 * 60 * 1000;

    private static AdBlocker instance;

    private final Context app;
    private final SharedPreferences prefs;
    private final CountDownLatch loaded = new CountDownLatch(1);
    private final AtomicBoolean updating = new AtomicBoolean(false);
    private final Handler main = new Handler(Looper.getMainLooper());

    private volatile BlockList list = new BlockList();
    private volatile Set<String> allowedSites = Collections.emptySet();

    public interface UpdateCallback {
        /** Called on the main thread. count is the new list size, or -1 on failure. */
        void done(int count, String error);
    }

    public static synchronized AdBlocker get(Context c) {
        if (instance == null) instance = new AdBlocker(c.getApplicationContext());
        return instance;
    }

    private AdBlocker(Context app) {
        this.app = app;
        this.prefs = Prefs.get(app);
        allowedSites = new HashSet<>(prefs.getStringSet(Prefs.ALLOWED_SITES, Collections.<String>emptySet()));
    }

    /** Loads the saved or bundled list on a background thread. */
    public void loadAsync() {
        Thread t = new Thread(() -> {
            try {
                list = loadLocal();
                Log.i(TAG, "Block list ready: " + list.size() + " domains");
            } catch (Throwable e) {
                Log.e(TAG, "Could not load block list", e);
            } finally {
                loaded.countDown();
            }
        }, "blocklist-load");
        t.setPriority(Thread.NORM_PRIORITY);
        t.start();
    }

    private BlockList loadLocal() throws IOException {
        BlockList l = new BlockList(160_000);
        File saved = new File(app.getFilesDir(), DOWNLOADED_LIST);
        boolean fromFile = false;
        if (saved.isFile() && saved.length() > 1000) {
            try (BufferedReader r = reader(new FileInputStream(saved))) {
                l.parse(r);
                fromFile = l.size() > 100;
            } catch (IOException e) {
                Log.w(TAG, "Saved list unreadable, using bundled one", e);
            }
        }
        if (!fromFile) {
            l = new BlockList(100_000);
            try (BufferedReader r = reader(app.getAssets().open(ASSET_LIST))) {
                l.parse(r);
            }
        }
        try (BufferedReader r = reader(app.getAssets().open(ASSET_EXTRA))) {
            l.parse(r);
        }
        prefs.edit().putInt(Prefs.LIST_SIZE, l.size()).apply();
        return l;
    }

    private static BufferedReader reader(InputStream in) {
        return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8), 64 * 1024);
    }

    public int size() {
        return list.size();
    }

    /** Domain check, waits up to two seconds for the first load to finish. */
    public boolean isBlockedHost(String host) {
        if (host == null) return false;
        if (loaded.getCount() > 0) {
            try {
                loaded.await(2, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        return list.isBlocked(host);
    }

    public boolean isBlockedUrl(String url) {
        if (url == null) return false;
        try {
            return isBlockedHost(Uri.parse(url).getHost());
        } catch (Exception e) {
            return false;
        }
    }

    // ---- Sites where the user turned blocking off ----

    public boolean isSiteAllowed(String host) {
        if (host == null) return false;
        return allowedSites.contains(BlockList.siteOf(host));
    }

    public void setSiteAllowed(String host, boolean allowed) {
        if (host == null) return;
        Set<String> s = new HashSet<>(allowedSites);
        String site = BlockList.siteOf(host);
        if (allowed) s.add(site); else s.remove(site);
        allowedSites = s;
        prefs.edit().putStringSet(Prefs.ALLOWED_SITES, new HashSet<>(s)).apply();
    }

    public Set<String> allowedSites() {
        return new HashSet<>(allowedSites);
    }

    // ---- Statistics ----

    public void addToTotal(int n) {
        if (n <= 0) return;
        prefs.edit().putLong(Prefs.TOTAL_BLOCKED, totalBlocked() + n).apply();
    }

    public long totalBlocked() {
        return prefs.getLong(Prefs.TOTAL_BLOCKED, 0);
    }

    // ---- Updating from the internet ----

    public long lastUpdate() {
        return prefs.getLong(Prefs.LIST_UPDATED, 0);
    }

    public boolean isUpdating() {
        return updating.get();
    }

    /** Downloads fresh lists if the last update is older than a week. */
    public void updateIfDue() {
        if (System.currentTimeMillis() - lastUpdate() < UPDATE_EVERY_MS) return;
        main.postDelayed(() -> update(null), 15_000);
    }

    public void update(UpdateCallback cb) {
        if (!updating.compareAndSet(false, true)) {
            if (cb != null) main.post(() -> cb.done(-1, app.getString(R.string.update_running)));
            return;
        }
        new Thread(() -> {
            int count = -1;
            String error = null;
            try {
                count = download();
            } catch (Throwable e) {
                Log.w(TAG, "Update failed", e);
                error = e.getMessage();
            } finally {
                updating.set(false);
            }
            final int c = count;
            final String err = error;
            if (cb != null) main.post(() -> cb.done(c, err));
        }, "blocklist-update").start();
    }

    private int download() throws IOException {
        boolean sb = Prefs.flag(app, Prefs.SOURCE_STEVENBLACK, true);
        boolean ag = Prefs.flag(app, Prefs.SOURCE_ADGUARD, true);
        if (!sb && !ag) sb = true;

        BlockList fresh = new BlockList(160_000);
        int ok = 0;
        IOException last = null;
        if (sb) {
            try {
                fetchInto(URL_STEVENBLACK, fresh);
                ok++;
            } catch (IOException e) {
                last = e;
            }
        }
        if (ag) {
            try {
                fetchInto(URL_ADGUARD, fresh);
                ok++;
            } catch (IOException e) {
                last = e;
            }
        }
        if (ok == 0 || fresh.size() < 1000) {
            throw last != null ? last : new IOException("Empty list");
        }

        // Save without the extras, they are re-added from the APK on every load.
        File dir = app.getFilesDir();
        File tmp = new File(dir, DOWNLOADED_LIST + ".tmp");
        try (BufferedWriter w = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8), 64 * 1024)) {
            fresh.write(w);
        }
        File target = new File(dir, DOWNLOADED_LIST);
        if (!tmp.renameTo(target)) {
            throw new IOException("Could not save the list");
        }

        try (BufferedReader r = reader(app.getAssets().open(ASSET_EXTRA))) {
            fresh.parse(r);
        }
        list = fresh;
        prefs.edit()
                .putLong(Prefs.LIST_UPDATED, System.currentTimeMillis())
                .putInt(Prefs.LIST_SIZE, fresh.size())
                .apply();
        return fresh.size();
    }

    private static void fetchInto(String url, BlockList into) throws IOException {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setConnectTimeout(15_000);
        con.setReadTimeout(30_000);
        con.setRequestProperty("User-Agent", "CleanBrowser/1.0 (Android)");
        con.setInstanceFollowRedirects(true);
        try {
            int code = con.getResponseCode();
            if (code != 200) throw new IOException("HTTP " + code + " " + new URL(url).getHost());
            try (BufferedReader r = reader(con.getInputStream())) {
                int before = into.size();
                into.parse(r);
                if (into.size() - before < 100) {
                    throw new IOException("List too small: " + new URL(url).getHost());
                }
            }
        } finally {
            con.disconnect();
        }
    }

    /** "1,234,567" style formatting for counts shown to the user. */
    public static String format(long n) {
        return String.format(Locale.US, "%,d", n);
    }
}
