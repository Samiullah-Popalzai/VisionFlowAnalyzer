package com.apro.MaherQuickBetAnalyzer;

import android.content.Context;
import android.view.View;
import android.widget.TextView;

import java.util.*;

public class Prediction {

    private TextView option;

    static class Item {
        String name;
        double value;       // OCR value
        double multiplier;  // multiplier also as double now
        double normalizedScore;

        Item(String name, double multiplier, double value) {
            this.name = name;
            this.value = value;
            this.multiplier = multiplier;
            if (multiplier != 0) {
                this.normalizedScore = value / multiplier; // avoid divide by zero
            } else {
                this.normalizedScore = 0;
            }
        }
    }

    // Method 1: Input handler
    public static List<Item> getItemsFromInput(double[] values, double[] multipliers) {
        String[] names = {"\uD83C\uDF57", "\uD83C\uDF45", "\uD83D\uDC04", "\uD83C\uDF36\uFE0F",
                "\uD83D\uDC1F", "\uD83E\uDD55", "\uD83E\uDD90", "\uD83C\uDF3D"};
        List<Item> items = new ArrayList<>();
        for (int i = 0; i < names.length; i++) {
            items.add(new Item(names[i], multipliers[i], values[i]));
        }
        return items;
    }

    // Method 2: Logic processor
    public static void evaluateBettingOptions(List<Item> items, SamiLogger samiLogger) {
        // Sort by normalizedScore descending
        items.sort((a, b) -> Double.compare(b.normalizedScore, a.normalizedScore));
        OverlayService overlay = OverlayService.getInstance();
        Context context = overlay != null ? overlay.getApplicationContext() : null;

        for (int i = 0; i < items.size(); i++) {
            String tier;
            if (i == 0) tier = context.getString(R.string.best);
            else if (i == 1) tier = context.getString(R.string.strong);
            else if (i == 2) tier = context.getString(R.string.safe);
            else if (i <= 4) tier = context.getString(R.string.wildcard);
            else tier = context.getString(R.string.avoid);

            String message = String.format(
                    "%s: %s (Score: %.4f, Multiplier: %.3f, Value: %.3f)",
                    tier, items.get(i).name, items.get(i).normalizedScore,
                    items.get(i).multiplier, items.get(i).value
            );

            String viewId = "Option" + i;
            int resId = overlay.controllerView.getResources().getIdentifier(viewId, "id",
                    overlay.controllerView.getContext().getPackageName());

            TextView option = overlay.controllerView.findViewById(resId);
            if (option != null) {
                option.setText(tier + ":" + items.get(i).name);
            }
            samiLogger.log("Prediction BettingOptions", message);
        }
        AutoClickService service = AutoClickService.getInstance();
        if (service != null) {
            service.runPredictionClicks(items);
        }

    }

    // Main method to run the logic
    public void run(String[] itemNames, double[] multipliers, double[] values,
                    View controllerView, SamiLogger samiLogger) {
        if (itemNames.length != values.length || values.length != multipliers.length) {
            samiLogger.log("Prediction", "Array lengths do not match!");
            return;
        }
        samiLogger.log("Prediction", "values[]: " + Arrays.toString(values));
        samiLogger.log("Prediction", "multipliers[]: " + Arrays.toString(multipliers));

        // Check if any value is 0.0
        for (double v : values) {
            if (v == 0.0) {
                samiLogger.log("Prediction", "One or more values are 0.0. Stopping execution.");
                return;
            }
        }

        List<Item> itemsList = getItemsFromInput(values, multipliers);
        evaluateBettingOptions(itemsList, samiLogger);
        samiLogger.log("Prediction", "Prediction run completed.");
    }
}
