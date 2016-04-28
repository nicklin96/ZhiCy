package lcn;

import java.util.ArrayList;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.queryParser.ParseException;
import org.apache.lucene.queryParser.QueryParser;
import org.apache.lucene.search.Hits;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;

import qa.Globals;


public class SearchInLiteralSubset {
	public  ArrayList<EntityNameAndScore> searchEntity(String literal, double thres1, double thres2, int k) throws Exception{
		
		Hits hits = null;
		String queryString = null;
		Query query = null;
		//IndexSearcher searcher = new IndexSearcher(Globals.localPath+"data\\DBpedia\\lucene\\literalSubset_index");
		IndexSearcher searcher = new IndexSearcher(Globals.localPath+"data/DBpedia3.9/lucene/literalSubset_index");
		
		ArrayList<EntityNameAndScore> entityname = new ArrayList<EntityNameAndScore>(); 
		
		queryString = literal;

		Analyzer analyzer = new StandardAnalyzer();
		try {
			QueryParser qp = new QueryParser("Literal", analyzer);
			query = qp.parse(queryString);
		} catch (ParseException e) {
			e.printStackTrace();
		}
		
		if (searcher != null) {
			hits = searcher.search(query);
			
			//System.out.println("find " + hits.length() + " answars!");
			if (hits.length() > 0) {
				for (int i=0; i<hits.length(); i++) {
				    //System.out.println(i+": "+hits.doc(i).get("EntityName") +";"+ hits.doc(i).get("PredicateId") +";"
				    //		  + hits.doc(i).get("Literal") 
				    //		  + "; Score: " + hits.score(i)
				    //		  + "; Score2: " + hits.score(i)*literalLength/hits.doc(i).get("Literal").length());
					if (i < k) {
					    if(hits.score(i) >= thres1){
							entityname.add(new EntityNameAndScore(hits.doc(i).get("EntityName"), hits.score(i)));
					    }
					    else {
					    	break;
					    }
					}
					else {
					    if(hits.score(i) >= thres2){
							entityname.add(new EntityNameAndScore(hits.doc(i).get("EntityName"), hits.score(i)));
					    }
					    else {
					    	break;
					    }						
					}
				}				
			}
		}
		//Collections.sort(entityname);
		return entityname;	
	}
}
