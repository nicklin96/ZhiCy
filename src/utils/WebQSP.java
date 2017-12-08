package utils;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.json.JSONArray;
import org.json.JSONObject;


public class WebQSP {
	
	public static String dataPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/WebQSP.test.json";
	public static String mainSpqPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/WebQSP.test.spqs.txt";
	public static String mannualSpqPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/WebQSP.test.spqs-mannual.txt";
	public static String compleSpqPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/WebQSP.test.spqs-complex.txt";
	public static String complexDataPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/WebQSP.test.complex.json";
	public static String qdecomposedPath = "D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/Test/test.questions.decomposed";
	public static String qUndecomposedPath = "D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/Test/test.questions.Undecomposed";
	public static String typeOutputPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/types.collection";
	public static String typeListPath = "D:/Documents/husen/Data/WebQSP/WebQSP/data/types.list";
	public static String entOutputPath = "D:/Documents/husen/Java/GAnswerOverFreebase/resources/Test/ent2id.txt";
	public static String relOutputPath = "D:/Documents/husen/Java/GAnswerOverFreebase/resources/Test/rel2id.txt";
	public static String relInputPath = "D:/Documents/husen/Java/GAnswerOverFreebase/resources/RE/classLabels.txt";
	public static String[] testELPaths = {"D:/Documents/husen/Java/GAnswerOverFreebase/resources/Test/test.EL.results", "D:/Documents/husen/Java/GAnswerOverFreebase/resources/Train/train.EL.results"};
	
	public static String dataStr = "";
	public static HashMap<String, List<String>> question_decomposed = new HashMap<>();
	public static HashSet<String> entDict = new HashSet<>();
	
	public void getRelationsList()
	{
		try 
		{
            List<String> lines = FileUtil.readFile(relInputPath);			
			ArrayList<String> output = new ArrayList<>();
			int id = 0;
			for(String line: lines)
			{
				if(line.length() > 1)
					output.add(line + "\t" + id++);
			}
			FileUtil.writeFile(output, relOutputPath);
			
		} 
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	public void getEntitiesList()
	{
		try 
		{
			for(int index = 0; index < testELPaths.length; index++)
			{
                List<String> lines = FileUtil.readFile(testELPaths[index]);
                for(String line:lines)
                {
                    JSONObject object = new JSONObject(line);
                    String question = object.get("question").toString();
                    HashMap<String, List<String>> EL_results = new HashMap<String, List<String>>();

                    JSONArray array = (JSONArray) object.get("mentions");
                    for(int i = 0; i < array.length(); i++){
                        JSONObject mention = (JSONObject) array.get(i);
                        Iterator<String> keyIt = mention.keys();
                        while(keyIt.hasNext())
                        {
                        	String key = keyIt.next();  
                        	List<String> cands = new ArrayList<String>();
                        	for(int j=0; j<mention.getJSONArray(key).length(); j++)
                        	{
                        		cands.add(mention.getJSONArray(key).getString(j));
                        	}
                            EL_results.put((String) key, cands);
                            
                            for(String cand: cands)
                            {
                            	String ent = cand.split("&")[0];
                            	entDict.add(ent);
                            }
                        }
                    }
                }
            }
			
			ArrayList<String> output = new ArrayList<>();
			int id = 0;
			for(String ent: entDict)
			{
				if(ent.length() > 1)
					output.add(ent + "\t" + id++);
			}
			FileUtil.writeFile(output, entOutputPath);
			
		} 
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	public void readQuestionDecomposed()
	{
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(qdecomposedPath), "utf-8"));
			String input = "";
			while( (input=br.readLine())!=null )
			{
				JSONObject object = new JSONObject(input);
                String question = object.get("question").toString();
                JSONArray decomposedArr = object.getJSONArray("decomposed");
                List<String> decomposed = new ArrayList<String>();
                for(int i=0;i<decomposedArr.length();i++)
                {
                	String tmp = decomposedArr.getString(i);
                	decomposed.add(tmp);
                }
                
                question_decomposed.put(question, decomposed);
			}        
			br.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	public void readData()
	{
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(dataPath), "utf-8"));
			StringBuilder rr = new StringBuilder();
			String input = "";
			while( (input=br.readLine())!=null )
			{
				rr.append(input);
			}
			dataStr = rr.toString();
			br.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
	}
	
	/*
	 * Get questions who has "Constraints".
	 * */
	public JSONObject getMultiNodesQuestions()
	{
		if(dataStr == "")
			readData();
		
		try 
		{
			JSONObject rootJson = new JSONObject(dataStr);
			JSONArray questionsJsonArray = rootJson.getJSONArray("Questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				JSONArray questionsParseArray = questionsJson.getJSONArray("Parses");
				boolean flag = false;
				for(int j=0; j<questionsParseArray.length(); j++)
				{
					JSONObject parse = questionsParseArray.getJSONObject(j);
					JSONArray constraintArray = parse.getJSONArray("Constraints");
					if(constraintArray != null && constraintArray.length() > 0)
						flag = true;
				}
				
				if(!flag)
				{
					questionsJsonArray.remove(i);
					i--;
				}
			}
		
//			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(complexDataPath), "utf-8");
//			fw.write(rootJson.toString());
//			fw.close();
			
			return rootJson;
		}
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}

		return null;
	}
	
	/*
	 * Get Sparql Triples and drop the complex prefix and aggregation.
	 * */
	public JSONObject getSparqlTriples()
	{
		if(dataStr == "")
			readData();
		
		ArrayList<String> outputs = new ArrayList<>();
		ArrayList<String> manualSpqQids = new ArrayList<>();
		try 
		{
			JSONObject rootJson = new JSONObject(dataStr);
			JSONArray questionsJsonArray = rootJson.getJSONArray("Questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				JSONArray questionsParseArray = questionsJson.getJSONArray("Parses");
				String qId = questionsJson.getString("QuestionId");
				for(int j=0; j<questionsParseArray.length(); j++)
				{
					JSONObject parse = questionsParseArray.getJSONObject(j);
					String spqStr = parse.getString("Sparql");
					String topicMention = parse.getString("PotentialTopicEntityMention");
					String topicEntity = parse.getString("TopicEntityName");
					String topicId = parse.getString("TopicEntityMid");

					if(spqStr != null && spqStr.length() > 0)
					{
						if(spqStr.contains("#MANUAL SPARQL"))
						{
							manualSpqQids.add(qId);
							continue;
						}
						
						outputs.add(qId);	
						Pattern pattern = Pattern.compile("(.+?) (.+?) (.+?) \\.\n");
						Matcher matcher = pattern.matcher(spqStr);
						while (matcher.find())
						{
							String s = matcher.group(1);
							String p = matcher.group(2);
							String o = matcher.group(3);
							System.out.println(s + "\t" + p + "\t" + o + "\n");
							outputs.add(s + "\t" + p + "\t" + o + " .");
						}
//						outputs.add(topicMention + "\t" + topicId + "\t" + topicEntity);
					}
					outputs.add("");
				}
			}
		
			FileUtil.writeFile(outputs, mainSpqPath);
			FileUtil.writeFile(manualSpqQids, mannualSpqPath);
			return rootJson;
		}
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}

		return null;
	}
	
	/*
	 * Types:
	 * 1:	e p ?x
	 * 2:	e1 p ?x.	?x p e2
	 * 3:	e  p ?y.	?y p ?x
	 * 4:	e1 p ?y.	?y p ?x.	?y p e2
	 * 5:	e1 p ?y.	?y p ?x.	?x p e2
	 * 0:	others / errors
	 * */
	public int getSpqType(ArrayList<String> triples)
	{
		int type = 0;
		if(triples.size() == 1)
		{
			String[] ts = triples.get(0).split("\t");
			String s = ts[0], p = ts[1], o = ts[2];
			if(s.startsWith("ns:") && o.startsWith("?x"))
				type = 1;
		}
		else if(triples.size() == 2)
		{
			String[] ts1 = triples.get(0).split("\t"), ts2 = triples.get(1).split("\t");
			String s1 = ts1[0], p1 = ts1[1], o1 = ts1[2];
			String s2 = ts2[0], p2 = ts2[1], o2 = ts2[2];
			if(s1.startsWith("ns:") && o1.startsWith("?x") && s2.startsWith("?x") && o2.startsWith("ns:"))
				type = 2;
			else if(s1.startsWith("ns:") && o1.startsWith("?y") && s2.startsWith("?y") && o2.startsWith("?x"))
				type = 3;
		}
		else if(triples.size() == 3)
		{
			String[] ts1 = triples.get(0).split("\t"), ts2 = triples.get(1).split("\t"), ts3 = triples.get(2).split("\t");
			String s1 = ts1[0], p1 = ts1[1], o1 = ts1[2];
			String s2 = ts2[0], p2 = ts2[1], o2 = ts2[2];
			String s3 = ts3[0], p3 = ts3[1], o3 = ts3[2];
			if(s1.startsWith("ns:") && o1.startsWith("?y") && s2.startsWith("?y") && o2.startsWith("?x"))
			{
				if(s3.startsWith("?y") && o3.startsWith("ns:"))
					type = 4;
				else if(s3.startsWith("?x") && o3.startsWith("ns:"))
					type = 5;
			}
		}
		
		return type;
	}
	
	public void SparqlCluster()
	{
		ArrayList<String> outputs = new ArrayList<String>();
		try{
			List<String> lines = FileUtil.readFile(mainSpqPath);
			String qId = "";
			ArrayList<String> triples = new ArrayList<>();
			int[] typeCnt = {0,0,0,0,0,0,0};
			int cnt = 0;
			for(int i=0; i<lines.size(); i++)
			{
				String line = lines.get(i);
				if(line.startsWith("WebQ"))
				{
					qId = line;
				}
				else if(line.length() > 3)
				{
					triples.add(line);
				}
				else
				{
					cnt++;
					int spqType = getSpqType(triples);
					typeCnt[spqType]++;
					if(spqType == 0)
					{
						outputs.add(qId);
						for(String triple: triples)
							outputs.add(triple);
						outputs.add("");
					}
					
					triples = new ArrayList<>();
				}
			}
			System.out.println("All: "+cnt);
			for(int i=0;i<6;i++)
			{
				System.out.println(i + ": " + typeCnt[i]);
			}
			
			FileUtil.writeFile(outputs, compleSpqPath);
		}
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	/*
	 * Get "types" train/test data.
	 * */
	public void collectTypes()
	{
		if(dataStr == "")
			readData();
		
		ArrayList<String> output = new ArrayList<String>();
		ArrayList<String> typeList = new ArrayList<String>();
		try 
		{
			JSONObject rootJson = new JSONObject(dataStr);
			JSONArray questionsJsonArray = rootJson.getJSONArray("Questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				JSONArray questionsParseArray = questionsJson.getJSONArray("Parses");
				String question = questionsJson.getString("RawQuestion");
				
				for(int j=0; j<questionsParseArray.length(); j++)
				{
					JSONObject parse = questionsParseArray.getJSONObject(j);
					JSONArray constraintArray = parse.getJSONArray("Constraints");
					if(constraintArray != null && constraintArray.length() > 0)
					{
						for(int k=0; k<constraintArray.length(); k++)
						{
							JSONObject constraint = constraintArray.getJSONObject(k);
							String predicate = constraint.getString("NodePredicate");
							if(predicate.equals("common.topic.notable_types"))
							{
								String typeStr = constraint.getString("EntityName");
								String tmp = constraint.getString("Argument") + "\t" + typeStr + "\t" + question;
								output.add(tmp);
								if(!typeList.contains(typeStr))
									typeList.add(typeStr);
							}
						}
					}
				}
			}
			
			FileUtil.writeFile(output, typeOutputPath);
			FileUtil.writeFile(typeList, typeListPath);
			
		}
		catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
	}
	
	public void findUndecomposedComplexQuestions()
	{
		readQuestionDecomposed();
		JSONObject rootJson = getMultiNodesQuestions();
		ArrayList<String> undecList = new ArrayList<String>();
		
		try 
		{
			JSONArray questionsJsonArray = rootJson.getJSONArray("Questions");
			for(int i=0; i<questionsJsonArray.length(); i++)
			{
				JSONObject questionsJson = (JSONObject) questionsJsonArray.get(i);
				// JSONObject questionId = questionsJson.getJSONObject("QuestionId");
				// int id = Integer.valueOf(questionId.toString().split("-")[1]);
				String question = questionsJson.getString("RawQuestion");
				
				if(!question_decomposed.containsKey(question))
				{
					System.err.println("Can not find question: " + question);
				}
				else
				{
					if(question_decomposed.get(question).size() < 2)
						undecList.add(question);
				}
			}
			
			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(qUndecomposedPath), "utf-8");
			for(String str: undecList)
			{
				fw.write(str + "\n");
			}
			fw.close();
		} 
		catch (Exception e) {
			// TODO: handle exception
		}
		
	}
	
	public static void main(String[] args) 
	{
		WebQSP webQSP = new WebQSP();
		webQSP.SparqlCluster();
	}

}
