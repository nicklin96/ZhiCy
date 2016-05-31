package log;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import javax.servlet.http.HttpServletRequest;

import qa.Matches;
import qa.Query;
import rdf.EntityMapping;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.MergedWord;
import rdf.SemanticUnit;
import qa.Answer;
import nlp.ds.Sentence;
import nlp.ds.Word;

public class QueryLogger {
	public Sentence s = null;
	public String ipAdress = null;
	
	public Sparql sparql = null;
	public Matches match = null;
	public ArrayList<Answer> answers = null;	
	
	public boolean MODE_debug = false;
	public boolean MODE_log = true;
	public boolean MODE_fragment = true;
	
	public HashMap<String, Integer> timeTable = null;
	
	public HashMap<Integer, SemanticRelation> semanticRelations = null;
	
	public HashMap<Word, ArrayList<EntityMapping>> entityDictionary = null;
	
	public ArrayList<Sparql> rankedSparqls = null;
	public Sparql zhiCySparql = null;
	public Word target = null;
	
	public boolean isMaltParserUsed = false;
	public int gStoreCallTimes = 0;
	
	public boolean shouldTermintate = false;
	
	public ArrayList<MergedWord> mWordList = null;
	public ArrayList<SemanticUnit> semanticUnitList = null;
	
	File outputFile = new File("./test/test_out.txt");
	public OutputStreamWriter fw;
	
	public String moreThanStr = null;
	public String mostStr = null;
	
	public QueryLogger (Query query, Sentence sentence) 
	{
		this.s = sentence;
		
		timeTable = new HashMap<String, Integer>();
		rankedSparqls = new ArrayList<Sparql>();
		
//		isMaltParserUsed = true;
		MODE_debug = false;
		MODE_log = true;
		MODE_fragment = true;
		mWordList = query.mWordList;
		
		try {
			fw = new OutputStreamWriter(new FileOutputStream(outputFile,true),"utf-8");
		} catch (UnsupportedEncodingException | FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
		
	// Source code: http://edu.21cn.com/java/g_189_755584-1.htm
	public static String getIpAddr(HttpServletRequest request) {
		String ip = request.getHeader("x-forwarded-for");
		if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("Proxy-Client-IP");
		}
		if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getHeader("WL-Proxy-Client-IP");
		}
		if(ip == null || ip.length() == 0 || "unknown".equalsIgnoreCase(ip)) {
			ip = request.getRemoteAddr();
		}
		
		int idx;
		if((idx = ip.indexOf(',')) != -1) {
			ip = ip.substring(0, idx);
		}
		return ip;
	}
	
	public void reviseAnswers()
	{	
		answers = new ArrayList<Answer>();
		if (match == null || sparql == null || match.answers == null || sparql.questionFocus == null)
			return;
		
		HashSet<Answer> answerSet = new HashSet<Answer>();
		String questionFocus = sparql.questionFocus;
		String sparqlString = sparql.toStringForGStore();		
		//System.out.println("mal="+match.answers.length);
		for (int i=0;i<match.answers.length;i++)
		{
			Answer ans = new Answer(questionFocus, match.answers[i]);
			if (!sparqlString.contains(ans.questionFocusValue))
				answerSet.add(ans);			
		}
		
		
		for (Answer ans : answerSet)
			answers.add(ans);	
		
		Collections.sort(answers);
	}
	
	
}
