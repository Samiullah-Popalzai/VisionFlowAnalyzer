package com.apro.MaherQuickBetAnalyzer;

import android.content.Intent;
import android.content.SharedPreferences;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.FirebaseApp;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;


public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_OVERLAY = 5469;
    private static final int REQUEST_SCREENSHOT = 1001;
    private SamiLogger samiLogger =null;
    private static final String REMOTE_URL =
            "https://raw.githubusercontent.com/2018samiullah/App-Access/refs/heads/main/Floating%20App%20(Maher%20Quick%20Bet%20Analyzer).txt";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
         samiLogger = new SamiLogger();
        FirebaseApp.initializeApp(this);
        setContentView(R.layout.activity_main);
        Intent accessibilityintent = new Intent(android.provider.Settings.ACTION_ACCESSIBILITY_SETTINGS);
        accessibilityintent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        startActivity(accessibilityintent);
        //checkLicenseAndLoadFragment();
        getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragment_container, new ActivatedFragment())
                .commit();
    }

    // ✅ Step 1: Check SharedPreferences and Firebase license
    private void checkLicenseAndLoadFragment() {
        SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
        String license = prefs.getString("License", "na");
        samiLogger.log("TAG22", "license: " +license);

        if (license.equals("na")) {
            // No license → show ActivationFragment
            getSupportFragmentManager().beginTransaction()
                    .replace(R.id.fragment_container, new ActivationFragment())
                    .commit();

        } else {
            FirebaseDatabase database = FirebaseDatabase.getInstance();
            DatabaseReference myRef = database.getReference(license);
            myRef.addValueEventListener(new ValueEventListener() {
                @Override
                public void onDataChange(DataSnapshot dataSnapshot) {
                    if (dataSnapshot.exists()) {
                        LicenseInfo licenseInfo = dataSnapshot.getValue(LicenseInfo.class);
                        if (licenseInfo != null) {
                            samiLogger.log("TAG22", "is-expired: " + licenseInfo.isId_expired());
                            samiLogger.log("TAG22", "is_license_used: " + licenseInfo.getIs_license_used());
                            samiLogger.log("TAG22", "License-Key: " + licenseInfo.getLicense_key());
                            samiLogger.log("TAG22", "valid-until: " + licenseInfo.getValid_until());

                            if(licenseInfo.isId_expired()){
                                Toast.makeText(MainActivity.this,"Your License is expired",Toast.LENGTH_LONG).show();
                                getSupportFragmentManager().beginTransaction()
                                        .replace(R.id.fragment_container, new ActivationFragment())
                                        .commit();
                            }else{
                                samiLogger.log("TAG22","GOOGLE TIME:"+getCurrentDate());
                                boolean expired = isDateExpired(licenseInfo.getValid_until(), getCurrentDate());
                                if (expired) {
                                    samiLogger.log("TAG22", "License is expired!");
                                    // Update the field "is-expired" to true
                                    myRef.child("is_expired").setValue(true)
                                            .addOnSuccessListener(aVoid -> {
                                                samiLogger.log("TAG22", "is-expired successfully updated to true");
                                            })
                                            .addOnFailureListener(e -> {
                                                samiLogger.log("TAG22", "Failed to update is-expired: " + e.getMessage());
                                            });
                                    Toast.makeText(MainActivity.this,"Your License is expired",Toast.LENGTH_LONG).show();
                                    getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, new ActivationFragment())
                                            .commitAllowingStateLoss();
                                } else {
                                    samiLogger.log("TAG22", "License is still valid.");
                                    getSupportFragmentManager().beginTransaction()
                                            .replace(R.id.fragment_container, new ActivatedFragment())
                                            .commitAllowingStateLoss();
                                }
                            }
                        }
                    }

                }

                @Override
                public void onCancelled(DatabaseError error) {
                    // Failed to read value
                    samiLogger.log("TAG22", "Failed to read value."+error.toException());
                   // Toast.makeText(MainActivity.this,"Failed to read license / or check your internet connection!",Toast.LENGTH_LONG).show();
                }
            });
        }
    }

    // ✅ Public method that can be called from ActivatedFragment
    public void runAnalyzer() {
        new FetchRemoteFlag().execute(REMOTE_URL);
    }

    // 🔹 AsyncTask to fetch remote flag
    private class FetchRemoteFlag extends AsyncTask<String, Void, String> {
        @Override
        protected String doInBackground(String... urls) {
            try {
                URL url = new URL(urls[0]);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setRequestMethod("GET");
                connection.setConnectTimeout(5000);
                connection.setReadTimeout(5000);

                BufferedReader reader = new BufferedReader(
                        new InputStreamReader(connection.getInputStream())
                );
                StringBuilder result = new StringBuilder();
                String line;

                while ((line = reader.readLine()) != null) {
                    result.append(line.trim());
                }

                reader.close();
                connection.disconnect();
                return result.toString();
            } catch (Exception e) {
                Log.e("RemoteFlag", "Error fetching flag: " + e.getMessage());
                return null;
            }
        }

        @Override
        protected void onPostExecute(String value) {
            if (value == null) {
                Toast.makeText(MainActivity.this, "Network error!", Toast.LENGTH_LONG).show();
                return;
            }

            samiLogger.log("RemoteFlag", "Value from server: " + value);

            if (value.equalsIgnoreCase("true")) {
                // ✅ allow app to run
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M && !Settings.canDrawOverlays(MainActivity.this)) {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    startActivityForResult(intent, REQUEST_OVERLAY);
                } else {
                    requestScreenCapture();
                }
            } else {
                Toast.makeText(MainActivity.this, "Access denied!", Toast.LENGTH_LONG).show();
            }
        }
    }

    private void requestScreenCapture() {
        startActivityForResult(
                ((android.media.projection.MediaProjectionManager) getSystemService(MEDIA_PROJECTION_SERVICE))
                        .createScreenCaptureIntent(),
                REQUEST_SCREENSHOT
        );
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == REQUEST_SCREENSHOT) {
            if (resultCode == RESULT_OK && data != null) {
                samiLogger.log("PERMISSION45", "✅ Screenshot permission granted");
                ScreenshotCaptureService.setPermissionResult(resultCode, data);
                startService(new Intent(this, OverlayService.class));
                finish();
            } else {
                samiLogger.log("PERMISSION45", "❌ Screenshot permission denied or cancelled");
            }
        } else if (requestCode == REQUEST_OVERLAY) {
            recreate();
        } else {
            super.onActivityResult(requestCode, resultCode, data);
        }
    }

    public String getCurrentDate() {
        // 1. Get the current date and time as a Date object
        Date currentDate = new Date();

        // 2. Define the desired date format
        // For example, "dd-MM-yyyy" for day-month-year
        // You can customize this format string based on your needs
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());

        // 3. Format the Date object into a String
        return dateFormat.format(currentDate);
        // Now, 'formattedDate' holds the current date as a String in the specified format.
        // You can then display it in a TextView, log it, or use it as needed.
        // Example:
        // TextView dateTextView = findViewById(R.id.date_text_view);
        // dateTextView.setText(formattedDate);
    }
    // Compare validUntil with currentDate
    public static boolean isDateExpired(String validUntil, String currentDate) {
        try {
            // validUntil format = "dd/MM/yyyy"
            SimpleDateFormat validFormat = new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault());
            Date validDate = validFormat.parse(validUntil);

            // currentDate format = "dd-MM-yyyy"
            SimpleDateFormat currentFormat = new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault());
            Date now = currentFormat.parse(currentDate);

            if (validDate == null || now == null) {
                return true; // if parsing failed, consider expired
            }

            // if current date is after validUntil, it's expired
            return now.after(validDate);

        } catch (ParseException e) {
            e.printStackTrace();
            return true; // if error, assume expired
        }
    }
}
