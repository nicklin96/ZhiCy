package qa.evaluation;

import rdf.SemanticRelation;
import log.QueryLogger;

public class BottomUp {
	
	public BottomUp()
	{
		
	}

	public void evaluation(QueryLogger qlog)
	{
		Matches testMatch = new Matches();
		
		// step1: initialize bindings
		for(SemanticRelation sr: qlog.semanticRelations.values())
		{
			testMatch.addEdge(sr);
		}
		
		// step2: select start node (order)
		
		// step3: search & explore
		GraphExplore ge = new GraphExplore();
		ge.search(testMatch);
	}
}
