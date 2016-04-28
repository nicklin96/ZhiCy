package log;

public class TestMyJDBC {

	
	public void testBPO() {
		MySQLBPO db = new MySQLBPO();
		//db.init("huangruizhe");
		db.init("ganswerlog");
		String query = "show tables";
		System.out.println(db.getResult(query));
	}
	
	public void testLog() {
		LogWriter log = new LogWriter();
		//log.insertLog("aaaa", "bbbb", "sss", 5, 0, "234");
		
		log.insertLog("0.0.0.1", "a question", "a sparql", 1, 0, 0);
	}
	
	public static void main(String[] args) {
		
		new TestMyJDBC().testLog();
		
	}
	    
};
