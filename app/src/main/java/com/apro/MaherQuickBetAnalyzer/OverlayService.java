package com.apro.MaherQuickBetAnalyzer;

import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
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

import java.io.File;
import java.io.FileOutputStream;

public class OverlayService extends Service {
    private static OverlayService instance;

    public static OverlayService getInstance() {
        return instance;
    }
    private WindowManager windowManager;
    public View controllerView;
    private DraggableOverlayView draggableOverlayView;
    private boolean touchEnabled = true; // default: touch is enabled
    private Intent screenshotIntent;

    private Handler timeHandler = new Handler();
    private Handler screenshotHandler = new Handler();
    private Runnable timeRunnable;
    private Runnable screenshotRunnable;
    private int timer;
    private int initialTimer=30000;
    private ProgressBar timeProgressBar;
    private TextView progressText;

    //options TextView
    private TextView best;
    private SamiLogger samiLogger;
    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;
        samiLogger=new SamiLogger();
        screenshotIntent = new Intent(this, ScreenshotCaptureService.class);

        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        controllerView = LayoutInflater.from(this).inflate(R.layout.controller_layout, null);
        timeProgressBar = controllerView.findViewById(R.id.circularProgress);
        timeProgressBar.setProgress(initialTimer/1000);
        progressText = controllerView.findViewById(R.id.progressText);
        progressText.setText(String.valueOf(initialTimer/1000));


        int layoutType = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_PHONE;

        // Controller layout params
        WindowManager.LayoutParams controllerParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE |
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT
        );
        controllerParams.gravity = Gravity.TOP | Gravity.START;
        controllerParams.x = 100;
        controllerParams.y = 300;



        // Create 8 draggable circle views
         draggableOverlayView = new DraggableOverlayView(this);

        WindowManager.LayoutParams draggableParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                layoutType,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                PixelFormat.TRANSLUCENT
        );
        draggableParams.gravity = Gravity.TOP | Gravity.START;
        Button btnToggleTouch = controllerView.findViewById(R.id.btnToggleTouch);

        btnToggleTouch.setOnClickListener(v -> {
            touchEnabled = !touchEnabled;

            if (touchEnabled) {
                // Enable touch
                draggableParams.flags &= ~WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                draggableOverlayView.setVisibility(View.VISIBLE);
                btnToggleTouch.setText("Enable Touch");
            } else {
                // Disable touch
                draggableParams.flags |= WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
                draggableOverlayView.setVisibility(View.GONE);
                btnToggleTouch.setText("Show Rectangles");
            }

            if (draggableOverlayView != null && draggableOverlayView.getParent() != null) {
                windowManager.updateViewLayout(draggableOverlayView, draggableParams);
            }

        });

        //views
        windowManager.addView(draggableOverlayView, draggableParams);
        windowManager.addView(controllerView, controllerParams);

        // Make controller draggable
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

        Button btnClose = controllerView.findViewById(R.id.btnClose);
        Button btnStart = controllerView.findViewById(R.id.btnStart);

        btnClose.setOnClickListener(v -> {
            stopSelf();
            // Create an screenshotIntent to stop the ScreenshotCaptureService
            Intent screenshotServiceIntent = new Intent(this, ScreenshotCaptureService.class);
            stopService(screenshotServiceIntent);  // This will stop the ScreenshotCaptureService
            // Finish all activities
            android.os.Process.killProcess(android.os.Process.myPid());
            System.exit(0);
            onDestroy();
        });

        btnStart.setOnClickListener(v -> {
            try{
                startReduceTimer();
                // startService(screenshotIntent); // 🔁 Start again
                screenshotIntent.putExtra("viewWidth", draggableOverlayView .getWidth());
                screenshotIntent.putExtra("viewHeight", draggableOverlayView .getHeight());
                startService(screenshotIntent);
            }catch( Exception e){
                try {
                    File file = new File(getExternalFilesDir(null), "error");
                    FileOutputStream out = new FileOutputStream(file);
                    out.close();
                    samiLogger.log("StartBt", "Saved error to: " + file.getAbsolutePath());
                    Toast.makeText(instance, "Saved error to: " + file.getAbsolutePath(), Toast.LENGTH_LONG).show();
                } catch (Exception er) {
                    samiLogger.log("StartBt", "Error saving file: " + er.getMessage());
                }
            }
        });

        //Control Rectangle Size
        // Get saved size or default
        android.content.SharedPreferences prefs = getSharedPreferences("rect_prefs", MODE_PRIVATE);
        int savedSize = prefs.getInt("rect_size", 350); // default 350

        SeekBar rectSizeSeekBar = controllerView.findViewById(R.id.rectSizeSeekBar);
        TextView rectSizeText = controllerView.findViewById(R.id.rectSizeText);
        rectSizeSeekBar.setMax(600);
        rectSizeSeekBar.setProgress(savedSize);
        rectSizeText.setText("Rectangle Size: " + savedSize);
        // Apply saved size to rectangles
        for (RectangleData rect : DraggableOverlayView.savedRectangles) {
            rect.width = savedSize;
            rect.height = savedSize;
        }
        draggableOverlayView.invalidate();

        rectSizeSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int newSize = Math.max(progress, 50); // minimum size 50
                rectSizeText.setText("Rectangle Size: " + newSize);

                // Update all rectangles
                for (RectangleData rect : DraggableOverlayView.savedRectangles) {
                    rect.width = newSize;
                    rect.height = newSize;

                    // Clamp position so resized rectangle stays on screen
                    int maxX = draggableOverlayView.getWidth() - rect.width;
                    int maxY = draggableOverlayView.getHeight() - rect.height;

                    rect.x = Math.max(0, Math.min(rect.x, maxX));
                    rect.y = Math.max(0, Math.min(rect.y, maxY));
                }

                // Redraw rectangles
                draggableOverlayView.invalidate();

                // Save size in SharedPreferences
                android.content.SharedPreferences.Editor editor = prefs.edit();
                editor.putInt("rect_size", newSize);
                editor.apply();
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) { }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) { }
        });




    }


    private void startReduceTimer() {
        timeRunnable = new Runnable() {
            @Override
            public void run() {
                if (timer<=0){
                    timer=initialTimer;
                }




                timer-=1000; // ⏳ reduce and update progress
                if (timeProgressBar != null) {
                    timeProgressBar.setProgress(Math.max(timer / 1000, 0));
                }

                timeHandler.postDelayed(this, 1000); // ⏱ repeat every 1 sec
            }
        };
        timeHandler.post(timeRunnable);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        instance = null; // release reference
        try {
            if (controllerView != null && controllerView.getParent() != null)
                windowManager.removeView(controllerView);

            if (draggableOverlayView != null && draggableOverlayView.getParent() != null)
                windowManager.removeView(draggableOverlayView);
        } catch (Exception e) {
            samiLogger.log("OverlayService", "Error removing view: " + e.getMessage());
        }

        timeHandler.removeCallbacks(timeRunnable);
        timeHandler.removeCallbacksAndMessages(null); // stop timer
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
