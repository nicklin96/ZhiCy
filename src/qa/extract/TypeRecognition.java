package qa.extract;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;

import nlp.ds.Word;
import nlp.tool.StopWordsList;
import fgmt.RelationFragment;
import fgmt.TypeFragment;
import lcn.SearchInTypeShortName;
import log.QueryLogger;
import qa.Globals;
import rdf.SemanticRelation;
import rdf.Triple;
import rdf.TypeMapping;

/*
 * 2016-6-17
 * 1、识别type，包括yago type；
 * 2、手动添加一些type对应，如“USState"-"yago:StatesOfTheUnitedStates"；
 * 3、开始加入一些extend variable，即【自带type的变量】的general版本，【自带triple的变量】；目前主要为形如  ?canadian <birthPlace> <Canada>
 * */
public class TypeRecognition {
	// dbpedia3.9
//	public static final int[] type_Person = {19,20,21};
//	public static final int[] type_Place = {43,45};
//	public static final int[] type_Organisation = {2,12};	
	
	// dbpedia 2014
	public static final int[] type_Person = {180,279};
	public static final int[] type_Place = {49,228};
	public static final int[] type_Organisation = {419,53};
	
	public HashMap<String, String> extendTypeMap = null; 
	public HashMap<String, Triple> extendVariableMap = null;
	
	SearchInTypeShortName st = new SearchInTypeShortName();
	
	public TypeRecognition()
	{
		extendTypeMap = new HashMap<String, String>();
		extendVariableMap = new HashMap<String, Triple>();
		Triple triple = null;
		
		//一些形式上变换的type
		extendTypeMap.put("NonprofitOrganizations", "dbo:Non-ProfitOrganisation");
		extendTypeMap.put("GivenNames", "dbo:GivenName");
		extendTypeMap.put("JamesBondMovies","yago:JamesBondFilms");
		extendTypeMap.put("TVShows", "dbo:TelevisionShow");
		extendTypeMap.put("USState", "yago:StatesOfTheUnitedStates");
		extendTypeMap.put("USStates", "yago:StatesOfTheUnitedStates");
		extendTypeMap.put("Europe", "yago:EuropeanCountries");
		extendTypeMap.put("Africa", "yago:AfricanCountries");
		
		//！！！以下pid，eid基于dbpedia2014，如更换数据集或更新id mapping，需要更新下面ID
		//一些extend variable，即“自带triples的变量”，如：[?E|surfers]-?uri dbo:occupation res:Surfing；canadians：<?canadian>	<birthPlace>	<Canada>
		//1) <?canadians>	<birthPlace>	<Canada> | <xx国人> <birthPlace|1639> <xx国>
		triple = new Triple(Triple.VAR_ROLE_ID, Triple.VAR_NAME, 1639, 2112902, "Canada", null, 100);
		extendVariableMap.put("canadian", triple);
		triple = new Triple(Triple.VAR_ROLE_ID, Triple.VAR_NAME, 1639, 883747, "Germany", null, 100);
		extendVariableMap.put("german", triple);
		//2) ?bandleader	<occupation |　6690>	<Bandleader>
		triple = new Triple(Triple.VAR_ROLE_ID, Triple.VAR_NAME, 6690, 5436853, "Bandleader", null, 100);
		extendVariableMap.put("bandleader", triple);
		triple = new Triple(Triple.VAR_ROLE_ID, Triple.VAR_NAME, 6690, 5436854, "Surfing>", null, 100);
		extendVariableMap.put("surfer", triple);
	}
	
	public void recognizeExtendVariable(Word w)
	{
		String key = w.baseForm;
		if(extendVariableMap.containsKey(key))
		{
			w.mayExtendVariable = true;
			Triple triple = extendVariableMap.get(key).copy();
			if(triple.subjId == Triple.VAR_ROLE_ID && triple.subject.equals(Triple.VAR_NAME))
				triple.subject = "?" + w.originalForm;
			if(triple.objId == Triple.VAR_ROLE_ID && triple.object.equals(Triple.VAR_NAME))
				triple.object = "?" + w.originalForm;
			w.embbededTriple = triple;
		}
	}
	
	public ArrayList<TypeMapping> getExtendTypeByStr(String allUpperFormWord)
	{
		ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
		
		//因为单word的yago type总是很多余，加上后反而查不到结果，例如：Battle, War, Daughter 什么的
		if(allUpperFormWord.length() > 1 && allUpperFormWord.substring(1).equals(allUpperFormWord.substring(1).toLowerCase()))
			return null;
		
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
	 * 这两种情况的分别处理在ExtractRelation -> constantVariableRecognition；本函数现只用来添加疑问词的type信息
	 * */
	public void AddTypesOfWhwords (HashMap<Integer, SemanticRelation> semanticRelations) {
		ArrayList<TypeMapping> ret = null;
		for (Integer it : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(it);
			//对非type节点识别疑问词的type信息
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
