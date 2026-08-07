package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class GoogleSheetsAdapterTest {
    @Test public void mapsRecordByHeaderNotFixedColumn() throws Exception {
        JSONArray headers=new JSONArray().put("category").put("date").put("note").put("amount");
        JSONObject record=new JSONObject().put("date","2026-08-07").put("amount",180).put("category","food").put("note","晚餐");
        JSONArray row=GoogleSheetsAdapter.mapRecordToHeaders(headers,record);
        assertEquals("food",row.getString(0));assertEquals("2026-08-07",row.getString(1));assertEquals("晚餐",row.getString(2));assertEquals(180,row.getInt(3));
    }

    @Test public void filtersRowsIntoDataViewRecords() throws Exception {
        JSONArray headers=new JSONArray().put("date").put("category").put("amount");
        JSONArray rows=new JSONArray().put(new JSONArray().put("2026-08-07").put("food").put(180)).put(new JSONArray().put("2026-08-07").put("transport").put(40));
        JSONArray out=GoogleSheetsAdapter.rowsToRecords(headers,rows,new JSONObject().put("category","food"));
        assertEquals(1,out.length());assertEquals(180,out.getJSONObject(0).getInt("amount"));
    }
}
