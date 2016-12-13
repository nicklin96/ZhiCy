package log;

public class LogWriter {
	private MySQLBPO db;
	
	public LogWriter() {
		db = new MySQLBPO();
		//db.init("test");
		db.init("GAnswerLog");
	}
	
	public boolean insertLog(String user_ip,
			String question,			
			String sparql,
			int answerCount,
			int errorNum,
			int userFeedback			
			) {
		/*
		  	insert into huangruizhe.ganswerlog (NL_question, sparql, user_feedback, error_num, user_ip, time) 
			values ('Who is the daughter of Bill Clinton married to?',
        	'?daughter <child> Bill_Clinton',
        	5,
        	0,
        	123,
        	now());
		 */
		StringBuilder query = new StringBuilder("insert into GAnswerLog.queryLog (user_ip,question, sparql, answer_count, error_num, user_feedback, time) values (");
		
		query.append('\'');
		query.append(user_ip);
		query.append("\',");
		
		query.append('\'');
		query.append(question);
		query.append("\',");
		
		query.append('\'');
		query.append(sparql);
		query.append("\',");

		query.append('\'');
		query.append(answerCount);
		query.append("\',");
		
		query.append('\'');
		query.append(errorNum);
		query.append("\',");
		
		query.append('\'');
		query.append(userFeedback);
		query.append("\',");

		//query.append("INET_ATON(\'");
		//query.append(user_ip);
		//query.append("\'),");
		
		query.append("now());");

		return db.execute(query.toString());		
	}
	
	public void close() {
		db.close();
	}
}
