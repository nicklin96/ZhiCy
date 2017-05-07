package qa;

import java.io.*;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import jgsc.GstoreConnector;
import log.QueryLogger;
import log.ResultJspFile;
import nlp.ds.Sentence;
import nlp.ds.Word;
import nlp.ds.Sentence.SentenceType;
import qa.mapping.SemanticItemMapping;
import qa.parsing.QuestionParsing;
import qa.parsing.BuildQueryGraph;
import rdf.EntityMapping;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.SemanticUnit;
import rdf.Sparql;
import rdf.Triple;
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
			ArrayList<Sparql> rankedSparqls = new ArrayList<Sparql>();
			NRtime = (int)(System.currentTimeMillis()-t0);
			System.out.println("step0 [Node Recognition] : "+ NRtime +"ms");	
			
			//对每种NR结果，新建qlog并尝试生成querys，把所有querys合并到plan 0的qlog中；
			//因为plan 0的得分是最高的，我们只保留plan 0求解过程中的日志（在for过程中fw的数据因为还没close就new了qlog，所以丢失掉，相当于只输出最后plan 0的log）；
			//system.out是输出了每种方案的日志。
			for(int i=query.sList.size()-1;i>=0;i--)
			{
				Sentence possibleSentence = query.sList.get(i);
				qlog = new QueryLogger(query,possibleSentence);
			
				System.out.println("transQ: "+qlog.s.plainText);
				qlog.NRlog = query.preLog;
				qlog.SQGlog += "Id: "+query.queryId+"\nQuery: "+query.MergedQuestionList.get(0)+"\n";
				qlog.timeTable.put("step0", (int)NRtime);
				
				if(i == 0 && qlog.fw!=null)	//只输出plan0的日志,只在本地的时候进行文件输出
				{
					qlog.fw.write("Id: "+query.queryId+"\nQuery: "+query.MergedQuestionList.get(0)+"\n");
					qlog.fw.write(query.preLog);
				}
				
				// step 1: question parsing (dependency tree, sentence type)
				t = System.currentTimeMillis();
				QuestionParsing step1 = new QuestionParsing();
				step1.process(qlog);
				qlog.timeTable.put("step1", (int)(System.currentTimeMillis()-t));
			
				// step 2: build query graph (structure construction, relation extraction, top-k join) 
				t = System.currentTimeMillis();
				BuildQueryGraph step2 = new BuildQueryGraph();
				step2.process(qlog);
				qlog.timeTable.put("step2", (int)(System.currentTimeMillis()-t));
				
				// step 3: some fix (such as "one-node" or "ask-one-triple") and aggregation
				t = System.currentTimeMillis();
				AddtionalFix step3 = new AddtionalFix();
				step3.process(qlog);
				
				//把各种方案生成的sparql记录下来
				rankedSparqls.addAll(qlog.rankedSparqls);
				qlog.timeTable.put("step3", (int)(System.currentTimeMillis()-t));
				
			}

			
			//排序，把最后一个qlog作为载体，放入完整的sparql list
			Collections.sort(rankedSparqls);
			qlog.rankedSparqls = rankedSparqls;
			System.out.println("number of rankedSparqls = " + qlog.rankedSparqls.size());
			
			//确定question focus，注意rankedSparqls是按照从小到大排序的
			int count = 0;
			QuestionParsing step1 = new QuestionParsing();
			for (int i = qlog.rankedSparqls.size()-1; i >= 0; i --) 
			{
				Sparql spq = qlog.rankedSparqls.get(i);
				// get question focus
				String questionFocus = step1.detectQuestionFocus(spq);
				
				// 如果从sparql上得不到focus，就认为target是focus
				if(questionFocus == null)
					questionFocus = "?"+qlog.target.originalForm;
				
				spq.questionFocus = questionFocus;
				
				count ++;
				if (count == terminateThreshold) 
				{
					break;
				}
			}
			curSparqlIdx = qlog.rankedSparqls.size()-1;
						
			return qlog;
		} catch (Exception e) {
			e.printStackTrace();
			return qlog;
		}	
	}
	
	public Sparql getNextSparql(QueryLogger qlog) {
		if (qlog == null || qlog.rankedSparqls.size()==0 || curSparqlIdx < 0 || qlog.gStoreCallTimes > terminateThreshold) {
			return null;
		}
		else {
			Sparql ret = qlog.rankedSparqls.get(curSparqlIdx);
			curSparqlIdx --;
			qlog.gStoreCallTimes ++;
			return ret;
		}
	}
	
	public Sparql getUntypedSparql (Sparql spq, QueryLogger qlog) {
		qlog.gStoreCallTimes ++;
		if(spq == null)
			return null;
		//注意，这是直接在传入的spq里去掉所有type，执行后，原spq就被改变了
		spq.removeAllTypeInfo();
		if (spq.tripleList.size() == 0) return null;
		return spq;
	}
	
	public boolean isBGP(QueryLogger qlog, Sparql spq)
	{
		if(qlog.s.sentenceType == SentenceType.GeneralQuestion || qlog.moreThanStr != null || spq.mostStr != null || spq.countTarget)
			return false;
		return true;
	}
	
	//Virtuoso can solve "Aggregation"
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
			
			//由于支持性原因，ask查询被转化为select查询执行。需要单独处理放回结果。
			if(qlog.s.sentenceType == SentenceType.GeneralQuestion)
			{
				ret.answersNum = 1;
				ret.answers = new String[1][1];
				//false
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
		
		//gc.load("db_dbpedia_ganswer");
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
			// 因为rawAnswer里一个答案可能包含\n，就会出现4个答案但rawLines.lengh为6的情况；在gserver修正输出格式前，先简单的截断尾部避免异常
			if(i-2 >= ansNum)
				break;
			
			String[] ansLineContents = rawLines[i].split("\t");
			for (int j=0;j<varNum;j++)
			{
				//System.out.println("i:"+i+" j:"+j);
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
			String qf = qlog.sparql.questionFocus;
			//int max = Matches.pageNum;			
		
			StringBuilder ret = new StringBuilder("");
			String questionFocus=qf;
			//String sparqlString = qlog.sparql.toStringForGStore();
			
			//HashSet<String> printed = new HashSet<String>();
			for (int i = beginResult; i < Math.min(endResult,qlog.answers.size()); i ++) {
				//if (i == max) break;
				//Answer ans = new Answer(questionFocus, answers[i]);
				//if (printed.contains(ans.questionFocusValue)) continue;
				//printed.add(ans.questionFocusValue);
				//System.out.println("ans="+ans.questionFocusKey+"("+ans.questionFocusValue+")"+" "+questionFocus);
				
				//if (sparqlString.contains(ans.questionFocusValue)) continue;
				//cnt ++;
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
		} catch (Exception e) {
			/*
			String[][] answers = qlog.match.answers;
			int max = Matches.pageNum;

			
			int cnt = 0;
			for (int i = 0; i < answers.length; i ++) {
				if (i == max) break;
				cnt ++;
				for (int j = 0; j < answers[i].length; j ++) {
					ret.append("<span id=\"values\">"
							+answers[i][j]
							+"   </br></span>");
				}
				ret.append("</td></tr>");
			}
			*/
			e.printStackTrace();
			StringBuilder ret = new StringBuilder("");
			return ret.toString();
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
	
	public ArrayList<String> genNRdata(QueryLogger qlog)
	{
		ArrayList<String> outputs = new ArrayList<String>();
		HashSet<Word> nodes = new HashSet<Word>();
		HashSet<Word> isConstant = new HashSet<Word>();
		for(SemanticUnit su: qlog.semanticUnitList)
		{
			Word word = su.centerWord;
			nodes.add(word);
		}
		for(SemanticRelation sr: qlog.semanticRelations.values())
		{
			if(sr.isArg1Constant)
				isConstant.add(sr.arg1Word);
			if(sr.isArg2Constant)
				isConstant.add(sr.arg2Word);
		}
		for(Word word: qlog.s.words)
		{
			if(nodes.contains(word))
			{
				if(word.mayEnt)
				{
					String[] ws = word.originalForm.split("_");
					int cnt = 0;
					for(String w: ws)
					{
						if(cnt == 0)
							outputs.add(w + " B-E");
						else
							outputs.add(w + " I-E");
						cnt++;
					}
				}
				else if(word.mayType)
				{
					if(isConstant.contains(word))
					{
						String[] ws = word.originalForm.split("_");
						int cnt = 0;
						for(String w: ws)
						{
							if(cnt == 0)
								outputs.add(w + " B-T");
							else
								outputs.add(w + " I-T");
							cnt++;
						}
					}
					else
					{
						String[] ws = word.originalForm.split("_");
						int cnt = 0;
						for(String w: ws)
						{
							if(cnt == 0)
								outputs.add(w + " B-V");
							else
								outputs.add(w + " I-V");
							cnt++;
						}
					}
				}
				else
				{
					String[] ws = word.originalForm.split("_");
					int cnt = 0;
					for(String w: ws)
					{
						if(cnt == 0)
							outputs.add(w + " B-V");
						else
							outputs.add(w + " I-V");
						cnt++;
					}
				}
			}
			else
				outputs.add(word.originalForm + " O");
		}
		
		return outputs;
	}
	
	/*
	 * 1. word, postag, type
	 * 2. 实体是经过下划线聚集的，例如将 Robert_Kennedy 作为一个word输出，而不拆开来
	 * */
	public ArrayList<String> genNRdataWithPosTag(QueryLogger qlog)
	{
		ArrayList<String> outputs = new ArrayList<String>();
		HashSet<Word> nodes = new HashSet<Word>();
		HashSet<Word> isConstant = new HashSet<Word>();
		for(SemanticUnit su: qlog.semanticUnitList)
		{
			Word word = su.centerWord;
			nodes.add(word);
		}
		for(SemanticRelation sr: qlog.semanticRelations.values())
		{
			if(sr.isArg1Constant)
				isConstant.add(sr.arg1Word);
			if(sr.isArg2Constant)
				isConstant.add(sr.arg2Word);
		}
		for(Word word: qlog.s.words)
		{
			if(nodes.contains(word))
			{
				if(word.mayEnt)
				{
					outputs.add(word.originalForm + " " + word.posTag + " B-E");
				}
				else if(word.mayType)
				{
					if(isConstant.contains(word))
					{
						outputs.add(word.originalForm + " " + word.posTag + " B-E");
					}
					else
					{
						outputs.add(word.originalForm + " " + word.posTag + " B-V");
					}
				}
				else
				{
					outputs.add(word.originalForm + " " + word.posTag + " B-V");
				}
			}
			else
				outputs.add(word.originalForm + " " + word.posTag + " O");
		}
		
		return outputs;
	}

	
	public static void main (String[] args){
				
		Globals.init();
		File inputFile = new File(Globals.localPath+"data/test/test_in.txt");
		try {
			GAnswer ga = new GAnswer();
			
			//file in/output
			BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"utf-8"));
//			OutputStreamWriter nrfw = new OutputStreamWriter(new FileOutputStream(new File(Globals.localPath+"data/test/nrQ6T.train"),true),"utf-8");
			String input;
			
			while ((input = fr.readLine()) != null) 
			{	
				long st_time = System.currentTimeMillis();
				QueryLogger qlog = ga.getSparqlList(input);
				if(qlog == null)
					continue;
				
//				if(qlog != null)
//				{
//					ArrayList<String>outputs = ga.genNRdataWithPosTag(qlog);
//					for(String line: outputs)
//					{
//						nrfw.write(line + "\n");
//					}
//					nrfw.write("\n");
//				}
				
				Sparql curSpq = ga.getNextSparql(qlog);		
				Sparql bestSpq = curSpq;
				int idx_sparql = 0;
				
				long ed_time = System.currentTimeMillis();
				System.out.println("Qustion Understanding time: "+ (int)(ed_time - st_time)+ "ms");
				System.out.println("TripleCheck time: "+ qlog.timeTable.get("TripleCheck") + "ms");
				System.out.println("SparqlCheck time: "+ qlog.timeTable.get("SparqlCheck") + "ms");
				System.out.println("Ranked Sparqls: " + qlog.rankedSparqls.size());
				if(qlog.fw != null)
				{
				//	for(String key: qlog.timeTable.keySet())
				//		qlog.fw.write(key + ": " + qlog.timeTable.get(key) + "ms\n");
					qlog.fw.write("Building HQG time: "+ (qlog.timeTable.get("step0")+qlog.timeTable.get("step1")+qlog.timeTable.get("step2")-qlog.timeTable.get("BQG_topkjoin")) + "ms\n");
					qlog.fw.write("TopKjoin time: "+ qlog.timeTable.get("BQG_topkjoin") + "ms\n");
					qlog.fw.write("Qustion Understanding time: "+ (int)(ed_time - st_time)+ "ms\n");
				}
					
				long excute_st_time = System.currentTimeMillis();
				Matches m = null;
				System.out.println("[RESULT]");
				ArrayList<String> lastSpqList = new ArrayList<String>();	//简单去一下重
				while (curSpq != null) 
				{
					qlog.sparql = curSpq;
					String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, curSpq);
					
					if(!lastSpqList.contains(stdSPQwoPrefix))
					{
						idx_sparql++;
						System.out.println("[" + idx_sparql + "]" + "score=" + curSpq.score);
						System.out.println(stdSPQwoPrefix);
	
						if(idx_sparql <= 3)
						{
							if(qlog.fw != null)
							{
								qlog.fw.write("[" + idx_sparql + "]" + "score=" + curSpq.score + "\n");
								qlog.fw.write(stdSPQwoPrefix+"\n");
							}
							//这去重先只对要输出文件的使用，即只保留”不同的前三个“+“第一的untyped”
							lastSpqList.add(stdSPQwoPrefix);
						}
						
						// execute by Virtuoso or GStore
//						if(m == null || m.answers == null)
//						{
//							if (curSpq.tripleList.size()>0 && curSpq.questionFocus!=null)
//							{
//								if(ga.isBGP(qlog, curSpq))
//                                    m = ga.getAnswerFromGStore2(curSpq);
//                                else
//                                    m = ga.getAnswerFromVirtuoso(qlog, curSpq);
//							}
//								
//							if (m != null && m.answers != null) 
//	                        {
//	                            // Found results using current SPQ, then we can break and print result.
//	                            qlog.sparql = curSpq;
//	                            qlog.match = m;
//	                            
//	                            qlog.reviseAnswers();
//	                            
//	                            qlog.fw.write("Query Executing time: "+ (int)(System.currentTimeMillis() - excute_st_time)+ "ms\n");
//	                            // ResultJspFile jspFile = new ResultJspFile();
//	                            // jspFile.saveToFile(qlog,"","","");
//	                        }
//						}
					}
					curSpq = ga.getNextSparql(qlog);
				}		
				
				//因为经常出现无用type导致查询不到结果(如 <type> <yago:Wife>)，追加一个untyped SPQ
				Sparql untypedSparql = ga.getUntypedSparql(bestSpq, qlog);
				if(untypedSparql != null)
				{
					String stdSPQwoPrefix = ga.getStdSparqlWoPrefix(qlog, untypedSparql);
					if(!lastSpqList.contains(stdSPQwoPrefix) && qlog.fw != null)
					{
						qlog.fw.write("[" + Math.min(4,idx_sparql) + "]" + "score=" + 1000 + "\n");
						qlog.fw.write(stdSPQwoPrefix+"\n");
					}
				}
				
				//System.out.println(ga.printMappingJsp(qlog));
				
				qlog.fw.close();
			}
			fr.close();
//			nrfw.close();
			
		} catch (IOException e) {
			e.printStackTrace();
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
}
