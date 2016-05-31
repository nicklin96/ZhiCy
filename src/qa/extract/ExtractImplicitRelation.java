package qa.extract;

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
import rdf.Sparql;
import rdf.Triple;
import rdf.ImplicitRelation;
import lcn.EntityFragmentFields;
import log.QueryLogger;
import fgmt.EntityFragment;
import fgmt.TypeFragment;
import nlp.ds.Word;
import nlp.tool.CoreNLP;

public class ExtractImplicitRelation {
	
	static final int SamplingNumber = 100;	//计算过程中，候选实体过多时，选择的最大数目
	static final int k = 3;	//可能有多个关系时，选择前top-k个；word可能对应多个ent时，选择前k个
	
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
			
		//有 变量 或者type1 & type2，认为不可能
		if(w1Role == 0 || w2Role == 0 || (w1Role == 2 && w2Role == 2))
			return null;
		
		//ent1 & ent2
		if(w1Role == 1 && w2Role == 1)
		{
			EntityFragment ef = null;
			
		}
		
		return res;
	}
	
	public ArrayList<Triple> supplementTriplesByModifyWord(QueryLogger qlog)
	{
		ArrayList<Triple> res = new ArrayList<Triple>();
		ArrayList<Word> typeVariableList = new ArrayList<Word>();
		
		//修饰词
		for(Word word: qlog.s.words)
		{
			if(word.modifiedWord != null)
			{
				ArrayList<ImplicitRelation> irList = null;
				// ent -> typeVariable | eg: Chinese actor, Czech movies
				if(word.mayEnt && word.modifiedWord.mayType)
				{
					typeVariableList.add(word.modifiedWord);
					int tId = word.modifiedWord.tmList.get(0).typeID;
					String tName = word.modifiedWord.tmList.get(0).typeName;
					for(int i=0; i<k&&i<word.emList.size(); i++)
					{
						int eId = word.emList.get(0).entityID;
						String eName = word.emList.get(0).entityName;
						irList = getPrefferdPidListBetween_Entity_TypeVariable(eId, tId);
						
						if(irList!=null && irList.size()>0)
						{
							ImplicitRelation ir = irList.get(0);
							String subjName = null, objName = null;
							Word subjWord = null, objWord = null;
							if(ir.subjId == eId)
							{
								subjName = eName;
								objName = "?"+tName;
								subjWord = word;
								objWord = word.modifiedWord;
							}
							else
							{
								subjName = "?"+tName;
								objName = eName;
								subjWord = word.modifiedWord;
								objWord = word;
							}
							Triple triple = new Triple(ir.subjId, subjName, ir.pId, ir.objId, objName, null, ir.score, subjWord, objWord);
							res.add(triple);
							break;
						}
					}
				}
			}
		}
		
		if(qlog.rankedSparqls == null || qlog.rankedSparqls.size() == 0)
		{
			if(res != null && res.size() > 0)
			{
				Sparql spq = new Sparql();
				for(Triple t: res)
					spq.addTriple(t);
				
				//因为之前是空集，所以这条 ”ent + type变量“的triple中的type变量并没有抽取出type triple，这里添加进去
				for(Word typeVar: typeVariableList)
				{
					Triple triple =	new Triple(Triple.VAR_ROLE_ID, "?"+typeVar.baseForm, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, typeVar.tmList.get(0).typeName, null, 100);
					spq.addTriple(triple);
				}
				
				qlog.rankedSparqls.add(spq);
			}
			
		}
		else
		{
			for(Sparql spq: qlog.rankedSparqls)
			{
				for(Triple t: res)
					spq.addTriple(t);
			}
		}
		
		return res;
	}
	
	/*
	 * Which is the film directed by Obama and starred by a Chinese ?x
	 * [What] is in a [chocolate_chip_cookie]      ?var + ent
What [country] is [Sitecore] from               ?type + ent	= [?var p ent + ?var<-type]
【Czech movies】      Chinese actor            ent + ?type
	 * */
	
	/*
	 * eg：Czech|ent movies|?type	Chinese|ent actor|?type
	 * type变量 + entity，转化为求该type下属entities与该ent的可能relation，取频繁 top 3
	 * */
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_Entity_TypeVariable(Integer entId, Integer typeId)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		
		TypeFragment tf = TypeFragment.typeFragments.get(typeId);
		EntityFragment ef2 = EntityFragment.getEntityFragmentByEntityId(entId);
		if(tf == null || ef2 == null)
		{
			System.out.println("Error in getPrefferdPidListBetween_TypeVariable_Entity ：Type or Entity no fragments.");
			return null;
		}
		
		// 随机选择（这里偷懒直接顺序选择）若干个该type的下属ent，统计可能的relaiton
		int samplingCnt = 0;
		HashMap<ImplicitRelation, Integer> irCount = new HashMap<ImplicitRelation, Integer>();
		for(int candidateEid: tf.entSet)
		{
			EntityFragment ef1 = EntityFragment.getEntityFragmentByEntityId(candidateEid);
			if(ef1 == null)
				continue;
			
			ArrayList<ImplicitRelation> tmp = getPrefferdPidListBetween_TwoEntities(ef1, ef2);
			if(tmp == null || tmp.size() == 0)	//type的这个下属ent找不到与给出ent2之间的关系，不计数，继续下一个下属ent
				continue;
			
			if(samplingCnt++ > SamplingNumber)
				break;
			
			for(ImplicitRelation ir: tmp)
			{
				//将type下属ent替换回?type
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
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_Entity_TypeVariable(String entName, String typeName)
	{
		if(!TypeFragment.typeShortName2IdList.containsKey(typeName) || !EntityFragmentFields.entityName2Id.containsKey(entName))
			return null;
		return getPrefferdPidListBetween_Entity_TypeVariable(EntityFragmentFields.entityName2Id.get(entName), TypeFragment.typeShortName2IdList.get(typeName).get(0));
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
			System.out.println("Error in getPrefferdPidListBetween_TypeVariable_Entity, Type invalid：" + typeName);
			return null;
		}
		
		// 随机选择（这里偷懒直接顺序选择）若干个该type的下属ent，统计可能的relaiton
		int samplingCnt = 0;
		HashMap<ImplicitRelation, Integer> irCount = new HashMap<ImplicitRelation, Integer>();
		for(int eid: tf.entSet)
		{
			if(!Globals.entityId2Name.containsKey(eid))
				continue;
			if(samplingCnt++ > SamplingNumber)
				break;
			
			//注意，ent2Name是实际ent name，但lucen里存储的是splitted name，碎片严格匹配要求使用lucene的格式
			String ent2Name = Globals.entityId2Name.get(eid);
			String ent2NameInLucene = ent2Name.replace("____", " ").replace("__", " ").replace("_", " ");
			ArrayList<ImplicitRelation> tmp = getPrefferdPidListBetween_TwoEntities(entNameInLucene, ent2NameInLucene);
			for(ImplicitRelation ir: tmp)
			{
				//将type下属ent替换回?type
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
	 * eg：[What] is in a [chocolate_chip_cookie]
	 * 变量没有任何相关信息可以依据，就是单纯的通过 entity进行猜测
	 * 目前猜测方法是：返回entity频率最大的入边  和 频率最大的出边, 事实上频率只统计了连着ent的边，如果literal边出现多次，也只看做一次
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

	// 这个函数好像不是很有必要，只是起了一个检查type triple的作用，和check阶段的功能重复
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
			//暂定100分，之后更新给分策略
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
		EntityFragment ef1 = null, ef2 = null;
		ef1 = EntityFragment.getEntityFragmentByEntityId(eId1);
		ef2 = EntityFragment.getEntityFragmentByEntityId(eId2);
		
		if(ef1 == null || ef2 == null)
		{
			System.out.println("Error in GetPrefferdPidListBetweenTwoEntities: Entity No Fragments!");
			return null;
		}
	
		return getPrefferdPidListBetween_TwoEntities(ef1,ef2);
	}
	
	public ArrayList<ImplicitRelation> getPrefferdPidListBetween_TwoEntities(EntityFragment ef1, EntityFragment ef2)
	{
		ArrayList<ImplicitRelation> res = new ArrayList<ImplicitRelation>();
		if(ef1 == null || ef2 == null)
			return null;
		
		int eId1 = ef1.eId;
		int eId2 = ef2.eId;
		
		// subj : ent1
		if(ef1.outEntMap.containsKey(eId2))
		{
			ArrayList<Integer> pidList = ef1.outEntMap.get(eId2);
			for(int pid: pidList)
			{
				//暂定100分，之后更新给分策略
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
			
			irList = eir.getPrefferdPidListBetween_TwoEntities(name1, name2);
			if(irList == null || irList.size()==0)
				System.out.println("Can't find!");
			else
			{
				for(ImplicitRelation ir: irList)
				{
					int pId = ir.pId;
					String p = Globals.pd.getPredicateById(pId);
					System.out.println(ir.subjId+"\t"+p+"\t"+ir.objId);
					System.out.println(ir.subj+"\t"+p+"\t"+ir.obj);
				}
			}
			
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
			
//			irList = eir.getPrefferdPidListBetween_Entity_Variable(name1, name2);
//			if(irList == null || irList.size()==0)
//				System.out.println("Can't find!");
//			else
//			{
//				for(ImplicitRelation ir: irList)
//				{
//					int pId = ir.pId;
//					String p = Globals.pd.getPredicateById(pId);
//					System.out.println(ir.subjId+"\t"+p+"\t"+ir.objId);
//				}
//			}
			
//			irList = eir.getPrefferdPidListBetween_Entity_TypeVariable(name1, name2);
//			if(irList == null || irList.size()==0)
//				System.out.println("Can't find!");
//			else
//			{
//				for(ImplicitRelation ir: irList)
//				{
//					int pId = ir.pId;
//					String p = Globals.pd.getPredicateById(pId);
//					System.out.println(ir.subjId+"\t"+p+"\t"+ir.objId);
//				}
//			}
		}
	}
}
