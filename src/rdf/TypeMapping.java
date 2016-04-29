package rdf;

import qa.Globals;

public class TypeMapping implements Comparable<TypeMapping> 
{
	public Integer typeID = null;
	public String typeName = null;
	public double score = 0;
	
	/*
	 * ��Ϊ�����ˡ�αtype���ĸ������Ҫ�����Ӧ��relaiton
	 * �����KB�еı�׼type����ôrelaiton����<type>
	 * �������type��������Ҫ����triple�ģ�Ҫ����relaion�����硱Which professional surfers were born in Australia?����?uri dbo:occupation res:Surfing ��relation��Ҫдdbo:occupation��id
	 * �������type���õ����Բ�����triple��(KB��û�����type)������-1������Who was the father of Queen Elizabeth II�е�Queen
	 * typeidΪ-1�����Ǳ�׼type
	 * */
	public int prefferdRelation = Globals.pd.typePredicateID; 
	
	public TypeMapping(Integer tid, String type, double sco) 
	{
		typeID = tid;
		typeName = type;
		score = sco;
	}
	
	//���� ��queen������Ҫ����triple����Ϊtype����-1,queen,-1,1��
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