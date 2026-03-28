package com.spendwise.dialogs;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.spendwise.R;
import com.spendwise.data.Instrument;
import com.spendwise.db.AppDatabase;
import com.spendwise.db.InstrumentDao;

public class EditInstrumentActivity extends AppCompatActivity {
    
    private Instrument instrument;
    private InstrumentDao instrumentDao;
    private EditText etName;
    private EditText etType;
    private EditText etBank;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_add_account);

        String instrumentId = getIntent().getStringExtra("instrument_id");
        
        etName = findViewById(R.id.et_name);
        etType = findViewById(R.id.et_account_number);
        etBank = findViewById(R.id.et_bank);
        
        AppDatabase db = AppDatabase.getInstance(this);
        instrumentDao = db.instrumentDao();
        
        if (instrumentId != null) {
            instrument = instrumentDao.getById(instrumentId);
            if (instrument != null) {
                etName.setText(instrument.getNickname());
                etType.setText(instrument.getInstrumentIdMasked());
                etBank.setText(instrument.getBankName());
            }
        } else {
             instrument = new Instrument(
                 java.util.UUID.randomUUID().toString(),
                 "",
                 "MANUAL_ACCOUNT",
                 "",
                 "",
                 true,
                 true,
                 1,
                 1,
                 "Manual Entry"
             );
        }
        
        findViewById(R.id.btn_save).setOnClickListener(v -> {
            instrument.setNickname(etName.getText().toString());
            instrument.setBankName(etBank.getText().toString());
            String typeOrId = etType.getText().toString();
            if (typeOrId.matches("\\d+")) {
                 instrument.setInstrumentIdMasked(typeOrId);
            } else {
                  instrument.setInstrumentType(typeOrId);
            }
            
            if (instrumentDao.getById(instrument.getId()) != null) {
                 instrumentDao.update(instrument);
            } else {
                 instrumentDao.insert(instrument);
            }
            Toast.makeText(this, "Saved", Toast.LENGTH_SHORT).show();
            finish();
        });
        
        findViewById(R.id.btn_close).setOnClickListener(v -> finish());
    }
}
