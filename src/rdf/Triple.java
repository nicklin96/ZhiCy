package rdf;

import nlp.ds.Word;
import qa.Globals;

public class Triple implements Comparable<Triple>{
	public String subject = null;	// 经过变量分配、映射后的、能够最终输出的subject/object
	public String object = null;
	public int predicateID = -1;
	
	public SemanticRelation semRltn = null;
	public double score = 0;
	public boolean isSubjObjOrderSameWithSemRltn = true;
	public boolean isSubjObjOrderPrefered = false;
	
	public Word typeSubjectWord = null; // for "type" triples only
	
	public Triple (Triple t) {
		subject = t.subject;
		object = t.object;
		predicateID = t.predicateID;
		
		semRltn = t.semRltn;
		score = t.score;
		isSubjObjOrderSameWithSemRltn = t.isSubjObjOrderSameWithSemRltn;
		isSubjObjOrderPrefered = t.isSubjObjOrderPrefered;
	}
	
	public Triple (String s, int p, String o,
			SemanticRelation sr, double sco) {
		subject = s;
		predicateID = p;
		object = o;
		semRltn = sr;
		score = sco;

	}

	public Triple (String s, int p, String o,
			SemanticRelation sr, double sco,boolean isSwap) {
		subject = s;
		predicateID = p;
		object = o;
		semRltn = sr;
		score = sco;
		isSubjObjOrderSameWithSemRltn = isSwap;
	}
	
	public Triple copy() {
		Triple t = new Triple(this);
		return t;
	}
	
	public Triple copySwap() {
		Triple t = new Triple(this);
		String temp;

		temp = t.subject;
		t.subject = t.object;
		t.object = temp;
		
		t.isSubjObjOrderSameWithSemRltn = !this.isSubjObjOrderSameWithSemRltn;
		t.isSubjObjOrderPrefered = !this.isSubjObjOrderPrefered;
		
		return t;
	}
	
	public void addScore(double s) {
		score += s;
	}
	
	public double getScore() {
		return score;
	}
		
	@Override
	public String toString() {
		return "<" + subject + "> <" + Globals.pd.getPredicateById(predicateID) + "> <" + object + ">" + " : " + score;
	}

	public String toStringForGStore() {
		StringBuilder sb = new StringBuilder("");
		
		String _subject;
		if(predicateID == Globals.pd.typePredicateID && subject.contains("|")) _subject = subject.substring(0, subject.indexOf('|'));
		else _subject = subject;
		if(_subject.startsWith("?")) sb.append(_subject+"\t");
		else sb.append("<" + _subject + ">\t");
		
		sb.append("<" + Globals.pd.getPredicateById(predicateID) + ">\t");
		
		String _object;
		if(predicateID == Globals.pd.typePredicateID && object.contains("|")) _object = object.substring(0, object.indexOf('|'));
		else _object = object;
		if(_object.startsWith("?")) sb.append(_object);
		else sb.append("<" + _object + ">");
		
		return sb.toString().replace(' ', '_');
	}
	
	public String toStringWithoutScore() {
		return "<" + subject + "> <" + Globals.pd.getPredicateById(predicateID) + "> <" + object + ">";
	}
	
	public Word getSubjectWord () {
		if (predicateID == Globals.pd.typePredicateID) {
			return typeSubjectWord;
		}
		else {
			if (isSubjObjOrderSameWithSemRltn) return semRltn.arg1Word;
			else return semRltn.arg2Word;			
		}
		
	}
	
	public Word getObjectWord () {
		if (predicateID == Globals.pd.typePredicateID) {
			return typeSubjectWord;
		}
		else {
			if (isSubjObjOrderSameWithSemRltn) return semRltn.arg2Word;
			else return semRltn.arg1Word;
		}
	}
	
	public boolean isSubjConstant () {
		if (predicateID == Globals.pd.typePredicateID) {
			return !subject.startsWith("?");
		}
		else {
			if (isSubjObjOrderSameWithSemRltn) return semRltn.isArg1Constant;
			else return semRltn.isArg2Constant;
		}
	}
	
	public boolean isObjConstant () {
		if (predicateID == Globals.pd.typePredicateID) {
			return !object.startsWith("?");
		}
		else {
			if (isSubjObjOrderSameWithSemRltn) return semRltn.isArg2Constant;
			else return semRltn.isArg1Constant;
		}
	}
	
	public int compareTo(Triple o) {
		// TODO Auto-generated method stub
		return 0;
	}
	
	public void swapSubjObjOrder() {		
		String temp = subject;
		subject = object;
		object = temp;
		isSubjObjOrderSameWithSemRltn = !isSubjObjOrderSameWithSemRltn;
	}
};