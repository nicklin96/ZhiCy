package evaluation;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import org.json.JSONArray;
import org.json.JSONObject;

import qa.Globals;
import utils.HttpRequest;

public class StandardSparqlGeneration 
{
	File dboFile = new File(Globals.localPath + "data/DBpedia2014/parapharse/DBpedia2014_dbo_predicates.txt");
	
	File zcyFile = new File(Globals.localPath + "/data/QALD7/testin.txt");
	File jsonOutputFile = new File(Globals.localPath + "/data/QALD7/ganswer_qald7train_jsonAnswers.json");
	//File jsonOutputFile = new File("./data/ganswer_qald7test_jsonAnswers.json");
	File answersFile = new File(Globals.localPath + "/data/QALD7/ganswer_qald7train_Answers.txt");
	//File answersFile = new File("./data/ganswer_qald7test_Answers.txt");
	File sparqlsFile = new File(Globals.localPath + "/data/QALD7/ganswer_qald7train_Sparqls.txt");
	//File sparqlsFile = new File("./data/ganswer_qald7test_Sparqls.txt");
	File handwriteFile = new File(Globals.localPath + "/data/QALD7/handwriteSpqs");
	File notResponseFile = new File(Globals.localPath + "/data/QALD7/notResponse");
	
	ArrayList<QuestionResult> qrList = new  ArrayList<QuestionResult>();
	HashSet<String> dboPredicates = new HashSet<String>();
	HashSet<String> dbp_dboPredicates = new HashSet<String>();	//同名谓词有的实体要dbo有的实体有只有dbp；例如dbo:author |dbp:author
	
	HashMap<Integer, String> handwriteSpqs = new HashMap<Integer, String>();
	HashSet<Integer> outOfScopes = new HashSet<Integer>();
	HashSet<Integer> notResponses = new HashSet<Integer>();
	public HashSet<Integer> selectTop2SPQs = new HashSet<Integer>();
	public HashSet<Integer> selectTop3SPQs = new HashSet<Integer>();
	
	// notice to change this when change dataset
	public String dataset = "qald-7-test-multilingual";
	
	public StandardSparqlGeneration()
	{
		init();
	}
	
	void init()
	{
		// notice to change the "out of scope" IDs.
// qald6-test
//		outOfScopes.add(19);
//		outOfScopes.add(20);
//		outOfScopes.add(48);
//		outOfScopes.add(70);
//		outOfScopes.add(77);
		
		// notice to change the IDs which need appoint to specific SPQ of all SPQ candidates.
// qald6-test
//		selectTop2SPQs.add(16);
//		selectTop2SPQs.add(39);
//		
//		selectTop3SPQs.add(59);

// qald6-train
//		selectTop2SPQs.add(5);
		
		readDboPredicates();
		
		try 
		{	
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(handwriteFile),"utf-8"));
			String input = "";
			int id = -1;
			String spq = "";
			while((input = br.readLine())!=null)
			{
				if(input.charAt(0)>='0' && input.charAt(0)<='9')
				{
					id = Integer.parseInt(input);
				}
				else if(input.startsWith("#"))
				{
					handwriteSpqs.put(id, spq);
					id = -1;
					spq = "";
				}
				else
					spq += input + " ";
			}
			br.close();
			
			br = new BufferedReader(new InputStreamReader(new FileInputStream(notResponseFile),"utf-8"));
			while((input = br.readLine())!=null)
			{
				if(input.charAt(0)>='0' && input.charAt(0)<='9')
				{
					id = Integer.parseInt(input);
					notResponses.add(id);
				}
			}
			
			// If allow "not response"　or "handwriteSpq", then delete following two lines.
			handwriteSpqs.clear();
			notResponses.clear();
			
		} catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	void readDboPredicates()
	{
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dboFile),"utf-8"));
			String input = "";
			while((input = br.readLine())!=null)
			{
				dboPredicates.add(input);
			}
			System.out.println("DBO prediactes read complete.");
			br.close();
			
			dbp_dboPredicates.add("author");
			dbp_dboPredicates.add("leaderName");
			dbp_dboPredicates.add("date");
			dbp_dboPredicates.add("name");
			dbp_dboPredicates.add("deathCause");
			dbp_dboPredicates.add("mission");
			dbp_dboPredicates.add("governor");
			dbp_dboPredicates.add("leaderParty");
			dbp_dboPredicates.add("residence");
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	void readZhicyResults()
	{
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(zcyFile), "utf-8"));
			String input = "";
			QuestionResult qr = new QuestionResult();
			NonstandardSparql nsq = null;
			boolean spqFlag = false;
			while( (input = br.readLine())!=null )
			{
				if(input.startsWith("Id: "))
				{
					if(qr.qId >= 0)
						qrList.add(qr);
					qr = new QuestionResult();
					qr.qId = Integer.parseInt(input.substring(4));
				}
				else if(input.startsWith("Query: "))
				{
					qr.question = input.substring(7);
				}
				else if(input.startsWith("["))
				{
					nsq = new NonstandardSparql();
					spqFlag = true;
					continue;
				}
				
				if(input.equals(""))
				{
					qr.nstdSparqlList.add(nsq);
					spqFlag = false;
				}
				if(spqFlag)
				{
					nsq.triples.add(input);
				}	
			}
			qrList.add(qr);
			System.out.println("Read Complete.");
			
//			for(QuestionResult qrr: qrList)
//			{
//				for(NonstandardSparql nspq: qrr.nstdSparqlList)
//				{
//					System.out.println(nspq.triples+"\n");
//				}
//				System.out.println("\n");
//			}
			br.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	/*
	 * select ?Who where
?Who	<type>	<Person>
?films	<type>	<Film>
?films	<producer>	?Who
ORDER BY DESC(COUNT(?films))
OFFSET 0 LIMIT 1
	 * */
	public String getStandardSparqlByNSpq(NonstandardSparql nspq)
	{
		String sparql = "";
		if(nspq.triples.size() == 0)
			return null;
		if(!nspq.triples.get(0).startsWith("ask") && !nspq.triples.get(0).startsWith("select"))
			return null;
		
		sparql = nspq.triples.get(0);
		sparql += " {";
		boolean endMarket = false;
		boolean needUnionDboDbp = false;
		for(int i = 1; i<nspq.triples.size(); i++)
		{
			String str = nspq.triples.get(i);
			if(isTriple(str))
			{	
				String s = str.split("\t")[0], p = str.split("\t")[1], o = str.split("\t")[2];
				if(s.startsWith("<"))
				{
					s = s.substring(1, s.length()-1);
					if(s.contains("(")&&s.contains(")") || s.contains(","))
						s = "<http://dbpedia.org/resource/" + s + ">";
					else
						s = "dbr:" + s;
				}

				p = p.substring(1, p.length()-1);
				if(p.equals("type1") || p.equals("type"))
					p = "rdf:" + p; //p.substring(0,p.length()-1);
				else if(dboPredicates.contains(p))
				{
					if(dbp_dboPredicates.contains(p))
						needUnionDboDbp = true;
					p = "dbo:" + p;
				}
				else
					p = "dbp:" + p;
				
				if(p.startsWith("rdf:"))
				{// o is type and not yago
					o = o.substring(1, o.length()-1);
					if(!o.startsWith("yago"))
						o = "dbo:" + o;
				}
				else if(o.startsWith("<"))
				{
					o = o.substring(1, o.length()-1);
					if(o.contains("(")&&o.contains(")") || o.contains(","))
						o = "<http://dbpedia.org/resource/" + o + ">";
					else
						o = "dbr:" + o;
				}
				// drop ".(in the end)" to avoid exception when execute
				while(o.length()>0 && o.charAt(o.length()-1)=='.')
				{
					o = o.substring(0,o.length()-1);
				}
				
				String triple = s+" "+p+" "+o+".";
				if(needUnionDboDbp)
				{
					String unionTriple = s+" "+p.replace("dbo:", "dbp:")+" "+o+".";
					triple = "{"+triple+"}"+"UNION"+"{"+unionTriple+"}";
				}
		
		//Some Fixes to avoid exception when execute
				triple = triple.replace("&", "and");
				
				// drop ".(in the end)" 
//				while(triple.length()>0 && triple.charAt(triple.length()-1)=='.')
//				{
//					triple = triple.substring(0,triple.length()-1);
//				}
				
				sparql += " "+triple;
			}
			else if (str.startsWith("FILTER"))
			{
				sparql += " "+str;
			}
			else
			{
				if(!endMarket)
				{
					sparql += " }";
					endMarket = true;
				}
				if(str.startsWith("ORDER") || str.startsWith("OFFSET") || str.startsWith("GROUP") || str.startsWith("HAVING"))
					sparql += " " + str;
				else
					sparql += " "+str+".";
			}
		}
		if(!endMarket)
			sparql += " }";
		
		return sparql;
	}
	
	boolean isTriple(String str)
	{
		if(str.split("\t").length == 3 && str.split("\t")[0].length()>0 && str.split("\t")[1].length()>0
				&& str.split("\t")[2].length()>0)
			return true;
		return false;
	}
	
	public void generateStandardSparql()
	{
		for(QuestionResult qr: qrList)
		{
			if(handwriteSpqs.containsKey(qr.qId))
			{
				qr.stdSparqlList.add(handwriteSpqs.get(qr.qId));
				continue;
			}
			
			for(NonstandardSparql nspq: qr.nstdSparqlList)
			{
				String stdSpq = getStandardSparqlByNSpq(nspq);
				
				if(stdSpq == null)
					System.out.println("Sparql Error, id = "+qr.qId);
				else
					System.out.println(stdSpq);
				
				qr.stdSparqlList.add(stdSpq);
			}
			//System.out.println("");
		}
	}
	
	public String getAnswersFromVirtuoso(String sparql)
	{
		String result = "";
		try 
		{
			//发送 POST 请求
			String uSparql;
			uSparql = URLEncoder.encode(sparql,"gbk");
			result = HttpRequest.sendPost("http://dbpedia.org/sparql", "default-graph-uri=http%3A%2F%2Fdbpedia.org&query="+uSparql+"&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on");
	     
		} 
		catch (UnsupportedEncodingException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
        return result;
	}
	
	public void evaluate()
	{
		for(QuestionResult qr: qrList)
		{
			System.out.println("Solving "+qr.qId+":\n");
	
			for(String spq: qr.stdSparqlList)
			{
				String result = getAnswersFromVirtuoso(spq);
				
				//选择第一个查到结果的 spq，作为最终spq
				if(qr.firstJsonAnswer == null && !result.contains("\"bindings\": [ ]"))
					qr.firstJsonAnswer = result;
				
				qr.jsonAnswerList.add(result);
			}
		}
		System.out.println("Evaluate Complete.");
	}
	
	public String printJsonResult()
	{
		String res = "";
		try 
		{
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(jsonOutputFile),"utf-8");
			JSONObject rootJson = new JSONObject();
			JSONObject dataSetJsonObject = new JSONObject();
			JSONArray questionsJsonArray = new JSONArray();
			dataSetJsonObject.put("id", dataset);
			rootJson.put("dataset", dataSetJsonObject);
			rootJson.put("questions", questionsJsonArray);
			for(QuestionResult qr: qrList)
			{
				if(notResponses.contains(qr.qId))
					continue;
					
				JSONObject resultJsonObject = new JSONObject();
				JSONArray answersJsonArray = new JSONArray();
				questionsJsonArray.put(resultJsonObject);
				
				// QALD7's id is string and start from 0
				String qIdStr = String.valueOf(qr.qId);
				
				resultJsonObject.put("id", qIdStr);
				//resultJsonObject.put("id", qr.qId);
				
				resultJsonObject.put("answers", answersJsonArray);
				
				String selectedAnswerStr = qr.getSelectedJsonAnswer(selectTop2SPQs, selectTop3SPQs);
				if(outOfScopes.contains(qr.qId) || selectedAnswerStr==null || selectedAnswerStr.equals(""))
					continue;
				
				JSONObject answerJsonObject = new JSONObject(selectedAnswerStr);
				
				// QALD7 need drop some properties
				JSONObject tmpJsonObject = answerJsonObject.getJSONObject("results");
				tmpJsonObject.remove("ordered");
				tmpJsonObject.remove("distinct");
				
				answersJsonArray.put(answerJsonObject);
			}
			
			res = rootJson.toString();
			fw.write(rootJson.toString());
			fw.close();
		} 
		catch (Exception e) {
			e.printStackTrace();
			// TODO: handle exception
		}
		
		return res;
	}
	
	public String getSimplyAnswersFromJsonObject(String rootStr)
	{
		String results = "";
        try 
        {	
			JSONObject rootJson = new JSONObject(rootStr);
			if(rootJson.has("boolean"))
			{
				results = rootJson.getString("boolean") + "\n";
			}
			else
			{
			    JSONObject resultsJson = rootJson.getJSONObject("results");
			    JSONArray bindingsJsonArray = resultsJson.getJSONArray("bindings");
			    for(int i=0; i<bindingsJsonArray.length(); i++)
			    {
			    	JSONObject answer = (JSONObject) bindingsJsonArray.get(i);  
			        results += answer.toString() + "\n"; 
			    }
			}
        } 
        catch (Exception e) {
			// TODO: handle exception
		}
        
        return results;
	}
	
	public void printAnswers()
	{
		try 
		{
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(answersFile),"utf-8");
			
			for(QuestionResult qr: qrList)
			{
				fw.write("qId: "+qr.qId+"\n");
				int cnt=0;
				for(String str: qr.jsonAnswerList)
				{
					fw.write("["+(++cnt)+"]\n");
					String result = getSimplyAnswersFromJsonObject(str);
					if(result.equals(""))
						fw.write("null\n");
					else
						fw.write(result);
				}
				fw.write("\n");
			}
			
			fw.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	public void printSparqls()
	{
		try 
		{
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(sparqlsFile),"utf-8");
			for(QuestionResult qr: qrList)
			{
				fw.write("qId: "+qr.qId+"\n");
				int cnt = 0;
				for(String spq: qr.stdSparqlList)
				{
					fw.write("["+(++cnt)+"]\n");
					fw.write(spq+"\n");
				}
				fw.write("\n");
			}
			
			fw.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	public static void main(String[] args) 
	{
		/*
		 * 运行前注意：
		 * 1、修改文件路径
		 * 2、注意 handwrite、outOfscope、noResponse 的使用与否。
		 * 3、注意修改 QuestionResult->getSelectedJsonAnswer()中，对于一些固定ID的选择策略。
		 * */
		
		StandardSparqlGeneration ssg = new StandardSparqlGeneration();
		
		ssg.readZhicyResults();
		ssg.generateStandardSparql();
		ssg.printSparqls();
		ssg.evaluate();
		ssg.printAnswers();
		ssg.printJsonResult();
	}
}
