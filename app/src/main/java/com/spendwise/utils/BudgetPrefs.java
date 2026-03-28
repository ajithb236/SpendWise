package com.spendwise.utils;

import android.content.Context;
import android.content.SharedPreferences;

public final class BudgetPrefs {

    private static final String PREFS_NAME = "spendwise_prefs";
    private static final String KEY_DAILY_BUDGET = "daily_budget";

    private BudgetPrefs() {
    }

    public static void setDailyBudget(Context context, double budget) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        prefs.edit().putFloat(KEY_DAILY_BUDGET, (float) budget).apply();
    }

    public static double getDailyBudget(Context context) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        return prefs.getFloat(KEY_DAILY_BUDGET, 0f);
    }
}