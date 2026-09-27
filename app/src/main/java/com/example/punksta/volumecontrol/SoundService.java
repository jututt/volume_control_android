package com.example.punksta.volumecontrol;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.punksta.volumecontrol.data.Settings;
import com.example.punksta.volumecontrol.data.SoundProfile;
import com.example.punksta.volumecontrol.model.SoundProfileStorage;
import com.example.punksta.volumecontrol.util.DNDModeChecker;
import com.example.punksta.volumecontrol.util.NotificationWidgetUpdateTracker;
import com.example.punksta.volumecontrol.util.ProfileApplier;
import com.punksta.apps.libs.VolumeControl;

import org.json.JSONException;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class SoundService extends Service {
    private static final String TAG = "SoundService";
    private static final int NOTIFICATION_ID = 1;
    private static final String CHANNEL_ID = "static";
    private static final int PROFILE_ID_PREFIX = 10000;
    private static final int VOLUME_ID_PREFIX = 100;
    private static final String APPLY_PROFILE_ACTION = "APPLY_PROFILE";
    private static final String STOP_ACTION = "STOP_ACTION";
    private static final String CHANGE_VOLUME_ACTION = "CHANGE_VOLUME_ACTION";
    private static final String FOREGROUND_ACTION = "FOREGROUND_ACTION";
    private static final String PROFILE_ID = "PROFILE_ID";
    private static final String EXTRA_VOLUME = "EXTRA_VOLUME";
    private static final String EXTRA_VOLUME_DELTA = "EXTRA_VOLUME_DELTA";
    private static final String EXTRA_TYPE = "EXTRA_TYPE";
    private static final String EXTRA_SHOW_PROFILES = "EXTRA_SHOW_PROFILES";
    private static final String EXTRA_VOLUME_TYPES_IDS = "EXTRA_VOLUME_TYPES_IDS";

    private List<Integer> profilesToShow;
    private boolean showProfiles;
    private boolean isForeground;
    private VolumeControl control;
    private SoundProfileStorage soundProfileStorage;
    private final NotificationWidgetUpdateTracker tracker = new NotificationWidgetUpdateTracker();
    private final SoundProfileStorage.Listener listener = this::updateNotification;
    private NotificationManagerCompat notificationManagerCompat;

    private final VolumeControl.VolumeListener volumeListener = ($$$, $$, $) -> updateNotification();

    private static int pendingIntentFlags() {
        return PendingIntent.FLAG_UPDATE_CURRENT
                | (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M ? PendingIntent.FLAG_IMMUTABLE : 0);
    }

    private static RemoteViews buildVolumeSlider(Context context, VolumeControl control, int typeId, String typeName) {
        RemoteViews views = new RemoteViews(context.getPackageName(), R.layout.notification_volume_slider);
        views.removeAllViews(R.id.volume_slider);
        int maxLevel = control.getMaxLevel(typeId);
        int minLevel = control.getMinLevel(typeId);
        int currentLevel = control.getLevel(typeId);
        int maxSliderLevel = Math.min(maxLevel, 8);
        if (maxSliderLevel <= 0) maxSliderLevel = 1;
        float delta = maxLevel / (float) maxSliderLevel;

        for (int i = control.getMinLevel(typeId); i <= maxSliderLevel; i++) {
            int volumeLevel = (maxLevel * i) / maxSliderLevel;
            RemoteViews sliderItemView = new RemoteViews(context.getPackageName(),
                    volumeLevel <= currentLevel ? R.layout.notification_slider_active : R.layout.notification_slider_inactive);
            if (i == maxSliderLevel) sliderItemView.setViewVisibility(R.id.deliver_item, View.GONE);
            sliderItemView.setOnClickPendingIntent(R.id.notification_slider_item,
                    PendingIntent.getService(context, VOLUME_ID_PREFIX + (volumeLevel + 1) * 100 + typeId,
                            setVolumeIntent(context, typeId, volumeLevel), pendingIntentFlags()));
            views.addView(R.id.volume_slider, sliderItemView);
        }

        views.setTextViewText(R.id.volume_title,
                capitalize(typeName) + " " + (currentLevel - minLevel) + "/" + (maxLevel - minLevel));
        views.setOnClickPendingIntent(R.id.volume_up,
                PendingIntent.getService(context, VOLUME_ID_PREFIX + 10 + typeId,
                        setVolumeByDeltaIntent(context, typeId, delta), pendingIntentFlags()));
        views.setOnClickPendingIntent(R.id.volume_down,
                PendingIntent.getService(context, VOLUME_ID_PREFIX + 20 + typeId,
                        setVolumeByDeltaIntent(context, typeId, -delta), pendingIntentFlags()));
        return views;
    }

    private static Notification buildForegroundNotification(Context context, SoundProfile[] profiles,
                                                              VolumeControl control, List<Integer> volumeTypesToShow) {
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID);
        RemoteViews remoteViews = new RemoteViews(context.getPackageName(), R.layout.notification_view);

        if (profiles != null) {
            remoteViews.removeAllViews(R.id.notifications_user_profiles);
            for (SoundProfile profile : profiles) {
                RemoteViews profileViews = new RemoteViews(context.getPackageName(), R.layout.notification_profile_name);
                profileViews.setTextViewText(R.id.notification_profile_title, profile.name);
                profileViews.setOnClickPendingIntent(R.id.notification_profile_title,
                        PendingIntent.getService(context, PROFILE_ID_PREFIX + profile.id,
                                getIntentForProfile(context, profile), pendingIntentFlags()));
                remoteViews.addView(R.id.notifications_user_profiles, profileViews);
            }
        }
        if (volumeTypesToShow != null) {
            remoteViews.removeAllViews(R.id.volume_sliders);
            for (AudioType type : AudioType.getAudioTypes(true)) {
                if (volumeTypesToShow.contains(type.audioStreamName)) {
                    remoteViews.addView(R.id.volume_sliders,
                            buildVolumeSlider(context, control, type.audioStreamName, context.getString(type.nameId)));
                }
            }
        }

        builder.setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(context, 0,
                        new Intent(context, MainActivity.class), pendingIntentFlags()))
                .setCustomContentView(remoteViews)
                .setCustomBigContentView(remoteViews)
                .setCustomHeadsUpContentView(remoteViews);
        return builder.build();
    }

    private static String capitalize(String str) {
        return str == null || str.isEmpty() ? "" : str.substring(0, 1).toUpperCase() + str.substring(1);
    }

    public static Intent getIntentForProfile(Context context, SoundProfile profile) {
        return new Intent(context, SoundService.class).setAction(APPLY_PROFILE_ACTION)
                .putExtra(PROFILE_ID, profile.id);
    }

    public static Intent getStopIntent(Context context) {
        return new Intent(context, SoundService.class).setAction(STOP_ACTION);
    }

    public static Intent setVolumeByDeltaIntent(Context context, int typeId, float delta) {
        return new Intent(context, SoundService.class).setAction(CHANGE_VOLUME_ACTION)
                .putExtra(EXTRA_VOLUME_DELTA, delta).putExtra(EXTRA_TYPE, typeId);
    }

    public static Intent setVolumeIntent(Context context, int typeId, int value) {
        return new Intent(context, SoundService.class).setAction(CHANGE_VOLUME_ACTION)
                .putExtra(EXTRA_VOLUME, value).putExtra(EXTRA_TYPE, typeId);
    }

    public static Intent getIntentForForeground(Context context, Settings settings) {
        return new Intent(context, SoundService.class).setAction(FOREGROUND_ACTION)
                .putExtra(EXTRA_SHOW_PROFILES, settings.showProfilesInNotification)
                .putExtra(EXTRA_VOLUME_TYPES_IDS, new ArrayList<>(Arrays.asList(settings.volumeTypesToShow)));
    }

    @Override public IBinder onBind(Intent intent) { return null; }

    private void updateNotification() {
        if (isForeground) {
            try {
                SoundProfile[] profiles = showProfiles ? soundProfileStorage.loadAll() : new SoundProfile[0];
                if (tracker.shouldShow(control, profilesToShow, profiles)) {
                    notificationManagerCompat.notify(NOTIFICATION_ID,
                            buildForegroundNotification(this, profiles, control, profilesToShow));
                    tracker.onNotificationShow(control, profilesToShow, profiles);
                }
            } catch (RuntimeException | JSONException e) {
                Log.e(TAG, "Failed to update notification", e);
            }
        }
    }

    /** Must be called immediately after startForegroundService(). */
    private void ensureForegroundNotification() {
        if (isForeground) return;
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setContentIntent(PendingIntent.getActivity(this, 0,
                        new Intent(this, MainActivity.class), pendingIntentFlags()))
                .setCustomContentView(new RemoteViews(getPackageName(), R.layout.notification_view))
                .setCustomBigContentView(new RemoteViews(getPackageName(), R.layout.notification_view))
                .setCustomHeadsUpContentView(new RemoteViews(getPackageName(), R.layout.notification_view))
                .build();
        startForeground(NOTIFICATION_ID, notification);
        isForeground = true;
    }

    private void updateFullNotification() {
        try {
            SoundProfile[] profiles = showProfiles ? soundProfileStorage.loadAll() : new SoundProfile[0];
            Notification notification = buildForegroundNotification(this, profiles, control, profilesToShow);
            notificationManagerCompat.notify(NOTIFICATION_ID, notification);
            tracker.onNotificationShow(control, profilesToShow, profiles);
        } catch (RuntimeException | JSONException e) {
            Log.e(TAG, "Failed to display notification", e);
        }
    }

    private void registerListeners() {
        if (profilesToShow != null) {
            for (Integer id : profilesToShow) control.registerVolumeListener(id, volumeListener, false);
        }
        soundProfileStorage.addListener(listener);
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        String action = intent == null ? null : intent.getAction();

        // This must precede DND checks and all potentially failing work.
        if (!STOP_ACTION.equals(action)) ensureForegroundNotification();

        if (STOP_ACTION.equals(action)) {
            stopForeground(true);
            notificationManagerCompat.cancel(NOTIFICATION_ID);
            isForeground = false;
            stopSelfResult(startId);
            return START_NOT_STICKY;
        }

        if (!DNDModeChecker.isDNDPermissionGranted(this)) {
            Toast.makeText(this, R.string.dnd_permission_title, Toast.LENGTH_LONG).show();
            return START_NOT_STICKY;
        }

        if (FOREGROUND_ACTION.equals(action)) {
            showProfiles = intent.getBooleanExtra(EXTRA_SHOW_PROFILES, true);
            Object value = intent.getSerializableExtra(EXTRA_VOLUME_TYPES_IDS);
            profilesToShow = value instanceof List ? (List<Integer>) value : null;
            registerListeners();
            updateFullNotification();
            return START_NOT_STICKY;
        }

        if (APPLY_PROFILE_ACTION.equals(action)) {
            try {
                SoundProfile profile = soundProfileStorage.loadById(intent.getIntExtra(PROFILE_ID, -1));
                if (profile != null) ProfileApplier.applyProfile(control, profile);
            } catch (JSONException e) {
                Log.e(TAG, "Failed to apply profile", e);
            }
            registerListeners();
            updateFullNotification();
            return START_STICKY;
        }

        if (CHANGE_VOLUME_ACTION.equals(action)) {
            int type = intent.getIntExtra(EXTRA_TYPE, 0);
            if (intent.hasExtra(EXTRA_VOLUME)) {
                control.setVolumeLevel(type, intent.getIntExtra(EXTRA_VOLUME, 0));
            } else {
                float delta = intent.getFloatExtra(EXTRA_VOLUME_DELTA, 0);
                int current = control.getLevel(type);
                int next = (int) (delta > 0 ? Math.ceil(current + delta) : Math.floor(current + delta));
                control.setVolumeLevel(type, next);
            }
            registerListeners();
            updateFullNotification();
            return START_STICKY;
        }
        return START_NOT_STICKY;
    }

    @Override public void onDestroy() {
        soundProfileStorage.removeListener(listener);
        for (AudioType type : AudioType.getAudioExtendedTypes()) {
            control.unRegisterVolumeListener(type.audioStreamName, volumeListener);
        }
        super.onDestroy();
    }

    @Override public void onCreate() {
        super.onCreate();
        notificationManagerCompat = NotificationManagerCompat.from(this);
        soundProfileStorage = SoundApplication.getSoundProfileStorage(this);
        control = SoundApplication.getVolumeControl(this);
        createStaticNotificationChannel();
    }

    private void createStaticNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID,
                    "Static notification widget", NotificationManager.IMPORTANCE_LOW);
            channel.setSound(null, null);
            channel.enableVibration(false);
            NotificationManager manager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
            if (manager != null) manager.createNotificationChannel(channel);
        }
    }
}
