package com.example.galggg;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.android.material.card.MaterialCardView;

public class MainActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            ViewCompat.setOnApplyWindowInsetsListener(bottomNav, (v, insets) -> {
                int systemBottom = insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                v.setPadding(
                        v.getPaddingLeft(),
                        v.getPaddingTop(),
                        v.getPaddingRight(),
                        v.getPaddingBottom() + systemBottom
                );
                return insets;
            });

            bottomNav.setOnItemSelectedListener(item -> {
                int itemId = item.getItemId();
                if (itemId == R.id.nav_home) {
                    // TODO: show Home (already here)
                    return true;
                } else if (itemId == R.id.nav_settings) {
                    // TODO: open Settings (navigation pending)
                    return true;
                } else if (itemId == R.id.nav_donat) {
                    // TODO: open Donat
                    return true;
                }
                return false;
            });

            bottomNav.setSelectedItemId(R.id.nav_home);
        }

        MaterialCardView config = findViewById(R.id.configUploadCard);
        if (config != null) {
            config.setOnClickListener(v ->
                    Toast.makeText(this, "Config import flow coming soon", Toast.LENGTH_SHORT).show()
            );
        }
    }
}