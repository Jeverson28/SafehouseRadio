package com.example.james.safehouseradio;

import android.util.Log;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

class JsonParser {

    static String getSongName(String json, String stream) {
        String songName = "";
        try {
            JSONArray sources = getSources(json);
            if (sources != null) {
                for (int i = 0; i < sources.length(); i++) {
                    JSONObject source = sources.getJSONObject(i);
                    if (source.getString("listenurl").equals(stream) && source.has("yp_currently_playing")) {
                        songName = source.getString("yp_currently_playing");
                    }
                }
            } else {
                songName = "Could not get song name";
            }
        } catch (JSONException e) {
            Log.e("JsonParser", e.getMessage());
            songName = "Could not get song name";
        }
        return songName;
    }

    static String getStreamUrl(String json) {
        String url = "http://eu3.radioboss.fm:8113/autodj";
        JSONArray sources = getSources(json);
        try {
            for (int i = 0; i < sources.length(); i++) {
                JSONObject source = sources.getJSONObject(i);
                if (source.getString("listenurl").equals("http://eu3.radioboss.fm:8113/live")) {
                    if (source.length() > 3) {
                        url = source.getString("listenurl");
                    }
                }
            }
        } catch (JSONException e) {
            Log.e("JsonParser", e.getMessage());
            url = "http://eu3.radioboss.fm:8113/autodj";
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
