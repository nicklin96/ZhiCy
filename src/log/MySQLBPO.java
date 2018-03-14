package log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


public class MySQLBPO {
	Connection conn;
	PreparedStatement ps = null;
	ResultSet rs = null;
	
	void init(String database) {
		try{
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			String useName = "husen";
			String password = "icstwip";
			String url = "jdbc:mysql://localhost:3306/" + database;
			conn = DriverManager.getConnection(url, useName, password);
			
			if(conn == null)
				System.out.println("Connect mysql error or no database named: "+database);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}	
	void close() {
		if(rs != null){
			try{
				rs.close();
			}catch(SQLException e){
				e.printStackTrace();
			}
		}
		if(ps != null) {
			try{
				ps.close();
			}catch(SQLException e){
				e.printStackTrace();
			}			
		}
		if(conn != null){
			try{
				conn.close();
			}catch(SQLException e){
				e.printStackTrace();
			}
		}
	}
	boolean execute(String query) {
		try {
			if (ps != null) ps.close();
			ps = conn.prepareStatement(query);
			return ps.execute();
		} catch (SQLException e) {
				// TODO Auto-generated catch block
			System.out.println("Access denied or Error query:");
			System.out.println(query);
			e.printStackTrace();
			return false;
		}
	}
	ResultSet getResult(String query) {
		try {
			if (ps != null) ps.close();
			ps = conn.prepareStatement(query);
			rs = ps.executeQuery();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return rs;
	}
}
