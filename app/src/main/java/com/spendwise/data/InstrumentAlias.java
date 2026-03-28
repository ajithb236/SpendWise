package com.spendwise.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

@Entity(tableName = "instrument_aliases")
public class InstrumentAlias {

    @PrimaryKey
    @NonNull
    private String aliasKey;
    private String instrumentRefId;

    public InstrumentAlias(@NonNull String aliasKey, String instrumentRefId) {
        this.aliasKey = aliasKey;
        this.instrumentRefId = instrumentRefId;
    }

    @NonNull
    public String getAliasKey() { return aliasKey; }
    public String getInstrumentRefId() { return instrumentRefId; }
}
