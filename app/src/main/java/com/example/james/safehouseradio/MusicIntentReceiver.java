package com.example.james.safehouseradio;

import android.content.Context;
import android.content.Intent;

public class MusicIntentReceiver extends android.content.BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(
                android.media.AudioManager.ACTION_AUDIO_BECOMING_NOISY)) {
            context.startService(new Intent(context, PlayerService.class).setAction(PlayerService.ACTION_PAUSE_RADIO));
        }
    }
}
