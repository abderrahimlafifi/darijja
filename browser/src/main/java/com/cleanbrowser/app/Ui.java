package com.cleanbrowser.app;

import android.app.AlertDialog;
import android.content.Context;
import android.content.res.Configuration;
import android.util.TypedValue;
import android.view.View;
import android.view.Window;

import java.util.Locale;

/** Small UI helpers shared by the activities. */
final class Ui {

    private Ui() {
    }

    /**
     * The interface is Arabic only, so lay it out right to left even when the
     * phone or TV itself is set to French or English.
     */
    static Context arabic(Context base) {
        Configuration c = new Configuration(base.getResources().getConfiguration());
        Locale ar = new Locale("ar");
        c.setLocale(ar);
        c.setLayoutDirection(ar);
        return base.createConfigurationContext(c);
    }

    static AlertDialog show(AlertDialog.Builder b) {
        AlertDialog d = b.create();
        d.show();
        Window w = d.getWindow();
        if (w != null) w.getDecorView().setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        return d;
    }

    /** Context.getColor only exists from Android 6. */
    @SuppressWarnings("deprecation")
    static int color(Context c, int res) {
        return c.getResources().getColor(res);
    }

    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics()));
    }
}
