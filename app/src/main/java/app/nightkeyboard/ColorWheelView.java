package app.nightkeyboard;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.view.MotionEvent;
import android.view.View;

/** Hue and saturation wheel. Brightness is set separately with setValue(). */
public class ColorWheelView extends View {
    public interface Listener {
        void onColor(int color);
    }

    private final float[] hsv = {0f, 0f, 1f};
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final float dp;
    private Shader sweep;
    private Shader radial;
    private float cx, cy, r;
    private Listener listener;

    public ColorWheelView(Context c) {
        super(c);
        dp = c.getResources().getDisplayMetrics().density;
    }

    public void setListener(Listener l) { listener = l; }

    public void setColor(int color) {
        Color.colorToHSV(color, hsv);
        invalidate();
    }

    public void setValue(float v) {
        hsv[2] = Math.max(0f, Math.min(1f, v));
        invalidate();
        fire();
    }

    public int getColor() { return Color.HSVToColor(hsv); }

    private void fire() {
        if (listener != null) listener.onColor(getColor());
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = Math.min(MeasureSpec.getSize(wSpec), (int) (dp * 280));
        setMeasuredDimension(w, w);
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        cx = w / 2f;
        cy = h / 2f;
        r = Math.min(w, h) / 2f - dp * 10;
        sweep = new SweepGradient(cx, cy,
                new int[]{Color.RED, Color.YELLOW, Color.GREEN, Color.CYAN, Color.BLUE, Color.MAGENTA, Color.RED}, null);
        radial = new RadialGradient(cx, cy, r, Color.WHITE, 0x00FFFFFF, Shader.TileMode.CLAMP);
    }

    @Override
    protected void onDraw(Canvas c) {
        if (sweep == null) return;
        p.setStyle(Paint.Style.FILL);
        p.setShader(sweep);
        c.drawCircle(cx, cy, r, p);
        p.setShader(radial);
        c.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setColor(Color.BLACK);
        p.setAlpha((int) (255 * (1f - hsv[2])));
        c.drawCircle(cx, cy, r, p);
        double a = Math.toRadians(hsv[0]);
        float mx = cx + (float) Math.cos(a) * hsv[1] * r;
        float my = cy + (float) Math.sin(a) * hsv[1] * r;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(dp * 2.5f);
        p.setColor(Color.WHITE);
        p.setAlpha(255);
        c.drawCircle(mx, my, dp * 9, p);
        p.setStrokeWidth(dp * 1f);
        p.setColor(Color.BLACK);
        c.drawCircle(mx, my, dp * 11, p);
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN || a == MotionEvent.ACTION_MOVE) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float dx = e.getX() - cx;
            float dy = e.getY() - cy;
            float hue = (float) Math.toDegrees(Math.atan2(dy, dx));
            if (hue < 0) hue += 360f;
            hsv[0] = hue;
            hsv[1] = Math.min(1f, (float) Math.hypot(dx, dy) / r);
            invalidate();
            fire();
        }
        return true;
    }
}
