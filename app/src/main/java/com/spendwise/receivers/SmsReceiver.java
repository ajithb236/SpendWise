package com.spendwise.receivers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.spendwise.MainActivity;
import com.spendwise.R;
import com.spendwise.db.AppDatabase;
import com.spendwise.data.Alias;
import com.spendwise.data.Transaction;
import com.spendwise.utils.AliasResolver;
import com.spendwise.utils.HybridInferenceManager;
import com.spendwise.utils.InstrumentLinker;
import com.spendwise.utils.TransactionParser;

import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String CHANNEL_ID = "transaction_notifications";
    private static final ExecutorService SMS_EXECUTOR = Executors.newSingleThreadExecutor();
    private static final String PREFS_NAME = "SpendWisePrefs";
    private static final String KEY_NOTIFICATIONS = "notifications_enabled";

    @Override
    public void onReceive(Context context, Intent intent) {
        if ("android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            final BroadcastReceiver.PendingResult pendingResult = goAsync();
            SMS_EXECUTOR.execute(() -> {
                try {
                    handleIncomingSms(context, intent);
                } catch (Exception e) {
                    Log.e(TAG, "Unexpected failure while processing SMS broadcast.", e);
                } finally {
                    pendingResult.finish();
                }
            });
        }
    }

    private void handleIncomingSms(Context context, Intent intent) {
        Bundle bundle = intent.getExtras();
        if (bundle == null) {
            return;
        }

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null || pdus.length == 0) {
            return;
        }

        String format = bundle.getString("format");
        Map<String, StringBuilder> messageBySender = new LinkedHashMap<>();

        for (Object pdu : pdus) {
            SmsMessage smsMessage = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (smsMessage == null) {
                continue;
            }

            String sender = smsMessage.getOriginatingAddress();
            if (sender == null || sender.trim().isEmpty()) {
                sender = "UNKNOWN";
            }

            String body = smsMessage.getMessageBody();
            if (body == null) {
                body = "";
            }

            messageBySender.computeIfAbsent(sender, ignored -> new StringBuilder()).append(body);
        }

        for (Map.Entry<String, StringBuilder> entry : messageBySender.entrySet()) {
            String sender = entry.getKey();
            String messageBody = entry.getValue().toString();
            Log.d(TAG, "SMS received from " + sender + ": " + messageBody);
            processSms(context, messageBody, sender);
        }
    }

    private void processSms(Context context, String messageBody, String sender) {
        long parsedAtMillis = System.currentTimeMillis();
        AppDatabase db = AppDatabase.getInstance(context);
        List<Alias> aliases = db.aliasDao().getAll();

        Transaction transaction = null;
        HybridInferenceManager hybridManager = null;
        try {
            hybridManager = new HybridInferenceManager(context);
            transaction = hybridManager.parseSms(context, messageBody, sender, aliases, parsedAtMillis);
        } catch (IOException e) {
            Log.e(TAG, "Hybrid parser initialization failed. Falling back to regex parser.", e);
            transaction = TransactionParser.parseSms(context, messageBody, sender, aliases, parsedAtMillis);
            Log.w("HybridInference", "Parser used: REGEX_ONLY_DUE_TO_HYBRID_INIT_FAILURE");
        } catch (Exception e) {
            Log.e(TAG, "Hybrid parsing failed. Falling back to regex parser.", e);
            transaction = TransactionParser.parseSms(context, messageBody, sender, aliases, parsedAtMillis);
            Log.w("HybridInference", "Parser used: REGEX_ONLY_DUE_TO_HYBRID_RUNTIME_FAILURE");
        } finally {
            if (hybridManager != null) {
                hybridManager.cleanup();
            }
        }
        
        if (transaction != null) {
            if (!isCanonicalDate(transaction.getDate())) {
                transaction.setDate(new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(new Date(parsedAtMillis)));
            }

            Alias matchedAlias = AliasResolver.resolveAliasEntryForText(messageBody, aliases);
            if (matchedAlias == null) {
                matchedAlias = AliasResolver.resolveAliasEntryForText(transaction.getMerchantName(), aliases);
            }
            if (matchedAlias != null && matchedAlias.getCategory() != null && !matchedAlias.getCategory().trim().isEmpty()) {
                transaction.setCategory(matchedAlias.getCategory().trim());
            }

            String instrumentRefId = InstrumentLinker.resolveOrCreateInstrument(
                    db,
                    transaction.getInstrumentType(),
                    transaction.getInstrumentId(),
                    sender
            );
            transaction.setInstrumentRefId(instrumentRefId);
            db.transactionDao().insert(transaction);
            Log.d(TAG, "Transaction saved: " + transaction.getMerchantName() + " - " + transaction.getAmount());
            
            showNotificationIfEnabled(context, transaction);
        } else {
            Log.d(TAG, "SMS was not a recognized transaction.");
        }
    }

    private boolean isCanonicalDate(String value) {
        return value != null && value.matches("\\d{4}-\\d{2}-\\d{2}");
    }

    private void showNotificationIfEnabled(Context context, Transaction transaction) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        boolean notificationsEnabled = prefs.getBoolean(KEY_NOTIFICATIONS, true);

        if (!notificationsEnabled) {
            return;
        }

        createNotificationChannel(context);

        Intent intent = new Intent(context, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        PendingIntent pendingIntent = PendingIntent.getActivity(context, 0, intent, PendingIntent.FLAG_IMMUTABLE);

        String title = transaction.getType().equalsIgnoreCase(Transaction.TYPE_INCOME) ? "Income Received" : "Expense Tracked";
        String content = String.format(Locale.getDefault(), "₹%.2f at %s", transaction.getAmount(), transaction.getMerchantName());

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_notification)
                .setContentTitle(title)
                .setContentText(content)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU || 
            ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify((int) System.currentTimeMillis(), builder.build());
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "Transaction Alerts";
            String description = "Notifications for automatically tracked transactions";
            int importance = NotificationManager.IMPORTANCE_DEFAULT;
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            if (notificationManager != null) {
                notificationManager.createNotificationChannel(channel);
            }
        }
    }
}
