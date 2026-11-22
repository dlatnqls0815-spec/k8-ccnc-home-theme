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
        float w = getWidth(), h = getHeight(), u = w / 48f;
        float stroke = Math.max(1f, u);
        box.set(stroke / 2, stroke / 2, w - stroke / 2, h - stroke / 2);
        fill.setStyle(Paint.Style.FILL);
        fill.setColor(pressed ? 0xff3e4448 : 0xe6262b2e);
        canvas.drawRoundRect(box, 8 * u, 8 * u, fill);
        fill.setStyle(Paint.Style.STROKE);
        fill.setStrokeWidth(stroke);
        fill.setColor(0xff7d878c);
        canvas.drawRoundRect(box, 8 * u, 8 * u, fill);

        line.setStrokeWidth(2.6f * u);
        line.setColor(0xfff0f2f3);
        // Three list lines on the right half.
        float left = 22 * u, right = 36 * u;
        for (int i = 0; i < 3; i++) {
            float y = (16 + i * 8) * u;
            canvas.drawLine(left, y, right, y, line);
        }
        // Arrow on the left: points left to open the list (it slides in from the side), right to close it.
        float cy = 24 * u, tip = expanded ? 17 * u : 10 * u, back = expanded ? 10 * u : 17 * u;
        canvas.drawLine(tip, cy, back, cy - 6 * u, line);
        canvas.drawLine(tip, cy, back, cy + 6 * u, line);
    }
}
