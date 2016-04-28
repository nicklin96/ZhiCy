package qa;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;

import fgmt.RelationFragment;
import fgmt.TypeFragment;
import paradict.ParaphraseDictionary;
import qa.mapping.DBpediaLookup;
import nlp.tool.NERecognizer;
import nlp.tool.CoreNLP;
import nlp.tool.MaltParser;
import nlp.tool.StanfordParser;
import nlp.tool.StopWordsList;
import test.PosTagPattern;

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
	// entity dictionary
	public static HashMap<String, Integer> entityName2Id = null;
	public static HashMap<Integer, String>  entityId2Name = null;
	
	public static String localPath="D:/husen/gAnswer/";
	//public static String localPath="/media/wip/hanshuo/gAnswer/";
	
	public static void entity_load() throws IOException 
	{
		String filename = Globals.localPath+"data/DBpedia2014/fragments/id_mappings/DBpedia2014_entities_id.txt";
		File file = new File(filename);
		InputStreamReader in = new InputStreamReader(new FileInputStream(file),"utf-8");
		BufferedReader br = new BufferedReader(in);

		entityName2Id = new HashMap<String, Integer>();
		entityId2Name = new HashMap<Integer, String>();

		String line;
		while((line = br.readLine()) != null) 
		{
			String[] lines = line.split("\t");
			String entName = lines[0].substring(1, lines[0].length()-1);
	
			entityName2Id.put(entName, Integer.parseInt(lines[1]));	
			entityId2Name.put(Integer.parseInt(lines[1]), entName);
		}
		br.close();
	}
	
	public static void init () {
		System.out.println("====== gAnswer over DBpedia ======");

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
			//entity_load();
			RelationFragment.load();
			TypeFragment.load();
		} 
		catch (Exception e1) {
			System.out.println("RelationFragment and TypeFragment loading error!");
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
