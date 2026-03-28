package com.spendwise.dialogs;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.widget.ImageView;
import android.widget.EditText;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import android.view.LayoutInflater;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;

import com.spendwise.R;
import com.spendwise.db.AppDatabase;
import com.spendwise.data.Alias;
import com.spendwise.data.Transaction;
import com.spendwise.utils.DateDisplayUtil;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TransactionDetailActivity extends AppCompatActivity {

    private static final String OPTION_CREATE_NEW_ALIAS = "Add new alias";
    private static final String OPTION_CUSTOM_CATEGORY = "Add new category...";
    private static final String[] PREDEFINED_CATEGORIES = {
            "FOOD", "PHARMA", "BILLS", "TRANSPORT", "SHOPPING", "UTILITIES",
            "ENTERTAINMENT", "HEALTHCARE", "SALARY", "OTHER"
    };

    private AppDatabase db;
    private Transaction tx;
    private String txId;

    private TextView tvAmount;
    private TextView tvDetailMerchant;
    private TextView tvDetailDate;
    private TextView tvDetailAccount;
    private TextView tvDetailNote;
    private TextView tvTxType;
    private EditText etAlias;
    private TextView btnEditAlias;
    private ImageView btnClose;
    private ImageView btnDelete;
    private ImageView btnEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_transaction_detail);

        db = AppDatabase.getInstance(this);
        
        txId = getIntent().getStringExtra("txId");
        if (txId == null) {
            finish();
            return;
        }

        bindViews();

        tx = db.transactionDao().getById(txId);
        if (tx == null) {
            Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }

        bindActions();
        renderTransaction();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (txId != null) {
            tx = db.transactionDao().getById(txId);
            if (tx == null) {
                Toast.makeText(this, "Transaction not found", Toast.LENGTH_SHORT).show();
                finish();
                return;
            }
            renderTransaction();
        }
    }

    private void bindViews() {
        tvAmount = findViewById(R.id.tv_tx_amount);
        tvDetailMerchant = findViewById(R.id.tv_detail_merchant);
        tvDetailDate = findViewById(R.id.tv_detail_date);
        tvDetailAccount = findViewById(R.id.tv_detail_account);
        tvDetailNote = findViewById(R.id.tv_detail_note);
        tvTxType = findViewById(R.id.tv_tx_type);
        etAlias = findViewById(R.id.et_alias);
        btnEditAlias = findViewById(R.id.btn_edit_alias);
        btnClose = findViewById(R.id.btn_close);
        btnDelete = findViewById(R.id.btn_delete);
        btnEdit = findViewById(R.id.btn_edit);
    }

    private void bindActions() {
        btnClose.setOnClickListener(v -> finish());

        btnDelete.setOnClickListener(v -> {
            db.transactionDao().delete(tx);
            finish();
        });

        btnEdit.setOnClickListener(v -> {
            Intent intent = new Intent(this, EditTransactionActivity.class);
            intent.putExtra("txId", tx.getId());
            startActivity(intent);
        });
    }

    private void renderTransaction() {
        if (tx == null) {
            return;
        }

        tvAmount.setText(String.format(Locale.getDefault(), "₹%.2f", tx.getAmount()));
        tvDetailMerchant.setText(tx.getMerchantName());
        tvDetailDate.setText(DateDisplayUtil.toIndianDisplayDate(tx.getDate()));
        
        String typeCategory = (tx.getType() != null ? tx.getType() : "") + " " +
                             (tx.getCategory() != null ? "· " + tx.getCategory() : "");
        tvTxType.setText(typeCategory);

        if (tx.getInstrumentType() != null) {
            tvDetailAccount.setText(tx.getInstrumentType() + " " + tx.getInstrumentId());
        } else {
             tvDetailAccount.setText("Unknown Instrument");
        }
        
        if (tvDetailNote != null) {
             tvDetailNote.setText(tx.getRawSms() != null ? tx.getRawSms() : "");
        }

        bindAliasUI(tvDetailMerchant, etAlias, btnEditAlias);
    }

    private void bindAliasUI(TextView tvDetailMerchant, EditText etAlias, TextView btnEditAlias) {
        if (etAlias == null || btnEditAlias == null || tx == null) {
            return;
        }

        String originalMerchant = tx.getMerchantName() != null ? tx.getMerchantName().trim() : "";
        if (originalMerchant.isEmpty()) {
            etAlias.setText("");
            btnEditAlias.setEnabled(false);
            return;
        }

        Alias existing = db.aliasDao().getByOriginalName(originalMerchant);
        String currentAlias = existing != null ? existing.getAliasName() : "";
        String currentCategory = existing != null && existing.getCategory() != null && !existing.getCategory().trim().isEmpty()
                ? existing.getCategory().trim()
                : "OTHER";
        etAlias.setText(currentAlias.isEmpty() ? "Not set" : currentAlias);

        btnEditAlias.setOnClickListener(v -> {
            View dialogView = LayoutInflater.from(this).inflate(R.layout.item_alias_dialog, null, false);
            EditText etOriginal = dialogView.findViewById(R.id.et_original_name);
            EditText inputAlias = dialogView.findViewById(R.id.et_alias_name);
            Spinner spinnerExistingAlias = dialogView.findViewById(R.id.spinner_existing_alias);
            Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_alias_category);
            EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_alias_category);

            etOriginal.setText(originalMerchant);
            etOriginal.setEnabled(false);

            List<AliasChoice> aliasChoices = buildAliasChoices();
            setupExistingAliasSpinner(spinnerExistingAlias, aliasChoices);
            setupCategorySpinner(spinnerCategory);

            inputAlias.setText(currentAlias);
            setCategorySelection(spinnerCategory, etCustomCategory, currentCategory);

            int existingIndex = findAliasChoiceIndex(aliasChoices, currentAlias);
            spinnerExistingAlias.setSelection(existingIndex > 0 ? existingIndex : 0);

            spinnerExistingAlias.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    if (position == 0) {
                        inputAlias.setEnabled(true);
                        return;
                    }

                    AliasChoice choice = aliasChoices.get(position);
                    inputAlias.setText(choice.aliasName);
                    inputAlias.setEnabled(false);
                    setCategorySelection(spinnerCategory, etCustomCategory, choice.category);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });

            spinnerCategory.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
                @Override
                public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                    String selected = (String) spinnerCategory.getSelectedItem();
                    etCustomCategory.setVisibility(OPTION_CUSTOM_CATEGORY.equals(selected) ? View.VISIBLE : View.GONE);
                }

                @Override
                public void onNothingSelected(android.widget.AdapterView<?> parent) {
                }
            });

            new AlertDialog.Builder(this)
                    .setTitle("Set alias for " + originalMerchant)
                    .setView(dialogView)
                    .setNegativeButton("Cancel", null)
                    .setPositiveButton("Save", (dialog, which) -> {
                        int selectedAliasPosition = spinnerExistingAlias.getSelectedItemPosition();
                        String aliasName;
                        if (selectedAliasPosition > 0) {
                            aliasName = aliasChoices.get(selectedAliasPosition).aliasName;
                        } else {
                            aliasName = inputAlias.getText().toString().trim();
                        }

                        String category = resolveCategory(spinnerCategory, etCustomCategory);

                        if (selectedAliasPosition == 0) {
                            AliasChoice duplicateAlias = findAliasChoiceByName(aliasChoices, aliasName);
                            if (duplicateAlias != null) {
                                aliasName = duplicateAlias.aliasName;
                                category = duplicateAlias.category;
                                Toast.makeText(this, "Alias already exists. Linked to existing alias.", Toast.LENGTH_SHORT).show();
                            }
                        }

                        if (aliasName.isEmpty()) {
                            Toast.makeText(this, "Alias cannot be empty", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        if (category.isEmpty()) {
                            Toast.makeText(this, "Category cannot be empty", Toast.LENGTH_SHORT).show();
                            return;
                        }

                        Alias latest = db.aliasDao().getByOriginalName(originalMerchant);
                        if (latest == null) {
                            db.aliasDao().insert(new Alias(originalMerchant, aliasName, category));
                        } else {
                            db.aliasDao().updateAlias(latest.getId(), aliasName, category);
                        }

                        db.transactionDao().updateCategoryForMerchantName(originalMerchant, category);
                        db.transactionDao().updateCategoryForMerchantName(aliasName, category);

                        etAlias.setText(aliasName);
                        tvDetailMerchant.setText(aliasName);
                        Toast.makeText(this, "Alias saved", Toast.LENGTH_SHORT).show();
                    })
                    .show();
        });
    }

    private void setupExistingAliasSpinner(Spinner spinner, List<AliasChoice> choices) {
        List<String> labels = new ArrayList<>();
        labels.add(OPTION_CREATE_NEW_ALIAS);
        for (int i = 1; i < choices.size(); i++) {
            AliasChoice choice = choices.get(i);
            labels.add(choice.aliasName + " (" + choice.category + ")");
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, labels);
        spinner.setAdapter(adapter);
    }

    private void setupCategorySpinner(Spinner spinner) {
        List<String> options = new ArrayList<>();
        for (String c : PREDEFINED_CATEGORIES) {
            options.add(c);
        }
        options.add(OPTION_CUSTOM_CATEGORY);
        ArrayAdapter<String> categoryAdapter = new ArrayAdapter<>(this, android.R.layout.simple_spinner_dropdown_item, options);
        spinner.setAdapter(categoryAdapter);
    }

    private List<AliasChoice> buildAliasChoices() {
        List<AliasChoice> choices = new ArrayList<>();
        choices.add(new AliasChoice(OPTION_CREATE_NEW_ALIAS, "OTHER"));

        List<Alias> aliases = db.aliasDao().getAll();
        Map<String, String> byAliasName = new LinkedHashMap<>();
        for (Alias alias : aliases) {
            if (alias == null || alias.getAliasName() == null) {
                continue;
            }
            String aliasName = alias.getAliasName().trim();
            if (aliasName.isEmpty()) {
                continue;
            }
            String category = alias.getCategory() == null || alias.getCategory().trim().isEmpty()
                    ? "OTHER"
                    : alias.getCategory().trim();
            if (!byAliasName.containsKey(aliasName)) {
                byAliasName.put(aliasName, category);
            }
        }

        for (Map.Entry<String, String> entry : byAliasName.entrySet()) {
            choices.add(new AliasChoice(entry.getKey(), entry.getValue()));
        }
        return choices;
    }

    private int findAliasChoiceIndex(List<AliasChoice> choices, String aliasName) {
        if (aliasName == null) {
            return -1;
        }
        String normalized = aliasName.trim();
        for (int i = 1; i < choices.size(); i++) {
            if (choices.get(i).aliasName.equalsIgnoreCase(normalized)) {
                return i;
            }
        }
        return -1;
    }

    private AliasChoice findAliasChoiceByName(List<AliasChoice> choices, String aliasName) {
        if (aliasName == null) {
            return null;
        }
        String normalized = aliasName.trim();
        if (normalized.isEmpty()) {
            return null;
        }

        for (int i = 1; i < choices.size(); i++) {
            AliasChoice choice = choices.get(i);
            if (choice.aliasName.equalsIgnoreCase(normalized)) {
                return choice;
            }
        }
        return null;
    }

    private void setCategorySelection(Spinner spinnerCategory, EditText etCustomCategory, String category) {
        String normalized = category == null || category.trim().isEmpty() ? "OTHER" : category.trim();
        ArrayAdapter<?> adapter = (ArrayAdapter<?>) spinnerCategory.getAdapter();
        int found = -1;
        for (int i = 0; i < adapter.getCount(); i++) {
            String item = String.valueOf(adapter.getItem(i));
            if (item.equalsIgnoreCase(normalized)) {
                found = i;
                break;
            }
        }

        if (found >= 0) {
            spinnerCategory.setSelection(found);
            etCustomCategory.setVisibility(View.GONE);
            etCustomCategory.setText("");
        } else {
            spinnerCategory.setSelection(adapter.getCount() - 1);
            etCustomCategory.setVisibility(View.VISIBLE);
            etCustomCategory.setText(normalized);
        }
    }

    private String resolveCategory(Spinner spinnerCategory, EditText etCustomCategory) {
        String selected = String.valueOf(spinnerCategory.getSelectedItem());
        if (OPTION_CUSTOM_CATEGORY.equals(selected)) {
            return etCustomCategory.getText().toString().trim();
        }
        return selected.trim();
    }

    private static class AliasChoice {
        final String aliasName;
        final String category;

        AliasChoice(String aliasName, String category) {
            this.aliasName = aliasName;
            this.category = category;
        }
    }
}
