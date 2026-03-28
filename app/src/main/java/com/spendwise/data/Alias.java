package com.spendwise.data;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;
import androidx.annotation.NonNull;

import java.io.Serializable;

@Entity(tableName = "aliases")
public class Alias implements Serializable {

    @PrimaryKey(autoGenerate = true)
    private int id;
    private String originalName;
    private String aliasName;
    private String category;

    @Ignore
    public Alias(String originalName, String aliasName) {
        this(originalName, aliasName, "OTHER");
    }

    public Alias(String originalName, String aliasName, String category) {
        this.originalName = originalName;
        this.aliasName = aliasName;
        this.category = category;
    }

    public int getId() { return id; }
    public void setId(int id) { this.id = id; }

    public String getOriginalName() { return originalName; }
    public void setOriginalName(String originalName) { this.originalName = originalName; }

    public String getAliasName() { return aliasName; }
    public void setAliasName(String aliasName) { this.aliasName = aliasName; }

    public String getCategory() { return category; }
    public void setCategory(String category) { this.category = category; }
}
