package com.example.ckpoolwidget;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Handler;
import android.os.Looper;
import android.widget.RemoteViews;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class CKPoolWidget extends AppWidgetProvider {

    // IMPORTANT: Change this to your Bitcoin address
    private static final String PREF_BITCOIN_ADDRESS = "bitcoin_address";
    private static final String PREFS_NAME = "CKPoolWidgetPrefs";
    private static final String PREF_BEST_EVER = "best_ever";

    private static ExecutorService executorService = Executors.newSingleThreadExecutor();

    @Override
    public void onUpdate(Context context, AppWidgetManager appWidgetManager, int[] appWidgetIds) {
        for (int appWidgetId : appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId);
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        if (intent.getAction() != null && intent.getAction().equals("com.example.ckpoolwidget.UPDATE")) {
            AppWidgetManager appWidgetManager = AppWidgetManager.getInstance(context);
            int[] appWidgetIds = appWidgetManager.getAppWidgetIds(
                    new ComponentName(context, CKPoolWidget.class));
            onUpdate(context, appWidgetManager, appWidgetIds);
        }
    }

    static void updateAppWidget(Context context, AppWidgetManager appWidgetManager, int appWidgetId) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.widget_layout);
// Load colors from settings
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String rateColor = prefs.getString("rate_color", "#00FF00");
        String sharesColor = prefs.getString("shares_color", "#00BFFF");
        String bestColor = prefs.getString("best_color", "#FFD700");

// Apply colors to widget
        try {
            views.setTextColor(R.id.hashrate_text, android.graphics.Color.parseColor(rateColor));
            views.setTextColor(R.id.shares_text, android.graphics.Color.parseColor(sharesColor));
            views.setTextColor(R.id.best_text, android.graphics.Color.parseColor(bestColor));
        } catch (Exception e) {
            // Invalid color, use defaults
        }

        // Set up click to manually refresh
        Intent intent = new Intent(context, CKPoolWidget.class);
        intent.setAction("com.example.ckpoolwidget.UPDATE");
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, 0, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);

        appWidgetManager.updateAppWidget(appWidgetId, views);

        // Fetch data in background
        fetchDataFromCKPool(context, appWidgetManager, appWidgetId, views);
    }

    private static void fetchDataFromCKPool(Context context, AppWidgetManager appWidgetManager,
                                            int appWidgetId, RemoteViews views) {
        executorService.execute(() -> {
            try {
                // Load Bitcoin address from settings
                SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
                String bitcoinAddress = prefs.getString(PREF_BITCOIN_ADDRESS, "");

                if (bitcoinAddress.isEmpty()) {
                    new Handler(Looper.getMainLooper()).post(() -> {
                        views.setTextViewText(R.id.hashrate_text, "Open");
                        views.setTextViewText(R.id.shares_text, "App");
                        views.setTextViewText(R.id.best_text, "Setup");
                        appWidgetManager.updateAppWidget(appWidgetId, views);
                    });
                    return;
                }

                String urlString = "https://solo.ckpool.org/users/" + bitcoinAddress;
                URL url = new URL(urlString);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(10000);
                connection.setReadTimeout(10000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                String jsonString = response.toString();
                JSONObject json = new JSONObject(jsonString);

                // Try to get hashrate5m - it might be a string or number
                double hashrate5m = 0;
                if (json.has("hashrate5m")) {
                    try {
                        hashrate5m = json.getDouble("hashrate5m");
                    } catch (Exception e) {
                        // If it's a string, try parsing it
                        String hashrateStr = json.optString("hashrate5m", "0");
                        try {
                            hashrate5m = Double.parseDouble(hashrateStr);
                        } catch (NumberFormatException nfe) {
                            hashrate5m = 0;
                        }
                    }
                }

                long shares = json.optLong("shares", 0);
                long bestever = json.optLong("bestever", 0);

                // Load saved best ever value
                long savedBestEver = prefs.getLong(PREF_BEST_EVER, 0);

                // Only update best ever if new value is larger
                long displayBestEver = Math.max(savedBestEver, bestever);
                if (displayBestEver > savedBestEver) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(PREF_BEST_EVER, displayBestEver);
                    editor.apply();
                }

                String hashrateStr = json.optString("hashrate5m", "0");
                hashrateStr = hashrateStr;
                String sharesStr = formatNumber(shares);
                String bestStr = formatNumber(displayBestEver);

                String currentTime = new SimpleDateFormat("HH:mm", Locale.getDefault()).format(new Date());

                // Update UI on main thread
                String finalHashrateStr = hashrateStr;
                new Handler(Looper.getMainLooper()).post(() -> {
                    views.setTextViewText(R.id.hashrate_text, finalHashrateStr);
                    views.setTextViewText(R.id.shares_text, sharesStr);
                    views.setTextViewText(R.id.best_text, bestStr);
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });

            } catch (Exception e) {
                e.printStackTrace();
                new Handler(Looper.getMainLooper()).post(() -> {
                    views.setTextViewText(R.id.hashrate_text, "Error");
                    views.setTextViewText(R.id.shares_text, "Error");
                    views.setTextViewText(R.id.best_text, "Error");
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
            }
        });
    }

    private static String formatNumber(double number) {
        if (number >= 1_000_000_000_000.0) {
            return String.format(Locale.US, "%.2f T", number / 1_000_000_000_000.0);
        } else if (number >= 1_000_000_000.0) {
            return String.format(Locale.US, "%.2f G", number / 1_000_000_000.0);
        } else if (number >= 1_000_000.0) {
            return String.format(Locale.US, "%.2f M", number / 1_000_000.0);
        } else if (number >= 1_000.0) {
            return String.format(Locale.US, "%.2f k", number / 1_000.0);
        } else {
            return String.format(Locale.US, "%.2f", number);
        }
    }
}