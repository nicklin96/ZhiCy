package evaluation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;

import log.QueryLogger;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import qa.Globals;
import qa.GAnswer;
import rdf.Sparql;

public class QaldJsonDataParser 
{
	public static String NOAnswerJSON = "{\"questions\":[{\"answers\":[{\"head\":{\"vars\":[\"uri\"]},\"results\":{\"bindings\":[]}}],\"id\":\"0\"}],\"dataset\":{\"id\":\"qald-7-test-multilingual\"}}";
	
	/*
	 * "id":1 -> "id":"1"
	 * */
	public void fixQald6trainJsonFormat()
	{
		File inputFile = new File("D:\\Documents\\husen\\Java\\DBpediaSparqlEvaluation\\data\\DBpedia2014_qald6train_beta2_jsonAnswers.json");
		File outputFile = new File("D:\\Documents\\husen\\Java\\DBpediaSparqlEvaluation\\data\\DBpedia2014_qald6train_beta2_jsonAnswers_fixFormat.json");		
		String rootStr = "";
		StringBuilder rr = new StringBuilder();
		BufferedReader br = null;
		OutputStreamWriter fw = null;
		try 
		{
			br = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile), "utf-8"));
			fw = new OutputStreamWriter(new FileOutputStream(outputFile), "utf-8");
			String input = "";
			while( (input=br.readLine())!=null )
			{
				rr.append(input);
			}
			rootStr = rr.toString();
			JSONObject rootJson = new JSONObject(rootStr);
			JSONArray questionsJsonArray = rootJson.getJSONArray("questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				int id = Integer.parseInt(questionsJson.getString("id"));
				
				questionsJson.put("id", String.valueOf(id));
			}
			fw.write(rootJson.toString());
			
			br.close();
			fw.close();
		} 
		catch (IOException e) {
			// TODO: handle exception
		} catch (JSONException e) {
			// TODO Auto-generated catch block
			System.err.println("Something wrong with JSON Object");
		}
		finally {
			try {
				if(br!=null)
					br.close();
			}catch(Exception e) {
				e.printStackTrace();
			}
			try {
				if(fw!=null)
					fw.close();
			}catch(Exception e) {
				e.printStackTrace();
			}
		}
	}
	
	public HashMap<Integer, String> parseQALDdata(String rootStr)
	{
		HashMap<Integer, String> qId_questions = new HashMap<Integer, String>();
		
		try 
		{
			JSONObject rootJson = new JSONObject(rootStr);
			JSONObject dataset = rootJson.getJSONObject("dataset");
			String datasetId = dataset.getString("id");
			qId_questions.put(-1, datasetId);
			
			JSONArray questionsJsonArray = rootJson.getJSONArray("questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				int id = Integer.parseInt(questionsJson.getString("id"));
				
				JSONObject tmp = (JSONObject)questionsJson.getJSONArray("question").get(0);
				String question = tmp.getString("string");
				qId_questions.put(id, question);
			}
		} 
		catch (Exception e) {
			System.err.println("Input data is not JSON format!");
		}
		
		return qId_questions;
	}
	
	/*
	 * Call ganswer to run the QALD input (JSON data), get answers and return with QALD format JSON file.
	 * */
	public String runQALDdata(String inputData)
	{
		String res = "";
		GAnswer ga = new GAnswer();
		StandardSparqlGeneration ssg = new StandardSparqlGeneration();
		
		HashMap<Integer, String> qId_questions = parseQALDdata(inputData);
		if(qId_questions.containsKey(-1))
			ssg.dataset = qId_questions.get(-1);
		
		for(int id: qId_questions.keySet())
		{
			if(id < 0)
				continue;
			
			String question = qId_questions.get(id);
			QueryLogger qlog = ga.getSparqlList(question);
			QuestionResult qr = new QuestionResult();
			qr.qId = id;
			qr.question = question;
			
			if(qlog.rankedSparqls.size() == 0)
				continue;
			
			int cnt = 0;
			ArrayList<String> lastSpqList = new ArrayList<String>();	//简单去一下重
			for(int j=qlog.rankedSparqls.size()-1; j>=0; j--)
			{
				Sparql spq = qlog.rankedSparqls.get(j);
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, spq);
				if(!lastSpqList.contains(stdSPQwoPrefix))
				{
					lastSpqList.add(stdSPQwoPrefix);
					NonstandardSparql nsq = new NonstandardSparql();
					for(String str: stdSPQwoPrefix.split("\n"))
						nsq.triples.add(str);
					qr.nstdSparqlList.add(nsq);
					cnt++;
				}
				
				if(cnt >= 3)
					break;
			}
			
			//因为经常出现无用type导致查询不到结果(如 <type> <yago:Wife>)，追加一个untyped SPQ
			Sparql untypedSparql = ga.getUntypedSparql(qlog.rankedSparqls.get(qlog.rankedSparqls.size()-1));
			if(untypedSparql != null)
			{
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
				if(!lastSpqList.contains(stdSPQwoPrefix))
				{
					NonstandardSparql nsq = new NonstandardSparql();
					for(String str: stdSPQwoPrefix.split("\n"))
						nsq.triples.add(str);
					qr.nstdSparqlList.add(nsq);
				}
			}
			
			ssg.qrList.add(qr);
		}
		
		ssg.generateStandardSparql();
		ssg.printSparqls();
		ssg.evaluate();
		ssg.printAnswers();
		res = ssg.printJsonResult();
		
		return res;
	}
	
	/*
	 * Call ganswer to run the QALD input (a sentence), get answers and return with QALD format JSON file.
	 * question (id, query, answers)
	 * */
	public String runQALDdataBySentence(String inputData)
	{
		String res = "";
		GAnswer ga = new GAnswer();
		StandardSparqlGeneration ssg = new StandardSparqlGeneration();
		
		String question = inputData;
		QueryLogger qlog = ga.getSparqlList(question);
		QuestionResult qr = new QuestionResult();
		qr.qId = 0;	// default
		qr.question = question;
		
		if(qlog == null || qlog.rankedSparqls.size() == 0)
		{
//			ssg.generateStandardSparql();
//			ssg.printSparqls();
//			ssg.evaluate();
//			ssg.printAnswers();
			
			return NOAnswerJSON;
		}
		
		int cnt = 0;
		ArrayList<String> lastSpqList = new ArrayList<String>();	//简单去一下重
		for(int j=qlog.rankedSparqls.size()-1; j>=0; j--)
		{
			Sparql spq = qlog.rankedSparqls.get(j);
			String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, spq);
			if(!lastSpqList.contains(stdSPQwoPrefix))
			{
				lastSpqList.add(stdSPQwoPrefix);
				NonstandardSparql nsq = new NonstandardSparql();
				for(String str: stdSPQwoPrefix.split("\n"))
					nsq.triples.add(str);
				qr.nstdSparqlList.add(nsq);
				cnt++;
			}
			
			if(cnt >= 3)
				break;
		}
		
		//因为经常出现无用type导致查询不到结果(如 <type> <yago:Wife>)，追加一个untyped SPQ
		Sparql untypedSparql = ga.getUntypedSparql(qlog.rankedSparqls.get(qlog.rankedSparqls.size()-1));
		if(untypedSparql != null)
		{
			String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
			if(!lastSpqList.contains(stdSPQwoPrefix))
			{
				NonstandardSparql nsq = new NonstandardSparql();
				for(String str: stdSPQwoPrefix.split("\n"))
					nsq.triples.add(str);
				qr.nstdSparqlList.add(nsq);
			}
		}
		
		ssg.qrList.add(qr);
		
		ssg.generateStandardSparql();
//		ssg.printSparqls();
		ssg.evaluate();
//		ssg.printAnswers();
		res = ssg.printJsonResult();
		
		if(res.length() <= 10)
			res = NOAnswerJSON;
		
		return res;
	}
	
	
	public static void main(String[] args) {
		
		GAnswer.init();
		QaldJsonDataParser parser = new QaldJsonDataParser();
		//StringBuilder sb = new StringBuilder();
		try 
		{
//			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(new File(Globals.localPath + "/data/QALD7/testin.json")), "utf-8"));
			String input = "";
//			while((input = br.readLine()) != null)
//			{
//				sb.append(input);
//			}
//			String jsonInput = sb.toString();
//			String jsonOutput = parser.runQALDdata(jsonInput);
			
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while((input = br.readLine()) != null)
			{
				String jsonOutput = parser.runQALDdataBySentence(input);
				System.out.println("Send:\n" + jsonOutput);
			}
//			String jsonOutput = parser.runQALDdataBySentence("Which building after the Burj Khalifa has the most floors?");
//			System.out.println("Send:\n" + jsonOutput);
//			jsonOutput = parser.runQALDdataBySentence("What is Donald Trump's main business?");
//			System.out.println("Send:\n" + jsonOutput);
			
//			br.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
	}
}
