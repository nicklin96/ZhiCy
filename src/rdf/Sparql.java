package rdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import nlp.ds.Sentence;
import qa.Globals;

public class Sparql implements Comparable<Sparql> 
{
	public ArrayList<Triple> tripleList = new ArrayList<Triple>();
	public boolean countTarget = false;
	public String mostStr = null;
	public double score = 0;
	int isSubjObjOrderPreferedCount = 0;
	
	public String questionFocus = null;
	
	public Sentence s;
	public HashMap<Integer, SemanticRelation> semanticRelations = null;

	public void addTriple(Triple t) {
		tripleList.add(t);
		score += t.score;
	}

	@Override
	public String toString() {
		String ret = "";
		for (Triple t : tripleList) {
			ret += t.toString();
			ret += '\n';
		}
		return ret;
	}
	
	public String toStringForGStore() {
		String ret = "";
		for (Triple t : tripleList) 
		{
			// 显式说明的literal类型不用输出
			if(t.object.equals("literal_HRZ"))
				continue;
			
			// 抛弃一些没意义且总出错的type
			if(t.predicateID==Globals.pd.typePredicateID && Globals.pd.bannedTypes.contains(t.object))
				continue;
			
			ret += t.toStringForGStore();
			ret += '\n';
			
		}
		return ret;
	}
	
	public String toStringForGStore2()
	{
		String ret = "";
		HashSet<String> variables = new HashSet<String>();
		
		for (Triple t: tripleList)
		{
			if (!t.isSubjConstant()) variables.add(t.subject.replaceAll(" ", "_"));
			if (!t.isObjConstant()) variables.add(t.object.replaceAll(" ", "_"));		
		}
		
		ret += "select ";
		for (String v : variables)
			ret += v + " ";
		ret += "where\n{\n";
		for (Triple t : tripleList) 
		{
			if (!t.object.equals("literal_HRZ")) {	// 显式说明的literal类型不用输出
				ret += t.toStringForGStore();
				ret += " .\n";
			}
		}
		ret += "}.";
		
		return ret;
	}
	
	public int getVariableNumber()
	{
		int res = 0;
		for (Triple t: tripleList)
		{
			if (!t.isSubjConstant()) res++;
			if (!t.isObjConstant()) res++;			
		}
		return res;
	}

	public static void sortArrayList(ArrayList<Sparql> list) {
		//Collections.sort(list, new SparqlComparator());
		Collections.sort(list);
	}

	public int compareTo(Sparql o) {
		// 小心!大于\等于\小于0的情况,都要考虑!
		double diff = this.score - o.score;
		if (diff < 0) {
			return -1;
		} else if (diff > 0) {
			return 1;
		}
		else {
			return 0;
		}
	}
	
	public Sparql(){}
	public Sparql(HashMap<Integer, SemanticRelation> semanticRelations) {
		this.semanticRelations = semanticRelations;
	}
	
	public Sparql copy() {
		Sparql spq = new Sparql(this.semanticRelations);
		for (Triple t : this.tripleList) {
			spq.addTriple(t);
		}
		return spq;
	}
	
	public void removeLastTriple() {
		int idx = tripleList.size()-1;
		score -= tripleList.get(idx).score;
		tripleList.remove(idx);
	}
	
	//  将含type边的三元组排在前面，这样有利于提高compatibility checking的效率
	public void typesComesFirst () {
		int p = 0;
		for (int i = 0; i < tripleList.size(); i ++) {
			if (tripleList.get(i).predicateID == Globals.pd.typePredicateID && i > p) {
				// swap
				Triple temp = tripleList.get(i);
				tripleList.set(i, tripleList.get(p));
				tripleList.set(p, temp);
				p ++;
			}
		}
	}
	
	public Sparql removeAllTypeInfo () {
		score = 0;
		ArrayList<Triple> newTripleList = new ArrayList<Triple>();
		for (Triple t : tripleList) {
			if (t.predicateID != Globals.pd.typePredicateID) {
				newTripleList.add(t);
				score += t.score;
			}
		}
		tripleList = newTripleList;
		return this;
	}

};

class SparqlSubjObjComparator implements Comparator<Sparql> {

	public int compare(Sparql n1, Sparql n2) {
		if (n1.isSubjObjOrderPreferedCount > n2.isSubjObjOrderPreferedCount) {
			return -1;
		}
		else if (n2.isSubjObjOrderPreferedCount > n1.isSubjObjOrderPreferedCount) {
			return 1;
		}
		else {
			int size1 = n1.tripleList.size();
			int size2 = n2.tripleList.size();
			if (size1 < size2) {
				return -1;
			}
			else if (size1 > size2) {
				return 1;
			}
			else {
				for (int i = 0; i < size1; i ++) {
					boolean b1 = n1.tripleList.get(i).isSubjObjOrderPrefered;
					boolean b2 = n2.tripleList.get(i).isSubjObjOrderPrefered;
					if (b1 && !b2) {
						return -1;
					}
					else if (!b1 && b2) {
						return 1;
					}
				}
				return -1;
			}
		}
	}	
};