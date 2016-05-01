package test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;

import paradict.ParaphraseDictionary;
import qa.Globals;
import rdf.Triple;
import lcn.EntityFragmentFields;
import fgmt.EntityFragment;
import fgmt.TypeFragment;
import nlp.ds.Word;
import nlp.tool.CoreNLP;

public class ExtractImplicitRelation {
	
	static final int SamplingNumber = 500;	//��������У���ѡʵ�����ʱ��ѡ��������Ŀ
	static final int k = 3;	//�����ж����ϵʱ��ѡ��ǰtop-k��
	
	//eg: "president Obama", "Andy Liu's Hero(film)".
	public ArrayList<Integer> getPrefferdPidListBetweenTwoConstant(Word w1, Word w2)
	{
		ArrayList<Integer> res = new ArrayList<Integer>();
		int w1Role = 0, w2Role = 0;	// 0:var	1:ent	2:type
		if(w1.mayEnt && w1.emList.size()>0)
			w1Role = 1;
		if(w1.mayType && w1.tmList.size()>0)
			w1Role = 2;
		if(w2.mayEnt && w2.emList.size()>0)
			w2Role = 1;
		if(w2.mayType && w2.tmList.size()>0)
			w2Role = 2;
			
		//�� ���� ����type1 & type2����Ϊ������
		if(w1Role == 0 || w2Role == 0 || (w1Role == 2 && w2Role == 2))
			return null;
		
		//ent1 & ent2
		if(w1Role == 1 && w2Role == 1)
		{
			EntityFragment ef = null;
			
		}
		
		return res;
	}
	
	/*
	 * Which is the film directed by Obama and starred by a Chinese ?x
	 * [What] is in a [chocolate_chip_cookie]      ?var + ent
What [country] is [Sitecore] from               ?type + ent	= [?var p ent + ?var<-type]
��Czech movies��      Chinese actor            ent + ?type
	 * */
	
	/*
	 * eg��Czech|ent movies|?type	Chinese|ent actor|?type
	 * type���� + entity��ת��Ϊ���type����entities���ent�Ŀ���relation��ȡƵ�� top 3
	 * */
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TypeVariable_Entity(Integer typeId, Integer entId)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		
		TypeFragment tf = TypeFragment.typeFragments.get(typeId);
		if(tf == null)
		{
			System.out.println("Error in getPrefferdPidListBetween_TypeVariable_Entity ��Type no fragments.");
			return null;
		}
		
		// ���ѡ������͵��ֱ��˳��ѡ�����ɸ���type������ent��ͳ�ƿ��ܵ�relaiton
		int samplingCnt = 0;
		HashMap<ImplicitRelation, Integer> irCount = new HashMap<ImplicitRelation, Integer>();
		for(int candidateEid: tf.entSet)
		{
			if(!EntityFragmentFields.entityId2Name.containsKey(candidateEid))
				continue;
			if(samplingCnt++ > SamplingNumber)
				break;
	
			ArrayList<ImplicitRelation> tmp = getPrefferdPidListBetween_TwoEntities(candidateEid, entId);
			for(ImplicitRelation ir: tmp)
			{
				//��type����ent�滻��?type
				if(ir.subjId == candidateEid)
					ir.setSubjectId(Triple.VAR_ROLE_ID);
				else if(ir.objId == candidateEid)
					ir.setObjectId(Triple.VAR_ROLE_ID);
				
				if(irCount.containsKey(ir))
					irCount.put(ir, irCount.get(ir)+1);
				else
					irCount.put(ir, 1);
			}
		}
		
		//sort, get top-k
		ByValueComparator bvc = new ByValueComparator(irCount);
		List<ImplicitRelation> keys = new ArrayList<ImplicitRelation>(irCount.keySet());
        Collections.sort(keys, bvc);
        for(ImplicitRelation ir: keys)
        {
        	res.add(ir);
        	if(res.size() >= k)
        		break;
        }
    	
		return res;
	}
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TypeVariable_Entity(String typeName, String entName)
	{
		if(!TypeFragment.typeShortName2IdList.containsKey(typeName) || !EntityFragmentFields.entityName2Id.containsKey(entName))
			return null;
		return getPrefferdPidListBetween_TypeVariable_Entity(TypeFragment.typeShortName2IdList.get(typeName).get(0), EntityFragmentFields.entityName2Id.get(entName));
	}
	
	/*
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TypeVariable_Entity(String typeName, String entNameInLucene)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		
		int typeId = -1;
		TypeFragment tf = null;
		if(TypeFragment.typeShortName2IdList.containsKey(typeName))
		{
			typeId = TypeFragment.typeShortName2IdList.get(typeName).get(0);
			tf = TypeFragment.typeFragments.get(typeId);
		}
		
		if(typeId == -1 || tf == null)
		{
			System.out.println("Error in getPrefferdPidListBetween_TypeVariable_Entity, Type invalid��" + typeName);
			return null;
		}
		
		// ���ѡ������͵��ֱ��˳��ѡ�����ɸ���type������ent��ͳ�ƿ��ܵ�relaiton
		int samplingCnt = 0;
		HashMap<ImplicitRelation, Integer> irCount = new HashMap<ImplicitRelation, Integer>();
		for(int eid: tf.entSet)
		{
			if(!Globals.entityId2Name.containsKey(eid))
				continue;
			if(samplingCnt++ > SamplingNumber)
				break;
			
			//ע�⣬ent2Name��ʵ��ent name����lucen��洢����splitted name����Ƭ�ϸ�ƥ��Ҫ��ʹ��lucene�ĸ�ʽ
			String ent2Name = Globals.entityId2Name.get(eid);
			String ent2NameInLucene = ent2Name.replace("____", " ").replace("__", " ").replace("_", " ");
			ArrayList<ImplicitRelation> tmp = getPrefferdPidListBetween_TwoEntities(entNameInLucene, ent2NameInLucene);
			for(ImplicitRelation ir: tmp)
			{
				//��type����ent�滻��?type
				if(ir.subj.equals(ent2NameInLucene))
					ir.setSubject(typeName);
				else if(ir.obj.equals(ent2NameInLucene))
					ir.setObject(typeName);
				
				if(irCount.containsKey(ir))
					irCount.put(ir, irCount.get(ir)+1);
				else
					irCount.put(ir, 1);
			}
		}
		
		//sort, get top-k
		ByValueComparator bvc = new ByValueComparator(irCount);
		List<ImplicitRelation> keys = new ArrayList<ImplicitRelation>(irCount.keySet());
        Collections.sort(keys, bvc);
        for(ImplicitRelation ir: keys)
        {
        	res.add(ir);
        	if(res.size() >= k)
        		break;
        }
    
			
		return res;
	}*/
	
	static class ByValueComparator implements Comparator<ImplicitRelation> {
        HashMap<ImplicitRelation, Integer> base_map;
  
        public ByValueComparator(HashMap<ImplicitRelation, Integer> base_map) {
            this.base_map = base_map;
        }
 
        public int compare(ImplicitRelation arg0, ImplicitRelation arg1) {
            if (!base_map.containsKey(arg0) || !base_map.containsKey(arg1)) {
                return 0;
            }
 
            if (base_map.get(arg0) < base_map.get(arg1)) {
                return 1;
            } 
            else if (base_map.get(arg0) == base_map.get(arg1)) 
            {
            	return 0;
            } 
            else {
                return -1;
            }
        }
    }
	
	/*
	 * eg��[What] is in a [chocolate_chip_cookie]
	 * ����û���κ������Ϣ�������ݣ����ǵ�����ͨ�� entity���в²�
	 * Ŀǰ�²ⷽ���ǣ�����entityƵ���������  �� Ƶ�����ĳ���, ��ʵ��Ƶ��ֻͳ��������ent�ıߣ����literal�߳��ֶ�Σ�Ҳֻ����һ��
	 * */
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_Entity_Variable(Integer entId, String var)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		
		EntityFragment ef = null;
		ef = EntityFragment.getEntityFragmentByEntityId(entId);
		
		if(ef == null)
		{
			System.out.println("Error in getPrefferdPidListBetween_Entity_Variable: Entity No Fragments!");
			return null;
		}
			
		// find most frequent inEdge
		int pid = findMostFrequentEdge(ef.inEntMap, ef.inEdges);
		if(pid != -1)
			res.add(new ImplicitRelation(Triple.VAR_ROLE_ID, entId, pid, 100));
		
		// find most frequent outEdge
		pid = findMostFrequentEdge(ef.outEntMap, ef.outEdges);
		if(pid != -1)
			res.add(new ImplicitRelation(entId, Triple.VAR_ROLE_ID, pid, 100));
			
		return res;
	}
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_Entity_Variable(String entName, String var)
	{
		return getPrefferdPidListBetween_Entity_Variable(EntityFragmentFields.entityName2Id.get(entName), var);
	}
	
	public int findMostFrequentEdge(HashMap<Integer, ArrayList<Integer>> entMap, HashSet<Integer> edges)
	{
		int mfPredicateId = -1, maxCount = 0;
		HashMap<Integer, Integer> edgeCount = new HashMap<Integer, Integer>();
		for(int key: entMap.keySet())
		{
			for(int edge: entMap.get(key))
			{
				if(!edgeCount.containsKey(edge))
					edgeCount.put(edge, 1);
				else
					edgeCount.put(edge, edgeCount.get(edge)+1);
				if(maxCount < edgeCount.get(edge))
				{
					maxCount = edgeCount.get(edge);
					mfPredicateId = edge;
				}
			}
		}
		
		return mfPredicateId;
	}

	// ������������Ǻ��б�Ҫ��ֻ������һ�����type triple�����ã���check�׶εĹ����ظ�
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TypeConstant_Entity(Integer typeId, Integer entId)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		TypeFragment tf = TypeFragment.typeFragments.get(typeId);
		
		if(tf == null)
		{
			System.out.println("Error in getPrefferdPidListBetween_TypeConstant_Entity: Type No Fragments!");
			return null;
		}
			
		// subj : ent1
		if(tf.entSet.contains(entId))
		{
			//�ݶ�100�֣�֮����¸��ֲ���
			ImplicitRelation  ir = new ImplicitRelation(entId, typeId, Globals.pd.typePredicateID, 100);
			res.add(ir);
		}
			
		return res;
	}
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TwoEntities(String eName1, String eName2)
	{
		return getPrefferdPidListBetween_TwoEntities(EntityFragmentFields.entityName2Id.get(eName1), EntityFragmentFields.entityName2Id.get(eName2));
	}
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TwoEntities(Integer eId1, Integer eId2)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		EntityFragment ef1 = null, ef2 = null;
		ef1 = EntityFragment.getEntityFragmentByEntityId(eId1);
		ef2 = EntityFragment.getEntityFragmentByEntityId(eId2);
		
		if(ef1 == null || ef2 == null)
		{
			System.out.println("Error in GetPrefferdPidListBetweenTwoEntities: Entity No Fragments!");
			return null;
		}
			
		// subj : ent1
		if(ef1.outEntMap.containsKey(eId2))
		{
			ArrayList<Integer> pidList = ef1.outEntMap.get(eId2);
			for(int pid: pidList)
			{
				//�ݶ�100�֣�֮����¸��ֲ���
				ImplicitRelation  ir = new ImplicitRelation(eId1, eId2, pid, 100);
				res.add(ir);
			}
		}
		// subj : ent2
		else if(ef2.outEntMap.containsKey(eId1))
		{
			ArrayList<Integer> pidList = ef2.outEntMap.get(eId1);
			for(int pid: pidList)
			{
				ImplicitRelation ir = new ImplicitRelation(eId2, eId1, pid, 100);
				res.add(ir);
			}
		}
			
		return res;
	}
	
	public static void main(String[] args) throws Exception {
		
		Globals.coreNLP = new CoreNLP();
		Globals.pd = new ParaphraseDictionary();
		try 
		{
			EntityFragmentFields.load();
			TypeFragment.load();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	
		ExtractImplicitRelation eir = new ExtractImplicitRelation();
		BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
		
		String name1,name2;
		while(true)
		{
			System.out.println("Input two node to extract their implicit relations:");
			name1 = br.readLine();
			name2 = br.readLine();
			
			ArrayList<ImplicitRelation> irList = null;
			
//			irList = eir.getPrefferdPidListBetween_TwoEntities(name1, name2);
//			if(irList == null || irList.size()==0)
//				System.out.println("Can't find!");
//			else
//			{
//				for(ImplicitRelation ir: irList)
//				{
//					int pId = ir.pId;
//					String p = Globals.pd.getPredicateById(pId);
//					System.out.println(ir.subjId+"\t"+p+"\t"+ir.objId);
//					System.out.println(ir.subj+"\t"+p+"\t"+ir.obj);
//				}
//			}
			
//			irList = eir.getPrefferdPidListBetween_TypeConstant_Entity(name1, name2);
//			if(irList == null || irList.size()==0)
//				System.out.println("Can't find!");
//			else
//			{
//				for(ImplicitRelation ir: irList)
//				{
//					int pId = ir.pId;
//					String p = Globals.pd.getPredicateById(pId);
//					System.out.println(ir.subj+"\t"+p+"\t"+ir.obj);
//				}
//			}
			
			irList = eir.getPrefferdPidListBetween_TypeVariable_Entity(name1, name2);
			if(irList == null || irList.size()==0)
				System.out.println("Can't find!");
			else
			{
				for(ImplicitRelation ir: irList)
				{
					int pId = ir.pId;
					String p = Globals.pd.getPredicateById(pId);
					System.out.println(ir.subjId+"\t"+p+"\t"+ir.objId);
				}
			}
		}
	}
}