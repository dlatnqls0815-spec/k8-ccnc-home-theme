package io.github.k8home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.MotionEvent;
import android.view.View;

/**
 * Small square button at the corner of the stock turn-by-turn box: opens or closes the stock navigation's
 * guidance list, whose own button is hidden under the theme's navigation row. Drawn here (no image assets):
 * a dark rounded square with a thin outline; "list" lines with an arrow pointing to where the list goes.
 *
 * The window is 48 units wide whatever the screen scale is (48 * screenWidth / 1920), so the icon is laid
 * out on that 48 unit grid. Stroke widths are rounded to whole device pixels and the straight lines are put
 * on the pixel grid: a fractional stroke (2.6 * 0.75 = 1.95px) is drawn across two half lit pixel rows and
 * looks soft and uneven, and that happens at every scale.
 *
 * The arrow direction is our own guess. The stock list state cannot be read from outside the navi app, so
 * NavBarService flips it on every successful tap; see NavBarService.syncGuide and toggleGuide.
 */
final class GuideButton extends View {
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint line = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF box = new RectF();
    private boolean expanded, pressed;

    GuideButton(Context context) {
        super(context);
        line.setStyle(Paint.Style.STROKE);
        line.setStrokeCap(Paint.Cap.ROUND);
        line.setStrokeJoin(Paint.Join.ROUND);
        setContentDescription("길안내 목록 펼치기 또는 접기");
    }

    void setExpanded(boolean value) {
        if (expanded == value) return;
        expanded = value;
        invalidate();
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            pressed = true;
            invalidate();
        } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
            pressed = false;
            invalidate();
            if (action == MotionEvent.ACTION_UP) performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        return super.performClick();
    }

    @Override protected void onDraw(Canvas canvas) {
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        float u = unit(w, h);
        float outline = strokePx(1f, u);
        float inner = strokePx(2.6f, u);
        box.set(outline / 2, outline / 2, w - outline / 2, h - outline / 2);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(pressed ? 0xff3e4448 : 0xe6262b2e);
        canvas.drawRoundRect(box, 8 * u, 8 * u, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(outline);
        fill.setColor(0xff7d878c);
        canvas.drawRoundRect(box, 8 * u, 8 * u, fill);

        line.setStrokeWidth(inner);
        line.setColor(0xfff0f2f3);
        // Three list lines on the right half.
        float left = onGrid(22f, u, inner), right = onGrid(36f, u, inner);
        for (int i = 0; i < 3; i++) {
            float y = onGrid(16f + i * 8f, u, inner);
            canvas.drawLine(left, y, right, y, line);
        }
        // Arrow on the left: points left to open the list (it slides in from the side), right to close it.
        float cy = onGrid(24f, u, inner);
        float tip = Math.round((expanded ? 17f : 10f) * u);
        float back = Math.round((expanded ? 10f : 17f) * u);
        float arm = Math.round(6f * u);
        canvas.drawLine(tip, cy, back, cy - arm, line);
        canvas.drawLine(tip, cy, back, cy + arm, line);
    }

    /** One logical unit (the button is a 48 unit square) in device pixels. */
    private static float unit(float w, float h) {
        return Math.min(w, h) / 48f;
    }

    /** A logical stroke width as whole device pixels, never thinner than one pixel. */
    private static float strokePx(float logical, float unit) {
        return Math.max(1f, Math.round(logical * unit));
    }

    /**
     * Where a straight line goes in device pixels: on the pixel grid, so a stroke of an even width covers
     * whole pixel rows and a stroke of an odd width covers whole rows between two half pixel rows.
     */
    private static float onGrid(float logical, float unit, float stroke) {
        float px = logical * unit;
        return stroke % 2f == 0f ? Math.round(px) : Math.round(px - 0.5f) + 0.5f;
    }
}
