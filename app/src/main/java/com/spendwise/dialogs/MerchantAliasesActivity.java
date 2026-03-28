package com.spendwise.dialogs;

import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.data.Alias;
import com.spendwise.db.AppDatabase;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class MerchantAliasesActivity extends AppCompatActivity {

    private static final String OPTION_CREATE_NEW_ALIAS = "Add new alias";
    private static final String OPTION_CUSTOM_CATEGORY = "Add new category...";
    private static final String[] PREDEFINED_CATEGORIES = {
            "FOOD", "PHARMA", "BILLS", "TRANSPORT", "SHOPPING", "UTILITIES",
            "ENTERTAINMENT", "HEALTHCARE", "SALARY", "OTHER"
    };

    private AppDatabase db;
    private RecyclerView rvAliases;
    private TextView tvEmpty;
    private AliasAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_merchant_aliases);

        db = SpendWiseApp.getDatabase();
        rvAliases = findViewById(R.id.rv_aliases);
        tvEmpty = findViewById(R.id.tv_empty);

        rvAliases.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AliasAdapter();
        rvAliases.setAdapter(adapter);

        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
        findViewById(R.id.btn_add_alias).setOnClickListener(v -> showAliasDialog(null));

        loadAliases();
    }

    private void loadAliases() {
        new Thread(() -> {
            List<Alias> aliases = db.aliasDao().getAll();
            runOnUiThread(() -> {
                adapter.setItems(aliases);
                tvEmpty.setVisibility(aliases.isEmpty() ? View.VISIBLE : View.GONE);
            });
        }).start();
    }

    private void showAliasDialog(Alias existing) {
        View dialogView = LayoutInflater.from(this).inflate(R.layout.item_alias_dialog, null, false);
        EditText etOriginal = dialogView.findViewById(R.id.et_original_name);
        EditText etAlias = dialogView.findViewById(R.id.et_alias_name);
        Spinner spinnerExistingAlias = dialogView.findViewById(R.id.spinner_existing_alias);
        Spinner spinnerCategory = dialogView.findViewById(R.id.spinner_alias_category);
        EditText etCustomCategory = dialogView.findViewById(R.id.et_custom_alias_category);

        List<AliasChoice> aliasChoices = buildAliasChoices();
        setupExistingAliasSpinner(spinnerExistingAlias, aliasChoices);
        setupCategorySpinner(spinnerCategory);

        spinnerExistingAlias.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view, int position, long id) {
                if (position == 0) {
                    etAlias.setEnabled(true);
                    etAlias.setInputType(InputType.TYPE_CLASS_TEXT);
                    return;
                }

                AliasChoice choice = aliasChoices.get(position);
                etAlias.setText(choice.aliasName);
                etAlias.setEnabled(false);
                etAlias.setInputType(InputType.TYPE_NULL);
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

        if (existing != null) {
            etOriginal.setText(existing.getOriginalName());
            etAlias.setText(existing.getAliasName());
            etOriginal.setEnabled(false);
            etOriginal.setInputType(InputType.TYPE_NULL);
            setCategorySelection(spinnerCategory, etCustomCategory, existing.getCategory());

            int existingIndex = findAliasChoiceIndex(aliasChoices, existing.getAliasName());
            spinnerExistingAlias.setSelection(existingIndex > 0 ? existingIndex : 0);
        } else {
            setCategorySelection(spinnerCategory, etCustomCategory, "OTHER");
            spinnerExistingAlias.setSelection(0);
        }

        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add Merchant Alias" : "Edit Merchant Alias")
                .setView(dialogView)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", (dialog, which) -> {
                    String original = etOriginal.getText().toString().trim();
                    int selectedAliasPosition = spinnerExistingAlias.getSelectedItemPosition();
                    String aliasName;
                    if (selectedAliasPosition > 0) {
                        aliasName = aliasChoices.get(selectedAliasPosition).aliasName;
                    } else {
                        aliasName = etAlias.getText().toString().trim();
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

                    if (original.isEmpty() || aliasName.isEmpty()) {
                        Toast.makeText(this, "Both fields are required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    if (category.isEmpty()) {
                        Toast.makeText(this, "Category is required", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    saveAlias(existing, original, aliasName, category);
                })
                .show();
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

    private void saveAlias(Alias existing, String original, String aliasName, String category) {
        new Thread(() -> {
            String normalizedCategory = category == null || category.trim().isEmpty() ? "OTHER" : category.trim();
            if (existing == null) {
                Alias current = db.aliasDao().getByOriginalName(original);
                if (current == null) {
                    db.aliasDao().insert(new Alias(original, aliasName, normalizedCategory));
                } else {
                    db.aliasDao().updateAlias(current.getId(), aliasName, normalizedCategory);
                }
            } else {
                db.aliasDao().updateAlias(existing.getId(), aliasName, normalizedCategory);
            }

            db.transactionDao().updateCategoryForMerchantName(original, normalizedCategory);
            db.transactionDao().updateCategoryForMerchantName(aliasName, normalizedCategory);
            if (existing != null && existing.getAliasName() != null) {
                db.transactionDao().updateCategoryForMerchantName(existing.getAliasName(), normalizedCategory);
            }

            runOnUiThread(() -> {
                Toast.makeText(this, "Alias saved", Toast.LENGTH_SHORT).show();
                loadAliases();
            });
        }).start();
    }

    private void deleteAlias(Alias alias) {
        new AlertDialog.Builder(this)
                .setTitle("Delete alias?")
                .setMessage(String.format(Locale.getDefault(), "Remove alias for %s?", alias.getOriginalName()))
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Delete", (dialog, which) -> {
                    new Thread(() -> {
                        db.aliasDao().deleteById(alias.getId());
                        runOnUiThread(() -> {
                            Toast.makeText(this, "Alias deleted", Toast.LENGTH_SHORT).show();
                            loadAliases();
                        });
                    }).start();
                })
                .show();
    }

    private class AliasAdapter extends RecyclerView.Adapter<AliasAdapter.ViewHolder> {

        private final List<Alias> items = new ArrayList<>();

        void setItems(List<Alias> aliases) {
            items.clear();
            items.addAll(aliases);
            notifyDataSetChanged();
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(parent.getContext()).inflate(R.layout.item_alias, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            Alias alias = items.get(position);
            holder.tvOriginal.setText(alias.getOriginalName());
            holder.tvAlias.setText(alias.getAliasName());
                String category = alias.getCategory() == null || alias.getCategory().trim().isEmpty()
                    ? "OTHER"
                    : alias.getCategory().trim();
                holder.tvCategory.setText("Category: " + category);
            holder.btnEdit.setOnClickListener(v -> showAliasDialog(alias));
            holder.btnDelete.setOnClickListener(v -> deleteAlias(alias));
        }

        @Override
        public int getItemCount() {
            return items.size();
        }

        class ViewHolder extends RecyclerView.ViewHolder {
            TextView tvOriginal;
            TextView tvAlias;
            TextView tvCategory;
            TextView btnEdit;
            TextView btnDelete;

            ViewHolder(@NonNull View itemView) {
                super(itemView);
                tvOriginal = itemView.findViewById(R.id.tv_original);
                tvAlias = itemView.findViewById(R.id.tv_alias);
                tvCategory = itemView.findViewById(R.id.tv_alias_category);
                btnEdit = itemView.findViewById(R.id.btn_edit);
                btnDelete = itemView.findViewById(R.id.btn_delete);
            }
        }
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
