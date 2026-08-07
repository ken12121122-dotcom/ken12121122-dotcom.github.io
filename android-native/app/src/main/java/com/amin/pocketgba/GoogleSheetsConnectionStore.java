package com.amin.pocketgba;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Maps abstract source_id to spreadsheet id. OAuth tokens are process-memory only. */
final class GoogleSheetsConnectionStore {
    private static final String PREFS = "amin_google_sheets_sources";
    private static final Map<String,String> TOKENS = new ConcurrentHashMap<>();
    private final SharedPreferences prefs;

    GoogleSheetsConnectionStore(Context context) { prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }
    void saveSource(String sourceId, String spreadsheetId) {
        String s=clean(sourceId), id=clean(spreadsheetId); if(s.isEmpty()||id.isEmpty()) throw new IllegalArgumentException("source_id / spreadsheet_id required");
        prefs.edit().putString(s,id).apply();
    }
    String spreadsheetId(String sourceId) { return prefs.getString(clean(sourceId), ""); }
    void setSessionToken(String sourceId, String token) { String s=clean(sourceId), t=clean(token); if(t.isEmpty()) TOKENS.remove(s); else TOKENS.put(s,t); }
    String sessionToken(String sourceId) { String t=TOKENS.get(clean(sourceId)); return t==null?"":t; }
    boolean isReady(String sourceId) { return !spreadsheetId(sourceId).isEmpty() && !sessionToken(sourceId).isEmpty(); }
    private static String clean(String s){return s==null?"":s.trim();}
}
