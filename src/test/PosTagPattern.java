package test;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.HashMap;

public class PosTagPattern {
	
	String localPath = "D:/husen/gAnswer/data/DBpedia3.9/fragments/pos_tag_pattern/";
	public ArrayList<String> entPosTagPatternList;
	public ArrayList<String> typePosTagPatternList;
	public HashMap<String,Integer> posTagtoId;
	public Trie entTrie, typeTrie;
	
	public PosTagPattern()
	{
		try 
		{
			loadEntPosTagPattern(6);
			loadTypePosTagPattern(6);
			buildEntTrie();
			buildTypeTrie();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void buildEntTrie()
	{
		 entTrie = new Trie();
		 for(String pattern: entPosTagPatternList)
		 {
			 String[] strArr = pattern.split(" ");
			 entTrie.insert(strArr);
		 }
	}
	
	public void buildTypeTrie()
	{
		 typeTrie = new Trie();
		 for(String pattern: typePosTagPatternList)
		 {
			 String[] strArr = pattern.split(" ");
			 typeTrie.insert(strArr);
		 }
	}
	
	public void loadPosTagSet()
	{
		posTagtoId = new HashMap<String, Integer>();
		File inputFile = new File(localPath + "posTagSet.txt");
		try 
		{
			BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"utf-8"));
			String input;
			int id = 0;
			while((input = fr.readLine())!=null)
			{
				posTagtoId.put(input, ++id);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	//只需要频次大于等于freq的pattern（因为频次过低的pattern可能是噪音，或者太过特殊很难被问到）
	public void loadEntPosTagPattern(int freq)
	{
		entPosTagPatternList = new ArrayList<String>();
		File inputFile = new File(localPath + "DBpedia3.9_entPosTagPatternList.txt");
		try 
		{
			BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"utf-8"));
			String input;
			while((input = fr.readLine())!=null)
			{
				String[] strArray = input.split("\t");
				String posTagPattern = strArray[0];
				int curFreq = freq;
				if(strArray.length > 1)
					curFreq = Integer.parseInt(strArray[1]);
				if(posTagPattern != null && curFreq>=freq)
					entPosTagPatternList.add(posTagPattern);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public void loadTypePosTagPattern(int freq)
	{
		typePosTagPatternList = new ArrayList<String>();
		File inputFile = new File(localPath + "DBpedia3.9_typePosTagPatternList.txt");
		try 
		{
			BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"utf-8"));
			String input;
			while((input = fr.readLine())!=null)
			{
				String[] strArray = input.split("\t");
				String posTagPattern = strArray[0];
				int curFreq = freq;
				if(strArray.length > 1)
					curFreq = Integer.parseInt(strArray[1]);
				if(posTagPattern != null && curFreq>=freq)
					typePosTagPatternList.add(posTagPattern);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
