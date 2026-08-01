package com.ytregister;

import android.annotation.SuppressLint;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.IBinder;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.core.app.NotificationCompat;

public class FloatingService extends Service {

    private WindowManager windowManager;
    private View floatingBtnView;
    private View floatingPanelView;
    private WebView webView;
    private boolean panelShowing = false;

    private int initX, initY;
    private float initTouchX, initTouchY;
    private long touchDownTime;
    private static final long CLICK_MAX_DURATION = 250;
    private static final float CLICK_MAX_DISTANCE = 15f;

    private static final String CHANNEL_ID = "yt_register_ch";
    private static final int NOTIF_ID = 101;

    @Override
    public void onCreate() {
        super.onCreate();
        createNotificationChannel();
        startForeground(NOTIF_ID, buildNotification());
        initFloatingButton();
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL_ID, "YouTube 登记助手", NotificationManager.IMPORTANCE_LOW);
            ch.setDescription("悬浮窗服务通知栏");
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm != null) nm.createNotificationChannel(ch);
        }
    }

    private Notification buildNotification() {
        PendingIntent pi = PendingIntent.getActivity(
            this, 0, new Intent(this, MainActivity.class),
            PendingIntent.FLAG_IMMUTABLE);
        return new NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("YouTube 登记助手运行中")
            .setContentText("点击屏幕上的 📹 悬浮按钮开始登记")
            .setSmallIcon(android.R.drawable.ic_menu_edit)
            .setContentIntent(pi)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void initFloatingButton() {
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        floatingBtnView = LayoutInflater.from(this).inflate(R.layout.floating_button, null);

        final WindowManager.LayoutParams btnParams = new WindowManager.LayoutParams(
            dpToPx(64), dpToPx(64),
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT);
        btnParams.gravity = Gravity.TOP | Gravity.START;
        btnParams.x = dpToPx(12);
        btnParams.y = dpToPx(200);
        windowManager.addView(floatingBtnView, btnParams);

        floatingBtnView.setOnTouchListener((v, event) -> {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = btnParams.x;
                    initY = btnParams.y;
                    initTouchX = event.getRawX();
                    initTouchY = event.getRawY();
                    touchDownTime = System.currentTimeMillis();
                    return true;
                case MotionEvent.ACTION_MOVE:
                    btnParams.x = initX + (int)(event.getRawX() - initTouchX);
                    btnParams.y = initY + (int)(event.getRawY() - initTouchY);
                    windowManager.updateViewLayout(floatingBtnView, btnParams);
                    return true;
                case MotionEvent.ACTION_UP:
                    float dx = Math.abs(event.getRawX() - initTouchX);
                    float dy = Math.abs(event.getRawY() - initTouchY);
                    long dt = System.currentTimeMillis() - touchDownTime;
                    if (dx < CLICK_MAX_DISTANCE && dy < CLICK_MAX_DISTANCE && dt < CLICK_MAX_DURATION) {
                        togglePanel();
                    }
                    return true;
            }
            return false;
        });
    }

    private void togglePanel() {
        if (panelShowing) hidePanel();
        else showPanel();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private void showPanel() {
        if (panelShowing) return;

        DisplayMetrics dm = new DisplayMetrics();
        windowManager.getDefaultDisplay().getMetrics(dm);
        int panelW = (int)(dm.widthPixels * 0.94f);
        int panelH = (int)(dm.heightPixels * 0.82f);

        floatingPanelView = LayoutInflater.from(this).inflate(R.layout.floating_panel, null);
        webView = floatingPanelView.findViewById(R.id.panel_webview);

        WebSettings ws = webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setCacheMode(WebSettings.LOAD_DEFAULT);
        ws.setMixedContentMode(WebSettings.MIXED_CONTENT_ALWAYS_ALLOW);
        ws.setAllowFileAccess(true);
        ws.setAllowFileAccessFromFileURLs(true);
        ws.setAllowUniversalAccessFromFileURLs(true);

        webView.addJavascriptInterface(new AndroidBridge(), "AndroidBridge");
        webView.setBackgroundColor(0xFF0F172A);

        webView.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView view, String url) {
                // 注入已保存的 Web App URL
                SharedPreferences prefs = getSharedPreferences("YTRegister", MODE_PRIVATE);
                String savedUrl = prefs.getString("webAppUrl", "");
                if (!savedUrl.isEmpty()) {
                    String escaped = savedUrl.replace("\\", "\\\\").replace("'", "\\'");
                    view.evaluateJavascript(
                        "(function(){" +
                        "  localStorage.setItem('mWebAppUrl','" + escaped + "');" +
                        "  var el=document.getElementById('webAppUrl');" +
                        "  if(el) el.value='" + escaped + "';" +
                        "})()", null);
                }
            }
        });

        webView.loadUrl("file:///android_asset/register.html");

        // 关闭按钮
        View closeBtn = floatingPanelView.findViewById(R.id.panel_close);
        closeBtn.setOnClickListener(v -> hidePanel());

        int overlayType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            : WindowManager.LayoutParams.TYPE_PHONE;

        WindowManager.LayoutParams panelParams = new WindowManager.LayoutParams(
            panelW, panelH, overlayType,
            WindowManager.LayoutParams.FLAG_DIM_BEHIND,
            PixelFormat.TRANSLUCENT);
        panelParams.gravity = Gravity.CENTER;
        panelParams.dimAmount = 0.55f;

        windowManager.addView(floatingPanelView, panelParams);
        panelShowing = true;
    }

    private void hidePanel() {
        if (floatingPanelView != null) {
            windowManager.removeView(floatingPanelView);
            floatingPanelView = null;
            webView = null;
        }
        panelShowing = false;
    }

    private int dpToPx(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (floatingBtnView != null) windowManager.removeView(floatingBtnView);
        if (floatingPanelView != null) windowManager.removeView(floatingPanelView);
    }

    @Override
    public IBinder onBind(Intent intent) { return null; }

    /** JavaScript ↔ Java 通信桥接 */
    public class AndroidBridge {
        @JavascriptInterface
        public void closePanel() {
            hidePanel();
        }

        @JavascriptInterface
        public String getSavedUrl() {
            return getSharedPreferences("YTRegister", MODE_PRIVATE)
                .getString("webAppUrl", "");
        }
    }
}
