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
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Locale;

/**
 * The weather pill under the rows: sky, temperature, condition, today's high, low and chance of
 * rain, then the next four hours and the next three days, each with its chance of rain. Its
 * gradient follows the sky and its width follows its content. Drawn once per forecast, nothing
 * animates, nothing is focusable.
 */
final class WeatherPill extends LinearLayout {
    private static final int WRAP = LayoutParams.WRAP_CONTENT;
    private static final int RAIN = 0xFF9FDCFF, SOFT = 0xD9FFFFFF, DIM = 0xA6FFFFFF;

    private final Icon icon;
    private final TextView temp, condition, high, low, chance;
    private final LinearLayout rainBox, hours, days;
    private final View hoursRule, daysRule;

    WeatherPill(Context c) {
        super(c);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setPadding(dp(14), dp(8), dp(30), dp(8));
        setMinimumHeight(dp(94));
        setFocusable(false);

        icon = new Icon(c);
        addView(icon, new LayoutParams(dp(66), dp(66)));

        LinearLayout now = new LinearLayout(c);
        now.setOrientation(VERTICAL);
        LinearLayout top = new LinearLayout(c);
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.BOTTOM);
        temp = Ui.text(c, 52, Ui.TEXT, Ui.outfit(c, 300));
        temp.setLetterSpacing(-0.03f);
        condition = Ui.text(c, 13, Ui.TEXT, Ui.outfit(c, 400));
        condition.setPadding(dp(9), 0, 0, dp(7));
        top.addView(temp);
        top.addView(condition);
        now.addView(top, new LayoutParams(WRAP, WRAP));

        LinearLayout facts = new LinearLayout(c);
        facts.setOrientation(HORIZONTAL);
        facts.setGravity(Gravity.CENTER_VERTICAL);
        facts.setPadding(dp(2), dp(4), 0, 0);
        high = Ui.text(c, 11, SOFT, Ui.outfit(c, 400));
        low = Ui.text(c, 11, SOFT, Ui.outfit(c, 400));
        rainBox = new LinearLayout(c);
        rainBox.setGravity(Gravity.CENTER_VERTICAL);
        Icon drop = new Icon(c);
        drop.set(null);
        chance = Ui.text(c, 11, RAIN, Ui.outfit(c, 400));
        rainBox.addView(drop, new LayoutParams(dp(9), dp(11)));
        LayoutParams cp = new LayoutParams(WRAP, WRAP);
        cp.leftMargin = dp(3);
        rainBox.addView(chance, cp);
        facts.addView(high);
        facts.addView(low, gap(9));
        facts.addView(rainBox, gap(9));
        now.addView(facts, new LayoutParams(WRAP, WRAP));
        LayoutParams np = new LayoutParams(WRAP, WRAP);
        np.leftMargin = dp(14);
        addView(now, np);

        hoursRule = rule(c);
        hours = new LinearLayout(c);
        addView(hours, new LayoutParams(WRAP, WRAP));
        daysRule = rule(c);
        days = new LinearLayout(c);
        addView(days, new LayoutParams(WRAP, WRAP));
    }

    private View rule(Context c) {
        View v = new View(c);
        v.setBackgroundColor(0x38FFFFFF);
        LayoutParams lp = new LayoutParams(Math.max(1, dp(1)), dp(60));
        lp.leftMargin = dp(16);
        lp.rightMargin = dp(16);
        addView(v, lp);
        return v;
    }

    private LayoutParams gap(int left) {
        LayoutParams lp = new LayoutParams(WRAP, WRAP);
        lp.leftMargin = dp(left);
        return lp;
    }

    void show(Weather w, boolean h24) {
        Context c = getContext();
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.TL_BR, gradient(w.sky));
        bg.setCornerRadius(dp(47));
        setBackground(bg);
        icon.set(w.sky);
        temp.setText(c.getString(R.string.wx_temp, w.temp));
        condition.setText(w.condition(c));
        high.setText(c.getString(R.string.wx_max, w.max));
        low.setText(c.getString(R.string.wx_min, w.min));
        chance.setText(c.getString(R.string.wx_percent, w.rain));
        rainBox.setVisibility(w.rain >= 0 ? VISIBLE : GONE);

        hours.removeAllViews();
        for (Weather.Hour h : w.next) {
            String label = h24 ? c.getString(R.string.wx_hour_24, h.hour)
                    : c.getString(R.string.wx_hour_12, h.hour % 12 == 0 ? 12 : h.hour % 12,
                            h.hour < 12 ? "AM" : "PM");
            TextView t = Ui.text(c, 13.5f, Ui.TEXT, Ui.outfit(c, 400));
            t.setText(c.getString(R.string.wx_temp, h.temp));
            column(hours, label, h.sky, t, h.rain);
        }
        days.removeAllViews();
        SimpleDateFormat weekday = new SimpleDateFormat("EEE", Locale.getDefault());
        for (Weather.Day d : w.days) {
            String name = weekday.format(d.date).replace(".", "");
            name = name.substring(0, 1).toUpperCase(Locale.getDefault()) + name.substring(1);
            TextView t = Ui.text(c, 11.5f, Ui.TEXT, Ui.outfit(c, 400));
            SpannableString range = new SpannableString(c.getString(R.string.wx_temp, d.max) + " "
                    + c.getString(R.string.wx_temp, d.min));
            range.setSpan(new ForegroundColorSpan(DIM), range.toString().indexOf(' ') + 1, range.length(),
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            t.setText(range);
            column(days, name, d.sky, t, d.rain);
        }
        hoursRule.setVisibility(w.next.isEmpty() ? GONE : VISIBLE);
        hours.setVisibility(w.next.isEmpty() ? GONE : VISIBLE);
        daysRule.setVisibility(w.days.isEmpty() ? GONE : VISIBLE);
        days.setVisibility(w.days.isEmpty() ? GONE : VISIBLE);
    }

    /** Label, sky, value and chance of rain, stacked and centered. */
    private void column(LinearLayout row, String label, Weather.Sky sky, TextView value, int rain) {
        Context c = getContext();
        LinearLayout col = new LinearLayout(c);
        col.setOrientation(VERTICAL);
        col.setGravity(Gravity.CENTER_HORIZONTAL);
        TextView top = Ui.text(c, 10, SOFT, Ui.outfit(c, 400));
        top.setText(label);
        // A vertical LinearLayout gives children MATCH_PARENT width by default, which in a
        // WRAP_CONTENT column collapses every text to the icon's width.
        col.addView(top, new LayoutParams(WRAP, WRAP));
        Icon small = new Icon(c);
        small.set(sky);
        LayoutParams ip = new LayoutParams(dp(21), dp(21));
        ip.topMargin = dp(3);
        ip.bottomMargin = dp(3);
        col.addView(small, ip);
        col.addView(value, new LayoutParams(WRAP, WRAP));
        TextView r = Ui.text(c, 8.5f, RAIN, Ui.outfit(c, 400));
        r.setText(rain >= 0 ? c.getString(R.string.wx_percent, rain) : "");
        r.setPadding(0, dp(2), 0, 0);
        col.addView(r, new LayoutParams(WRAP, WRAP));
        row.addView(col, row.getChildCount() > 0 ? gap(17) : new LayoutParams(WRAP, WRAP));
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

        /** {@code null} draws a single rain drop, for the chance of rain. */
        void set(Weather.Sky s) {
            sky = s;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            float s = Math.min(getWidth(), getHeight());
            if (sky == null) {
                float w = getWidth(), h = getHeight();
                Path d = new Path();
                d.moveTo(w / 2, 0);
                d.cubicTo(w * 0.15f, h * 0.45f, 0, h * 0.6f, 0, h * 0.7f);
                d.arcTo(new RectF(0, h - w, w, h), 180, -180);
                d.cubicTo(w, h * 0.6f, w * 0.85f, h * 0.45f, w / 2, 0);
                paint.setShader(null);
                paint.setColor(RAIN);
                canvas.drawPath(d, paint);
                return;
            }
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
