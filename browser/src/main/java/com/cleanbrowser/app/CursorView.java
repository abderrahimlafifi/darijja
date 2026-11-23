package com.cleanbrowser.app;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.os.SystemClock;
import android.util.AttributeSet;
import android.view.View;

/**
 * Mouse pointer for TV remotes. The arrow keys move it (it speeds up while a key
 * is held), and the activity turns the OK key into a touch at its position.
 * It draws only, it never takes touches itself.
 */
public class CursorView extends View {

    public static final int LEFT = 1, RIGHT = 2, UP = 4, DOWN = 8;

    public interface Listener {
        /** Scroll the page under (x, y) by (dx, dy) pixels. Return false if it cannot scroll that way. */
        boolean onEdgeScroll(float x, float y, int dx, int dy);

        /** The pointer is at the top, the page is at its top and the user keeps pressing up. */
        void onLeaveTop();

        /** The pointer moved while the OK key is held, for dragging. */
        void onDrag(float x, float y);
    }

    private static final long HIDE_AFTER_MS = 6000;
    private static final long LEAVE_TOP_HOLD_MS = 450;

    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadow = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path arrow = new Path();
    private final float density;

    private Listener listener;
    private float x = -1, y = -1;
    private int dirs;
    private long pressStart, lastFrame, lastActivity, topHoldStart;
    private boolean startedAtTop;
    private boolean active;
    private boolean dragging;
    private float speedFactor = 1f;

    public CursorView(Context c) {
        this(c, null);
    }

    public CursorView(Context c, AttributeSet a) {
        super(c, a);
        density = getResources().getDisplayMetrics().density;
        fill.setColor(Color.WHITE);
        fill.setStyle(Paint.Style.FILL);
        stroke.setColor(0xFF1B1F27);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(2f * density);
        stroke.setStrokeJoin(Paint.Join.ROUND);
        shadow.setColor(0x66000000);
        shadow.setStyle(Paint.Style.FILL);
        float s = density * 1.25f;
        // Classic arrow pointer, tip at (0, 0).
        arrow.moveTo(0, 0);
        arrow.lineTo(0, 20 * s);
        arrow.lineTo(5 * s, 15.5f * s);
        arrow.lineTo(8.5f * s, 23 * s);
        arrow.lineTo(11.5f * s, 21.6f * s);
        arrow.lineTo(8f * s, 14.3f * s);
        arrow.lineTo(14.5f * s, 14.3f * s);
        arrow.close();
        setClickable(false);
        setFocusable(false);
        setWillNotDraw(false);
    }

    public void setListener(Listener l) {
        listener = l;
    }

    /** 1 slow, 2 normal, 3 fast. */
    public void setSpeed(int level) {
        speedFactor = level <= 1 ? 0.65f : level >= 3 ? 1.45f : 1f;
    }

    public float getCursorX() {
        ensurePosition();
        return x;
    }

    public float getCursorY() {
        ensurePosition();
        return y;
    }

    public void setActive(boolean on) {
        active = on;
        if (!on) {
            dirs = 0;
            dragging = false;
        } else {
            wake();
        }
        invalidate();
    }

    public boolean isActive() {
        return active;
    }

    public void setDragging(boolean d) {
        dragging = d;
    }

    /** Shows the pointer again and restarts the hide timer. */
    public void wake() {
        lastActivity = SystemClock.uptimeMillis();
        removeCallbacks(hideCheck);
        postDelayed(hideCheck, HIDE_AFTER_MS + 50);
        invalidate();
    }

    private final Runnable hideCheck = this::invalidate;

    private boolean isShown_() {
        return active && SystemClock.uptimeMillis() - lastActivity < HIDE_AFTER_MS;
    }

    private void ensurePosition() {
        if (x < 0 || y < 0 || x >= getWidth() || y >= getHeight()) {
            if (getWidth() > 0) {
                x = getWidth() / 2f;
                y = getHeight() / 2f;
            } else {
                x = 0;
                y = 0;
            }
        }
    }

    @Override
    protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        if (x >= w || y >= h || x < 0 || y < 0) {
            x = w / 2f;
            y = h / 2f;
        }
    }

    public void press(int dir) {
        ensurePosition();
        boolean wasIdle = dirs == 0;
        dirs |= dir;
        if (wasIdle) {
            pressStart = SystemClock.uptimeMillis();
            lastFrame = pressStart;
            topHoldStart = 0;
            startedAtTop = (dir == UP) && y <= 1;
            // A small first step makes single presses precise.
            stepBy(1f / 60f);
            postOnAnimation(frame);
        }
        wake();
    }

    public void release(int dir) {
        dirs &= ~dir;
        if (dirs == 0) {
            removeCallbacks(frame);
        }
    }

    public void releaseAll() {
        dirs = 0;
        removeCallbacks(frame);
    }

    private final Runnable frame = new Runnable() {
        @Override
        public void run() {
            if (dirs == 0 || !active) return;
            long now = SystemClock.uptimeMillis();
            float dt = Math.min(0.05f, (now - lastFrame) / 1000f);
            lastFrame = now;
            stepBy(dt);
            postOnAnimation(this);
        }
    };

    private void stepBy(float dt) {
        float held = (SystemClock.uptimeMillis() - pressStart) / 1000f;
        // dp per second: starts slow for precision, speeds up while the key is held.
        float dpPerSec = held < 0.3f ? 160f : Math.min(1100f, 160f + (held - 0.3f) * 1000f);
        float step = dpPerSec * density * speedFactor * dt;
        float dx = 0, dy = 0;
        if ((dirs & LEFT) != 0) dx -= step;
        if ((dirs & RIGHT) != 0) dx += step;
        if ((dirs & UP) != 0) dy -= step;
        if ((dirs & DOWN) != 0) dy += step;

        float maxX = Math.max(0, getWidth() - 1);
        float maxY = Math.max(0, getHeight() - 1);
        float nx = clamp(x + dx, 0, maxX);
        float ny = clamp(y + dy, 0, maxY);

        // What could not be moved becomes a page scroll at the edges.
        int sx = Math.round((x + dx) - nx);
        int sy = Math.round((y + dy) - ny);
        x = nx;
        y = ny;
        lastActivity = SystemClock.uptimeMillis();
        invalidate();
        if (dragging && listener != null) listener.onDrag(x, y);

        if (listener != null && (sx != 0 || sy != 0)) {
            int scrollX = sx == 0 ? 0 : (int) Math.signum(sx) * Math.max(Math.abs(sx), (int) (4 * density));
            int scrollY = sy == 0 ? 0 : (int) Math.signum(sy) * Math.max(Math.abs(sy), (int) (4 * density));
            boolean scrolled = listener.onEdgeScroll(x, y, scrollX, scrollY);
            if (sy < 0 && !scrolled && (dirs & UP) != 0) {
                long now = SystemClock.uptimeMillis();
                if (topHoldStart == 0) topHoldStart = now;
                if (startedAtTop || now - topHoldStart >= LEAVE_TOP_HOLD_MS) {
                    releaseAll();
                    listener.onLeaveTop();
                }
            } else {
                topHoldStart = 0;
            }
        }
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (!isShown_()) return;
        ensurePosition();
        canvas.save();
        canvas.translate(x + 1.5f * density, y + 2f * density);
        canvas.drawPath(arrow, shadow);
        canvas.restore();
        canvas.save();
        canvas.translate(x, y);
        canvas.drawPath(arrow, fill);
        canvas.drawPath(arrow, stroke);
        canvas.restore();
    }
}
