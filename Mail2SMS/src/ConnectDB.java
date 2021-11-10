import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/**
 * ConnectDB.java
 * 16.06.2016
 * @author Wei Tao
 * ConnectDB class
 */
public class ConnectDB {
	public final String url = "jdbc:mysql://192.168.10.42/SMSC";
	public final String user = "root";
	public final String password = "n6mxltl3";

	private Connection con;
	
	/**
	 * Create a connection to the database
	 */
	public boolean connect() {
		if (con == null) {
			try {
				con = DriverManager.getConnection(url, user, password);
				System.out.println("[DB]: Connect to the database successfully!");
				return true;
			} catch (SQLException e) {
				e.printStackTrace();
				con = null;
			}
		}
		return false;
	}
	
	/**
	 * Execute an query statement in the database
	 */
	public ResultSet query(String query) {
		if (con != null) {
			Statement stmt;
			try {
				stmt = con.createStatement();
				return stmt.executeQuery(query);
			} catch (SQLException e) {
				e.printStackTrace();
			}
		}
		return null;
	}
}