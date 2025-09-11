package com.apro.ProMaherQuickBetAnalyzer;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ActivatedFragment extends Fragment {

    private Button btnRunAnalyzer;
    private Button btnChangeLicense;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(R.layout.fragment_activated, container, false);

        btnRunAnalyzer = view.findViewById(R.id.btnRunAnalyzer);
        btnChangeLicense = view.findViewById(R.id.btnChangeLicense);

        btnRunAnalyzer.setOnClickListener(v -> {
            // Placeholder: start your overlay service later
            Toast.makeText(getContext(), "Run Analyzer clicked", Toast.LENGTH_SHORT).show();
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).runAnalyzer();
            } else {
                Toast.makeText(getContext(), "Cannot run analyzer", Toast.LENGTH_SHORT).show();
            }
        });

        btnChangeLicense.setOnClickListener(v -> {
            // Clear activation flag to allow license re-entry
            if (getActivity() != null) {
                getActivity().getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                        .edit()
                        .putBoolean("isActivated", false)
                        .apply();

                // Switch back to ActivationFragment
                getActivity().getSupportFragmentManager().beginTransaction()
                        .replace(R.id.fragment_container, new ActivationFragment())
                        .commit();
            }
        });

        return view;
    }



}
