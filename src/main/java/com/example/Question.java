package com.example;

import java.sql.Timestamp;
import java.util.Set;

public class Question {
    public int id;
    public String title;
    public String body;
    public String author;
    public Timestamp timestamp;
    public int score;
    
    public Set<String> tags;
}
