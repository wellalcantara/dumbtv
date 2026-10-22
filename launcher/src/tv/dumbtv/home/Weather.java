package tv.dumbtv.home;

import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.ParseException;
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
    private static final int HOURS = 4, DAYS = 3;

    enum Sky { SUN, MOON, SUN_CLOUD, MOON_CLOUD, CLOUD, FOG, RAIN, SNOW, STORM }

    /** One upcoming hour; {@code rain} is the precipitation probability in %, -1 if unknown. */
    static final class Hour {
        final int hour, temp, rain;
        final Sky sky;

        Hour(int hour, Sky sky, int temp, int rain) {
            this.hour = hour;
            this.sky = sky;
            this.temp = temp;
            this.rain = rain;
        }
    }

    /** One of the next days, from tomorrow. */
    static final class Day {
        final Date date;
        final Sky sky;
        final int max, min, rain;

        Day(Date date, Sky sky, int max, int min, int rain) {
            this.date = date;
            this.sky = sky;
            this.max = max;
            this.min = min;
            this.rain = rain;
        }
    }

    final int temp, max, min, code, rain;
    final boolean day;
    final Sky sky;
    final List<Hour> next = new ArrayList<>();
    final List<Day> days = new ArrayList<>();

    private Weather(int temp, int max, int min, int rain, int code, boolean day) {
        this.temp = temp;
        this.max = max;
        this.min = min;
        this.rain = rain;
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
        } catch (RuntimeException | JSONException | ParseException e) {
            Log.w("dumbtv", "weather unavailable", e);
            return null;
        }
    }

    static Weather parse(String json, Date now) throws JSONException, ParseException {
        JSONObject root = new JSONObject(json);
        JSONObject cur = root.getJSONObject("current");
        JSONObject daily = root.getJSONObject("daily");
        JSONArray dTimes = daily.getJSONArray("time");
        JSONArray dMax = daily.getJSONArray("temperature_2m_max");
        JSONArray dMin = daily.getJSONArray("temperature_2m_min");
        JSONArray dRain = daily.optJSONArray("precipitation_probability_max");
        JSONArray dCode = daily.optJSONArray("weather_code");

        // timezone=auto: the API's dates and times are the place's local time, which is the TV's.
        String today = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).format(now);
        int t = 0;
        while (t < dTimes.length() - 1 && dTimes.getString(t).compareTo(today) < 0) t++;
        Weather w = new Weather(
                round(cur.getDouble("temperature_2m")),
                round(dMax.getDouble(t)),
                round(dMin.getDouble(t)),
                percent(dRain, t),
                cur.getInt("weather_code"),
                cur.getInt("is_day") == 1);

        SimpleDateFormat dayFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.ROOT);
        for (int i = t + 1; i < dTimes.length() && w.days.size() < DAYS; i++) {
            int code = dCode == null ? 3 : dCode.optInt(i, 3);
            w.days.add(new Day(dayFormat.parse(dTimes.getString(i)), sky(code, true),
                    round(dMax.getDouble(i)), round(dMin.getDouble(i)), percent(dRain, i)));
        }

        JSONObject hourly = root.getJSONObject("hourly");
        JSONArray times = hourly.getJSONArray("time");
        JSONArray temps = hourly.getJSONArray("temperature_2m");
        JSONArray codes = hourly.getJSONArray("weather_code");
        JSONArray isDay = hourly.getJSONArray("is_day");
        JSONArray rain = hourly.optJSONArray("precipitation_probability");
        String key = new SimpleDateFormat("yyyy-MM-dd'T'HH:00", Locale.ROOT).format(now);
        for (int i = 0; i < times.length(); i++) {
            if (!key.equals(times.getString(i))) continue;
            for (int j = i + 1; j < times.length() && w.next.size() < HOURS; j++) {
                String s = times.getString(j);
                int hour = Integer.parseInt(s.substring(s.indexOf('T') + 1, s.indexOf('T') + 3));
                w.next.add(new Hour(hour, sky(codes.getInt(j), isDay.getInt(j) == 1),
                        round(temps.getDouble(j)), percent(rain, j)));
            }
            break;
        }
        return w;
    }

    private static int percent(JSONArray a, int i) {
        return a == null || a.isNull(i) ? -1 : a.optInt(i, -1);
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
