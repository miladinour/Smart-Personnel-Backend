package com.smartwallet.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public class TestConn {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://db:5432/smartwallet";
        String user = "postgres";
        String password = "04072004";
        try (Connection conn = DriverManager.getConnection(url, user, password)) {
            System.out.println("Connection successful!");
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
