package com.ytregister;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import androidx.appcompat.app.AppCompatActivity;

public class MainActivity extends AppCompatActivity {

    private static final int REQ_OVERLAY = 1001;
    private EditText urlInput;
    private Switch autoStartSwitch;
    private TextView permStatusText;
    private SharedPreferences prefs;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        prefs = getSharedPreferences("YTRegister", MODE_PRIVATE);
        urlInput = findViewById(R.id.url_input);
        autoStartSwitch = findViewById(R.id.auto_start_switch);
        permStatusText = findViewById(R.id.perm_status);
        Button permissionBtn = findViewById(R.id.permission_btn);
        Button startBtn = findViewById(R.id.start_btn);
        Button stopBtn = findViewById(R.id.stop_btn);

        // 读取已保存的配置
        urlInput.setText(prefs.getString("webAppUrl", ""));
        autoStartSwitch.setChecked(prefs.getBoolean("autoStart", false));

        // 授权悬浮窗权限
        permissionBtn.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
                startActivityForResult(i, REQ_OVERLAY);
            } else {
                Toast.makeText(this, "当前系统版本无需特别授权", Toast.LENGTH_SHORT).show();
            }
        });

        // 启动悬浮窗
        startBtn.setOnClickListener(v -> {
            if (!canDrawOverlays()) {
                Toast.makeText(this, "❌ 请先点击上方按钮授予悬浮窗权限", Toast.LENGTH_LONG).show();
                return;
            }
            String url = urlInput.getText().toString().trim();
            prefs.edit()
                .putString("webAppUrl", url)
                .putBoolean("autoStart", autoStartSwitch.isChecked())
                .apply();

            startService(new Intent(this, FloatingService.class));
            Toast.makeText(this,
                "✅ 悬浮窗已启动！按 Home 键回到主屏幕，点击 📹 按钮开始登记",
                Toast.LENGTH_LONG).show();
            moveTaskToBack(true);
        });

        // 停止悬浮窗
        stopBtn.setOnClickListener(v -> {
            stopService(new Intent(this, FloatingService.class));
            Toast.makeText(this, "悬浮窗已停止", Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        updatePermissionStatus();
    }

    private void updatePermissionStatus() {
        if (canDrawOverlays()) {
            permStatusText.setText("✅ 悬浮窗权限已授予");
            permStatusText.setTextColor(0xFF34D399);
        } else {
            permStatusText.setText("❌ 未授权，请点击下方按钮");
            permStatusText.setTextColor(0xFFF87171);
        }
    }

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }
}
