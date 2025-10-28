package com.example.galggg;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        MaterialCardView config = findViewById(R.id.configUploadCard);
        if (config != null) {
            config.setOnClickListener(v ->
                    Toast.makeText(this, "Здесь подключим импорт конфига", Toast.LENGTH_SHORT).show()
            );
        }
    }
}
