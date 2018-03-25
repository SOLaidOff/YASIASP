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
//import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
//import java.util.Collection;
import java.util.HashMap;
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
        String queryString = request.getQueryString(); // Ex. "name=Harry" (without the quotes)
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT Name FROM Users;");

            List<String> output = new ArrayList<String>();
            while (rs.next()) {
                output.add(rs.getString(DbColNames.USERS_NAME));
            }
            model.put("names", output);
        } catch (Exception e) {
            model.put("message", "error in try/catch of root (users section); " + e.getMessage());

            return "error";
        }

        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();
            Statement stmt2 = connection.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT * FROM Questions;");

            List<Question> questions = new ArrayList<Question>();
            while (rs.next()) { // Essentially "for each question"
                Question currentQuestion = new Question();
                
                currentQuestion.id = rs.getInt(DbColNames.QUESTION_ID);
                currentQuestion.title = rs.getString(DbColNames.QUESTION_TITLE);
                currentQuestion.author = rs.getString(DbColNames.QUESTION_AUTHOR);
                currentQuestion.timestamp = rs.getTimestamp(DbColNames.QUESTION_TIMESTAMP);
                currentQuestion.score = rs.getInt(DbColNames.QUESTION_SCORE);
                
                ResultSet rsTags = stmt2.executeQuery("SELECT TagName FROM QuestionTags WHERE QId='" + currentQuestion.id + "';");
                
                Set<String> tags = new HashSet<String>();
                while(rsTags.next()) {
                    tags.add(rsTags.getString(DbColNames.QUESTION_TAGS_TAGNAME));
                }
                currentQuestion.tags = tags;
                
                questions.add(currentQuestion);
            }
            model.put("questions", questions);
        } catch (Exception e) {
            model.put("message", "error in try/catch of root (users section); " + e.getMessage());

            return "error";
        }

        return "index";
    }
    
    //////////////////// Methods related to YASIAS questions

    @RequestMapping("/ask")
    String ask(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString();

        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);
        
        model.put("submitted", httpRqstVarsMap.get("submission"));
        
        boolean hasTitle = httpRqstKeys.contains("title");
        boolean hasBody = httpRqstKeys.contains("body");
        boolean hasTags = httpRqstKeys.contains("tags");
        
        if(hasTitle && hasBody && hasTags) {
            try (Connection connection = dataSource.getConnection()) {
                // TODO: figure out what this has to do with atomicity
                
                Statement stmt = connection.createStatement();
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("INSERT INTO Questions VALUES (");
                sqlBuilder.append("DEFAULT"); // auto-incrementing ID
                sqlBuilder.append(", '");
                sqlBuilder.append(currentUser); // author
                sqlBuilder.append("', '");
                sqlBuilder.append(httpRqstVarsMap.get("title")); // title
                sqlBuilder.append("', '");
                sqlBuilder.append(httpRqstVarsMap.get("body")); // body
                sqlBuilder.append("', ");
                sqlBuilder.append("'now', "); // timestamp
                sqlBuilder.append("0"); // score
                sqlBuilder.append(");");

                stmt.executeUpdate(sqlBuilder.toString());
                // TODO: handle DB errors
                
                // Get the ID of the just-created question, for tag purposes
                ResultSet rs = stmt.executeQuery("SELECT Id FROM Questions WHERE Author='" + currentUser + "' AND Title='" + httpRqstVarsMap.get("title") + "' ORDER BY Timestamp DESC LIMIT 1;"); // FIXME this is an approximation; use atomic operation?
                rs.next();
                int questionId = rs.getInt("Id");
                model.put("newlyAskedQuestionsId", questionId);
                                
                // Handle tags (get the raw submitted tag string, divide it into individual tags, insert any new ones, associate with current question 
                String tags = httpRqstVarsMap.get("tags");
                Scanner tagScanner = new Scanner(tags);
                tagScanner.useDelimiter("\\+"); // + because of GET operation
                while(tagScanner.hasNext()) {
                    String nextTag = tagScanner.next();

                    stmt.executeUpdate("INSERT INTO Tags VALUES ('" + nextTag + "') ON CONFLICT DO NOTHING;");
                    stmt.executeUpdate("INSERT INTO QuestionTags VALUES ('" + questionId + "', '" + nextTag + "') ON CONFLICT DO NOTHING;");
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
        String queryString = request.getQueryString(); // Ex. "upvote=true&foo=bar" (without the quotes)
        model.put("queryString", queryString);

        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        if (httpRqstKeys.contains("id")) {
            model.put("qid", httpRqstVarsMap.get("id"));
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();
                Statement stmt2 = connection.createStatement();

                // Get info on the current question
                ResultSet rs = stmt.executeQuery("SELECT * FROM Questions WHERE Id=" + httpRqstVarsMap.get("id"));
                ResultSet rsTags = stmt2.executeQuery("SELECT TagName FROM QuestionTags WHERE QId='" + httpRqstVarsMap.get("id") + "';");
                
                Set<String> tags = new HashSet<String>();
                while(rsTags.next()) {
                    tags.add(rsTags.getString(DbColNames.QUESTION_TAGS_TAGNAME));
                }
                model.put("tags", tags);

                while (rs.next()) {
                    model.put("author", rs.getString(DbColNames.QUESTION_AUTHOR));
                    model.put("title", rs.getString(DbColNames.QUESTION_TITLE));
                    model.put("body", rs.getString(DbColNames.QUESTION_BODY));
                    model.put("timestamp", rs.getString(DbColNames.QUESTION_TIMESTAMP));
                    model.put("score", rs.getString(DbColNames.QUESTION_SCORE));
                }
                
                // Get info on any/all answers to the current question
                List<Answer> answers = new ArrayList<Answer>();
                rs = stmt.executeQuery("SELECT * FROM Answers WHERE Question =" + httpRqstVarsMap.get("id"));

                while (rs.next()) {
                    Answer currentAnswer = new Answer();

                    currentAnswer.id = rs.getInt(DbColNames.ANSWER_ID);
                    currentAnswer.author = rs.getString(DbColNames.ANSWER_AUTHOR);
                    currentAnswer.question = rs.getInt(DbColNames.ANSWER_QUESTION);
                    currentAnswer.body = rs.getString(DbColNames.ANSWER_BODY);
                    currentAnswer.timestamp = rs.getTimestamp(DbColNames.ANSWER_TIMESTAMP);
                    currentAnswer.score = rs.getInt(DbColNames.ANSWER_SCORE);
                    
                    answers.add(currentAnswer);
                }
                
                model.put("answers", answers);
            } catch (Exception e) {
                model.put("message", "try/catch error in question() " + e.getMessage());

                return "error";
            }
        }

        if (httpRqstKeys.contains("upvote")) {
            // stmt.executeUpdate("UPDATE questions WHERE id=blah"); // DB query for upvoting
        }

        if (httpRqstKeys.contains("downvote")) {
            // stmt.executeUpdate("UPDATE questions WHERE id=blah"); // DB query for downvoting
        }

        return "question";
    }
    
    @RequestMapping("/questionedit")
    String questionedit(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString();
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        if (httpRqstKeys.contains("newdata")) {
            model.put("qid", httpRqstVarsMap.get("id"));
            
            String sql = "";
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("UPDATE Questions SET ");
                sqlBuilder.append("Title='" + httpRqstVarsMap.get("title") + "', ");
                sqlBuilder.append("Body='" + httpRqstVarsMap.get("body") + "', ");
                sqlBuilder.append("Author='" + httpRqstVarsMap.get("author") + "', ");
                sqlBuilder.append("Timestamp='now' ");
                sqlBuilder.append("WHERE Id=" + httpRqstVarsMap.get("id"));
                sql = sqlBuilder.toString();
                
                stmt.executeUpdate(sql);
            } catch (Exception e) {
                model.put("message", "try/catch error in questionedit() when updating DB; SQL was " + sql + " --- " + e.getMessage());

                return "error";
            }
        }

        if (httpRqstKeys.contains("id")) {
            model.put("qid", httpRqstVarsMap.get("id"));
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                ResultSet rs = stmt.executeQuery("SELECT * FROM Questions WHERE Id=" + httpRqstVarsMap.get("id"));

                while (rs.next()) {
                    model.put("title", rs.getString(DbColNames.QUESTION_TITLE));
                    model.put("body", rs.getString(DbColNames.QUESTION_BODY));
                }
            } catch (Exception e) {
                model.put("message", "try/catch error in questionedit() " + e.getMessage());

                return "error";
            }
        }
        
        return "questionedit";
    }
    
    @RequestMapping("/questiondelete")
    String questionDelete(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString();
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        if (httpRqstKeys.contains("id")) {
            int sqlReturnValue = -2;
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                sqlReturnValue = stmt.executeUpdate("DELETE FROM Questions WHERE Id = '" + httpRqstVarsMap.get("id") + "';");
                // TODO: handle DB errors, e.g. non-existant ID
            } catch (Exception e) {
                model.put("message", "try/catch error in questiondelete(); SQL return value is " + sqlReturnValue + " (default is -2); " + e.getMessage());

                return "error";
            }

            model.put("removedQuestionId", httpRqstVarsMap.get("id"));
        }        
        
        return "questiondelete";
    }
    
    //////////////////// Methods related to YASIAS answers

    @RequestMapping("/answer")
    String answer(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString();

        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);
        
        String questionTitle = httpRqstVarsMap.get("questionTitle");
        model.put("questionTitle", questionTitle);
        String questionId = httpRqstVarsMap.get("qid");
        model.put("questionId", questionId);
        
        if(httpRqstKeys.contains("submission")) {
            model.put("submitted", "true");
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("INSERT INTO Answers VALUES (");
                sqlBuilder.append("DEFAULT"); // auto-incrementing ID
                sqlBuilder.append(", '");
                sqlBuilder.append(currentUser); // author
                sqlBuilder.append("', '");
                sqlBuilder.append(questionId); // question ID
                sqlBuilder.append("', '");
                sqlBuilder.append(httpRqstVarsMap.get("body")); // body
                sqlBuilder.append("', ");
                sqlBuilder.append("'now', "); // timestamp
                sqlBuilder.append("0"); // score
                sqlBuilder.append(");");

                stmt.executeUpdate(sqlBuilder.toString());
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
        String queryString = request.getQueryString();
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        model.put("aid", httpRqstVarsMap.get("id"));
        model.put("qid", httpRqstVarsMap.get("qid"));

        if (httpRqstKeys.contains("newdata")) {
            model.put("submitted", "true");
            
            String sql = "";
            
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();
                
                StringBuilder sqlBuilder = new StringBuilder();
                sqlBuilder.append("UPDATE Answers SET ");
                sqlBuilder.append("Body='" + httpRqstVarsMap.get("body") + "', ");
                sqlBuilder.append("Author='" + httpRqstVarsMap.get("author") + "', ");
                sqlBuilder.append("Timestamp='now' ");
                sqlBuilder.append("WHERE Id=" + httpRqstVarsMap.get("id"));
                sql = sqlBuilder.toString();
                
                stmt.executeUpdate(sql);
            } catch (Exception e) {
                model.put("message", "try/catch error in answeredit() when updating DB; SQL was " + sql + " --- " + e.getMessage());

                return "error";
            }
        }

        if (httpRqstKeys.contains("id")) {
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                ResultSet rs = stmt.executeQuery("SELECT * FROM Answers WHERE Id=" + httpRqstVarsMap.get("id"));

                while (rs.next()) {
                    model.put("body", rs.getString(DbColNames.ANSWER_BODY));
                }
            } catch (Exception e) {
                model.put("message", "try/catch error in answeredit() " + e.getMessage());

                return "error";
            }
        }
        
        return "answeredit";
    }
    
    @RequestMapping("/answerdelete")
    String answerDelete(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString();
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        model.put("qid", httpRqstVarsMap.get("qid"));

        if (httpRqstKeys.contains("id")) {
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                stmt.executeUpdate("DELETE FROM Answers WHERE Id = '" + httpRqstVarsMap.get("id") + "';");
                // TODO: handle DB errors, e.g. non-existant ID
            } catch (Exception e) {
                model.put("message", "try/catch error in questiondelete(); " + e.getMessage());

                return "error";
            }

            model.put("removedAnswerId", httpRqstVarsMap.get("id"));
        }        
        
        return "answerdelete";
    }
    
    //////////////////// Methods related to YASIAS users

    @RequestMapping("/useradd")
    String useradd(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString(); // Ex. "upvote=true&foo=bar" (without the quotes)
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        if (httpRqstKeys.contains("newusername")) {
            int sqlReturnValue = -5;
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                sqlReturnValue = stmt.executeUpdate("INSERT INTO Users VALUES ('" + httpRqstVarsMap.get("newusername") + "');");
                // TODO: handle DB errors, e.g. duplicate name
            } catch (Exception e) {
                model.put("message", "try/catch error in useradd(); SQL return value is " + sqlReturnValue + " (default is -5); " + e.getMessage());

                return "error";
            }

            return "useradd";
        }

        return "useradd";
    }

    @RequestMapping("/userdelete")
    String userdelete(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString(); // Ex. "upvote=true&foo=bar" (without the quotes)
        Map<String, String> httpRqstVarsMap = parseHttpRequestParams(queryString);
        Set<String> httpRqstKeys = httpRqstVarsMap.keySet();

        String currentUser = getCurrentUser(httpRqstVarsMap);
        model.put("currentUser", currentUser);

        if (httpRqstKeys.contains("name")) {
            int sqlReturnValue = -3;
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                sqlReturnValue = stmt.executeUpdate("DELETE FROM Users WHERE Name = '" + httpRqstVarsMap.get("name") + "';");
                // TODO: handle DB errors, e.g. non-existant name
            } catch (Exception e) {
                model.put("message", "try/catch error in userdelete(); SQL return value is " + sqlReturnValue + " (default is -3); " + e.getMessage());

                return "error";
            }

            model.put("removedUser", httpRqstVarsMap.get("name"));
        }

        return "userdelete";
    }
    
    //////////////////// Methods related to YASIAS tags
    
    
    
    //////////////////// YASIAS helper methods

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

    private String getCurrentUser(Map<String, String> httpRqstVarsMap) {
        String currentUser = httpRqstVarsMap.get("name"); // null if there was no name param
        if(currentUser == null) {
            currentUser = "anonymous";
        }
        
        return currentUser;
    }

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
    String debug(Map<String, Object> model) {
        try {
            Connection connection = dataSource.getConnection();
            if(connection == null) {
                // Just getting rid of a warning.
            }
            
            // model.put("message", "Hello, world! dbUrl: " + dbUrl); // The first arg must be "message" because the error page uses <p th:text="${message}">
            //model.put("message", new DebugClass());
        } catch (SQLException e) {
            model.put("message", e.getMessage() + " --- dbUrl: " + dbUrl);

            return "error";
        }

        return "debug";
    }

    @RequestMapping("/db")
    String db(Map<String, Object> model) {
        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();

            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS ticks (tick timestamp)");

            stmt.executeUpdate("INSERT INTO ticks VALUES (now())");

            ResultSet rs = stmt.executeQuery("SELECT tick FROM ticks");

            ArrayList<String> output = new ArrayList<String>();
            while (rs.next()) {
                output.add("Read from DB: " + rs.getTimestamp("tick"));
            }
            model.put("records", output);

            return "db";
        } catch (Exception e) {
            model.put("message", e.getMessage());

            return "error";
        }
    }
}

/*
class DebugClass {
    private String property1 = "This is property one";
    public String property2 = "Here's the second property";

    public String getProperty1() {
        return property1;
    }

    @Override
    public String toString() {
        return "Custom toString() for DebugClass [property1=" + property1 + ", property2=" + property2 + "]";
    }
}
*/