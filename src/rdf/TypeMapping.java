package rdf;

import qa.Globals;

public class TypeMapping implements Comparable<TypeMapping> 
{
	public Integer typeID = null;
	public String typeName = null;
	public double score = 0;
	
	/*
	 * 因为加入了”伪type“的概念，所以要加入对应的relaiton
	 * 如果是KB中的标准type，那么relaiton还是<type>
	 * 如果是起到type作用且需要加入triple的，要声明relaion，例如”Which professional surfers were born in Australia?“，?uri dbo:occupation res:Surfing ，relation就要写dbo:occupation的id
	 * 如果是起到type作用但可以不加入triple的(KB中没有这个type)，声明-1，例如Who was the father of Queen Elizabeth II中的Queen
	 * typeid为-1代表非标准type
	 * */
	public int prefferdRelation = Globals.pd.typePredicateID; 
	
	public TypeMapping(Integer tid, String type, double sco) 
	{
		typeID = tid;
		typeName = type;
		score = sco;
	}
	
	//例如 ”queen“不需要加入triple但视为type，则（-1,queen,-1,1）
	public TypeMapping(Integer tid, String type, Integer relation, double sco) 
	{
		typeID = tid;
		typeName = type.replace("_", "");
		score = sco;
		prefferdRelation = relation;
	}
	
	// In descending order: big --> small
	public int compareTo(TypeMapping o) 
	{
		double diff = this.score - o.score;
		if (diff > 0) return -1;
		else if (diff < 0) return 1;
		else return 0;
	}
	
	public int hashCode()
	{
		return typeID.hashCode();
	}
	
	public String toString() 
	{
		StringBuilder res = new StringBuilder(typeName+"("+score+")");
		return res.toString();
	}
}