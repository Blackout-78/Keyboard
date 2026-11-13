package app.nightkeyboard;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.Typeface;
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
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

public class SettingsActivity extends Activity {
    private static final int REQ_IMAGE = 1, REQ_FONT = 2;
    private static final int BG = 0xFF0B0B10, ACCENT = 0xFFA99CFF, MUTED = 0xFF8B8BA3;
    private static final String RELEASES = "https://github.com/Blackout-78/Keyboard/releases";

    private LinearLayout col;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(BG);
        col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        int p = dp(16);
        col.setPadding(p, p, p, p * 2);
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(sv);

        text("Keyboard", 24, Color.WHITE);
        text("Offline. Private. Nothing is collected or saved.", 13, MUTED);

        Cfg.firstRun(this);
        section("SET UP");
        button("1. Enable keyboard", v -> startActivity(new Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)));
        button("2. Switch to this keyboard", v -> {
            InputMethodManager m = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            if (m != null) m.showInputMethodPicker();
        });
        EditText test = new EditText(this);
        test.setHint("Type here to test");
        test.setHintTextColor(MUTED);
        test.setTextColor(Color.WHITE);
        col.addView(test);

        section("THEME");
        button("Apply Skull theme (built-in)", v -> toast(Cfg.applySkull(this) ? "Skull theme applied" : "Could not apply"));
        button("Choose background image", v -> pick("image/*", REQ_IMAGE));
        button("Remove background image", v -> {
            Cfg.bgFile(this).delete();
            toast("Background removed");
        });
        button("Image fit: tap to change (stretch / crop / fit)", v -> {
            int f = (Cfg.fit(this) + 1) % 3;
            Cfg.p(this).edit().putInt("fit", f).apply();
            toast(f == 0 ? "Stretch to fill" : (f == 1 ? "Crop to fill" : "Fit inside"));
        });
        text("Image tint", 13, MUTED);
        LinearLayout tints = new LinearLayout(this);
        tints.setOrientation(LinearLayout.HORIZONTAL);
        addTint(tints, "None", 0);
        addTint(tints, "Blue", 0xFF5C7CFA);
        addTint(tints, "Violet", 0xFF8B5CF6);
        addTint(tints, "Crimson", 0xFFE5384D);
        col.addView(tints);
        slider("Image dimming", "dim", 255, Cfg.DEFAULT_DIM);
        slider("Key opacity", "keyAlpha", 255, Cfg.DEFAULT_KEY_ALPHA);
        text("Key color", 13, MUTED);
        LinearLayout colors = new LinearLayout(this);
        colors.setOrientation(LinearLayout.HORIZONTAL);
        addColor(colors, "Black", 0xFF000000);
        addColor(colors, "Gray", 0xFF1B1B24);
        addColor(colors, "Navy", 0xFF0D1B3A);
        addColor(colors, "Crimson", 0xFF3A0D14);
        col.addView(colors);
        button("Choose font (.ttf)", v -> pick("*/*", REQ_FONT));
        button("Remove custom font", v -> {
            Cfg.fontFile(this).delete();
            toast("Font removed");
        });
        button("Hint symbols on keys: toggle", v -> {
            boolean on = !Cfg.hints(this);
            Cfg.p(this).edit().putBoolean("hints", on).apply();
            toast(on ? "Hints on" : "Hints off");
        });

        section("PRIVACY");
        text("Internet access: none. The app has no network permission.", 14, Color.WHITE);
        text("Backups and cloud sync: disabled for this app.", 14, Color.WHITE);
        text("Typed text is never stored or logged.", 14, Color.WHITE);

        section("ABOUT");
        text("Version " + BuildConfig.VERSION_NAME + " (build " + BuildConfig.VERSION_CODE + ")", 14, Color.WHITE);
        Button rel = button("Open GitHub Releases", v -> {
            try {
                startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES)));
            } catch (Exception e) {
                toast("No browser found");
            }
        });
        rel.setTextColor(ACCENT);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density);
    }

    private TextView text(String t, int sp, int color) {
        TextView tv = new TextView(this);
        tv.setText(t);
        tv.setTextSize(sp);
        tv.setTextColor(color);
        tv.setPadding(0, dp(6), 0, dp(6));
        col.addView(tv);
        return tv;
    }

    private void section(String t) {
        TextView tv = text(t, 12, ACCENT);
        tv.setPadding(0, dp(22), 0, dp(4));
    }

    private Button button(String t, View.OnClickListener l) {
        Button bt = new Button(this);
        bt.setText(t);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        bt.setBackgroundColor(0xFF1B1B24);
        bt.setOnClickListener(l);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(8);
        col.addView(bt, lp);
        return bt;
    }

    private void addColor(LinearLayout row, String name, final int color) {
        Button bt = new Button(this);
        bt.setText(name);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        bt.setBackgroundColor(color);
        bt.setOnClickListener(v -> {
            Cfg.p(this).edit().putInt("keyColor", color).apply();
            toast(name + " keys");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        row.addView(bt, lp);
    }

    private void addTint(LinearLayout row, String name, final int color) {
        Button bt = new Button(this);
        bt.setText(name);
        bt.setAllCaps(false);
        bt.setTextColor(Color.WHITE);
        bt.setBackgroundColor(color == 0 ? 0xFF1B1B24 : color);
        bt.setOnClickListener(v -> {
            Cfg.p(this).edit().putInt("tint", color).apply();
            toast(name + " tint");
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        lp.rightMargin = dp(4);
        row.addView(bt, lp);
    }

    private void slider(String title, final String key, int max, int def) {
        text(title, 13, MUTED);
        SeekBar sb = new SeekBar(this);
        sb.setMax(max);
        sb.setProgress(Cfg.p(this).getInt(key, def));
        sb.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar s, int v, boolean user) {
                if (user) Cfg.p(SettingsActivity.this).edit().putInt(key, v).apply();
            }
            @Override public void onStartTrackingTouch(SeekBar s) {}
            @Override public void onStopTrackingTouch(SeekBar s) {}
        });
        col.addView(sb);
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
