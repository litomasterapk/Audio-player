package com.litomasterapk.audioplayer;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;
import com.getcapacitor.JSObject;
import com.getcapacitor.PermissionState;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.getcapacitor.annotation.Permission;
import com.getcapacitor.annotation.PermissionCallback;

@CapacitorPlugin(
    name = "MediaNotif",
    permissions = { @Permission(strings = { Manifest.permission.POST_NOTIFICATIONS }, alias = "notif") }
)
public class MediaNotifPlugin extends Plugin {
    private static final String CH = "audio_ctl";
    private static final String P = "com.litomasterapk.audioplayer.ACT_";
    private static final int ID = 7;
    private MediaSessionCompat session;

    private final BroadcastReceiver rx = new BroadcastReceiver() {
        @Override
        public void onReceive(Context c, Intent i) {
            String a = i.getAction();
            if (a != null && a.startsWith(P)) emit(a.substring(P.length()));
        }
    };

    @Override
    public void load() {
        IntentFilter f = new IntentFilter();
        for (String s : new String[] { "prev", "play", "pause", "next" }) f.addAction(P + s);
        ContextCompat.registerReceiver(getContext(), rx, f, ContextCompat.RECEIVER_NOT_EXPORTED);
    }

    private void emit(String a) {
        JSObject o = new JSObject();
        o.put("action", a);
        notifyListeners("action", o);
    }

    @PluginMethod
    public void show(PluginCall call) {
        if (Build.VERSION.SDK_INT >= 33 && getPermissionState("notif") != PermissionState.GRANTED) {
            requestPermissionForAlias("notif", call, "permDone");
            return;
        }
        doShow(call);
    }

    @PermissionCallback
    private void permDone(PluginCall call) {
        if (getPermissionState("notif") == PermissionState.GRANTED) doShow(call);
        else call.reject("denied");
    }

    private PendingIntent act(Context ctx, String a, int code) {
        Intent i = new Intent(P + a).setPackage(ctx.getPackageName());
        return PendingIntent.getBroadcast(ctx, code, i, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private void doShow(PluginCall call) {
        try {
            Context ctx = getContext();
            String title = call.getString("title", "");
            String app = call.getString("app", "");
            boolean playing = Boolean.TRUE.equals(call.getBoolean("playing", false));
            NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
            if (Build.VERSION.SDK_INT >= 26) {
                nm.createNotificationChannel(new NotificationChannel(CH, "Audio controls", NotificationManager.IMPORTANCE_LOW));
            }
            if (session == null) {
                session = new MediaSessionCompat(ctx, "AudioPlayer");
                session.setCallback(new MediaSessionCompat.Callback() {
                    @Override public void onPlay() { emit("play"); }
                    @Override public void onPause() { emit("pause"); }
                    @Override public void onSkipToNext() { emit("next"); }
                    @Override public void onSkipToPrevious() { emit("prev"); }
                });
            }
            session.setMetadata(new MediaMetadataCompat.Builder()
                .putString(MediaMetadataCompat.METADATA_KEY_TITLE, title)
                .putString(MediaMetadataCompat.METADATA_KEY_ARTIST, app)
                .build());
            session.setPlaybackState(new PlaybackStateCompat.Builder()
                .setActions(PlaybackStateCompat.ACTION_PLAY | PlaybackStateCompat.ACTION_PAUSE
                    | PlaybackStateCompat.ACTION_PLAY_PAUSE | PlaybackStateCompat.ACTION_SKIP_TO_NEXT
                    | PlaybackStateCompat.ACTION_SKIP_TO_PREVIOUS)
                .setState(playing ? PlaybackStateCompat.STATE_PLAYING : PlaybackStateCompat.STATE_PAUSED,
                    PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1f)
                .build());
            session.setActive(true);

            Intent launch = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
            PendingIntent open = PendingIntent.getActivity(ctx, 0, launch,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
            NotificationCompat.Builder b = new NotificationCompat.Builder(ctx, CH)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle(title)
                .setContentText(app)
                .setContentIntent(open)
                .setOngoing(playing)
                .setOnlyAlertOnce(true)
                .setShowWhen(false)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .addAction(android.R.drawable.ic_media_previous, "Prev", act(ctx, "prev", 1))
                .addAction(playing ? android.R.drawable.ic_media_pause : android.R.drawable.ic_media_play,
                    playing ? "Pause" : "Play", act(ctx, playing ? "pause" : "play", 2))
                .addAction(android.R.drawable.ic_media_next, "Next", act(ctx, "next", 3))
                .setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                    .setMediaSession(session.getSessionToken())
                    .setShowActionsInCompactView(0, 1, 2));
            nm.notify(ID, b.build());
            call.resolve();
        } catch (Exception e) {
            call.reject(String.valueOf(e.getMessage()));
        }
    }

    @PluginMethod
    public void hide(PluginCall call) {
        try {
            ((NotificationManager) getContext().getSystemService(Context.NOTIFICATION_SERVICE)).cancel(ID);
            if (session != null) session.setActive(false);
        } catch (Exception e) {
            // ignore
        }
        call.resolve();
    }
}
