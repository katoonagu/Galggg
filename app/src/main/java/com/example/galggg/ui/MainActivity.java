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

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.galggg.R;
import com.example.galggg.vpn.GalgggVpnService;
import com.google.android.material.card.MaterialCardView;

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
            showStatus(R.string.config_loaded, R.color.success_text);
        }

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                granted -> {
                    if (granted) {
                        openImagePicker();
                    } else {
                        showStatus(R.string.permission_required_to_load_config, R.color.warning_text);
                    }
                }
        );

        pickImageLauncher = registerForActivityResult(
                new ActivityResultContracts.GetContent(),
                uri -> {
                    if (uri != null) {
                        configUri = uri;
                        prefs.edit().putString("config_uri", uri.toString()).apply();
                        showStatus(R.string.config_loaded, R.color.success_text);
                        // TODO: hook into actual config processing if/when it becomes available.
                    }
                }
        );

        if (configUploadCard != null) {
            configUploadCard.setOnClickListener(v -> startConfigFlow());
        }

        if (vpnButton != null) {
            vpnButton.setOnClickListener(v -> {
                if (!isConfigLoaded()) {
                    showStatus(R.string.warning_no_config, R.color.warning_text);
                    startConfigFlow();
                } else {
                    toggleVpn();
                }
            });
        }
    }

    private boolean isConfigLoaded() {
        return configUri != null;
    }

    private void startConfigFlow() {
        final String permission = (Build.VERSION.SDK_INT >= 33)
                ? Manifest.permission.READ_MEDIA_IMAGES
                : Manifest.permission.READ_EXTERNAL_STORAGE;

        if (ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else {
            requestPermissionLauncher.launch(permission);
        }
    }

    private void openImagePicker() {
        pickImageLauncher.launch("image/*");
    }

    private void toggleVpn() {
        Intent prepareIntent = VpnService.prepare(this);
        if (prepareIntent != null) {
            startActivityForResult(prepareIntent, REQ_VPN_PREP);
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
        boolean active = GalgggVpnService.isActive();
        Intent svc = new Intent(this, GalgggVpnService.class);
        if (active) {
            svc.setAction(GalgggVpnService.ACTION_STOP);
            startService(svc);
        } else {
            svc.setAction(GalgggVpnService.ACTION_START);
            if (configUri != null) {
                svc.setData(configUri);
            }
            ContextCompat.startForegroundService(this, svc);
        }
    }

    private void showStatus(@StringRes int messageId, @ColorRes int colorId) {
        if (statusText != null) {
            statusText.setText(messageId);
            statusText.setTextColor(ContextCompat.getColor(this, colorId));
        }
    }
}