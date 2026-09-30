package com.example.punksta.volumecontrol;

import android.animation.LayoutTransition;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.example.punksta.volumecontrol.data.SoundProfile;
import com.example.punksta.volumecontrol.model.SoundProfileStorage;
import com.example.punksta.volumecontrol.util.DNDModeChecker;
import com.example.punksta.volumecontrol.util.DynamicShortcutManager;
import com.example.punksta.volumecontrol.util.IntentHelper;
import com.example.punksta.volumecontrol.view.RingerModeSwitch;
import com.example.punksta.volumecontrol.view.VolumeProfileView;
import com.example.punksta.volumecontrol.view.VolumeSliderView;
import com.punksta.apps.libs.VolumeControl;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import static com.example.punksta.volumecontrol.EditProfileActivity.REQUEST_CODE_EDIT_PROFILE;
import static com.example.punksta.volumecontrol.EditProfileActivity.REQUEST_CODE_NEW_PROFILE;
import static com.example.punksta.volumecontrol.util.DNDModeChecker.isDNDPermissionGranted;
import static com.example.punksta.volumecontrol.util.DNDModeChecker.showDNDPermissionAlert;
import static com.example.punksta.volumecontrol.util.ProfileApplier.applyProfile;
import static com.example.punksta.volumecontrol.util.WritePermissionChecker.checkWriteSettingsPermission;

public class MainActivity extends BaseActivity {

    private static final int POST_NOTIFICATIONS_REQUEST_CODE = 1001;
    public static final String PROFILE_ID = "PROFILE_ID";
    private final List<TypeListener> volumeListeners = new ArrayList<>();
    private SoundProfileStorage profileStorage;
    private boolean goingGoFinish = false;
    private final VolumeControl.RingerModeChangeListener ringerModeSwitcher = (int mode) -> {
        RingerModeSwitch ringerModeSwitch = findViewById(R.id.ringerMode);
        ringerModeSwitch.setRingMode(mode);
    };

    public static Intent createOpenProfileIntent(Context context, SoundProfile profile) {
        Intent intent1 = new Intent(context.getApplicationContext(), MainActivity.class);
        intent1.setAction(Intent.ACTION_VIEW);
        intent1.putExtra(PROFILE_ID, profile.id);
        return intent1;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        requestNotificationPermissionIfNeeded();
        profileStorage = SoundApplication.getSoundProfileStorage(this);
        buildUi();
        if (savedInstanceState == null) {
            if (handleIntent(getIntent())) {
                goingGoFinish = true;
                finish();
            }
        }
    }

    private void requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{android.Manifest.permission.POST_NOTIFICATIONS},
                    POST_NOTIFICATIONS_REQUEST_CODE);
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        if (!handleIntent(intent)) {
            super.onNewIntent(intent);
        }
    }

    private boolean handleIntent(Intent intent) {
        if (intent.hasExtra(PROFILE_ID)) {
            int profileId = intent.getIntExtra(PROFILE_ID, 0);
            try {
                SoundProfile profile = profileStorage.loadById(profileId);
                if (profile != null) {
                    applyProfile(control, profile);
                }
            } catch (JSONException e) {
                e.printStackTrace();
            }
            setIntent(null);
            return true;
        } else {
            return false;
        }
    }

    @Override
    protected void setExtendedVolumesEnabled(boolean isEnabled) {
        super.setExtendedVolumesEnabled(isEnabled);
        renderProfileItems();
    }

    private void renderProfileItems() {
        View title = findViewById(R.id.audio_types_holder_title);
        ViewGroup titlesGroup = findViewById(R.id.linearLayout);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            titlesGroup.getLayoutTransition().enableTransitionType(LayoutTransition.CHANGING);
        }

        int indexOfTitle = titlesGroup.indexOfChild(title);

        List<AudioType> audioTypes = AudioType.getAudioTypes(true);

        if (!Boolean.TRUE.equals(title.getTag())) {
            for (int i = 0; i < audioTypes.size(); i++) {
                AudioType type = audioTypes.get(i);

                final VolumeSliderView volumeSliderView = new VolumeSliderView(this);

                volumeSliderView.setTag(type.audioStreamName);
                titlesGroup.addView(volumeSliderView, indexOfTitle + i + 1, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

                volumeSliderView.setVolumeName(getString(type.nameId));
                volumeSliderView.setMaxVolume(control.getMaxLevel(type.audioStreamName));
                volumeSliderView.setMinVolume(control.getMinLevel(type.audioStreamName));
                volumeSliderView.setCurrentVolume(control.getLevel(type.audioStreamName));

                final TypeListener volumeListener = new TypeListener(type.audioStreamName) {
                    @Override
                    public void onChangeIndex(int audioType, int currentLevel, int max) {
                        if (currentLevel < control.getMinLevel(type)) {
                            volumeSliderView.setCurrentVolume(control.getMinLevel(type));
                        } else {
                            volumeSliderView.setCurrentVolume(currentLevel);
                        }
                    }
                };

                volumeListeners.add(volumeListener);

                volumeSliderView.setListener((volume, fromUser) -> {
                    if (fromUser) {
                        requireChangeVolume(type, volume);
                    }
                });
            }
            title.setTag(Boolean.TRUE);
        }

        for (AudioType audioExtendedType : AudioType.getAudioExtendedTypes()) {
            titlesGroup.findViewWithTag(audioExtendedType.audioStreamName).setVisibility(isExtendedVolumesEnabled() ? View.VISIBLE : View.GONE);
        }
    }

    private void buildUi() {
        Switch s = findViewById(R.id.dark_theme_switcher);

        s.setChecked(isDarkTheme());
        s.setOnCheckedChangeListener((buttonView, isChecked) -> setThemeAndRecreate(isChecked));

        renderProfileItems();

        Switch s2 = findViewById(R.id.extended_volumes);
        s2.setChecked(isExtendedVolumesEnabled());
        s2.setOnCheckedChangeListener((buttonView, isChecked) -> setExtendedVolumesEnabled(isChecked));

        findViewById(R.id.go_to_settings).setOnClickListener(v -> IntentHelper.goToVolumeSettings(this));
        findViewById(R.id.new_profile).setOnClickListener(v -> startActivityForResult(new Intent(MainActivity.this, EditProfileActivity.class), REQUEST_CODE_NEW_PROFILE));

        RingerModeSwitch ringerModeSwitch = findViewById(R.id.ringerMode);
        ringerModeSwitch.setRingMode(control.getRingerMode());

        control.addOnRingerModeListener(ringerModeSwitcher);
        ringerModeSwitch.setVisibility(View.GONE);

        Switch vibrateOnCalls = findViewById(R.id.vibrate_on_calls);
        vibrateOnCalls.setOnCheckedChangeListener((compoundButton, isEnabled) -> {
            if (checkWriteSettingsPermission(MainActivity.this, 0)) {
                control.setVibrateOnCalls(isEnabled);
            } else {
                try {
                    vibrateOnCalls.setChecked(control.isVibrateOnCallsEnabled());
                } catch (Settings.SettingNotFoundException e) {
                    e.printStackTrace();
                }
            }
        });

        try {
            renderProfiles();
        } catch (JSONException e) {
            throw new RuntimeException(e);
        }
    }

    private void updateVibrateOnCalls() {
        Switch vibrateOnCalls = findViewById(R.id.vibrate_on_calls);
        try {
            vibrateOnCalls.setChecked(control.isVibrateOnCallsEnabled());
        } catch (Throwable throwable) {
            throwable.printStackTrace();
            vibrateOnCalls.setVisibility(View.GONE);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (goingGoFinish) {
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DynamicShortcutManager.setShortcuts(this, profileStorage.loadAll());
            }
        } catch (JSONException e) {
            e.printStackTrace();
        }
        updateVibrateOnCalls();
    }

    private void renderProfile(final SoundProfile profile) {
        final LinearLayout profiles = findViewById(R.id.profile_list);
        final VolumeProfileView view = new VolumeProfileView(this);
        String tag = "profile_" + profile.id;
        profiles.removeView(profiles.findViewWithTag(tag));
        view.setTag(tag);

        view.setProfileTitle(profile.name);
        view.setOnActivateClickListener(() -> applyProfile(control, profile));
        view.setOnEditClickListener(() -> {
            profileStorage.removeProfile(profile.id);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                DynamicShortcutManager.removeShortcut(this, profile);
            }
            profiles.removeView(view);
        });
        view.setOnClickListener(view1 -> startActivityForResult(EditProfileActivity.getIntentForEdit(this, profile), REQUEST_CODE_EDIT_PROFILE));
        profiles.addView(view,
                new LinearLayout.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.WRAP_CONTENT
                )
        );
    }

    private void renderProfiles() throws JSONException {
        LinearLayout profiles = findViewById(R.id.profile_list);
        profiles.removeAllViews();

        for (final SoundProfile profile : profileStorage.loadAll()) {
            renderProfile(profile);
        }
    }

    private void requireChangeVolume(AudioType audioType, int volume) {
        try {
            control.setVolumeLevel(audioType.audioStreamName, volume);
        } catch (Throwable throwable) {
            Toast.makeText(this, throwable.getMessage(), Toast.LENGTH_SHORT).show();
            throwable.printStackTrace();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        if (!DNDModeChecker.isDNDPermissionGranted(this)) {
            showDNDPermissionAlert(this);
        }
        for (TypeListener listener : volumeListeners)
            control.registerVolumeListener(listener.type, listener, true);
    }

    @Override
    protected void recreateActivity() {
        Intent intent = new Intent(this, this.getClass());
        finish();
        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
        startActivity(intent);
    }

    @Override
    protected void onStop() {
        super.onStop();
        for (TypeListener volumeListener : volumeListeners)
            control.unRegisterVolumeListener(volumeListener.type, volumeListener);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        volumeListeners.clear();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case EditProfileActivity.REQUEST_CODE_EDIT_PROFILE:
            case EditProfileActivity.REQUEST_CODE_NEW_PROFILE: {
                if (resultCode == Activity.RESULT_OK) {
                    HashMap<Integer, Integer> volumes = (HashMap<Integer, Integer>) data.getSerializableExtra("volumes");
                    String name = data.getStringExtra("name");
                    SoundProfile profile;
                    if (requestCode == EditProfileActivity.REQUEST_CODE_NEW_PROFILE) {
                        profile = profileStorage.addProfile(name, volumes);
                    } else {
                        int id = data.getIntExtra("id", -1);
                        profile = new SoundProfile();
                        profile.id = id;
                        profile.name = name;
                        profile.settings = volumes;
                        profileStorage.saveProfile(profile);
                    }
                    renderProfile(profile);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        if (DynamicShortcutManager.isPinnedShortcutSupported(this)) {
                            DynamicShortcutManager.installPinnedShortcut(this, profile);
                        }
                    }
                }
                break;
            }
            default:
                super.onActivityResult(requestCode, resultCode, data);
        }
    }

    static abstract class TypeListener implements VolumeControl.VolumeListener {
        final int type;

        TypeListener(int type) {
            this.type = type;
        }
    }
}
