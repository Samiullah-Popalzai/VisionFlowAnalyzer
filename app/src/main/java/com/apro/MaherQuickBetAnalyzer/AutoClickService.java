package com.apro.MaherQuickBetAnalyzer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.content.ComponentName;
import android.content.Context;
import android.provider.Settings;
import android.graphics.Path;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;

import java.util.List;

public class AutoClickService extends AccessibilityService {

    private static AutoClickService instance;

    public static AutoClickService getInstance() {
        return instance;
    }

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d("AutoClickService", "Service connected");
    }

    @Override
    public void onAccessibilityEvent(AccessibilityEvent event) {
        // Not used
    }

    @Override
    public void onInterrupt() {
        Log.d("AutoClickService", "Service interrupted");
    }

    public static boolean isAccessibilityServiceEnabled(Context context) {
        String enabledServices = Settings.Secure.getString(
                context.getContentResolver(),
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        );
        if (enabledServices == null) return false;
        ComponentName cn = new ComponentName(context, AutoClickService.class);
        return enabledServices.toLowerCase().contains(cn.flattenToString().toLowerCase());
    }

    public void performClick(int x, int y) {
        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke =
                new GestureDescription.StrokeDescription(clickPath, 0, 100);

        GestureDescription gesture = new GestureDescription.Builder()
                .addStroke(stroke)
                .build();

        boolean result = dispatchGesture(gesture, new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                Log.d("AutoClickService", "Click SUCCESS at: " + x + "," + y);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                Log.d("AutoClickService", "Click FAILED at: " + x + "," + y);
            }
        }, null);

        if (!result) {
            Log.d("AutoClickService", "Click dispatch rejected at: " + x + "," + y);
        }
    }

    public void runPredictionClicks(List<Prediction.Item> items) {
        if (items == null || items.isEmpty()) return;

        int maxClicks = Math.min(items.size(), 4); // Best, Strong, Safe, Wildcards
        int delay = 0;

        for (int i = 0; i < maxClicks; i++) {
            String target = mapEmojiToName(items.get(i).name);

            for (RectangleData rect : DraggableOverlayView.savedRectangles) {
                if (rect.name != null && rect.name.equals(target)) {
                    int cx = rect.x + rect.width / 2;
                    int cy = rect.y + rect.height / 2;

                    Path path = new Path();
                    path.moveTo(cx, cy);

                    GestureDescription gesture = new GestureDescription.Builder()
                            .addStroke(new GestureDescription.StrokeDescription(path, delay, 100))
                            .build();

                    final int finalI = i;
                    dispatchGesture(gesture, new GestureResultCallback() {
                        @Override
                        public void onCompleted(GestureDescription gestureDescription) {
                            Log.d("AutoClickService", "Click SUCCESS: " + target + " at (" + cx + "," + cy + ")");
                            // Do not stop service here
                        }

                        @Override
                        public void onCancelled(GestureDescription gestureDescription) {
                            Log.d("AutoClickService", "Click CANCELLED: " + target + " at (" + cx + "," + cy + ")");
                        }
                    }, null);

                    delay += 300; // space out clicks
                    break;
                }
            }
        }
    }

    // helper inside AutoClickService
    private String mapEmojiToName(String emoji) {
        switch (emoji) {
            case "🍗": return "Chicken";
            case "🍅": return "Tomato";
            case "🐄": return "Cow";
            case "🌶️": return "Pepper";
            case "🐟": return "Fish";
            case "🥕": return "Carrot";
            case "🦐": return "Shrimp";
            case "🌽": return "Corn";
            default: return "";
        }
    }

}
