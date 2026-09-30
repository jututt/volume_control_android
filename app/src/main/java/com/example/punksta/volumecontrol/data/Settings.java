package com.example.punksta.volumecontrol.data;

public class Settings {
    public boolean isDarkThemeEnabled = true;
    public boolean isExtendedVolumeSettingsEnabled = false;

    public Settings(boolean isDarkThemeEnabled,
                    boolean isExtendedVolumeSettingsEnabled
    ) {
        this.isDarkThemeEnabled = isDarkThemeEnabled;
        this.isExtendedVolumeSettingsEnabled = isExtendedVolumeSettingsEnabled;
    }

    public Settings() {}
}
