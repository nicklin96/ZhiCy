package rdf;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;

import nlp.ds.Sentence;
import nlp.ds.Sentence.SentenceType;
import qa.Globals;

public class Sparql implements Comparable<Sparql> 
{
	public ArrayList<Triple> tripleList = new ArrayList<Triple>();
	public boolean countTarget = false;
	public String mostStr = null;
	public double score = 0;
	
	public String questionFocus = null;
	
	public Sentence s;
	public HashMap<Integer, SemanticRelation> semanticRelations = null;

	public void addTriple(Triple t) 
	{
		tripleList.add(t);
		score += t.score;
	}
	
	public void delTriple(Triple t)
	{
		tripleList.remove(t);
		score -= t.score;
	}

	@Override
	public String toString() 
	{
		String ret = "";
		for (Triple t : tripleList) {
			ret += t.toString();
			ret += '\n';
		}
		return ret;
	}
	
	//Use to display (can not execute)
	public String toStringForGStore() 
	{
		String ret = "";
		for (Triple t : tripleList) 
		{
			// !Omit obvious LITERAL
			if(t.object.equals("literal_HRZ"))
				continue;
			
			// !Omit some bad TYPEs
			if(t.predicateID==Globals.pd.typePredicateID && Globals.pd.bannedTypes.contains(t.object))
				continue;
			
			ret += t.toStringForGStore();
			ret += '\n';
		}
		return ret;
	}
	
	//Use to execute (select all variables)
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
	
	//Use to execute (select all variables; format 'aggregation' and 'ask')
	public String toStringForVirtuoso(SentenceType sentenceType, String moreThanStr, HashSet<String> variables)
	{
		String ret = "";
		if(variables == null)
			variables = new HashSet<String>();
		
		// prefix
		if (sentenceType==SentenceType.GeneralQuestion)
			ret += "select * where";
		else if(countTarget)
			ret += ("select COUNT(DISTINCT " + questionFocus + ") where");
		else
		{
			// AGG: select question focus
			if(moreThanStr != null || mostStr != null)
				ret += ("select DISTINCT " + questionFocus + " where");
			// BGP: select all variables
			else
			{
				for (Triple t: tripleList)
				{
					if (!t.isSubjConstant()) variables.add(t.subject.replaceAll(" ", "_"));
					if (!t.isObjConstant()) variables.add(t.object.replaceAll(" ", "_"));		
				}
				
				ret += "select ";
				for (String v : variables)
					ret += v + " ";
				ret += "where";
			}
		}					
		ret += "\n{\n";
		if(variables.size() == 0)
			variables.add(questionFocus);
		
		// triples
		for (Triple t : tripleList) 
		{
			if (!t.object.equals("literal_HRZ")) {	// 显式说明的literal类型不用输出
				ret += t.toStringForGStore();
				ret += " .\n";
			}
		}
		ret += "}\n";
		
		// suffix
		if(moreThanStr != null)
		{
			ret += moreThanStr+"\n";
		}
		if(mostStr != null)
		{
			ret += mostStr+"\n";
		}
	
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

	public void adjustTriplesOrder() 
	{
		Collections.sort(this.tripleList);
	}

	public int compareTo(Sparql o) 
	{
		double diff = this.score - o.score;
		if (diff > 0) 
			return -1;
		else if (diff < 0)
			return 1;
		else
			return 0;
	}
	
	public Sparql(){}
	public Sparql(HashMap<Integer, SemanticRelation> semanticRelations) 
	{
		this.semanticRelations = semanticRelations;
	}
	
	public Sparql copy() 
	{
		Sparql spq = new Sparql(this.semanticRelations);
		for (Triple t : this.tripleList)
			spq.addTriple(t);
		return spq;
	}
	
	public void removeLastTriple() 
	{
		int idx = tripleList.size()-1;
		score -= tripleList.get(idx).score;
		tripleList.remove(idx);
	}
	
	public Sparql removeAllTypeInfo () 
	{
		score = 0;
		ArrayList<Triple> newTripleList = new ArrayList<Triple>();
		for (Triple t : tripleList) 
		{	
			if (t.predicateID != Globals.pd.typePredicateID) 
			{
				newTripleList.add(t);
				score += t.score;
			}
		}
		tripleList = newTripleList;
		return this;
	}

};
