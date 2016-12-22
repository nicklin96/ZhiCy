package qa.evaluation;

import lcn.EntityFragmentFields;
import fgmt.EntityFragment;
import nlp.ds.Word;
import qa.Globals;
import rdf.EntityMapping;
import rdf.PredicateMapping;
import rdf.SemanticRelation;

public class GraphExplore {

	public void search(Matches match)
	{
		//在上一轮search的结果上继续search，因为加入了新的节点和边
		
		//一条边一条边加入的话，又分三种情况，1.新加入的边和原图不连通  2.连通不成环(即新加入的点只有一个) 3.连通成环（即在已有的两点间连边）
		//现在只考虑 2 情况
		
		//若原图为null，即第一次加入triple
		SemanticRelation sr = match.newAddedSR;
		//从entity的一端进行check
		Word entWord = null;
		boolean isSameOrderWithSr = true;
		if(sr.isArg1Constant && sr.arg1Word.mayEnt)
		{
			entWord = sr.arg1Word;
		}
		else if(sr.isArg2Constant && sr.arg2Word.mayEnt)
		{
			entWord = sr.arg2Word;
		}
			
		if(entWord != null)
		{
			for(EntityMapping em: entWord.emList)
			{
				int entId = em.entityID;
				String entStr = EntityFragmentFields.entityId2Name.get(entId);
				EntityFragment ef1 = EntityFragment.getEntityFragmentByEntityId(entId);
				
				// check，注意现在只解决  variable - entity，即直接可以得出结果
				
				// 认为  subj obj可随意调换顺序，先把 ent 当做subj
				for(PredicateMapping pm: sr.predicateMappings)
				{
					int pId = pm.pid;
					String pStr = Globals.pd.getPredicateById(pId);
				
					if(!ef1.outEdges.contains(pId))
						continue;
					
					for(int objEid: ef1.outEntMap.keySet())
					{
						if(ef1.outEntMap.get(objEid).contains(pId))
						{
							int ansEid = objEid;
							String ansStr = EntityFragmentFields.entityId2Name.get(ansEid);
							
							System.out.println(entStr + "\t" + pStr + "\t" + ansStr);
							return;
						}
					}
				}	
				
				for(PredicateMapping pm: sr.predicateMappings)
				{
					int pId = pm.pid;
					String pStr = Globals.pd.getPredicateById(pId);
					
					if(!ef1.inEdges.contains(pId))
						continue;
					
					for(int subjEid: ef1.inEntMap.keySet())
					{
						if(ef1.inEntMap.get(subjEid).contains(pId))
						{
							int ansEid = subjEid;
							String ansStr = EntityFragmentFields.entityId2Name.get(ansEid);
							
							System.out.println(ansStr + "\t" + pStr + "\t" + entStr);
							return;
						}
					}
				}
			}
		}
		
	}
}
