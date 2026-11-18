package com.talktranslate.app;

import android.text.Html;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Free translation with no API key.
 * First tries the public Google endpoint, then falls back to MyMemory.
 * All methods are blocking: call them from a background thread only.
 */
final class Translator {

    private static final int TIMEOUT_MS = 10_000;

    private Translator() {
    }

    /** Returns the translated text, or throws if both services fail. */
    static String translate(String text, String src, String dst) throws Exception {
        Exception last;
        try {
            String r = google(text, src, dst);
            if (!r.isEmpty()) return r;
            last = new IOException("Google returned an empty result");
        } catch (Exception e) {
            last = e;
        }
        try {
            String r = myMemory(text, src, dst);
            if (!r.isEmpty()) return r;
            last = new IOException("MyMemory returned an empty result");
        } catch (Exception e) {
            last = e;
        }
        throw last;
    }

    private static String google(String text, String src, String dst) throws Exception {
        String url = "https://translate.googleapis.com/translate_a/single?client=gtx"
                + "&sl=" + enc(src) + "&tl=" + enc(dst) + "&dt=t&q=" + enc(text);
        JSONArray root = new JSONArray(httpGet(url));
        JSONArray parts = root.getJSONArray(0);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < parts.length(); i++) {
            JSONArray p = parts.optJSONArray(i);
            if (p != null && !p.isNull(0)) sb.append(p.getString(0));
        }
        return sb.toString().trim();
    }

    private static String myMemory(String text, String src, String dst) throws Exception {
        String url = "https://api.mymemory.translated.net/get?q=" + enc(text)
                + "&langpair=" + enc(src + "|" + dst);
        JSONObject o = new JSONObject(httpGet(url));
        int status = o.optInt("responseStatus", 0);
        JSONObject data = o.optJSONObject("responseData");
        String t = data == null ? "" : data.optString("translatedText", "");
        if (status != 200 || t.toUpperCase().contains("MYMEMORY WARNING")) {
            throw new IOException("MyMemory status " + status);
        }
        // MyMemory sometimes returns HTML entities such as &#39;
        return Html.fromHtml(t, Html.FROM_HTML_MODE_LEGACY).toString().trim();
    }

    private static String httpGet(String urlStr) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(urlStr).openConnection();
        c.setConnectTimeout(TIMEOUT_MS);
        c.setReadTimeout(TIMEOUT_MS);
        c.setRequestProperty("User-Agent", "Mozilla/5.0 (Linux; Android 11) TalkTranslate/1.0");
        c.setRequestProperty("Accept", "application/json,*/*");
        try {
            int code = c.getResponseCode();
            if (code != HttpURLConnection.HTTP_OK) throw new IOException("HTTP " + code);
            try (InputStream in = c.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return new String(out.toByteArray(), StandardCharsets.UTF_8);
            }
        } finally {
            c.disconnect();
        }
    }

    private static String enc(String s) throws IOException {
        return URLEncoder.encode(s, "UTF-8");
    }
}
