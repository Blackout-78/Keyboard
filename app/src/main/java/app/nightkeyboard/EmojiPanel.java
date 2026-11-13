package app.nightkeyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.MotionEvent;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

/** Emoji picker with search. Data is bundled in the app (Emojibase, MIT). No history, no network. */
final class EmojiPanel {
    interface Out {
        void insert(String s);
        void del();
        void redraw();
    }

    private static boolean loaded;
    private static int n;
    private static String[] ch = new String[0], find = new String[0];
    private static int[] grp = new int[0], expr = new int[0];
    private static String[][] skins = new String[0][];
    private static final int[] CATS = {0, 1, 3, 4, 5, 6, 7, 8, 9};
    private static final String[] CAT_GLYPH = {"😀", "👋", "🐻", "🍔", "✈️", "⚽", "💡", "❤️", "🏁"};
    private static final int[] CAT_START = new int[CATS.length];
    private static final int COLS = 8;
    private static final int R_STRIP = 1, R_GRID = 2, R_BAR = 3, R_RES = 4, R_POP = 5;

    private final Context ctx;
    private final float dp;
    private final Out out;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Paint pE = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pT = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pL = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pF = new Paint(Paint.ANTI_ALIAS_FLAG);
    private int textColor = 0xFFFFFFFF, accent = 0xFF7C6CF0, hintColor = 0xFF9A9AB0, keyColor = 0xFF000000, keyAlpha = 200;
    private boolean skull = true;

    boolean active;
    boolean searching;
    private String query = "";
    private float scrollY, scrollX;
    private int[] res = new int[0];

    private int popIdx = -1;
    private String[] popItems;
    private final RectF popRect = new RectF();
    private float popCell;

    private boolean owns, dragging, longFired;
    private int region, hitIdx = -1, hitRow, hitCol;
    private float downX, downY, startScroll;
    private float lastTop, lastRowH, lastW;
    private int lastRows;

    EmojiPanel(Context c, float dp, Out out) {
        this.ctx = c;
        this.dp = dp;
        this.out = out;
        pE.setTextAlign(Paint.Align.CENTER);
        pT.setTextAlign(Paint.Align.CENTER);
        pL.setStyle(Paint.Style.STROKE);
        pL.setStrokeCap(Paint.Cap.ROUND);
        pL.setStrokeJoin(Paint.Join.ROUND);
        pL.setStrokeWidth(dp * 1.6f);
    }

    void setTheme(int text, int acc, int hint, int key, int kAlpha, boolean skullOn) {
        textColor = text;
        accent = acc;
        hintColor = hint;
        keyColor = key;
        keyAlpha = kAlpha;
        skull = skullOn;
    }

    private static void load(Context c) {
        if (loaded) return;
        List<String> lch = new ArrayList<>(), lfind = new ArrayList<>();
        List<int[]> lnum = new ArrayList<>();
        List<String[]> lsk = new ArrayList<>();
        Paint p = new Paint();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(c.getAssets().open("emoji.tsv"), "UTF-8"))) {
            String line;
            while ((line = r.readLine()) != null) {
                String[] a = line.split("\t", -1);
                if (a.length < 5) continue;
                if (!p.hasGlyph(a[2])) continue;
                lch.add(a[2]);
                lfind.add(a[3]);
                lnum.add(new int[]{Integer.parseInt(a[0]), Integer.parseInt(a[1])});
                lsk.add(a[4].isEmpty() ? null : a[4].split(" "));
            }
        } catch (Exception x) {
            return;
        }
        n = lch.size();
        ch = lch.toArray(new String[0]);
        find = lfind.toArray(new String[0]);
        grp = new int[n];
        expr = new int[n];
        skins = new String[n][];
        for (int i = 0; i < n; i++) {
            grp[i] = lnum.get(i)[0];
            expr[i] = lnum.get(i)[1];
            skins[i] = lsk.get(i);
        }
        int last = 0;
        for (int k = 0; k < CATS.length; k++) {
            int s = -1;
            for (int i = 0; i < n; i++) if (grp[i] == CATS[k]) { s = i; break; }
            if (s < 0) s = last;
            CAT_START[k] = s;
            last = s;
        }
        loaded = true;
    }

    void open() {
        load(ctx);
        active = true;
        searching = false;
        query = "";
        scrollY = 0;
        scrollX = 0;
        res = new int[0];
        popIdx = -1;
        out.redraw();
    }

    void close() {
        active = false;
        searching = false;
        query = "";
        res = new int[0];
        popIdx = -1;
        handler.removeCallbacks(longRun);
    }

    void type(String s) {
        query += s;
        if (query.length() > 40) query = query.substring(0, 40);
        refresh();
    }

    void backspace() {
        if (!query.isEmpty()) query = query.substring(0, query.length() - 1);
        refresh();
    }

    private void refresh() {
        scrollX = 0;
        String q = query.trim().toLowerCase();
        if (q.isEmpty()) { res = new int[0]; return; }
        String[] tok = q.split("\\s+");
        List<Integer> first = new ArrayList<>(), rest = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            boolean all = true;
            for (String t : tok) if (!find[i].contains(t)) { all = false; break; }
            if (!all) continue;
            if (find[i].startsWith(tok[0])) first.add(i); else rest.add(i);
        }
        first.addAll(rest);
        int m = Math.min(first.size(), 120);
        res = new int[m];
        for (int i = 0; i < m; i++) res[i] = first.get(i);
    }

    private void drawEntry(Canvas c, int idx, float cx, float cy, float cell) {
        if (skull && expr[idx] >= 0) {
            Bitmap b = SkullFaces.get(ctx, expr[idx], (int) (cell * 0.86f), textColor, accent);
            if (b != null) {
                c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, null);
                return;
            }
        }
        drawGlyph(c, ch[idx], cx, cy, cell);
    }

    private void drawGlyph(Canvas c, String s, float cx, float cy, float cell) {
        pE.setTextSize(cell * 0.58f);
        Paint.FontMetrics fm = pE.getFontMetrics();
        c.drawText(s, cx, cy - (fm.ascent + fm.descent) / 2f, pE);
    }

    void drawStrip(Canvas c, int w, float toolbarH, Typeface tf) {
        RectF p = new RectF(dp * 3, dp * 3, w - dp * 3, toolbarH - dp * 3);
        pF.setColor(keyColor);
        pF.setAlpha(keyAlpha);
        c.drawRoundRect(p, dp * 8, dp * 8, pF);
        pL.setColor(accent);
        c.drawRoundRect(p, dp * 8, dp * 8, pL);
        float cy = p.centerY();
        pL.setColor(hintColor);
        c.drawCircle(p.left + dp * 18, cy - dp * 1.5f, dp * 5, pL);
        c.drawLine(p.left + dp * 21.5f, cy + dp * 2, p.left + dp * 25, cy + dp * 5.5f, pL);
        pT.setTypeface(tf);
        pT.setTextSize(dp * 15);
        pT.setTextAlign(Paint.Align.LEFT);
        float x = p.left + dp * 34;
        float base = cy + dp * 5;
        if (query.isEmpty()) {
            pT.setColor(hintColor);
            c.drawText("Search emoji", x, base, pT);
        } else {
            pT.setColor(textColor);
            c.drawText(query, x, base, pT);
            x += pT.measureText(query) + dp * 2;
        }
        if (searching) {
            pL.setColor(accent);
            c.drawLine(x, cy - dp * 9, x, cy + dp * 9, pL);
        }
        pL.setColor(hintColor);
        float xc = p.right - dp * 22;
        c.drawLine(xc - dp * 5, cy - dp * 5, xc + dp * 5, cy + dp * 5, pL);
        c.drawLine(xc - dp * 5, cy + dp * 5, xc + dp * 5, cy - dp * 5, pL);
        pT.setTextAlign(Paint.Align.CENTER);
    }

    private void geom(float w, float top, float rowH, int rows) {
        lastW = w;
        lastTop = top;
        lastRowH = rowH;
        lastRows = rows;
    }

    void drawBody(Canvas c, int w, float top, float rowH, int rows, Typeface tf) {
        geom(w, top, rowH, rows);
        float cell = w / (float) COLS;
        float gridH = (rows - 1) * rowH;
        float barTop = top + gridH;
        float contentH = (float) Math.ceil(n / (double) COLS) * cell;
        float maxScroll = Math.max(0, contentH - gridH);
        if (scrollY > maxScroll) scrollY = maxScroll;
        if (scrollY < 0) scrollY = 0;
        c.save();
        c.clipRect(0, top, w, barTop);
        int first = (int) (scrollY / cell);
        int lastRow = (int) ((scrollY + gridH) / cell);
        for (int r = first; r <= lastRow; r++) {
            for (int col = 0; col < COLS; col++) {
                int idx = r * COLS + col;
                if (idx >= n) break;
                drawEntry(c, idx, col * cell + cell / 2f, top + r * cell - scrollY + cell / 2f, cell);
            }
        }
        c.restore();
        pL.setColor(hintColor);
        pL.setAlpha(90);
        c.drawLine(0, barTop, w, barTop, pL);
        pL.setAlpha(255);
        float abcW = dp * 50, delW = dp * 50;
        float catW = (w - abcW - delW) / CATS.length;
        float cy = barTop + rowH / 2f;
        pT.setTypeface(tf);
        pT.setColor(textColor);
        pT.setTextSize(dp * 14);
        pT.setTextAlign(Paint.Align.CENTER);
        c.drawText("ABC", abcW / 2f, cy + dp * 5, pT);
        int cur = 0;
        int topIdx = Math.min(n - 1, first * COLS);
        for (int k = 0; k < CATS.length; k++) if (topIdx >= CAT_START[k]) cur = k;
        for (int k = 0; k < CATS.length; k++) {
            float cx = abcW + k * catW + catW / 2f;
            float sz = Math.min(catW * 1.2f, dp * 30);
            if (k == 0 && skull) {
                Bitmap b = SkullFaces.get(ctx, 1, (int) (sz * 0.9f), textColor, accent);
                if (b != null) c.drawBitmap(b, cx - b.getWidth() / 2f, cy - b.getHeight() / 2f, null);
            } else {
                drawGlyph(c, CAT_GLYPH[k], cx, cy, sz * 1.1f);
            }
            if (k == cur) {
                pL.setColor(accent);
                c.drawLine(cx - catW * 0.35f, barTop + rowH - dp * 4, cx + catW * 0.35f, barTop + rowH - dp * 4, pL);
            }
        }
        pL.setColor(textColor);
        float dx = w - delW / 2f;
        Path pp = new Path();
        pp.moveTo(dx - dp * 11, cy);
        pp.lineTo(dx - dp * 5, cy - dp * 8);
        pp.lineTo(dx + dp * 11, cy - dp * 8);
        pp.lineTo(dx + dp * 11, cy + dp * 8);
        pp.lineTo(dx - dp * 5, cy + dp * 8);
        pp.close();
        c.drawPath(pp, pL);
        c.drawLine(dx - dp * 1, cy - dp * 3.5f, dx + dp * 6, cy + dp * 3.5f, pL);
        c.drawLine(dx - dp * 1, cy + dp * 3.5f, dx + dp * 6, cy - dp * 3.5f, pL);
        if (popIdx >= 0 && popItems != null) {
            pF.setColor(0xFF15151D);
            pF.setAlpha(255);
            c.drawRoundRect(popRect, dp * 8, dp * 8, pF);
            pL.setColor(accent);
            c.drawRoundRect(popRect, dp * 8, dp * 8, pL);
            for (int i = 0; i < popItems.length; i++) {
                drawGlyph(c, popItems[i], popRect.left + popCell * i + popCell / 2f, popRect.centerY(), popCell);
            }
        }
    }

    void drawResults(Canvas c, int w, float top, float rowH, Typeface tf) {
        geom(w, top, rowH, lastRows);
        RectF row = new RectF(dp * 3, top + dp * 3, w - dp * 3, top + rowH - dp * 3);
        pF.setColor(keyColor);
        pF.setAlpha(keyAlpha);
        c.drawRoundRect(row, dp * 8, dp * 8, pF);
        float cell = w / (float) COLS;
        float cy = top + rowH / 2f;
        if (res.length == 0) {
            pT.setTypeface(tf);
            pT.setTextSize(dp * 14);
            pT.setTextAlign(Paint.Align.LEFT);
            pT.setColor(hintColor);
            c.drawText(query.trim().isEmpty() ? "Type to search emoji" : "No match", dp * 14, cy + dp * 5, pT);
            pT.setTextAlign(Paint.Align.CENTER);
            return;
        }
        float maxScroll = Math.max(0, res.length * cell - w);
        if (scrollX > maxScroll) scrollX = maxScroll;
        if (scrollX < 0) scrollX = 0;
        c.save();
        c.clipRect(row);
        for (int i = 0; i < res.length; i++) {
            float cx = i * cell - scrollX + cell / 2f;
            if (cx < -cell || cx > w + cell) continue;
            drawEntry(c, res[i], cx, cy, Math.min(cell, rowH));
        }
        c.restore();
    }

    private final Runnable longRun = new Runnable() {
        @Override public void run() {
            if (hitIdx < 0 || skins[hitIdx] == null || region != R_GRID || dragging) return;
            Paint p = new Paint();
            List<String> items = new ArrayList<>();
            items.add(ch[hitIdx]);
            for (String s : skins[hitIdx]) if (p.hasGlyph(s)) items.add(s);
            if (items.size() < 2) return;
            popItems = items.toArray(new String[0]);
            popIdx = hitIdx;
            popCell = lastW / (float) COLS;
            float wdt = popItems.length * popCell;
            float left = Math.max(dp * 3, Math.min(hitCol * popCell, lastW - wdt - dp * 3));
            float cellTop = lastTop + hitRow * popCell - scrollY;
            float t = cellTop - popCell - dp * 4;
            if (t < lastTop) t = cellTop + popCell + dp * 4;
            popRect.set(left, t, left + wdt, t + popCell);
            longFired = true;
            out.redraw();
        }
    };

    private int idxAt(float x, float y) {
        float cell = lastW / (float) COLS;
        float barTop = lastTop + (lastRows - 1) * lastRowH;
        if (y < lastTop || y >= barTop) return -1;
        int row = (int) ((y - lastTop + scrollY) / cell);
        int col = (int) (x / cell);
        hitRow = row;
        hitCol = col;
        int idx = row * COLS + col;
        return (col < 0 || col >= COLS || idx < 0 || idx >= n) ? -1 : idx;
    }

    boolean touch(MotionEvent e, float top, float rowH, int rows, int w) {
        int a = e.getActionMasked();
        float x = e.getX(), y = e.getY();
        geom(w, top, rowH, rows);
        if (a == MotionEvent.ACTION_DOWN) {
            owns = false;
            dragging = false;
            longFired = false;
            hitIdx = -1;
            float bodyBottom = top + rows * rowH;
            if (popIdx >= 0) { owns = true; region = R_POP; }
            else if (y < top) { owns = true; region = R_STRIP; }
            else if (!searching && y < bodyBottom) { owns = true; region = y >= top + (rows - 1) * rowH ? R_BAR : R_GRID; }
            else if (searching && y < top + rowH) { owns = true; region = R_RES; }
            if (!owns) return false;
            downX = x;
            downY = y;
            startScroll = region == R_RES ? scrollX : scrollY;
            if (region == R_GRID) {
                hitIdx = idxAt(x, y);
                if (hitIdx >= 0 && skins[hitIdx] != null) handler.postDelayed(longRun, 380);
            }
            return true;
        }
        if (!owns) return false;
        if (a == MotionEvent.ACTION_MOVE) {
            if (region == R_GRID || region == R_RES) {
                float d = region == R_RES ? x - downX : y - downY;
                if (!dragging && Math.abs(d) > dp * 8) {
                    dragging = true;
                    handler.removeCallbacks(longRun);
                }
                if (dragging) {
                    if (region == R_RES) scrollX = startScroll - d; else scrollY = startScroll - d;
                    out.redraw();
                }
            }
            return true;
        }
        if (a == MotionEvent.ACTION_UP) {
            handler.removeCallbacks(longRun);
            if (!dragging && !longFired) tap(x, y);
            owns = false;
            return true;
        }
        if (a == MotionEvent.ACTION_CANCEL) {
            handler.removeCallbacks(longRun);
            owns = false;
        }
        return true;
    }

    private void tap(float x, float y) {
        float cell = lastW / (float) COLS;
        if (region == R_POP) {
            if (popRect.contains(x, y)) {
                int i = (int) ((x - popRect.left) / popCell);
                if (i >= 0 && i < popItems.length) out.insert(popItems[i]);
            }
            popIdx = -1;
        } else if (region == R_STRIP) {
            if (x > lastW - dp * 52) close(); else searching = true;
        } else if (region == R_GRID) {
            int idx = idxAt(x, y);
            if (idx >= 0) out.insert(ch[idx]);
        } else if (region == R_BAR) {
            float abcW = dp * 50, delW = dp * 50;
            if (x < abcW) close();
            else if (x > lastW - delW) out.del();
            else {
                float catW = (lastW - abcW - delW) / CATS.length;
                int k = Math.max(0, Math.min(CATS.length - 1, (int) ((x - abcW) / catW)));
                scrollY = (CAT_START[k] / COLS) * cell;
            }
        } else if (region == R_RES) {
            int i = (int) ((x + scrollX) / cell);
            if (i >= 0 && i < res.length) out.insert(ch[res[i]]);
        }
        out.redraw();
    }
}
