package com.example.james.safehouseradio;

import android.app.Notification;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.AsyncTask;
import android.os.IBinder;
import android.os.PowerManager;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaMetadataCompat;
import android.support.v4.media.session.MediaButtonReceiver;
import android.support.v4.media.session.MediaSessionCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.support.v7.app.NotificationCompat;
import android.util.Log;
import android.widget.Toast;

import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.Calendar;
import java.util.Timer;
import java.util.TimerTask;

public class PlayerService extends Service implements MediaPlayer.OnPreparedListener, MediaPlayer.OnErrorListener, AudioManager.OnAudioFocusChangeListener {

    //TODO MediaSessionCompat
    //TODO onCreate Find Stream and song name. Update now playing!
    //TODO Update intents to use media buttons

    //Fields
    private MediaPlayer player = null;
    private WifiManager.WifiLock wifiLock;
    private Timer songTimer;
    private Timer streamTimer;
    private GetSongNameTask songNameTask;
    private GetStreamTask streamTask;
    private String currentSong = "";
    private String currentStream = "";

    //Constants
    public final static int MUSIC_PLAYING_NOTIFICATION_ID = 1;
    public final static String ACTION_STOP_RADIO = "com.example.james.safehouseradio.STOP";
    public final static String ACTION_PLAY_RADIO = "com.example.james.safehouseradio.PLAY_UPDATE";
    public final static String ACTION_PAUSE_RADIO = "com.example.james.safehouseradio.PAUSE_UPDATE";
    public final static String ACTION_GET_SONG_NAME = "com.example.james.safehouseradio.SONG_NAME";
    public final static String ACTION_GET_PLAYER_STATE = "com.example.james.safehouseradio.PLAYER_STATE";
    public final static String SAFEHOUSE_INTENT_FILTER = "com.example.james.safehouseradio.SAFEHOUSE_INTENT_FILTER";
    private final static String TAG = "PlayerService";
    private final String UPDATE_PLAY = "update_play";
    private final String UPDATE_PAUSE = "update_pause";
    private final String CREATE_NOTIFICATION = "create_notification";
    private final String UPDATE_NAME = "update_name";

    //System
    private MediaSessionCompat mySession;
    private AudioManager mAudioManager;
    private MediaSessionCompat.Token sessionToken;
    //private BroadcastReceiver connectivityReceiver;

    @Override
    public void onCreate() {
        super.onCreate();

        /*
        IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
        intentFilter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        connectivityReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                ConnectivityManager cm =
                        (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);
                NetworkInfo activeNetwork = cm.getActiveNetworkInfo();
                boolean isConnected = activeNetwork != null &&
                        activeNetwork.isConnectedOrConnecting();
                if (!isConnected) {
                    sendShowProgressBroadcast();
                } else {
                    sendCancelProgressBroadcast();
                }
            }
        };
        registerReceiver(connectivityReceiver, intentFilter);
        */
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startid) {
        super.onStartCommand(intent, flags, startid);
        ComponentName mediaButtonReceiver =
                new ComponentName(getApplicationContext(), RemoteControlReceiver.class);
        mySession = new MediaSessionCompat(getApplicationContext(), TAG, mediaButtonReceiver, null);
        mySession.setFlags(
                MediaSessionCompat.FLAG_HANDLES_MEDIA_BUTTONS |
                MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        //sessionToken = mySession.getSessionToken();
        mySession.setCallback(new MediaSessionCompat.Callback() {
            @Override
            public void onPlay() {
                super.onPlay();
                playMusic();
            }

            @Override
            public void onPause() {
                super.onPause();
                pauseMusic();
            }

            @Override
            public void onStop() {
                super.onStop();
                stopMusic();
            }
        });
        mySession.setActive(true);
        String action = "";
        if (intent == null) {
            Log.d(TAG, "Intent passed to this service was null");
            stopSelf();
        } else {
            action = intent.getAction();
        }
        switch (action) {
            case ACTION_PLAY_RADIO:
                playMusic();
                break;
            case ACTION_PAUSE_RADIO:
                pauseMusic();
                break;
            case ACTION_STOP_RADIO:
                stopMusic();
                break;
            case ACTION_GET_SONG_NAME:
                Log.d(TAG, "Returning song name");
                sendSongNameChangeBroadcast();
                if (player == null) {
                    stopSelf();
                }
                break;
            case ACTION_GET_PLAYER_STATE:
                Log.d(TAG, "Returning player state");
                broadcastState();
                if (player == null) {
                    stopSelf();
                }
                break;
            default:
                MediaButtonReceiver.handleIntent(mySession, intent);
                break;
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (songNameTask != null) {
            songNameTask.cancel(true);
            songNameTask = null;
        }
        if (streamTask != null) {
            streamTask.cancel(true);
            streamTask = null;
        }
        if (songTimer != null) {
            songTimer.cancel();
            songTimer = null;
        }
        if (streamTimer != null) {
            streamTimer.cancel();
        }
        if (player != null) {
            player.release();
            player = null;
        }
        if (wifiLock != null) {
            if (wifiLock.isHeld()) {
                wifiLock.release();
            }
            wifiLock = null;
        }
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        mAudioManager.abandonAudioFocus(this);
        //unregisterReceiver(connectivityReceiver);
        Log.d(TAG, "Service Destroyed");
    }

    @Override
    public void onPrepared(MediaPlayer mediaPlayer) {
        Log.d(TAG, "Player prepared!");
        sendCancelProgressBroadcast();
        Log.d(TAG, "Requesting audio focus");
        mAudioManager = (AudioManager) getSystemService(Context.AUDIO_SERVICE);
        int result = mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN);
        if (result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED) {
            Log.d(TAG, "Audio focus gained");
            Log.d(TAG, "Starting media player");
            player.start();
            MediaMetadataCompat metadata = getDefaultMetadata();
            MediaMetadataCompat.Builder metadataBuilder = new MediaMetadataCompat.Builder(metadata);
            metadataBuilder.putString(MediaMetadataCompat.METADATA_KEY_DISPLAY_TITLE, currentSong);
            mySession.setMetadata(metadataBuilder.build());
            PlaybackStateCompat.Builder stateBuilder = new PlaybackStateCompat.Builder();
            stateBuilder.setState(PlaybackStateCompat.STATE_PLAYING, PlaybackStateCompat.PLAYBACK_POSITION_UNKNOWN, 1);
            //TODO SET ACTIONS
            mySession.setPlaybackState(stateBuilder.build());
            //TODO Update metadata and state (state and actions) whenever things change
            Log.d(TAG, "Player started");
            doNotification(CREATE_NOTIFICATION);
            songTimer = new Timer();
            songTimer.schedule(new NameCheck(), 0, 10000);
            streamTimer = new Timer();
            int second = Calendar.getInstance().get(Calendar.SECOND);
            int secondsToWait = 60 - second;
            streamTimer.schedule(new StreamCheck(), secondsToWait, 30000);
            Intent playIntent = new Intent(SAFEHOUSE_INTENT_FILTER)
                    .putExtra("action", ACTION_PLAY_RADIO);
            sendBroadcast(playIntent);
        } else {
            Log.d(TAG, "Could not get audio focus");
            Toast.makeText(getApplicationContext(), "Could not get audio focus, please try again"
                    , Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    public boolean onError(MediaPlayer mediaPlayer, int i, int i1) {
        //TODO ERRORS!
        //This will be in the actual release! player.reset();
        stopSelf();
        return false;
    }

    @Override
    public void onAudioFocusChange(int focusChange) {
        switch (focusChange) {
            case AudioManager.AUDIOFOCUS_GAIN:
                if (player == null) {
                    initMediaPlayer();
                } else {
                    if (!player.isPlaying()) {
                        player.start();
                        broadcastState();
                    }
                }
                player.setVolume(1.0f, 1.0f);
                break;
            case AudioManager.AUDIOFOCUS_LOSS:
                if (player != null) {
                    if (player.isPlaying()) {
                        player.pause();
                        broadcastState();
                    }
                    releaseMediaPlayer();
                    mAudioManager.abandonAudioFocus(this);
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                if (player.isPlaying()) {
                    player.pause();
                    broadcastState();
                }
                break;
            case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                if (player.isPlaying()) {
                    player.setVolume(0.1f, 0.1f);
                }
                break;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * Frees up system resources by releasing MediaPlayer, and nullifying player field. Also stops
     * the foreground notification, and the service as a whole (calls stopSelf()).
     */
    private void releaseMediaPlayer() {
        Log.d(TAG, "Releasing player...");
        player.release();
        Log.d(TAG, "Player released");
        player = null;
        if (wifiLock.isHeld()) {
            wifiLock.release();
            Log.d(TAG, "WifiLock released");
        }
        stopForeground(true);
        stopSelf();
    }

    private void playMusic() {
        if (player == null) {
            //Player needs to be initialised
            initMediaPlayer();
        } else if (!player.isPlaying()) {
            Log.d(TAG, "Player starting...");
            player.start();
            //Remove pause and replace with play
            doNotification(UPDATE_PLAY);
            Intent playIntent = new Intent(SAFEHOUSE_INTENT_FILTER)
                    .putExtra("action", ACTION_PLAY_RADIO);
            sendBroadcast(playIntent);
            Log.d(TAG, "PLAY_RADIO broadcast sent");
        }
    }

    private void pauseMusic() {
        if (player != null) {
            player.pause();
            //Remove play and replace with pause
            doNotification(UPDATE_PAUSE);
            Intent pauseIntent = new Intent(SAFEHOUSE_INTENT_FILTER)
                    .putExtra("action", ACTION_PAUSE_RADIO);
            sendBroadcast(pauseIntent);
            Log.d(TAG, "PAUSE_RADIO broadcast sent");
        }
    }

    private void stopMusic() {
        Log.d(TAG, "Player stopping");
        player.stop();
        Intent stopRadio = new Intent(SAFEHOUSE_INTENT_FILTER)
                .putExtra("action", ACTION_STOP_RADIO);
        sendBroadcast(stopRadio);
        releaseMediaPlayer();
    }

    /**
     * Starts GetStreamTask AsyncTask, which itself calls prepareForPlayBack(String url)
     */
    private void initMediaPlayer() {
        sendShowProgressBroadcast();
        streamTask = new GetStreamTask();
        streamTask.execute();
    }

    /**
     * Prepares the MediaPlayer object for playback. Gets CPU and WiFi wakelock, and calls
     * prepareAsync()
     */
    private void prepareForPlayback() {
        player = new MediaPlayer();
        player.setAudioStreamType(AudioManager.STREAM_MUSIC);
        try {
            player.setDataSource(currentStream);
            Log.d(TAG, "Player's data source set to " + currentStream);
            player.setOnPreparedListener(this);
            player.setOnErrorListener(this);
            player.setWakeMode(getApplicationContext(), PowerManager.PARTIAL_WAKE_LOCK);
            Log.d(TAG, "Partial wake lock set");
            wifiLock = ((WifiManager) getSystemService(Context.WIFI_SERVICE))
                    .createWifiLock(WifiManager.WIFI_MODE_FULL, "WifiLock");
            Log.d(TAG, "WifiLock created");
            wifiLock.acquire();
            Log.d(TAG, "WifiLock acquired");
            player.prepareAsync();
            Log.d(TAG, "Player preparing...");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public MediaMetadataCompat getDefaultMetadata() {
        return new MediaMetadataCompat.Builder()
            .putBitmap(MediaMetadataCompat.METADATA_KEY_ART, BitmapFactory.decodeResource(getResources(), R.drawable.logo_with_mic_background))
            .build();
    }

    /**
     * Given an InputStream this method will translate it to a String object.
     *
     * @param inputStream InputStream object to be used
     * @return String. String representation of the inputStream parameter
     */
    private String getStringFromInputStream(InputStream inputStream) {
        StringWriter writer = new StringWriter();
        String string = "";
        if (inputStream != null) {
            try {
                IOUtils.copy(inputStream, writer, "UTF-8");
                string = writer.toString();
            } catch (IOException e) {
                Log.e("PlayerService", e.getMessage());
            }
        }
        return string;
    }

    /**
     * Swaps the current stream
     */
    private void swapStreams() {
        player.release();
        player = null;
        prepareForPlayback();
        if (currentStream.equals("http://eu3.radioboss.fm:8113/live")) {
            songTimer.cancel();
            sendSongNameChangeBroadcast();
        } else {
            songTimer.schedule(new NameCheck(), 10000, 10000);
        }

    }

    //<-----------------------------------Notifications-------------------------------------------->

    /**
     * Starts an AsyncTask to get the song name, and then calls updateNotification()
     *
     * @param command String. Command to be passed to updateNotification(). See Javadoc for
     *                updateNotification for valid fields
     */
    private void doNotification(String command) {
        songNameTask = new GetSongNameTask();
        songNameTask.execute(command);
    }

    /**
     * Updates this Service's notification. The command given determines how the notification will
     * change. UPDATE_PLAY removes the pause action and replaces it with a play action. UPDATE_PAUSE
     * removes the play action and replaces it with a pause. UPDATE_NAME simply changes the current
     * song title.
     *
     * @param command String. Determines which part of the notification is updated
     */
    private void updateNotification(String command) {
        Notification notification;
        switch (command) {
            case UPDATE_PLAY: {
                notification = getPlayingNotification();
                break;
            }
            case UPDATE_PAUSE: {
                notification = getPausedNotification();
                break;
            }
            case CREATE_NOTIFICATION: {
                notification = getPlayingNotification();
                Log.d(TAG, "Notification started");
                break;
            }
            case UPDATE_NAME:
                notification = player.isPlaying() ? getPlayingNotification() : getPausedNotification();
                break;
            default:
                notification = null;
                break;
        }
        if (notification != null) {
            notification.flags |= Notification.FLAG_ONGOING_EVENT;
            startForeground(MUSIC_PLAYING_NOTIFICATION_ID, notification);
        }
    }

    /**
     * Creates a notification that indicates music is playing. Includes two actions: pause and stop.
     * Pause will pause the current playing music, and stop will stop the music and stop the service
     *
     * @return Notification. A notification that indicates music is playing.
     */
    private Notification getPlayingNotification() {
        return new Notification.Builder(getApplicationContext())
                .setTicker("Music playing")
                .setSmallIcon(R.drawable.ic_play_arrow_black_24dp)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_with_mic_background))
                .setContentTitle("Now playing")
                .setContentText(currentSong)
                .setContentIntent(getStartPendingIntent())
                .addAction(R.drawable.ic_pause_black_24dp, "Pause", getPausePendingIntent())
                .addAction(R.drawable.ic_stop_black_24dp, "Stop", getStopPendingIntent())
                //.setStyle(new NotificationCompat.MediaStyle()
                //.setMediaSession(mySession.getSessionToken()))
                .build();
    }

    /**
     * Creates a notification that indicates music is paused. Includes two actions: play and stop.
     * Play will resume playback, and stop will end the service.
     *
     * @return Notification. A notification indicating music is paused.
     */
    private Notification getPausedNotification() {
        return new Notification.Builder(getApplicationContext())
                .setTicker("Music playing")
                .setSmallIcon(R.drawable.ic_play_arrow_black_24dp)
                .setLargeIcon(BitmapFactory.decodeResource(getResources(), R.drawable.logo_with_mic_background))
                .setContentTitle("Now playing")
                .setContentText(currentSong)
                .setContentIntent(getStartPendingIntent())
                .addAction(R.drawable.ic_play_arrow_black_24dp, "Play", getPlayPendingIntent())
                .addAction(R.drawable.ic_stop_black_24dp, "Stop", getStopPendingIntent())
                //.setStyle(new NotificationCompat.MediaStyle()
                //        .setMediaSession(mySession.getSessionToken()))
                .build();
    }

    //<--------------------------------------Broadcasts-------------------------------------------->

    /**
     * Sends the song name broadcast to update this app's activity
     */
    private void sendSongNameChangeBroadcast() {
        Intent songNameChangeIntent = new Intent(SAFEHOUSE_INTENT_FILTER)
                .putExtra("action", ACTION_GET_SONG_NAME)
                .putExtra("songName", currentSong);
        sendBroadcast(songNameChangeIntent);
        Log.d(TAG, "GET_SONG broadcast sent");
    }

    private void broadcastState() {
        boolean isPlaying = player != null && player.isPlaying();
        boolean isPaused = player != null && !player.isPlaying();
        Intent playerStateIntent = new Intent(SAFEHOUSE_INTENT_FILTER)
                .putExtra("action", ACTION_GET_PLAYER_STATE)
                .putExtra("isPlaying", isPlaying)
                .putExtra("isPaused", isPaused);
        sendBroadcast(playerStateIntent);
        Log.d(TAG, "PLAYER_STATE broadcast sent");
    }

    private void sendShowProgressBroadcast() {
        Intent showProgress = new Intent(SAFEHOUSE_INTENT_FILTER)
                .putExtra("action", MainActivity.SHOW_PROGRESS_DIALOGUE);
        sendBroadcast(showProgress);
    }

    private void sendCancelProgressBroadcast() {
        Intent stopProgressDialogue = new Intent(SAFEHOUSE_INTENT_FILTER)
                .putExtra("action", MainActivity.CANCEL_PROGRESS_DIALOGUE);
        sendBroadcast(stopProgressDialogue);
    }

    //<------------------------------------PendingIntents------------------------------------------>

    /**
     * Gets a "start activity" PendingIntent for use in a notification for this application. Will
     * start the MainActivity class
     *
     * @return PendingIntent. PendingIntent that starts MainActivity
     */
    private PendingIntent getStartPendingIntent() {
        return PendingIntent.getActivity(getApplicationContext(), 0,
                new Intent(getApplicationContext(), MainActivity.class),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Gets a "play music" PendingIntent for use in an action for a notification in this application.
     * Will resume playback of the MediaPlayer.
     *
     * @return PendingIntent. PendingIntent that resumes playback.
     */
    private PendingIntent getPlayPendingIntent() {
        return PendingIntent.getService(getApplicationContext(), 0,
                new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PLAY_RADIO),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Gets a "stop music" PendingIntent for use in an action for a notification in this application.
     * Will stop playback of the MediaPlayer and end the service.
     *
     * @return PendingIntent. PendingIntent that stops playback and ends the Service.
     */
    private PendingIntent getStopPendingIntent() {
        return PendingIntent.getService(getApplicationContext(), 0,
                new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_STOP_RADIO),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /**
     * Gets a "pause music" PendingIntent for use in an action for a notification in this application.
     * Will pause playback of the MediaPlayer.
     *
     * @return PendingIntent. PendingIntent that pauses playback
     */
    private PendingIntent getPausePendingIntent() {
        return PendingIntent.getService(getApplicationContext(), 0,
                new Intent(this, PlayerService.class).setAction(PlayerService.ACTION_PAUSE_RADIO),
                PendingIntent.FLAG_UPDATE_CURRENT);
    }

    //<------------------------------------AsyncTasks---------------------------------------------->

    private class GetSongNameTask extends AsyncTask<String, Void, Void> {

        private String name;
        private String command;

        @Override
        protected Void doInBackground(String... strings) {
            command = strings[0];
            InputStream in = null;
            try {
                in = new URL("http://eu3.radioboss.fm:8113/status-json.xsl").openStream();
                name = JsonParser.getSongName(getStringFromInputStream(in), currentStream);
            } catch (MalformedURLException ex) {
                name = "Unknown";
            } catch (IOException e) {
                name = "Unknown";
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Log.e("PlayerService", "finally block exception GetSongNameTask");
                    }
                }
            }
            currentSong = name;
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            updateNotification(command);
        }
    }

    private class GetStreamTask extends AsyncTask<Void, Void, Void> {

        @Override
        protected Void doInBackground(Void... voids) {
            InputStream in = null;
            try {
                in = new URL("http://eu3.radioboss.fm:8113/status-json.xsl").openStream();
                currentStream = JsonParser.getStreamUrl(getStringFromInputStream(in));
            } catch (MalformedURLException e) {
                currentStream = "error";
            } catch (IOException e) {
                currentStream = "error";
            } finally {
                if (in != null) {
                    try {
                        in.close();
                    } catch (IOException e) {
                        Log.e("PlayerService", "finally block exception GetStreamTask");
                    }
                }
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) {
            prepareForPlayback();
        }
    }

    //<-----------------------------------TimerTasks----------------------------------------------->

    private class NameCheck extends TimerTask {

        @Override
        public void run() {
            InputStream in = null;
            try {
                Log.d(TAG, "Song name check...");
                in = new URL("http://eu3.radioboss.fm:8113/status-json.xsl").openStream();
                String name = JsonParser.getSongName(getStringFromInputStream(in), currentStream);
                currentSong = currentStream.equals("http://eu3.radioboss.fm:8113/live") ? "Live - " + name : name;
                sendSongNameChangeBroadcast();
                updateNotification(UPDATE_NAME);
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            } finally {
                try {
                    if (in != null) {
                        in.close();
                    }
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    private class StreamCheck extends TimerTask {

        @Override
        public void run() {
            try {
                Log.d(TAG, "Stream check...");
                InputStream in = new URL("http://eu3.radioboss.fm:8113/status-json.xsl").openStream();
                String stream = JsonParser.getStreamUrl(getStringFromInputStream(in));
                Log.d(TAG, "Stream = " + stream);
                if (!stream.equals(currentStream)) {
                    currentStream = stream;
                    swapStreams();
                }
            } catch (MalformedURLException e) {
                e.printStackTrace();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}