package reaperbot;

import arc.util.Log;

import java.sql.*;
import java.time.LocalDateTime;

import static reaperbot.ReaperBot.config;

public class Database {
    private Connection con;

    public Database(){
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(config.get("db-address"), config.get("db-username"), config.get("db-password"));
            Log.info("Connecting to database...");
            init();
        }catch (SQLException | ClassNotFoundException e){
            Log.err(e);
        }
    }

    public void init(){

    }

    public Connection getCon() {
        return con;
    }
}
