/*
 * Copyright 2002-2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;

import javax.servlet.http.HttpServletRequest;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

@Controller
@SpringBootApplication
public class Main {

    @Value("${spring.datasource.url}") // Pulls from src/main/resources/application.properties, which in turn refers to ${JDBC_DATABASE_URL:} (a system variable)
    private String dbUrl;

    @Autowired
    private DataSource dataSource;

    public static void main(String[] args) throws Exception {
        SpringApplication.run(Main.class, args);
    }

    //////////////////// The YASIAS main page

    @RequestMapping("/")
    String index(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement usersStmt = connection.prepareStatement("SELECT Name FROM Users;");
            ResultSet rs = usersStmt.executeQuery();

            List<String> output = new ArrayList<String>();
            while (rs.next()) {
                output.add(rs.getString(DbColNames.USERS_NAME));
            }
            model.put("names", output);
        } catch (Exception e) {
            model.put("message", "error in try/catch 1 of root (users section); " + e.getMessage());

            return "error";
        }

        try (Connection connection = dataSource.getConnection()) {
            PreparedStatement questionsStmt = connection.prepareStatement("SELECT * FROM Questions;");
            ResultSet rs = questionsStmt.executeQuery();

            List<Question> questions = new ArrayList<Question>();
            while (rs.next()) { // Essentially "for each question"
                Question currentQuestion = new Question();

                currentQuestion.id = rs.getInt(DbColNames.QUESTION_ID);
                currentQuestion.title = rs.getString(DbColNames.QUESTION_TITLE);
                currentQuestion.author = rs.getString(DbColNames.QUESTION_AUTHOR);
                currentQuestion.timestamp = rs.getTimestamp(DbColNames.QUESTION_TIMESTAMP);
                currentQuestion.score = rs.getInt(DbColNames.QUESTION_SCORE);

                PreparedStatement questionTagsStmt = connection.prepareStatement("SELECT TagName FROM QuestionTags WHERE QId= ? ;");
                questionTagsStmt.setInt(1, currentQuestion.id);
                ResultSet rsTags = questionTagsStmt.executeQuery();

                Set<String> tags = new HashSet<String>();
                while (rsTags.next()) {
                    tags.add(rsTags.getString(DbColNames.QUESTION_TAGS_TAGNAME));
                }
                currentQuestion.tags = tags;

                questions.add(currentQuestion);
            }
            model.put("questions", questions);
        } catch (Exception e) {
            model.put("message", "error in try/catch 2 of root (users section); " + e.getMessage());

            return "error";
        }

        return "index";
    }

    //////////////////// Methods related to YASIAS questions

    @RequestMapping("/ask")
    String ask(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        model.put("submitted", request.getParameter("submission"));

        boolean hasTitle = request.getParameter("title") != null;
        boolean hasBody = request.getParameter("body") != null;
        boolean hasTags = request.getParameter("tags") != null;

        if (hasTitle && hasBody && hasTags) {
            try (Connection connection = dataSource.getConnection()) {
                // TODO: figure out what this has to do with atomicity

                PreparedStatement questionAddStmt = connection.prepareStatement("INSERT INTO Questions VALUES (DEFAULT, ? , ? , ? , 'now', 0);");
                questionAddStmt.setString(1, currentUser);
                questionAddStmt.setString(2, request.getParameter("title"));
                questionAddStmt.setString(3, request.getParameter("body"));
                questionAddStmt.executeUpdate();
                // TODO: handle DB errors

                // Get the ID of the just-created question, for tag purposes
                PreparedStatement questionAddTagsStmt = connection.prepareStatement("SELECT Id FROM Questions WHERE Author= ? AND Title= ? ORDER BY Timestamp DESC LIMIT 1;");
                // FIXME this is a hacky approximation; use atomic operation?

                questionAddTagsStmt.setString(1, currentUser);
                questionAddTagsStmt.setString(2, request.getParameter("title"));
                ResultSet rs = questionAddTagsStmt.executeQuery();

                rs.next();
                int questionId = rs.getInt("Id");
                model.put("newlyAskedQuestionsId", questionId);

                // Handle tags (get the raw submitted tag string, divide it into individual tags, insert any new ones, associate with current question
                String tags = request.getParameter("tags");
                Scanner tagScanner = new Scanner(tags);
                tagScanner.useDelimiter("\\+"); // Delimiter is + to support when requests are GETs
                while (tagScanner.hasNext()) {
                    String nextTag = tagScanner.next();

                    PreparedStatement tagAddStmt = connection.prepareStatement("INSERT INTO Tags VALUES ( ? ) ON CONFLICT DO NOTHING;");
                    tagAddStmt.setString(1, nextTag);
                    tagAddStmt.executeUpdate();

                    PreparedStatement questionTagAddStmt = connection.prepareStatement("INSERT INTO QuestionTags VALUES ( ? , ? ) ON CONFLICT DO NOTHING;");
                    questionTagAddStmt.setInt(1, questionId);
                    questionTagAddStmt.setString(2, nextTag);
                    questionTagAddStmt.executeUpdate();
                }

                tagScanner.close();
            } catch (Exception e) {
                model.put("message", "try/catch error in /ask(); " + e.getMessage());

                return "error";
            }
        }

        return "ask";
    }

    @RequestMapping("/question")
    String question(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        Integer currentQuestionId = Integer.parseInt(request.getParameter("id"));
        model.put("qid", currentQuestionId);

        // Display the question

        // Handle voting
        boolean isQuestionUpvote = request.getParameter("upvoteQ") != null;
        boolean isQuestionCancelvote = request.getParameter("cancelvoteQ") != null;
        boolean isQuestionDownvote = request.getParameter("downvoteQ") != null;
        boolean isAnswerUpvote = request.getParameter("upvoteA") != null;
        boolean isAnswerCancelvote = request.getParameter("cancelvoteA") != null;
        boolean isAnswerDownvote = request.getParameter("downvoteA") != null;

        short voteCount = 0;
        //@formatter:off
        if(isQuestionUpvote) { voteCount++; }
        if(isQuestionCancelvote) { voteCount++; }
        if(isQuestionDownvote) { voteCount++; }
        if(isAnswerUpvote) { voteCount++; }
        if(isAnswerCancelvote) { voteCount++; }
        if(isAnswerDownvote) { voteCount++; }
        //@formatter:on

        if (voteCount > 1) {
            model.put("message", "Can't perform multiple vote actions simultaneously.");

            return "error";
        }

        try (Connection connection = dataSource.getConnection()) {
            // Determine existing vote status
            StringBuilder sqlBuilder = new StringBuilder();

            if (isQuestionUpvote || isQuestionDownvote || isAnswerUpvote || isAnswerDownvote) {
                // Example:
                // INSERT INTO Votes VALUES ( ? [PrepStmt1: Voter] , ' [StrBrk12: PostType] ', ? [PrepStmt2:PostId] , ' [StrBrk23: Value] ', 'now') ON CONFLICT (Voter, PostType, PostId) DO UPDATE SET
                // Value=' [StrBrk34: Value] ', Timestamp='now';";

                String sqlPiece1 = "INSERT INTO Votes VALUES ( ? , '";
                String sqlPiece2 = "', ? , '";
                String sqlPiece3 = "', 'now') ON CONFLICT (Voter, PostType, PostId) DO UPDATE SET Value='";
                String sqlPiece4 = "', Timestamp='now';";

                String sqlPieceUpvote = "upvote";
                String sqlPieceDownvote = "downvote";
                String sqlPieceQuestion = "question";
                String sqlPieceAnswer = "answer";

                Integer postId = new Integer(-1);

                if (isQuestionUpvote) {
                    sqlBuilder.append(sqlPiece1);
                    sqlBuilder.append(sqlPieceQuestion);
                    sqlBuilder.append(sqlPiece2);
                    sqlBuilder.append(sqlPieceUpvote);
                    sqlBuilder.append(sqlPiece3);
                    sqlBuilder.append(sqlPieceUpvote);
                    sqlBuilder.append(sqlPiece4);

                    postId = currentQuestionId;
                } else if (isQuestionDownvote) {
                    sqlBuilder.append(sqlPiece1);
                    sqlBuilder.append(sqlPieceQuestion);
                    sqlBuilder.append(sqlPiece2);
                    sqlBuilder.append(sqlPieceDownvote);
                    sqlBuilder.append(sqlPiece3);
                    sqlBuilder.append(sqlPieceDownvote);
                    sqlBuilder.append(sqlPiece4);

                    postId = currentQuestionId;
                } else if (isAnswerUpvote) {
                    sqlBuilder.append(sqlPiece1);
                    sqlBuilder.append(sqlPieceAnswer);
                    sqlBuilder.append(sqlPiece2);
                    sqlBuilder.append(sqlPieceUpvote);
                    sqlBuilder.append(sqlPiece3);
                    sqlBuilder.append(sqlPieceUpvote);
                    sqlBuilder.append(sqlPiece4);

                    postId = Integer.parseInt(request.getParameter("answerId"));
                } else if (isAnswerDownvote) {
                    sqlBuilder.append(sqlPiece1);
                    sqlBuilder.append(sqlPieceAnswer);
                    sqlBuilder.append(sqlPiece2);
                    sqlBuilder.append(sqlPieceDownvote);
                    sqlBuilder.append(sqlPiece3);
                    sqlBuilder.append(sqlPieceDownvote);
                    sqlBuilder.append(sqlPiece4);

                    postId = Integer.parseInt(request.getParameter("answerId"));
                }

                PreparedStatement voteChangeStmt = connection.prepareStatement(sqlBuilder.toString());
                voteChangeStmt.setString(1, currentUser);
                voteChangeStmt.setInt(2, postId);
                voteChangeStmt.executeUpdate();
            }

            if (isQuestionCancelvote) {
                // Simply remove the existing vote

                // FIXME (low priority) - does not preserve time of deletion
                PreparedStatement voteChangeStmt = connection.prepareStatement("DELETE FROM Votes WHERE Voter= ? AND PostType='question' AND PostId= ? ;");
                voteChangeStmt.setString(1, currentUser);
                voteChangeStmt.setInt(2, currentQuestionId);
                voteChangeStmt.executeUpdate();
            } else if (isAnswerCancelvote) {
                // Simply remove the existing vote

                // FIXME (low priority) - does not preserve time of deletion
                PreparedStatement voteChangeStmt = connection.prepareStatement("DELETE FROM Votes WHERE Voter= ? AND PostType='answer' AND PostId= ? ;");
                voteChangeStmt.setString(1, currentUser);
                voteChangeStmt.setInt(2, Integer.parseInt(request.getParameter("answerId")));
                voteChangeStmt.executeUpdate();
            }
        } catch (Exception e) {
            model.put("message", "Exception in question() - vote processing logic " + e.getMessage());

            return "error";
        }

        // Done with vote handling, get the rest of the post info
        try (Connection connection = dataSource.getConnection()) {
            // Get basic info on the current question
            PreparedStatement questionStmtMain = connection.prepareStatement("SELECT * FROM Questions WHERE Id= ? ;");
            questionStmtMain.setInt(1, currentQuestionId);
            ResultSet rs = questionStmtMain.executeQuery();

            while (rs.next()) {
                model.put("author", rs.getString(DbColNames.QUESTION_AUTHOR));
                model.put("title", rs.getString(DbColNames.QUESTION_TITLE));
                model.put("body", rs.getString(DbColNames.QUESTION_BODY));
                model.put("timestamp", rs.getString(DbColNames.QUESTION_TIMESTAMP));
                model.put("score", rs.getString(DbColNames.QUESTION_SCORE));
            }

            // Get info on the question's tags
            PreparedStatement questionStmtTags = connection.prepareStatement("SELECT TagName FROM QuestionTags WHERE QId= ? ;");
            questionStmtTags.setInt(1, currentQuestionId);
            ResultSet rsTags = questionStmtTags.executeQuery();

            Set<String> tags = new HashSet<String>();
            while (rsTags.next()) {
                tags.add(rsTags.getString(DbColNames.QUESTION_TAGS_TAGNAME));
            }
            model.put("tags", tags);

            // Get info on the question's votes, with respect to the current user
            PreparedStatement questionStmtVotes = connection.prepareStatement("SELECT Value FROM Votes WHERE Voter= ? AND PostType='question' AND PostId= ? ;");
            questionStmtVotes.setString(1, currentUser);
            questionStmtVotes.setInt(2, currentQuestionId);
            ResultSet rsVotes = questionStmtVotes.executeQuery();

            String existingQVote = "novote";
            while (rsVotes.next()) {
                existingQVote = rsVotes.getString(DbColNames.VOTES_VALUE);
                if (existingQVote == null) {
                    existingQVote = "novote";
                }
            }
            model.put("existingQVote", existingQVote);

            // Get info on any/all answers to the current question
            List<Answer> answers = new ArrayList<Answer>();
            PreparedStatement answerStmtMain = connection.prepareStatement("SELECT * FROM Answers WHERE Question = ? ;");
            answerStmtMain.setInt(1, currentQuestionId);
            rs = answerStmtMain.executeQuery();

            while (rs.next()) {
                Answer currentAnswer = new Answer();

                currentAnswer.id = rs.getInt(DbColNames.ANSWER_ID);
                currentAnswer.author = rs.getString(DbColNames.ANSWER_AUTHOR);
                currentAnswer.question = rs.getInt(DbColNames.ANSWER_QUESTION);
                currentAnswer.body = rs.getString(DbColNames.ANSWER_BODY);
                currentAnswer.timestamp = rs.getTimestamp(DbColNames.ANSWER_TIMESTAMP);
                currentAnswer.score = rs.getInt(DbColNames.ANSWER_SCORE);

                // Get info on the answer's votes, with respect to the current user
                PreparedStatement answerStmtVotes = connection.prepareStatement("SELECT Value FROM Votes WHERE Voter= ? AND PostType='answer' AND PostId= ? ;");
                answerStmtVotes.setString(1, currentUser);
                answerStmtVotes.setInt(2, rs.getInt(DbColNames.ANSWER_ID));
                rsVotes = answerStmtVotes.executeQuery();
                String existingAVote = "novote";
                while (rsVotes.next()) {
                    existingAVote = rsVotes.getString(DbColNames.VOTES_VALUE);
                    if (existingAVote == null) {
                        existingAVote = "novote";
                    }
                }
                currentAnswer.currentUserVote = existingAVote;

                answers.add(currentAnswer);
            }

            model.put("answers", answers);
        } catch (Exception e) {
            model.put("message", "try/catch error in question() " + e.getMessage());

            return "error";
        }

        return "question";
    }

    @RequestMapping("/questionedit")
    String questionedit(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        Integer currentQuestionId = Integer.parseInt(request.getParameter("id"));
        model.put("qid", currentQuestionId);

        // An edited question has been submitted
        if (request.getParameter("newdata") != null) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement questionEditStmtMain = connection.prepareStatement("UPDATE Questions SET Title= ? , Body= ? , Author= ? , Timestamp='now' WHERE Id= ? ;");
                questionEditStmtMain.setString(1, request.getParameter("title"));
                questionEditStmtMain.setString(2, request.getParameter("body"));
                questionEditStmtMain.setString(3, request.getParameter("author"));
                questionEditStmtMain.setInt(4, currentQuestionId);
                questionEditStmtMain.executeUpdate();

                // Update tags: simple (if inelegant) solution is to remove all tags for the Q and then add the new ones
                PreparedStatement questionEditStmtTags1 = connection.prepareStatement("DELETE FROM QuestionTags WHERE QId= ? ;");
                questionEditStmtTags1.setInt(1, currentQuestionId);
                questionEditStmtTags1.executeUpdate();

                // Read incoming tags and handle them
                String tags = request.getParameter("tags");
                Scanner tagScanner = new Scanner(tags);
                tagScanner.useDelimiter("\\+"); // Delimiter supports + character in case requests are GETs
                while (tagScanner.hasNext()) {
                    String nextTag = tagScanner.next();

                    PreparedStatement questionEditStmtTags2 = connection.prepareStatement("INSERT INTO Tags VALUES ( ? ) ON CONFLICT DO NOTHING;");
                    questionEditStmtTags2.setString(1, nextTag);
                    questionEditStmtTags2.executeUpdate();

                    PreparedStatement questionEditStmtTags3 = connection.prepareStatement("INSERT INTO QuestionTags VALUES ( ? , ? ) ON CONFLICT DO NOTHING;");
                    questionEditStmtTags3.setInt(1, currentQuestionId);
                    questionEditStmtTags3.setString(2, nextTag);
                    questionEditStmtTags3.executeUpdate();
                }
                tagScanner.close();
            } catch (Exception e) {
                model.put("message", "try/catch error in questionedit() (first half) when updating DB; " + e.getMessage());

                return "error";
            }
        }

        try (Connection connection = dataSource.getConnection()) {
            ResultSet rs = null;

            PreparedStatement questionEditStmtReadMain = connection.prepareStatement("SELECT * FROM Questions WHERE Id= ? ;");
            questionEditStmtReadMain.setInt(1, currentQuestionId);
            rs = questionEditStmtReadMain.executeQuery();

            rs.next();
            model.put("title", rs.getString(DbColNames.QUESTION_TITLE));
            model.put("body", rs.getString(DbColNames.QUESTION_BODY));

            // Tags for the question
            StringBuilder tagStringBuilder = new StringBuilder();

            PreparedStatement questionEditStmtReadTags = connection.prepareStatement("SELECT TagName FROM QuestionTags WHERE QId= ? ;");
            questionEditStmtReadTags.setInt(1, currentQuestionId);
            rs = questionEditStmtReadTags.executeQuery();

            while (rs.next()) {
                tagStringBuilder.append(rs.getString(DbColNames.QUESTION_TAGS_TAGNAME));
                tagStringBuilder.append(" ");
            }

            if (tagStringBuilder.length() > 0) { // This if statement shouldn't be necessary if things are working correctly, but for testing, tags might not be present...
                tagStringBuilder.deleteCharAt(tagStringBuilder.length() - 1); // Remove trailing space
            }

            model.put("tags", tagStringBuilder.toString());
        } catch (Exception e) {
            model.put("message", "try/catch error in questionedit() (second half) " + e.getMessage());

            return "error";
        }

        return "questionedit";
    }

    @RequestMapping("/questiondelete")
    String questionDelete(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        Integer questionId = Integer.parseInt(request.getParameter("id"));

        if (questionId != null) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("DELETE FROM Questions WHERE Id = ? ;");
                stmt.setInt(1, questionId);
                stmt.executeUpdate();
                // TODO: handle DB errors, e.g. non-existant ID
            } catch (Exception e) {
                model.put("message", "try/catch error in questiondelete(); " + e.getMessage());

                return "error";
            }

            model.put("removedQuestionId", questionId);
        }

        return "questiondelete";
    }

    //////////////////// Methods related to YASIAS answers

    @RequestMapping("/answer")
    String answer(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        String questionTitle = request.getParameter("questionTitle");
        model.put("questionTitle", questionTitle);
        Integer questionId = Integer.parseInt(request.getParameter("qid"));
        model.put("questionId", questionId);

        if (request.getParameter("submission") != null) {
            model.put("submitted", "true");

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("INSERT INTO Answers VALUES (DEFAULT, ? , ? , ? , 'now', 0);");
                stmt.setString(1, currentUser);
                stmt.setInt(2, questionId);
                stmt.setString(3, request.getParameter("body"));
                stmt.executeUpdate();
                // TODO: handle DB errors
            } catch (Exception e) {
                model.put("message", "try/catch error in /ask(); " + e.getMessage());

                return "error";
            }
        }

        return "answer";
    }

    @RequestMapping("/answeredit")
    String answerEdit(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        model.put("aid", request.getParameter("id"));
        model.put("qid", request.getParameter("qid"));

        if (request.getParameter("newdata") != null) {
            model.put("submitted", "true");

            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("UPDATE Answers SET Body= ? , Author= ? , Timestamp='now' WHERE Id= ? ;");
                stmt.setString(1, request.getParameter("body"));
                stmt.setString(2, request.getParameter("author"));
                stmt.setInt(3, Integer.parseInt(request.getParameter("id")));
                stmt.executeUpdate();
            } catch (Exception e) {
                model.put("message", "try/catch error in answeredit() when updating DB; " + e.getMessage());

                return "error";
            }
        }

        if (request.getParameter("id") != null) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("SELECT * FROM Answers WHERE Id= ? ;");
                stmt.setInt(1, Integer.parseInt(request.getParameter("id")));
                ResultSet rs = stmt.executeQuery();

                rs.next();
                model.put("body", rs.getString(DbColNames.ANSWER_BODY));
            } catch (Exception e) {
                model.put("message", "try/catch error in answeredit() " + e.getMessage());

                return "error";
            }
        }

        return "answeredit";
    }

    @RequestMapping("/answerdelete")
    String answerDelete(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        model.put("qid", request.getParameter("qid"));

        Integer answerId = Integer.parseInt(request.getParameter("id"));

        if (answerId != null) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("DELETE FROM Answers WHERE Id = ? ;");
                stmt.setInt(1, answerId);
                stmt.executeUpdate();
                // TODO: handle DB errors, e.g. non-existant ID
            } catch (Exception e) {
                model.put("message", "try/catch error in questiondelete(); " + e.getMessage());

                return "error";
            }

            model.put("removedAnswerId", answerId);
        }

        return "answerdelete";
    }

    //////////////////// Methods related to YASIAS users

    @RequestMapping("/useradd")
    String useradd(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        if (request.getParameter("newusername") != null) {
            // int sqlReturnValue = -5;
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("INSERT INTO Users VALUES ( ? );");
                stmt.setString(1, request.getParameter("newusername"));
                stmt.executeUpdate();
                // TODO: handle DB errors, e.g. duplicate name
            } catch (Exception e) {
                model.put("message", "try/catch error in useradd(); " + e.getMessage());

                return "error";
            }

            return "useradd";
        }

        return "useradd";
    }

    @RequestMapping("/userdelete")
    String userdelete(Map<String, Object> model, HttpServletRequest request) {
        String currentUser = getCurrentUser(request);
        model.put("currentUser", currentUser);

        String userToDelete = request.getParameter("name");

        if (userToDelete != null) {
            try (Connection connection = dataSource.getConnection()) {
                PreparedStatement stmt = connection.prepareStatement("DELETE FROM Users WHERE Name = ? ;");
                stmt.setString(1, userToDelete);
                stmt.executeUpdate();
                // TODO: handle DB errors, e.g. non-existant name
            } catch (Exception e) {
                model.put("message", "try/catch error in userdelete(); " + e.getMessage());

                return "error";
            }

            model.put("removedUser", userToDelete);
        }

        return "userdelete";
    }

    //////////////////// Methods related to YASIAS tags

    // TODO: allow looking at info about existing tags; not top priority

    //////////////////// YASIAS helper methods

    /*
     * @formatter:off
    private Map<String, String> parseHttpRequestParams(String queryString) {
        Map<String, String> paramMap = new HashMap<String, String>();

        if (queryString == null) {
            return paramMap;
        }

        Scanner paramScanner = new Scanner(queryString);
        paramScanner.useDelimiter("[=&]");

        while (paramScanner.hasNext()) {
            String key = paramScanner.next();
            String value = paramScanner.next();

            paramMap.put(key, value);
        }

        paramScanner.close();

        return paramMap;
    }
     * @formatter:on
     */

    private String getCurrentUser(HttpServletRequest request) {
        String currentUser = request.getParameter("name"); // null if there was no name param
        if (currentUser == null) {
            currentUser = "anonymous";
        }

        return currentUser;
    }

    /*
     * @formatter:off
    private String getCurrentUserFromQueryString(Map<String, String> httpRqstVarsMap) {
        String currentUser = httpRqstVarsMap.get("name"); // null if there was no name param if(currentUser == null)
        {
            currentUser = "anonymous";
        }

        return currentUser;
    }
     * @formatter:on
     */

    // tl;dr on Hikari:
    // Came with the example code, is a third-party open-source drop-in connection pool solution
    // Ref 1: https://brettwooldridge.github.io/HikariCP/
    // Ref 2: https://github.com/brettwooldridge/HikariCP
    @Bean
    public DataSource dataSource() throws SQLException {
        if (dbUrl == null || dbUrl.isEmpty()) {
            return new HikariDataSource();
        } else {
            HikariConfig config = new HikariConfig();
            config.setJdbcUrl(dbUrl);
            return new HikariDataSource(config);
        }
    }

    //////////////////// Starter or debugging code not integral to YASIAS

    // First step: Add new RequestMapping annotation for a URL, an associated method,
    // and an .html page under src/main/resources/templates with the returned name
    // See "db" for a more complex example that accesses the DB and modifies the model and view
    @RequestMapping("/test")
    String test() {
        return "test";
    }

    @RequestMapping("/debug")
    String debug(Map<String, Object> model, HttpServletRequest request) {
        String testString = request.getParameter("parameterThatDoesNotExist"); // returns null
        if (testString.equals("valueThatIsNonexistant")) {
            testString = "Getting rid of a warning.";
        }

        try {
            Connection connection = dataSource.getConnection();
            if (connection == null) {
                // Just getting rid of a warning.
            }

            // model.put("message", "Hello, world! dbUrl: " + dbUrl); // The first arg must be "message" because the error page uses <p th:text="${message}">
        } catch (SQLException e) {
            model.put("message", e.getMessage() + " --- dbUrl: " + dbUrl);

            return "error";
        }

        return "debug";
    }
}
