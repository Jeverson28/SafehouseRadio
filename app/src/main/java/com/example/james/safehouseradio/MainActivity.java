package com.example.james.safehouseradio;

import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.os.Bundle;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    //TODO Add share buttons
    //TODO Add settings to keep screen on?
    //TODO Keep Now Playing state on orientation state

    private ImageButton mPlayButton, mStopButton, mPauseButton;
    private TextView mNowPlayingTextView;
    private boolean isPlaying, isPaused;
    private BroadcastReceiver mReceiver;
    private final static String TAG = "MainActivity";
    public final static String SHOW_PROGRESS_DIALOGUE = "com.example.james.safehouseradio.SHOW";
    public final static String CANCEL_PROGRESS_DIALOGUE = "com.example.james.safehouseradio.CANCEL";
    private ProgressDialog mProgressDialog = null;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mNowPlayingTextView = (TextView) findViewById(R.id.now_playing);
        mNowPlayingTextView.setText(getResources().getString(R.string.now_playing_text, ""));
        mPlayButton = (ImageButton) findViewById(R.id.playButton);
        mStopButton = (ImageButton) findViewById(R.id.stopButton);
        mPauseButton = (ImageButton) findViewById(R.id.pauseButton);
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_GET_PLAYER_STATE));
        setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onResume() {
        super.onResume();

        IntentFilter intentFilter = new IntentFilter(PlayerService.SAFEHOUSE_INTENT_FILTER);
        mReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                Log.d(TAG, "Intent received");
                switch (intent.getStringExtra("action")) {
                    case PlayerService.ACTION_GET_SONG_NAME:
                        Log.d(TAG, "Received GET_SONG intent");
                        Log.d(TAG, "Song name is " + intent.getStringExtra("songName"));
                        mNowPlayingTextView.setText(getResources().getString(R.string.now_playing_text,
                                intent.getExtras().getString("songName")));
                        break;
                    case PlayerService.ACTION_GET_PLAYER_STATE:
                        Log.d(TAG, "Received PLAYER_STATE intent");
                        isPlaying = intent.getBooleanExtra("isPlaying", false);
                        isPaused = intent.getBooleanExtra("isPaused", false);
                        updateButtons();
                        break;
                    case PlayerService.ACTION_PLAY_RADIO:
                        Log.d(TAG, "Received PLAY_RADIO intent");
                        isPlaying = true;
                        updateButtons();
                        break;
                    case PlayerService.ACTION_PAUSE_RADIO:
                        Log.d(TAG, "Received PAUSE_RADIO intent");
                        isPlaying = false;
                        isPaused = true;
                        updateButtons();
                        break;
                    case PlayerService.ACTION_STOP_RADIO:
                        isPlaying = false;
                        isPaused = false;
                        updateButtons();
                        break;
                    case SHOW_PROGRESS_DIALOGUE:
                        if (mProgressDialog == null) {
                            Log.d(TAG, "Showing progress dialogue");
                            mProgressDialog = new ProgressDialog(MainActivity.this);
                            mProgressDialog.setIndeterminate(true);
                            mProgressDialog.setCancelable(false);
                            mProgressDialog.setTitle("Loading");
                            mProgressDialog.setMessage("Loading...");
                            mProgressDialog.show();
                        }
                        break;
                    case CANCEL_PROGRESS_DIALOGUE:
                        if (mProgressDialog != null) {
                            Log.d(TAG, "Cancelling progress dialogue");
                            mProgressDialog.cancel();
                            mProgressDialog = null;
                        }
                        break;
                }
            }
        };
        registerReceiver(mReceiver, intentFilter);
        Log.d(TAG, "Receiver registered");
    }

    @Override
    protected void onPause() {
        super.onPause();
        unregisterReceiver(mReceiver);
        Log.d(TAG, "Receiver unregistered");
    }

    /**
     * Sends an Intent to PlayerService to play music. It sends PlayerService.ACTION_PLAY_RADIO
     */
    public void radioPlay(View view) {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PLAY_RADIO));
    }

    /**
     * Sends an Intent to PlayerService to pause music. It sends PlayerService.ACTION_PAUSE_RADIO
     */
    public void radioPause(View view) {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PAUSE_RADIO));
    }

    /**
     * Sends an Intent to PlayerService to stop music. It sends PlayerService.ACTION_STOP_RADIO
     * @param view
     */
    public void radioStop(View view) {
        startService(new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_STOP_RADIO));
    }

    /**
     * Updates the buttons on the screen. The play button will change to a pause icon when music is
     * streaming, and vice versa. The stop button will not be clickable and will have low opacity
     * when music is stopped, but will be solid and clickable when music is playing or stopped.
     */
    private void updateButtons() {
        if (isPlaying) {
            enableButton(mStopButton);
            enableButton(mPauseButton);
            disableButton(mPlayButton);
        } else {
            if (isPaused) {
                enableButton(mStopButton);
                enableButton(mPlayButton);
                disableButton(mPauseButton);
            } else {
                disableButton(mStopButton);
                disableButton(mPauseButton);
                enableButton(mPlayButton);
            }
        }
        Log.d(TAG, "Updated buttons");
    }

    private void enableButton(ImageButton button) {
        button.setEnabled(true);
        button.setClickable(true);
        button.setAlpha((float) 1.0);
    }

    private void disableButton(ImageButton button) {
        button.setEnabled(false);
        button.setClickable(false);
        button.setAlpha((float) 0.2);
    }
}
