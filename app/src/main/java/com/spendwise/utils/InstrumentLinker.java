package com.spendwise.utils;

import com.spendwise.db.AppDatabase;
import com.spendwise.data.Instrument;
import com.spendwise.data.InstrumentAlias;

import java.util.Locale;
import java.util.UUID;

public class InstrumentLinker {

    private InstrumentLinker() {
    }

    public static String resolveOrCreateInstrument(AppDatabase db, String instrumentType, String instrumentId, String bankHint) {
        if (db == null || instrumentType == null || instrumentId == null
                || "UNKNOWN".equalsIgnoreCase(instrumentType)
                || "UNKNOWN".equalsIgnoreCase(instrumentId)) {
            return "UNKNOWN";
        }

        String canonicalType = toCanonicalAccountType(instrumentType);

        String normalizedKey = buildAliasKey(instrumentType, instrumentId);

        InstrumentAlias alias = db.instrumentAliasDao().getByAliasKey(normalizedKey);
        if (alias != null && alias.getInstrumentRefId() != null) {
            return alias.getInstrumentRefId();
        }

        Instrument existing = db.instrumentDao().findByTypeAndMaskedId(canonicalType, instrumentId);
        if (existing != null) {
            db.instrumentAliasDao().insert(new InstrumentAlias(normalizedKey, existing.getId()));
            return existing.getId();
        }

        String newId = UUID.randomUUID().toString();
        String nickname = canonicalType + " " + instrumentId;
        Instrument created = new Instrument(
                newId,
                nickname,
                canonicalType,
                instrumentId,
                bankHint == null ? "Unknown" : bankHint,
                true,
                false,
                "CREDIT_CARD".equalsIgnoreCase(canonicalType) ? 1 : 0,
                0,
                "Auto-detected from SMS"
        );
        db.instrumentDao().insert(created);
        db.instrumentAliasDao().insert(new InstrumentAlias(normalizedKey, newId));
        db.instrumentAliasDao().insert(new InstrumentAlias(buildAliasKey(canonicalType, instrumentId), newId));
        return newId;
    }

    private static String toCanonicalAccountType(String detectedType) {
        String normalized = detectedType == null ? "" : detectedType.trim().toUpperCase(Locale.getDefault());
        if ("CREDIT_CARD".equals(normalized)) {
            return "CREDIT_CARD";
        }
        if ("BANK_ACCOUNT".equals(normalized) || "DEBIT_CARD".equals(normalized) || "CARD".equals(normalized)) {
            return "BANK_ACCOUNT";
        }
        return "BANK_ACCOUNT";
    }

    public static String buildAliasKey(String instrumentType, String instrumentId) {
        return (instrumentType + "::" + instrumentId).toUpperCase(Locale.getDefault()).trim();
    }
}
