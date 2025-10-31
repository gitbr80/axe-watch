package com.example.ckpoolwidget;

import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final String PREFS_NAME = "CKPoolWidgetPrefs";
    private static final String PREF_BITCOIN_ADDRESS = "bitcoin_address";

    private EditText bitcoinAddressInput;
    private Button saveButton;
    private TextView statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bitcoinAddressInput = findViewById(R.id.bitcoin_address_input);
        saveButton = findViewById(R.id.save_button);
        statusText = findViewById(R.id.status_text);

        // Load saved address
        loadSettings();

        // Save button click
        saveButton.setOnClickListener(v -> saveSettings());
    }

    private void loadSettings() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String savedAddress = prefs.getString(PREF_BITCOIN_ADDRESS, "");
        bitcoinAddressInput.setText(savedAddress);
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
        editor.apply();

        statusText.setText("Address saved! Widget will update soon.");
        statusText.setTextColor(Color.parseColor("#00FF00"));

        // Trigger widget update
        Intent intent = new Intent(this, CKPoolWidget.class);
        intent.setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE);
        int[] ids = AppWidgetManager.getInstance(getApplication())
                .getAppWidgetIds(new ComponentName(getApplication(), CKPoolWidget.class));
        intent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids);
        sendBroadcast(intent);
    }
}