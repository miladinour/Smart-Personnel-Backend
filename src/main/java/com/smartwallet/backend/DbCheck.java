
package com.smartwallet.backend;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

public class DbCheck {
    public static void main(String[] args) {
        String url = "jdbc:postgresql://localhost:5432/smartwallet";
        String user = "postgres";
        String password = "04072004";

        try (Connection conn = DriverManager.getConnection(url, user, password);
             Statement stmt = conn.createStatement()) {
            
            System.out.println("--- ALL CATEGORIES ---");
            ResultSet rs = stmt.executeQuery("SELECT id, nom, type, user_id FROM categories");
            while (rs.next()) {
                System.out.println(rs.getInt("id") + " | " + rs.getString("nom") + " | " + rs.getString("type") + " | UserID: " + rs.getObject("user_id"));
            }

            System.out.println("\n--- RECENT TRANSACTIONS ---");
            rs = stmt.executeQuery("SELECT id, description, transaction_type, categorie_id FROM transactions ORDER BY id DESC LIMIT 5");
            while (rs.next()) {
                System.out.println(rs.getInt("id") + " | " + rs.getString("description") + " | " + rs.getString("transaction_type") + " | CatID: " + rs.getObject("categorie_id"));
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
