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

    private static final String PREF_BITCOIN_ADDRESS = "bitcoin_address";
    private static final String PREFS_NAME = "CKPoolWidgetPrefs";
    private static final String PREF_BEST_EVER = "best_ever";
    private static final String PREF_BEST_DATE = "best_date";

    // Cache keys for last known good data
    private static final String PREF_LAST_HASHRATE = "last_hashrate";
    private static final String PREF_LAST_SHARES = "last_shares";
    private static final String PREF_LAST_BTC_PRICE = "last_btc_price";
    private static final String PREF_LAST_POOL_INFO = "last_pool_info";

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
        fetchBitcoinPriceAndPoolInfo(context, appWidgetManager, appWidgetId, views);
    }

    private static void fetchDataFromCKPool(Context context, AppWidgetManager appWidgetManager,
                                            int appWidgetId, RemoteViews views) {
        executorService.execute(() -> {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);

            try {
                // Load Bitcoin address from settings
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
                connection.setConnectTimeout(15000);  // Increased timeout
                connection.setReadTimeout(15000);      // Increased timeout

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

                // Get hashrate as string (already formatted)
                String hashrateStr = json.optString("hashrate5m", "0");
                long shares = json.optLong("shares", 0);
                long bestever = json.optLong("bestever", 0);

                // Load saved best ever value
                long savedBestEver = prefs.getLong(PREF_BEST_EVER, 0);

                // Only update best ever if new value is larger
                long displayBestEver = Math.max(savedBestEver, bestever);
                String bestDate = prefs.getString(PREF_BEST_DATE, "");

                // ONLY save a date if:
                // 1. We had a previous best saved (savedBestEver > 0)
                // 2. AND the new value is higher than the saved value
                if (savedBestEver > 0 && bestever > savedBestEver) {
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(PREF_BEST_EVER, bestever);
                    // Save current date - we KNOW this is a new record
                    String currentDate = new SimpleDateFormat("M/d/yy", Locale.getDefault()).format(new Date());
                    editor.putString(PREF_BEST_DATE, currentDate);
                    editor.apply();
                    bestDate = currentDate;
                    displayBestEver = bestever;
                } else if (savedBestEver == 0 && bestever > 0) {
                    // First time seeing a best - save it but NO date
                    SharedPreferences.Editor editor = prefs.edit();
                    editor.putLong(PREF_BEST_EVER, bestever);
                    editor.apply();
                    displayBestEver = bestever;
                    // bestDate stays empty - we don't know when this happened
                }

                String sharesStr = formatNumber(shares);
                String bestStr = formatNumber(displayBestEver);

                // Cache the successful data
                SharedPreferences.Editor editor = prefs.edit();
                editor.putString(PREF_LAST_HASHRATE, hashrateStr);
                editor.putString(PREF_LAST_SHARES, sharesStr);
                editor.apply();

                // Update UI on main thread
                String finalHashrateStr = hashrateStr;
                String finalBestDate = bestDate;
                new Handler(Looper.getMainLooper()).post(() -> {
                    views.setTextViewText(R.id.hashrate_text, finalHashrateStr);
                    views.setTextViewText(R.id.shares_text, sharesStr);
                    views.setTextViewText(R.id.best_text, bestStr);
                    views.setTextViewText(R.id.best_date_text, finalBestDate);
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });

            } catch (Exception e) {
                e.printStackTrace();

                // Load cached data instead of showing error
                String cachedHashrate = prefs.getString(PREF_LAST_HASHRATE, "Error");
                String cachedShares = prefs.getString(PREF_LAST_SHARES, "Error");
                long savedBestEver = prefs.getLong(PREF_BEST_EVER, 0);
                String bestStr = savedBestEver > 0 ? formatNumber(savedBestEver) : "Error";
                String bestDate = prefs.getString(PREF_BEST_DATE, "");

                new Handler(Looper.getMainLooper()).post(() -> {
                    views.setTextViewText(R.id.hashrate_text, cachedHashrate);
                    views.setTextViewText(R.id.shares_text, cachedShares);
                    views.setTextViewText(R.id.best_text, bestStr);
                    views.setTextViewText(R.id.best_date_text, bestDate);
                    appWidgetManager.updateAppWidget(appWidgetId, views);
                });
            }
        });
    }

    private static void fetchBitcoinPriceAndPoolInfo(Context context, AppWidgetManager appWidgetManager,
                                                     int appWidgetId, RemoteViews views) {
        executorService.execute(() -> {
            SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
            String bitcoinPrice = "?";
            String poolBlockInfo = "?";

            // Fetch Bitcoin price
            try {
                URL url = new URL("https://api.coinbase.com/v2/prices/BTC-USD/spot");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);  // Increased timeout
                connection.setReadTimeout(15000);      // Increased timeout

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                JSONObject json = new JSONObject(response.toString());
                String price = json.getJSONObject("data").getString("amount");
                double priceValue = Double.parseDouble(price);
                bitcoinPrice = String.format(Locale.US, "$%.0fk", priceValue / 1000);

                // Cache successful price
                prefs.edit().putString(PREF_LAST_BTC_PRICE, bitcoinPrice).apply();
            } catch (Exception e) {
                e.printStackTrace();
                // Use cached price
                bitcoinPrice = prefs.getString(PREF_LAST_BTC_PRICE, "?");
            }

            // Fetch pool last block from mempool.space API
            try {
                URL url = new URL("https://mempool.space/api/v1/mining/pool/solock/blocks");
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(15000);  // Increased timeout
                connection.setReadTimeout(15000);      // Increased timeout

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream()));
                StringBuilder response = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    response.append(line);
                }
                reader.close();

                // Response is an array of blocks
                org.json.JSONArray blocks = new org.json.JSONArray(response.toString());

                if (blocks.length() > 0) {
                    // Get the most recent block (first in array)
                    JSONObject latestBlock = blocks.getJSONObject(0);
                    long blockTimestamp = latestBlock.getLong("timestamp");

                    long currentTime = System.currentTimeMillis() / 1000;
                    long timeDiff = currentTime - blockTimestamp;

                    long days = timeDiff / 86400;
                    long hours = (timeDiff % 86400) / 3600;

                    if (days > 0) {
                        poolBlockInfo = days + "d ago";
                    } else if (hours > 0) {
                        poolBlockInfo = hours + "h ago";
                    } else {
                        poolBlockInfo = "< 1h ago";
                    }

                    // Cache successful pool info
                    prefs.edit().putString(PREF_LAST_POOL_INFO, poolBlockInfo).apply();
                }
            } catch (Exception e) {
                e.printStackTrace();
                // Use cached pool info
                poolBlockInfo = prefs.getString(PREF_LAST_POOL_INFO, "N/A");
            }

            String finalBitcoinPrice = bitcoinPrice;
            String finalPoolBlockInfo = poolBlockInfo;

            new Handler(Looper.getMainLooper()).post(() -> {
                String topInfo = "     ₿ " + finalBitcoinPrice + " | Last pool block: " + finalPoolBlockInfo;
                views.setTextViewText(R.id.top_info_text, topInfo);
                appWidgetManager.updateAppWidget(appWidgetId, views);
            });
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