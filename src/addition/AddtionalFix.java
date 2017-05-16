package addition;

import java.util.ArrayList;
import java.util.HashMap;

import paradict.PredicateIDAndSupport;
import log.QueryLogger;
import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Word;
import nlp.ds.Sentence.SentenceType;
import qa.Globals;
import qa.extract.TypeRecognition;
import qa.mapping.SemanticItemMapping;
import rdf.EntityMapping;
import rdf.SemanticUnit;
import rdf.Sparql;
import rdf.Triple;
import fgmt.TypeFragment;


public class AddtionalFix 
{
	public HashMap<String, String> pattern2category = new HashMap<String, String>();
	
	public AddtionalFix()
	{
		//base form
		pattern2category.put("endanger", "Endangered_animals");
		pattern2category.put("gangster_from_the_prohibition_era", "Prohibition-era_gangsters");
		pattern2category.put("seven_wonder_of_the_ancient_world", "Seven_Wonders_of_the_Ancient_World");
		pattern2category.put("three_ship_use_by_columbus", "Christopher_Columbus");
		pattern2category.put("13_british_colony", "Thirteen_Colonies");
		//pattern2category.put("battle_in_1836_in_san_antonio", "Battle_of_San_Jacinto");
	}
	
	public void process(QueryLogger qlog)
	{
		fixCategory(qlog);
		oneTriple(qlog);
		oneNode(qlog);
		
		//aggregation
		AggregationRecognition ar = new AggregationRecognition();
		ar.recognize(qlog);
	
	}
	
	public void fixCategory(QueryLogger qlog)
	{
		String var = null, category = null;
		for(SemanticUnit su: qlog.semanticUnitList)
		{
			if(su.centerWord.mayCategory)
			{
				var = "?"+su.centerWord.originalForm;
				category = su.centerWord.category;
			}
		}
		
		if(category != null && var != null)
			for(Sparql spq: qlog.rankedSparqls)
			{
				boolean occured = false;
				for(Triple tri: spq.tripleList)
				{
					if(tri.subject.equals(var))
					{
						occured = true;
						break;
					}
				}
				String oName = category;
				String pName = "subject";
				int pid = Globals.pd.predicate_2_id.get(pName);
				Triple triple =	new Triple(Triple.VAR_ROLE_ID, var, pid, Triple.CAT_ROLE_ID, oName, null, 100);
				spq.addTriple(triple);
			}
	}
	
	/* recognize one-Node query 
	 * 两种情况：1、特殊疑问句|祈使句	2、一般疑问句
	 * 1-1：how many [], highest [] ...  | 对单个 variable（一般为type）添加一些额外限制(aggregation)
	 * 1-2: What is backgammon? | What is a bipolar syndrome? | (有时会把what识别成node，有时则会被消解)查询  ent的定义，无额外限制；目前认为直接返回该 ent 
	 * 1-3: Give me all Seven Wonders of the Ancient World. | 注意Seven Wonders of the Ancient World需要先被识别为ent（实际他是category而不是ent）
 	 * 2-1: Are there any [castles_in_the_United_States](yago:type)
 	 * 2-2：Was Sigmund Freud married? | 谓词易抽取，只是缺少一个variable node
 	 * 2-3：Are penguins endangered? | 蕴含信息，需要变换
	 */ 
	public void oneNode(QueryLogger qlog)
	{
		//避免和ask-one-triple重复，这里先判断一下sparqlList是否为空 |如果有两个或更多的node也退出
		//因为merge words后是从得分低的decision到得分高的来做，低分时可能通过该函数增加一条sparql了，若这里因为sparqlList不为空return则会错过高分（正确）decision
		if(qlog.semanticUnitList.size()>1)
			return;
		
		Word target = qlog.target;
		Word[] words = qlog.s.words;
		if(qlog.s.sentenceType != SentenceType.GeneralQuestion)
		{
			//1-1: how many [type] are there | List all [type]
			if(target.mayType && target.tmList!=null)
			{
				String subName = "?"+target.originalForm;
				String typeName = target.tmList.get(0).typeName;
				Triple triple =	new Triple(Triple.VAR_ROLE_ID, subName, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, typeName, null, 100);
				Sparql sparql = new Sparql();
				sparql.addTriple(triple);
				qlog.rankedSparqls.add(sparql);
			}
			//1-2: What is [ent]?
			else if(target.mayEnt && target.emList != null)
			{
				if(words.length >= 3 && words[0].baseForm.equals("what") && words[1].baseForm.equals("be"))
				{
					int eid = target.emList.get(0).entityID;
					String subName = target.emList.get(0).entityName;
					Triple triple =	new Triple(eid, subName, Globals.pd.typePredicateID, Triple.VAR_ROLE_ID, "?"+target.originalForm, null, target.emList.get(0).score);
					Sparql sparql = new Sparql();
					sparql.addTriple(triple);
					qlog.rankedSparqls.add(sparql);
				}
			}
			//1-3: Give me all Seven Wonders of the Ancient World.
			else if(target.mayCategory && target.category != null)
			{
				String oName = target.category;
				String pName = "subject";
				int pid = Globals.pd.predicate_2_id.get(pName);
				Triple triple =	new Triple(Triple.VAR_ROLE_ID, "?"+target.originalForm, pid, Triple.CAT_ROLE_ID, oName, null, 100);
				Sparql sparql = new Sparql();
				sparql.addTriple(triple);
				qlog.rankedSparqls.add(sparql);
			}
		}
		else 
		{
			if(target.mayEnt && target.emList != null)
			{
				//2-2：Was Sigmund Freud married?
				String relMention = "";
				for(Word word: words)
					if(word != target && !word.baseForm.equals(".") && !word.baseForm.equals("?"))
						relMention += word.baseForm+" ";
				if(relMention.length() > 1)
					relMention = relMention.substring(0, relMention.length()-1);
				
				ArrayList<PredicateIDAndSupport> pmList = null;
				if(Globals.pd.nlPattern_2_predicateList.containsKey(relMention))
					pmList = Globals.pd.nlPattern_2_predicateList.get(relMention);
				
				if(pmList != null && pmList.size() > 0)
				{
					int pid = pmList.get(0).predicateID;
					int eid = target.emList.get(0).entityID;
					String subName = target.emList.get(0).entityName;
					Triple triple =	new Triple(eid, subName, pid, Triple.VAR_ROLE_ID, "?x", null, 100);
					Sparql sparql = new Sparql();
					sparql.addTriple(triple);
					qlog.rankedSparqls.add(sparql);
				}
		
				//2-3：Are penguins endangered? | 蕴含信息，需要变换
				else
				{
					if(target.position < words.length && pattern2category.containsKey(words[target.position].baseForm))
					{
						String oName = pattern2category.get(words[target.position].baseForm);
						String pName = "subject";
						int pid = Globals.pd.predicate_2_id.get(pName);
						int eid = target.emList.get(0).entityID;
						String subName = target.emList.get(0).entityName;
						Triple triple =	new Triple(eid, subName, pid, Triple.CAT_ROLE_ID, oName, null, 100);
						Sparql sparql = new Sparql();
						sparql.addTriple(triple);
						qlog.rankedSparqls.add(sparql);
					}
				}
			}
			//2-1: Are there any [castles_in_the_United_States](yago:type)
			else if(target.mayType && target.tmList != null)
			{
				String typeName = target.tmList.get(0).typeName;
				String subName = "?" + target.originalForm;
				//System.out.println("typeName="+typeName+" subName="+subName);
				Triple triple =	new Triple(Triple.VAR_ROLE_ID, subName, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, typeName, null, 100);
				Sparql sparql = new Sparql();
				sparql.addTriple(triple);
				qlog.rankedSparqls.add(sparql);
			}
		}
		/*TODO: 处理target是ent的情况，例如下面；需要一个候选谓词列表
		 * res:Aldi dbo:numberOfLocations ?number .
		 */
	}
	
	/*
	 * 识别出一个triple，但未找到合适relation
	 * 两种情况：1、特殊疑问句	2、一般疑问句
	 * 1-1: What is backgammon? | What is a bipolar syndrome? | 查询  ent的定义，无额外限制；经QALD主办方解释，需要返回 type
	 * 2-1: Is [horse racing] a sport?
	 * */ 
	// TODO：这是用dependency tree来识别ent和type的，需要更新
	// recognize add ASK-one-triple sparql here |这个好像就是处理了  一般疑问句问一个ent的type的情况
	public void oneTriple (QueryLogger qlog)
	{
		if(qlog.s.sentenceType == SentenceType.SpecialQuestion)
		{
			Word[] words = qlog.s.words;
			if(qlog.semanticUnitList.size() == 2)
			{
				Word entWord = null, whWord = null;
				for(int i=0;i<qlog.semanticUnitList.size();i++)
				{
					if(qlog.semanticUnitList.get(i).centerWord.baseForm.startsWith("wh"))
						whWord = qlog.semanticUnitList.get(i).centerWord;
					if(qlog.semanticUnitList.get(i).centerWord.mayEnt)
						entWord = qlog.semanticUnitList.get(i).centerWord;
				}
				//1-1: (what) is [ent]
				if(entWord!=null && whWord!= null && words.length >= 3 && words[0].baseForm.equals("what") && words[1].baseForm.equals("be"))
				{
					int eid = entWord.emList.get(0).entityID;
					String subName = entWord.emList.get(0).entityName;
					//System.out.println("typeName="+typeName+" subName="+subName);
					Triple triple =	new Triple(eid, subName, Globals.pd.typePredicateID, Triple.VAR_ROLE_ID, "?"+whWord.originalForm, null, entWord.emList.get(0).score);
					Sparql sparql = new Sparql();
					sparql.addTriple(triple);
					qlog.rankedSparqls.add(sparql);
				}
				// 特殊处理Category
				// 1: who killed/assassinated [ent]?
				if(entWord!=null && whWord!=null && words.length >= 3 && words[0].baseForm.equals("who") && (words[1].baseForm.equals("kill")||words[1].baseForm.equals("assassinate")))
				{
					if(entWord.emList != null && entWord.emList.size() > 0)
					{
						String oName = entWord.emList.get(0).entityName;
						oName = "Assassination_of_"+oName;
						if(entWord.baseForm.equals("caesar"))
							oName = "Assassins_of_Julius_Caesar";
						String pName = "subject";
						int pid = Globals.pd.predicate_2_id.get(pName);
						Triple triple =	new Triple(Triple.VAR_ROLE_ID, "?"+whWord.originalForm, pid, Triple.CAT_ROLE_ID, oName, null, 10000);
						Sparql sparql = new Sparql();
						sparql.addTriple(triple);
						qlog.rankedSparqls.add(sparql);
					}
				}
			}
	
			
		}

		//2-1: Is horse_racing a sport? 
		if(qlog.s.sentenceType == SentenceType.GeneralQuestion && qlog.s.words.length<=5)
		{
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
}

