package com.tvbox.acceptance.probe;
import android.app.*;
import android.content.Intent;
import android.media.*;
import android.os.IBinder;
import android.util.Log;
public class FocusService extends Service {
    AudioManager audio;
    AudioFocusRequest request;
    @Override public void onCreate() {
        super.onCreate();
        getSystemService(NotificationManager.class).createNotificationChannel(
            new NotificationChannel("probe", "Acceptance", NotificationManager.IMPORTANCE_LOW));
        startForeground(1, new Notification.Builder(this, "probe")
            .setContentTitle("Audio focus acceptance probe")
            .setSmallIcon(android.R.drawable.ic_media_play).build());
        audio = getSystemService(AudioManager.class);
        request = new AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
            .setOnAudioFocusChangeListener(value -> Log.i("TVBoxFocusProbe", "focus=" + value)).build();
        Log.i("TVBoxFocusProbe", "granted=" + audio.requestAudioFocus(request));
    }
    @Override public int onStartCommand(Intent intent, int flags, int startId) { return START_NOT_STICKY; }
    @Override public IBinder onBind(Intent intent) { return null; }
    @Override public void onDestroy() {
        audio.abandonAudioFocusRequest(request);
        stopForeground(true);
        super.onDestroy();
    }
}
