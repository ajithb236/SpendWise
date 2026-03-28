package com.spendwise.db;

import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.spendwise.data.Instrument;

import java.util.List;

@Dao
public interface InstrumentDao {
    @Query("SELECT * FROM instruments ORDER BY nickname COLLATE NOCASE ASC")
    List<Instrument> getAll();

    @Query("SELECT * FROM instruments WHERE id = :id")
    Instrument getById(String id);

    @Query("SELECT * FROM instruments WHERE instrumentType = :instrumentType AND instrumentIdMasked = :instrumentIdMasked LIMIT 1")
    Instrument findByTypeAndMaskedId(String instrumentType, String instrumentIdMasked);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Instrument instrument);

    @Update
    void update(Instrument instrument);

    @Delete
    void delete(Instrument instrument);

    @Query("DELETE FROM instruments")
    void deleteAll();
}
