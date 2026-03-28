package com.spendwise.dialogs;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.adapters.TransactionAdapter;
import com.spendwise.data.Instrument;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class AccountDetailActivity extends AppCompatActivity {

    private AppDatabase db;
    private Instrument account;
    private TransactionAdapter adapter;
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_account_detail);

        db = SpendWiseApp.getDatabase();
        String accountId = getIntent().getStringExtra("accountId");
        RecyclerView rvTransactions = findViewById(R.id.rv_transactions);

        rvTransactions.setLayoutManager(new LinearLayoutManager(this));
        adapter = new TransactionAdapter(new java.util.ArrayList<>(), tx -> {
            Intent intent = new Intent(this, TransactionDetailActivity.class);
            intent.putExtra("txId", tx.getId());
            startActivity(intent);
        });
        rvTransactions.setAdapter(adapter);

        if (accountId != null && !accountId.isEmpty()) {
            new Thread(() -> {
                account = db.instrumentDao().getById(accountId);
                List<Transaction> linkedTransactions = db.transactionDao().getByInstrumentRefId(accountId);
                runOnUiThread(() -> updateUI(linkedTransactions));
            }).start();
        }

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_edit).setOnClickListener(v -> {
            if (account != null) {
                Intent intent = new Intent(this, EditInstrumentActivity.class);
                intent.putExtra("instrument_id", account.getId());
                startActivity(intent);
            }
        });
    }

    private void updateUI(List<Transaction> linkedTransactions) {
        TextView tvTitle = findViewById(R.id.tv_title);
        TextView tvSubtitle = findViewById(R.id.tv_subtitle);
        TextView tvMonthIncome = findViewById(R.id.tv_month_income);
        TextView tvMonthExpense = findViewById(R.id.tv_month_expense);
        TextView tvMonthNet = findViewById(R.id.tv_month_net);
        TextView tvTxCount = findViewById(R.id.tv_tx_count);
        TextView tvNoTransactions = findViewById(R.id.tv_no_transactions);

        if (account != null) {
            String name = safeText(account.getNickname(), "Account");
            String bank = safeText(account.getBankName(), "Unknown bank");
            String masked = safeText(account.getInstrumentIdMasked(), "UNKNOWN");

            tvTitle.setText(name);
            tvSubtitle.setText("UNKNOWN".equalsIgnoreCase(masked) ? bank : bank + " • " + masked);
        }

        double monthIn = 0;
        double monthOut = 0;
        long monthStart = startOfMonthMs();

        for (Transaction tx : linkedTransactions) {
            long txDate = parseDate(tx.getDate());
            if (txDate >= monthStart) {
                if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                    monthIn += tx.getAmount();
                } else {
                    monthOut += tx.getAmount();
                }
            }
        }

        double monthNet = monthIn - monthOut;
        tvMonthIncome.setText(fmt(monthIn));
        tvMonthExpense.setText(fmt(monthOut));
        tvMonthNet.setText("Net this month: " + fmt(monthNet));
        tvMonthNet.setTextColor(monthNet >= 0 ? Color.parseColor("#00C896") : Color.parseColor("#FF5A5A"));

        tvTxCount.setText(linkedTransactions.size() + " transaction" + (linkedTransactions.size() == 1 ? "" : "s"));
        if (linkedTransactions.isEmpty()) {
            tvNoTransactions.setVisibility(TextView.VISIBLE);
        } else {
            tvNoTransactions.setVisibility(TextView.GONE);
        }

        adapter.updateTransactions(linkedTransactions);
    }

    private String safeText(String value, String fallback) {
        if (value == null || value.trim().isEmpty()) {
            return fallback;
        }
        return value.trim();
    }

    private long parseDate(String dateStr) {
        try {
            Date parsed = dateFormat.parse(dateStr);
            return parsed != null ? parsed.getTime() : 0;
        } catch (ParseException | NullPointerException e) {
            return 0;
        }
    }

    private long startOfMonthMs() {
        java.util.Calendar cal = java.util.Calendar.getInstance();
        cal.set(java.util.Calendar.DAY_OF_MONTH, 1);
        cal.set(java.util.Calendar.HOUR_OF_DAY, 0);
        cal.set(java.util.Calendar.MINUTE, 0);
        cal.set(java.util.Calendar.SECOND, 0);
        cal.set(java.util.Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private String fmt(double value) {
        return String.format(Locale.getDefault(), "₹%,.2f", value);
    }
}
