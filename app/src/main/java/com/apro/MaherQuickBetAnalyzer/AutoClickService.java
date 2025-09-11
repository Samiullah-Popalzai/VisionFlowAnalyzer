package com.apro.MaherQuickBetAnalyzer;

import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.GestureDescription;
import android.graphics.Path;
import android.util.Log;

public class AutoClickService extends AccessibilityService {

    private static AutoClickService instance;

    @Override
    protected void onServiceConnected() {
        super.onServiceConnected();
        instance = this;
        Log.d("AutoClickService", "Service connected");
    }

    @Override
    public void onAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {
        // Not used for now
    }

    @Override
    public void onInterrupt() {
        Log.d("AutoClickService", "Service interrupted");
    }

    @Override
    public boolean onUnbind(android.content.Intent intent) {
        instance = null;
        return super.onUnbind(intent);
    }

    public static AutoClickService getInstance() {
        return instance;
    }

    public boolean isServiceActive() {
        return instance != null;
    }

    /**
     * Perform a click at given coordinates
     */
    public void performClick(int x, int y) {
        if (!isServiceActive()) {
            Log.d("AutoClickService", "Service not active, cannot perform click");
            return;
        }

        Path clickPath = new Path();
        clickPath.moveTo(x, y);

        GestureDescription.StrokeDescription stroke = new GestureDescription.StrokeDescription(clickPath, 0, 50);
        GestureDescription.Builder gestureBuilder = new GestureDescription.Builder();
        gestureBuilder.addStroke(stroke);

        dispatchGesture(gestureBuilder.build(), new GestureResultCallback() {
            @Override
            public void onCompleted(GestureDescription gestureDescription) {
                super.onCompleted(gestureDescription);
                Log.d("AutoClickService", "Click performed at: " + x + ", " + y);
            }

            @Override
            public void onCancelled(GestureDescription gestureDescription) {
                super.onCancelled(gestureDescription);
                Log.d("AutoClickService", "Click cancelled");
            }
        }, null);
    }
}
