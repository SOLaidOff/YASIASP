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
import javax.servlet.http.HttpServletResponse;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
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

    @RequestMapping("/")
    String index(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString(); // Ex. "name=Harry" (without the quotes)
        Map<String, String> queryMap = parseHttpRequestParams(queryString);

        String currentUser = queryMap.get("name"); // null if there was no name param
        model.put("currentUser", currentUser);

        try (Connection connection = dataSource.getConnection()) {
            Statement stmt = connection.createStatement();

            ResultSet rs = stmt.executeQuery("SELECT Name FROM Users");

            ArrayList<String> output = new ArrayList<String>();
            while (rs.next()) {
                output.add(rs.getString("Name"));
            }
            model.put("names", output);
        } catch (Exception e) {
            model.put("message", e.getMessage());

            return "error";
        }

        return "index";
    }

    @RequestMapping("/ask")
    String ask() {
        return "ask";
    }

    @RequestMapping("/question")
    String question(Map<String, Object> model, HttpServletRequest request, HttpServletResponse response) {
        // TODO: fill in model, using question ID

        String queryString = request.getQueryString(); // Ex. "upvote=true&foo=bar" (without the quotes)
        model.put("queryString", queryString);

        // String httpVerb = request.getMethod();

        Map<String, String> queryMap = parseHttpRequestParams(queryString);
        Set<String> queryParams = queryMap.keySet();
        Collection<String> queryValues = queryMap.values();

        if (queryParams.contains("id")) {
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                ResultSet rs = stmt.executeQuery("SELECT * FROM Questions WHERE Id=" + queryMap.get("id"));

                while (rs.next()) {
                    model.put("author", rs.getString("Author"));
                    model.put("title", rs.getString("Title"));
                    model.put("body", rs.getString("Body"));
                    model.put("timestamp", rs.getString("Timestamp"));
                    model.put("score", rs.getString("Score"));
                }
            } catch (Exception e) {
                model.put("message", "try/catch error in question() " + e.getMessage());

                return "error";
            }
        }

        if (queryParams.contains("upvote")) {
            // stmt.executeUpdate("UPDATE questions WHERE id=blah"); // DB query for upvoting
        }

        return "question";
    }

    // First step: Add new RequestMapping annotation for a URL, an associated method,
    // and an .html page under src/main/resources/templates with the returned name
    // See "db" for a more complex example that accesses the DB and modifies the model and view
    @RequestMapping("/test")
    String test() {
        return "test";
    }

    @RequestMapping("/adduser")
    String adduser(Map<String, Object> model, HttpServletRequest request) {
        String queryString = request.getQueryString(); // Ex. "upvote=true&foo=bar" (without the quotes)
        Map<String, String> queryMap = parseHttpRequestParams(queryString);
        Set<String> queryParams = queryMap.keySet();

        if (queryParams.contains("newusername")) {
            int sqlReturnValue = -5;
            try (Connection connection = dataSource.getConnection()) {
                Statement stmt = connection.createStatement();

                sqlReturnValue = stmt.executeUpdate("INSERT INTO Users VALUES ('" + queryMap.get("newusername") + "');");
                // TODO: handle DB errors, e.g. duplicate name
            } catch (Exception e) {
                model.put("message", "try/catch error in adduser(); SQL return value is " + sqlReturnValue + " (default is -5); " + e.getMessage());

                return "error";
            }

            return "adduser";
        }

        return "adduser";
    }

    @RequestMapping("/debug")
    String debug(Map<String, Object> model) {
        try {
            Connection connection = dataSource.getConnection();
            if(connection == null) {
                // Just getting rid of a warning.
            }
            
            // model.put("message", "Hello, world! dbUrl: " + dbUrl); // The first arg must be "message" because the error page uses <p th:text="${message}">
            model.put("message", new DebugClass());
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
}

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
