package app.nightkeyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.util.SparseArray;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Skull-style face art, drawn in code from simple shapes. No images, no fonts, nothing downloaded. */
final class SkullFaces {
    private static final class El {
        String tag;
        final HashMap<String, String> a = new HashMap<>();
    }

    private static final List<List<El>> FACES = new ArrayList<>();
    private static final SparseArray<Bitmap> CACHE = new SparseArray<>();
    private static int cSize, cText, cAccent;

    private SkullFaces() {}

    private static void load(Context c) {
        if (!FACES.isEmpty()) return;
        Pattern pe = Pattern.compile("<(path|ellipse|circle|rect)\\s([^>]*?)/>");
        Pattern pa = Pattern.compile("([a-zA-Z-]+)=\"([^\"]*)\"");
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("skull_faces.txt"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                List<El> list = new ArrayList<>();
                Matcher m = pe.matcher(line);
                while (m.find()) {
                    El e = new El();
                    e.tag = m.group(1);
                    Matcher am = pa.matcher(m.group(2));
                    while (am.find()) e.a.put(am.group(1), am.group(2));
                    list.add(e);
                }
                FACES.add(list);
            }
        } catch (Exception x) {
            FACES.clear();
        }
    }

    static Bitmap get(Context c, int expr, int size, int text, int accent) {
        load(c);
        if (expr < 0 || expr >= FACES.size() || size < 4) return null;
        if (size != cSize || text != cText || accent != cAccent) {
            CACHE.clear();
            cSize = size;
            cText = text;
            cAccent = accent;
        }
        Bitmap b = CACHE.get(expr);
        if (b == null) {
            b = render(FACES.get(expr), size, text, accent);
            CACHE.put(expr, b);
        }
        return b;
    }

    private static Integer color(String s, int text, int accent) {
        if (s == null || s.equals("none")) return null;
        if (s.equalsIgnoreCase("#e8e8f0")) return text;
        if (s.equalsIgnoreCase("#7c6cf0")) return accent;
        if (s.equalsIgnoreCase("#0b0b10")) return 0xFF0B0B10;
        try { return Color.parseColor(s); } catch (Exception x) { return null; }
    }

    private static float f(El e, String k, float def) {
        String v = e.a.get(k);
        if (v == null) return def;
        try { return Float.parseFloat(v); } catch (Exception x) { return def; }
    }

    private static Bitmap render(List<El> els, int size, int text, int accent) {
        Bitmap b = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(b);
        float sc = size / 41f;
        cv.translate(sc * 0.5f, 0);
        cv.scale(sc, sc);
        Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
        Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        for (El e : els) {
            Integer fc = color(e.a.get("fill"), text, accent);
            String ss = e.a.get("stroke");
            Integer sc2 = ss == null ? Integer.valueOf(text) : color(ss, text, accent);
            line.setStrokeWidth(f(e, "stroke-width", 1.3f));
            Path p = null;
            RectF r = null;
            if (e.tag.equals("path")) {
                p = parse(e.a.get("d") == null ? "" : e.a.get("d"));
            } else if (e.tag.equals("ellipse")) {
                float cx = f(e, "cx", 0), cy = f(e, "cy", 0), rx = f(e, "rx", 0), ry = f(e, "ry", 0);
                r = new RectF(cx - rx, cy - ry, cx + rx, cy + ry);
                p = new Path();
                p.addOval(r, Path.Direction.CW);
            } else if (e.tag.equals("circle")) {
                float cx = f(e, "cx", 0), cy = f(e, "cy", 0), rr = f(e, "r", 0);
                p = new Path();
                p.addCircle(cx, cy, rr, Path.Direction.CW);
            } else if (e.tag.equals("rect")) {
                float x = f(e, "x", 0), y = f(e, "y", 0), w = f(e, "width", 0), h = f(e, "height", 0), rx = f(e, "rx", 0);
                p = new Path();
                p.addRoundRect(new RectF(x, y, x + w, y + h), rx, rx, Path.Direction.CW);
            }
            if (p == null) continue;
            if (fc != null) {
                fill.setColor(fc);
                cv.drawPath(p, fill);
            }
            if (sc2 != null) {
                line.setColor(sc2);
                cv.drawPath(p, line);
            }
        }
        return b;
    }

    private static Path parse(String d) {
        Path p = new Path();
        List<String> t = new ArrayList<>();
        Matcher m = Pattern.compile("[MLQCZ]|-?\\d*\\.?\\d+(?:[eE]-?\\d+)?").matcher(d);
        while (m.find()) t.add(m.group());
        int i = 0;
        while (i < t.size()) {
            String s = t.get(i++);
            char c = s.charAt(0);
            try {
                if (c == 'M') { p.moveTo(n(t, i), n(t, i + 1)); i += 2; }
                else if (c == 'L') { p.lineTo(n(t, i), n(t, i + 1)); i += 2; }
                else if (c == 'Q') { p.quadTo(n(t, i), n(t, i + 1), n(t, i + 2), n(t, i + 3)); i += 4; }
                else if (c == 'C') { p.cubicTo(n(t, i), n(t, i + 1), n(t, i + 2), n(t, i + 3), n(t, i + 4), n(t, i + 5)); i += 6; }
                else if (c == 'Z') { p.close(); }
            } catch (Exception x) {
                break;
            }
        }
        return p;
    }

    private static float n(List<String> t, int i) {
        return Float.parseFloat(t.get(i));
    }
}
