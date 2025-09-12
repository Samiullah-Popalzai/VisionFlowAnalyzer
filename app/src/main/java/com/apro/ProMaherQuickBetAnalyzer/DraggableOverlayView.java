package com.apro.ProMaherQuickBetAnalyzer;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.view.MotionEvent;
import android.view.View;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;

public class DraggableOverlayView extends View {

    private final ArrayList<RectangleData> circles = new ArrayList<>();
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    // Replace CircleData with RectangleData
    private final ArrayList<RectangleData> rectangles = new ArrayList<>();
    public static ArrayList<RectangleData> savedRectangles = new ArrayList<>();
    private int draggingIndex = -1;
    private float offsetX, offsetY;

    public static ArrayList<RectangleData> savedCircles = new ArrayList<>();

    public DraggableOverlayView(Context context) {
        super(context);

        paint.setColor(ContextCompat.getColor(context, R.color.rectangle));
        paint.setAlpha(150);
        paint.setStyle(Paint.Style.FILL);
        loadRectanglePositions(); // load saved positions

    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(Color.WHITE);
        textPaint.setTextSize(40f);

        Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        borderPaint.setColor(Color.BLACK); // border color
        borderPaint.setStyle(Paint.Style.STROKE); // only stroke
        borderPaint.setStrokeWidth(6f); // border thickness
        borderPaint.setColor(ContextCompat.getColor(getContext(),R.color.rectangleborder));
        for (RectangleData rect : savedRectangles) {
            // draw filled rectangle
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, paint);

            // draw border
            canvas.drawRect(rect.x, rect.y, rect.x + rect.width, rect.y + rect.height, borderPaint);

            // draw name inside rectangle (top-left corner)
            canvas.drawText(rect.name, rect.x + 20, rect.y + 50, textPaint);
        }
    }



    @Override
    public boolean onTouchEvent(MotionEvent event) {
        float x = event.getX();
        float y = event.getY();

        switch (event.getAction()) {
            case MotionEvent.ACTION_DOWN:
                for (int i = 0; i < savedRectangles.size(); i++) {
                    RectangleData r = savedRectangles.get(i);
                    if (x >= r.x && x <= r.x + r.width && y >= r.y && y <= r.y + r.height) {
                        draggingIndex = i;
                        offsetX = x - r.x;
                        offsetY = y - r.y;
                        return true;
                    }
                }
                return false;

            case MotionEvent.ACTION_MOVE:
                if (draggingIndex != -1) {
                    RectangleData r = savedRectangles.get(draggingIndex);

                    int newX = (int) (x - offsetX);
                    int newY = (int) (y - offsetY);

                    int maxX = getWidth() - r.width;
                    int maxY = getHeight() - r.height;

                    r.x = Math.max(0, Math.min(newX, maxX));
                    r.y = Math.max(0, Math.min(newY, maxY));

                    invalidate();
                    return true;
                }
                return false;

            case MotionEvent.ACTION_UP:
                draggingIndex = -1;
                saveRectanglePositions();
                return true;
        }
        return false; // Pass through touch for background
    }

    private void saveRectanglePositions() {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("rect_prefs", Context.MODE_PRIVATE);
        android.content.SharedPreferences.Editor editor = prefs.edit();

        for (int i = 0; i < savedRectangles.size(); i++) {
            RectangleData r = savedRectangles.get(i);
            editor.putInt("rect_" + i + "_x", r.x);
            editor.putInt("rect_" + i + "_y", r.y);
        }

        editor.apply();
    }

    private void loadRectanglePositions() {
        Context context = getContext();
        android.content.SharedPreferences prefs = context.getSharedPreferences("rect_prefs", Context.MODE_PRIVATE);

        savedRectangles.clear();

        // Load saved rectangle size or default
        int rectSize = prefs.getInt("rect_size", 350);

        int screenWidth = getResources().getDisplayMetrics().widthPixels;
        int screenHeight = getResources().getDisplayMetrics().heightPixels;

        String[] names = {"Chicken", "Tomato", "Cow", "Pepper", "Fish", "Carrot", "Shrimp", "Corn"};

        for (int i = 0; i < 8; i++) {
            int defaultX = 200 + (i % 4) * 250;
            int defaultY = 400 + (i / 4) * 300;

            int x = prefs.getInt("rect_" + i + "_x", defaultX);
            int y = prefs.getInt("rect_" + i + "_y", defaultY);

            x = Math.max(0, Math.min(x, screenWidth - rectSize));
            y = Math.max(0, Math.min(y, screenHeight - rectSize));

            savedRectangles.add(new RectangleData(x, y, rectSize, rectSize, names[i]));
        }
    }

}
