package com.example.punksta.volumecontrol.model;

import android.content.SharedPreferences;

import com.example.punksta.volumecontrol.data.Settings;

public class SettingsStorage {
    private static final String KEY_EXTENDED_VOLUME_SETTINGS = "EXTENDED_VOLUME_SETTINGS";
    private static final String KEY_DARK_THEME = "DARK_THEME";
    private final SharedPreferences preferences;

    public SettingsStorage(SharedPreferences preferences) {
        this.preferences = preferences;
    }

    public void save(Settings settings) {
        preferences.edit()
                .putBoolean(KEY_DARK_THEME, settings.isDarkThemeEnabled)
                .putBoolean(KEY_EXTENDED_VOLUME_SETTINGS, settings.isExtendedVolumeSettingsEnabled)
                .apply();
    }

    private static Settings defaultSettings = new Settings();

    public Settings settings() {
        return new Settings(
                preferences.getBoolean(KEY_DARK_THEME, defaultSettings.isDarkThemeEnabled),
                preferences.getBoolean(KEY_EXTENDED_VOLUME_SETTINGS, defaultSettings.isExtendedVolumeSettingsEnabled)
        );
    }
}
