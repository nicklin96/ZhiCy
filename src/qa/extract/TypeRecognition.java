package qa.extract;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import nlp.tool.StopWordsList;
import fgmt.RelationFragment;
import fgmt.TypeFragment;
import lcn.SearchInTypeShortName;
import qa.Globals;
import rdf.SemanticRelation;
import rdf.TypeMapping;

public class TypeRecognition {
	// dbpedia3.9
//	public static final int[] type_Person = {19,20,21};
//	public static final int[] type_Place = {43,45};
//	public static final int[] type_Organisation = {2,12};	
	
	// dbpedia 2014
	public static final int[] type_Person = {180,279};
	public static final int[] type_Place = {49,228};
	public static final int[] type_Organisation = {419,53};
	
	public HashMap<String,String> extendTypeMap = null; 
	
	SearchInTypeShortName st = new SearchInTypeShortName();
	
	public TypeRecognition()
	{
		extendTypeMap = new HashMap<String, String>();
		
		//一些形式上变换的type
		extendTypeMap.put("NonprofitOrganizations", "dbo:Non-ProfitOrganisation");
		extendTypeMap.put("GivenNames", "dbo:GivenName");
		extendTypeMap.put("JamesBondMovies","yago:JamesBondFilms");
		extendTypeMap.put("TVShows", "dbo:TelevisionShow");
		extendTypeMap.put("US", "yago:StatesOfTheUnitedStates");
		extendTypeMap.put("Europe", "yago:EuropeanCountries");
		extendTypeMap.put("Africa", "yago:AfricanCountries");
	}
	
	public ArrayList<TypeMapping> getExtendTypeByStr(String allUpperFormWord)
	{
		ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
		
		//search in yago type
		if(TypeFragment.yagoTypeList.contains(allUpperFormWord))
		{
			//yago前缀标记
			String typeName = "yago:"+allUpperFormWord;
			TypeMapping tm = new TypeMapping(-1,typeName,Globals.pd.typePredicateID,1);
			tmList.add(tm);
		}
		else if(extendTypeMap.containsKey(allUpperFormWord))
		{
			String typeName = extendTypeMap.get(allUpperFormWord);
			TypeMapping tm = new TypeMapping(-1,typeName,Globals.pd.typePredicateID,1);
			tmList.add(tm);
		}
		if(tmList.size()>0)
			return tmList;
		else
			return null;
	}
	
	public ArrayList<TypeMapping> getTypeIDsAndNamesByStr (String baseform) 
	{
		ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
		
		try 
		{
			tmList = st.searchTypeScore(baseform, 0.4, 0.8, 10);
			Collections.sort(tmList);
			if (tmList.size()>0) 
				return tmList;
			else 
				return null;
			
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}		
	}
		
	public ArrayList<Integer> recognize (String baseform) {
		
		char c = baseform.charAt(baseform.length()-1);
		if (c >= '0' && c <= '9') {
			baseform = baseform.substring(0, baseform.length()-2);
		}
		
		try {
			ArrayList<String> ret = st.searchType(baseform, 0.4, 0.8, 10);
			ArrayList<Integer> ret_in = new ArrayList<Integer>();
			for (String s : ret) {
				System.out.println("["+s+"]");
				ret_in.addAll(TypeFragment.typeShortName2IdList.get(s));
			}
			if (ret_in.size()>0) return ret_in;
			else return null;
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}		
	}

	/*
	 * 2015-12-04，这个函数在NodeRecognition后，SemanticRelation生成后，topK检验前执行，
	 * 作用是：将 ?who、?where的type信息加入tmList
	 * TODO:
	 * type一旦识别成功，有两种情况：
	 * （1）认为该word为变量，并加一条该word的type三元组。
	 * 例如：Which books by Kerouac were published by Viking Press? 中的“books”。
	 * （2）认为该word为常量。它被用来修饰其他word。
	 * 例如：Are tree frogs a type of amphibian? 中的“amphibian”。
	 * 这两种情况的分别处理在ExtractRelation -> constantVariableRecognition
	 * */
	public void recognize (HashMap<Integer, SemanticRelation> semanticRelations) {
		ArrayList<TypeMapping> ret = null;
		for (Integer it : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(it);
			//这时候还没有对sr中变量的isArgConstant进行过修改
			if(!sr.arg1Word.mayType) 
			{
				ret = recognizeSpecial(sr.arg1Word.baseForm);
				if (ret != null) 
				{
					sr.arg1Word.tmList = ret;
				}
			}
			if(!sr.arg2Word.mayType) 
			{
				ret = recognizeSpecial(sr.arg2Word.baseForm);
				if (ret != null) 
				{
					sr.arg2Word.tmList = ret;
				}
			}
		}	
	}
	
	public ArrayList<TypeMapping> recognizeSpecial (String wordSpecial) 
	{
		ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
		if (wordSpecial.toLowerCase().equals("who")) 
		{
			for (Integer i : type_Person) 
			{
				tmList.add(new TypeMapping(i,"Person",1));
			}
			//"who" can also means organization
			for (Integer i : type_Organisation) 
			{
				tmList.add(new TypeMapping(i,"Organization",1));
			}
			return tmList;
		}
		else if (wordSpecial.toLowerCase().equals("where")) 
		{
			for (Integer i : type_Place) 
			{
				tmList.add(new TypeMapping(i,"Place",1));
			}
			for (Integer i : type_Organisation) 
			{
				tmList.add(new TypeMapping(i,"Organization",1));
			}
			return tmList;
		}
		/*
		else if (wordSpecial.toLowerCase().equals("when")) {
			ArrayList<Integer> ret = new ArrayList<Integer>();
			ret.add(RelationFragment.literalTypeId);
			return ret;
		}
		*/
		
		return null;
	}
	
	public static void main (String[] args) 
	{
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		String type = "space mission";
		try 
		{
			TypeFragment.load();
			Globals.stopWordsList = new StopWordsList();
			TypeRecognition tr = new TypeRecognition();
			while(true)
			{
				System.out.print("Input query type: ");
				type = br.readLine();
				tr.recognize(type);
			}
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
}
