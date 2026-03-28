package com.spendwise.workers;

import android.content.Context;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.work.Worker;
import androidx.work.WorkerParameters;

import com.spendwise.db.AppDatabase;

public class SmsProcessingWorker extends Worker {
    
    private static final String TAG = "SmsProcessingWorker";
    
    public SmsProcessingWorker(@NonNull Context context, @NonNull WorkerParameters params) {
        super(context, params);
    }
    
    @NonNull
    @Override
    public Result doWork() {
        try {
            Context context = getApplicationContext();
            AppDatabase db = AppDatabase.getInstance(context);

            Log.d(TAG, "SMS Processing worker executing");
            
            return Result.success();
        } catch (Exception e) {
            Log.e(TAG, "Error in SMS processing worker", e);
            return Result.retry();
        }
    }
}
