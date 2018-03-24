package com.example;

public class DbColNames {
    // Questions
    public static final String QUESTION_ID = "Id"; // Primary key
    public static final String QUESTION_AUTHOR = "Author"; // Foreign key, matches with Users
    public static final String QUESTION_TITLE = "Title";
    public static final String QUESTION_BODY = "Body";
    public static final String QUESTION_TIMESTAMP = "Timestamp";
    public static final String QUESTION_SCORE = "Score";
    
    // Answers
    public static final String ANSWER_ID = "Id"; // Primary key
    public static final String ANSWER_QUESTION = "Question"; // Foreign key, matches with Questions
    public static final String ANSWER_AUTHOR = "Author"; // Foriegn key, matches with Users
    public static final String ANSWER_BODY = "Body";
    public static final String ANSWER_TIMESTAMP = "Timestamp";
    public static final String ANSWER_SCORE = "Score";
    
    // Comments
    public static final String COMMENTS_ID = "Id"; // Primary key; auto-increment
    public static final String COMMENTS_POSTID = "PostId"; // Foreign key, matches with Questions or Answers
    public static final String COMMENTS_POSTTYPE = "PostType";
    public static final String COMMENTS_BODY = "Body";
    public static final String COMMENTS_TIMESTAMP = "Timestamp";
    
    // Tags
    public static final String TAGS_NAME = "Name"; // Primary key
    
    // Users
    public static final String USERS_NAME = "Name"; // Primary key
    
    // Votes
    public static final String VOTES_POSTID = "PostId"; // Primary key, jointly with PostType and Author
    public static final String VOTES_POSTTYPE = "PostType"; // Primary key, jointly with PostId and Author
    public static final String VOTES_AUTHOR = "Author"; // Primary key, jointly with PostId and PostType
    public static final String VOTES_TIMESTAMP = "Timestamp";
    public static final String VOTES_VALUE = "Value";
    
    // QuestionTags
    public static final String QUESTION_TAGS_QID = "QId"; // Primary key, jointly with TagName; Foreign key, matches with Questions
    public static final String QUESTION_TAGS_TAGNAME = "TagName"; // Primary key, jointly with QId; Foreign key, matches with Tags
    
    // QuestionHistory
    public static final String QUESTION_HISTORY_ID = "Id"; // Primary key; auto-increment
    public static final String QUESTION_HISTORY_QID = "QId"; // Foreign key, matches with Questions
    public static final String QUESTION_HISTORY_AUTHOR = "Author"; // Foreign key, matches with Users
    public static final String QUESTION_HISTORY_TITLE = "Title";
    public static final String QUESTION_HISTORY_BODY = "Body";
    public static final String QUESTION_HISTORY_TIMESTAMP = "Timestamp";
    
    // AnswerHistory
    public static final String ANSWER_HISTORY_ID = "Id"; // Primary key; auto-increment
    public static final String ANSWER_HISTORY_AID = "AId"; // Foreign key, matches with Answers
    public static final String ANSWER_HISTORY_AUTHOR = "Author"; // Foreign key, matches with Users
    public static final String ANSWER_HISTORY_TITLE = "Title";
    public static final String ANSWER_HISTORY_BODY = "Body";
    public static final String ANSWER_HISTORY_TIMESTAMP = "Timestamp";
}
