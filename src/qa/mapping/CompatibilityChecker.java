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
 * ע�⣺һ��compatiblityCheckerֻ������checkһ��sparql����Ҫ�ظ�ʹ�ã���
 * 
 * @author huangruizhe
 *
 */
public class CompatibilityChecker {
	
	public EntityFragmentDict efd = null;
	public HashMap<String, VariableFragment> variable_fragment = null;
	
	public CompatibilityChecker(EntityFragmentDict efd) {
		this.efd = efd;
		variable_fragment = new HashMap<String, VariableFragment>();
	}

	// �������ʵ��û�б�����  ���� ��ɭ
	// TODO: sparql��ֻҪ�г���80%��triple��compatible�ļ��ɣ���compatible��triple����ɾ��
	public boolean isSparqlCompatible (Sparql spq) {
		boolean[] swapped = new boolean[spq.tripleList.size()];	// ��¼ĳ��triple��subject��object˳���Ƿ񽻻�������������������ܽ����ڶ��Σ���Ҳ��֪���������Բ��ԡ�������
		boolean[] isFixed = new boolean[spq.tripleList.size()];	// ��¼ĳ��triple��compatibility�Ƿ��Ѿ��̶����䣬����Ҫ���¼��
		for (int i = 0; i < spq.tripleList.size(); i ++) {
			swapped[i] = isFixed[i] = false;
		}

		Iterator<Triple> it;
		boolean shouldContinue = true;
		while (shouldContinue) {
			shouldContinue = false;
			it = spq.tripleList.iterator();
			int t_cnt = 0;
			while (it.hasNext()) {
				Triple t = it.next();
				switch (getTripleType(t)) {
				case 1:	// (1) E1, P, E2
					if (!isFixed[t_cnt]) {
						int ret = check1_E1PE2(t);
						if (ret == 0) {
							isFixed[t_cnt] = true;
						}
						else if (ret == 5 && !swapped[t_cnt]) {
							swapTriple(t);
							swapped[t_cnt] = true;
							//shouldContinue = true;
							ret = check1_E1PE2(t);
							if (ret == 0) {
								isFixed[t_cnt] = true;								
							}
							else {
								return false;
							}
						}
					}
					break;
				case 2:	// (2) E,  P, V
					int ret = check2_EPV(t);
					if (ret == 5 && !swapped[t_cnt]) {
						variable_fragment.remove(t.object);
						swapTriple(t);
						swapped[t_cnt] = true;
						shouldContinue = true;
						ret = check4_VPE(t);
						if (ret == 5) return false;
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 3:	// (3) E,  <type1>, T
					if (!isFixed[t_cnt]) {
						ret = check3_Etype1T(t);
						if (ret == -2) return false;
						if (ret == 0) isFixed[t_cnt] = true;
					}
					break;
				case 4:	// (4) V,  P, E
					ret = check4_VPE(t);
					if (ret == 5 && !swapped[t_cnt]) {
						variable_fragment.remove(t.subject);
						swapTriple(t);
						swapped[t_cnt] = true;
						shouldContinue = true;
						ret = check2_EPV(t);
						if (ret == 5) return false;
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 5:	// (5) V1, P, V2
					ret = check5_V1PV2(t);
					if (ret == 5 && !swapped[t_cnt]) {
						variable_fragment.remove(t.subject);
						variable_fragment.remove(t.object);
						swapTriple(t);
						swapped[t_cnt] = true;
						shouldContinue = true;
						ret = check5_V1PV2(t);
						if (ret == 5) return false;						
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 6:	// (6) V,  <type1>, T
					ret = check6_Vtype1T(t);
					if (ret == -2) return false;
					if (ret == 0) isFixed[t_cnt] = true;
					break;
				case 7:
					// do nothing
					break;

				default:
					break;
				}
				t_cnt ++;
			}
		}
		return true;
	}
	
	// �����������ʱ���Ѿ�ͨ���˵�һ����Ƭ���飨���򵥵ġ�����߼��顰�� ���� husen
	// �������棬����ı�spq��Ҳ����˵�����spq������˳�򲻶ԣ��������ǲ��ܵģ�����false��������
	public boolean isSparqlCompatible2 (Sparql spq) {
		boolean[] isFixed = new boolean[spq.tripleList.size()];	// ��¼ĳ��triple��compatibility�Ƿ��Ѿ��̶����䣬����Ҫ���¼��
		for (int i = 0; i < spq.tripleList.size(); i ++) {
			isFixed[i] = false;
		}
		
		//System.out.println("tripleList size="+spq.tripleList.size());
		Iterator<Triple> it;
		boolean shouldContinue = true;
		// shouldContinueʵ�������ж�ĳһ����variable��tripleʱ��������variable fragment����������[ĳ���Ѿ�������var]�Ŀ���types��������Ҫ�����µ�vf��ͷ����sparql  
		while (shouldContinue) {
			shouldContinue = false;
			it = spq.tripleList.iterator();
			int t_cnt = 0;
			while (it.hasNext()) {
				Triple t = it.next();
				
				//debug..
				//System.out.println("tripleType:"+t+" "+getTripleType(t));
				
				switch (getTripleType(t)) {	
				//û���壬�������жϡ�e1������p���͡�e2�����p���͡�P�ĳ���types������E1E2��types������ȫ��ȡ�����������0��������5���°汾ֱ�Ӽ��E1һ���ھӼ��ɡ� 
				case 1:	// (1) E1, P, E2	
					if (!isFixed[t_cnt]) {
						int ret = check1_E1PE2(t);
						if (ret == 0) {
							isFixed[t_cnt] = true;
						}
						else if (ret == 5) {
							return false;
						}
					}
					break;
				case 2:	// (2) E,  P, V
					int ret = check2_EPV(t);
					if (ret == 5) {
						return false;
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 3:	// (3) E,  <type1>, T
					if (!isFixed[t_cnt]) {
						ret = check3_Etype1T(t);
						if (ret == -2) return false;
						if (ret == 0) isFixed[t_cnt] = true;
					}
					break;
				case 4:	// (4) V,  P, E 
					ret = check4_VPE(t);					
					if (ret == 5) {
						return false;
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 5:	// (5) V1, P, V2
					ret = check5_V1PV2(t);
					if (ret == 5) {
						return false;						
					}
					else if (ret == 1) {
						shouldContinue = true;
					}
					break;
				case 6:	// (6) V,  <type1>, T
					if (!isFixed[t_cnt]) {
						ret = check6_Vtype1T(t);
						if (ret == -2) return false;
						if (ret == 0) isFixed[t_cnt] = true;
						//����shouldContinue=1��V��type��Ϣ���¿���Ӱ������triple���жϣ�fixed=1����Ϊ V type T��tripleֻ��Ҫ���һ��
						if (ret == 1) {
							isFixed[t_cnt] = true;
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
	 * ��ȡTriple���ͣ��Բ�ͬ���͵�triple��������
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
	 * P: Predicate (��<type1>����)
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
		
		// P �� E1.outEdges
		if (Collections.disjoint(pidList, E1.outEdges)) {
			return 5;
		}
		
		// P �� E2.inEdges
		if (Collections.disjoint(pidList, E2.inEdges)) {
			return 5;
		}

		// E1��E2��types, ��Ҫ��P��һ��Fragment���˵�types��ͬʱ�����
		Iterator<Integer> it_int = pidList.iterator();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
			Iterator<RelationFragment> it_rln = flist.iterator();
			while (it_rln.hasNext()) {
				RelationFragment rf = it_rln.next();
				if (rf.inTypes.containsAll(E1.types) && E1.types.containsAll(rf.inTypes) 
						&& rf.outTypes.containsAll(E2.types) && E2.types.containsAll(rf.outTypes)) {	// С��������containsAll���жϼ������
					return 0;
				}
			}
		}
		
		return 5;
	}
	
	public int check2_EPV(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		EntityFragment E = efd.getEntityFragmentByEid(t.subjId);
		VariableFragment V = variable_fragment.get(t.object);
		
		// P �� E.outEdges
		if (Collections.disjoint(pidList, E.outEdges)) {
			return 5;
		}

		// P �� V.inEdges // ����Ҫ��飬��Ϊ���ȼ�������һ���ļ��

		// E��V��types, Ҫ��P��һ��Fragment���˵�types��ͬʱ�����
		Iterator<Integer> it_int = pidList.iterator();
		ArrayList<HashSet<Integer>> newCandTypes = new ArrayList<HashSet<Integer>>();
		while (it_int.hasNext()) {
			Integer i = it_int.next();
			ArrayList<RelationFragment> flist = RelationFragment.relFragments.get(i);
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
	
	public int check3_Etype1T(Triple t) {
		String[] T = t.object.split("\\|");	// ע��"|"��Ҫת��
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
		
		// P �� E.inEdges 
		if (Collections.disjoint(pidList, E.inEdges)) {
			return 5;
		}

		// P �� V.outEdges // ����Ҫ��飬��Ϊ���ȼ�������һ���ļ��   // �ܺ�����ô��� ���� �ĳ��� by husen

		// V��E��types, Ҫ��P��һ��Fragment���˵�types��ͬʱ�����   
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
	
	public int check5_V1PV2(Triple t) {
		ArrayList<Integer> pidList = new ArrayList<Integer>();
		pidList.add(t.predicateID);
		VariableFragment V1 = variable_fragment.get(t.subject);
		VariableFragment V2 = variable_fragment.get(t.object);
		
		// V��E��types, Ҫ��P��һ��Fragment���˵�types��ͬʱ�����
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
					if (V1.containsAll(rf.inTypes) && V2.containsAll(rf.outTypes)) {
						if (V2.containsAll(rf.outTypes)) {
							newCandTypes1.add(rf.inTypes);
							newCandTypes2.add(rf.outTypes);
						}						
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
	
	public int check6_Vtype1T(Triple t) {
		
		String[] T = t.object.split("\\|");	// ע��"|"��Ҫת��
		VariableFragment V = variable_fragment.get(t.subject);

		String newTypeString = "";
		boolean contained = false;

		// check whether each type in T is proper for V
		if (T.length == 0) return -2;
		
		ArrayList<HashSet<Integer>> newCandTypes = new ArrayList<HashSet<Integer>>();
		for (String s : T) 
		{
			contained = false;
			
			//yago type��δ��ŵ�type��������ֱ���˳�����Ȼ������쳣 |���ﷵ��0�Ǳ�������type��Ԫ�飬����-2��ɾ�� husen
			if(!TypeFragment.typeShortName2IdList.containsKey(s))
				return 0;
			
			for (Integer i : TypeFragment.typeShortName2IdList.get(s)) 
			{
				if (V == null) {
					// ͨ���û�������type��Ϣ������V,����V��type��һ����ȫ,��˲�������Ǻű��
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
				//v ͨ������triple������ n�֡�����types�� �У����ٰ���һ��T��type�ġ�����types�����Ա���������ɾȥ�á�����types��
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
	
	public void swapTriple (Triple t) {
		String temp = t.subject;
		t.subject = t.object;
		t.object = temp;
	}
};