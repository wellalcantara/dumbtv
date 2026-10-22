package tv.dumbtv.home;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * The forecast dumbtv weather (weather/) keeps, read through its provider. This app has no
 * internet permission; the helper app fetches from Open-Meteo, and a query is what tells it the
 * forecast is wanted. Absent helper, no forecast yet or one older than {@link #STALE_MS}: null.
 */
final class Weather {
    static final Uri URI = Uri.parse("content://tv.dumbtv.weather/forecast");
    private static final long STALE_MS = 3 * 3_600_000L;

    enum Sky { SUN, MOON, SUN_CLOUD, MOON_CLOUD, CLOUD, FOG, RAIN, SNOW, STORM }

    static final class Hour {
        final int hour;
        final Sky sky;
        final int temp;

        Hour(int hour, Sky sky, int temp) {
            this.hour = hour;
            this.sky = sky;
            this.temp = temp;
        }
    }

    final int temp, max, min, code;
    final boolean day;
    final Sky sky;
    final List<Hour> next = new ArrayList<>();

    private Weather(int temp, int max, int min, int code, boolean day) {
        this.temp = temp;
        this.max = max;
        this.min = min;
        this.code = code;
        this.day = day;
        this.sky = sky(code, day);
    }

    /** Slow (a binder call that may start the helper's process): call off the UI thread. */
    static Weather load(Context c) {
        try (Cursor cur = c.getContentResolver().query(URI, null, null, null, null)) {
            if (cur == null || !cur.moveToFirst()) return null;
            String json = cur.getString(0);
            long fetched = cur.getLong(1);
            if (json == null || System.currentTimeMillis() - fetched > STALE_MS) return null;
            return parse(json, new Date());
        } catch (RuntimeException | JSONException e) {
            Log.w("dumbtv", "weather unavailable", e);
            return null;
        }
    }

    static Weather parse(String json, Date now) throws JSONException {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        Weather w = new Weather(
                round(cur.getDouble("temperature_2m")),
                round(daily.getJSONArray("temperature_2m_max").getDouble(0)),
                round(daily.getJSONArray("temperature_2m_min").getDouble(0)),
                cur.getInt("weather_code"),
                cur.getInt("is_day") == 1);

        JSONObject hourly = root.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray days = hourly.getJSONArray("is_day");
        // timezone=auto: the API's times are the place's local time, which is the TV's.
        String key = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.ROOT).format(now);
        for (int i = 0; i < times.length(); i++) {
            if (!key.equals(times.getString(i))) continue;
            for (int j = i + 1; j < times.length() && w.next.size() < 3; j++) {
                String t = times.getString(j);
                int hour = Integer.parseInt(t.substring(t.indexOf('T') + 1, t.indexOf('T') + 3));
                w.next.add(new Hour(hour, sky(codes.getInt(j), days.getInt(j) == 1), round(temps.getDouble(j))));
            }
            break;
        }
        return w;
    }

    private static int round(double v) {
        return (int) Math.round(v);
    }

    /** WMO weather interpretation codes, as Open-Meteo documents them. */
    static Sky sky(int code, boolean day) {
        if (code <= 1) return day ? Sky.SUN : Sky.MOON;
        if (code == 2) return day ? Sky.SUN_CLOUD : Sky.MOON_CLOUD;
        if (code == 3) return Sky.CLOUD;
        if (code == 45 || code == 48) return Sky.FOG;
        if (code >= 71 && code <= 77 || code == 85 || code == 86) return Sky.SNOW;
        if (code >= 95) return Sky.STORM;
        return Sky.RAIN;
    }

    /** The condition's name, from the string resources. */
    String condition(Context c) {
        int res;
        if (code == 0) res = day ? R.string.wx_sunny : R.string.wx_clear;
        else if (code == 1) res = R.string.wx_mostly_clear;
        else if (code == 2) res = R.string.wx_partly_cloudy;
        else if (code == 3) res = R.string.wx_overcast;
        else if (sky == Sky.FOG) res = R.string.wx_fog;
        else if (code >= 51 && code <= 57) res = R.string.wx_drizzle;
        else if (code >= 80 && code <= 82) res = R.string.wx_showers;
        else if (sky == Sky.SNOW) res = R.string.wx_snow;
        else if (sky == Sky.STORM) res = R.string.wx_storm;
        else res = R.string.wx_rain;
        return c.getString(res);
    }
}
