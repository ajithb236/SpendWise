package com.spendwise.db;

import android.content.Context;
import android.database.Cursor;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.sqlite.db.SupportSQLiteDatabase;
import androidx.annotation.NonNull;
import androidx.room.migration.Migration;

import com.spendwise.data.Alias;
import com.spendwise.data.Instrument;
import com.spendwise.data.InstrumentAlias;
import com.spendwise.data.Transaction;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.TimeZone;
import java.util.regex.Pattern;

@Database(entities = {Transaction.class, Alias.class, Instrument.class, InstrumentAlias.class}, version = 6, exportSchema = false)
public abstract class AppDatabase extends RoomDatabase {

    private static volatile AppDatabase INSTANCE;

    private static final Migration MIGRATION_1_2 = new Migration(1, 2) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN instrumentType TEXT");
            database.execSQL("ALTER TABLE transactions ADD COLUMN instrumentId TEXT");
            database.execSQL("UPDATE transactions SET instrumentType = 'UNKNOWN' WHERE instrumentType IS NULL");
            database.execSQL("UPDATE transactions SET instrumentId = 'UNKNOWN' WHERE instrumentId IS NULL");
        }
    };

    private static final Migration MIGRATION_2_3 = new Migration(2, 3) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE transactions ADD COLUMN instrumentRefId TEXT");
            database.execSQL("UPDATE transactions SET instrumentRefId = 'UNKNOWN' WHERE instrumentRefId IS NULL");

            database.execSQL("CREATE TABLE IF NOT EXISTS instruments (" +
                    "id TEXT NOT NULL, " +
                    "nickname TEXT, " +
                    "instrumentType TEXT, " +
                    "instrumentIdMasked TEXT, " +
                    "bankName TEXT, " +
                    "isActive INTEGER NOT NULL, " +
                    "isComplete INTEGER NOT NULL, " +
                    "cycleStartDay INTEGER NOT NULL, " +
                    "paymentDueDay INTEGER NOT NULL, " +
                    "notes TEXT, " +
                    "PRIMARY KEY(id))");

            database.execSQL("CREATE TABLE IF NOT EXISTS instrument_aliases (" +
                    "aliasKey TEXT NOT NULL, " +
                    "instrumentRefId TEXT, " +
                    "PRIMARY KEY(aliasKey))");
        }
    };

    /**
     * Data migration script: normalize legacy mixed date values in transactions.date to yyyy-MM-dd.
     */
    private static final Migration MIGRATION_4_5 = new Migration(4, 5) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            Cursor cursor = database.query("SELECT id, date FROM transactions");
            try {
                int idIdx = cursor.getColumnIndex("id");
                int dateIdx = cursor.getColumnIndex("date");
                while (cursor.moveToNext()) {
                    if (idIdx < 0 || dateIdx < 0) {
                        continue;
                    }

                    String id = cursor.getString(idIdx);
                    String rawDate = cursor.getString(dateIdx);
                    String normalized = normalizeDateForMigration(rawDate);

                    if (id != null && normalized != null && !normalized.equals(rawDate)) {
                        database.execSQL("UPDATE transactions SET date = ? WHERE id = ?", new Object[] { normalized, id });
                    }
                }
            } finally {
                cursor.close();
            }
        }
    };

    private static final Migration MIGRATION_5_6 = new Migration(5, 6) {
        @Override
        public void migrate(@NonNull SupportSQLiteDatabase database) {
            database.execSQL("ALTER TABLE aliases ADD COLUMN category TEXT");
            database.execSQL("UPDATE aliases SET category = 'OTHER' WHERE category IS NULL OR TRIM(category) = ''");
        }
    };

    private static final Pattern EPOCH_PATTERN = Pattern.compile("^\\d{10,13}$");

    private static String normalizeDateForMigration(String rawDate) {
        if (rawDate == null || rawDate.trim().isEmpty()) {
            return canonicalToday();
        }

        String trimmed = rawDate.trim();

        if (trimmed.matches("^\\d{4}-\\d{1,2}-\\d{1,2}$")) {
            Date parsed = parseWithFormat(trimmed, "yyyy-M-d", Locale.getDefault());
            return parsed != null ? formatCanonical(parsed) : canonicalToday();
        }

        if (EPOCH_PATTERN.matcher(trimmed).matches()) {
            try {
                long epoch = Long.parseLong(trimmed);
                if (trimmed.length() == 10) {
                    epoch *= 1000L;
                }
                return formatCanonical(new Date(epoch));
            } catch (NumberFormatException ignored) {
            }
        }

        Date parsed = null;
        String[] patterns = new String[] {
                "dd MMM yyyy",
                "dd-MM-yyyy",
                "dd-MM-yy",
                "dd/MM/yyyy",
                "yyyy/MM/dd",
                "EEE MMM dd HH:mm:ss zzz yyyy"
        };

        for (String pattern : patterns) {
            Locale locale = "EEE MMM dd HH:mm:ss zzz yyyy".equals(pattern) ? Locale.ENGLISH : Locale.getDefault();
            parsed = parseWithFormat(trimmed, pattern, locale);
            if (parsed != null) {
                break;
            }
        }

        return parsed != null ? formatCanonical(parsed) : canonicalToday();
    }

    private static Date parseWithFormat(String value, String pattern, Locale locale) {
        SimpleDateFormat sdf = new SimpleDateFormat(pattern, locale);
        sdf.setLenient(false);
        sdf.setTimeZone(TimeZone.getDefault());
        try {
            return sdf.parse(value);
        } catch (ParseException e) {
            return null;
        }
    }

    private static String formatCanonical(Date date) {
        return new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(date);
    }

    private static String canonicalToday() {
        return formatCanonical(new Date());
    }

    public abstract TransactionDao transactionDao();
    public abstract AliasDao aliasDao();
    public abstract InstrumentDao instrumentDao();
    public abstract InstrumentAliasDao instrumentAliasDao();

    public static AppDatabase getInstance(final Context context) {
        if (INSTANCE == null) {
            synchronized (AppDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(context.getApplicationContext(),
                                    AppDatabase.class, "finance_tracker_db")
                        .addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_4_5, MIGRATION_5_6)
                        .allowMainThreadQueries()
                        .build();
                }
            }
        }
        return INSTANCE;
    }
}
