package com.example.james.safehouseradio;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JsonParser {

    private static final String autoDjStream = "http://eu3.radioboss.fm:8113/autodj";
    private static final String liveStream = "http://eu3.radioboss.fm:8113/live";

    static String getSongName(String json, String stream) {
        String songName = "";
        try {
            JSONArray sources = getSources(json);
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                if (source.getString("listenurl").equals(stream)) {
                    songName = stream.equals(liveStream) ? source.getString("server_name") : source.getString("yp_currently_playing");
                }
            }
        } catch (JSONException e) {
            Log.e("JsonParser", e.getMessage());
            songName = "Could not get song name";
        }
        return songName;
    }

    static String getStreamUrl(String json) {
        String url = autoDjStream;
        JSONArray sources = getSources(json);
        try {
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                if (source.getString("listenurl").equals(liveStream)) {
                    if (source.length() > 3) {
                        url = liveStream;
                    }
                }
            }
        } catch (JSONException e) {
            Log.e("JsonParser", e.getMessage());
            url = autoDjStream;
        }
        return url;
    }

    private static JSONArray getSources(String json) {
        JSONArray sources = null;
        try {
            JSONObject home = new JSONObject(json);
            JSONObject info = home.getJSONObject("icestats");
            sources = info.getJSONArray("source");
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return sources;
    }
}
