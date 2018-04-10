package com.example;

import java.sql.Timestamp;

public abstract class History {
    public int id;
    public String body;
    public String author;
    public Timestamp timestamp;
    public int parent;
}
