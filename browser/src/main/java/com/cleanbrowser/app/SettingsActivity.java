package com.cleanbrowser.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebStorage;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class SettingsActivity extends Activity {

    private LinearLayout list;
    private AdBlocker blocker;
    private SharedPreferences prefs;
    private boolean isTv;

    private TextView updateSummary, allowedSummary, searchSummary, textSummary, speedSummary, totalText;
    private View firstRow;

    @Override
    protected void attachBaseContext(Context base) {
        super.attachBaseContext(Ui.arabic(base));
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        blocker = AdBlocker.get(this);
        prefs = Prefs.get(this);
        isTv = Prefs.isTv(this);

        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(Ui.color(this, R.color.bg));
        scroll.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        scroll.setFillViewport(true);

        FrameLayout center = new FrameLayout(this);
        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        int pad = Ui.dp(this, 16);
        list.setPadding(pad, pad, pad, Ui.dp(this, 32));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(
                Math.min(getResources().getDisplayMetrics().widthPixels, Ui.dp(this, 760)),
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL);
        center.addView(list, lp);
        scroll.addView(center);

        TextView title = new TextView(this);
        title.setText(R.string.settings);
        title.setTextColor(Ui.color(this, R.color.text));
        title.setTextSize(24);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        title.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        list.addView(title);

        header(R.string.section_ads);
        firstRow = toggle(Prefs.BLOCK_ADS, R.string.set_block_ads, R.string.set_block_ads_sum, true);
        toggle(Prefs.BLOCK_POPUPS, R.string.set_block_popups, R.string.set_block_popups_sum, true);
        toggle(Prefs.HIDE_ELEMENTS, R.string.set_hide, R.string.set_hide_sum, true);
        toggle(Prefs.YOUTUBE_ADS, R.string.set_youtube, R.string.set_youtube_sum, true);
        updateSummary = action(R.string.set_update, v -> runUpdate());
        toggle(Prefs.SOURCE_STEVENBLACK, R.string.set_src_sb, R.string.set_sources_sum, true);
        toggle(Prefs.SOURCE_ADGUARD, R.string.set_src_ag, R.string.set_sources_sum, true);
        allowedSummary = action(R.string.set_allowed, v -> showAllowedSites());
        totalText = note("");

        header(R.string.section_browsing);
        searchSummary = action(R.string.set_search, v -> chooseSearch());
        textSummary = action(R.string.set_text, v -> chooseTextSize());
        toggle(Prefs.DESKTOP_MODE, R.string.set_desktop, R.string.set_desktop_sum, false);
        toggle(Prefs.CURSOR_MODE, R.string.set_cursor, R.string.set_cursor_sum, isTv);
        speedSummary = action(R.string.set_cursor_speed, v -> chooseSpeed());
        TextView clear = action(R.string.set_clear, v -> confirmClear());
        clear.setText(R.string.set_clear_sum);

        header(R.string.section_about);
        String version = "";
        try {
            version = getPackageManager().getPackageInfo(getPackageName(), 0).versionName;
        } catch (Exception ignored) {
        }
        note(getString(R.string.about_text, version));

        setContentView(scroll);
        refresh();
        if (isTv) firstRow.requestFocus();
    }

    private void refresh() {
        long updated = blocker.lastUpdate();
        String when = updated == 0 ? getString(R.string.set_update_never)
                : new SimpleDateFormat("yyyy/MM/dd HH:mm", Locale.US).format(new Date(updated));
        if (blocker.isUpdating()) {
            updateSummary.setText(R.string.set_updating);
        } else {
            updateSummary.setText(getString(R.string.set_update_sum, AdBlocker.format(blocker.size()), when));
        }
        int allowed = blocker.allowedSites().size();
        allowedSummary.setText(allowed == 0 ? getString(R.string.set_allowed_none)
                : getString(R.string.set_allowed_sum, AdBlocker.format(allowed)));
        totalText.setText(getString(R.string.set_total, AdBlocker.format(blocker.totalBlocked())));
        searchSummary.setText(Prefs.SEARCH_NAMES[Prefs.searchEngine(this)]);
        textSummary.setText(String.format(Locale.US, "%d%%", Prefs.textZoom(this)));
        speedSummary.setText(speedName(Prefs.cursorSpeed(this)));
    }

    private String speedName(int level) {
        return getString(level <= 1 ? R.string.speed_slow : level >= 3 ? R.string.speed_fast : R.string.speed_normal);
    }

    // ---------------------------------------------------------------- rows

    private void header(int text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Ui.color(this, R.color.accent_light));
        t.setTextSize(15);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 20), Ui.dp(this, 4), Ui.dp(this, 8));
        list.addView(t);
    }

    private TextView note(String text) {
        TextView t = new TextView(this);
        t.setText(text);
        t.setTextColor(Ui.color(this, R.color.text_muted));
        t.setTextSize(13);
        t.setLineSpacing(0, 1.2f);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8));
        list.addView(t);
        return t;
    }

    private LinearLayout row() {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackgroundResource(R.drawable.row_bg);
        r.setFocusable(true);
        r.setClickable(true);
        int p = Ui.dp(this, 14);
        r.setPadding(p, p, p, p);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = Ui.dp(this, 6);
        list.addView(r, lp);
        return r;
    }

    /** Adds title and summary texts to a row, returns the summary. */
    private TextView texts(LinearLayout r, int title) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Ui.color(this, R.color.text));
        t.setTextSize(isTv ? 18 : 16);
        TextView s = new TextView(this);
        s.setTextColor(Ui.color(this, R.color.text_muted));
        s.setTextSize(isTv ? 14 : 13);
        col.addView(t);
        col.addView(s);
        r.addView(col, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));
        return s;
    }

    private View toggle(String key, int title, int summary, boolean def) {
        LinearLayout r = row();
        TextView s = texts(r, title);
        s.setText(summary);
        Switch sw = new Switch(this);
        sw.setFocusable(false);
        sw.setClickable(false);
        sw.setChecked(prefs.getBoolean(key, def));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.setMarginStart(Ui.dp(this, 12));
        r.addView(sw, lp);
        r.setOnClickListener(v -> {
            boolean now = !sw.isChecked();
            sw.setChecked(now);
            prefs.edit().putBoolean(key, now).apply();
        });
        return r;
    }

    private TextView action(int title, View.OnClickListener click) {
        LinearLayout r = row();
        TextView s = texts(r, title);
        r.setOnClickListener(click);
        return s;
    }

    // ------------------------------------------------------------- actions

    private void runUpdate() {
        updateSummary.setText(R.string.set_updating);
        blocker.update((count, error) -> {
            if (isFinishing()) return;
            if (count >= 0) {
                Toast.makeText(this, getString(R.string.set_update_done, AdBlocker.format(count)), Toast.LENGTH_LONG).show();
            } else if (!getString(R.string.update_running).equals(error)) {
                Toast.makeText(this, R.string.set_update_failed, Toast.LENGTH_LONG).show();
            }
            refresh();
        });
    }

    private void showAllowedSites() {
        Set<String> set = blocker.allowedSites();
        if (set.isEmpty()) {
            Toast.makeText(this, R.string.set_allowed_none, Toast.LENGTH_SHORT).show();
            return;
        }
        List<String> sites = new ArrayList<>(set);
        java.util.Collections.sort(sites);
        String[] items = sites.toArray(new String[0]);
        boolean[] checked = new boolean[items.length];
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.set_allowed_pick)
                .setMultiChoiceItems(items, checked, (d, which, isChecked) -> checked[which] = isChecked)
                .setPositiveButton(R.string.set_allowed_restore, (d, w) -> {
                    for (int i = 0; i < items.length; i++) {
                        if (checked[i]) blocker.setSiteAllowed(items[i], false);
                    }
                    refresh();
                })
                .setNegativeButton(R.string.cancel, null));
    }

    private void chooseSearch() {
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.set_search)
                .setSingleChoiceItems(Prefs.SEARCH_NAMES, Prefs.searchEngine(this), (d, which) -> {
                    prefs.edit().putInt(Prefs.SEARCH_ENGINE, which).apply();
                    d.dismiss();
                    refresh();
                }));
    }

    private void chooseTextSize() {
        String[] names = new String[Prefs.TEXT_ZOOMS.length];
        int current = 0;
        for (int i = 0; i < names.length; i++) {
            names[i] = String.format(Locale.US, "%d%%", Prefs.TEXT_ZOOMS[i]);
            if (Prefs.TEXT_ZOOMS[i] == Prefs.textZoom(this)) current = i;
        }
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.set_text)
                .setSingleChoiceItems(names, current, (d, which) -> {
                    prefs.edit().putInt(Prefs.TEXT_ZOOM, Prefs.TEXT_ZOOMS[which]).apply();
                    d.dismiss();
                    refresh();
                }));
    }

    private void chooseSpeed() {
        String[] names = {speedName(1), speedName(2), speedName(3)};
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.set_cursor_speed)
                .setSingleChoiceItems(names, Prefs.cursorSpeed(this) - 1, (d, which) -> {
                    prefs.edit().putInt(Prefs.CURSOR_SPEED, which + 1).apply();
                    d.dismiss();
                    refresh();
                }));
    }

    private void confirmClear() {
        Ui.show(new AlertDialog.Builder(this)
                .setTitle(R.string.set_clear)
                .setMessage(R.string.set_clear_confirm)
                .setPositiveButton(R.string.delete, (d, w) -> {
                    CookieManager.getInstance().removeAllCookies(null);
                    CookieManager.getInstance().flush();
                    WebStorage.getInstance().deleteAllData();
                    // The open page's history and cache are cleared when the browser comes back.
                    prefs.edit().putBoolean("clear_history", true).apply();
                    Toast.makeText(this, R.string.set_cleared, Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.cancel, null));
    }
}
