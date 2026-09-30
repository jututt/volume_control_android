package com.example.punksta.volumecontrol;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

import com.punksta.apps.libs.VolumeControl;

public class SoundService extends Service {

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        return START_NOT_STICKY;
    }

    public static Intent getStopIntent(android.content.Context context) {
        return new Intent(context, SoundService.class);
    }

    public static Intent getIntentForForeground(android.content.Context context, com.example.punksta.volumecontrol.data.Settings settings) {
        return new Intent(context, SoundService.class);
    }
}
