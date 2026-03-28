package com.spendwise.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.spendwise.data.InstrumentAlias;

@Dao
public interface InstrumentAliasDao {
    @Query("SELECT * FROM instrument_aliases WHERE aliasKey = :aliasKey LIMIT 1")
    InstrumentAlias getByAliasKey(String aliasKey);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(InstrumentAlias alias);

    @Query("DELETE FROM instrument_aliases")
    void deleteAll();
}
