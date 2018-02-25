package com.example;

import java.io.IOException;
import java.io.PrintWriter;

import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;

/*
 * Servlet implementation class FileCounter
 * 
 * Placeholder code from tutorial at http://www.vogella.com/tutorials/EclipseWTP/article.html
 */
@WebServlet("/VogTut") // URL for accessing servlet goes here
public class VogTutMain extends HttpServlet {
    private static final long serialVersionUID = 1L;

    int count;
    private VogTutFileDao dao;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
        // Set a cookie for the user, so that the counter does not increase EVERY time the user presses refresh
        HttpSession session = request.getSession(true);
        // Set the session valid for 5 secs
        session.setMaxInactiveInterval(5);
        response.setContentType("text/plain");
        PrintWriter out = response.getWriter();
        if (session.isNew()) {
            count++;
        }
        out.println("This site has been accessed " + count + " times.");
        out.println();
        out.println("This servlet based on the Vogella Eclipse WTP tutorial at http://www.vogella.com/tutorials/EclipseWTP/article.html.");
    }

    @Override
    public void init() throws ServletException {
        dao = new VogTutFileDao();
        try {
            count = dao.getCount();
        } catch (Exception e) {
            getServletContext().log("An exception occurred in FileCounter", e);
            throw new ServletException("An exception occurred in FileCounter" + e.getMessage());
        }
    }

    public void destroy() {
        super.destroy();
        try {
            dao.save(count);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
