package qa.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;

import qa.Globals;
import rdf.Sparql;
import rdf.Triple;
import fgmt.EntityFragment;
import fgmt.RelationFragment;
import fgmt.TypeFragment;
import fgmt.VariableFragment;

/**
 * Notice：one compatiblityChecker can be only used once to check a SPARQL.
 * @author husen
 */
public class CompatibilityChecker {
	
	static int EnumerateThreshold = 1000;
	public EntityFragmentDict efd = null;
	public HashMap<String, VariableFragment> variable_fragment = null;
	
	public CompatibilityChecker(EntityFragmentDict efd) {
		this.efd = efd;
		variable_fragment = new HashMap<String, VariableFragment>();
	}

	// 运行这个函数时，已经通过了第一步碎片检验（即single triple check，”出入边检验“） 
	// 因为isSparqlCompatible2对于3条triple的处理慢到1分钟以上，所以重新实现
	// variable 将落实到 entity，依赖于将entity及邻居编码放入内存；注意 variable 为literal时的情况
	public boolean isSparqlCompatible3 (Sparql spq) 
	{
		boolean[] isFixed = new boolean[spq.tripleList.size()];	// 记录某个triple的compatibility是否已经固定不变，不需要重新检查
		for (int i = 0; i < spq.tripleList.size(); i ++) {
			isFixed[i] = false;
		}
		
		//System.out.println("tripleList size="+spq.tripleList.size());
		Iterator<Triple> it;
		boolean shouldContinue = true;
		// shouldContinue实际是在判断某一个带variable的triple时，更新了variable fragment，即更新了[某个已经过检测的var]的可能types，这样需要按照新的vf从头检验sparql  
		while (shouldContinue) 
		{
			shouldContinue = false;
			it = spq.tripleList.iterator();
			int t_cnt = 0;
			while (it.hasNext()) {
				Triple t = it.next();
				
				//debug..
				//System.out.println("tripleType:"+t+" "+getTripleType(t));
				
				switch (getTripleType(t)) {	
				case 1:	// (1) E1, P, E2	
					if (!isFixed[t_cnt]) 
					{
						int ret = hs_check1_E1PE2(t);
						if (ret == 0)
							isFixed[t_cnt] = true;
						else if (ret == 5)
							return false;
					}
					break;
				case 2:	// (2) E,  P, V
					if(!isFixed[t_cnt])
					{
						int ret = hs_check2_EPV(t);
						if (ret == 5)
							return false;
						else 
						{
							isFixed[t_cnt] = true;	// 此时 V 已确定 entities 或 literal，注意 确定的E和P得到的V 不一定唯一，如 ”xx的starring“有很多 
							if (ret == 1) 
								shouldContinue = true;
						}
					}
					break;
				case 3:	// (3) E,  <type1>, T
					if (!isFixed[t_cnt]) 
					{
						int ret = check3_Etype1T(t);
						if (ret == -2) return false;
						if (ret == 0) isFixed[t_cnt] = true;
					}
					break;
				case 4:	// (4) V,  P, E 
					if(!isFixed[t_cnt])
					{
						int ret = hs_check4_VPE(t);					
						if (ret == 5)
							return false;
						else 
						{
							isFixed[t_cnt] = true;	// 此时 V 已确定 entities 或 literal，注意 确定的E和P得到的V 不一定唯一，如 ”xx的starring“有很多 
							if (ret == 1) 
								shouldContinue = true;
						}
					}
					break;
				case 5:	// (5) V1, P, V2	(最重要，最耗时)
					if(!isFixed[t_cnt])
					{
						int ret = hs_check5_V1PV2(t); 
						if (ret == 5)
							return false;
						else 
						{
							isFixed[t_cnt] = true;	// 暂认为 V1 和 V2 一次即可确定cands，不再重复
							if (ret == 1) 
								shouldContinue = true;
						}
					}
					break;
				case 6:	// (6) V,  <type1>, T
					if (!isFixed[t_cnt]) 
					{
						int ret = hs_check6_Vtype1T(t);
						if (ret == -2) return false;
						else
						{
							isFixed[t_cnt] = true;
							if (ret == 1)
								shouldContinue = true;
						}
					}
					break;
				case 7:
					// do nothing
					break;
				case 8:
				default:
					return false;
				}
				t_cnt ++;
			}
		}
		return true;
	}
	
	/**
	 * 获取Triple类型，对不同类型的triple分类讨论
	 * (1) E1, P, E2
	 * (2) E,  P, V
	 * (3) E,  <type1>, T
	 * (4) V,  P, E
	 * (5) V1, P, V2
	 * (6) V,  <type1>, T
	 * (7) E,  <type1>, V
	 * (8) error
	 * 
	 * E: Entity
	 * P: Predicate (除<type1>以外)
	 * V: Variable
	 * T: Type
	 * 
	 * @param t
	 * @return
	 */
	public int getTripleType (Triple t) {
		if (t.predicateID == Globals.pd.typePredicateID) {
			boolean s = t.subject.startsWith("?");
			boolean o = t.object.startsWith("?");
			if (s && !o) return 6;
			else if (o && !s) return 7;
			else if (!s && !o) return 3;
			else return 8;
		}
		else if (t.subject.startsWith("?")) {
			if (t.object.startsWith("?")) return 5;
			else return 4;
		}
		else {
			if (t.object.startsWith("?")) return 2;
			else return 1;
		}
	}
	
	public int check1_E1PE2(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		EntityFragment E1 = efd.getEntityFragmentByEid(t.subjId);
		EntityFragment E2 = efd.getEntityFragmentByEid(t.objId);
		
		// P ∈ E1.outEdges
		if (Collections.disjoint(pidList, E1.outEdges)) {
			return 5;
		}
		
		// P ∈ E2.inEdges
		if (Collections.disjoint(pidList, E2.inEdges)) {
			return 5;
		}

		// E1和E2的types, 还要与P的一个Fragment两端的types“同时”相等
		Iterator<Integer> it_int = pidList.iterator();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
			Iterator<RelationFragment> it_rln = flist.iterator();
			while (it_rln.hasNext()) {
				RelationFragment rf = it_rln.next();
				if (rf.inTypes.containsAll(E1.types) && E1.types.containsAll(rf.inTypes) 
						&& rf.outTypes.containsAll(E2.types) && E2.types.containsAll(rf.outTypes)) {	// 小心这里是containsAll来判断集合相等
					return 0;
				}
			}
		}
		
		return 5;
	}

	public int hs_check1_E1PE2(Triple t) 
	{
		int pid = t.predicateID;
		EntityFragment E1 = efd.getEntityFragmentByEid(t.subjId);
		EntityFragment E2 = efd.getEntityFragmentByEid(t.objId);

		// E2 是 E1 的一步邻居，并且经过 谓词p
		if(E1.outEntMap.containsKey(E2.eId))
		{
			ArrayList<Integer> pList = E1.outEntMap.get(E2.eId);
			if(pList.contains(pid))
				return 0;
		}
		
		return 5;
	}
	
	public int check2_EPV(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		EntityFragment E = efd.getEntityFragmentByEid(t.subjId);
		VariableFragment V = variable_fragment.get(t.object);
		
		// P ∈ E.outEdges
		if (Collections.disjoint(pidList, E.outEdges)) {
			return 5;
		}

		// P ∈ V.inEdges // 不需要检查，因为它等价于下面一步的检查

		// E和V的types, 要与P的一个Fragment两端的types“同时”相等
		Iterator<Integer> it_int = pidList.iterator();
		ArrayList<HashSet<Integer>> newCandTypes = new ArrayList<HashSet<Integer>>();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
			if(flist == null || flist.size()==0)
				return 0;
			Iterator<RelationFragment> it_rln = flist.iterator();
			while (it_rln.hasNext()) {
				RelationFragment rf = it_rln.next();
				boolean entityokay = rf.inTypes.containsAll(E.types) && E.types.containsAll(rf.inTypes);
				if (V == null && entityokay) {
					newCandTypes.add(rf.outTypes);
				}
				else if (entityokay && V.containsAll(rf.outTypes)) {
					newCandTypes.add(rf.outTypes);
				}
			}
		}
		
		if (newCandTypes.size() > 0) {
			if (V == null) {
				variable_fragment.put(t.object, new VariableFragment());
				variable_fragment.get(t.object).candTypes = newCandTypes;
				return 1;
			}
			else {
				if (variable_fragment.get(t.object).candTypes.size() > newCandTypes.size()) {
					variable_fragment.get(t.object).candTypes = newCandTypes;
					return 1;
				}
				else return 0;
			}
		}
		else return 5;
	}
	
	public int hs_check2_EPV(Triple t) 
	{
		int pid = t.predicateID;
		EntityFragment E = efd.getEntityFragmentByEid(t.subjId);
		VariableFragment V = variable_fragment.get(t.object);
		
		// P ∈ E.outEdges
		if (!E.outEdges.contains(pid)) {
			return 5;
		}

		// 确定 V ，注意可能是 literal
		if(V == null)
		{
			variable_fragment.put(t.object, new VariableFragment());
			V = variable_fragment.get(t.object);
			for(int vid: E.outEntMap.keySet())
			{
				if(E.outEntMap.get(vid).contains(pid))
				{
					V.candEntities.add(vid);
				}
			}
			// E的outEdges中有p，但是通过p找不到邻居ent，那么V可能是literal
			if(V.candEntities.size() == 0)
			{
				V.mayLiteral = true;
				return 0;
			}
		}
		else	
		{
			//如果 V 是literal，直接认为可行，因为fragment中未存储literal信息而无法进一步判断
			if(V.mayLiteral)
				return 0;
			
			// 通过当前E的邻居ent更新V的binding，尽量提高效率
			HashSet<Integer> newCandEntities = new HashSet<Integer>();
			if(V.candEntities.size() > 0 && V.candEntities.size() < E.outEntMap.size())
			{
				for(int vid: V.candEntities)
				{
					if(E.outEntMap.containsKey(vid) && E.outEntMap.get(vid).contains(pid))
					{
						newCandEntities.add(vid);
					}
				}
			}
			else
			{
				for(int vid: E.outEntMap.keySet())
				{
					if(E.outEntMap.get(vid).contains(pid) && (V.candEntities.size() == 0 || V.candEntities.contains(vid)))
					{
						newCandEntities.add(vid);
					}
				}
			}
			V.candEntities = newCandEntities;
		}
		
		if(V.candEntities.size() > 0)
			return 0;
		else
			return 5;
	}
	
	public int check3_Etype1T(Triple t) {
		String[] T = t.object.split("\\|");	// 注意"|"需要转义
		EntityFragment E = efd.getEntityFragmentByEid(t.subjId);

		String newTypeString = "";
		boolean contained = false;

		// check whether each type int T is proper for E
		if (T.length == 0) return -2;
		for (String s : T) {
			contained = false;
			for (Integer i : TypeFragment.typeShortName2IdList.get(s)) {
				if (E.types.contains(i)) {
					if (!contained) {
						contained = true;
						newTypeString += s;
						newTypeString += "|";
					}
				}
			}
		}
		
		if (newTypeString.length() > 1) {
			t.object = newTypeString.substring(0, newTypeString.length()-1);
			return 0;
		}
		else return -2;
	}
	
	public int check4_VPE(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		VariableFragment V = variable_fragment.get(t.subject);
		EntityFragment E = efd.getEntityFragmentByEid(t.objId);
		
		// P ∈ E.inEdges 
		if (Collections.disjoint(pidList, E.inEdges)) {
			return 5;
		}

		// P ∈ V.outEdges // 不需要检查，因为它等价于下面一步的检查   // 很好奇怎么检查 变量 的出边 by husen

		// V和E的types, 要与P的一个Fragment两端的types“同时”相等   
		Iterator<Integer> it_int = pidList.iterator();
		ArrayList<HashSet<Integer>> newCandTypes = new ArrayList<HashSet<Integer>>();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
			Iterator<RelationFragment> it_rln = flist.iterator();
			while (it_rln.hasNext()) {
				RelationFragment rf = it_rln.next();
				boolean entityokay = rf.outTypes.containsAll(E.types) && E.types.containsAll(rf.outTypes);
				//System.out.println(rf.outTypes);
				if (V == null && entityokay) {
					newCandTypes.add(rf.inTypes);
				}
				else if (entityokay && V.containsAll(rf.inTypes)) {
					newCandTypes.add(rf.inTypes);
				}
			}
		}
		
		if (newCandTypes.size() > 0) {
			if (V == null) {
				variable_fragment.put(t.subject, new VariableFragment());
				variable_fragment.get(t.subject).candTypes = newCandTypes;
				return 1;
			}
			else {
				if (V.candTypes.size() > newCandTypes.size()) {
					V.candTypes = newCandTypes;
					return 1;
				}
				else return 0;
			}
		}
		else return 5;
	}
	
	public int hs_check4_VPE(Triple t) 
	{
		int pid = t.predicateID;
		EntityFragment E = efd.getEntityFragmentByEid(t.objId);
		VariableFragment V = variable_fragment.get(t.subject);
		TypeFragment subjTf = SemanticItemMapping.getTypeFragmentByWord(t.getSubjectWord());
				
		// P ∈ E.inEdges
		if (!E.inEdges.contains(pid)) {
			return 5;
		}

		// 确定 V ，注意 V 不可能是 literal，因为此时 V 是subject
		if(V == null)
		{
			variable_fragment.put(t.subject, new VariableFragment());
			V = variable_fragment.get(t.subject);
			
			for(int vid: E.inEntMap.keySet())
			{
				if(E.inEntMap.get(vid).contains(pid) && (subjTf == null || subjTf.entSet.contains(vid)))
				{
					V.candEntities.add(vid);
				}
			}
			// E的inEdges中有p，但是通过p找不到邻居ent，此时V是subject不可能是literal，所以匹配失败
			if(V.candEntities.size() == 0)
			{
				return 5;
			}
		}
		else	
		{
			//如果 V 是literal，直接认为错误，因为subject 不可能为 literal
			if(V.mayLiteral)
				return 5;
			
			// 通过当前E的邻居ent更新V的binding，尽量提高效率
			HashSet<Integer> newCandEntities = new HashSet<Integer>();
			if(V.candEntities.size() > 0 && V.candEntities.size() < E.inEntMap.size())
			{
				for(int vid: V.candEntities)
				{
					if(E.inEntMap.containsKey(vid) && E.inEntMap.get(vid).contains(pid))
					{
						newCandEntities.add(vid);
					}
				}
			}
			else
			{
				for(int vid: E.inEntMap.keySet())
				{
					if(E.inEntMap.get(vid).contains(pid) && (V.candEntities.size() == 0 || V.candEntities.contains(vid)))
					{
						newCandEntities.add(vid);
					}
				}
			}
			V.candEntities = newCandEntities;
		}
		
		if(V.candEntities.size() > 0)
			return 0;
		else
			return 5;
	}
	
	public int check5_V1PV2(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		VariableFragment V1 = variable_fragment.get(t.subject);
		VariableFragment V2 = variable_fragment.get(t.object);
		
		// V1和V2的types, 要与P的一个Fragment两端的types“同时”相等
		Iterator<Integer> it_int = pidList.iterator();
		ArrayList<HashSet<Integer>> newCandTypes1 = new ArrayList<HashSet<Integer>>();
		ArrayList<HashSet<Integer>> newCandTypes2 = new ArrayList<HashSet<Integer>>();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
			Iterator<RelationFragment> it_rln = flist.iterator();
			while (it_rln.hasNext()) {
				RelationFragment rf = it_rln.next();
				if (V1 == null && V2 == null) {
					newCandTypes1.add(rf.inTypes);
					newCandTypes2.add(rf.outTypes);
				}
				else if (V1 == null && V2 != null) {
					if (V2.containsAll(rf.outTypes)) {
						newCandTypes1.add(rf.inTypes);
						newCandTypes2.add(rf.outTypes);				
					}
				}
				else if (V2 == null && V1 != null) {
					if (V1.containsAll(rf.inTypes)) {
						newCandTypes1.add(rf.inTypes);
						newCandTypes2.add(rf.outTypes);				
					}
				}
				else {					
					if (V1.containsAll(rf.inTypes) && V2.containsAll(rf.outTypes)) 
					{
						newCandTypes1.add(rf.inTypes);
						newCandTypes2.add(rf.outTypes);				
					}
				}
			}
		}		

		
		if (newCandTypes1.size() > 0 && newCandTypes2.size() > 0) {
			if (V1 == null && V2 == null) {
				variable_fragment.put(t.subject, new VariableFragment());
				variable_fragment.get(t.subject).candTypes = newCandTypes1;
				
				variable_fragment.put(t.object, new VariableFragment());
				variable_fragment.get(t.object).candTypes = newCandTypes2;
				return 1;
			}
			else if (V1 == null && V2 != null) {
				variable_fragment.put(t.subject, new VariableFragment());
				variable_fragment.get(t.subject).candTypes = newCandTypes1;
				
				if (V2.candTypes.size() > newCandTypes2.size()) {
					V2.candTypes = newCandTypes2;
					return 1;
				}
				else return 0;
			}
			else if (V2 == null && V1 != null) {				
				variable_fragment.put(t.object, new VariableFragment());
				variable_fragment.get(t.object).candTypes = newCandTypes2;				

				if (V1.candTypes.size() > newCandTypes1.size()) {
					V1.candTypes = newCandTypes1;
					return 1;
				}
				else return 0;
			}
			else {
				if (V1.candTypes.size() > newCandTypes1.size() || V2.candTypes.size() > newCandTypes2.size()) {
					V1.candTypes = newCandTypes1;
					V2.candTypes = newCandTypes2;
					return 1;
				}
				else return 0;
			}
		}
		else return 5;
	}
	
	public int hs_check5_V1PV2(Triple t) 
	{
		int pid = t.predicateID;
		VariableFragment V1 = variable_fragment.get(t.subject);
		VariableFragment V2 = variable_fragment.get(t.object);
		
		if(V1 == null && V2 == null)	//最糟糕的情况，当前relation fragment没有记录两端entities，若不依赖types则无法check；所以应当把此类triple放在最后
		{
			return 0;	// 这里直接认为正确，也可以返回1 期待该次迭代后续的triple为V1,V2提供cands，下一轮时该triple即可起到check作用
		}
		else if(V2 == null)
		{
			if(V1.mayLiteral)
				return 5;
			
			variable_fragment.put(t.object, new VariableFragment());
			V2 = variable_fragment.get(t.object);
			
			HashSet<Integer> newV1cands = new HashSet<Integer>();
			int cnt = 0;
			for(int v1id: V1.candEntities)
			{
				cnt++;
				if(cnt > EnumerateThreshold)
					break;
				EntityFragment E = efd.getEntityFragmentByEid(v1id);
				if(E != null && E.outEdges.contains(pid))
				{
					newV1cands.add(v1id);
					for(int v2id: E.outEntMap.keySet())
					{
						if(E.outEntMap.get(v2id).contains(pid))
							V2.candEntities.add(v2id);
					}
				}
			}
			V1.candEntities = newV1cands;
		}
		else if(V1 == null)
		{
			if(V2.mayLiteral)
				return 0;
			
			variable_fragment.put(t.subject, new VariableFragment());
			V1 = variable_fragment.get(t.subject);
			
			HashSet<Integer> newV2cands = new HashSet<Integer>();
			int cnt = 0;
			for(int v2id: V2.candEntities)
			{
				cnt++;
				if(cnt > EnumerateThreshold)
					break;
				EntityFragment E = efd.getEntityFragmentByEid(v2id);
				if(E != null && E.inEdges.contains(pid))
				{
					newV2cands.add(v2id);
					for(int v1id: E.inEntMap.keySet())
					{
						if(E.inEntMap.get(v1id).contains(pid))
							V1.candEntities.add(v1id);
					}
				}
			}
			V2.candEntities = newV2cands;
		}
		else
		{
			if(V1.mayLiteral)
				return 5;
			if(V2.mayLiteral)
				return 0;
			
			HashSet<Integer> newV1cands = new HashSet<Integer>();
			HashSet<Integer> newV2cands = new HashSet<Integer>();
			for(int v1id: V1.candEntities)
			{
				EntityFragment E1 = efd.getEntityFragmentByEid(v1id);
				if(E1 != null && E1.outEdges.contains(pid))
					newV1cands.add(v1id);
			}
			V1.candEntities = newV1cands;
			for(int v2id: V2.candEntities)
			{
				EntityFragment E2 = efd.getEntityFragmentByEid(v2id);
				if(E2 != null && E2.inEdges.contains(pid))
					newV2cands.add(v2id);
			}
			V2.candEntities = newV2cands;
			
			newV1cands = new HashSet<Integer>();
			newV2cands = new HashSet<Integer>();
			for(int v1id: V1.candEntities)
			{
				EntityFragment E1 = efd.getEntityFragmentByEid(v1id);
				for(int v2id: V2.candEntities)
				{
					if(E1.outEntMap.containsKey(v2id) && E1.outEntMap.get(v2id).contains(pid))
					{
						newV1cands.add(v1id);
						newV2cands.add(v2id);
					}
				}
			}
			V1.candEntities = newV1cands;
			V2.candEntities = newV2cands;
		}
		
		if(V1.candEntities.size() == 0 || V2.candEntities.size() == 0)
			return 5;
		else
			return 0;
	}
	
	public int check6_Vtype1T(Triple t) {
		
		String[] T = t.object.split("\\|");	// 注意"|"需要转义
		VariableFragment V = variable_fragment.get(t.subject);

		String newTypeString = "";
		boolean contained = false;

		// check whether each type in T is proper for V
		if (T.length == 0) return -2;
		
		ArrayList<HashSet<Integer>> newCandTypes = new ArrayList<HashSet<Integer>>();
		for (String s : T) 
		{
			contained = false;
			
			//yago type等未编号的type，碰到后直接退出，不然后面会异常 |这里返回0是保留这条type三元组，返回-2是删除 husen
			if(!TypeFragment.typeShortName2IdList.containsKey(s))
				return 0;
			
			for (Integer i : TypeFragment.typeShortName2IdList.get(s)) 
			{
				if (V == null) {
					// 通过用户给定的type信息来限制V,由于V的type不一定完全,因此采用特殊记号标记
					HashSet<Integer> set = new HashSet<Integer>();
					set.add(i);
					set.add(VariableFragment.magic_number);
					newCandTypes.add(set);
					if (!contained) {
						contained = true;
						newTypeString += s;
						newTypeString += "|";
					}
				}
				else if (V.contains(i)) {
					if (!contained) {
						contained = true;
						newTypeString += s;
						newTypeString += "|";
					}
				}
			}
		}
		
		// check whether each fragment in V is proper for T
		// if not, delete the fragment (that means we can narrow the scope)
		ArrayList<HashSet<Integer>> deleteCandTypes = new ArrayList<HashSet<Integer>>();
		if (V != null) 
		{
			Iterator<HashSet<Integer>> it = V.candTypes.iterator();
			while(it.hasNext()) {
				HashSet<Integer> set = it.next();
				boolean isCandTypeOkay = false;
				//v 通过其他triple得来的 n种【限制types】 中，至少包含一个T中type的【限制types】可以保留，否则删去该【限制types】
				for (String s : T) 
				{
					for (Integer i : TypeFragment.typeShortName2IdList.get(s)) {
						if (set.contains(i)) {
							isCandTypeOkay = true;
							break;
						}
					}
				}
				if (!isCandTypeOkay) {
					deleteCandTypes.add(set);
				}
			}
			V.candTypes.removeAll(deleteCandTypes);			
		}
		
		
		if (V == null) {
			variable_fragment.put(t.subject, new VariableFragment());
			variable_fragment.get(t.subject).candTypes = newCandTypes;
		}
		if (newTypeString.length() > 1) {
			t.object = newTypeString.substring(0, newTypeString.length()-1);
			if (deleteCandTypes.size() > 0) {
				return 1;
			}
			else {
				return 0;
			}
		}
		else return -2;
	}

	public int hs_check6_Vtype1T(Triple t) 
	{
		String[] tList = t.object.split("\\|");	// 注意"|"需要转义
		VariableFragment V = variable_fragment.get(t.subject);

		if (tList.length == 0) return -2;
		
		// 简化，只考虑第一个type
		// yago type等未编号的type，碰到后直接退出，不然后面会异常 |这里返回0是保留这条type三元组，返回-2是删除 husen
		if(!TypeFragment.typeShortName2IdList.containsKey(tList[0]))
			return 0;
		
		int tid = TypeFragment.typeShortName2IdList.get(tList[0]).get(0);
		TypeFragment T = TypeFragment.typeFragments.get(tid);
		if(V == null)
		{
			variable_fragment.put(t.subject, new VariableFragment());
			V = variable_fragment.get(t.subject);
			V.candEntities = T.entSet;
		}
		else
		{
			if(V.mayLiteral)	//literal做subject又有type，咋可能
				return -2;
			
			HashSet<Integer> newVcands = new HashSet<Integer>();
			for(int vid: V.candEntities)
			{
				EntityFragment E = efd.getEntityFragmentByEid(vid);
				if(E.types.contains(tid))
					newVcands.add(vid);
			}
			V.candEntities = newVcands;	
		}
		
		if(V.candEntities.size() == 0)
			return -2;
		else
			return 0;
	}

	public void swapTriple (Triple t) {
		String temp = t.subject;
		t.subject = t.object;
		t.object = temp;
	}
};