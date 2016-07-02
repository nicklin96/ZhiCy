package addition;

import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Word;
import qa.Globals;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.Triple;
import log.QueryLogger;

public class AggregationRecognition {
	
	public void recognize(QueryLogger qlog)
	{
		DependencyTree ds = qlog.s.dependencyTreeStanford;
		if(qlog.isMaltParserUsed)
			ds = qlog.s.dependencyTreeMalt;
		
		Word[] words = qlog.s.words;
		
		// how often | how many
		if(qlog.s.plainText.indexOf("How many")!=-1||qlog.s.plainText.indexOf("How often")!=-1||qlog.s.plainText.indexOf("how many")!=-1||qlog.s.plainText.indexOf("how often")!=-1)
		{
			for(Sparql sp: qlog.rankedSparqls)
			{
				sp.countTarget = true;
				//  How many pages does War and Peace have? --> res:War_and_Peace dbo:numberOfPages ?n . 
				//	 ?uri dbo:populationTotal ?inhabitants . 
				for(Triple triple: sp.tripleList)
				{
					String p = Globals.pd.getPredicateById(triple.predicateID).toLowerCase();
					if(p.contains("number") || p.contains("total") || p.contains("calories"))
					{
						sp.countTarget = false;
					}
				}
			}
		}
		
		// more than [num] [node]
		for(DependencyTreeNode dtn: ds.nodesList)
		{
			if(dtn.word.baseForm.equals("more"))
			{
				if(dtn.father!=null && dtn.father.word.baseForm.equals("than"))
				{
					DependencyTreeNode tmp = dtn.father;
					if(tmp.father!=null && tmp.father.word.posTag.equals("CD") && tmp.father.father!=null && tmp.father.father.word.posTag.startsWith("N"))
					{
						DependencyTreeNode target = tmp.father.father;
						
						// Which caves have more than 3 entrances | entranceCount | filter
						
						if(target.father !=null && target.father.word.baseForm.equals("have"))
						{
							qlog.moreThanStr = "GROUP BY ?" + qlog.target.originalForm + "\nHAVING (COUNT(?"+target.word.originalForm + ") > "+tmp.father.word.baseForm+")";
						}
						else
							qlog.moreThanStr = "FILTER (?"+target.word.originalForm+"> "+tmp.father.word.baseForm+")";
					}
				}
			}
		}
		
		// most
		for(Word word: words)
		{
			if(word.baseForm.equals("most"))
			{
				Word modifiedWord = word.modifiedWord;
				if(modifiedWord != null)
				{
					for(Sparql sp: qlog.rankedSparqls)
					{
						//  Which Indian company has the most employees? --> ... dbo:numberOfEmployees ?n . || ?employees dbo:company ...
						sp.mostStr = "ORDER BY DESC(COUNT(?"+modifiedWord.originalForm+"))\nOFFSET 0 LIMIT 1";
						for(Triple triple: sp.tripleList)
						{
							String p = Globals.pd.getPredicateById(triple.predicateID).toLowerCase();
							if(p.contains("number") || p.contains("total"))
							{
								sp.mostStr = "ORDER BY DESC(?"+modifiedWord.originalForm+")\nOFFSET 0 LIMIT 1";
							}
						}
					}
				}
			}
		}
	}

}
