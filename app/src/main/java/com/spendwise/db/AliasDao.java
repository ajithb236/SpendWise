package com.spendwise.db;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;

import com.spendwise.data.Alias;

import java.util.List;

@Dao
public interface AliasDao {
    @Query("SELECT * FROM aliases ORDER BY originalName COLLATE NOCASE ASC")
    List<Alias> getAll();

    @Query("SELECT * FROM aliases WHERE originalName = :originalName LIMIT 1")
    Alias getByOriginalName(String originalName);

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    void insert(Alias alias);

    @Query("UPDATE aliases SET aliasName = :aliasName WHERE id = :id")
    void updateAliasName(int id, String aliasName);

    @Query("UPDATE aliases SET aliasName = :aliasName, category = :category WHERE id = :id")
    void updateAlias(int id, String aliasName, String category);

    @Query("DELETE FROM aliases WHERE id = :id")
    void deleteById(int id);

    @Query("DELETE FROM aliases")
    void deleteAll();
}
