package com.spendwise.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.spendwise.data.Transaction;

import java.util.List;

@Dao
public interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY date DESC")
    LiveData<List<Transaction>> getAll();

    @Query("SELECT * FROM transactions ORDER BY date DESC")
    List<Transaction> getAllForLookup();

    @Query("SELECT * FROM transactions WHERE id = :id")
    Transaction getById(String id);

    @Query("SELECT * FROM transactions WHERE instrumentRefId = :instrumentRefId ORDER BY date DESC")
    List<Transaction> getByInstrumentRefId(String instrumentRefId);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Transaction transaction);

    @Update
    void update(Transaction transaction);

    @Delete
    void delete(Transaction transaction);

    @Query("UPDATE transactions SET instrumentRefId = :instrumentRefId WHERE instrumentType = :instrumentType AND instrumentId = :instrumentId AND (instrumentRefId IS NULL OR instrumentRefId = 'UNKNOWN')")
    void linkDetectedTransactions(String instrumentRefId, String instrumentType, String instrumentId);

    @Query("DELETE FROM transactions")
    void deleteAll();

    @Query("UPDATE transactions SET category = :category WHERE merchantName = :merchantName")
    void updateCategoryForMerchantName(String merchantName, String category);
}
