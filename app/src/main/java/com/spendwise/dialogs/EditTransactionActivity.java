package com.spendwise.dialogs;

import android.app.DatePickerDialog;
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
import java.util.Locale;

public class EditTransactionActivity extends AppCompatActivity {

    private static final String[] TYPES = { Transaction.TYPE_EXPENSE, Transaction.TYPE_INCOME };
    private static final String[] CATEGORIES = {
            "FOOD", "TRANSPORT", "SHOPPING", "UTILITIES", "ENTERTAINMENT",
            "HEALTHCARE", "SALARY", "OTHER"
    };
    private final SimpleDateFormat canonicalDateFormat = new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault());

    private AppDatabase db;
    private Transaction tx;

    private Spinner spinnerType;
    private Spinner spinnerCategory;
    private EditText etMerchant;
    private EditText etAmount;
    private EditText etDate;
    private EditText etPaymentMethod;
    private EditText etConfidence;
    private EditText etRawSms;
    private EditText etInstrumentType;
    private EditText etInstrumentId;
    private EditText etInstrumentRefId;
    private TextView tvError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_edit_transaction);
        db = SpendWiseApp.getDatabase();

        String txId = getIntent().getStringExtra("txId");
        if (txId == null || txId.trim().isEmpty()) {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        tx = db.transactionDao().getById(txId);
        if (tx == null) {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindViews();
        bindData();

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_save).setOnClickListener(v -> saveChanges());
    }

    private void bindViews() {
        spinnerType = findViewById(R.id.spinner_type);
        spinnerCategory = findViewById(R.id.spinner_category);
        etMerchant = findViewById(R.id.et_merchant);
        etAmount = findViewById(R.id.et_amount);
        etDate = findViewById(R.id.et_date);
        etPaymentMethod = findViewById(R.id.et_payment_method);
        etConfidence = findViewById(R.id.et_confidence);
        etRawSms = findViewById(R.id.et_raw_sms);
        etInstrumentType = findViewById(R.id.et_instrument_type);
        etInstrumentId = findViewById(R.id.et_instrument_id);
        etInstrumentRefId = findViewById(R.id.et_instrument_ref_id);
        tvError = findViewById(R.id.tv_error);

        ArrayAdapter<String> typeAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_dropdown_item, TYPES);
        spinnerType.setAdapter(typeAdapter);

        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this,
            android.R.layout.simple_spinner_dropdown_item, CATEGORIES);
        spinnerCategory.setAdapter(categoryAdapter);

        canonicalDateFormat.setLenient(false);
        etDate.setFocusable(false);
        etDate.setClickable(true);
        etDate.setOnClickListener(v -> openDatePicker());
    }

    private void bindData() {
        int typePos = Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType()) ? 1 : 0;
        spinnerType.setSelection(typePos);
        etMerchant.setText(safe(tx.getMerchantName()));
        etAmount.setText(String.format(Locale.getDefault(), "%.2f", tx.getAmount()));
        etDate.setText(safe(tx.getDate()));
        spinnerCategory.setSelection(getCategoryIndex(tx.getCategory()));
        etPaymentMethod.setText(safe(tx.getPaymentMethod()));
        etConfidence.setText(String.format(Locale.getDefault(), "%.2f", tx.getConfidenceScore()));
        etRawSms.setText(safe(tx.getRawSms()));
        etInstrumentType.setText(safe(tx.getInstrumentType()));
        etInstrumentId.setText(safe(tx.getInstrumentId()));
        etInstrumentRefId.setText(safe(tx.getInstrumentRefId()));
    }

    private void saveChanges() {
        tvError.setVisibility(View.GONE);

        String merchant = etMerchant.getText().toString().trim();
        String amountText = etAmount.getText().toString().trim();
        String date = etDate.getText().toString().trim();
        String category = CATEGORIES[spinnerCategory.getSelectedItemPosition()];
        String paymentMethod = etPaymentMethod.getText().toString().trim();
        String confidenceText = etConfidence.getText().toString().trim();
        String rawSms = etRawSms.getText().toString().trim();
        String instrumentType = etInstrumentType.getText().toString().trim();
        String instrumentId = etInstrumentId.getText().toString().trim();
        String instrumentRefId = etInstrumentRefId.getText().toString().trim();
        String type = TYPES[spinnerType.getSelectedItemPosition()];

        if (merchant.isEmpty()) {
            showError("Merchant is required");
            return;
        }
        if (amountText.isEmpty()) {
            showError("Amount is required");
            return;
        }
        if (date.isEmpty()) {
            showError("Date is required");
            return;
        }
        if (!isCanonicalDate(date)) {
            showError("Date must be in yyyy-MM-dd format");
            return;
        }

        double amount;
        float confidence;
        try {
            amount = Double.parseDouble(amountText);
        } catch (NumberFormatException e) {
            showError("Invalid amount");
            return;
        }

        if (amount <= 0) {
            showError("Amount must be greater than 0");
            return;
        }

        if (confidenceText.isEmpty()) {
            confidence = 0f;
        } else {
            try {
                confidence = Float.parseFloat(confidenceText);
            } catch (NumberFormatException e) {
                showError("Invalid confidence score");
                return;
            }
        }

        Transaction updated = new Transaction(
                tx.getId(),
                merchant,
                amount,
                date,
                paymentMethod.isEmpty() ? "UNKNOWN" : paymentMethod,
                confidence,
                category,
                type,
                rawSms,
                instrumentType.isEmpty() ? "UNKNOWN" : instrumentType,
                instrumentId.isEmpty() ? "UNKNOWN" : instrumentId,
                instrumentRefId.isEmpty() ? "UNKNOWN" : instrumentRefId
        );

        new Thread(() -> {
            try {
                db.transactionDao().update(updated);
                runOnUiThread(() -> {
                    Toast.makeText(this, "Transaction updated", Toast.LENGTH_SHORT).show();
                    setResult(RESULT_OK);
                    finish();
                });
            } catch (Exception e) {
                runOnUiThread(() -> showError("Failed to update transaction"));
            }
        }).start();
    }

    private void showError(String error) {
        tvError.setText(error);
        tvError.setVisibility(View.VISIBLE);
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private int getCategoryIndex(String category) {
        String normalized = category == null ? "" : category.trim();
        for (int i = 0; i < CATEGORIES.length; i++) {
            if (CATEGORIES[i].equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return CATEGORIES.length - 1;
    }

    private void openDatePicker() {
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
