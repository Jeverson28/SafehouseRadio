package com.example.james.safehouseradio;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

public class RemoteControlReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(Intent.ACTION_MEDIA_BUTTON)) {
            //TODO Handle EXTRA_KEY_EVENTS here
            KeyEvent event = intent.getParcelableExtra(Intent.EXTRA_KEY_EVENT);
            if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                context.startService(new Intent(context, PlayerService.class)
                        .setAction(PlayerService.ACTION_PAUSE_RADIO));
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_PLAY) {
                context.startService(new Intent(context, PlayerService.class)
                        .setAction(PlayerService.ACTION_PLAY_RADIO));
            }
            if (event.getKeyCode() == KeyEvent.KEYCODE_MEDIA_STOP) {
                context.startService(new Intent(context, PlayerService.class)
                        .setAction(PlayerService.ACTION_STOP_RADIO));
            }
        }
    }
}
