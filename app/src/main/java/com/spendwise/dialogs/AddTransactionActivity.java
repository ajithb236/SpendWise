package com.spendwise.dialogs;

import android.app.DatePickerDialog;
import android.graphics.Color;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

public class AddTransactionActivity extends AppCompatActivity {

    private static final String[] CATEGORIES = {
        "FOOD", "TRANSPORT", "SHOPPING", "UTILITIES", "ENTERTAINMENT",
        "HEALTHCARE", "SALARY", "OTHER"
    };

    private String transactionType = "expense";
    private AppDatabase db;
    private final SimpleDateFormat canonicalDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_transaction);
        db = SpendWiseApp.getDatabase();

        EditText etAmount = findViewById(R.id.et_amount);
        EditText etMerchant = findViewById(R.id.et_merchant);
        EditText etNote = findViewById(R.id.et_note);
        EditText etDate = findViewById(R.id.et_date);
        Spinner spinnerCategory = findViewById(R.id.spinner_category);
        TextView btnTypeExpense = findViewById(R.id.btn_type_expense);
        TextView btnTypeIncome = findViewById(R.id.btn_type_income);
        TextView btnSave = findViewById(R.id.btn_save);
        TextView tvError = findViewById(R.id.tv_error);

        canonicalDateFormat.setLenient(false);

        String today = canonicalDateFormat.format(new Date());
        etDate.setText(today);
        etDate.setOnClickListener(v -> openDatePicker(etDate));

        ArrayAdapter<String> catAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(catAdapter);

        btnTypeExpense.setOnClickListener(v -> {
            transactionType = "expense";
            btnTypeExpense.setBackground(getDrawable(R.drawable.rounded_button_expense));
            btnTypeExpense.setTextColor(Color.WHITE);
            btnTypeIncome.setBackground(null);
            btnTypeIncome.setTextColor(Color.parseColor("#8B9BBF"));
            btnSave.setBackground(getDrawable(R.drawable.rounded_button_expense));
        });

        btnTypeIncome.setOnClickListener(v -> {
            transactionType = "income";
            btnTypeIncome.setBackground(getDrawable(R.drawable.rounded_button_primary));
            btnTypeIncome.setTextColor(Color.WHITE);
            btnTypeExpense.setBackground(null);
            btnTypeExpense.setTextColor(Color.parseColor("#8B9BBF"));
            btnSave.setBackground(getDrawable(R.drawable.rounded_button_primary));
        });

        transactionType = "expense";
        btnTypeExpense.setBackground(getDrawable(R.drawable.rounded_button_expense));
        btnTypeExpense.setTextColor(Color.WHITE);
        btnSave.setBackground(getDrawable(R.drawable.rounded_button_expense));

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String amountStr = etAmount.getText().toString().trim();
            String merchant = etMerchant.getText().toString().trim();
            String selectedDate = etDate.getText().toString().trim();

            if (amountStr.isEmpty()) {
                tvError.setText("Please enter an amount");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (merchant.isEmpty()) {
                tvError.setText("Please enter a merchant name");
                tvError.setVisibility(View.VISIBLE);
                return;
            }
            if (!isCanonicalDate(selectedDate)) {
                tvError.setText("Please select a valid date");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            double amount;
            try {
                amount = Double.parseDouble(amountStr);
            } catch (NumberFormatException e) {
                tvError.setText("Invalid amount");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (amount <= 0) {
                tvError.setText("Amount must be greater than 0");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            String category = CATEGORIES[spinnerCategory.getSelectedItemPosition()];
            String description = etNote.getText().toString().trim();

            new Thread(() -> {
                try {
                    String type = "income".equalsIgnoreCase(transactionType)
                        ? Transaction.TYPE_INCOME
                        : Transaction.TYPE_EXPENSE;
                    String date = selectedDate;

                    Transaction transaction = new Transaction(
                        UUID.randomUUID().toString(),
                        merchant,
                        amount,
                        date,
                        "MANUAL",
                        1.0f,
                        category,
                        type,
                        description,
                        "UNKNOWN",
                        "UNKNOWN",
                        "UNKNOWN"
                    );

                    db.transactionDao().insert(transaction);

                    runOnUiThread(() -> {
                        Toast.makeText(AddTransactionActivity.this, 
                            "Transaction saved", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvError.setText("Error saving transaction: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            }).start();
        });
    }

    private void openDatePicker(EditText etDate) {
        Calendar calendar = Calendar.getInstance();
        String currentDate = etDate.getText().toString().trim();
        if (isCanonicalDate(currentDate)) {
            try {
                calendar.setTime(canonicalDateFormat.parse(currentDate));
            } catch (ParseException ignored) {
            }
        }

        DatePickerDialog dialog = new DatePickerDialog(
                this,
                (view, year, month, dayOfMonth) -> etDate.setText(
                        String.format(Locale.getDefault(), "%04d-%02d-%02d", year, month + 1, dayOfMonth)
                ),
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
        );
        dialog.show();
    }

    private boolean isCanonicalDate(String date) {
        if (date == null || date.trim().isEmpty()) {
            return false;
        }
        try {
            canonicalDateFormat.parse(date.trim());
            return true;
        } catch (ParseException e) {
            return false;
        }
    }
}
