package com.apro.ProMaherQuickBetAnalyzer;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.graphics.*;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;

import androidx.annotation.Nullable;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.File;
import java.io.FileOutputStream;
import java.math.BigDecimal;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class ScreenshotCaptureService extends Service {
    private boolean isMediaProjectionStarted = false;
    private volatile boolean isProcessing = false;

    static int resultCode;
    static Intent dataIntent;

    private MediaProjection mediaProjection;
    private VirtualDisplay virtualDisplay;
    private ImageReader imageReader;
    private Intent startIntent;
    private final String[] rectOrder = {"Chicken", "Tomato", "Cow", "Pepper", "Fish", "Carrot", "Shrimp", "Corn"};
    private final String[] rectNames = new String[rectOrder.length];
    double[] multipliers = new double[rectOrder.length]; // ✅ allow decimals
    double[] values = new double[rectOrder.length];
    private int sizeCounter;
    public View controllerView;

    private ExecutorService ocrExecutor;
    private SamiLogger samilogger;

    @Override
    public void onCreate() {
        super.onCreate();
        controllerView = LayoutInflater.from(this).inflate(R.layout.controller_layout, null);
        ocrExecutor = Executors.newSingleThreadExecutor();
        samilogger = new SamiLogger();
        samilogger.log("Service", "ScreenshotCaptureService created");
        Log.d("ScreenshotService", "Service created");
    }

    public static void setPermissionResult(int code, Intent data) {
        resultCode = code;
        dataIntent = data;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        startIntent = intent;

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel("screen_capture_channel", "Screen Capture", NotificationManager.IMPORTANCE_LOW);
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager.getNotificationChannel("screen_capture_channel") == null) {
                manager.createNotificationChannel(channel);
            }
            Notification notification = new Notification.Builder(this, "screen_capture_channel")
                    .setContentTitle("Screen Capture Active")
                    .setContentText("Capturing screen…")
                    .setSmallIcon(R.drawable.ic_launcher_foreground)
                    .setOngoing(true)
                    .build();
            startForeground(1, notification);
        }

        samilogger.log("Service", "Starting screenshot capture");
        Log.d("ScreenshotService", "Starting screenshot capture");

        startScreenshotOnce();
        return START_NOT_STICKY; // stop after done
    }

    private void startScreenshotOnce() {
        if (isMediaProjectionStarted) return;

        if (mediaProjection == null) {
            MediaProjectionManager manager = (MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE);
            mediaProjection = manager.getMediaProjection(resultCode, dataIntent);
        }

        if (mediaProjection == null) {
            samilogger.log("Service", "MediaProjection is null, stopping service");
            Log.d("ScreenshotService", "MediaProjection is null, stopping service");
            stopSelf();
            return;
        }

        WindowManager windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getRealSize(size);
        int width = size.x;
        int height = size.y;

        if (imageReader == null) {
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2);
        }

        if (virtualDisplay == null) {
            virtualDisplay = mediaProjection.createVirtualDisplay("screenshot",
                    width, height, getResources().getDisplayMetrics().densityDpi,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR, imageReader.getSurface(), null, null);
        }

        isMediaProjectionStarted = true;
        samilogger.log("Service", "MediaProjection started");
        Log.d("ScreenshotService", "MediaProjection started");

        // Delay a bit to allow VirtualDisplay to initialize
        new Handler().postDelayed(this::captureFrameOnce, 200);
    }

    private void captureFrameOnce() {
        if (!isMediaProjectionStarted || isProcessing) return;

        isProcessing = true;

        ocrExecutor.execute(() -> {
            Bitmap bitmap = imageFromReader(imageReader, imageReader.getWidth(), imageReader.getHeight());
            if (bitmap != null && startIntent != null) {
                int overlayWidth = controllerView.getWidth();
                int overlayHeight = controllerView.getHeight();
                if (overlayWidth <= 0) overlayWidth = startIntent.getIntExtra("viewWidth", bitmap.getWidth());
                if (overlayHeight <= 0) overlayHeight = startIntent.getIntExtra("viewHeight", bitmap.getHeight());

                recognizeTextInRectangles(bitmap, overlayWidth, overlayHeight);
            }
            isProcessing = false;

            // After single run, stop the service
            stopSelf();
        });
    }

    private Bitmap imageFromReader(ImageReader reader, int width, int height) {
        try (Image image = reader.acquireLatestImage()) {
            if (image == null) return null;
            Image.Plane[] planes = image.getPlanes();
            ByteBuffer buffer = planes[0].getBuffer();
            int pixelStride = planes[0].getPixelStride();
            int rowStride = planes[0].getRowStride();
            int rowPadding = rowStride - pixelStride * width;
            Bitmap bitmap = Bitmap.createBitmap(width + rowPadding / pixelStride, height, Bitmap.Config.ARGB_8888);
            bitmap.copyPixelsFromBuffer(buffer);
            return Bitmap.createBitmap(bitmap, 0, 0, width, height);
        } catch (Exception e) {
            samilogger.log("Service", "Failed to read image: " + e.getMessage());
            Log.e("ScreenshotService", "Failed to read image: " + e.getMessage());
            return null;
        }
    }

    private void recognizeTextInRectangles(Bitmap fullBitmap, int overlayWidth, int overlayHeight) {
        if (fullBitmap == null || overlayWidth <= 0 || overlayHeight <= 0) return;
        float scaleX = (float) fullBitmap.getWidth() / overlayWidth;
        float scaleY = (float) fullBitmap.getHeight() / overlayHeight;

        int margin = 5; // extra pixels at top

        for (int i = 0; i < DraggableOverlayView.savedRectangles.size(); i++) {
            RectangleData rect = DraggableOverlayView.savedRectangles.get(i);
            if (rect == null) continue;
            int left = Math.max((int) (rect.x * scaleX), 0);
            int top = Math.max((int) (rect.y * scaleY) - margin, 0);
            int right = Math.min((int) ((rect.x + rect.width) * scaleX), fullBitmap.getWidth());
            int bottom = Math.min((int) ((rect.y + rect.height) * scaleY), fullBitmap.getHeight());
            int width = right - left;
            int height = bottom - top;
            if (width <= 0 || height <= 0) continue;

            try {
                Bitmap cropped = Bitmap.createBitmap(fullBitmap, left, top, width, height);
                saveCroppedBitmap(cropped, rect.name != null ? rect.name : "rect_" + i);
                String rectName = (rect.name != null && !rect.name.isEmpty()) ? rect.name : ("rect_" + i);
                recognizeText(cropped, rectName);
            } catch (Exception e) {
                samilogger.log("Service", "Failed to crop rectangle " + rect.name + ": " + e.getMessage());
                Log.e("ScreenshotService", "Failed to crop rectangle " + rect.name + ": " + e.getMessage());
            }
        }
    }

    private void saveCroppedBitmap(Bitmap bitmap, String name) {
        try {
            File dir = new File(getExternalFilesDir(Environment.DIRECTORY_PICTURES), "rectangles_debug");
            if (!dir.exists()) dir.mkdirs();
            String fileName = name + ".png"; // overwrite previous
            File file = new File(dir, fileName);
            try (FileOutputStream fos = new FileOutputStream(file)) {
                bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos);
                samilogger.log("Service", "Saved rectangle: " + file.getAbsolutePath());
                Log.d("ScreenshotService", "Saved rectangle: " + file.getAbsolutePath());
            }
        } catch (Exception e) {
            samilogger.log("Service", "Failed to save rectangle: " + e.getMessage());
            Log.e("ScreenshotService", "Failed to save rectangle: " + e.getMessage());
        }
    }

    private void recognizeText(Bitmap bitmap, String rectName) {
        if (bitmap == null) return;
        InputImage image = InputImage.fromBitmap(bitmap, 0);
        TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
                .process(image)
                .addOnSuccessListener(result -> {
                    String allText = result.getText();
                    samilogger.log("OCR", rectName + ": " + allText);

                    // ✅ Support integers, floats with . or ,
                    Pattern numberPattern = Pattern.compile("\\d+[.,]?\\d*");
                    Matcher matcher = numberPattern.matcher(allText);

                    Double foundMultiplier = null;
                    Double foundValue = null;

                    while (matcher.find()) {
                        String rawNum = matcher.group();
                        if (rawNum == null) continue;

                        // normalize commas to dots
                        String cleaned = rawNum.replace(",", ".").trim();

                        try {
                            double parsed = new BigDecimal(cleaned).doubleValue();
                            if (foundMultiplier == null) {
                                foundMultiplier = parsed;
                            } else if (foundValue == null) {
                                foundValue = parsed;
                            }
                        } catch (Exception e) {
                            Log.e("ScreenshotService", "Failed parsing number: " + rawNum);
                        }
                    }

                    double multiplier = (foundMultiplier != null) ? foundMultiplier : 0.0;
                    double value = (foundValue != null) ? foundValue : 0.0;

                    int index = -1;
                    for (int i = 0; i < rectOrder.length; i++) {
                        if (rectOrder[i].equals(rectName)) { index = i; break; }
                    }
                    if (index != -1) {
                        rectNames[index] = rectName;
                        values[index] = value;
                        multipliers[index] = multiplier;
                        sizeCounter++;

                        if (sizeCounter >= 8) {
                            // Log full arrays before running Prediction
                            StringBuilder namesStr = new StringBuilder();
                            StringBuilder multipliersStr = new StringBuilder();
                            StringBuilder valuesStr = new StringBuilder();
                            for (int j = 0; j < rectOrder.length; j++) {
                                namesStr.append(rectNames[j]).append(", ");
                                multipliersStr.append(multipliers[j]).append(", ");
                                valuesStr.append(values[j]).append(", ");
                            }
                            samilogger.log("Prediction of service", "rectNames: " + namesStr);
                            samilogger.log("Prediction of service", "multipliers: " + multipliersStr);
                            samilogger.log("Prediction of service", "values: " + valuesStr);

                            sizeCounter = 0;
                            new Prediction().run(rectNames, multipliers, values, controllerView, samilogger);
                        }
                    }
                })
                .addOnFailureListener(e -> samilogger.log("OCR", "Failed " + rectName + ": " + e.getMessage()));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mediaProjection != null) mediaProjection.stop();
        if (virtualDisplay != null) virtualDisplay.release();
        if (imageReader != null) imageReader.close();
        if (ocrExecutor != null) ocrExecutor.shutdownNow();
        isProcessing = false;
        isMediaProjectionStarted = false;
        samilogger.log("Service", "ScreenshotCaptureService destroyed");
        Log.d("ScreenshotService", "Service destroyed");
        stopSelf();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
