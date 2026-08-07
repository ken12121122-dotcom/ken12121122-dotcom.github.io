package com.amin.pocketgba;

import android.content.Context;

final class FinanceStorageConfig {
    static final String SOURCE_ID = "personal_finance";
    static final String SPREADSHEET_ID = "1v-n6x-jwP_LNUSn4kmSW7yzcVWyQokSTJCtQDnb4EXo";
    static final String SPREADSHEET_URL = "https://docs.google.com/spreadsheets/d/1v-n6x-jwP_LNUSn4kmSW7yzcVWyQokSTJCtQDnb4EXo/edit";

    static final String TRANSACTIONS = "Transactions";
    static final String ACCOUNTS = "Accounts";
    static final String CATEGORIES = "Categories";
    static final String ASSETS = "Assets";

    static final long TRANSACTIONS_SHEET_ID = 1413636105L;
    static final long ACCOUNTS_SHEET_ID = 1341913812L;
    static final long CATEGORIES_SHEET_ID = 306815438L;
    static final long ASSETS_SHEET_ID = 655656327L;

    private FinanceStorageConfig() {}

    static void ensureSourceMapping(Context context) {
        GoogleSheetsConnectionStore store = new GoogleSheetsConnectionStore(context);
        if (store.spreadsheetId(SOURCE_ID).trim().isEmpty()) {
            store.saveSource(SOURCE_ID, SPREADSHEET_ID);
        }
    }

    static String tabUrl(long sheetId) {
        return SPREADSHEET_URL + "#gid=" + sheetId;
    }
}
