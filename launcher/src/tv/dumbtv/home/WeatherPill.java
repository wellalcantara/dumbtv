package tv.dumbtv.home;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * The weather pill under the rows: sky icon, temperature, condition with today's high (or low
 * at night), then the next three hours. Its gradient follows the sky. Drawn once per forecast,
 * nothing animates, nothing is focusable.
 */
final class WeatherPill extends LinearLayout {
    private static final int WRAP = LayoutParams.WRAP_CONTENT;

    private final Icon icon;
    private final TextView temp, line;
    private final LinearLayout hours;
    private final View divider;

    WeatherPill(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(10), 0, dp(24), 0);
        setMinimumHeight(dp(78));
        setFocusable(false);

        icon = new Icon(c);
        addView(icon, new LayoutParams(dp(58), dp(58)));

        LinearLayout text = new LinearLayout(c);
        text.setOrientation(VERTICAL);
        temp = Ui.text(c, 42, Ui.TEXT, Ui.outfit(c, 300));
        temp.setLetterSpacing(-0.03f);
        line = Ui.text(c, 10.5f, 0xE0FFFFFF, Ui.outfit(c, 400));
        line.setPadding(dp(2), dp(3), 0, 0);
        text.addView(temp);
        text.addView(line);
        LayoutParams tp = new LayoutParams(WRAP, WRAP);
        tp.leftMargin = dp(12);
        addView(text, tp);

        divider = new View(c);
        divider.setBackgroundColor(0x38FFFFFF);
        LayoutParams dp1 = new LayoutParams(Math.max(1, dp(1)), dp(46));
        dp1.leftMargin = dp(16);
        dp1.rightMargin = dp(16);
        addView(divider, dp1);

        hours = new LinearLayout(c);
        hours.setOrientation(HORIZONTAL);
        addView(hours, new LayoutParams(WRAP, WRAP));
    }

    void show(Weather w, boolean h24) {
        Context c = getContext();
        int[] g = gradient(w.sky);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, g);
        bg.setCornerRadius(dp(39));
        setBackground(bg);
        icon.set(w.sky);
        temp.setText(c.getString(R.string.wx_temp, w.temp));
        line.setText(w.condition(c) + "  ·  "
                + c.getString(w.day ? R.string.wx_max : R.string.wx_min, w.day ? w.max : w.min));

        hours.removeAllViews();
        for (Weather.Hour h : w.next) {
            LinearLayout col = new LinearLayout(c);
            col.setOrientation(VERTICAL);
            col.setGravity(Gravity.CENTER_HORIZONTAL);
            TextView label = Ui.text(c, 9.5f, 0xD9FFFFFF, Ui.outfit(c, 400));
            label.setText(h24 ? c.getString(R.string.wx_hour_24, h.hour)
                    : c.getString(R.string.wx_hour_12, h.hour % 12 == 0 ? 12 : h.hour % 12,
                            h.hour < 12 ? "AM" : "PM"));
            Icon small = new Icon(c);
            small.set(h.sky);
            TextView t = Ui.text(c, 12.5f, Ui.TEXT, Ui.outfit(c, 400));
            t.setText(c.getString(R.string.wx_temp, h.temp));
            col.addView(label);
            LayoutParams ip = new LayoutParams(dp(22), dp(22));
            ip.topMargin = dp(4);
            ip.bottomMargin = dp(4);
            col.addView(small, ip);
            col.addView(t);
            LayoutParams cp = new LayoutParams(WRAP, WRAP);
            if (hours.getChildCount() > 0) cp.leftMargin = dp(16);
            hours.addView(col, cp);
        }
        int gone = w.next.isEmpty() ? GONE : VISIBLE;
        divider.setVisibility(gone);
        hours.setVisibility(gone);
    }

    /** Light top-left to dark bottom-right, by sky; day skies warm, night ones deep blue. */
    private static int[] gradient(Weather.Sky sky) {
        switch (sky) {
            case SUN: return new int[] {0xFFF29A3A, 0xFFD8622B, 0xFF5A2C2A};
            case SUN_CLOUD: return new int[] {0xFFE8A24E, 0xFFB86A3C, 0xFF4A3330};
            case MOON: return new int[] {0xFF3B3F7A, 0xFF22264A, 0xFF161A2C};
            case MOON_CLOUD: return new int[] {0xFF3A4066, 0xFF262B45, 0xFF171A28};
            case CLOUD: return new int[] {0xFF7C8EA3, 0xFF4B5A6D, 0xFF262D38};
            case FOG: return new int[] {0xFF8A8F96, 0xFF5A6068, 0xFF2C3036};
            case RAIN: return new int[] {0xFF3D7F96, 0xFF24506A, 0xFF182634};
            case SNOW: return new int[] {0xFF9FB7D1, 0xFF5F7C9E, 0xFF2C3A4D};
            default: return new int[] {0xFF4A3F6B, 0xFF2B2645, 0xFF17142A};
        }
    }

    private int dp(float v) {
        return Ui.dp(getContext(), v);
    }

    /** A sky drawn in code: shaded discs and clouds, no bitmaps. */
    static final class Icon extends View {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private Weather.Sky sky = Weather.Sky.SUN;

        Icon(Context c) {
            super(c);
            setFocusable(false);
        }

        void set(Weather.Sky s) {
            sky = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            switch (sky) {
                case SUN: sun(canvas, s * 0.5f, s * 0.5f, s * 0.5f, true); break;
                case MOON: moon(canvas, s * 0.5f, s * 0.5f, s * 0.5f, true); break;
                case SUN_CLOUD:
                    sun(canvas, s * 0.38f, s * 0.36f, s * 0.36f, false);
                    cloud(canvas, s * 0.18f, s * 0.36f, s * 0.8f, s * 0.5f, 0xFFFFFFFF, 0xFFC9D3DC);
                    break;
                case MOON_CLOUD:
                    moon(canvas, s * 0.38f, s * 0.36f, s * 0.36f, false);
                    cloud(canvas, s * 0.18f, s * 0.36f, s * 0.8f, s * 0.5f, 0xFFD6DAF0, 0xFF9AA0C8);
                    break;
                case CLOUD: cloud(canvas, s * 0.04f, s * 0.16f, s * 0.92f, s * 0.66f, 0xFFFFFFFF, 0xFFB9C8D4); break;
                case FOG: fog(canvas, s); break;
                case SNOW:
                    cloud(canvas, s * 0.06f, s * 0.04f, s * 0.88f, s * 0.6f, 0xFFFFFFFF, 0xFFC4D2DE);
                    flakes(canvas, s);
                    break;
                case STORM:
                    cloud(canvas, s * 0.06f, s * 0.04f, s * 0.88f, s * 0.6f, 0xFFD5DCE3, 0xFF8D99A6);
                    bolt(canvas, s);
                    break;
                default:
                    cloud(canvas, s * 0.06f, s * 0.04f, s * 0.88f, s * 0.6f, 0xFFFFFFFF, 0xFFB9C8D4);
                    drops(canvas, s);
            }
        }

        private void sun(Canvas c, float cx, float cy, float r, boolean halo) {
            if (halo) {
                paint.setShader(new RadialGradient(cx, cy, r,
                        new int[] {0x73FFD25A, 0x73FFD25A, 0x00FFD25A}, new float[] {0f, 0.55f, 1f},
                        Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, paint);
            }
            float d = halo ? r * 0.62f : r * 0.8f;
            paint.setShader(new RadialGradient(cx - d * 0.3f, cy - d * 0.35f, d * 1.4f,
                    new int[] {0xFFFFF6C8, 0xFFFFD25A, 0xFFF08A1C}, new float[] {0f, 0.45f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawCircle(cx, cy, d, paint);
            paint.setShader(null);
            paint.setColor(0x8CFFFFFF);
            c.drawOval(new RectF(cx - d * 0.62f, cy - d * 0.58f, cx - d * 0.0f, cy - d * 0.2f), paint);
        }

        private void moon(Canvas c, float cx, float cy, float r, boolean glow) {
            if (glow) {
                paint.setShader(new RadialGradient(cx, cy, r,
                        new int[] {0x59B9B2FF, 0x59B9B2FF, 0x00B9B2FF}, new float[] {0f, 0.5f, 1f},
                        Shader.TileMode.CLAMP));
                c.drawCircle(cx, cy, r, paint);
            }
            float d = glow ? r * 0.62f : r * 0.8f;
            Path disc = new Path();
            disc.addCircle(cx, cy, d, Path.Direction.CW);
            Path bite = new Path();
            bite.addCircle(cx + d * 0.55f, cy - d * 0.42f, d * 0.82f, Path.Direction.CW);
            disc.op(bite, Path.Op.DIFFERENCE);
            paint.setShader(new RadialGradient(cx - d * 0.4f, cy - d * 0.3f, d * 1.5f,
                    new int[] {0xFFFFFFFF, 0xFFE6E1FF, 0xFFA9A2E6}, new float[] {0f, 0.6f, 1f},
                    Shader.TileMode.CLAMP));
            c.drawPath(disc, paint);
            paint.setShader(null);
            if (glow) {
                paint.setColor(0xFFFFFFFF);
                c.drawCircle(cx + r * 0.62f, cy - r * 0.52f, r * 0.04f, paint);
                paint.setColor(0xCCFFFFFF);
                c.drawCircle(cx + r * 0.42f, cy - r * 0.74f, r * 0.026f, paint);
            }
        }

        /** A cloud filling the box: three puffs on a rounded base, lit from above. */
        private void cloud(Canvas c, float x, float y, float w, float h, int top, int bottom) {
            Path p = new Path();
            p.addCircle(x + w * 0.32f, y + h * 0.58f, w * 0.2f, Path.Direction.CW);
            Path puff = new Path();
            puff.addCircle(x + w * 0.56f, y + h * 0.42f, w * 0.26f, Path.Direction.CW);
            p.op(puff, Path.Op.UNION);
            Path right = new Path();
            right.addCircle(x + w * 0.78f, y + h * 0.62f, w * 0.17f, Path.Direction.CW);
            p.op(right, Path.Op.UNION);
            Path base = new Path();
            base.addRoundRect(new RectF(x + w * 0.1f, y + h * 0.6f, x + w * 0.92f, y + h * 0.96f),
                    h * 0.18f, h * 0.18f, Path.Direction.CW);
            p.op(base, Path.Op.UNION);
            paint.setShader(new LinearGradient(0, y, 0, y + h, top, bottom, Shader.TileMode.CLAMP));
            c.drawPath(p, paint);
            paint.setShader(null);
        }

        private void drops(Canvas c, float s) {
            paint.setShader(new LinearGradient(0, s * 0.68f, 0, s * 0.96f, 0xFFBFF0FF, 0xFF4FB6E0,
                    Shader.TileMode.CLAMP));
            for (float x : new float[] {0.32f, 0.5f, 0.68f}) {
                float cx = s * x, top = s * (x == 0.5f ? 0.72f : 0.68f);
                Path d = new Path();
                d.moveTo(cx, top);
                d.quadTo(cx - s * 0.06f, top + s * 0.12f, cx, top + s * 0.17f);
                d.quadTo(cx + s * 0.06f, top + s * 0.12f, cx, top);
                c.drawPath(d, paint);
            }
            paint.setShader(null);
        }

        private void flakes(Canvas c, float s) {
            paint.setColor(0xFFFFFFFF);
            for (float[] f : new float[][] {{0.32f, 0.76f}, {0.5f, 0.88f}, {0.68f, 0.76f}}) {
                c.drawCircle(s * f[0], s * f[1], s * 0.045f, paint);
            }
        }

        private void bolt(Canvas c, float s) {
            Path b = new Path();
            b.moveTo(s * 0.54f, s * 0.6f);
            b.lineTo(s * 0.4f, s * 0.8f);
            b.lineTo(s * 0.5f, s * 0.8f);
            b.lineTo(s * 0.44f, s * 0.98f);
            b.lineTo(s * 0.64f, s * 0.74f);
            b.lineTo(s * 0.53f, s * 0.74f);
            b.close();
            paint.setColor(0xFFFFD25A);
            c.drawPath(b, paint);
        }

        private void fog(Canvas c, float s) {
            paint.setColor(0xD9FFFFFF);
            float[][] bars = {{0.14f, 0.34f, 0.86f}, {0.24f, 0.52f, 0.92f}, {0.1f, 0.7f, 0.76f}};
            for (float[] b : bars) {
                c.drawRoundRect(new RectF(s * b[0], s * b[1] - s * 0.05f, s * b[2], s * b[1] + s * 0.05f),
                        s * 0.05f, s * 0.05f, paint);
            }
        }
    }
}
