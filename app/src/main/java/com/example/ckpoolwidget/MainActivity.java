package com.example.ckpoolwidget;

import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "CKPoolWidgetPrefs";
    private static final String PREF_BITCOIN_ADDRESS = "bitcoin_address";
    private static final String PREF_RATE_COLOR = "rate_color";
    private static final String PREF_SHARES_COLOR = "shares_color";
    private static final String PREF_BEST_COLOR = "best_color";

    private EditText bitcoinAddressInput;
    private Button saveButton;
    private Button rateColorButton, sharesColorButton, bestColorButton;
    private TextView statusText;

    private String rateColor = "#00FF00";
    private String sharesColor = "#00BFFF";
    private String bestColor = "#FFD700";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinAddressInput = findViewById(R.id.bitcoin_address_input);
        saveButton = findViewById(R.id.save_button);
        statusText = findViewById(R.id.status_text);
        rateColorButton = findViewById(R.id.rate_color_button);
        sharesColorButton = findViewById(R.id.shares_color_button);
        bestColorButton = findViewById(R.id.best_color_button);

        // Load saved settings
        loadSettings();

        // Save button click
        saveButton.setOnClickListener(v -> saveSettings());

        // Color button clicks
        rateColorButton.setOnClickListener(v -> showColorPicker("Rate", rateColor, color -> {
            rateColor = color;
            updateButtonColor(rateColorButton, color);
        }));

        sharesColorButton.setOnClickListener(v -> showColorPicker("Shares", sharesColor, color -> {
            sharesColor = color;
            updateButtonColor(sharesColorButton, color);
        }));

        bestColorButton.setOnClickListener(v -> showColorPicker("Best", bestColor, color -> {
            bestColor = color;
            updateButtonColor(bestColorButton, color);
        }));
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedAddress = prefs.getString(PREF_BITCOIN_ADDRESS, "");
        bitcoinAddressInput.setText(savedAddress);

        rateColor = prefs.getString(PREF_RATE_COLOR, "#00FF00");
        sharesColor = prefs.getString(PREF_SHARES_COLOR, "#00BFFF");
        bestColor = prefs.getString(PREF_BEST_COLOR, "#FFD700");

        updateButtonColor(rateColorButton, rateColor);
        updateButtonColor(sharesColorButton, sharesColor);
        updateButtonColor(bestColorButton, bestColor);
    }

    private void saveSettings() {
        String address = bitcoinAddressInput.getText().toString().trim();

        if (address.isEmpty()) {
            statusText.setText("Please enter a Bitcoin address");
            statusText.setTextColor(Color.parseColor("#FF0000"));
            return;
        }

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        SharedPreferences.Editor editor = prefs.edit();
        editor.putString(PREF_BITCOIN_ADDRESS, address);
        editor.putString(PREF_RATE_COLOR, rateColor);
        editor.putString(PREF_SHARES_COLOR, sharesColor);
        editor.putString(PREF_BEST_COLOR, bestColor);
        editor.apply();

        statusText.setText("Settings saved! Widget will update soon.");
        statusText.setTextColor(Color.parseColor("#00FF00"));

        // Trigger widget update
        Intent intent = new Intent(this, CKPoolWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new ComponentName(getApplication(), CKPoolWidget.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }

    private void showColorPicker(String title, String currentColor, ColorPickerCallback callback) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        View dialogView = LayoutInflater.from(this).inflate(R.layout.color_picker_dialog, null);

        EditText colorInput = dialogView.findViewById(R.id.color_hex_input);
        View colorPreview = dialogView.findViewById(R.id.color_preview);

        colorInput.setText(currentColor);
        colorPreview.setBackgroundColor(Color.parseColor(currentColor));

        colorInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                String hex = s.toString();
                if (hex.startsWith("#") && (hex.length() == 7 || hex.length() == 4)) {
                    try {
                        colorPreview.setBackgroundColor(Color.parseColor(hex));
                    } catch (Exception e) {
                        // Invalid color
                    }
                }
            }

            @Override
            public void afterTextChanged(Editable s) {}
        });

        builder.setTitle("Pick " + title + " Color")
                .setView(dialogView)
                .setPositiveButton("OK", (dialog, which) -> {
                    String hex = colorInput.getText().toString();
                    if (hex.startsWith("#") && hex.length() == 7) {
                        callback.onColorPicked(hex);
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void updateButtonColor(Button button, String hexColor) {
        try {
            int color = Color.parseColor(hexColor);
            button.setBackgroundColor(color);
            button.setText(hexColor);

            // Set text color to contrast with background
            int red = Color.red(color);
            int green = Color.green(color);
            int blue = Color.blue(color);
            int brightness = (red * 299 + green * 587 + blue * 114) / 1000;
            button.setTextColor(brightness > 128 ? Color.BLACK : Color.WHITE);
        } catch (Exception e) {
            // Invalid color
        }
    }

    interface ColorPickerCallback {
        void onColorPicked(String color);
    }
}