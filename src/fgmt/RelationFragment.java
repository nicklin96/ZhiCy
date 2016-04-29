package fgmt;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import qa.Globals;

public class RelationFragment extends Fragment {

	public static HashMap<Integer, ArrayList<RelationFragment>> relFragments = null;
	public static HashMap<String, ArrayList<Integer>> relationShortName2IdList = null;
	public static HashSet<Integer> literalRelationSet = null;
	
	public HashSet<Integer> inTypes = new HashSet<Integer>();
	public HashSet<Integer> outTypes = new HashSet<Integer>();
	
	public static final int literalTypeId = -176;
	
	public RelationFragment(String inFgmt, String outFgmt, int fid) {
		fragmentId = fid;
		fragmentType = typeEnum.RELATION_FRAGMENT;
		String[] nums;
		
		// in
//nums = inFgmt.split(",");
		nums = inFgmt.split(", ");
		for(String s: nums) {
			if(s.length() > 0) {
				inTypes.add(Integer.parseInt(s));
			}
		}
		
		// out
		if(outFgmt.equals("itera")) {	// ����literal���� //֮ǰֱ��ȥ������β
			outTypes.add(literalTypeId);
		}
		else {
//nums = outFgmt.split(",");
			nums = outFgmt.split(", ");
			for(String s: nums) {
				if(s.length() > 0) {
					outTypes.add(Integer.parseInt(s));
				}
			}			
		}
	}
	
	public static void load() throws Exception {
		
		//String filename = Globals.localPath+"data\\DBpedia\\fragments\\predicate_RDF_fragment\\predicate_fragment_nt.txt"; 
		//String filename = Globals.localPath+"data/DBpedia3.9/fragments/predicate_RDF_fragment/predicate_fragment_nt.txt"; 
		
		String filename = Globals.localPath+"data/DBpedia2014/fragments/predicate_RDF_fragment/predicate_fragment.txt"; 
		File file = new File(filename);
		InputStreamReader in = new InputStreamReader(new FileInputStream(file),"utf-8");
		BufferedReader br = new BufferedReader(in);

		relFragments = new HashMap<Integer, ArrayList<RelationFragment>>();
		literalRelationSet = new HashSet<Integer>();
		
		String line;
		while((line = br.readLine()) != null) 
		{
			String[] lines = line.split("\t");
			
			String inString = lines[0].substring(1, lines[0].length()-1);
			int pid = Integer.parseInt(lines[1]);
			String outString = lines[2].substring(1, lines[2].length()-1);
			
			// ��¼��Щ����literal���ԣ�����дʵ�����ǣ������pid�ǿ��ܽ�literal�ģ�Ҳ���ܿ��Խ�ent������Ϊÿ��p�����˺ܶ�fragment
			//if (outString.equals("literal"))	 
			if(outString.equals("itera"))	//literal��ȥ��ͷβ
			{
				literalRelationSet.add(pid);
			}
			
			if(!relFragments.containsKey(pid)) {
				relFragments.put(pid, new ArrayList<RelationFragment>());
			}			
			relFragments.get(pid).add(new RelationFragment(inString, outString, pid));
		}
		
		br.close();
		
		// load BianHao_predicate
		loadId();
	}
	
	public static void loadId() throws IOException {
		
		//String filename = Globals.localPath+"data\\DBpedia\\fragments\\id_mappings\\BianHao_predicate.txt";
		//String filename = Globals.localPath+"data/DBpedia3.9/fragments/id_mappings/DBpedia3.9_fragment_predicates_id.txt";
		
		String filename = Globals.localPath+"data/DBpedia2014/fragments/id_mappings/DBpedia2014_predicates_id.txt";
		File file = new File(filename);
		InputStreamReader in = new InputStreamReader(new FileInputStream(file),"utf-8");
		BufferedReader br = new BufferedReader(in);

		relationShortName2IdList = new HashMap<String, ArrayList<Integer>>();

		String line;
		while((line = br.readLine()) != null) {
			String[] lines = line.split("\t");
			String rlnShortName = lines[0];	//.substring(1, lines[0].length()-1);
			
			// ����typeShortName��Сд
			if (!relationShortName2IdList.containsKey(rlnShortName)) {
				relationShortName2IdList.put(rlnShortName, new ArrayList<Integer>());
			}
			relationShortName2IdList.get(rlnShortName).add(Integer.parseInt(lines[1]));
		}
				
		br.close();
	}
	
	public static boolean isLiteral (String p) {
		for (Integer i : relationShortName2IdList.get(p)) {
			if (literalRelationSet.contains(i)) return true;
		}
		return false;
	}
	
	public static boolean isLiteral (int pid) {
		if (literalRelationSet.contains(pid)) return true;
		else return false;
	}
}