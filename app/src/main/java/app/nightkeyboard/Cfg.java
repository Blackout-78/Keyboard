package app.nightkeyboard;

import android.content.Context;
import android.content.SharedPreferences;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;

/** Theme settings. Stored only in the app's private storage. */
final class Cfg {
    static final int DEFAULT_KEY_ALPHA = 200;
    static final int DEFAULT_DIM = 90;

    private Cfg() {}

    static SharedPreferences p(Context c) {
        return c.getSharedPreferences("cfg", Context.MODE_PRIVATE);
    }

    static File bgFile(Context c) { return new File(c.getFilesDir(), "bg.img"); }

    static File fontFile(Context c) { return new File(c.getFilesDir(), "font.ttf"); }

    /** Opacity of letter, number and symbol keys: 0 transparent to 255 solid. */
    static int letterAlpha(Context c) { return p(c).getInt("letterAlpha", DEFAULT_KEY_ALPHA); }

    /** Opacity of shift, backspace, ?123, Space and Enter keys. */
    static int otherAlpha(Context c) { return p(c).getInt("otherAlpha", DEFAULT_KEY_ALPHA); }

    static int dim(Context c) { return p(c).getInt("dim", DEFAULT_DIM); }

    static int keyColor(Context c) { return p(c).getInt("keyColor", 0xFF000000); }

    /** 0 = stretch to fill, 1 = crop to fill, 2 = fit inside. */
    static int fit(Context c) { return p(c).getInt("fit", 0); }

    /** Image tint color, 0 = none. */
    static int tint(Context c) { return p(c).getInt("tint", 0); }

    static boolean hints(Context c) { return p(c).getBoolean("hints", true); }

    /** Built-in Skull theme: copies the bundled artwork into private storage and sets the look. */
    static boolean applySkull(Context c) {
        try (InputStream in = c.getAssets().open("themes/skull.jpg");
             OutputStream out = new FileOutputStream(bgFile(c))) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
        } catch (Exception e) {
            return false;
        }
        p(c).edit().putInt("keyColor", 0xFF000000).putInt("letterAlpha", 170).putInt("otherAlpha", 170).putInt("dim", 0)
                .putInt("fit", 0).putInt("tint", 0xFF5C7CFA)
                .putBoolean("themeInit", true).apply();
        return true;
    }

    /** First run: start with the Skull theme. */
    static void firstRun(Context c) {
        if (!p(c).getBoolean("themeInit", false)) applySkull(c);
    }
}
