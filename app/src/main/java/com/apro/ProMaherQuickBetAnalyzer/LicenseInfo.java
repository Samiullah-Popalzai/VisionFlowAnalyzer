package com.apro.ProMaherQuickBetAnalyzer;

public class LicenseInfo {
    private boolean id_expired;
    private boolean is_license_used;
    private String license_key;
    private String valid_until;
    private boolean pro;

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
    public boolean pro(){
        return pro;
    }
}
