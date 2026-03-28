package com.spendwise;

import android.app.Application;

import com.spendwise.db.AppDatabase;
import com.spendwise.utils.NotificationUtil;

public class SpendWiseApp extends Application {
    
    private static AppDatabase database;
    
    @Override
    public void onCreate() {
        super.onCreate();
        
        
        database = AppDatabase.getInstance(this);
        
         
        NotificationUtil.createNotificationChannel(this);
    }
    
    public static AppDatabase getDatabase() {
        return database;
    }
}
