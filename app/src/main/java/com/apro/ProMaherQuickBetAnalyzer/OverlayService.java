package com.apro.ProMaherQuickBetAnalyzer;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

public class OverlayService extends Service {

    private static OverlayService instance;

    public static OverlayService getInstance() {
        return instance;
    }

    private WindowManager windowManager;
    public View controllerView;
    private DraggableOverlayView draggableOverlayView;
    private boolean touchEnabled = true;
    private Handler timeHandler = new Handler();
    private Runnable timeRunnable;
    private int timer;
    private int initialTimer = 30000;
    private ProgressBar timeProgressBar;
    private TextView progressText;
    public SamiLogger samiLogger;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        samiLogger = new SamiLogger();

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        // Inflate controller layout
        controllerView = LayoutInflater.from(this).inflate(R.layout.controller_layout, null);
        timeProgressBar = controllerView.findViewById(R.id.circularProgress);
        progressText = controllerView.findViewById(R.id.progressText);

        timeProgressBar.setProgress(initialTimer / 1000);
        progressText.setText(String.valueOf(initialTimer / 1000));

        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Controller layout params
        WindowManager.LayoutParams controllerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        controllerParams.gravity = Gravity.TOP | Gravity.START;
        controllerParams.x = 100;
        controllerParams.y = 300;

        // Draggable overlay rectangles
        draggableOverlayView = new DraggableOverlayView(this);
        WindowManager.LayoutParams draggableParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        draggableParams.gravity = Gravity.TOP | Gravity.START;

        // Add views to window
        windowManager.addView(draggableOverlayView, draggableParams);
        windowManager.addView(controllerView, controllerParams);

        // Controller draggable
        controllerView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX, initialY;
            private float initialTouchX, initialTouchY;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = controllerParams.x;
                        initialY = controllerParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        return true;
                    case MotionEvent.ACTION_MOVE:
                        controllerParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        controllerParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(controllerView, controllerParams);
                        return true;
                }
                return false;
            }
        });

        // Buttons
        Button btnClose = controllerView.findViewById(R.id.btnClose);
        Button btnStart = controllerView.findViewById(R.id.btnStart);
        Button btnToggleTouch = controllerView.findViewById(R.id.btnToggleTouch);

        btnClose.setOnClickListener(v -> stopService());

        btnToggleTouch.setOnClickListener(v -> toggleTouch(draggableParams, btnToggleTouch));

        btnStart.setOnClickListener(v -> startCaptureAndTimer());






        // Rectangle size control
        setupRectangleSizeControl();
    }

    private void stopService() {
        stopSelf();
        android.os.Process.killProcess(android.os.Process.myPid());
        System.exit(0);
    }

    private void toggleTouch(WindowManager.LayoutParams params, Button btnToggleTouch) {
        touchEnabled = !touchEnabled;
        if (touchEnabled) {
            params.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            draggableOverlayView.setVisibility(View.VISIBLE);
            btnToggleTouch.setText("Enable Touch");
        } else {
            params.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
            draggableOverlayView.setVisibility(View.GONE);
            btnToggleTouch.setText("Show Rectangles");
        }
        if (draggableOverlayView != null && draggableOverlayView.getParent() != null) {
            windowManager.updateViewLayout(draggableOverlayView, params);
        }
    }

    private void startCaptureAndTimer() {
        startReduceTimer();

        // Start ScreenshotCaptureService here
        Intent screenshotIntent = new Intent(this, ScreenshotCaptureService.class);
        screenshotIntent.putExtra("viewWidth", draggableOverlayView.getWidth());
        screenshotIntent.putExtra("viewHeight", draggableOverlayView.getHeight());
        startService(screenshotIntent);
    }

    private void startReduceTimer() {
        timer = initialTimer;
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                timer -= 1000;
                if (timeProgressBar != null) timeProgressBar.setProgress(Math.max(timer / 1000, 0));
                timeHandler.postDelayed(this, 1000);
            }
        };
        timeHandler.post(timeRunnable);
    }

    private void setupRectangleSizeControl() {
        android.content.SharedPreferences prefs = getSharedPreferences("rect_prefs", MODE_PRIVATE);
        int savedSize = prefs.getInt("rect_size", 350);

        SeekBar rectSizeSeekBar = controllerView.findViewById(R.id.rectSizeSeekBar);
        TextView rectSizeText = controllerView.findViewById(R.id.rectSizeText);

        rectSizeSeekBar.setMax(600);
        rectSizeSeekBar.setProgress(savedSize);
        rectSizeText.setText("Rectangle Size: " + savedSize);

        for (RectangleData rect : DraggableOverlayView.savedRectangles) {
            rect.width = savedSize;
            rect.height = savedSize;
        }
        draggableOverlayView.invalidate();

        rectSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = Math.max(progress, 50);
                rectSizeText.setText("Rectangle Size: " + newSize);
                for (RectangleData rect : DraggableOverlayView.savedRectangles) {
                    rect.width = newSize;
                    rect.height = newSize;
                    rect.x = Math.max(0, Math.min(rect.x, draggableOverlayView.getWidth() - rect.width));
                    rect.y = Math.max(0, Math.min(rect.y, draggableOverlayView.getHeight() - rect.height));
                }
                draggableOverlayView.invalidate();
                prefs.edit().putInt("rect_size", newSize).apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null;
        if (controllerView != null && controllerView.getParent() != null)
            windowManager.removeView(controllerView);
        if (draggableOverlayView != null && draggableOverlayView.getParent() != null)
            windowManager.removeView(draggableOverlayView);
        timeHandler.removeCallbacks(timeRunnable);
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }


}
