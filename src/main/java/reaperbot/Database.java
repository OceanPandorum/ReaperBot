package reaperbot;

import arc.util.Log;

import java.sql.*;
import java.time.LocalDateTime;

import static reaperbot.ReaperBot.config;

public class Database {
    private Connection con;

    public void connect(){
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
            con = DriverManager.getConnection(config.get("db-address", ""), config.get("db-username", ""), config.get("db-password", ""));
            Log.info("The database connection is made.");
        }catch (SQLException | ClassNotFoundException e){
            Log.err(e);
        }
    }

    public void init(){
        try {
            getCon().createStatement().execute(
                "CREATE SCHEMA IF NOT EXISTS DISCORD;"
            );
            getCon().createStatement().execute(
                "CREATE TABLE IF NOT EXISTS DISCORD.WARNINGS (USER_ID LONG, WARNS_COUNT INT(11), MUTE_END_DATE VARCHAR(20));"
            );
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public Connection getCon() {
        return con;
    }

    private String muteDate(){
        return String.format("%s-%s-%s", LocalDateTime.now().getDayOfMonth(), LocalDateTime.now().getHour() + 6, LocalDateTime.now().getMinute());
    }

    public void addWarn(long id){
        try {
            Statement statement = getCon().createStatement();

            int warns = getWarns(id);
            statement.executeUpdate("INSERT INTO DISCORD.WARNINGS (ID, WARNS, MUTE_END_DATE) " +
                    "SELECT " + id + ", " + warns + ", '' FROM DUAL " +
                    "WHERE NOT EXISTS (SELECT ID FROM DISCORD.WARNINGS WHERE ID=" + id + " AND WARNS=" + warns + ");");

            if (getWarns(id) >= 2) {
                statement.executeUpdate("UPDATE DISCORD.WARNINGS SET WARNS=" + (warns + 1) + " WHERE ID=" + id + ";");
                statement.executeUpdate("UPDATE DISCORD.WARNINGS SET MUTE_END_DATE='" + muteDate() + "' WHERE ID=" + id + ";");
            } else {
                statement.executeUpdate("UPDATE DISCORD.WARNINGS SET WARNS=" + (warns + 1) + " WHERE ID=" + id + ";");
            }
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public void removeWarns(long id, int count){
        try {
            Statement statement = getCon().createStatement();
            int warns = getWarns(id) - count;

            statement.executeUpdate("UPDATE DISCORD.WARNINGS SET WARNS=" + warns + " WHERE ID=" + id + ";");
        } catch (SQLException e) {
            Log.err(e);
        }
    }

    public int getWarns(long id){
        try {
            int warns = 0;
            Statement statement = getCon().createStatement();
            ResultSet resultSet = statement.executeQuery("SELECT WARNS FROM DISCORD.WARNINGS WHERE ID=" + id);

            while (resultSet.next()) {
                warns = resultSet.getInt(1);
            }

            return warns;
        } catch (SQLException e) {
            Log.err(e);
            return 0;
        }
    }
}
