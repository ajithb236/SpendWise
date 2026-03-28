package com.spendwise.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.adapters.TransactionAdapter;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;
import com.spendwise.dialogs.AddTransactionActivity;
import com.spendwise.dialogs.MerchantAliasesActivity;
import com.spendwise.dialogs.TransactionDetailActivity;

import java.util.ArrayList;
import java.util.List;

public class TransactionsFragment extends Fragment {

    private AppDatabase db;
    private TransactionAdapter adapter;
    private List<Transaction> allTransactions = new ArrayList<>();
    private List<Transaction> filtered = new ArrayList<>();
    private String currentFilter = "All";
    private String searchQuery = "";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_transactions, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        db = AppDatabase.getInstance(getContext());

        RecyclerView rv = view.findViewById(R.id.rv_transactions);
        EditText etSearch = view.findViewById(R.id.et_search);
        View btnAdd = view.findViewById(R.id.btn_add_transaction);
        View btnAliases = view.findViewById(R.id.btn_manage_aliases);
        TextView chipAll = view.findViewById(R.id.chip_all);
        TextView chipIncome = view.findViewById(R.id.chip_income);
        TextView chipExpenses = view.findViewById(R.id.chip_expenses);

        rv.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new TransactionAdapter(filtered, tx ->
            startActivity(new Intent(getContext(), TransactionDetailActivity.class)
                .putExtra("txId", tx.getId())));
        rv.setAdapter(adapter);

        db.transactionDao().getAll().observe(getViewLifecycleOwner(), transactions -> {
            allTransactions = transactions;
            updateFilter();
            adapter.updateTransactions(filtered);
        });

        btnAdd.setOnClickListener(v ->
            startActivity(new Intent(getContext(), AddTransactionActivity.class)));

        btnAliases.setOnClickListener(v ->
            startActivity(new Intent(getContext(), MerchantAliasesActivity.class)));

        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                searchQuery = s.toString();
                updateFilter();
                adapter.updateTransactions(filtered);
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        View[] chips = {chipAll, chipIncome, chipExpenses};
        String[] labels = {"All", "Income", "Expenses"};
        for (int i = 0; i < chips.length; i++) {
            final String label = labels[i];
            chips[i].setOnClickListener(v -> {
                currentFilter = label;
                updateChipStyles(chipAll, chipIncome, chipExpenses);
                updateFilter();
                adapter.updateTransactions(filtered);
            });
        }
        
        updateChipStyles(chipAll, chipIncome, chipExpenses);
    }

    private void updateFilter() {
        filtered.clear();
        for (Transaction tx : allTransactions) {
            boolean matchesType = currentFilter.equals("All")
                || (currentFilter.equals("Income") && Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType()))
                || (currentFilter.equals("Expenses") && Transaction.TYPE_EXPENSE.equalsIgnoreCase(tx.getType()));
            
            boolean matchesSearch = searchQuery.isEmpty()
                || (tx.getMerchantName() != null && tx.getMerchantName().toLowerCase().contains(searchQuery.toLowerCase()))
                || (tx.getCategory() != null && tx.getCategory().toLowerCase().contains(searchQuery.toLowerCase()));
            
            if (matchesType && matchesSearch) {
                filtered.add(tx);
            }
        }
    }

    private void updateChipStyles(TextView all, TextView income, TextView expenses) {
        if (getContext() == null) return;
        all.setBackground(currentFilter.equals("All")
            ? requireContext().getDrawable(R.drawable.chip_background_active)
            : requireContext().getDrawable(R.drawable.chip_background_inactive));
        all.setTextColor(currentFilter.equals("All") ? 0xFFFFFFFF : 0xFF8B9BBF);
        
        income.setBackground(currentFilter.equals("Income")
             ? requireContext().getDrawable(R.drawable.chip_background_active)
             : requireContext().getDrawable(R.drawable.chip_background_inactive));
        income.setTextColor(currentFilter.equals("Income") ? 0xFFFFFFFF : 0xFF8B9BBF);
        
        expenses.setBackground(currentFilter.equals("Expenses")
             ? requireContext().getDrawable(R.drawable.chip_background_active)
             : requireContext().getDrawable(R.drawable.chip_background_inactive));
        expenses.setTextColor(currentFilter.equals("Expenses") ? 0xFFFFFFFF : 0xFF8B9BBF);
    }
}
