package com.apro.MaherQuickBetAnalyzer;

public class LicenseInfo {
    private boolean id_expired;
    private boolean is_license_used;
    private String license_key;
    private String valid_until;

    // Empty constructor required for Firebase
    public LicenseInfo() {}

    // Getters
    public boolean isId_expired() {
        return id_expired;
    }

    public Boolean getIs_license_used() {
        return is_license_used;
    }

    public String getLicense_key() {
        return license_key;
    }

    public String getValid_until(){
        return valid_until;
    }
}
