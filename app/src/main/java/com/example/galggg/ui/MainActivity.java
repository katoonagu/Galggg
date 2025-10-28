package com.example.galggg.ui;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.net.VpnService;
import android.os.Build;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.google.android.material.card.MaterialCardView;

import com.example.galggg.R;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_VPN_PREP = 1001;

    private TextView statusText;
    private MaterialCardView configUploadCard;
    private ImageView vpnButton;

    private Uri configUri;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        statusText = findViewById(R.id.statusText);
        configUploadCard = findViewById(R.id.configUploadCard);
        vpnButton = findViewById(R.id.vpnButton);

        SharedPreferences prefs = getSharedPreferences("secret_vpn", MODE_PRIVATE);
        String saved = prefs.getString("config_uri", null);
        if (saved != null) {
            configUri = Uri.parse(saved);
        }

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openImagePicker();
                    } else {
                        statusText.setText(R.string.permission_required_to_load_config);
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        configUri = uri;
                        prefs.edit().putString("config_uri", uri.toString()).apply();
                        statusText.setText(R.string.config_loaded);

                        // TODO: здесь вызови свою функцию обработки импортированного конфига
                        // например: ExistingConfig.importFromUri(this, uri);
                    }
                }
        );

        configUploadCard.setOnClickListener(v -> startConfigFlow());

        vpnButton.setOnClickListener(v -> {
            if (!isConfigLoaded()) {
                statusText.setText(R.string.warning_no_config);
                startConfigFlow();
            } else {
                toggleVpn();
            }
        });
    }

    private boolean isConfigLoaded() {
        return configUri != null;
    }

    private void startConfigFlow() {
        final String perm = (Build.VERSION.SDK_INT >= 33)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, perm) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestPermissionLauncher.launch(perm);
        }
    }

    private void openImagePicker() {
        pickImageLauncher.launch("image/*");
    }

    private void toggleVpn() {
        Intent prep = VpnService.prepare(this);
        if (prep != null) {
            startActivityForResult(prep, REQ_VPN_PREP);
        } else {
            startVpnOrToggle();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN_PREP && resultCode == RESULT_OK) {
            startVpnOrToggle();
        }
    }

    private void startVpnOrToggle() {
        // TODO: ЗДЕСЬ подключи СУЩЕСТВУЮЩИЕ функции старта/стопа VPN
        // Пример через сервис (замени YourVpnService и логику isRunning на свои):
        // Intent i = new Intent(this, YourVpnService.class)
        //        .setAction("TOGGLE")
        //        .setData(configUri);
        // if (YourVpnService.isRunning()) stopService(i); else startForegroundService(i);

        Toast.makeText(this, "TODO: start/stop VPN with config: " + configUri, Toast.LENGTH_SHORT).show();
    }
}