package qa;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import jgsc.GstoreConnector;
import log.QueryLogger;
import nlp.ds.Sentence;
import nlp.ds.Word;
import nlp.ds.Sentence.SentenceType;
import qa.mapping.SemanticItemMapping;
import qa.parsing.QuestionParsing;
import qa.parsing.BuildQueryGraph;
import rdf.EntityMapping;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.Triple;
import utils.FileUtil;
import addition.AddtionalFix;
import qa.Globals;

public class GAnswer {
	
	public static final int terminateThreshold = 5;
	public static int curSparqlIdx = 0;
	
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

			// Sort (descending order).
			Collections.sort(rankedSparqls);
			curSparqlIdx = 0;
			qlog.rankedSparqls = rankedSparqls;
			System.out.println("number of rankedSparqls = " + qlog.rankedSparqls.size());
			
			// Detect question focus.
			int count = 0;
			for (int i=0; i<qlog.rankedSparqls.size(); i++) 
			{
				// First detect by SPARQLs.
				Sparql spq = qlog.rankedSparqls.get(i);
				String questionFocus = QuestionParsing.detectQuestionFocus(spq);
				
				// If failed, use TARGET directly.
				if(questionFocus == null)
					questionFocus = "?"+qlog.target.originalForm;
				
				spq.questionFocus = questionFocus;
				
				count ++;
				if (count == terminateThreshold)
					break;
			}
						
			return qlog;
		} 
		catch (Exception e) {
			e.printStackTrace();
			return qlog;
		}	
	}
	
	public Sparql getNextSparql(QueryLogger qlog) 
	{
		// Notice, only return TOP-K queries.
		if (qlog == null || qlog.rankedSparqls.size()==0 || curSparqlIdx < 0 || curSparqlIdx >= qlog.rankedSparqls.size() 
				|| curSparqlIdx >= terminateThreshold)
			return null;
		else 
		{
			Sparql ret = qlog.rankedSparqls.get(curSparqlIdx);
			curSparqlIdx ++;
			return ret;
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
		GstoreConnector gc = new GstoreConnector(Globals.QueryEngineIP, 3304);
		
		//gc.load("DBpediaYago");
		String rawAnswer = gc.query(spq.toStringForGStore2());
		String[] rawLines = rawAnswer.split("\n");
		
		Matches ret = new Matches();
		if (rawLines.length == 0 || rawLines[0].equals("[empty result]"))
		{
			ret.answersNum = 0;
			return ret;
		}
		
		String[] ansNumLineContents = rawLines[0].split(" ");
		int ansNum = Integer.parseInt(ansNumLineContents[ansNumLineContents.length - 1]);
		String[] varLineContents = rawLines[1].split("\t");
		int varNum = varLineContents.length;
		ret.answers = new String[ansNum][varNum];
		
		System.out.println("ansNum=" + ansNum);
		System.out.println("varNum=" + varNum);
		System.out.println("rawLines.length=" + rawLines.length);
		for (int i=2;i<rawLines.length;i++)
		{
			// if one answer of rawAnswer contains '\n', it may leads error so we just return.
			if(i-2 >= ansNum)
				break;
			
			String[] ansLineContents = rawLines[i].split("\t");
			for (int j=0;j<varNum;j++)
			{
				ret.answers[i-2][j] = varLineContents[j] + ":" + ansLineContents[j];
			}
		}
		
		return ret;
	}
	
	public String printAnswerJsp(QueryLogger qlog,int beginResult,int endResult) {
		System.out.println("printAnswerJsp..."+beginResult+" "+endResult+ " "+ qlog.answers.size());
		if (qlog==null || qlog.match == null 
			|| qlog.match.answers == null 
			|| qlog.match.answers.length == 0
			|| qlog.sparql == null) {
			return "";
		}
		try {
			//String[][] answers = qlog.match.answers;
			//String qf = qlog.sparql.questionFocus;
			//int max = Matches.pageNum;			
		
			StringBuilder ret = new StringBuilder("");
			//String questionFocus=qf;
			//String sparqlString = qlog.sparql.toStringForGStore();
			
			//HashSet<String> printed = new HashSet<String>();
			for (int i = beginResult; i < Math.min(endResult,qlog.answers.size()); i ++) {
				Answer ans = qlog.answers.get(i);
				if (!Character.isDigit(ans.questionFocusValue.charAt(0))) {
					String link = null;
					if (ans.questionFocusValue.startsWith("http")) {
						link = ans.questionFocusValue;
					}
					else {
						link = "http://en.wikipedia.org/wiki/"+ans.questionFocusValue;
					}
					ret.append("<tr><td id=\"hit\"><a id=\"entity_name\" href=\""+link+"\" target=\"_blank\">");
					ret.append(ans.questionFocusValue);
					ret.append("</a><br/>");
					for (int j = 0; j < ans.otherInformationKey.size(); j ++) {
						ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
								+":</span><span id=\"values\">"
								+ans.otherInformationValue.get(j)
								+"   </span>");
					}
					ret.append("</td></tr>");
				}
				else {
					//String link = ans.questionFocusValue;
					ret.append("<tr><td id=\"hit\">");
					ret.append(ans.questionFocusValue);
					ret.append("<br/>");
					for (int j = 0; j < ans.otherInformationKey.size(); j ++) {
						ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
								+":</span><span id=\"values\">"
								+ans.otherInformationValue.get(j)
								+"   </span>");
					}
					ret.append("</td></tr>");
				}
			}
			
			return ret.toString();
		} 
		catch (Exception e) {	
			e.printStackTrace();
			return "";
		}
	}
	
	public String printDependencyTreeJsp(QueryLogger qlog) {
		if (qlog == null) {
			return "";
		}
		if (qlog.sparql == null) {
			return qlog.s.dependencyTreeStanford.toString();
		}

		
		int countStanford = 0;
		int countMalt = 0;
		for (Triple t : qlog.sparql.tripleList) {
			if (t.semRltn != null) {
				if(t.semRltn.extractingMethod == 'S') countStanford ++;
				else if(t.semRltn.extractingMethod == 'M') countMalt ++;
			}
		}
		
		if (countStanford > countMalt) return qlog.s.dependencyTreeStanford.toString();
		else return qlog.s.dependencyTreeMalt.toString();
	}
	
	public String printTextCoverageJsp (QueryLogger qlog) {
		if (qlog == null || qlog.s == null) 
			return "";
		Sentence s = qlog.s;
		StringBuilder sb = new StringBuilder("");
		int notCoveredCount = 0;
		for (Word w : s.words) {
			if (w.isCovered || w.isIgnored || w.posTag.equals(".")) {
				sb.append("["+w.originalForm+"] ");
			}
			else {
				sb.append(w.originalForm + " ");
				notCoveredCount ++;
			}
		}
		sb.append(" <not covered:"+notCoveredCount + "/" +s.words.length+">");
		return sb.toString();
	}
	
	public String printSemanticRelations (QueryLogger qlog) {
		if (qlog == null || qlog.sparql == null || qlog.sparql.semanticRelations == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder("");
		HashMap<Integer, SemanticRelation> semRltns = qlog.sparql.semanticRelations;
		for (Integer key : semRltns.keySet()) {
			sb.append("(\"" + semRltns.get(key).arg1Word.getFullEntityName() + "\"");
			sb.append(", \"" + semRltns.get(key).arg2Word.getFullEntityName() + "\"");
			sb.append(", \"" + semRltns.get(key).predicateMappings.get(0).parapharase + "\"");
			sb.append(", " + ((int)(semRltns.get(key).LongestMatchingScore*1000))/1000.0 + ")\n");
		}
		return sb.toString();
	}
	
	public String printMappingJsp (QueryLogger qlog) {
		if (qlog == null || qlog.sparql == null) {
			return "";
		}
		StringBuilder sb = new StringBuilder("");
		HashSet<Word> printed = new HashSet<Word>();

		int threshold = SemanticItemMapping.t;
		if (threshold > 5) threshold = 5;
		
		for (Triple triple : qlog.sparql.tripleList) {
			if (triple.predicateID == Globals.pd.typePredicateID) continue;
			SemanticRelation sr = triple.semRltn;
			if (sr == null) continue;
			Word subjWord = triple.getSubjectWord();
			Word objWord = triple.getObjectWord();
			if (!printed.contains(subjWord)) {
				printed.add(subjWord);
				if (!triple.subject.startsWith("?")) {
					ArrayList<EntityMapping> emlist = qlog.entityDictionary.get(subjWord);
					
					int emlist_size = 0;
					if (emlist != null) emlist_size = emlist.size();
					else emlist_size = 0;
					
					sb.append("\"" + subjWord.getFullEntityName() + "\"" + "(" + emlist_size + ") --> ");
					if (emlist_size > 0) {
						int i = 0;
						for (EntityMapping em : emlist) {
							sb.append("<" + em.entityName.replace(" ", "_") + ">,");
							i ++;
							if (i == threshold) break;
						}
					}
					sb.append("\n");
				}
				else {
					sb.append("\"" + subjWord.getFullEntityName() + "\"" + "(" + 1 + ") --> "+triple.subject);
					sb.append("\n");					
				}
			}
			if (!printed.contains(objWord)) {
				printed.add(objWord);
				if (!triple.object.startsWith("?")) {
					ArrayList<EntityMapping> emlist = qlog.entityDictionary.get(objWord);
					if (emlist==null) continue;
					sb.append("\"" + objWord.getFullEntityName() + "\"" + "(" + emlist.size() + ") --> ");
					int i = 0;
					for (EntityMapping em : emlist) {
						sb.append("<" + em.entityName.replace(" ", "_") + ">,");
						i ++;
						if (i == threshold) break;
					}
					sb.append("\n");
				}
				else {
					sb.append("\"" + objWord.getFullEntityName() + "\"" + "(" + 1 + ") --> " + triple.object);
					sb.append("\n");
				}
			}
			if (sr.dependOnSemanticRelation == null) {
				sb.append("\"" + triple.semRltn.predicateMappings.get(0).parapharase + "\"" + "(" + triple.semRltn.predicateMappings.size() +") --> ");
				int i = 0;
				for (PredicateMapping pm : triple.semRltn.predicateMappings) {
					sb.append("<" + Globals.pd.getPredicateById(pm.pid) + ">,");
					i ++;
					if (i == threshold) break;
				}
				sb.append("\n");
			}
		}
		return sb.toString();
	}
	
	public String printCRR (QueryLogger qlog) {
		StringBuilder sb = new StringBuilder("");
		HashSet<Word> printed = new HashSet<Word>();
		for (Word w : qlog.s.words) {
			w = w.getNnHead();
			if (printed.contains(w)) 
				continue;
			if (w.crr != null) 
				sb.append("\""+w.getFullEntityName() + "\" = \"" + w.crr.getFullEntityName() + "\"");
			printed.add(w);
		}
		return sb.toString();
	}
	
	public static void main (String[] args)
	{			
		Globals.init();
		GAnswer ga = new GAnswer();
		
		//file in/output
		List<String> inputList = FileUtil.readFile(Globals.localPath+"data/test/test_in.txt");
		for(String input: inputList) 
		{	
			ArrayList<String> outputs = new ArrayList<String>();
			long parsing_st_time = System.currentTimeMillis();
			
			QueryLogger qlog = ga.getSparqlList(input);
			if(qlog == null)
				continue;
			
			Sparql curSpq = ga.getNextSparql(qlog);		
			Sparql bestSpq = curSpq;
			int idx_sparql = 0;
			
			long parsing_ed_time = System.currentTimeMillis();
			System.out.println("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");
			System.out.println("TripleCheck time: "+ qlog.timeTable.get("TripleCheck") + "ms");
			System.out.println("SparqlCheck time: "+ qlog.timeTable.get("SparqlCheck") + "ms");
			System.out.println("Ranked Sparqls: " + qlog.rankedSparqls.size());
			
			outputs.add(qlog.SQGlog);
//			outputs.add(qlog.SQGlog + "Building HQG time: "+ (qlog.timeTable.get("step0")+qlog.timeTable.get("step1")+qlog.timeTable.get("step2")-qlog.timeTable.get("BQG_topkjoin")) + "ms");
			outputs.add("TopKjoin time: "+ qlog.timeTable.get("BQG_topkjoin") + "ms");
			outputs.add("Question Understanding time: "+ (int)(parsing_ed_time - parsing_st_time)+ "ms");
				
			long excuting_st_time = System.currentTimeMillis();
			Matches m = null;
			System.out.println("[RESULT]");
			ArrayList<String> lastSpqList = new ArrayList<String>();
			while (curSpq != null) 
			{
				qlog.sparql = curSpq;
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, curSpq);
				
				if(!lastSpqList.contains(stdSPQwoPrefix))
				{
					idx_sparql++;
					System.out.println("[" + idx_sparql + "]" + "score=" + curSpq.score);
					System.out.println(stdSPQwoPrefix);

					// Print top-3 SPARQLs to file.
					if(idx_sparql <= 3)
					{
						outputs.add("[" + idx_sparql + "]" + "score=" + curSpq.score + "\n" + stdSPQwoPrefix);
						lastSpqList.add(stdSPQwoPrefix);
					}
					
					// Execute by Virtuoso or GStore when answers not found
//					if(m == null || m.answers == null)
//					{
//						if (curSpq.tripleList.size()>0 && curSpq.questionFocus!=null)
//						{
//							if(ga.isBGP(qlog, curSpq))
//                                m = ga.getAnswerFromGStore2(curSpq);
//                            else
//                                m = ga.getAnswerFromVirtuoso(qlog, curSpq);
//						}
//						if (m != null && m.answers != null) 
//                        {
//                            // Found results using current SPQ, then we can break and print result.
//                            qlog.sparql = curSpq;
//                            qlog.match = m;
//                            qlog.reviseAnswers();
//                            System.out.println("Query Executing time: "+ (int)(System.currentTimeMillis() - excuting_st_time)+ "ms");
//                        }
//					}
				}
				curSpq = ga.getNextSparql(qlog);
			}		
			
			// Some TYPEs can be omitted, (such as <type> <yago:Wife>)
			Sparql untypedSparql = ga.getUntypedSparql(bestSpq);
			if(untypedSparql != null)
			{
				String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
				if(!lastSpqList.contains(stdSPQwoPrefix))
				{
					outputs.add("[" + Math.min(4,idx_sparql) + "]" + "score=" + 1000 + "\n" + stdSPQwoPrefix + "\n");
				}
			}
			
			FileUtil.writeFile(outputs, Globals.localPath + "data/test/test_out.txt", true);
		}
			
	}
}
