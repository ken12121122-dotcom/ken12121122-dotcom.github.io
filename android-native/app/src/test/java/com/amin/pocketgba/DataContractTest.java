package com.amin.pocketgba;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class DataContractTest {
    private DataContract contract() throws Exception {
        return new DataContract(new JSONObject()
                .put("contract_id","transaction_v1")
                .put("fields",new JSONArray()
                        .put(new JSONObject().put("key","amount").put("label","金額").put("type","number").put("required",true))
                        .put(new JSONObject().put("key","category").put("label","分類").put("type","select").put("required",true).put("options",new JSONArray().put("food").put("transport")))
                        .put(new JSONObject().put("key","paid").put("label","已付款").put("type","boolean").put("required",false))));
    }

    @Test public void validatesSupportedSchemaAndRecord() throws Exception {
        DataContract c=contract();assertTrue(c.validateSchema().valid);
        assertTrue(c.validateRecord(new JSONObject().put("amount",180).put("category","food").put("paid",true)).valid);
    }

    @Test public void rejectsMissingRequiredAndBadSelect() throws Exception {
        DataContract.Validation v=contract().validateRecord(new JSONObject().put("category","other"));
        assertFalse(v.valid);assertTrue(v.errors.toString().contains("amount"));assertTrue(v.errors.toString().contains("invalid option"));
    }

    @Test public void contextPrefillsButRecordCanOverride() throws Exception {
        JSONObject out=contract().applyContext(new JSONObject().put("category","food"),new JSONObject().put("category","transport").put("amount",20));
        assertEquals("transport",out.getString("category"));assertEquals(20,out.getInt("amount"));
    }
}
