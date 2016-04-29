package log;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;


/**
 * MySQL access
 * @author Yi Feng
 *
 */

public class MySQLBPO {
	Connection conn;
	PreparedStatement ps = null;
	ResultSet rs = null;
	
	void init(String database) {
		try{
			Class.forName("com.mysql.jdbc.Driver").newInstance();
			String useName = "root";
			String password = "icstwip";
			//String url = "jdbc:mysql://172.31.222.76:3307/" + database;
			//String url = "jdbc:mysql://172.31.222.77:3306/" + database;
			String url = "jdbc:mysql://localhost:3306/" + database;
			conn = DriverManager.getConnection(url, useName, password);
		}
		catch(Exception e) {
			e.printStackTrace();
		}
	}	
	void close() {
		if(rs != null){ // �رռ�¼��
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
		if(conn != null){ // �ر����Ӷ���
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
			System.out.println(query);
			e.printStackTrace();
		}
		return false;
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