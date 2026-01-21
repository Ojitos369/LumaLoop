package com.ojitos369.lumaloop.infoScreen;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.ojitos369.lumaloop.R;

public class InfoScreenActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_info);
        getSupportFragmentManager().beginTransaction().replace(R.id.content, new InfoScreenFragment()).commit();
    }
}