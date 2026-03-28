package com.spendwise.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.spendwise.R;
import com.spendwise.SpendWiseApp;
import com.spendwise.adapters.InstrumentAdapter;
import com.spendwise.data.Instrument;
import com.spendwise.data.Transaction;
import com.spendwise.db.AppDatabase;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.HashMap;
import java.util.Calendar;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AccountsFragment extends Fragment {

    private RecyclerView recyclerView;
    private InstrumentAdapter adapter;
    private TextView emptyView;
    private TextView tvNetWorth;
    private TextView tvCount;
        private final SimpleDateFormat[] supportedDateFormats = new SimpleDateFormat[] {
            new SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()),
            new SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()),
            new SimpleDateFormat("dd-MM-yyyy", Locale.getDefault()),
            new SimpleDateFormat("yyyy/MM/dd", Locale.getDefault()),
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()),
            new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
        };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_accounts, container, false);

        recyclerView = view.findViewById(R.id.rv_accounts);
        emptyView = view.findViewById(R.id.tv_no_accounts);
        tvNetWorth = view.findViewById(R.id.tv_net_worth);
        tvCount = view.findViewById(R.id.tv_account_count);
        View btnAdd = view.findViewById(R.id.btn_add_account);

        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        adapter = new InstrumentAdapter(getContext());
        recyclerView.setAdapter(adapter);

        btnAdd.setOnClickListener(v ->
                startActivity(new Intent(getContext(), com.spendwise.dialogs.EditInstrumentActivity.class)));

        loadInstruments();
        return view;
    }

    private void loadInstruments() {
        if (getContext() == null) {
            return;
        }

        AppDatabase db = SpendWiseApp.getDatabase();
        List<Instrument> instruments = db.instrumentDao().getAll();
        List<Transaction> transactions = db.transactionDao().getAllForLookup();

        Map<String, Double> netByInstrumentId = new HashMap<>();
        double totalOutflow = 0;
        double totalInflow = 0;
        long monthStart = startOfCurrentMonthMillis();
        long nextMonthStart = startOfNextMonthMillis();

        for (Transaction tx : transactions) {
            long txDate = parseDateMillis(tx.getDate());
            if (txDate < monthStart || txDate >= nextMonthStart) {
                continue;
            }

            String refId = tx.getInstrumentRefId();
            if (refId != null && !refId.trim().isEmpty() && !"UNKNOWN".equalsIgnoreCase(refId.trim())) {
                double signed = Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())
                        ? tx.getAmount()
                        : -tx.getAmount();
                netByInstrumentId.put(refId, netByInstrumentId.getOrDefault(refId, 0.0) + signed);
            }

            if (Transaction.TYPE_INCOME.equalsIgnoreCase(tx.getType())) {
                totalInflow += tx.getAmount();
            } else {
                totalOutflow += tx.getAmount();
            }
        }

        adapter.setNetByInstrumentId(netByInstrumentId);

        if (instruments.isEmpty()) {
            if (emptyView != null) emptyView.setVisibility(View.VISIBLE);
            if (recyclerView != null) recyclerView.setVisibility(View.GONE);
        } else {
            if (emptyView != null) emptyView.setVisibility(View.GONE);
            if (recyclerView != null) recyclerView.setVisibility(View.VISIBLE);
            adapter.setInstruments(instruments);
        }

        if (tvCount != null) {
            tvCount.setText(instruments.size() + " account" + (instruments.size() == 1 ? "" : "s"));
        }
        if (tvNetWorth != null) {
            double netFlow = totalInflow - totalOutflow;
            tvNetWorth.setText(String.format(Locale.getDefault(), "₹%,.2f", netFlow));
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        loadInstruments();
    }

    private long startOfCurrentMonthMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        return cal.getTimeInMillis();
    }

    private long startOfNextMonthMillis() {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.DAY_OF_MONTH, 1);
        cal.set(Calendar.HOUR_OF_DAY, 0);
        cal.set(Calendar.MINUTE, 0);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        cal.add(Calendar.MONTH, 1);
        return cal.getTimeInMillis();
    }

    private long parseDateMillis(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return 0;
        }
        String value = dateStr.trim();

        if (value.matches("\\d{10,13}")) {
            try {
                long epoch = Long.parseLong(value);
                if (value.length() == 10) {
                    epoch *= 1000L;
                }
                return epoch;
            } catch (NumberFormatException ignored) {
            }
        }

        for (SimpleDateFormat format : supportedDateFormats) {
            try {
                Date parsed = format.parse(value);
                if (parsed != null) {
                    return parsed.getTime();
                }
            } catch (ParseException ignored) {
            }
        }
        return 0;
    }
}
