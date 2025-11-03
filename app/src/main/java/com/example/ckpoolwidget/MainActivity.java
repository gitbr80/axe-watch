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
        android.widget.GridLayout colorGrid = dialogView.findViewById(R.id.color_grid);

        // Preset colors - lots of options!
        String[] presetColors = {
                // Reds
                "#FF0000", "#FF3333", "#FF6666", "#CC0000", "#990000", "#660000",
                // Oranges
                "#FF8800", "#FFAA00", "#FF6600", "#CC6600", "#994C00", "#663300",
                // Yellows
                "#FFFF00", "#FFFF66", "#FFCC00", "#FFD700", "#FFA500", "#FF8C00",
                // Greens
                "#00FF00", "#00FF66", "#00CC00", "#009900", "#33FF33", "#66FF66",
                // Cyans
                "#00FFFF", "#00CCCC", "#009999", "#66FFFF", "#33CCCC", "#00CED1",
                // Blues
                "#0000FF", "#3333FF", "#6666FF", "#0066FF", "#0099FF", "#00BFFF",
                // Purples
                "#8800FF", "#AA00FF", "#CC00FF", "#6600CC", "#9933FF", "#CC66FF",
                // Pinks/Magentas
                "#FF00FF", "#FF66FF", "#FF33CC", "#CC0099", "#FF1493", "#FF69B4",
                // Whites/Grays
                "#FFFFFF", "#CCCCCC", "#999999", "#666666", "#333333", "#000000"
        };

        // Create color buttons in grid
        int buttonSize = 100; // dp
        float density = getResources().getDisplayMetrics().density;
        int buttonSizePx = (int) (buttonSize * density / 6); // Divide to fit 6 columns

        for (String color : presetColors) {
            Button colorButton = new Button(this);
            android.widget.GridLayout.LayoutParams params = new android.widget.GridLayout.LayoutParams();
            params.width = buttonSizePx;
            params.height = buttonSizePx;
            params.setMargins(4, 4, 4, 4);
            colorButton.setLayoutParams(params);

            try {
                colorButton.setBackgroundColor(Color.parseColor(color));
            } catch (Exception e) {
                continue;
            }

            colorButton.setOnClickListener(v -> {
                colorInput.setText(color);
                colorPreview.setBackgroundColor(Color.parseColor(color));
            });

            colorGrid.addView(colorButton);
        }

        // Set initial preview
        colorInput.setText(currentColor);
        colorPreview.setBackgroundColor(Color.parseColor(currentColor));

        // Live preview as user types
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