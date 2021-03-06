package qa;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;

import jgsc.GstoreConnector;
import log.QueryLogger;
import nlp.ds.Sentence;
import nlp.ds.Sentence.SentenceType;
import qa.parsing.QuestionParsing;
import qa.parsing.BuildQueryGraph;
import rdf.Sparql;
import utils.FileUtil;
import addition.AddtionalFix;
import qa.Globals;

public class GAnswer {
	
	public static final int MAX_SPQ_NUM = 3;
	
	public static void init() {
		System.out.println("gAnswer2 init ...");
		
		Globals.init();
		
		System.out.println("gAnswer2 init ... ok!");
	}
	
	public QueryLogger getSparqlList(String input) 
	{
		QueryLogger qlog = null;
		try 
		{
			if (input.length() <= 5)
				return null;
			
			System.out.println("[Input:] "+input);
			
			// step 0: Node (entity & type & literal) Recognition 
			long t0 = System.currentTimeMillis(), t, NRtime;
			Query query = new Query(input);
			qlog = new QueryLogger(query);
			ArrayList<Sparql> rankedSparqls = new ArrayList<Sparql>();
			NRtime = (int)(System.currentTimeMillis()-t0);
			System.out.println("step0 [Node Recognition] : "+ NRtime +"ms");	
			
			// Try to solve each NR plan, and combine the ranked SPARQLs.
			// We only reserve LOG of BEST NR plan for convenience.
			for(int i=query.sList.size()-1; i>=0; i--)
			{
				Sentence possibleSentence = query.sList.get(i);
				qlog.reloadSentence(possibleSentence);
//				qlog.isMaltParserUsed = true;
				
				// LOG
				System.out.println("transQ: "+qlog.s.plainText);
				qlog.NRlog = query.preLog;
				qlog.SQGlog = "Id: "+query.queryId+"\nQuery: "+query.NLQuestion+"\n";
				qlog.SQGlog += qlog.NRlog;
				qlog.timeTable.put("step0", (int)NRtime);
				
				// step 1: question parsing (dependency tree, sentence type)
				t = System.currentTimeMillis();
				QuestionParsing step1 = new QuestionParsing();
				step1.process(qlog);
				qlog.timeTable.put("step1", (int)(System.currentTimeMillis()-t));
			
				// step 2: build query graph (structure construction, relation extraction, top-k join) 
				t = System.currentTimeMillis();
				BuildQueryGraph step2 = new BuildQueryGraph();
				step2.process(qlog);
//				step2.processEXP(qlog);
				qlog.timeTable.put("step2", (int)(System.currentTimeMillis()-t));
				
				// step 3: some fix (such as "one-node" or "ask-one-triple") and aggregation
				t = System.currentTimeMillis();
				AddtionalFix step3 = new AddtionalFix();
				step3.process(qlog);
				
				// Collect SPARQLs.
				rankedSparqls.addAll(qlog.rankedSparqls);
				qlog.timeTable.put("step3", (int)(System.currentTimeMillis()-t));
			}

			// deduplicate in SPARQL
			for(Sparql spq: rankedSparqls)
				spq.deduplicate();
			
			// Sort (descending order).
			Collections.sort(rankedSparqls);
			qlog.rankedSparqls = rankedSparqls;
			System.out.println("number of rankedSparqls = " + qlog.rankedSparqls.size());
			
			// Detect question focus.
			for (int i=0; i<qlog.rankedSparqls.size(); i++) 
			{
				// First detect by SPARQLs.
				Sparql spq = qlog.rankedSparqls.get(i);
				String questionFocus = QuestionParsing.detectQuestionFocus(spq);
				
				// If failed, use TARGET directly.
				if(questionFocus == null)
					questionFocus = "?"+qlog.target.originalForm;
				
				spq.questionFocus = questionFocus;
			}
						
			return qlog;
		} 
		catch (Exception e) {
			e.printStackTrace();
			return qlog;
		}	
	}
	
	public String getStdSparqlWoPrefix(QueryLogger qlog, Sparql curSpq) 
	{
		if(qlog == null || curSpq == null)
			return null;
		
		String res = "";
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)
			res += "ask where";
		else
		{
			if(!curSpq.countTarget)
				res += ("select DISTINCT " + curSpq.questionFocus + " where");		
			else
				res += ("select COUNT(DISTINCT " + curSpq.questionFocus + ") where");	
		}					
		res += "\n";
		res += curSpq.toStringForGStore();
		if(qlog.moreThanStr != null)
		{
			res += qlog.moreThanStr+"\n";
		}
		if(curSpq.mostStr != null)
		{
			res += curSpq.mostStr+"\n";
		}
		
		return res;
	}
	
	// Notice, this will change the original SPARQL.
	public Sparql getUntypedSparql (Sparql spq) 
	{
		if(spq == null)
			return null;
		spq.removeAllTypeInfo();
		if (spq.tripleList.size() == 0) return null;
		return spq;
	}
	
	// Is it a Basic Graph Pattern without filter and aggregation?
	public boolean isBGP(QueryLogger qlog, Sparql spq)
	{
		if(qlog.s.sentenceType == SentenceType.GeneralQuestion || qlog.moreThanStr != null || spq.mostStr != null || spq.countTarget)
			return false;
		return true;
	}
	
	
	/**
	 * Get answers from Virtuoso + DBpedia, this function require OLD version Virtuoso + Virtuoso Handler.
	 * Virtuoso can solve "Aggregation"
	 **/
	public Matches getAnswerFromVirtuoso (QueryLogger qlog, Sparql spq)
	{
		Matches ret = new Matches();
		try 
		{
			Socket socket = new Socket(Globals.QueryEngineIP, 1112);
			DataOutputStream dos = new DataOutputStream(new BufferedOutputStream(socket.getOutputStream()));
			
			//formatting SPARQL & evaluate
			HashSet<String> variables = new HashSet<String>();
			String formatedSpq = spq.toStringForVirtuoso(qlog.s.sentenceType, qlog.moreThanStr, variables);
			dos.writeUTF(formatedSpq);
			dos.flush();
			System.out.println("STD SPARQL:\n"+formatedSpq+"\n");
			
			ArrayList<String> rawLines = new ArrayList<String>();
			DataInputStream dis = new DataInputStream(new BufferedInputStream(socket.getInputStream()));			
			while (true)
			{
				String line = dis.readUTF();
				if (line.equals("[[finish]]"))	break;
				rawLines.add(line);
			}
			
			// ASK query was translated to SELECT query, whose answer need translation.
			if(qlog.s.sentenceType == SentenceType.GeneralQuestion)
			{
				ret.answersNum = 1;
				ret.answers = new String[1][1];
				if(rawLines.size() == 0)
				{
					ret.answers[0][0] = "general:false";
				}
				else
				{
					ret.answers[0][0] = "general:true";
				}
				System.out.println("general question answer:" + ret.answers[0][0]);
				dos.close();
				dis.close();
				socket.close();
				return ret;
			}
			
			//select but no results
			if (rawLines.size() == 0)
			{
				ret.answersNum = 0;
				dos.close();
				dis.close();
				socket.close();
				return ret;
			}
			
			int ansNum = rawLines.size();
			int varNum = variables.size();
			ArrayList<String> valist = new ArrayList<String>(variables);
			ret.answers = new String[ansNum][varNum];
			
			System.out.println("ansNum=" + ansNum);
			System.out.println("varNum=" + varNum);
			for (int i=0;i<rawLines.size();i++)
			{
				String[] ansLineContents = rawLines.get(i).split("\t");
				for (int j=0;j<varNum;j++)
				{
					ret.answers[i][j] = valist.get(j) + ":" + ansLineContents[j];
				}
			}
			
			dos.close();
			dis.close();
			socket.close();		
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
		
		return ret;
	}
	
	public Matches getAnswerFromGStore2 (Sparql spq)
	{
		// modified by Lin Yinnian using ghttp - 2018-9-28
		GstoreConnector gc = new GstoreConnector("172.31.222.94", 9000);
        String answer = gc.query("root", "123456", "dbpedia16", spq.toStringForGStore2());
        System.out.println(answer);
		String[] rawLines = answer.split("\n");
		
		Matches ret = new Matches();
		if (rawLines.length == 0 || rawLines[0].equals("[empty result]"))
		{
			ret.answersNum = 0;
			return ret;
		}
		int ansNum = rawLines.length-1;
		String[] varLineContents = rawLines[0].split("\t");
		int varNum = varLineContents.length;
		ret.answers = new String[ansNum][varNum];
		
		System.out.println("ansNum=" + ansNum);
		System.out.println("varNum=" + varNum);
		System.out.println("rawLines.length=" + rawLines.length);	
		
		for (int i=1;i<rawLines.length;i++)
		{
			// if one answer of rawAnswer contains '\n', it may leads error so we just return.
			if(i-1 >= ansNum)
				break;
			
			String[] ansLineContents = rawLines[i].split("\t");
			for (int j=0;j<varNum;j++)
			{
				ret.answers[i-1][j] = varLineContents[j] + ":" + ansLineContents[j];
			}
		}
		//TODO: Fix DBpedia data. 
		if(spq.tripleList.size() == 1 && spq.toStringForGStore2().contains("<United_States>\t<leader>\t?"))
		{
			ret.answers[0][0] = ret.answers[0][0].replace("<John_Roberts>", "<Donald_Trump>");
		}
		
		
		
		
		//String[] ansNumLineContents = rawLines[0].split(" ");
		//int ansNum = Integer.parseInt(ansNumLineContents[ansNumLineContents.length - 1]);
		//String[] varLineContents = rawLines[1].split("\t");
		//int varNum = varLineContents.length;
		//ret.answers = new String[ansNum][varNum];
		
		//System.out.println("ansNum=" + ansNum);
		//System.out.println("varNum=" + varNum);
		//System.out.println("rawLines.length=" + rawLines.length);
		//for (int i=2;i<rawLines.length;i++)
		//{
			// if one answer of rawAnswer contains '\n', it may leads error so we just return.
		//	if(i-2 >= ansNum)
		//		break;
			
		//	String[] ansLineContents = rawLines[i].split("\t");
		//	for (int j=0;j<varNum;j++)
		//	{
				//ret.answers[i-2][j] = varLineContents[j] + ":" + ansLineContents[j];
		//	}
		//}
		//TODO: Fix DBpedia data. 
		//if(spq.tripleList.size() == 1 && spq.toStringForGStore2().contains("<United_States>\t<leader>\t?"))
		//{
			//ret.answers[0][0] = ret.answers[0][0].replace("<John_Roberts>", "<Donald_Trump>");
		//}
		
		return ret;
	}
	
	public static void main (String[] args)
	{			
		Globals.init();
		GAnswer ga = new GAnswer();
		int i =1;
		
		//file in/output
		List<String> inputList = FileUtil.readFile("E:/Linyinnian/qald9_15.txt");
		for(String input: inputList) 
		{	
			ArrayList<String> outputs = new ArrayList<String>();
			ArrayList<String> spqs = new ArrayList<String>(); 
			spqs.add("id:"+String.valueOf(i));
			i++;
			
			long parsing_st_time = System.currentTimeMillis();
			
			QueryLogger qlog = ga.getSparqlList(input);
			if(qlog == null || qlog.rankedSparqls == null)
				continue;
			
			long parsing_ed_time = System.currentTimeMillis();
			System.out.println("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");
			System.out.println("TripleCheck time: "+ qlog.timeTable.get("TripleCheck") + "ms");
			System.out.println("SparqlCheck time: "+ qlog.timeTable.get("SparqlCheck") + "ms");
			System.out.println("Ranked Sparqls: " + qlog.rankedSparqls.size());
			
//			outputs.add(qlog.SQGlog);
//			outputs.add(qlog.SQGlog + "Building HQG time: "+ (qlog.timeTable.get("step0")+qlog.timeTable.get("step1")+qlog.timeTable.get("step2")-qlog.timeTable.get("BQG_topkjoin")) + "ms");
//			outputs.add("TopKjoin time: "+ qlog.timeTable.get("BQG_topkjoin") + "ms");
//			outputs.add("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");
			
			long excuting_st_time = System.currentTimeMillis();
			Matches m = null;
			System.out.println("[RESULT]");
			ArrayList<String> lastSpqList = new ArrayList<String>();
			int idx;
			// Consider top-5 SPARQLs
			for(idx=1; idx<=Math.min(qlog.rankedSparqls.size(), 5); idx++) 
			{
				Sparql curSpq = qlog.rankedSparqls.get(idx-1);
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, curSpq);
				lastSpqList.add(stdSPQwoPrefix);
				
				System.out.println("[" + idx + "]" + "score=" + curSpq.score);
				System.out.println(stdSPQwoPrefix);

				// Print top-3 SPARQLs to file.
				if(idx <= MAX_SPQ_NUM)
				  spqs.add("[" + idx + "]" + "score=" + curSpq.score + "\n" + stdSPQwoPrefix);
//					outputs.add("[" + idx + "]" + "score=" + curSpq.score + "\n" + stdSPQwoPrefix);
					
//				// Execute by Virtuoso or GStore when answers not found
//				if(m == null || m.answers == null)
//				{
//					if (curSpq.tripleList.size()>0 && curSpq.questionFocus!=null)
//					{
//						if(ga.isBGP(qlog, curSpq))
//                            m = ga.getAnswerFromGStore2(curSpq);
//                        else
//                            m = ga.getAnswerFromVirtuoso(qlog, curSpq);
//					}
//					if (m != null && m.answers != null) 
//                    {
//                        // Found results using current SPQ, then we can break and print result.
//                        qlog.sparql = curSpq;
//                        qlog.match = m;
//                        qlog.reviseAnswers();
//                        System.out.println("Query Executing time: "+ (int)(System.currentTimeMillis() - excuting_st_time)+ "ms");
//                    }
//				}
			}		
			
			// Some TYPEs can be omitted, (such as <type> <yago:Wife>)
			if(!qlog.rankedSparqls.isEmpty())
			{
				Sparql untypedSparql = ga.getUntypedSparql(qlog.rankedSparqls.get(0));
				if(untypedSparql != null)
				{
					String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
					if(!lastSpqList.contains(stdSPQwoPrefix))
						spqs.add("[" + Math.min(MAX_SPQ_NUM+1, idx) + "]" + "score=" + 1000 + "\n" + stdSPQwoPrefix + "\n");
						// outputs.add("[" + Math.min(MAX_SPQ_NUM+1, idx) + "]" + "score=" + 1000 + "\n" + stdSPQwoPrefix + "\n");
				}
			}
			
			FileUtil.writeFile(spqs, "E:/Linyinnian/qald9_out6.txt", true);
		}
			
	}
}
