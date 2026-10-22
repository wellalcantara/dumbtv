package tv.dumbtv.weather;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.util.Log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Serves the last Open-Meteo forecast to dumbtv home as one row: {@code json} (the API's own
 * response), {@code fetched} (ms since epoch) and {@code status}. A query that finds the forecast
 * older than {@link #FRESH_MS} starts one refresh in the background and answers with what it has;
 * the launcher hears about the new one through {@link #URI}'s change notification.
 *
 * <p>The place comes from {@code weather.conf} in this app's external files folder, pushed with
 * adb: {@code lat=-20.82}, {@code lon=-49.38}, optionally {@code unit=fahrenheit}.
 */
public final class Provider extends ContentProvider {
    static final Uri URI = Uri.parse("content://tv.dumbtv.weather/forecast");
    private static final String TAG = "dumbtv-weather";
    private static final long FRESH_MS = 30 * 60_000L, RETRY_MS = 5 * 60_000L;
    private static final String[] COLUMNS = {"json", "fetched", "status"};

    private final AtomicBoolean busy = new AtomicBoolean();
    private volatile long lastTry;
    private volatile String status = "ok";

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        Context c = getContext();
        File cache = new File(c.getFilesDir(), "forecast-v2.json");
        long now = System.currentTimeMillis();
        long fetched = cache.isFile() ? cache.lastModified() : 0;
        if (now - fetched > FRESH_MS && now - lastTry > RETRY_MS) refresh(c, cache);
        MatrixCursor out = new MatrixCursor(COLUMNS, 1);
        out.addRow(new Object[] {fetched > 0 ? read(cache) : null, fetched, status});
        out.setNotificationUri(c.getContentResolver(), URI);
        return out;
    }

    private void refresh(Context c, File cache) {
        if (!busy.compareAndSet(false, true)) return;
        lastTry = System.currentTimeMillis();
        new Thread(() -> {
            try {
                status = fetch(c, cache);
            } catch (IOException | RuntimeException e) {
                Log.w(TAG, "forecast failed", e);
                status = "error";
            } finally {
                busy.set(false);
            }
            c.getContentResolver().notifyChange(URI, null);
        }, "weather").start();
    }

    private static String fetch(Context c, File cache) throws IOException {
        String lat = null, lon = null, unit = null;
        File external = c.getExternalFilesDir(null);
        File conf = external == null ? null : new File(external, "weather.conf");
        if (conf != null && conf.isFile()) {
            for (String line : read(conf).split("\n")) {
                int eq = line.indexOf('=');
                if (line.startsWith("#") || eq < 0) continue;
                String k = line.substring(0, eq).trim(), v = line.substring(eq + 1).trim();
                if (k.equals("lat")) lat = number(v);
                else if (k.equals("lon")) lon = number(v);
                else if (k.equals("unit") && v.equals("fahrenheit")) unit = v;
            }
        }
        if (lat == null || lon == null) return "no-location";

        String query = String.format(Locale.ROOT,
                "https://api.open-meteo.com/v1/forecast?latitude=%s&longitude=%s"
                        + "&current=temperature_2m,weather_code,is_day"
                        + "&hourly=temperature_2m,weather_code,is_day,precipitation_probability"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
                        + "&timezone=auto&forecast_days=4%s",
                lat, lon, unit == null ? "" : "&temperature_unit=" + unit);
        HttpURLConnection http = (HttpURLConnection) new URL(query).openConnection();
        http.setConnectTimeout(10_000);
        http.setReadTimeout(10_000);
        http.setRequestProperty("User-Agent", "dumbtv-weather");
        try {
            if (http.getResponseCode() != 200) return "http " + http.getResponseCode();
            String body;
            try (InputStream in = http.getInputStream()) {
                body = read(in);
            }
            if (!body.startsWith("{") || !body.contains("\"current\"")) return "bad response";
            File tmp = new File(cache.getPath() + ".tmp");
            try (OutputStream o = new FileOutputStream(tmp)) {
                o.write(body.getBytes(StandardCharsets.UTF_8));
            }
            if (!tmp.renameTo(cache)) return "could not save";
            return "ok";
        } finally {
            http.disconnect();
        }
    }

    /** Accepts a plain decimal only, so nothing else reaches the URL. */
    private static String number(String v) {
        return v.matches("-?\\d{1,3}(\\.\\d{1,6})?") ? v : null;
    }

    private static String read(File f) {
        try (InputStream in = new FileInputStream(f)) {
            return read(in);
        } catch (IOException e) {
            return null;
        }
    }

    private static String read(InputStream in) throws IOException {
        StringBuilder b = new StringBuilder();
        BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
        char[] buf = new char[4096];
        for (int n; (n = r.read(buf)) > 0; ) b.append(buf, 0, n);
        return b.toString();
    }

    @Override
    public String getType(Uri uri) {
        return null;
    }

    @Override
    public Uri insert(Uri uri, ContentValues values) {
        return null;
    }

    @Override
    public int delete(Uri uri, String selection, String[] args) {
        return 0;
    }

    @Override
    public int update(Uri uri, ContentValues values, String selection, String[] args) {
        return 0;
    }
}
