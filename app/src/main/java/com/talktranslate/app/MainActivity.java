package com.talktranslate.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognizerIntent;
import android.speech.tts.TextToSpeech;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Talk Translate: two way voice translator between Arabic and English, French or Spanish.
 * The whole UI is built in code so the app stays a single small Activity.
 */
public class MainActivity extends Activity {

    // Colors
    private static final int C_BG = 0xFF0F1115;
    private static final int C_SURFACE = 0xFF1A1D24;
    private static final int C_SURFACE_ON = 0xFF2A2F3A;
    private static final int C_TEXT = 0xFFECEFF4;
    private static final int C_MUTED = 0xFF9AA3B2;
    private static final int C_GREEN = 0xFF1E9E5A;
    private static final int C_BLUE = 0xFF2F6FE4;
    private static final int C_GREEN_BUBBLE = 0xFF173F2B;
    private static final int C_BLUE_BUBBLE = 0xFF1B2F55;
    private static final int C_ERROR_BG = 0xFF4A1C1F;
    private static final int C_ERROR_TEXT = 0xFFFFB4B4;

    // Target languages
    private static final String[] TARGET_CODES = {"en", "fr", "es"};
    private static final String[] TARGET_NAMES = {"English", "Français", "Español"};
    private static final String[] TARGET_SPEECH_TAGS = {"en-US", "fr-FR", "es-ES"};
    private static final String[] TARGET_BUTTON = {"Speak English", "Parlez français", "Hable español"};
    private static final String[] TARGET_PROMPT = {"Speak now", "Parlez maintenant", "Hable ahora"};
    private static final Locale[] TARGET_LOCALES = {Locale.US, Locale.FRANCE, new Locale("es", "ES")};

    // Arabic dialects for speech recognition
    private static final String[] DIALECT_TAGS = {"ar-MA", "ar-SA", "ar-EG"};
    private static final String[] DIALECT_NAMES = {"الدارجة المغربية", "الفصحى", "المصرية"};

    private static final String AR = "ar";
    private static final String GOOGLE_TTS = "com.google.android.tts";
    private static final String GOOGLE_APP = "com.google.android.googlequicksearchbox";

    private static final int REQ_ARABIC = 101;
    private static final int REQ_OTHER = 102;

    // Arabic messages
    private static final String MSG_NO_INTERNET = "لا يوجد اتصال بالإنترنت. تحقّق من الواي فاي أو بيانات الهاتف ثم حاول مرة أخرى.";
    private static final String MSG_TRANSLATE_FAILED = "تعذّرت الترجمة. حاول مرة أخرى بعد قليل.";
    private static final String MSG_NO_SPEECH = "لم أفهم الكلام. تكلّم بوضوح وحاول مرة أخرى.";
    private static final String MSG_MIC = "مشكلة في الميكروفون. تأكّد أن تطبيق Google لديه إذن الميكروفون.";
    private static final String MSG_RECOGNIZER = "خطأ في خدمة التعرّف على الصوت. حاول مرة أخرى.";
    private static final String MSG_GOOGLE_MISSING = "تطبيق Google غير مثبت أو معطّل على هاتفك.\n\n"
            + "التعرّف على الصوت يحتاج تطبيق Google. ثبّته أو فعّله من متجر Play ثم أعد المحاولة.";
    private static final String MSG_TTS_ENGINE = "محرك تحويل النص إلى كلام غير متاح.\n\n"
            + "ثبّت تطبيق \"Speech Services by Google\" من متجر Play، ثم اختره من:\n"
            + "الإعدادات > تحويل النص إلى كلام > Google";

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private final ArrayList<Msg> messages = new ArrayList<>();

    private SharedPreferences prefs;
    private int targetIdx;
    private int dialectIdx;
    private boolean busy;

    private TextToSpeech tts;
    private boolean ttsReady;
    private String pendingText;
    private String pendingLang;

    private LinearLayout chatList;
    private ScrollView chatScroll;
    private TextView emptyHint;
    private TextView errorBanner;
    private TextView statusText;
    private Button arabicButton;
    private Button otherButton;
    private final TextView[] targetChips = new TextView[3];
    private final TextView[] dialectChips = new TextView[3];

    /** One chat entry. */
    private static final class Msg {
        final boolean fromArabic;
        final String original;
        final String translation;
        final String originalLang;
        final String translationLang;

        Msg(boolean fromArabic, String original, String translation, String originalLang, String translationLang) {
            this.fromArabic = fromArabic;
            this.original = original;
            this.translation = translation;
            this.originalLang = originalLang;
            this.translationLang = translationLang;
        }
    }

    // ------------------------------------------------------------------ lifecycle

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("settings", MODE_PRIVATE);
        targetIdx = clamp(prefs.getInt("target", 0));
        dialectIdx = clamp(prefs.getInt("dialect", 0));

        setContentView(buildUi());
        refreshSelectors();

        if (savedInstanceState != null) restoreMessages(savedInstanceState);
        initTts();
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        int n = messages.size();
        boolean[] from = new boolean[n];
        ArrayList<String> orig = new ArrayList<>();
        ArrayList<String> trans = new ArrayList<>();
        ArrayList<String> srcLang = new ArrayList<>();
        ArrayList<String> lang = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            Msg m = messages.get(i);
            from[i] = m.fromArabic;
            orig.add(m.original);
            trans.add(m.translation);
            srcLang.add(m.originalLang);
            lang.add(m.translationLang);
        }
        out.putBooleanArray("from", from);
        out.putStringArrayList("orig", orig);
        out.putStringArrayList("trans", trans);
        out.putStringArrayList("srcLang", srcLang);
        out.putStringArrayList("lang", lang);
    }

    private void restoreMessages(Bundle b) {
        boolean[] from = b.getBooleanArray("from");
        ArrayList<String> orig = b.getStringArrayList("orig");
        ArrayList<String> trans = b.getStringArrayList("trans");
        ArrayList<String> srcLang = b.getStringArrayList("srcLang");
        ArrayList<String> lang = b.getStringArrayList("lang");
        if (from == null || orig == null || trans == null || srcLang == null || lang == null) return;
        for (int i = 0; i < from.length; i++) {
            addMessage(new Msg(from[i], orig.get(i), trans.get(i), srcLang.get(i), lang.get(i)));
        }
    }

    @Override
    protected void onDestroy() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        worker.shutdownNow();
        super.onDestroy();
    }

    // ------------------------------------------------------------------ UI

    private View buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(C_BG);
        root.setLayoutDirection(View.LAYOUT_DIRECTION_RTL);
        root.setPadding(dp(14), dp(10), dp(14), dp(12));

        // Header: title and clear button
        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        TextView title = text("Talk Translate", 22, C_TEXT, true);
        header.addView(title, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));

        TextView clear = text("مسح المحادثة", 15, C_TEXT, true);
        clear.setGravity(Gravity.CENTER);
        clear.setPadding(dp(16), dp(10), dp(16), dp(10));
        clear.setBackground(pressable(C_SURFACE, dp(22)));
        clear.setOnClickListener(v -> clearConversation());
        header.addView(clear);
        root.addView(header);

        // Target language selector
        root.addView(label("اللغة الأخرى"));
        root.addView(chipRow(targetChips, TARGET_NAMES, i -> {
            targetIdx = i;
            prefs.edit().putInt("target", i).apply();
            refreshSelectors();
        }));

        // Dialect selector
        root.addView(label("لهجتك العربية"));
        root.addView(chipRow(dialectChips, DIALECT_NAMES, i -> {
            dialectIdx = i;
            prefs.edit().putInt("dialect", i).apply();
            refreshSelectors();
        }));

        // Error banner (tap to hide)
        errorBanner = text("", 15, C_ERROR_TEXT, false);
        errorBanner.setTextDirection(View.TEXT_DIRECTION_RTL);
        errorBanner.setPadding(dp(14), dp(10), dp(14), dp(10));
        errorBanner.setBackground(rounded(C_ERROR_BG, dp(12)));
        errorBanner.setVisibility(View.GONE);
        errorBanner.setOnClickListener(v -> v.setVisibility(View.GONE));
        LinearLayout.LayoutParams ebp = matchWrap();
        ebp.topMargin = dp(10);
        root.addView(errorBanner, ebp);

        // Conversation
        chatScroll = new ScrollView(this);
        chatScroll.setFillViewport(true);
        chatScroll.setBackground(rounded(0xFF14171D, dp(16)));
        chatList = new LinearLayout(this);
        chatList.setOrientation(LinearLayout.VERTICAL);
        chatList.setPadding(dp(10), dp(10), dp(10), dp(10));
        chatScroll.addView(chatList, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        emptyHint = text("اضغط على الزر الأخضر وتكلّم بالعربية.\n"
                + "اضغط على الزر الأزرق ليتكلّم الشخص الآخر.\n\n"
                + "اضغط على أي رسالة لإعادة الاستماع، واضغط مطوّلاً لنسخ الترجمة.", 16, C_MUTED, false);
        emptyHint.setGravity(Gravity.CENTER);
        emptyHint.setTextDirection(View.TEXT_DIRECTION_RTL);
        emptyHint.setLineSpacing(0, 1.3f);
        emptyHint.setPadding(dp(16), dp(40), dp(16), dp(40));
        chatList.addView(emptyHint, matchWrap());

        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1);
        sp.topMargin = dp(10);
        root.addView(chatScroll, sp);

        // Status line
        statusText = text("", 15, C_MUTED, false);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, dp(8), 0, dp(2));
        statusText.setVisibility(View.GONE);
        root.addView(statusText, matchWrap());

        // Big buttons, side by side for one hand use. RTL puts the green one on the right.
        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        arabicButton = bigButton("تكلّم بالعربية", C_GREEN);
        arabicButton.setOnClickListener(v -> startListening(true));
        otherButton = bigButton("", C_BLUE);
        otherButton.setOnClickListener(v -> startListening(false));

        LinearLayout.LayoutParams b1 = new LinearLayout.LayoutParams(0, dp(112), 1);
        b1.setMarginEnd(dp(6));
        LinearLayout.LayoutParams b2 = new LinearLayout.LayoutParams(0, dp(112), 1);
        b2.setMarginStart(dp(6));
        buttons.addView(arabicButton, b1);
        buttons.addView(otherButton, b2);
        LinearLayout.LayoutParams bp = matchWrap();
        bp.topMargin = dp(10);
        root.addView(buttons, bp);

        return root;
    }

    private interface IntPick {
        void pick(int i);
    }

    private LinearLayout chipRow(TextView[] holder, String[] names, IntPick onPick) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < names.length; i++) {
            final int idx = i;
            TextView chip = text(names[i], 16, C_TEXT, true);
            chip.setGravity(Gravity.CENTER);
            chip.setSingleLine(true);
            chip.setPadding(dp(6), dp(12), dp(6), dp(12));
            chip.setOnClickListener(v -> onPick.pick(idx));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1);
            if (i > 0) lp.setMarginStart(dp(8));
            row.addView(chip, lp);
            holder[i] = chip;
        }
        return row;
    }

    private void refreshSelectors() {
        for (int i = 0; i < 3; i++) {
            boolean t = i == targetIdx;
            targetChips[i].setBackground(pressable(t ? C_BLUE : C_SURFACE, dp(14)));
            targetChips[i].setTextColor(t ? 0xFFFFFFFF : C_MUTED);
            boolean d = i == dialectIdx;
            dialectChips[i].setBackground(pressable(d ? C_GREEN : C_SURFACE, dp(14)));
            dialectChips[i].setTextColor(d ? 0xFFFFFFFF : C_MUTED);
        }
        otherButton.setText("🎤\n" + TARGET_BUTTON[targetIdx]);
        arabicButton.setText("🎤\nتكلّم بالعربية");
    }

    private Button bigButton(String label, int color) {
        Button b = new Button(this);
        b.setAllCaps(false);
        b.setText(label);
        b.setTextColor(0xFFFFFFFF);
        b.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setGravity(Gravity.CENTER);
        b.setLineSpacing(0, 1.1f);
        b.setStateListAnimator(null);
        b.setBackground(pressable(color, dp(24)));
        return b;
    }

    private TextView label(String s) {
        TextView t = text(s, 14, C_MUTED, false);
        t.setPadding(dp(4), dp(12), dp(4), dp(6));
        return t;
    }

    private TextView text(String s, int sp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void setBusy(boolean b, String status) {
        busy = b;
        arabicButton.setEnabled(!b);
        otherButton.setEnabled(!b);
        arabicButton.setAlpha(b ? 0.5f : 1f);
        otherButton.setAlpha(b ? 0.5f : 1f);
        if (status == null) {
            statusText.setVisibility(View.GONE);
        } else {
            statusText.setText(status);
            statusText.setVisibility(View.VISIBLE);
        }
    }

    private void showError(String msg) {
        errorBanner.setText(msg);
        errorBanner.setVisibility(View.VISIBLE);
    }

    private void hideError() {
        errorBanner.setVisibility(View.GONE);
    }

    // ------------------------------------------------------------------ chat bubbles

    private void addMessage(Msg m) {
        messages.add(m);
        emptyHint.setVisibility(View.GONE);

        boolean toArabic = AR.equals(m.translationLang);
        int maxW = (int) (getResources().getDisplayMetrics().widthPixels * 0.78f);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        bubble.setPadding(dp(14), dp(10), dp(14), dp(12));
        bubble.setBackground(pressable(m.fromArabic ? C_GREEN_BUBBLE : C_BLUE_BUBBLE, dp(18)));

        String route = displayName(m.originalLang) + " > " + displayName(m.translationLang);
        TextView tag = text("🔊 " + (m.fromArabic ? "أنت" : "الشخص الآخر") + "  " + route, 12, C_MUTED, false);
        tag.setTextDirection(View.TEXT_DIRECTION_RTL);
        tag.setMaxWidth(maxW);
        bubble.addView(tag);

        TextView orig = text(m.original, 14, 0xFFB8C0CC, false);
        orig.setTextDirection(m.fromArabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        orig.setMaxWidth(maxW);
        orig.setPadding(0, dp(4), 0, dp(4));
        bubble.addView(orig);

        TextView trans = text(m.translation, 22, 0xFFFFFFFF, true);
        trans.setTextDirection(toArabic ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
        trans.setMaxWidth(maxW);
        trans.setLineSpacing(0, 1.15f);
        bubble.addView(trans);

        bubble.setOnClickListener(v -> speak(m.translation, m.translationLang));
        bubble.setOnLongClickListener(v -> {
            copy(m.translation);
            return true;
        });

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        // Arabic speaker on the right, the other person on the left.
        lp.gravity = m.fromArabic ? Gravity.RIGHT : Gravity.LEFT;
        lp.topMargin = dp(6);
        lp.bottomMargin = dp(6);
        chatList.addView(bubble, lp);

        chatScroll.post(() -> chatScroll.fullScroll(View.FOCUS_DOWN));
    }

    private static String displayName(String lang) {
        for (int i = 0; i < TARGET_CODES.length; i++) {
            if (TARGET_CODES[i].equals(lang)) return TARGET_NAMES[i];
        }
        return "العربية";
    }

    private void clearConversation() {
        if (tts != null) tts.stop();
        messages.clear();
        chatList.removeAllViews();
        chatList.addView(emptyHint, matchWrap());
        emptyHint.setVisibility(View.VISIBLE);
        hideError();
    }

    private void copy(String s) {
        ClipboardManager cm = getSystemService(ClipboardManager.class);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("translation", s));
            Toast.makeText(this, "تم نسخ الترجمة", Toast.LENGTH_SHORT).show();
        }
    }

    // ------------------------------------------------------------------ speech to text

    private void startListening(boolean arabic) {
        if (busy) return;
        hideError();
        if (!isOnline()) {
            showError(MSG_NO_INTERNET);
            return;
        }
        if (tts != null) tts.stop();

        String tag = arabic ? DIALECT_TAGS[dialectIdx] : TARGET_SPEECH_TAGS[targetIdx];
        Intent i = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, tag);
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, tag);
        i.putExtra(RecognizerIntent.EXTRA_ONLY_RETURN_LANGUAGE_PREFERENCE, tag);
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1);
        i.putExtra(RecognizerIntent.EXTRA_PROMPT, arabic ? "تكلّم الآن بالعربية" : TARGET_PROMPT[targetIdx]);
        try {
            startActivityForResult(i, arabic ? REQ_ARABIC : REQ_OTHER);
        } catch (ActivityNotFoundException | SecurityException e) {
            showGoogleMissing();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode != REQ_ARABIC && requestCode != REQ_OTHER) return;
        boolean arabic = requestCode == REQ_ARABIC;

        if (resultCode == RESULT_OK) {
            ArrayList<String> results = data == null ? null
                    : data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS);
            String heard = (results == null || results.isEmpty()) ? null : results.get(0);
            if (heard == null || heard.trim().isEmpty()) {
                showError(MSG_NO_SPEECH);
            } else {
                translateAndShow(heard.trim(), arabic);
            }
            return;
        }
        switch (resultCode) {
            case RESULT_CANCELED:
                break; // user closed the dialog
            case RecognizerIntent.RESULT_NO_MATCH:
                showError(MSG_NO_SPEECH);
                break;
            case RecognizerIntent.RESULT_NETWORK_ERROR:
            case RecognizerIntent.RESULT_SERVER_ERROR:
                showError(isOnline() ? MSG_RECOGNIZER : MSG_NO_INTERNET);
                break;
            case RecognizerIntent.RESULT_AUDIO_ERROR:
                showError(MSG_MIC);
                break;
            default:
                showError(MSG_RECOGNIZER);
                break;
        }
    }

    private void showGoogleMissing() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("التعرّف على الصوت غير متاح")
                .setMessage(MSG_GOOGLE_MISSING)
                .setPositiveButton("فتح متجر Play", (d, w) -> openStore(GOOGLE_APP))
                .setNegativeButton("حسناً", null)
                .show();
        showError("تطبيق Google غير مثبت أو معطّل.");
    }

    private void openStore(String pkg) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=" + pkg)));
        } catch (ActivityNotFoundException e) {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=" + pkg)));
            } catch (ActivityNotFoundException ignored) {
                Toast.makeText(this, "تعذّر فتح المتجر", Toast.LENGTH_SHORT).show();
            }
        }
    }

    // ------------------------------------------------------------------ translation

    private void translateAndShow(String text, boolean fromArabic) {
        final String src = fromArabic ? AR : TARGET_CODES[targetIdx];
        final String dst = fromArabic ? TARGET_CODES[targetIdx] : AR;
        setBusy(true, "جارٍ الترجمة...");
        worker.execute(() -> {
            String result = null;
            try {
                result = Translator.translate(text, src, dst);
            } catch (Exception ignored) {
                // handled below as a failed translation
            }
            final String translated = result;
            main.post(() -> {
                if (isFinishing() || isDestroyed()) return;
                setBusy(false, null);
                if (translated == null) {
                    showError((isOnline() ? MSG_TRANSLATE_FAILED : MSG_NO_INTERNET) + "\n\n" + text);
                    return;
                }
                addMessage(new Msg(fromArabic, text, translated, src, dst));
                speak(translated, dst);
            });
        });
    }

    private boolean isOnline() {
        ConnectivityManager cm = getSystemService(ConnectivityManager.class);
        if (cm == null) return true;
        Network n = cm.getActiveNetwork();
        if (n == null) return false;
        NetworkCapabilities caps = cm.getNetworkCapabilities(n);
        return caps != null && caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);
    }

    // ------------------------------------------------------------------ text to speech

    private void initTts() {
        TextToSpeech.OnInitListener listener = status -> main.post(() -> onTtsInit(status));
        if (isInstalled(GOOGLE_TTS)) {
            // MIUI often ships its own engine first; prefer Google's, which has Arabic voices.
            tts = new TextToSpeech(this, listener, GOOGLE_TTS);
        } else {
            tts = new TextToSpeech(this, listener);
        }
    }

    private void onTtsInit(int status) {
        if (isFinishing() || isDestroyed()) return;
        if (status == TextToSpeech.SUCCESS) {
            ttsReady = true;
            if (pendingText != null) {
                String t = pendingText;
                String l = pendingLang;
                pendingText = null;
                pendingLang = null;
                speak(t, l);
            }
        } else {
            ttsReady = false;
            showTtsEngineMissing();
        }
    }

    private void speak(String text, String lang) {
        if (tts == null) return;
        if (!ttsReady) {
            pendingText = text;
            pendingLang = lang;
            return;
        }
        Locale loc = localeFor(lang);
        int r = tts.setLanguage(loc);
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            r = tts.setLanguage(new Locale(lang));
        }
        if (r == TextToSpeech.LANG_MISSING_DATA || r == TextToSpeech.LANG_NOT_SUPPORTED) {
            showVoiceMissing(lang);
            return;
        }
        tts.speak(text, TextToSpeech.QUEUE_FLUSH, null, "tt" + System.currentTimeMillis());
    }

    private Locale localeFor(String lang) {
        for (int i = 0; i < TARGET_CODES.length; i++) {
            if (TARGET_CODES[i].equals(lang)) return TARGET_LOCALES[i];
        }
        return new Locale(AR);
    }

    private String arabicNameOf(String lang) {
        switch (lang) {
            case "en":
                return "الإنجليزية";
            case "fr":
                return "الفرنسية";
            case "es":
                return "الإسبانية";
            default:
                return "العربية";
        }
    }

    private void showVoiceMissing(String lang) {
        String name = arabicNameOf(lang);
        showError("صوت اللغة " + name + " غير مثبت. ثبّته من الإعدادات > تحويل النص إلى كلام > Google");
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("صوت " + name + " غير مثبت")
                .setMessage("لا يمكن قراءة الترجمة بصوت عالٍ لأن صوت " + name + " غير مثبت على هاتفك.\n\n"
                        + "ثبّته من:\nالإعدادات > تحويل النص إلى كلام > Google > تثبيت بيانات الصوت\n\n"
                        + "ويمكنك دائماً قراءة الترجمة على الشاشة.")
                .setPositiveButton("فتح الإعدادات", (d, w) -> openTtsSettings())
                .setNegativeButton("حسناً", null)
                .show();
    }

    private void showTtsEngineMissing() {
        new AlertDialog.Builder(this, android.R.style.Theme_Material_Dialog_Alert)
                .setTitle("القراءة الصوتية غير متاحة")
                .setMessage(MSG_TTS_ENGINE)
                .setPositiveButton("فتح متجر Play", (d, w) -> openStore(GOOGLE_TTS))
                .setNeutralButton("الإعدادات", (d, w) -> openTtsSettings())
                .setNegativeButton("حسناً", null)
                .show();
    }

    private void openTtsSettings() {
        try {
            startActivity(new Intent("com.android.settings.TTS_SETTINGS")
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
            return;
        } catch (ActivityNotFoundException ignored) {
            // try the next option
        }
        try {
            startActivity(new Intent(TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA));
        } catch (ActivityNotFoundException ignored) {
            openStore(GOOGLE_TTS);
        }
    }

    private boolean isInstalled(String pkg) {
        try {
            getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    // ------------------------------------------------------------------ small helpers

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                getResources().getDisplayMetrics()));
    }

    private static int clamp(int i) {
        return (i < 0 || i > 2) ? 0 : i;
    }

    private static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private static GradientDrawable rounded(int color, int radius) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(radius);
        return g;
    }

    private static Drawable pressable(int color, int radius) {
        return new RippleDrawable(ColorStateList.valueOf(0x40FFFFFF), rounded(color, radius),
                rounded(0xFFFFFFFF, radius));
    }
}
