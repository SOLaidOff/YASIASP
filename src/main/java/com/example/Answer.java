package com.example;

import java.sql.Timestamp;

public class Answer {
    public int id;
    public int question;
    public String body;
    public String author;
    public Timestamp timestamp;
    public int score;

    public String currentUserVote;
}
