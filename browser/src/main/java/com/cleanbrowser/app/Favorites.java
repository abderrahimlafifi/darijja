package com.cleanbrowser.app;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Saved favorite sites, stored as JSON in SharedPreferences, plus their small icons. */
public final class Favorites {

    public static final class Item {
        public String title;
        public String url;

        public Item(String title, String url) {
            this.title = title;
            this.url = url;
        }
    }

    private static final String FILE = "favorites";
    private static final String KEY_ITEMS = "items";
    private static final String KEY_INIT = "initialized";

    private Favorites() {
    }

    private static SharedPreferences prefs(Context c) {
        return c.getApplicationContext().getSharedPreferences(FILE, Context.MODE_PRIVATE);
    }

    public static List<Item> load(Context c) {
        SharedPreferences p = prefs(c);
        if (!p.getBoolean(KEY_INIT, false)) {
            List<Item> defaults = new ArrayList<>();
            defaults.add(new Item("Google", "https://www.google.com/"));
            defaults.add(new Item("YouTube", "https://m.youtube.com/"));
            defaults.add(new Item(c.getString(R.string.default_wikipedia), "https://ar.wikipedia.org/"));
            save(c, defaults);
            p.edit().putBoolean(KEY_INIT, true).apply();
            return defaults;
        }
        List<Item> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p.getString(KEY_ITEMS, "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                String url = o.optString("u", "");
                if (url.isEmpty()) continue;
                out.add(new Item(o.optString("t", url), url));
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    public static void save(Context c, List<Item> items) {
        JSONArray a = new JSONArray();
        try {
            for (Item it : items) {
                JSONObject o = new JSONObject();
                o.put("t", it.title);
                o.put("u", it.url);
                a.put(o);
            }
        } catch (JSONException ignored) {
        }
        prefs(c).edit().putString(KEY_ITEMS, a.toString()).putBoolean(KEY_INIT, true).apply();
    }

    public static int indexOf(List<Item> items, String url) {
        String key = normalize(url);
        if (key.isEmpty()) return -1;
        for (int i = 0; i < items.size(); i++) {
            if (normalize(items.get(i).url).equals(key)) return i;
        }
        return -1;
    }

    public static boolean contains(Context c, String url) {
        return indexOf(load(c), url) >= 0;
    }

    public static void add(Context c, String title, String url) {
        List<Item> items = load(c);
        if (indexOf(items, url) >= 0) return;
        items.add(new Item(title == null || title.trim().isEmpty() ? hostOf(url) : title.trim(), url));
        save(c, items);
    }

    public static void remove(Context c, String url) {
        List<Item> items = load(c);
        int i = indexOf(items, url);
        if (i >= 0) {
            items.remove(i);
            save(c, items);
        }
    }

    /** Same page regardless of http/https, "www.", a trailing slash or a #fragment. */
    static String normalize(String url) {
        if (url == null) return "";
        String u = url.trim();
        int hash = u.indexOf('#');
        if (hash >= 0) u = u.substring(0, hash);
        u = u.replaceFirst("(?i)^https?://", "").replaceFirst("(?i)^www\\.", "");
        while (u.endsWith("/")) u = u.substring(0, u.length() - 1);
        int slash = u.indexOf('/');
        if (slash < 0) return u.toLowerCase(Locale.ROOT);
        return u.substring(0, slash).toLowerCase(Locale.ROOT) + u.substring(slash);
    }

    public static String hostOf(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return url;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return url;
        }
    }

    // ---- Site icons ----

    private static File iconFile(Context c, String host) {
        File dir = new File(c.getFilesDir(), "icons");
        //noinspection ResultOfMethodCallIgnored
        dir.mkdirs();
        String name = host.toLowerCase(Locale.ROOT).replaceFirst("^www\\.", "").replaceAll("[^a-z0-9.-]", "_");
        return new File(dir, name + ".png");
    }

    public static void saveIcon(Context c, String host, Bitmap icon) {
        if (host == null || icon == null) return;
        try (FileOutputStream out = new FileOutputStream(iconFile(c, host))) {
            icon.compress(Bitmap.CompressFormat.PNG, 100, out);
        } catch (Exception ignored) {
        }
    }

    public static Bitmap loadIcon(Context c, String host) {
        if (host == null) return null;
        File f = iconFile(c, host);
        if (!f.isFile()) return null;
        try {
            return BitmapFactory.decodeFile(f.getAbsolutePath());
        } catch (Exception e) {
            return null;
        }
    }

    public static boolean hasIcon(Context c, String host) {
        return host != null && iconFile(c, host).isFile();
    }
}
