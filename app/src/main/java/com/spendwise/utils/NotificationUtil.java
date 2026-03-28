package com.spendwise.utils;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.os.Build;

import androidx.core.app.NotificationCompat;

import com.spendwise.MainActivity;
import com.spendwise.R;

import java.util.Locale;

public class NotificationUtil {
    
    private static final String CHANNEL_ID = "spendwise_transactions";
    private static final String CHANNEL_NAME = "Transaction Notifications";
    private static final int TRANSACTION_NOTIFICATION_ID = 100;
    
    public static void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_DEFAULT
            );
            channel.setDescription("Notifications for new transactions");
            
            NotificationManager notificationManager = 
                context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
    
    public static void showTransactionNotification(Context context, String merchant, 
                                                   double amount, String type) {
        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
            context, 0, intent, 
            PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT
        );
        
        String title;
        String text;
        int icon = android.R.drawable.ic_dialog_info;
        
        if ("income".equalsIgnoreCase(type)) {
            title = "Deposit Received";
            text = merchant + " - ₹" + String.format(Locale.getDefault(), "%.2f", amount);
        } else {
            title = "Payment Made";
            text = merchant + " - ₹" + String.format(Locale.getDefault(), "%.2f", amount);
        }
        
        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(icon)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setStyle(new NotificationCompat.BigTextStyle()
                .bigText(text));
        
        NotificationManager notificationManager = 
            (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
        if (notificationManager != null) {
            notificationManager.notify(TRANSACTION_NOTIFICATION_ID, builder.build());
        }
    }
}
