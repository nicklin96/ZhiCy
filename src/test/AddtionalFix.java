package test;

import java.util.ArrayList;

import log.QueryLogger;
import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Word;
import nlp.ds.Sentence.SentenceType;
import qa.Globals;
import qa.extract.TypeRecognition;
import qa.mapping.SemanticItemMapping;
import rdf.EntityMapping;
import rdf.Sparql;
import rdf.Triple;
import fgmt.TypeFragment;


public class AddtionalFix 
{
	public void process(QueryLogger qlog)
	{
		askOneTriple(qlog);
		oneNode(qlog);
		
		//aggregation
		AggregationRecognition ar = new AggregationRecognition();
		ar.recognize(qlog);
	}
	
	// recognize one-Node query
	public void oneNode(QueryLogger qlog)
	{
		//避免和ask-one-triple重复，这里先判断一下sparqlList是否为空 |如果有两个或更多的node也退出
		if(qlog.rankedSparqls.size()!=0 || qlog.semanticUnitList.size()>1)
			return;
		
		//处理how many [movie] are there | List all [movies]
		Word target = qlog.target;
		if(target.mayType && target.tmList!=null)
		{
			String subName = "?"+target.originalForm;
			String typeName = target.tmList.get(0).typeName;
			Triple triple =	new Triple(Triple.VAR_ROLE_ID, subName, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, typeName, null, 100);
			Sparql sparql = new Sparql();
			sparql.addTriple(triple);
			qlog.rankedSparqls.add(sparql);
		}
		
		/*TODO: 处理target是ent的情况，例如下面；需要一个候选谓词列表
		 * res:Aldi dbo:numberOfLocations ?number .
		 */
	}
	
	// recognize add ASK-one-triple sparql here |这个好像就是处理了  一般疑问句问一个ent的type的情况
	public void askOneTriple (QueryLogger qlog)
	{
		if (!(qlog.s.sentenceType == SentenceType.GeneralQuestion && qlog.s.words.length<=5))
			return;
		
		TypeRecognition tr = new TypeRecognition();
		DependencyTree dependencyTree = qlog.isMaltParserUsed ? qlog.s.dependencyTreeMalt : qlog.s.dependencyTreeStanford;
		String type = dependencyTree.getRoot().word.getNnHead().getFullEntityName();
		
		Word sub = null;
		for (DependencyTreeNode dtn : qlog.s.dependencyTreeMalt.nodesList)		
			if (Globals.pd.relns_subject.contains(dtn.dep_father2child))
			{
				sub = dtn.word.getNnHead();
				break;
			}
		ArrayList<EntityMapping> emList=null;
		SemanticItemMapping sim = new SemanticItemMapping();
		emList = sim.getEntityIDsAndNames(sub, qlog);
		ArrayList<Integer> typeList = tr.recognize(type);
		if (typeList!=null && !typeList.isEmpty() &&
			emList!=null && !emList.isEmpty())
		{
			String typeName = TypeFragment.typeId2ShortName.get(typeList.get(0));
			int eid = emList.get(0).entityID;
			String subName = emList.get(0).entityName;
			//System.out.println("typeName="+typeName+" subName="+subName);
			Triple triple =	new Triple(eid, subName, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, typeName, null, 100);
			Sparql sparql = new Sparql();
			sparql.addTriple(triple);
			qlog.rankedSparqls.add(sparql);
		}		
	}
}

