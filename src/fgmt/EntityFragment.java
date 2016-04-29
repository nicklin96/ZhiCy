package fgmt;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;

import rdf.EntityMapping;
import lcn.EntityFragmentFields;
import lcn.EntityNameAndScore;
import lcn.SearchInEntityFragments;

public class EntityFragment extends Fragment {
	
	public int eId;
	public HashSet<Integer> inEdges = new HashSet<Integer>();
	public HashSet<Integer> outEdges = new HashSet<Integer>();
	public HashSet<Integer> types = new HashSet<Integer>();	
	
	// inEnt�Լ�inEnt������Щedge����������� <eId><director><tom> <eId><star><tom>, ��ôinEntMap����һ���� <tom,<director,star>>
	// ������ArrayList����Ϊ�����ļ�Ҳ��ȥ�صģ����Բ���s,p,o���߶�һ��������s,o�̶�����ôp�϶���û���ظ��ġ�
	public HashMap<Integer, ArrayList<Integer>> inEntMap = new HashMap<Integer, ArrayList<Integer>>();
	public HashMap<Integer, ArrayList<Integer>> outEntMap = new HashMap<Integer, ArrayList<Integer>>();
		
	static double thres1 = 0.4;
	static double thres2 = 0.8;
	static int thres3 = 3;
	static int k = 50;
	
	/**
	 * ��һ��phrase(��ʶ��Ϊ����ʵ��)��ӳ�䵽֪ʶ������Ӧ��<entity>
	 * 
	 * ����:
	 * (1)��phraseȥ��subject��, ����"����"���������������г���"��ȷƥ��"������Ҫ���в���(2)��������Ҫ���в���(2)
	 * dropped: (2)��phraseȥ��literal�򣬰���"����"��������ע�⴦����(1)���ظ������Ρ�
	 * 
	 * ����
	 * ÿ��phraseֻȡǰk�������(1)��ǰk������г��ַ�������thres1�ģ������ضϣ������ȥ��
	 * (2)��ǰk����������һ�������Ը���thres2����ȡ��thres2Ϊֹ��
	 * 
	 * ��ȷƥ�䣺
	 * ���ȣ�Lucene score�������1����Σ�����ַ����Ƿ���ȫƥ�䣬(��ת��ΪСд��)�༭���벻������ֵthres3��
	 * 
	 * ������
	 * ֱ����Lucene�ķ������ã���Ϊ���Ѿ��ۺϿ��Ƕ������ء�
	 * 
	 * @param phrase
	 * @return
	 */
	public static HashMap<Integer, Double> getCandEntityNames2(String phrase) {
		
		HashMap<Integer, Double> ret = new HashMap<Integer, Double>();

		// �˴����subject��score���ڵ���thres1��ƥ��
		ArrayList<EntityNameAndScore> list1 = getCandEntityNames_subject(phrase, thres1, thres2, k);
		
		if(list1 == null)
			return ret;
		
		// ���չ�������ֻѡ��ǰk��
		int iter_size = 0;
		if (list1.size() <= k) {
			iter_size = list1.size();
		}
		else if (list1.size() > k) {
			if (list1.get(k-1).score >= thres2) {
				iter_size = list1.size();
			}
			else {
				iter_size = k;
			}
		}
		
		// �������棩ѡ��ǰk��
		for(int i = 0; i < iter_size; i ++) {
			if (i < k) {
				ret.put(list1.get(i).entityID, getScore(phrase, list1.get(i).entityName, list1.get(i).score));
			}
			else if (list1.get(i).score >= thres2) {
				ret.put(list1.get(i).entityID, getScore(phrase, list1.get(i).entityName, list1.get(i).score));
			}
			else {
				break;
			}
		}

		return ret;
	}	
	
	// �ɰ汾����lucene entity nameΪkey�����ڸ�Ϊ��entity idΪkey
//	public static HashMap<String, Double> getCandEntityNames2(String phrase) {
//				
//		HashMap<String, Double> ret = new HashMap<String, Double>();
//
//		// �˴����subject��score���ڵ���thres1��ƥ��
//		ArrayList<EntityNameAndScore> list1 = getCandEntityNames_subject(phrase, thres1, thres2, k);
//		
//		// �Ƿ���ھ�ȷƥ�䣬�����ڣ�ֱ�ӷ��ؾ�ȷƥ��	| ��ȷƥ�䲻һ������Ҫ��
//		/*HashMap<String, Double> exact = getExactMatchings(list1, phrase);
//		if (exact.size() > 0) {
//			System.out.println("PHRASE=\"" + phrase + "\" is EXACTLY mapped to the following entities:");
//			for(String s : exact.keySet()) {
//				System.out.println("\t<" + s + "> " + exact.get(s));
//			}
//			return exact;
//		}*/
//		
//		if(list1 == null)
//			return ret;
//		
//		// ���չ�������ֻѡ��ǰk��
//		int iter_size = 0;
//		if (list1.size() <= k) {
//			iter_size = list1.size();
//		}
//		else if (list1.size() > k) {
//			if (list1.get(k-1).score >= thres2) {
//				iter_size = list1.size();
//			}
//			else {
//				iter_size = k;
//			}
//		}
//		
//		// �������棩ѡ��ǰk��
//		for(int i = 0; i < iter_size; i ++) {
//			if (i < k) {
//				ret.put(list1.get(i).entityName, getScore(phrase, list1.get(i).entityName, list1.get(i).score));
//			}
//			else if (list1.get(i).score >= thres2) {
//				ret.put(list1.get(i).entityName, getScore(phrase, list1.get(i).entityName, list1.get(i).score));
//			}
//			else {
//				break;
//			}
//		}
//		
//		/*
//		System.out.println("PHRASE=\"" + phrase + "\" is mapped to the following "+ret.size()+" entities:");
//		for(String s : ret.keySet()) {
//			System.out.println("\t<" + s + "> " + ret.get(s));
//		}
//		*/
//
//		return ret;
//	}
	
	
	/**
	 * ʹ��ǰ�����뱣֤list�ǴӴ�С����
	 * @param list
	 * @param phrase
	 * @return
	 */
/*	public static HashMap<String, Double> getExactMatchings(ArrayList<EntityNameAndScore> list, String phrase) {
		HashMap<String, Double> ret = new HashMap<String, Double>();
		for (EntityNameAndScore enas : list) {
			if (enas.score < 0.95) {
				break;
			}
			else {
				int ed = calEditDistance(phrase, enas.entityName);
				if (ed<=thres3) {
					// ��ȷƥ���score����������
					ret.put(enas.entityName, enas.score*((double)enas.entityName.length()-ed)/enas.entityName.length());
				}
			}
		}
		
		return ret;
	}*/
	
	public static ArrayList<EntityMapping> getEntityMappingList (String n) 
	{
		HashMap<Integer, Double> map = getCandEntityNames2(n);
		ArrayList<EntityMapping> ret = new ArrayList<EntityMapping>();
		for (int eid : map.keySet()) 
		{
			String s = EntityFragmentFields.entityId2Name.get(eid);
			ret.add(new EntityMapping(eid, s, map.get(eid)));
		}
		Collections.sort(ret);
		return ret;
	}
	
	//�ɰ汾��stringΪkey
//	public static ArrayList<EntityMapping> getEntityMappingList (String n) {
//		HashMap<String, Double> map = getCandEntityNames2(n);
//		ArrayList<EntityMapping> ret = new ArrayList<EntityMapping>();
//		for (String s : map.keySet()) {
//			ret.add(new EntityMapping(s, s, map.get(s)));
//		}
//		Collections.sort(ret);
//		return ret;
//	}
	
	public static double getScore (String s1, String s2, double luceneScore) {
		double ret = luceneScore*100.0/(Math.log(calEditDistance(s1, s2)*1.5+1)+1);
		return ret;
	}
	
	/**
	 * ����༭���룬�����Ǵ�Сд����Сд��Ϊ��ͬ��
	 * @param s1
	 * @param s2
	 * @return
	 */
	public static int calEditDistance (String s1, String s2) {
		s1 = s1.toLowerCase();
		s2 = s2.toLowerCase();
		
		int d[][];//���� 
        int n = s1.length(); 
        int m = s2.length(); 
        int i;    //����str1�� 
        int j;    //����str2�� 
        char ch1;    //str1�� 
        char ch2;    //str2�� 
        int temp;    //��¼��ͬ�ַ�,��ĳ������λ��ֵ������,����0����1 
		
        if(n == 0) { 
            return m; 
        } 
        if(m == 0) { 
            return n; 
        } 

        d = new int[n+1][m+1]; 
        for(i=0; i<=n; i++) {    //��ʼ����һ�� 
            d[i][0] = i; 
        } 
        for(j=0; j<=m; j++) {    //��ʼ����һ�� 
            d[0][j] = j; 
        } 

        for(i=1; i<=n; i++) {    //����str1 
            ch1 = s1.charAt(i-1); 
            //ȥƥ��str2 
            for(j=1; j<=m; j++) { 
                ch2 = s2.charAt(j-1); 
                if(ch1 == ch2) { 
                    temp = 0; 
                } else { 
                    temp = 1; 
                } 
                //���+1,�ϱ�+1, ���Ͻ�+tempȡ��С 
                d[i][j] = min(d[i-1][j]+1, d[i][j-1]+1, d[i-1][j-1]+temp); 
            } 
        } 

	    return d[n][m]; 
	}
	
	private static int min(int a, int b, int c) {
		int ab = a<b?a:b;
		return ab<c?ab:c;
	}	
	
	public static ArrayList<EntityNameAndScore> getCandEntityNames_subject(String phrase, double thres1, double thres2, int k) {
		SearchInEntityFragments sf = new SearchInEntityFragments();
		
		//System.out.println("EntityFragment.getCandEntityNames_subject() ...");
		
		ArrayList<EntityNameAndScore> ret_sf = null;
		try {
			ret_sf = sf.searchName(phrase, thres1, thres2, k);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return ret_sf;
	}
	
//	public static ArrayList<EntityNameAndScore> getCandEntityNames_literal(String phrase, double thres1, double thres2, int k) {
//		SearchInLiteralSubset se = new SearchInLiteralSubset();
//		
//		//System.out.println("EntityFragment.getCandEntityNames_literal() ...");
//		
//		ArrayList<EntityNameAndScore> ret_se = null;
//		try {
//			ret_se = se.searchEntity(phrase, thres1, thres2, k);
//		} catch (Exception e) {
//			e.printStackTrace();
//		}
//		
//		return ret_se;
//	}

	public static EntityFragment getEntityFragmentByEntityId(Integer entityId)
	{
		if(!EntityFragmentFields.entityFragmentString.containsKey(entityId))
			return null;
		String fgmt = EntityFragmentFields.entityFragmentString.get(entityId);
		EntityFragment ef = new EntityFragment(entityId, fgmt);
		return ef;
	}
	
	public static String getEntityFgmtStringByName(String entityName) 
	{
		int id = EntityFragmentFields.entityName2Id.get(entityName);	
		String fgmt = EntityFragmentFields.entityFragmentString.get(id);
		return fgmt;
		
		//�ϰ���룬��lucene��ȡent fragment
//		SearchInEntityFragments sf = new SearchInEntityFragments();
//		EntityFragmentFields eff = null;
//		try {
//			eff = sf.searchFragment(entityName);
//		} catch (IOException e) {
//			e.printStackTrace();
//		}
//		if (eff != null){
//			return eff.fragment;
//		}
//		else {
//			return null;
//		}
	}
	
	public EntityFragment(int eid, String fgmt) 
	{
		eId = eid;
		fragmentType = typeEnum.ENTITY_FRAGMENT;
		
		//eg: 11	|3961112:2881;410;,4641020:2330;,
		fgmt = fgmt.replace('|', '#');
		String[] fields = fgmt.split("#");
		
		if(fields.length > 0 && fields[0].length() > 0) 
		{
			String[] entEdgesArr = fields[0].split(",");
			for(int i = 0; i < entEdgesArr.length; i ++) 
			{
				String[] nums = entEdgesArr[i].split(":");
				if(nums.length != 2)
					continue;
				int intEntId = Integer.valueOf(nums[0]);
				String[] intEdges = nums[1].split(";");
				ArrayList<Integer> intEdgeList = new ArrayList<Integer>();
				for(String outEdge: intEdges)
				{
					intEdgeList.add(Integer.valueOf(outEdge));
				}
				if(intEdgeList.size()>0)
					inEntMap.put(intEntId, intEdgeList);
			}
		}
		
		if(fields.length > 1 && fields[1].length() > 0) 
		{
			String[] entEdgesArr = fields[1].split(",");
			for(int i = 0; i < entEdgesArr.length; i ++) 
			{
				String[] nums = entEdgesArr[i].split(":");
				if(nums.length != 2)
					continue;
				int outEntId = Integer.valueOf(nums[0]);
				String[] outEdges = nums[1].split(";");
				ArrayList<Integer> outEdgeList = new ArrayList<Integer>();
				for(String outEdge: outEdges)
				{
					outEdgeList.add(Integer.valueOf(outEdge));
				}
				if(outEdgeList.size()>0)
					outEntMap.put(outEntId, outEdgeList);
			}
		}
		
		if(fields.length > 2 && fields[2].length() > 0) {
			String[] nums = fields[2].split(",");
			for(int i = 0; i < nums.length; i ++) {
				if (nums[i].length() > 0) {
					inEdges.add(Integer.parseInt(nums[i]));
				}
			}
		}
		if(fields.length > 3 && fields[3].length() > 0) {
			String[] nums = fields[3].split(",");
			for(int i = 0; i < nums.length; i ++) {
				if (nums[i].length() > 0) {
					outEdges.add(Integer.parseInt(nums[i]));
				}
			}
		}
		if(fields.length > 4 && fields[4].length() > 0) {
			String[] nums = fields[4].split(",");
			for(int i = 0; i < nums.length; i ++) {
				if (nums[i].length() > 0) {
					types.add(Integer.parseInt(nums[i]));
				}
			}
		}
	}
	
	@Override
	public String toString() 
	{
		StringBuilder ret = new StringBuilder("");
		for(Integer inEnt: inEntMap.keySet())
		{
			ArrayList<Integer> inEdgeList = inEntMap.get(inEnt);
			if(inEdgeList==null || inEdgeList.size()==0)
				continue;
			ret.append(inEnt+":");
			for(int inEdge: inEdgeList)
				ret.append(inEdge+";");
			ret.append(",");
		}
		ret.append('|');
		for(Integer outEnt: outEntMap.keySet())
		{
			ArrayList<Integer> outEdgeList = outEntMap.get(outEnt);
			if(outEdgeList==null || outEdgeList.size()==0)
				continue;
			ret.append(outEnt+":");
			for(int outEdge: outEdgeList)
				ret.append(outEdge+";");
			ret.append(",");
		}
		ret.append('|');
		for(Integer p : inEdges) {
			ret.append(p);
			ret.append(',');
		}
		ret.append('|');
		for(Integer p : outEdges) {
			ret.append(p);
			ret.append(',');
		}
		ret.append('|');
		for(Integer t : types) {
			ret.append(t);
			ret.append(',');
		}
		return ret.toString();
	}
}