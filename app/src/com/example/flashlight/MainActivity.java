package com.example.flashlight;

import android.app.Activity;
import android.graphics.Color;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;

public class MainActivity extends Activity {

    private static final int MODE_TORCH = 0;
    private static final int MODE_SOS = 1;
    private static final int MODE_STROBE = 2;
    private static final int MODE_SCREEN = 3;

    // SOS 摩尔斯电码：3短3长3短。元素为 {亮毫秒, 灭毫秒} 循环序列
    private static final long[] SOS_PATTERN = {
            200, 200, 200, 200, 200, 500,      // S · · ·
            600, 200, 600, 200, 600, 500,      // O — — —
            200, 200, 200, 200, 200, 900       // S · · ·
    };
    private static final long STROBE_INTERVAL = 100;

    private CameraManager cameraManager;
    private String cameraId;
    private boolean torchOn = false;
    private int mode = MODE_TORCH;
    private int timerMinutes = 0; // 0 = 不定时
    private long timerDeadline = 0;

    private LinearLayout root;
    private ImageView icon;
    private TextView status;
    private TextView brightnessValue;
    private TextView timerCountdown;
    private Button btnToggle;
    private Button[] modeButtons = new Button[4];
    private Button[] timerButtons;
    private SeekBar brightness;
    private int torchMaxLevel = 1;
    private boolean brightnessSupported = false;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable patternRunner = new Runnable() {
        @Override
        public void run() {
            if (mode == MODE_SOS) {
                long total = 0;
                for (long d : SOS_PATTERN) total += d;
                long t = System.currentTimeMillis() % total;
                boolean on = false;
                long remain = 0;
                for (int i = 0; i < SOS_PATTERN.length; i += 2) {
                    if (t < SOS_PATTERN[i]) { on = true; remain = SOS_PATTERN[i] - t; break; }
                    t -= SOS_PATTERN[i];
                    if (t < SOS_PATTERN[i + 1]) { on = false; remain = SOS_PATTERN[i + 1] - t; break; }
                    t -= SOS_PATTERN[i + 1];
                }
                setTorchRaw(on);
                handler.postDelayed(this, Math.max(50, remain));
            } else if (mode == MODE_STROBE) {
                setTorchRaw(!torchOn);
                handler.postDelayed(this, STROBE_INTERVAL);
            }
        }
    };
    private final Runnable timerRunner = new Runnable() {
        @Override
        public void run() {
            if (timerMinutes <= 0 || timerDeadline == 0) return;
            long left = timerDeadline - System.currentTimeMillis();
            if (left <= 0) {
                stopAllLight();
                timerCountdown.setText("已到时自动关闭");
                return;
            }
            long min = left / 60000;
            long sec = (left % 60000) / 1000;
            timerCountdown.setText(String.format(Locale.CHINA, "剩余 %02d:%02d 后自动关闭", min, sec));
            handler.postDelayed(this, 1000);
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        root = findViewById(R.id.root);
        icon = findViewById(R.id.icon);
        status = findViewById(R.id.status);
        brightnessValue = findViewById(R.id.brightness_value);
        timerCountdown = findViewById(R.id.timer_countdown);
        btnToggle = findViewById(R.id.btn_toggle);
        brightness = findViewById(R.id.brightness);

        modeButtons[MODE_TORCH] = findViewById(R.id.mode_torch);
        modeButtons[MODE_SOS] = findViewById(R.id.mode_sos);
        modeButtons[MODE_STROBE] = findViewById(R.id.mode_strobe);
        modeButtons[MODE_SCREEN] = findViewById(R.id.mode_screen);
        for (int i = 0; i < modeButtons.length; i++) {
            final int m = i;
            modeButtons[i].setOnClickListener(v -> switchMode(m));
        }

        timerButtons = new Button[]{
                findViewById(R.id.timer_off),
                findViewById(R.id.timer_5),
                findViewById(R.id.timer_15),
                findViewById(R.id.timer_30),
                findViewById(R.id.timer_60)
        };
        int[] minutes = {0, 5, 15, 30, 60};
        for (int i = 0; i < timerButtons.length; i++) {
            final int mins = minutes[i];
            timerButtons[i].setOnClickListener(v -> {
                timerMinutes = mins;
                refreshTimerButtons();
                if (mins > 0 && torchOn) {
                    timerDeadline = System.currentTimeMillis() + mins * 60000L;
                    handler.removeCallbacks(timerRunner);
                    handler.post(timerRunner);
                } else {
                    timerDeadline = 0;
                    handler.removeCallbacks(timerRunner);
                    timerCountdown.setText("");
                }
            });
        }

        cameraManager = (CameraManager) getSystemService(CAMERA_SERVICE);
        cameraId = findFlashCamera();
        if (cameraId == null) {
            status.setText("此设备没有闪光灯，可使用屏幕光");
            btnToggle.setEnabled(false);
            btnToggle.setText("不可用");
            modeButtons[MODE_TORCH].setEnabled(false);
            modeButtons[MODE_SOS].setEnabled(false);
            modeButtons[MODE_STROBE].setEnabled(false);
        }

        setupBrightness();

        btnToggle.setOnClickListener(v -> {
            if (mode == MODE_SCREEN) {
                android.content.Intent it = new android.content.Intent(this, ScreenLightActivity.class);
                it.putExtra("brightness", screenBrightnessFraction());
                startActivity(it);
                return;
            }
            toggleTorch();
        });

        Button btnExit = findViewById(R.id.btn_exit);
        btnExit.setOnClickListener(v -> {
            stopAllLight();
            finish();
        });
    }

    private String findFlashCamera() {
        try {
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (hasFlash != null && hasFlash) return id;
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
        return null;
    }

    private void setupBrightness() {
        // 闪光灯强度（Android 13+ 且硬件支持多档时生效）
        if (Build.VERSION.SDK_INT >= 33 && cameraId != null) {
            try {
                Integer max = cameraManager.getCameraCharacteristics(cameraId)
                        .get(CameraCharacteristics.FLASH_INFO_STRENGTH_MAXIMUM_LEVEL);
                torchMaxLevel = (max != null) ? max : 1;
                brightnessSupported = torchMaxLevel > 1;
            } catch (Exception ignored) {
            }
        }
        brightness.setMax(10);
        brightness.setProgress(brightnessSupported ? torchMaxLevel : 7);
        brightness.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar sb, int value, boolean fromUser) {
                updateBrightnessText();
            }

            @Override public void onStartTrackingTouch(SeekBar sb) { }
            @Override public void onStopTrackingTouch(SeekBar sb) { }
        });
        updateBrightnessText();
    }

    private void updateBrightnessText() {
        if (mode == MODE_SCREEN) {
            int pct = 20 + (int) (brightness.getProgress() * 8.0f); // 20% ~ 100%
            brightnessValue.setText("屏幕亮度：约 " + pct + "%");
        } else if (brightnessSupported) {
            brightnessValue.setText("亮度：" + Math.max(1, brightness.getProgress()) + " / " + torchMaxLevel);
        } else {
            brightnessValue.setText("此设备不支持闪光灯调亮，屏幕光仍可用");
        }
    }

    private int flashLevel() {
        if (!brightnessSupported) return 1;
        // 把拉条 1..10 映射到硬件 1..torchMaxLevel
        int p = Math.max(1, brightness.getProgress());
        return Math.max(1, Math.round(p * torchMaxLevel / 10.0f));
    }

    private float screenBrightnessFraction() {
        // 拉条 0..10 → 0.2..1.0
        return 0.2f + brightness.getProgress() * 0.08f;
    }

    private void applyBrightness(int level) {
        try {
            cameraManager.turnOnTorchWithStrengthLevel(cameraId, level);
        } catch (Exception e) {
            status.setText("亮度调节失败：" + e.getMessage());
        }
    }

    private void setTorchRaw(boolean on) {
        try {
            if (on && brightnessSupported) {
                cameraManager.turnOnTorchWithStrengthLevel(cameraId, flashLevel());
            } else {
                cameraManager.setTorchMode(cameraId, on);
            }
            torchOn = on;
            icon.setImageResource(on ? R.drawable.torch_on : R.drawable.torch_off);
        } catch (CameraAccessException ignored) {
        }
    }

    private void switchMode(int m) {
        stopAllLight();
        mode = m;
        refreshModeButtons();
        updateBrightnessText();
        switch (m) {
            case MODE_TORCH:
                btnToggle.setText("打开手电筒");
                status.setText("手电筒已关闭");
                break;
            case MODE_SOS:
                btnToggle.setText("开始 SOS");
                status.setText("SOS 摩尔斯求救模式");
                break;
            case MODE_STROBE:
                btnToggle.setText("开始爆闪");
                status.setText("爆闪警示模式");
                break;
            case MODE_SCREEN:
                btnToggle.setText("打开屏幕光");
                status.setText("全屏白光补光模式");
                break;
        }
    }

    private void refreshModeButtons() {
        int active = Color.parseColor("#FFFFC107");
        int normal = Color.parseColor("#FF888888");
        for (int i = 0; i < modeButtons.length; i++) {
            modeButtons[i].setTextColor(i == mode ? active : normal);
        }
    }

    private void refreshTimerButtons() {
        int active = Color.parseColor("#FFFFC107");
        int normal = Color.parseColor("#FF888888");
        int[] minutes = {0, 5, 15, 30, 60};
        for (int i = 0; i < timerButtons.length; i++) {
            timerButtons[i].setTextColor(timerMinutes == minutes[i] ? active : normal);
        }
    }

    private void toggleTorch() {
        if (cameraId == null) return;
        if (torchOn) {
            stopAllLight();
            return;
        }
        torchOn = true;
        try {
            setTorchRaw(true);
        } catch (Exception e) {
            status.setText("操作失败：" + e.getMessage());
            torchOn = false;
        }
        updateToggleUi(true);
        if (mode == MODE_SOS || mode == MODE_STROBE) {
            handler.removeCallbacks(patternRunner);
            handler.post(patternRunner);
        }
        if (timerMinutes > 0) {
            timerDeadline = System.currentTimeMillis() + timerMinutes * 60000L;
            handler.removeCallbacks(timerRunner);
            handler.post(timerRunner);
        }
    }

    private void updateToggleUi(boolean on) {
        switch (mode) {
            case MODE_TORCH:
                btnToggle.setText(on ? "关闭手电筒" : "打开手电筒");
                status.setText(on ? "手电筒已打开" : "手电筒已关闭");
                break;
            case MODE_SOS:
                btnToggle.setText(on ? "停止 SOS" : "开始 SOS");
                status.setText(on ? "SOS 求救闪烁中" : "SOS 已停止");
                break;
            case MODE_STROBE:
                btnToggle.setText(on ? "停止爆闪" : "开始爆闪");
                status.setText(on ? "爆闪运行中" : "爆闪已停止");
                break;
        }
        if (on) {
            root.setBackgroundColor(Color.WHITE);
            status.setTextColor(Color.BLACK);
        } else {
            root.setBackgroundColor(Color.parseColor("#FF111111"));
            status.setTextColor(Color.parseColor("#FFDDDDDD"));
            icon.setImageResource(R.drawable.torch_off);
        }
    }

    private void stopAllLight() {
        handler.removeCallbacks(patternRunner);
        handler.removeCallbacks(timerRunner);
        timerDeadline = 0;
        timerCountdown.setText("");
        if (cameraId != null && torchOn) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException ignored) {
            }
        }
        torchOn = false;
        updateToggleUi(false);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (torchOn && cameraId != null) {
            try {
                cameraManager.setTorchMode(cameraId, false);
            } catch (CameraAccessException ignored) {
            }
        }
    }
}
