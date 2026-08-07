package com.amin.pocketgba;

import android.content.Context;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/** Minimal Google Sheets v4 values transport. Must be called off the main thread. */
final class GoogleSheetsApiTransport implements GoogleSheetsAdapter.Transport {
    private final GoogleSheetsConnectionStore connections;
    GoogleSheetsApiTransport(Context context) { connections = new GoogleSheetsConnectionStore(context); }

    @Override public JSONArray readHeaders(String sourceId, String table) throws Exception {
        JSONArray values = getValues(sourceId, table + "!1:1");
        return values.length() > 0 && values.optJSONArray(0) != null ? values.optJSONArray(0) : new JSONArray();
    }

    @Override public void appendRow(String sourceId, String table, JSONArray orderedValues) throws Exception {
        String spreadsheetId = requireSpreadsheet(sourceId); String token = requireToken(sourceId);
        String range = encode(table + "!A:ZZZ");
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + encode(spreadsheetId)
                + "/values/" + range + ":append?valueInputOption=USER_ENTERED&insertDataOption=INSERT_ROWS");
        JSONObject body = new JSONObject().put("majorDimension", "ROWS").put("values", new JSONArray().put(orderedValues));
        request(url, "POST", token, body.toString());
    }

    @Override public JSONArray readRows(String sourceId, String table) throws Exception {
        JSONArray values = getValues(sourceId, table + "!A:ZZZ");
        JSONArray out = new JSONArray();
        for (int i = 1; i < values.length(); i++) out.put(values.optJSONArray(i));
        return out;
    }

    private JSONArray getValues(String sourceId, String rangeRaw) throws Exception {
        String spreadsheetId = requireSpreadsheet(sourceId); String token = requireToken(sourceId);
        URL url = new URL("https://sheets.googleapis.com/v4/spreadsheets/" + encode(spreadsheetId) + "/values/" + encode(rangeRaw));
        JSONObject response = new JSONObject(request(url, "GET", token, null));
        JSONArray values = response.optJSONArray("values"); return values == null ? new JSONArray() : values;
    }

    private String request(URL url, String method, String token, String body) throws Exception {
        HttpURLConnection c = (HttpURLConnection) url.openConnection();
        c.setRequestMethod(method); c.setConnectTimeout(12000); c.setReadTimeout(15000);
        c.setRequestProperty("Authorization", "Bearer " + token); c.setRequestProperty("Accept", "application/json");
        if (body != null) {
            c.setDoOutput(true); c.setRequestProperty("Content-Type", "application/json; charset=utf-8");
            try (OutputStream os = c.getOutputStream()) { os.write(body.getBytes(StandardCharsets.UTF_8)); }
        }
        int code = c.getResponseCode(); InputStream in = code >= 200 && code < 300 ? c.getInputStream() : c.getErrorStream();
        String text = read(in); c.disconnect();
        if (code < 200 || code >= 300) throw new IllegalStateException("Google Sheets HTTP " + code + ": " + text);
        return text.isEmpty() ? "{}" : text;
    }

    private String requireSpreadsheet(String sourceId) { String id=connections.spreadsheetId(sourceId); if(id.isEmpty()) throw new IllegalStateException("source_id 尚未綁定 spreadsheet_id"); return id; }
    private String requireToken(String sourceId) { String token=connections.sessionToken(sourceId); if(token.isEmpty()) throw new IllegalStateException("本次工作階段尚未提供 Google OAuth token"); return token; }
    private static String encode(String s) throws Exception { return URLEncoder.encode(s, "UTF-8").replace("+", "%20"); }
    private static String read(InputStream in) throws Exception { if(in==null)return"";StringBuilder s=new StringBuilder();try(BufferedReader r=new BufferedReader(new InputStreamReader(in,StandardCharsets.UTF_8))){String line;while((line=r.readLine())!=null)s.append(line);}return s.toString(); }
}
