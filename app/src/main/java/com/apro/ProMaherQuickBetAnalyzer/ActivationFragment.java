package com.apro.ProMaherQuickBetAnalyzer;

import static android.content.Context.MODE_PRIVATE;

import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.*;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;

public class ActivationFragment extends Fragment {

    private EditText etLicense;
    private Button btnVerify;
    private ProgressBar progressBar;
    private TextView tvStatus;
    private TextView tvSupport;
    private SamiLogger samiLogger = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_activation, container, false);

        etLicense = view.findViewById(R.id.etLicense);
        btnVerify = view.findViewById(R.id.btnVerify);
        progressBar = view.findViewById(R.id.progressBar);
        tvStatus = view.findViewById(R.id.tvStatus);
        tvSupport = view.findViewById(R.id.tvSupport);

        btnVerify.setEnabled(false);
        progressBar.setVisibility(View.GONE);
        samiLogger = new SamiLogger();

        // Enable button only if user enters text
        etLicense.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                btnVerify.setEnabled(s.length() > 0);
            }
        });

        // Verify license
        btnVerify.setOnClickListener(v -> verifyLicense());

        // Contact support click
        tvSupport.setOnClickListener(v -> {
            if (!isAdded()) return;
            Intent intent = new Intent(Intent.ACTION_SENDTO, Uri.parse("mailto:abufares3055@gmail.com"));
            intent.putExtra(Intent.EXTRA_SUBJECT, "License Support");
            if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                startActivity(intent);
            } else {
                safeToast("No email app found");
            }
        });

        return view;
    }

    private void verifyLicense() {
        if (!isAdded()) return;

        String licenseKey = etLicense.getText().toString().trim();
        if (licenseKey.isEmpty()) return;

        btnVerify.setEnabled(false);
        progressBar.setVisibility(View.VISIBLE);
        samiLogger.log("Activate33", "btnVerify clicked textfield: " + licenseKey);

        DatabaseReference myRef = FirebaseDatabase.getInstance().getReference(licenseKey);
        myRef.addListenerForSingleValueEvent(new ValueEventListener() { // one-time listener
            @Override
            public void onDataChange(@NonNull DataSnapshot dataSnapshot) {
                if (!isAdded()) return;

                if (!dataSnapshot.exists()) {
                    safeToast("This License is not registered");
                    samiLogger.log("Activate33", "This License is not registered");
                    resetUi();
                    return;
                }

                LicenseInfo licenseInfo = dataSnapshot.getValue(LicenseInfo.class);
                if (licenseInfo == null) {
                    safeToast("Invalid license data");
                    resetUi();
                    return;
                }

                samiLogger.log("Activate33", "is-expired: " + licenseInfo.isId_expired());
                samiLogger.log("Activate33", "is_license_used: " + licenseInfo.getIs_license_used());
                samiLogger.log("Activate33", "valid-until: " + licenseInfo.getValid_until());

                if (licenseInfo.isId_expired()) {
                    safeToast("This License is expired");
                    resetUi();
                    return;
                }

                if (Boolean.TRUE.equals(licenseInfo.getIs_license_used())) {
                    safeToast("This License is used by another person");
                    resetUi();
                    return;
                }

                // Mark license as used
                myRef.child("is_license_used").setValue(true)
                        .addOnSuccessListener(aVoid -> {
                            samiLogger.log("Activate33", "is_license_used successfully updated to true");

                            SharedPreferences prefs = requireActivity()
                                    .getSharedPreferences("app_prefs", MODE_PRIVATE);
                            prefs.edit().putString("License", licenseKey).apply();

                            // Navigate to ActivatedFragment
                            requireActivity().getSupportFragmentManager().beginTransaction()
                                    .replace(R.id.fragment_container, new ActivatedFragment())
                                    .commit();
                        })
                        .addOnFailureListener(e -> {
                            samiLogger.log("Activate33", "Failed to update is_license_used: " + e.getMessage());
                            safeToast("Failed to update license. Try again.");
                            resetUi();
                        });
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                if (!isAdded()) return;
                samiLogger.log("Activate33", "Failed to read value. "+error.toException());
                safeToast("Failed to read license / check your internet connection!");
                resetUi();
            }
        });
    }

    private void resetUi() {
        if (!isAdded()) return;
        progressBar.setVisibility(View.GONE);
        btnVerify.setEnabled(true);
    }

    private void safeToast(String msg) {
        if (!isAdded()) return;
        Toast.makeText(requireContext().getApplicationContext(), msg, Toast.LENGTH_LONG).show();
    }
}
