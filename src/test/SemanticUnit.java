package test;

import java.util.ArrayList;
import java.util.HashMap;

import rdf.SemanticRelation;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Word;

public class SemanticUnit 
{
	public Word centerWord = null;
	public ArrayList<DependencyTreeNode> describeNodeList = new ArrayList<DependencyTreeNode>();
	public ArrayList<SemanticUnit> neighborUnitList = new ArrayList<SemanticUnit>();
	public HashMap<Word, SemanticRelation> RelationList = new HashMap<Word, SemanticRelation>();
	
	public boolean isSubj = true;
	public Integer prefferdType = null;
	
	public SemanticUnit(Word center, boolean isSubj)
	{
		centerWord = center;
		isSubj = isSubj;
	}
	
}
