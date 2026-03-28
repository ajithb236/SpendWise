package com.spendwise.data;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.PrimaryKey;

import java.io.Serializable;

@Entity(tableName = "instruments")
public class Instrument implements Serializable {

    @PrimaryKey
    @NonNull
    private String id;
    private String nickname;
    private String instrumentType;
    private String instrumentIdMasked;
    private String bankName;
    private boolean isActive;
    private boolean isComplete;
    private int cycleStartDay;
    private int paymentDueDay;
    private String notes;

    public Instrument(
            @NonNull String id,
            String nickname,
            String instrumentType,
            String instrumentIdMasked,
            String bankName,
            boolean isActive,
            boolean isComplete,
            int cycleStartDay,
            int paymentDueDay,
            String notes
    ) {
        this.id = id;
        this.nickname = nickname;
        this.instrumentType = instrumentType;
        this.instrumentIdMasked = instrumentIdMasked;
        this.bankName = bankName;
        this.isActive = isActive;
        this.isComplete = isComplete;
        this.cycleStartDay = cycleStartDay;
        this.paymentDueDay = paymentDueDay;
        this.notes = notes;
    }

    @NonNull
    public String getId() { return id; }
    public String getNickname() { return nickname; }
    public String getInstrumentType() { return instrumentType; }
    public String getInstrumentIdMasked() { return instrumentIdMasked; }
    public String getBankName() { return bankName; }
    public boolean isActive() { return isActive; }
    public boolean isComplete() { return isComplete; }
    public int getCycleStartDay() { return cycleStartDay; }
    public int getPaymentDueDay() { return paymentDueDay; }
    public String getNotes() { return notes; }

    public void setNickname(String nickname) { this.nickname = nickname; }
    public void setInstrumentType(String instrumentType) { this.instrumentType = instrumentType; }
    public void setInstrumentIdMasked(String instrumentIdMasked) { this.instrumentIdMasked = instrumentIdMasked; }
    public void setBankName(String bankName) { this.bankName = bankName; }
    public void setActive(boolean active) { isActive = active; }
    public void setComplete(boolean complete) { isComplete = complete; }
    public void setCycleStartDay(int cycleStartDay) { this.cycleStartDay = cycleStartDay; }
    public void setPaymentDueDay(int paymentDueDay) { this.paymentDueDay = paymentDueDay; }
    public void setNotes(String notes) { this.notes = notes; }
}
