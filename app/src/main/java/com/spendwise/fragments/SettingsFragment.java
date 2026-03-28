package com.spendwise.fragments;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;
import com.spendwise.dialogs.MerchantAliasesActivity;
import com.spendwise.utils.BudgetPrefs;

import java.util.Locale;

public class SettingsFragment extends Fragment {

    private AppDatabase db;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = SpendWiseApp.getDatabase();

        TextView tvProfile = view.findViewById(R.id.tv_profile_summary);
        TextView tvNetWorth = view.findViewById(R.id.tv_stat_net_worth);
        TextView tvTotalIn = view.findViewById(R.id.tv_stat_total_in);
        TextView tvTotalOut = view.findViewById(R.id.tv_stat_total_out);
        TextView tvBudgetValue = view.findViewById(R.id.tv_budget_value);

        refreshBudgetValue(tvBudgetValue);

        boolean openBudgetDialog = getArguments() != null && getArguments().getBoolean("open_budget_dialog", false);
        if (openBudgetDialog) {
            getArguments().putBoolean("open_budget_dialog", false);
            view.post(() -> showBudgetDialog(tvBudgetValue));
        }

        db.transactionDao().getAll().observe(getViewLifecycleOwner(), transactions -> {
            int accountCount = db.instrumentDao().getAll().size();
            tvProfile.setText(accountCount + " accounts · " + transactions.size() + " transactions");

            double totalIn = 0, totalOut = 0, netWorth;
            for (Transaction tx : transactions) {
                if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                    totalIn += tx.getAmount();
                } else {
                    totalOut += tx.getAmount();
                }
            }
            netWorth = totalIn - totalOut;

            tvNetWorth.setText(fmt(netWorth));
            tvTotalIn.setText(fmt(totalIn));
            tvTotalOut.setText(fmt(totalOut));
        });

        view.findViewById(R.id.row_aliases).setOnClickListener(v ->
            startActivity(new Intent(requireContext(), MerchantAliasesActivity.class)));

        view.findViewById(R.id.row_budget).setOnClickListener(v ->
            showBudgetDialog(tvBudgetValue));

        view.findViewById(R.id.row_sms).setOnClickListener(v -> 
            showSmsInfo());
    }

    private void showBudgetDialog(TextView tvBudgetValue) {
        EditText input = new EditText(requireContext());
        input.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        input.setHint("Enter daily budget (e.g. 1500)");

        double current = BudgetPrefs.getDailyBudget(requireContext());
        if (current > 0) {
            input.setText(String.format(Locale.getDefault(), "%.0f", current));
        }

        new AlertDialog.Builder(requireContext())
            .setTitle("Set Daily Budget")
            .setView(input)
            .setNegativeButton("Cancel", null)
            .setPositiveButton("Save", (dialog, which) -> {
                String raw = input.getText().toString().trim();
                if (raw.isEmpty()) {
                    Toast.makeText(requireContext(), "Please enter a value", Toast.LENGTH_SHORT).show();
                    return;
                }
                try {
                    double budget = Double.parseDouble(raw);
                    if (budget <= 0) {
                        Toast.makeText(requireContext(), "Budget must be greater than 0", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    BudgetPrefs.setDailyBudget(requireContext(), budget);
                    refreshBudgetValue(tvBudgetValue);
                    Toast.makeText(requireContext(), "Daily budget saved", Toast.LENGTH_SHORT).show();
                } catch (NumberFormatException e) {
                    Toast.makeText(requireContext(), "Invalid budget value", Toast.LENGTH_SHORT).show();
                }
            })
            .show();
    }

    private void refreshBudgetValue(TextView tvBudgetValue) {
        double budget = BudgetPrefs.getDailyBudget(requireContext());
        if (budget > 0) {
            tvBudgetValue.setText(String.format(Locale.getDefault(), "₹%,.0f per day", budget));
        } else {
            tvBudgetValue.setText("Not set");
        }
    }

    private void showAliasesInfo() {
        new AlertDialog.Builder(requireContext())
            .setTitle("SMS Auto-Detection")
            .setMessage("SpendWise automatically detects bank transactions from your SMS messages using on-device ML. The TFLite NER model runs locally on your phone - your messages never leave your device.")
            .setPositiveButton("OK", null)
            .show();
    }

    private void showSmsInfo() {
        new AlertDialog.Builder(requireContext())
            .setTitle("Privacy & Security")
            .setMessage("• SMS reading requires READ_SMS permission\n• All processing is done on-device\n• No data is sent to servers\n• Uses TensorFlow Lite for entity recognition\n• Your financial data stays private")
            .setPositiveButton("OK", null)
            .show();
    }

    private String fmt(double v) {
        return String.format(Locale.getDefault(), "₹%,.0f", v);
    }
}
