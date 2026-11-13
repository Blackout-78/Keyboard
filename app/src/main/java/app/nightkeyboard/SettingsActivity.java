package app.nightkeyboard;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SettingsActivity extends Activity {
    private static final int REQ_IMAGE = 1, REQ_FONT = 2, REQ_VOICE = 3, REQ_MIC = 7;
    private static final int BG = 0xFF0B0B10, CARD = 0xFF13131A, BTN = 0xFF1B1B24, MUTED = 0xFF8B8BA3;
    private static final String RELEASES = "https://github.com/Blackout-78/Keyboard/releases";
    private static final String[] TABS = {"Theme", "Typing", "Privacy", "About"};
    private static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    private static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;

    private LinearLayout col;
    private LinearLayout tabBar;
    private int tab = 0;
    private int accent;
    private KeyboardView preview;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        Cfg.firstRun(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(BG);
        ScrollView sv = new ScrollView(this);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(12), dp(12), dp(12), dp(24));
        sv.addView(col, new ViewGroup.LayoutParams(MATCH, WRAP));
        root.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        tabBar = new LinearLayout(this);
        tabBar.setOrientation(LinearLayout.HORIZONTAL);
        tabBar.setBackgroundColor(0xFF0F0F15);
        for (int i = 0; i < TABS.length; i++) {
            final int idx = i;
            TextView t = new TextView(this);
            t.setText(TABS[i]);
            t.setTextSize(13);
            t.setGravity(Gravity.CENTER);
            t.setPadding(0, dp(16), 0, dp(16));
            t.setOnClickListener(v -> selectTab(idx));
            tabBar.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        }
        root.addView(tabBar, new LinearLayout.LayoutParams(MATCH, WRAP));
        setContentView(root);
        selectTab(getIntent().getIntExtra("tab", 0));
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private void selectTab(int i) {
        tab = i;
        accent = Cfg.accent(this);
        preview = null;
        col.removeAllViews();
        TextView h = new TextView(this);
        h.setText("Keyboard");
        h.setTextSize(22);
        h.setTextColor(Color.WHITE);
        h.setPadding(dp(4), 0, 0, dp(4));
        col.addView(h);
        if (i == 0) buildTheme();
        else if (i == 1) buildTyping();
        else if (i == 2) buildPrivacy();
        else buildAbout();
        for (int k = 0; k < tabBar.getChildCount(); k++) {
            ((TextView) tabBar.getChildAt(k)).setTextColor(k == i ? accent : MUTED);
        }
    }

    private void buildTheme() {
        preview = new KeyboardView(this);
        preview.setPreview(true);
        LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(MATCH, WRAP);
        plp.topMargin = dp(8);
        col.addView(preview, plp);

        LinearLayout c = card("BACKGROUND");
        button(c, "Use built-in Skull theme", v -> {
            toast(Cfg.applySkull(this) ? "Skull theme applied" : "Could not apply");
            refreshPreview();
        });
        button(c, "Choose your own image", v -> pick("image/*", REQ_IMAGE));
        button(c, "Remove background image", v -> {
            Cfg.bgFile(this).delete();
            refreshPreview();
            toast("Background removed");
        });
        final Button fb = button(c, fitLabel(), null);
        fb.setOnClickListener(v -> {
            Cfg.p(this).edit().putInt("fit", (Cfg.fit(this) + 1) % 3).apply();
            fb.setText(fitLabel());
        });
        label(c, "Tint", 13, MUTED);
        LinearLayout tints = new LinearLayout(this);
        tints.setOrientation(LinearLayout.HORIZONTAL);
        chip(tints, "None", 0xFF1B1B24, () -> setTint(0));
        chip(tints, "Blue", 0xFF5C7CFA, () -> setTint(0xFF5C7CFA));
        chip(tints, "Violet", 0xFF8B5CF6, () -> setTint(0xFF8B5CF6));
        chip(tints, "Crimson", 0xFFE5384D, () -> setTint(0xFFE5384D));
        chip(tints, "Wheel", 0xFF2A2A38, () -> pickColor("Image tint", "tint", 0xFF5C7CFA));
        c.addView(tints);
        slider(c, "Image dimming", "dim", 255, Cfg.DEFAULT_DIM);

        c = card("KEYS");
        slider(c, "Letter keys: transparent to solid", "letterAlpha", 255, Cfg.DEFAULT_KEY_ALPHA);
        slider(c, "Other keys: transparent to solid", "otherAlpha", 255, Cfg.DEFAULT_KEY_ALPHA);
        rangeSlider(c, "Keyboard height (percent)", "heightPct", 60, 140, 100);
        colorRow(c, "Key color", "keyColor", 0xFF000000);

        c = card("COLORS");
        colorRow(c, "Accent color (Enter key, tabs, titles)", "accent", Cfg.DEFAULT_ACCENT);
        colorRow(c, "Text color (letters, numbers, icons)", "textColor", 0xFFFFFFFF);
        colorRow(c, "Hint symbol color", "hintColor", 0xFF9A9AB0);

        c = card("FONT");
        label(c, Cfg.fontFile(this).exists() ? "Custom font in use" : "Default font in use", 13, MUTED);
        button(c, "Use built-in Typewriter font", v -> {
            toast(Cfg.applyTypewriterFont(this) ? "Typewriter font set" : "Could not apply");
            refreshPreview();
            selectTab(tab);
        });
        button(c, "Choose font (.ttf)", v -> pick("*/*", REQ_FONT));
        button(c, "Remove custom font", v -> {
            Cfg.fontFile(this).delete();
            refreshPreview();
            toast("Font removed");
            selectTab(tab);
        });
    }

    private void buildTyping() {
        LinearLayout c = card("SET UP");
        button(c, "1. Enable keyboard", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button(c, "2. Switch to this keyboard", v -> {
            InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m != null) m.showInputMethodPicker();
        });
        EditText test = new EditText(this);
        test.setHint("Type here to test");
        test.setHintTextColor(MUTED);
        test.setTextColor(Color.WHITE);
        c.addView(test);

        c = card("TYPING");
        toggle(c, "Slide on Space bar to move cursor", "spaceSwipe", true);
        toggle(c, "Hint symbols on keys (hold a key to type its hint)", "hints", true);
        label(c, "Suggestions and autocorrect: coming next.", 13, MUTED);

        c = card("VOICE (OFFLINE)");
        File vm = Cfg.voiceFile(this);
        boolean mic = checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) == android.content.pm.PackageManager.PERMISSION_GRANTED;
        label(c, "Voice runs fully on this phone. Audio is never saved or sent. No internet.", 13, MUTED);
        label(c, mic ? "Microphone: allowed" : "Microphone: not allowed yet", 14, Color.WHITE);
        if (!mic) button(c, "Allow microphone", v -> requestPermissions(new String[]{android.Manifest.permission.RECORD_AUDIO}, REQ_MIC));
        label(c, vm.exists() ? "Voice model: loaded (" + (vm.length() / 1048576) + " MB)" : "Voice model: not loaded", 14, Color.WHITE);
        button(c, "Load voice model (ggml .bin)", v -> pick("*/*", REQ_VOICE));
        if (vm.exists()) button(c, "Remove voice model", v -> { vm.delete(); selectTab(tab); });
    }

    @Override
    public void onRequestPermissionsResult(int req, String[] perms, int[] results) {
        super.onRequestPermissionsResult(req, perms, results);
        selectTab(tab);
    }

    private void buildPrivacy() {
        LinearLayout c = card("PRIVACY");
        label(c, "Internet access: none. The app has no network permission.", 14, Color.WHITE);
        label(c, "Backups and cloud sync: disabled for this app.", 14, Color.WHITE);
        label(c, "Typed text is never stored, logged, or sent anywhere.", 14, Color.WHITE);
        label(c, "No clipboard history. Copy, cut, and paste use Android's own clipboard.", 14, Color.WHITE);
        label(c, "Only your theme choices and background image are saved, in the app's private storage.", 14, Color.WHITE);
    }

    private void buildAbout() {
        LinearLayout c = card("ABOUT");
        label(c, "Version " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")", 14, Color.WHITE);
        label(c, "Offline. Private. Installed only through GitHub.", 13, MUTED);
        label(c, "Typewriter font: Special Elite by Astigmatic, Apache License 2.0 (license bundled in the app).", 12, MUTED);
        Button rel = button(c, "Open GitHub Releases", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES)));
            } catch (Exception e) {
                toast("No browser found");
            }
        });
        rel.setTextColor(accent);
    }

    // ---- helpers ----

    private String fitLabel() {
        int f = Cfg.fit(this);
        return "Image fit: " + (f == 0 ? "Stretch" : (f == 1 ? "Crop" : "Fit inside")) + " (tap to change)";
    }

    private void setTint(int c) {
        Cfg.p(this).edit().putInt("tint", c).apply();
    }

    private void refreshPreview() {
        if (preview != null) preview.reloadTheme();
    }

    private LinearLayout card(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable g = new GradientDrawable();
        g.setColor(CARD);
        g.setCornerRadius(dp(12));
        c.setBackground(g);
        c.setPadding(dp(14), dp(12), dp(14), dp(12));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(10);
        col.addView(c, lp);
        if (title != null) label(c, title, 11, accent);
        return c;
    }

    private TextView label(LinearLayout p, String t, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setPadding(0, dp(4), 0, dp(4));
        p.addView(tv);
        return tv;
    }

    private Button button(LinearLayout p, String t, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        GradientDrawable g = new GradientDrawable();
        g.setColor(BTN);
        g.setCornerRadius(dp(8));
        bt.setBackground(g);
        if (l != null) bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(MATCH, WRAP);
        lp.topMargin = dp(8);
        p.addView(bt, lp);
        return bt;
    }

    private void chip(LinearLayout row, String name, int color, final Runnable action) {
        Button bt = new Button(this);
        bt.setText(name);
        bt.setTextSize(11);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        bt.setMinWidth(0);
        bt.setMinimumWidth(0);
        bt.setPadding(0, 0, 0, 0);
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(8));
        bt.setBackground(g);
        bt.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, WRAP, 1f);
        lp.rightMargin = dp(4);
        row.addView(bt, lp);
    }

    private void toggle(LinearLayout p, String t, final String key, boolean def) {
        Switch sw = new Switch(this);
        sw.setText(t);
        sw.setTextColor(Color.WHITE);
        sw.setChecked(Cfg.p(this).getBoolean(key, def));
        sw.setPadding(0, dp(8), 0, dp(8));
        sw.setOnCheckedChangeListener((bv, on) -> Cfg.p(this).edit().putBoolean(key, on).apply());
        p.addView(sw);
    }

    private void tintBar(SeekBar sb) {
        sb.setProgressTintList(ColorStateList.valueOf(accent));
        sb.setThumbTintList(ColorStateList.valueOf(accent));
    }

    private void slider(LinearLayout p, String title, final String key, int max, int def) {
        label(p, title, 13, MUTED);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(Cfg.p(this).getInt(key, def));
        tintBar(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) Cfg.p(SettingsActivity.this).edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        p.addView(sb);
    }

    private void rangeSlider(LinearLayout p, final String title, final String key, final int min, int max, int def) {
        final TextView lb = label(p, title + ": " + Cfg.p(this).getInt(key, def), 13, MUTED);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max - min);
        sb.setProgress(Cfg.p(this).getInt(key, def) - min);
        tintBar(sb);
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                lb.setText(title + ": " + (v + min));
                if (user) Cfg.p(SettingsActivity.this).edit().putInt(key, v + min).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        p.addView(sb);
    }

    private void colorRow(LinearLayout p, String title, final String key, final int def) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(0, dp(8), 0, dp(8));
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(14);
        t.setTextColor(Color.WHITE);
        row.addView(t, new LinearLayout.LayoutParams(0, WRAP, 1f));
        View sw = new View(this);
        GradientDrawable g = new GradientDrawable();
        g.setColor(Cfg.p(this).getInt(key, def));
        g.setCornerRadius(dp(6));
        g.setStroke(dp(1), 0xFF5A5A70);
        sw.setBackground(g);
        row.addView(sw, new LinearLayout.LayoutParams(dp(44), dp(26)));
        row.setOnClickListener(v -> pickColor(title, key, def));
        p.addView(row);
    }

    /** Opens the color wheel. Changes apply to the keyboard live. */
    private void pickColor(String title, final String key, final int def) {
        int start = Cfg.p(this).getInt(key, def);
        if (start == 0) start = def;
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(16), dp(8), dp(16), dp(8));
        final ColorWheelView wheel = new ColorWheelView(this);
        wheel.setColor(start);
        wheel.setListener(c -> Cfg.p(this).edit().putInt(key, c).apply());
        box.addView(wheel, new LinearLayout.LayoutParams(MATCH, WRAP));
        TextView lb = new TextView(this);
        lb.setText("Brightness");
        lb.setTextColor(MUTED);
        lb.setTextSize(13);
        box.addView(lb);
        float[] hsv = new float[3];
        Color.colorToHSV(start, hsv);
        SeekBar sb = new SeekBar(this);
        sb.setMax(100);
        sb.setProgress((int) (hsv[2] * 100));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) wheel.setValue(v / 100f);
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        box.addView(sb);
        new AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setTitle(title)
                .setView(box)
                .setPositiveButton("Done", null)
                .setNeutralButton("Reset", (d, w) -> {
                    if (key.equals("tint")) Cfg.p(this).edit().putInt(key, 0).apply();
                    else Cfg.p(this).edit().remove(key).apply();
                })
                .setOnDismissListener(d -> selectTab(tab))
                .show();
    }

    private void pick(String mime, int req) {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType(mime);
        startActivityForResult(i, req);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (res != RESULT_OK || data == null || data.getData() == null) return;
        Uri u = data.getData();
        if (req == REQ_VOICE) {
            toast("Loading voice model\u2026");
            final Uri src = u;
            new Thread(new Runnable() {
                @Override public void run() {
                    final File f = Cfg.voiceFile(SettingsActivity.this);
                    boolean ok = false;
                    try (InputStream in = getContentResolver().openInputStream(src);
                         OutputStream out = new FileOutputStream(f)) {
                        byte[] buf = new byte[65536];
                        int n;
                        if (in != null) {
                            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                            ok = true;
                        }
                    } catch (Exception e) {
                        ok = false;
                    }
                    final boolean good = ok && f.length() > 1000000;
                    if (!good) f.delete();
                    runOnUiThread(new Runnable() {
                        @Override public void run() {
                            toast(good ? "Voice model loaded" : "Could not load that file");
                            selectTab(tab);
                        }
                    });
                }
            }).start();
            return;
        }
        if (req == REQ_IMAGE) {
            File f = Cfg.bgFile(this);
            if (!copy(u, f)) return;
            BitmapFactory.Options o = new BitmapFactory.Options();
            o.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(f.getAbsolutePath(), o);
            if (o.outWidth <= 0) {
                f.delete();
                toast("That file is not an image");
            } else {
                toast("Background set");
            }
            refreshPreview();
        } else if (req == REQ_FONT) {
            File f = Cfg.fontFile(this);
            if (!copy(u, f)) return;
            try {
                Typeface.createFromFile(f);
                toast("Font set");
            } catch (Exception e) {
                f.delete();
                toast("That file is not a usable font");
            }
            refreshPreview();
            selectTab(tab);
        }
    }

    private boolean copy(Uri u, File to) {
        try (InputStream in = getContentResolver().openInputStream(u);
             OutputStream out = new FileOutputStream(to)) {
            if (in == null) return false;
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            return true;
        } catch (Exception e) {
            toast("Could not read that file");
            return false;
        }
    }

    private void toast(String s) {
        Toast t = Toast.makeText(this, s, Toast.LENGTH_SHORT);
        t.setGravity(Gravity.BOTTOM, 0, dp(80));
        t.show();
    }
}
