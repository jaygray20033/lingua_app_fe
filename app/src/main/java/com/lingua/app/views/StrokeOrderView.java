package com.lingua.app.views;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PathMeasure;
import android.os.Handler;
import android.os.Looper;
import android.util.AttributeSet;
import android.view.View;

import java.util.ArrayList;
import java.util.List;

/**
 * 3.1 FIX: A lightweight Kanji / Hán-tự stroke-order visualization view.
 *
 * - Accepts an array of SVG path data strings (KanjiVG-like) via {@link #setStrokes(List)}.
 * - Animates strokes one by one in order, showing previously drawn strokes in
 *   black and the currently-drawing stroke in red.
 * - Drawn at a normalized 109×109 viewport (KanjiVG convention) then scaled to
 *   the view's width/height.
 *
 * Implemented with Canvas + a per-frame Handler so we avoid adding any extra
 * dependency. If the path data is missing/empty the view simply shows the raw
 * character text passed via {@link #setCharacter(String)} as a fallback.
 */
public class StrokeOrderView extends View {

    /** KanjiVG canonical canvas size. */
    private static final float VB_SIZE = 109f;

    /** Per-step duration in ms (one stroke). */
    private static final long STEP_MS = 700L;
    /** Frame interval (≈60fps). */
    private static final long FRAME_MS = 16L;

    private final Paint paintActive = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDone = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintGuide = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintText = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final List<Path> strokes = new ArrayList<>();
    private final List<Float> strokeLengths = new ArrayList<>();

    private int currentStrokeIndex = 0;
    private long strokeStartTs = 0L;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private boolean animating = false;
    private String fallbackChar = "";

    public StrokeOrderView(Context context) { super(context); init(); }
    public StrokeOrderView(Context context, AttributeSet attrs) { super(context, attrs); init(); }
    public StrokeOrderView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr); init();
    }

    private void init() {
        paintActive.setStyle(Paint.Style.STROKE);
        paintActive.setColor(0xFFE53935); // red — currently drawing
        paintActive.setStrokeWidth(6f);
        paintActive.setStrokeCap(Paint.Cap.ROUND);
        paintActive.setStrokeJoin(Paint.Join.ROUND);

        paintDone.setStyle(Paint.Style.STROKE);
        paintDone.setColor(0xFF222222); // black — done strokes
        paintDone.setStrokeWidth(5f);
        paintDone.setStrokeCap(Paint.Cap.ROUND);
        paintDone.setStrokeJoin(Paint.Join.ROUND);

        paintGuide.setStyle(Paint.Style.STROKE);
        paintGuide.setColor(0x33000000);
        paintGuide.setStrokeWidth(1f);

        paintText.setColor(0xFF555555);
        paintText.setTextSize(120f);
        paintText.setTextAlign(Paint.Align.CENTER);
    }

    /** Set raw character (used as fallback when no SVG strokes are available). */
    public void setCharacter(String ch) {
        this.fallbackChar = ch == null ? "" : ch;
        invalidate();
    }

    /**
     * Set a list of SVG path-data strings (e.g. "M22,30 C30,42 50,55 80,60").
     * Replays the animation from stroke #0.
     */
    public void setStrokes(List<String> svgPathDataList) {
        strokes.clear();
        strokeLengths.clear();
        if (svgPathDataList != null) {
            for (String d : svgPathDataList) {
                Path p = SvgPathParser.parse(d);
                if (p == null) continue;
                strokes.add(p);
                PathMeasure pm = new PathMeasure(p, false);
                strokeLengths.add(pm.getLength());
            }
        }
        currentStrokeIndex = 0;
        startAnimation();
        invalidate();
    }

    public void startAnimation() {
        stopAnimation();
        if (strokes.isEmpty()) return;
        animating = true;
        currentStrokeIndex = 0;
        strokeStartTs = System.currentTimeMillis();
        handler.post(ticker);
    }

    public void stopAnimation() {
        animating = false;
        handler.removeCallbacks(ticker);
    }

    private final Runnable ticker = new Runnable() {
        @Override public void run() {
            long now = System.currentTimeMillis();
            if (now - strokeStartTs >= STEP_MS) {
                currentStrokeIndex++;
                if (currentStrokeIndex >= strokes.size()) {
                    // loop after a small pause
                    handler.postDelayed(() -> { currentStrokeIndex = 0; strokeStartTs = System.currentTimeMillis(); invalidate(); handler.postDelayed(this, FRAME_MS); }, 1200);
                    invalidate();
                    return;
                }
                strokeStartTs = now;
            }
            invalidate();
            if (animating) handler.postDelayed(this, FRAME_MS);
        }
    };

    @Override
    protected void onDetachedFromWindow() {
        super.onDetachedFromWindow();
        stopAnimation();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int w = getWidth();
        int h = getHeight();
        if (w == 0 || h == 0) return;

        // Grid guide (4 quadrants)
        canvas.drawLine(w / 2f, 0, w / 2f, h, paintGuide);
        canvas.drawLine(0, h / 2f, w, h / 2f, paintGuide);

        if (strokes.isEmpty()) {
            if (!fallbackChar.isEmpty()) {
                Paint.FontMetrics fm = paintText.getFontMetrics();
                float baseline = h / 2f - (fm.ascent + fm.descent) / 2f;
                canvas.drawText(fallbackChar, w / 2f, baseline, paintText);
            }
            return;
        }

        canvas.save();
        canvas.scale(w / VB_SIZE, h / VB_SIZE);

        // Already-drawn strokes
        for (int i = 0; i < Math.min(currentStrokeIndex, strokes.size()); i++) {
            canvas.drawPath(strokes.get(i), paintDone);
        }

        // Active stroke — partial reveal
        if (currentStrokeIndex < strokes.size()) {
            long elapsed = System.currentTimeMillis() - strokeStartTs;
            float t = Math.min(1f, elapsed / (float) STEP_MS);
            Path partial = new Path();
            PathMeasure pm = new PathMeasure(strokes.get(currentStrokeIndex), false);
            pm.getSegment(0, pm.getLength() * t, partial, true);
            canvas.drawPath(partial, paintActive);
        }
        canvas.restore();
    }

    /**
     * Minimal SVG path "d" parser. Supports M, m, L, l, C, c, Q, q, Z/z commands
     * — enough for KanjiVG data. Not a full SVG implementation.
     */
    static class SvgPathParser {
        static Path parse(String d) {
            if (d == null || d.isEmpty()) return null;
            Path path = new Path();
            float cx = 0, cy = 0;
            float startX = 0, startY = 0;
            int i = 0;
            char cmd = 0;
            try {
                while (i < d.length()) {
                    while (i < d.length() && d.charAt(i) == ' ') i++;
                    if (i >= d.length()) break;
                    char c = d.charAt(i);
                    if (Character.isLetter(c)) { cmd = c; i++; continue; }
                    float[] args;
                    switch (cmd) {
                        case 'M':
                            args = readNums(d, i, 2); i = (int) args[args.length - 1];
                            cx = args[0]; cy = args[1];
                            path.moveTo(cx, cy);
                            startX = cx; startY = cy;
                            cmd = 'L'; // subsequent pairs are implicit lineto
                            break;
                        case 'm':
                            args = readNums(d, i, 2); i = (int) args[args.length - 1];
                            cx += args[0]; cy += args[1];
                            path.moveTo(cx, cy);
                            startX = cx; startY = cy;
                            cmd = 'l';
                            break;
                        case 'L':
                            args = readNums(d, i, 2); i = (int) args[args.length - 1];
                            cx = args[0]; cy = args[1];
                            path.lineTo(cx, cy);
                            break;
                        case 'l':
                            args = readNums(d, i, 2); i = (int) args[args.length - 1];
                            cx += args[0]; cy += args[1];
                            path.lineTo(cx, cy);
                            break;
                        case 'C':
                            args = readNums(d, i, 6); i = (int) args[args.length - 1];
                            path.cubicTo(args[0], args[1], args[2], args[3], args[4], args[5]);
                            cx = args[4]; cy = args[5];
                            break;
                        case 'c':
                            args = readNums(d, i, 6); i = (int) args[args.length - 1];
                            path.cubicTo(cx + args[0], cy + args[1],
                                    cx + args[2], cy + args[3],
                                    cx + args[4], cy + args[5]);
                            cx += args[4]; cy += args[5];
                            break;
                        case 'Q':
                            args = readNums(d, i, 4); i = (int) args[args.length - 1];
                            path.quadTo(args[0], args[1], args[2], args[3]);
                            cx = args[2]; cy = args[3];
                            break;
                        case 'q':
                            args = readNums(d, i, 4); i = (int) args[args.length - 1];
                            path.quadTo(cx + args[0], cy + args[1],
                                    cx + args[2], cy + args[3]);
                            cx += args[2]; cy += args[3];
                            break;
                        case 'Z':
                        case 'z':
                            path.lineTo(startX, startY);
                            cx = startX; cy = startY;
                            i++;
                            break;
                        default:
                            i++; // skip unknown
                    }
                }
            } catch (Exception e) {
                return null;
            }
            return path;
        }

        /** Reads `count` numbers starting at idx, returns array [n1, n2, ..., newIdx]. */
        static float[] readNums(String s, int idx, int count) {
            float[] out = new float[count + 1];
            int i = idx;
            for (int k = 0; k < count; k++) {
                while (i < s.length() && (s.charAt(i) == ' ' || s.charAt(i) == ',')) i++;
                int start = i;
                if (i < s.length() && (s.charAt(i) == '-' || s.charAt(i) == '+')) i++;
                while (i < s.length() && (Character.isDigit(s.charAt(i)) || s.charAt(i) == '.' || s.charAt(i) == 'e' || s.charAt(i) == 'E')) i++;
                if (start == i) { out[k] = 0; }
                else { out[k] = Float.parseFloat(s.substring(start, i)); }
            }
            out[count] = i;
            return out;
        }
    }
}
