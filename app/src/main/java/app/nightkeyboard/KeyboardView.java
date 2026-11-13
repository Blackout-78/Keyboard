package app.nightkeyboard;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.Path;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.MotionEvent;
import android.view.View;
import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Draws the whole keyboard (toolbar + keys) on one canvas. No external libraries. */
public class KeyboardView extends View {
    public interface Listener {
        void onText(String s);
        void onKey(int code);
        void onAction(int toolbarId);
    }

    static final int DEL = -2, SHIFT = -1, SYM = -3, ENTER = -4, SPACE = -5, ABC = -6, EMOJI = -7, NONE = -9;
    static final int T_INC_L = 1, T_SELECT = 2, T_COPY = 3, T_PASTE = 4, T_CLIP = 5, T_MENU = 6, T_SETTINGS = 7, T_INC_R = 8;

    private static final class Key {
        final String label;
        final String hint;
        final float w;
        final int code;
        final RectF r = new RectF();

        Key(String label, String hint, float w, int code) {
            this.label = label;
            this.hint = hint;
            this.w = w;
            this.code = code;
        }
    }

    private static final int[] TOOLS = {T_INC_L, T_SELECT, T_COPY, T_PASTE, T_CLIP, T_MENU, T_SETTINGS, T_INC_R};

    private Listener listener;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final float dp;
    private final Paint pFill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pText = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pHint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pLine = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pStamp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint pBmp = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private int fit;

    private final List<List<Key>> rows = new ArrayList<>();
    private final RectF[] toolRects = new RectF[TOOLS.length];
    private final SparseArray<Key> pressedKeys = new SparseArray<>();
    private final SparseArray<Integer> pressedTools = new SparseArray<>();

    private boolean symbols;
    private int shift; // 0 off, 1 once, 2 caps lock
    private long lastShiftTap;
    private boolean incognito = true;

    private Bitmap bg;
    private int keyColor = 0xFF000000;
    private int keyAlpha = Cfg.DEFAULT_KEY_ALPHA;
    private int dim = Cfg.DEFAULT_DIM;
    private boolean hints = true;
    private final String stamp;

    private Key longKey;
    private boolean longFired;
    private final Runnable longRun = new Runnable() {
        @Override public void run() {
            if (longKey != null && longKey.hint != null && hints) {
                longFired = true;
                if (listener != null) listener.onText(longKey.hint);
                invalidate();
            }
        }
    };
    private Key repeatKey;
    private final Runnable repeatRun = new Runnable() {
        @Override public void run() {
            if (repeatKey != null && listener != null) {
                listener.onKey(DEL);
                handler.postDelayed(this, 45);
            }
        }
    };

    public KeyboardView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
        pLine.setStyle(Paint.Style.STROKE);
        pLine.setStrokeWidth(dp * 1.6f);
        pLine.setColor(Color.WHITE);
        pLine.setStrokeCap(Paint.Cap.ROUND);
        pHint.setTextSize(dp * 11);
        pHint.setTextAlign(Paint.Align.RIGHT);
        pStamp.setTextSize(dp * 10);
        pStamp.setColor(0x99FFFFFF);
        stamp = "v" + BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")";
        for (int i = 0; i < toolRects.length; i++) toolRects[i] = new RectF();
        reloadTheme();
        buildRows();
    }

    public void setListener(Listener l) { listener = l; }

    public void setSymbols(boolean on) {
        symbols = on;
        shift = 0;
        buildRows();
        layoutKeys(getWidth());
        invalidate();
    }

    /** Re-reads theme settings (image, colors, font). Called every time the keyboard opens. */
    public void reloadTheme() {
        Context c = getContext();
        keyAlpha = Cfg.keyAlpha(c);
        keyColor = Cfg.keyColor(c);
        dim = Cfg.dim(c);
        hints = Cfg.hints(c);
        fit = Cfg.fit(c);
        int tint = Cfg.tint(c);
        pBmp.setColorFilter(tint == 0 ? null : new PorterDuffColorFilter(tint, PorterDuff.Mode.MULTIPLY));
        Typeface tf = Typeface.DEFAULT;
        File ff = Cfg.fontFile(c);
        if (ff.exists()) {
            try { tf = Typeface.createFromFile(ff); } catch (Exception ignored) { tf = Typeface.DEFAULT; }
        }
        pText.setTypeface(tf);
        pHint.setTypeface(tf);
        pText.setTextAlign(Paint.Align.CENTER);
        pText.setColor(Color.WHITE);
        pHint.setColor(0xFF9A9AB0);
        if (bg != null) { bg.recycle(); bg = null; }
        File bf = Cfg.bgFile(c);
        if (bf.exists()) {
            try {
                BitmapFactory.Options o = new BitmapFactory.Options();
                o.inJustDecodeBounds = true;
                BitmapFactory.decodeFile(bf.getAbsolutePath(), o);
                int s = 1;
                while (o.outWidth / (s * 2) >= 1200) s *= 2;
                o = new BitmapFactory.Options();
                o.inSampleSize = s;
                bg = BitmapFactory.decodeFile(bf.getAbsolutePath(), o);
            } catch (Throwable ignored) { bg = null; }
        }
        invalidate();
    }

    private void buildRows() {
        rows.clear();
        if (!symbols) {
            rows.add(textRow("1234567890", null, 0f));
            rows.add(textRow("qwertyuiop", new String[]{"%", "\\", "|", "=", "[", "]", "<", ">", "{", "}"}, 0f));
            rows.add(textRow("asdfghjkl", new String[]{"@", "#", "$", "_", "&", "-", "+", "(", ")"}, 0f));
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key("^", null, 1.5f, SHIFT));
            String z = "zxcvbnm";
            String[] zh = {"*", "\"", "'", ":", ";", "!", "?"};
            for (int i = 0; i < z.length(); i++) r3.add(new Key(z.substring(i, i + 1), zh[i], 1f, 0));
            r3.add(new Key("<x", null, 1.5f, DEL));
            rows.add(r3);
        } else {
            rows.add(textRow("1234567890", null, 0f));
            rows.add(textRow("~`|^={}[]\\", null, 0f));
            rows.add(textRow("@#$_&-+()/", null, 0f));
            List<Key> r3 = new ArrayList<>();
            r3.add(new Key("%", null, 1.5f, 0));
            String z = "*\"':;!?";
            for (int i = 0; i < z.length(); i++) r3.add(new Key(z.substring(i, i + 1), null, 1f, 0));
            r3.add(new Key("<x", null, 1.5f, DEL));
            rows.add(r3);
        }
        List<Key> b = new ArrayList<>();
        b.add(new Key(symbols ? "ABC" : "?123", null, 1.5f, symbols ? ABC : SYM));
        b.add(new Key(",", null, 1f, 0));
        b.add(new Key(":)", null, 1f, EMOJI));
        b.add(new Key("Space", null, 4f, SPACE));
        b.add(new Key(".", null, 1f, 0));
        b.add(new Key("<-'", null, 1.5f, ENTER));
        rows.add(b);
    }

    private List<Key> textRow(String chars, String[] hs, float unused) {
        List<Key> r = new ArrayList<>();
        for (int i = 0; i < chars.length(); i++) {
            r.add(new Key(chars.substring(i, i + 1), hs == null ? null : hs[i], 1f, 0));
        }
        return r;
    }

    private float toolbarH() { return dp * 52; }
    private float rowH() { return dp * 52; }
    private float bottomPad() { return dp * 16; }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = (int) (toolbarH() + rowH() * 5 + bottomPad());
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        layoutKeys(w);
    }

    private void layoutKeys(int w) {
        if (w <= 0) return;
        float gap = dp * 3;
        float margin = dp * 3;
        float unit = (w - 2 * margin) / 10f;
        float tw = (float) w / TOOLS.length;
        for (int i = 0; i < TOOLS.length; i++) toolRects[i].set(i * tw, 0, (i + 1) * tw, toolbarH());
        float y = toolbarH();
        for (List<Key> row : rows) {
            float sum = 0;
            for (Key k : row) sum += k.w;
            float x = margin + (10f - sum) / 2f * unit;
            for (Key k : row) {
                k.r.set(x + gap / 2, y + gap / 2, x + k.w * unit - gap / 2, y + rowH() - gap / 2);
                x += k.w * unit;
            }
            y += rowH();
        }
    }

    @Override
    protected void onDraw(Canvas c) {
        int w = getWidth(), h = getHeight();
        c.drawColor(0xFF000000);
        if (bg != null) {
            Rect full = new Rect(0, 0, bg.getWidth(), bg.getHeight());
            if (fit == 0) {
                c.drawBitmap(bg, full, new Rect(0, 0, w, h), pBmp);
            } else if (fit == 2) {
                float sc = Math.min((float) w / bg.getWidth(), (float) h / bg.getHeight());
                int dw = (int) (bg.getWidth() * sc), dh = (int) (bg.getHeight() * sc);
                int dx = (w - dw) / 2, dy = (h - dh) / 2;
                c.drawBitmap(bg, full, new Rect(dx, dy, dx + dw, dy + dh), pBmp);
            } else {
                float br = (float) bg.getWidth() / bg.getHeight();
                float vr = (float) w / h;
                Rect src;
                if (br > vr) {
                    int sw = (int) (bg.getHeight() * vr);
                    int sx = (bg.getWidth() - sw) / 2;
                    src = new Rect(sx, 0, sx + sw, bg.getHeight());
                } else {
                    int sh = (int) (bg.getWidth() / vr);
                    int sy = (bg.getHeight() - sh) / 2;
                    src = new Rect(0, sy, bg.getWidth(), sy + sh);
                }
                c.drawBitmap(bg, src, new Rect(0, 0, w, h), pBmp);
            }
            c.drawColor(Color.argb(dim, 0, 0, 0));
        }
        for (int i = 0; i < TOOLS.length; i++) drawTool(c, TOOLS[i], toolRects[i]);
        float rad = dp * 8;
        for (List<Key> row : rows) {
            for (Key k : row) {
                boolean down = isPressed(k);
                int base = k.code == ENTER ? 0xFF7C6CF0 : keyColor;
                int a = down ? Math.min(255, keyAlpha + 55) : keyAlpha;
                pFill.setColor(down && k.code != ENTER ? 0xFF2A2A38 : base);
                pFill.setAlpha(a);
                c.drawRoundRect(k.r, rad, rad, pFill);
                drawKeyFace(c, k);
            }
        }
        c.drawText(stamp, dp * 8, h - dp * 4, pStamp);
    }

    private boolean isPressed(Key k) {
        for (int i = 0; i < pressedKeys.size(); i++) if (pressedKeys.valueAt(i) == k) return true;
        return false;
    }

    private void drawKeyFace(Canvas c, Key k) {
        float cx = k.r.centerX();
        float cy = k.r.centerY();
        pLine.setColor(Color.WHITE);
        if (k.code == SHIFT) {
            pText.setTextSize(dp * 20);
            drawCentered(c, shift == 2 ? "^^" : (shift == 1 ? "^." : "^"), cx, cy);
            return;
        }
        if (k.code == DEL) { drawBackspace(c, cx, cy); return; }
        if (k.code == ENTER) { drawEnter(c, cx, cy); return; }
        if (k.code == EMOJI) { drawSmiley(c, cx, cy); return; }
        boolean letter = k.code == 0 && k.label.length() == 1 && Character.isLetter(k.label.charAt(0));
        String t = (letter && shift != 0) ? k.label.toUpperCase() : k.label;
        if (k.code == SPACE) {
            pText.setTextSize(dp * 14);
            pText.setColor(0xFF9A9AB0);
            drawCentered(c, "Space", cx, cy);
            pText.setColor(Color.WHITE);
            return;
        }
        pText.setTextSize(k.code == 0 ? dp * 22 : dp * 15);
        drawCentered(c, t, cx, cy);
        if (hints && k.hint != null) c.drawText(k.hint, k.r.right - dp * 6, k.r.top + dp * 14, pHint);
    }

    private void drawCentered(Canvas c, String t, float cx, float cy) {
        Paint.FontMetrics fm = pText.getFontMetrics();
        c.drawText(t, cx, cy - (fm.ascent + fm.descent) / 2f, pText);
    }

    private void drawBackspace(Canvas c, float cx, float cy) {
        float w = dp * 13, h = dp * 9;
        Path p = new Path();
        p.moveTo(cx - w, cy);
        p.lineTo(cx - w * 0.4f, cy - h);
        p.lineTo(cx + w, cy - h);
        p.lineTo(cx + w, cy + h);
        p.lineTo(cx - w * 0.4f, cy + h);
        p.close();
        c.drawPath(p, pLine);
        c.drawLine(cx - dp * 2, cy - dp * 4, cx + dp * 5, cy + dp * 4, pLine);
        c.drawLine(cx + dp * 5, cy - dp * 4, cx - dp * 2, cy + dp * 4, pLine);
    }

    private void drawEnter(Canvas c, float cx, float cy) {
        pLine.setColor(0xFF0B0B10);
        Path p = new Path();
        p.moveTo(cx + dp * 9, cy - dp * 8);
        p.lineTo(cx + dp * 9, cy + dp * 2);
        p.lineTo(cx - dp * 9, cy + dp * 2);
        c.drawPath(p, pLine);
        c.drawLine(cx - dp * 9, cy + dp * 2, cx - dp * 3, cy - dp * 4, pLine);
        c.drawLine(cx - dp * 9, cy + dp * 2, cx - dp * 3, cy + dp * 8, pLine);
        pLine.setColor(Color.WHITE);
    }

    private void drawSmiley(Canvas c, float cx, float cy) {
        float r = dp * 11;
        c.drawCircle(cx, cy, r, pLine);
        c.drawPoint(cx - dp * 4, cy - dp * 3, pLine);
        c.drawPoint(cx + dp * 4, cy - dp * 3, pLine);
        RectF m = new RectF(cx - dp * 6, cy - dp * 4, cx + dp * 6, cy + dp * 6);
        c.drawArc(m, 20, 140, false, pLine);
    }

    private void drawTool(Canvas c, int id, RectF r) {
        float cx = r.centerX(), cy = r.centerY(), s = dp * 11;
        pLine.setColor(Color.WHITE);
        pLine.setPathEffect(null);
        pFill.setColor(Color.WHITE);
        pFill.setAlpha(255);
        boolean pressed = false;
        for (int i = 0; i < pressedTools.size(); i++) if (pressedTools.valueAt(i) == id) pressed = true;
        if (pressed) { pLine.setAlpha(140); pFill.setAlpha(140); }
        switch (id) {
            case T_INC_L:
            case T_INC_R:
                drawIncognito(c, cx, cy, s, id == T_INC_L);
                break;
            case T_SELECT:
                pLine.setPathEffect(new DashPathEffect(new float[]{dp * 3, dp * 3}, 0));
                c.drawRect(cx - s, cy - s, cx + s, cy + s, pLine);
                pLine.setPathEffect(null);
                break;
            case T_COPY:
                c.drawRoundRect(cx - s * 0.5f, cy - s, cx + s, cy + s * 0.6f, dp * 2, dp * 2, pLine);
                c.drawRoundRect(cx - s, cy - s * 0.6f, cx + s * 0.5f, cy + s, dp * 2, dp * 2, pLine);
                break;
            case T_PASTE:
                c.drawRoundRect(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s, dp * 2, dp * 2, pLine);
                c.drawRect(cx - s * 0.4f, cy - s, cx + s * 0.4f, cy - s * 0.5f, pFill);
                break;
            case T_CLIP:
                c.drawRoundRect(cx - s * 0.8f, cy - s * 0.8f, cx + s * 0.8f, cy + s, dp * 2, dp * 2, pLine);
                c.drawLine(cx - s * 0.4f, cy - s * 0.2f, cx + s * 0.4f, cy - s * 0.2f, pLine);
                c.drawLine(cx - s * 0.4f, cy + s * 0.3f, cx + s * 0.4f, cy + s * 0.3f, pLine);
                break;
            case T_MENU:
                c.drawLine(cx - s, cy - s * 0.6f, cx + s, cy - s * 0.6f, pLine);
                c.drawLine(cx - s * 0.6f, cy, cx + s, cy, pLine);
                c.drawLine(cx - s, cy + s * 0.6f, cx + s * 0.5f, cy + s * 0.6f, pLine);
                break;
            case T_SETTINGS:
                c.drawCircle(cx, cy, s * 0.45f, pLine);
                for (int i = 0; i < 8; i++) {
                    double a = Math.PI / 4 * i;
                    float ca = (float) Math.cos(a), sa = (float) Math.sin(a);
                    c.drawLine(cx + ca * s * 0.7f, cy + sa * s * 0.7f, cx + ca * s, cy + sa * s, pLine);
                }
                c.drawCircle(cx, cy, s * 0.7f, pLine);
                break;
            default:
                break;
        }
        pLine.setAlpha(255);
    }

    private void drawIncognito(Canvas c, float cx, float cy, float s, boolean left) {
        pFill.setColor(Color.WHITE);
        pFill.setAlpha(incognito ? 255 : 110);
        c.drawCircle(cx, cy, s * 1.25f, pFill);
        pLine.setColor(0xFF000000);
        c.drawCircle(cx - s * 0.4f, cy - s * 0.1f, s * 0.28f, pLine);
        c.drawCircle(cx + s * 0.4f, cy - s * 0.1f, s * 0.28f, pLine);
        float d = left ? -1 : 1;
        c.drawLine(cx + d * s * 0.2f, cy + s * 0.5f, cx - d * s * 0.2f, cy + s * 0.75f, pLine);
        c.drawLine(cx + d * s * 0.2f, cy + s * 0.5f, cx - d * s * 0.2f, cy + s * 0.25f, pLine);
        pLine.setColor(Color.WHITE);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        int idx = e.getActionIndex();
        int id = e.getPointerId(idx);
        switch (a) {
            case MotionEvent.ACTION_DOWN:
            case MotionEvent.ACTION_POINTER_DOWN:
                down(id, e.getX(idx), e.getY(idx));
                break;
            case MotionEvent.ACTION_UP:
            case MotionEvent.ACTION_POINTER_UP:
                up(id);
                break;
            case MotionEvent.ACTION_CANCEL:
                cancelAll();
                break;
            default:
                break;
        }
        return true;
    }

    private void down(int id, float x, float y) {
        if (y < toolbarH()) {
            for (int i = 0; i < TOOLS.length; i++) {
                if (toolRects[i].contains(x, y)) { pressedTools.put(id, TOOLS[i]); break; }
            }
            invalidate();
            return;
        }
        Key hit = null;
        for (List<Key> row : rows) {
            for (Key k : row) {
                RectF r = k.r;
                if (x >= r.left - dp * 1.5f && x <= r.right + dp * 1.5f && y >= r.top - dp * 1.5f && y <= r.bottom + dp * 1.5f) {
                    hit = k;
                }
            }
        }
        if (hit == null) return;
        pressedKeys.put(id, hit);
        if (hit.code == DEL) {
            if (listener != null) listener.onKey(DEL);
            repeatKey = hit;
            handler.postDelayed(repeatRun, 380);
        } else if (hit.hint != null && hints) {
            longKey = hit;
            longFired = false;
            handler.postDelayed(longRun, 380);
        }
        invalidate();
    }

    private void up(int id) {
        Integer tool = pressedTools.get(id);
        if (tool != null) {
            pressedTools.remove(id);
            if (tool == T_INC_L || tool == T_INC_R) incognito = !incognito;
            else if (listener != null) listener.onAction(tool);
            invalidate();
            return;
        }
        Key k = pressedKeys.get(id);
        if (k == null) return;
        pressedKeys.remove(id);
        if (k == repeatKey) {
            repeatKey = null;
            handler.removeCallbacks(repeatRun);
        } else {
            boolean fired = false;
            if (k == longKey) {
                handler.removeCallbacks(longRun);
                fired = longFired;
                longKey = null;
            }
            if (!fired) fire(k);
        }
        invalidate();
    }

    private void cancelAll() {
        handler.removeCallbacks(longRun);
        handler.removeCallbacks(repeatRun);
        repeatKey = null;
        longKey = null;
        pressedKeys.clear();
        pressedTools.clear();
        invalidate();
    }

    private void fire(Key k) {
        if (listener == null) return;
        switch (k.code) {
            case SHIFT: {
                long now = System.currentTimeMillis();
                if (shift == 2) shift = 0;
                else if (shift == 1 && now - lastShiftTap < 350) shift = 2;
                else shift = shift == 0 ? 1 : 0;
                lastShiftTap = now;
                break;
            }
            case SYM: setSymbols(true); break;
            case ABC: setSymbols(false); break;
            case ENTER: listener.onKey(ENTER); break;
            case SPACE: listener.onText(" "); break;
            case EMOJI: break;
            case 0: {
                boolean letter = k.label.length() == 1 && Character.isLetter(k.label.charAt(0));
                listener.onText(letter && shift != 0 ? k.label.toUpperCase() : k.label);
                if (shift == 1) shift = 0;
                break;
            }
            default: break;
        }
    }
}
