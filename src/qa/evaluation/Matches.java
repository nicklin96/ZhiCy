package qa.evaluation;

import java.util.HashMap;

import rdf.SemanticRelation;
import nlp.ds.Word;

public class Matches {

	HashMap<Word, Binding> nodeBindings = new HashMap<Word, Binding>();
	HashMap<SemanticRelation, Binding> relationBindings = new HashMap<SemanticRelation, Binding>();
	
	// 要有：1、已经匹配好的图结构 + 每个点/边对应的binding；存储binding时又有两种思路：分别存（省空间） or 连起来存（会出现局部信息重复，但始终保持完整信息，便于新限制来时删除）
	// 2、新加入的边（认为至少有一点与原图相连）
	SemanticRelation newAddedSR = null;
	
	
	public void addEdge(SemanticRelation sr)
	{
		newAddedSR = sr;
		
		// edge
		Binding eBinding = new Binding();
		eBinding.isVertexBinding = false;
		eBinding.candPreMappings = sr.predicateMappings;
		
		// subj
		Binding sBinding = new Binding();
		sBinding.isVertexBinding = true;
		if(!sr.isArg1Constant)
		{
			if(!sr.arg1Word.mayType)
				sBinding.canMatchAllVetices = true;
		//	else
		}
		else	// constant
		{
			if(sr.arg1Word.mayEnt)	// ent
			{
				sBinding.candEntMappings = sr.arg1Word.emList;
			}
		//	else
		}
		
		// obj
		Binding oBinding = new Binding();
		oBinding.isVertexBinding = true;
		if(!sr.isArg2Constant)
		{
			if(!sr.arg2Word.mayType)
				oBinding.canMatchAllVetices = true;
		//	else
		}
		else	// constant
		{
			if(sr.arg2Word.mayEnt)	// ent
			{
				oBinding.candEntMappings = sr.arg2Word.emList;
			}
		//	else
		}
		
		nodeBindings.put(sr.arg1Word, sBinding);
		nodeBindings.put(sr.arg2Word, oBinding);
		relationBindings.put(sr, eBinding);
	}
}
