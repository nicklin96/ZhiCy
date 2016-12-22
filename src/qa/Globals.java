package qa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import lcn.EntityFragmentFields;
import fgmt.EntityFragment;
import fgmt.RelationFragment;
import fgmt.TypeFragment;
import paradict.ParaphraseDictionary;
import qa.mapping.DBpediaLookup;
import nlp.tool.NERecognizer;
import nlp.tool.CoreNLP;
import nlp.tool.MaltParser;
import nlp.tool.StanfordParser;
import nlp.tool.StopWordsList;
import addition.PosTagPattern;

public class Globals {
	// nlp tools
	public static CoreNLP coreNLP;
	public static StanfordParser stanfordParser;
	public static StopWordsList stopWordsList;
	public static MaltParser maltParser;
	public static NERecognizer nerRecognizer;
	// paraphrase dictionary
	public static ParaphraseDictionary pd;
	// fragments
	public static DBpediaLookup dblk;
	// postag pattern
//	public static PosTagPattern pp;
	
	/*
	 * evaluationMethod:
	 * 1. baseline稳定版，从question focus出发生成确定的query graph结构，“先到先得”策略，不允许有环；足以应付绝大多数case，实际推荐使用本方法
	 * 2. hyper query graph + top-down方法，即生成的hyper query graph包含所有可能边，允许有环；执行时总体和1一致，只是需要先枚举结构；
	 * 3. hyper query graph + bottom-up方法，与2不同之处在于不生成SPARQL，直接在hyper query graph基础上进行graph exploration，只供实验，实际非常不推荐
	 * */
	public static int evaluationMethod = 3; 
	public static boolean isRunAsWebServer = false;	// 在本机运行为 false，作为服务端运行为 true
	
	public static String localPath="/media/wip/husen/NBgAnswer/";
	public static String QueryEngineIP = "127.0.0.1";	//端口还需要在对应函数中修改
	
	public static void init () 
	{
		System.out.println("====== gAnswer2.0 over DBpedia ======");
		
		if(isRunAsWebServer == false)
		{
			localPath="D:/husen/gAnswer/";
			QueryEngineIP = "172.31.222.72";
		}

		long t1, t2, t3, t4, t5, t6, t7, t8, t9;
		
		t1 = System.currentTimeMillis();
		coreNLP = new CoreNLP();
		
		t2 = System.currentTimeMillis();
		stanfordParser = new StanfordParser();
		
		t3 = System.currentTimeMillis();
		maltParser = new MaltParser();
		
		t4 = System.currentTimeMillis();
		nerRecognizer = new NERecognizer();
		
		t5 = System.currentTimeMillis();
		stopWordsList = new StopWordsList();
		
		t6 = System.currentTimeMillis();
		pd = new ParaphraseDictionary();
		//pp = new PosTagPattern();
		
		t7 = System.currentTimeMillis();
		try 
		{	
			EntityFragmentFields.load();
			RelationFragment.load();
			TypeFragment.load();
		} 
		catch (Exception e1) {
			System.out.println("EntityIDs and RelationFragment and TypeFragment loading error!");
			e1.printStackTrace();
		}
		
		t8 = System.currentTimeMillis();
		dblk = new DBpediaLookup();
		
		t9 = System.currentTimeMillis();
		System.out.println("======Initialization======");
		System.out.println("CoreNLP(Lemma): " + (t2-t1) + "ms.");
		System.out.println("StanfordParser: " + (t3-t2) + "ms.");
		System.out.println("MaltParser: " + (t4-t3) + "ms.");
		System.out.println("NERecognizer: " + (t5-t4) + "ms.");
		System.out.println("StopWordsList: " + (t6-t5) + "ms.");
		System.out.println("ParaphraseDict & posTagPattern: " + (t7-t6) + "ms.");
		System.out.println("GraphFragments: " + (t8-t7) + "ms.");
		System.out.println("DBpediaLookup: " + (t9-t8) + "ms.");
		System.out.println("* Total *: " + (t9-t1) + "ms.");
		System.out.println("==========================");
	}

	
	/**
	 * Use as system("pause") in C
	 */
	public static void systemPause () {
		System.out.println("System pause ...");
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		try {
			br.readLine();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
