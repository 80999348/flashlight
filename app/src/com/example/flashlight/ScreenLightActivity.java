package com.example.flashlight;

import android.app.Activity;
import android.os.Bundle;

public class ScreenLightActivity extends Activity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_screen_light);
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        float b = getIntent().getFloatExtra("brightness", 1.0f);
        getWindow().getAttributes().screenBrightness = Math.max(0.01f, Math.min(1.0f, b));
        getWindow().setAttributes(getWindow().getAttributes());
        findViewById(android.R.id.content).setOnClickListener(v -> finish());
    }
}
