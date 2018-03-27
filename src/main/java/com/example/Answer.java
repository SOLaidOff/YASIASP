package com.example;

import java.sql.Timestamp;
import java.util.List;

public class Answer {
    public int id;
    public int question;
    public String body;
    public String author;
    public Timestamp timestamp;
    public int score;

    public String currentUserVote;
    
    public List<Comment> comments;
}
