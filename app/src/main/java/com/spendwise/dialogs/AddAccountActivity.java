package com.spendwise.dialogs;

import android.os.Bundle;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.data.Instrument;
import com.spendwise.db.AppDatabase;

import java.util.UUID;

public class AddAccountActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);

        EditText etName = findViewById(R.id.et_name);
        EditText etBank = findViewById(R.id.et_bank);
        EditText etBalance = findViewById(R.id.et_balance);
        EditText etAccountNumber = findViewById(R.id.et_account_number);
        TextView tvError = findViewById(R.id.tv_error);
        TextView btnSave = findViewById(R.id.btn_save);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String name = etName.getText().toString().trim();
            String bank = etBank.getText().toString().trim();
            String balStr = etBalance.getText().toString().trim();
            String accNum = etAccountNumber.getText().toString().trim();

            if (name.isEmpty()) {
                tvError.setText("Please enter account name");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            double parsedBalance = 0;
            if (!balStr.isEmpty()) {
                try {
                    parsedBalance = Double.parseDouble(balStr);
                } catch (NumberFormatException e) {
                    tvError.setText("Invalid balance");
                    tvError.setVisibility(View.VISIBLE);
                    return;
                }
            }
            
            final double balance = parsedBalance;

            new Thread(() -> {
                try {
                    Instrument instrument = new Instrument(
                        UUID.randomUUID().toString(),
                        name,
                        "BANK_ACCOUNT",
                        accNum,
                        bank,
                        true,
                        true,
                        1,
                        1,
                        ""
                    );

                    AppDatabase db = SpendWiseApp.getDatabase();
                    db.instrumentDao().insert(instrument);

                    runOnUiThread(() -> {
                        Toast.makeText(AddAccountActivity.this, 
                            "Account added", Toast.LENGTH_SHORT).show();
                        setResult(RESULT_OK);
                        finish();
                    });
                } catch (Exception e) {
                    runOnUiThread(() -> {
                        tvError.setText("Error: " + e.getMessage());
                        tvError.setVisibility(View.VISIBLE);
                    });
                }
            }).start();
        });
    }
}
