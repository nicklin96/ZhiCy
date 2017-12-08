package qa.parsing;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import fgmt.EntityFragment;
import fgmt.TypeFragment;
import log.QueryLogger;
import nlp.ds.*;
import nlp.ds.Sentence.SentenceType;
import qa.Globals;
import qa.extract.*;
import qa.mapping.SemanticItemMapping;
import rdf.PredicateMapping;
import rdf.Triple;
import rdf.SemanticRelation;
import rdf.SimpleRelation;
import rdf.SemanticUnit;
import paradict.ParaphraseDictionary;

/*
 * The core class to build query graph, i.e, to generate SPARQL queries.
 * */
public class BuildQueryGraph 
{
	public ArrayList<SemanticUnit> semanticUnitList = new ArrayList<SemanticUnit>();
	public ArrayList<String> whList = new ArrayList<String>();
	public ArrayList<String> stopNodeList = new ArrayList<String>();
	public ArrayList<Word> modifierList = new ArrayList<Word>();
	public HashSet<DependencyTreeNode> visited = new HashSet<DependencyTreeNode>();
	public HashMap<Integer, SemanticRelation> matchedSemanticRelations = new HashMap<Integer, SemanticRelation>();
	
	public int aggregationType = -1; // 1:how many  2:latest/first...
	
	public BuildQueryGraph()
	{
		whList.add("what");
		whList.add("which");
		whList.add("who");
		whList.add("whom");
		whList.add("when");
		whList.add("how");
		whList.add("where");
		
		// Bad words for NODE. (base form)
		stopNodeList.add("list");
		stopNodeList.add("give");
		stopNodeList.add("show");
		stopNodeList.add("star");
		stopNodeList.add("theme");
		stopNodeList.add("world");
		stopNodeList.add("independence");
		stopNodeList.add("office");
		stopNodeList.add("year");
		stopNodeList.add("work");
	}
	
	public void fixStopWord(QueryLogger qlog, DependencyTree ds)
	{
		String qStr = qlog.s.plainText.toLowerCase();
		
		//... [which] 
		for(int i=2;i<qlog.s.words.length;i++)
			if(qlog.s.words[i].baseForm.equals("which"))
				stopNodeList.add(qlog.s.words[i].baseForm);
		
		//take [place]
		if(qStr.contains("take place") || qStr.contains("took place"))
			stopNodeList.add("place");
		
		//(When was Alberta admitted) as [province] 
		if(qStr.contains("as province"))
			stopNodeList.add("province");
		
		//what form of government is found in ...
		if(qStr.contains("form of government"))
			stopNodeList.add("government");
		
		//alma mater of the chancellor
		if(qStr.contains("alma mater of the chancellor"))
		{
			stopNodeList.add("chancellor");
		}
		//How large is the area of UK?
		if(qStr.contains("the area of") || qStr.contains("how big"))
		{
			stopNodeList.add("area");
		}
		//how much is the total population of  european union?
		if(qStr.contains("how much"))
		{
			stopNodeList.add("population");
			stopNodeList.add("elevation");
		}
		//when was the founding date of french fifth republic
		if(qStr.contains("when was the"))
		{
			stopNodeList.add("founding");
			stopNodeList.add("date");
			stopNodeList.add("death");
			stopNodeList.add("episode");
		}
		if(qStr.contains("what other book"))
		{
			stopNodeList.add("book");
		}
		//Is [Michelle Obama] the [wife] of Barack Obama?
		if(qlog.s.words[0].baseForm.equals("be") && isNode(ds.getNodeByIndex(2)) && ds.getNodeByIndex(3).dep_father2child.equals("det") 
				&& isNode(ds.getNodeByIndex(4)) && qlog.s.words[4].baseForm.equals("of"))
			stopNodeList.add(ds.getNodeByIndex(4).word.baseForm);
	}

	// Semantic Parsing for DBpedia.
	public ArrayList<SemanticUnit> process(QueryLogger qlog)
	{
		try 
		{
			semanticUnitList = new ArrayList<SemanticUnit>();
			
			DependencyTree ds = qlog.s.dependencyTreeStanford;
			if(qlog.isMaltParserUsed)
				ds = qlog.s.dependencyTreeMalt;
			
			long t = System.currentTimeMillis();
		
/* Prepare for building query graph:  
 * 0)Fix stop nodes.
 * 1)Detect modified node(the center of semantic unit, compose the basic structure of query graph);
 *   Detect modifier (include ent/type/adj, NOT appear in basic structure, may be SUPPLEMNT info of query graph, degree always be 1).
 * 2)Detect the target, also the start node to build query graph.
 * 3)Coreference resolution.
 * */		
			//0) Fix stop words
			fixStopWord(qlog, ds);
			
			//1) Detect Modifier/Modified
			//rely on sentence (rather than dependency tree)
			//with some ADJUSTMENT (eg, ent+noun(noType&&noEnt) -> noun.omitNode=TRUE)
			for(Word word: qlog.s.words)
				getTheModifiedWordBySentence(qlog.s, word);	//Find continuous modifier
			for(Word word: qlog.s.words)
				getDiscreteModifiedWordBySentence(qlog.s, word); //Find discrete modifier
			for(Word word: qlog.s.words)
				if(word.modifiedWord == null)	//Other words modify themselves. NOTICE: only can be called after detecting all modifier.
					word.modifiedWord = word;
			
			//print log
			for(Word word: qlog.s.words) 
			{
				if(word.modifiedWord != null && word.modifiedWord != word)
				{
					modifierList.add(word);
					qlog.SQGlog += "++++ Modify detect: "+word+" --> " + word.modifiedWord + "\n";
				}
			}
			
			//2) Detect target & 3) Coreference resolution 
			DependencyTreeNode target = detectTarget(ds,qlog); 
			qlog.SQGlog += "++++ Target detect: "+target+"\n";
			
			if(target == null)
				return null;
			
			qlog.target = target.word;
			// !target can NOT be entity. (except general question)| which [city] has most people?
			if(qlog.s.sentenceType != SentenceType.GeneralQuestion && target.word.emList!=null) 
			{
				//Counter example：Give me all Seven_Wonders_of_the_Ancient_World | (in fact, it not ENT, but CATEGORY, ?x subject Seve...)
				target.word.mayEnt = false;
				target.word.emList.clear();
			}
			
			//3) Coreference resolution, now we just OMIT the represented one.
			//TODO: In some cases, the two node should be MERGED, instead of OMITTING directly. 
			CorefResolution cr = new CorefResolution();
			
			qlog.timeTable.put("BQG_prepare", (int)(System.currentTimeMillis()-t));
/* Prepare Done */		
			
			t = System.currentTimeMillis();
			DependencyTreeNode curCenterNode = target;
			ArrayList<DependencyTreeNode> expandNodeList;
			Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
			HashSet<DependencyTreeNode> expandedNodes = new HashSet<DependencyTreeNode>();
			queue.add(target);
			expandedNodes.add(target);
			visited.clear();
			
			//step1: build the structure of query graph | notice, we allow CIRCLE and WRONG edge (for evaluation method 2)
			while((curCenterNode = queue.poll()) != null)
			{	
				if(curCenterNode.word.represent != null || cr.getRefWord(curCenterNode.word,ds,qlog) != null )
				{
					//[2017-1-7]如果target就被represent，continue就直接退出了，semanticUnitList=null，尝试加入规则放过who；治标不治本，还是有空做根本改动，在确定结构后再共指消解
					//if(curCenterNode != target)
					//被扩展SU被其他SU代表，则直接略过此次扩展; TODO: 共指消解应该在结构确定后做，直接抛弃可能会丢失部分边信息
					//[2015-12-13]这样相当于丢失了这个方向，沿该SU继续走本来能找到其他点，但直接continue就断绝了找到这些点的希望; 之所以一直没有发现问题是因为绝大多数情况被代表的SU都在query graph的边缘，即不会中断探索
					//[2015-12-13]干脆先剥夺他们在dfs中被探索到的权利，即在isNode中拒绝represent，注意先只针对 represent;  
						continue;
				}					
				
				//Notice, the following codes guarantee all possible edges (allow CIRCLE).
				//Otherwise, NO CIRCLE, and the structure may be different by changing target.
				if(Globals.evaluationMethod > 1)
				{
					visited.clear();
				}
				
				SemanticUnit curSU = new SemanticUnit(curCenterNode.word,true);
				expandNodeList = new ArrayList<DependencyTreeNode>();
				dfs(curCenterNode, curCenterNode, expandNodeList);	// search neighbors of current node
				// expand nodes
				for(DependencyTreeNode expandNode: expandNodeList)
				{
					if(!expandedNodes.contains(expandNode))
					{
						queue.add(expandNode);
						expandedNodes.add(expandNode);
					}
				}
				
				semanticUnitList.add(curSU);
				for(DependencyTreeNode expandNode: expandNodeList)
				{	
					String subj = curCenterNode.word.getBaseFormEntityName();
					String obj = expandNode.word.getBaseFormEntityName();
					
					//omit inner relation
					if(subj.equals(obj))
						continue;
					
					//we just omit represented nodes now.
					//TODO: Co-refernce (continue may not suitable in some cases)
					if(expandNode.word.represent != null)
						continue;
					
					//expandNode is a new SemanticUnit
					SemanticUnit expandSU = new SemanticUnit(expandNode.word,false);
					//expandUnit is the neighbor of current unit
					curSU.neighborUnitList.add(expandSU);
				}
			}
			qlog.timeTable.put("BQG_structure", (int)(System.currentTimeMillis()-t));
			
			//step2: Find relations (Notice, we regard that the coreference have been resolved now)
			t = System.currentTimeMillis();
			qlog.semanticUnitList = new ArrayList<SemanticUnit>();
			extractRelation(semanticUnitList, qlog); // RE for each two connected nodes
			matchRelation(semanticUnitList, qlog);	// Drop the nodes who cannot find relations (except implicit relation)
			qlog.timeTable.put("BQG_relation", (int)(System.currentTimeMillis()-t));
		
			//Prepare for item mapping
			TypeRecognition.AddTypesOfWhwords(qlog.semanticRelations); // Type supplementary
			TypeRecognition.constantVariableRecognition(qlog.semanticRelations, qlog); // Constant or Variable, embedded triples
			
			//(just for display)
			recordOriginalTriples(semanticUnitList, qlog);
				
			//step3: item mapping & top-k join
			t = System.currentTimeMillis();
			SemanticItemMapping step5 = new SemanticItemMapping();
			step5.process(qlog, qlog.semanticRelations);	//top-k join (generate SPARQL queries), disambiguation
			qlog.timeTable.put("BQG_topkjoin", (int)(System.currentTimeMillis()-t));
			
			//step6: implicit relation [modify word]
			t = System.currentTimeMillis();
			ExtractImplicitRelation step6 = new ExtractImplicitRelation();
			step6.supplementTriplesByModifyWord(qlog);
			qlog.timeTable.put("BQG_implicit", (int)(System.currentTimeMillis()-t));
				
		} 
		catch (Exception e) {
			e.printStackTrace();
		}
	
		return semanticUnitList;
	}
	
	public void extractRelation(ArrayList<SemanticUnit> semanticUnitList, QueryLogger qlog)
	{
		ExtractRelation er = new ExtractRelation();
		ArrayList<SimpleRelation> simpleRelations = new ArrayList<SimpleRelation>();
		for(SemanticUnit curSU: semanticUnitList)
		{
			for(SemanticUnit expandSU: curSU.neighborUnitList)
			{
				//Deduplicate | method 1 only can generate DIRECTED edge 
				if(Globals.evaluationMethod > 1 && curSU.centerWord.position > expandSU.centerWord.position)
					continue;
				
				ArrayList<SimpleRelation> tmpRelations = null;
				//get simple relations by PARAPHRASE
				tmpRelations = er.findRelationsBetweenTwoUnit(curSU, expandSU, qlog);
				if(tmpRelations!=null && tmpRelations.size()>0)
					simpleRelations.addAll(tmpRelations);
				else
				{
					tmpRelations = new ArrayList<SimpleRelation>();
					//Copy relations (for 'and', 'as soon as'...) |eg, In which films did Julia_Roberts and Richard_Gere play?
					//TODO: judge by dependency tree | other way to supplement relations
					if(curSU.centerWord.position + 2 == expandSU.centerWord.position && qlog.s.words[curSU.centerWord.position].baseForm.equals("and"))
					{
						for(SimpleRelation sr: simpleRelations)
						{
							if(sr.arg1Word == curSU.centerWord)
							{
								SimpleRelation tsr = new SimpleRelation(sr);
								tsr.arg1Word = expandSU.centerWord;
								tmpRelations.add(tsr);
							}
							else if (sr.arg2Word == curSU.centerWord)
							{
								SimpleRelation tsr = new SimpleRelation(sr);
								tsr.arg2Word = expandSU.centerWord;
								tmpRelations.add(tsr);
							}
						}
						if(tmpRelations.size() > 0)
							simpleRelations.addAll(tmpRelations);
					}
				}
			}
		}
		
		//get semantic relations
		HashMap<Integer, SemanticRelation> semanticRelations = er.groupSimpleRelationsByArgsAndMapPredicate(simpleRelations);
		
		if(Globals.evaluationMethod > 1)
		{
			//TODO: recognize unsteady edge by judging connectivity (now we just recognize all edges are unsteady when it has circle)
			if(semanticRelations.size() >= semanticUnitList.size())	// has CIRCLE
				for(SemanticRelation sr: semanticRelations.values())
				{
					sr.isSteadyEdge = false;
				}
		}
		
		qlog.semanticRelations = semanticRelations;
	}
	
	public void matchRelation(ArrayList<SemanticUnit> semanticUnitList, QueryLogger qlog) 
	{
		//Drop the nodes who cannot find relations (except [modifier] implicit relation)
		for(int relKey: qlog.semanticRelations.keySet())
		{
			boolean matched = false;
			SemanticRelation sr = qlog.semanticRelations.get(relKey);
			for(SemanticUnit curSU: semanticUnitList)
			{
				for(SemanticUnit expandSU: curSU.neighborUnitList)
				{
					//Deduplicate | method 1 only can generate DIRECTED edge 
					if(Globals.evaluationMethod > 1 && curSU.centerWord.position > expandSU.centerWord.position)
						continue;
					
					int key = curSU.centerWord.getNnHead().hashCode() ^ expandSU.centerWord.getNnHead().hashCode();
					if(relKey == key)
					{
						matched = true;
						matchedSemanticRelations.put(relKey, sr);
						if(!qlog.semanticUnitList.contains(curSU))
							qlog.semanticUnitList.add(curSU);
						if(!qlog.semanticUnitList.contains(expandSU))
							qlog.semanticUnitList.add(expandSU);
						
						curSU.RelationList.put(expandSU.centerWord, sr);
						expandSU.RelationList.put(curSU.centerWord, sr);
					}
				}
			}
			if(!matched)
			{
				qlog.SQGlog += "sr not found: "+sr+"\n";
			}
		}
		if(qlog.semanticUnitList.size() == 0)
			qlog.semanticUnitList = semanticUnitList;
		
		// Now we regard that ONLY modified word can have implicit relations, they will be supplemented later.
		// TODO: Maybe some other reasons lead to relation extraction FAILED between two nodes. (eg, .. and .. | .. in ..) 
	}

	// Print original structure of query graph. Notice, the relations have not been decided.
	public void recordOriginalTriples(ArrayList<SemanticUnit> SUList, QueryLogger qlog)
	{
		SemanticUnit curSU = null;
		SemanticUnit neighborSU = null;
		SemanticRelation sr = null;
		String subj = null;
		String obj = null;
		int rel = 0;
	
		for(int i=0;i<SUList.size();i++)
		{
			curSU = SUList.get(i);
			subj = curSU.centerWord.getFullEntityName();
			
			for(int j=0;j<curSU.neighborUnitList.size();j++)
			{
				neighborSU = curSU.neighborUnitList.get(j);
				
				// Deduplicate
				if(Globals.evaluationMethod > 1 && curSU.centerWord.position > neighborSU.centerWord.position)
					continue;

				obj = neighborSU.centerWord.getFullEntityName();
				sr = curSU.RelationList.get(neighborSU.centerWord);
				rel = 0;
				if(sr != null && sr.predicateMappings.size()>0)
				{
					PredicateMapping pm = sr.predicateMappings.get(0);
					rel = pm.pid;
					if(sr.preferredSubj != null)
					{
						if(sr.arg1Word == sr.preferredSubj)
						{
							subj = sr.arg1Word.getFullEntityName();
							obj = sr.arg2Word.getFullEntityName();						
							if(sr.isArg1Constant == false)
								subj = "?"+subj;
							if(sr.isArg2Constant == false)
								obj = "?"+obj;
						}
						else
						{
							subj = sr.arg2Word.getFullEntityName();
							obj = sr.arg1Word.getFullEntityName();
							if(sr.isArg2Constant == false)
								subj = "?"+subj;
							if(sr.isArg1Constant == false)
								obj = "?"+obj;
						}
					}
				}
					
				Triple next = new Triple(-1, subj,rel, -1, obj,null,0);
				qlog.SQGlog += "++++ Triple detect: "+next+"\n";
			}
			// current unit's TYPE
			if(curSU.prefferdType != null)
			{
				String type = TypeFragment.typeId2ShortName.get(curSU.prefferdType);
				Triple next = new Triple(-1, curSU.centerWord.getFullEntityName(),Globals.pd.typePredicateID,Triple.TYPE_ROLE_ID, type,null,0);
				qlog.SQGlog += "++++ Triple detect: "+next+"\n";
			}
			// current unit's describe
			for(DependencyTreeNode describeNode: curSU.describeNodeList)
			{
				qlog.SQGlog += "++++ Describe detect: "+describeNode.dep_father2child+"\t"+describeNode.word+"\t"+curSU.centerWord+"\n";
			}
		}
	}
	
	public void dfs(DependencyTreeNode head, DependencyTreeNode cur, ArrayList<DependencyTreeNode> ret)
	{
		if(cur == null)
			return;
		visited.add(cur);
		
		if(isNode(cur) && head!=cur)
		{
			ret.add(cur);
			return;
		}
		
		if(cur.father!=null && !visited.contains(cur.father))
		{
			dfs(head,cur.father,ret);
		}
		for(DependencyTreeNode child: cur.childrenList)
		{
			if(!visited.contains(child))
				dfs(head,child,ret);
		}
		return;
	}
	
	/*
	 * Judge nodes strictly.
	 * */
	public boolean isNode(DependencyTreeNode cur)
	{
		if(stopNodeList.contains(cur.word.baseForm))
			return false;
		
		if(cur.word.omitNode || cur.word.represent!=null)
			return false;
		
		// Modifier can NOT be node (They may be added in query graph in the end) e.g., Queen Elizabeth II，Queen(modifier)
		if(modifierList.contains(cur.word))
			return false;
		
		// NOUN
		if(cur.word.posTag.startsWith("N"))
			return true;

		// Wh-word
		if(whList.contains(cur.word.baseForm))
			return true;
		
		if(cur.word.mayEnt || cur.word.mayType || cur.word.mayCategory)
			return true;
		return false;
	}
	
	public DependencyTreeNode detectTarget(DependencyTree ds, QueryLogger qlog)
	{
		visited.clear();
		DependencyTreeNode target = null;
		Word[] words = qlog.s.words;
		
		for(DependencyTreeNode cur : ds.nodesList)
		{
			if(isWh(cur.word)) 
			{
				target = cur;
				break;
			}
		}
		// No Wh-Word: use the first node; NOTICE: consider MODIFIER rules. E.g, was us president Obama ..., target=obama (rather us)
		if(target == null)
		{
			for(Word word: words)
			{
				Word modifiedWord = word.modifiedWord;
				if(modifiedWord != null && isNodeCandidate(modifiedWord))
				{
					target = ds.getNodeByIndex(modifiedWord.position);
					break;
				}
			}
			
			if(target == null)
				target = ds.nodesList.get(0);
			
			/* Are [E|tree_frogs] a type of [E|amphibian] , type
			*/
			for(DependencyTreeNode dtn: target.childrenList)
			{
				if(dtn.word.baseForm.equals("type"))
				{
					dtn.word.represent = target.word;
				}
			}
			
			
		}
		//where, NOTICE: wh target from NN may not pass the function isNode()
		if(target.word.baseForm.equals("where"))
		{
			int curPos = target.word.position - 1;
			
			//!Where is the residence of
			if(words[curPos+1].baseForm.equals("be") && words[curPos+2].posTag.equals("DT"))
			{
				for(int i=curPos+4;i<words.length;i++)
					if(words[i-1].posTag.startsWith("N") && words[i].posTag.equals("IN"))
					{
						target.word.represent = words[i-1];
						target = ds.getNodeByIndex(i);
						break;
					}
				
			}
		}
		//which
		if(target.word.baseForm.equals("which"))
		{
			// test case: In which US state is Mount_McKinley located
			int curPos = target.word.position-1;
			if(curPos+1 < words.length)
			{
				Word word1 = words[curPos+1].modifiedWord;
				if(isNodeCandidate(word1))
				{
					// which city ... target = city
					target.word.represent = word1;
					target = ds.getNodeByIndex(word1.position);
					int word1Pos = word1.position - 1;
					// word1 + be + (the) + word2, and be is root: word1 & word2 may coreference
					if(ds.root.word.baseForm.equals("be") && word1Pos+3 < words.length && words[word1Pos+1].baseForm.equals("be"))
					{
						// which city is [the] headquarters ...
						Word word2 = words[word1Pos+2].modifiedWord;
						if(words[word1Pos+2].posTag.equals("DT"))
							word2 = words[word1Pos+3].modifiedWord;
						int word2Pos = word2.position - 1;
						if(word2Pos+1 < words.length && isNodeCandidate(word2) && words[word2Pos+1].posTag.startsWith("IN"))
						{
							//In which city is [the] headquarters of ... | target = headquarters, city & headquarters: coreference
							//In which city was the president of Montenegro born? | COUNTER example, city & president: independent
							target.word.represent = word2;
							target = ds.getNodeByIndex(word2.position);
						}
					}
				}
			}
			// by dependency tree
			if(target.word.baseForm.equals("which"))
			{
				//Which of <films> had the highest budget
				boolean ok = false;
				for(DependencyTreeNode dtn: target.childrenList)
				{
					if(dtn.word.posTag.startsWith("IN"))
					{
						for(DependencyTreeNode chld: dtn.childrenList)
							if(isNode(chld))
							{
								target.word.represent = chld.word;
								target = chld;
								ok = true;
								break;
							}
					}
					if(ok)
						break;
				}
			}
			
		}
		//what
		else if(target.word.baseForm.equals("what"))
		{
			//Detect：what is [the] sth1 prep. sth2?
			//Omit: what is sth? 
			if(target.father != null && ds.nodesList.size()>=5)
			{
				DependencyTreeNode tmp1 = target.father;
				if(tmp1.word.baseForm.equals("be"))
				{
					for(DependencyTreeNode child: tmp1.childrenList)
					{
						if(child == target)
							continue;
						if(isNode(child))
						{
							target.word.represent = child.word;
							target = child;
							break;
						}
					}
				}
				//what sth || What airlines are (part) of the SkyTeam alliance?
				else if(isNode(tmp1))
				{
					target.word.represent = tmp1.word;
					target = tmp1;
					// Coreference resolution
					int curPos = target.word.position - 1;
					if(curPos+3<words.length && words[curPos+1].baseForm.equals("be")&&words[curPos+3].posTag.startsWith("IN") && words.length > 6)
					{
						words[curPos+2].represent = target.word;
					}
					
				}
			}
			// by sentence
			if(target.word.baseForm.equals("what"))
			{
				int curPos = target.word.position - 1;
				// what be the [node] ... ? (Notice: words.length CONTAINS symbol(?)，different from nodeList)
				if(words.length > 5 && words[curPos+1].baseForm.equals("be") && words[curPos+2].baseForm.equals("the") && isNodeCandidate(words[curPos+3]))
				{
					target.word.represent = words[curPos+3];
					target = ds.getNodeByIndex(words[curPos+3].position);
				}
			}
			
		}
		//who
		else if(target.word.baseForm.equals("who"))
		{
			//Detect：who is/does [the] sth1 prep. sth2?  || Who was the pope that founded the Vatican_Television ? | Who does the voice of Bart Simpson?
			//Others: who is sth? who do sth?  | target = who
			//test case: Who is the daughter of Robert_Kennedy married to?
			if(target.father != null && ds.nodesList.size()>=5)
			{	//who
				DependencyTreeNode tmp1 = target.father;
				if(tmp1.word.baseForm.equals("be") || tmp1.word.baseForm.equals("do"))
				{	//is
					for(DependencyTreeNode child: tmp1.childrenList)
					{
						if(child == target)
							continue;
						if(isNode(child))
						{	//sth1
							boolean hasPrep = false;
							for(DependencyTreeNode grandson: child.childrenList)
							{	//prep
								if(grandson.dep_father2child.equals("prep"))
									hasPrep = true;
							}
							//Detect: who is the sht1's sth2?
							if(hasPrep || qlog.s.plainText.contains(child.word.originalForm + " 's"))
							{
								target.word.represent = child.word;
								target = child;
								break;
							}
						}
					}
				}
			}
			// by sentence
			if(target.word.baseForm.equals("who"))
			{
				int curPos = target.word.position - 1;
				// who is usually coreference when it not the first word. 
				if(curPos - 1 >= 0 && isNodeCandidate(words[curPos-1]))
				{
					target.word.represent = words[curPos-1];
					target = ds.getNodeByIndex(words[curPos-1].position);
				}
			}
		}
		//how
		else if(target.word.baseForm.equals("how"))
		{	
			//Detect：how many sth ...  |eg: how many popular Chinese director are there
			int curPos = target.word.position-1;
			if(curPos+2 < words.length && words[curPos+1].baseForm.equals("many"))
			{
				Word modifiedWord = words[curPos+2].modifiedWord;
				if(isNodeCandidate(modifiedWord))
				{
					target.word.represent = modifiedWord;
					target = ds.getNodeByIndex(modifiedWord.position);
				}
			}
			//Detect: how big is [det] (ent)'s (var), how = var
			else if(curPos+6 < words.length && words[curPos+1].baseForm.equals("big"))
			{
				if(words[curPos+2].baseForm.equals("be") && words[curPos+3].baseForm.equals("the") && words[curPos+4].mayEnt && words[curPos+5].baseForm.equals("'s"))
				{
					Word modifiedWord = words[curPos+6].modifiedWord;
					if(isNodeCandidate(modifiedWord))
					{
						target.word.represent = modifiedWord;
						target = ds.getNodeByIndex(modifiedWord.position);
					}
				}
			}
			//Detect：how much ... 
			else if(curPos+2 < words.length && words[curPos+1].baseForm.equals("much"))
			{
				Word modifiedWord = words[curPos+2].modifiedWord;
				// How much carbs does peanut_butter have 
				if(isNodeCandidate(modifiedWord))
				{
					target.word.represent = modifiedWord;
					target = ds.getNodeByIndex(modifiedWord.position);
				}
				// How much did Pulp_Fiction cost | dependency tree
				else
				{
					if(target.father!=null && isNodeCandidate(target.father.word))
					{
						target.word.represent = target.father.word;
						target = target.father;
					}
				}
			}
		}
		return target;
	}
	
	/*
	 * There are two cases of [ent]+[type]：1、Chinese company 2、De_Beer company; 
	 * For 1, chinese -> company，for 2, De_Beer <- company
	 * Return: True : ent -> type | False ： type <- ent
	 * */
	public boolean checkModifyBetweenEntType(Word entWord, Word typeWord)
	{
		int eId = entWord.emList.get(0).entityID;
		int tId = typeWord.tmList.get(0).typeID;
		EntityFragment ef = EntityFragment.getEntityFragmentByEntityId(eId);
		
		if(ef == null || !ef.types.contains(tId))
			return true;
		
		return false;
	}
		
	/*
	 * 修饰的概念：在正确的dependency tree中，一个word(ent/type)指向另一个word，边通常为mod系列，它们之间没有其他点，并且是独立的两个object
	 * 例如：Chinese teacher --> Chinese修饰teacher；the Chinese teacher Wang Wei --> Chinese和teacher都修饰Wang Wei；
	 * 注意：the Television Show Charmed，因为属于一个object的word sequence会被提前识别出来并用下划线连接为一个word，所以Television_Show修饰Charmed
	 * 找到当前word所修饰的那个word（如果它不修饰别人，返回它自己）
	 * 通过sentence而不是dependency tree (因为后者常生成错误)
	 * 通常连续出现的node都是修饰最后一个node的；例外情况如test case 3；采用递归的方式
	 * test case:
	 * 1) the highest Chinese mountain
	 * 2) the Chinese popular director
	 * 3) the De_Beers company  (注意这里 company[type]修饰 De_Beers[ent])
	 * */
	public Word getTheModifiedWordBySentence(Sentence s, Word curWord)
	{
		if(curWord == null) 
			return null;
		if(curWord.modifiedWord != null)
			return curWord.modifiedWord;
		//既不是形容词也不是node，与修饰关系无关，设为null
		if(!isNodeCandidate(curWord) && !curWord.posTag.startsWith("JJ") && !curWord.posTag.startsWith("R"))
			return curWord.modifiedWord = null;
		
		curWord.modifiedWord = curWord;	//默认修饰自己，修饰自己的word不算做modifier
		Word preWord = null, nextWord = null;
		int curPos = curWord.position - 1; //word的position从1开始，所以减一
		if(curPos-1 >= 0)	preWord = s.words[curPos-1];
		if(curPos+1 < s.words.length)	nextWord = s.words[curPos+1];
		Word nextModifiedWord = getTheModifiedWordBySentence(s, nextWord);
		
		//External rule: 认为 ent+noun(非type|ent)的形式，ent不是修饰词并且后面的noun不是node；
		//eg：Does the [Isar] [flow] into a lake? | Who was on the [Apollo 11] [mission] | When was the [De Beers] [company] founded
		if(curWord.mayEnt && nextWord != null && !nextWord.mayEnt && !nextWord.mayType && !nextWord.mayLiteral)
		{
			nextWord.omitNode = true;
			if(nextModifiedWord == nextWord)
				return curWord.modifiedWord = curWord;
		}
		
		//修饰左边: ent + type(cur) : De_Beer company
		if(preWord != null && curWord.mayType && preWord.mayEnt) //ent + type(cur)
		{
			if(!checkModifyBetweenEntType(preWord, curWord)) //De_Beer <- company, 注意此时即使type后面还连着node，也不理会了
				return curWord.modifiedWord = preWord;
		}
		
		//修饰自己: ent(cur) + type : De_Beer company
		if(nextModifiedWord != null && curWord.mayEnt && nextModifiedWord.mayType)
		{
			if(!checkModifyBetweenEntType(curWord, nextModifiedWord))
				return curWord.modifiedWord = curWord;
		}
		
		//排除特殊情况后，通常情况都是修饰右边
		if(nextModifiedWord != null)
			return curWord.modifiedWord = nextModifiedWord;
		
		//右边没有node了，只能修饰自己
		return curWord.modifiedWord;
	}
	
	/*
	 * 识别不连续词之间的modifier/modified关系，如：
	 * 1、[ent1] 's [ent2]
	 * 2、[ent1] by [ent2]
	 * 注意：需先运行getTheModifiedWordBySentence
	 * */
	public Word getDiscreteModifiedWordBySentence(Sentence s, Word curWord)
	{	
		int curPos = curWord.position - 1; //word的position从1开始，所以减一
		
		//[ent1](cur) 's [ent2], 则ent1是ent2的修饰词，且通常不需要出现在sparql中。| eg：Show me all books in Asimov 's Foundation_series
		if(curPos+2 < s.words.length && curWord.mayEnt && s.words[curPos+1].baseForm.equals("'s") && s.words[curPos+2].mayEnt)
			return curWord.modifiedWord = s.words[curPos+2];
		
		//[ent1] by [ent2](cur), 则ent2是ent1的修饰词，且通常不需要出现在sparql中。 | eg: Which museum exhibits The Scream by Munch?
		if(curPos-2 >=0 && curWord.mayEnt && s.words[curPos-1].baseForm.equals("by") && s.words[curPos-2].mayEnt)
			return curWord.modifiedWord = s.words[curPos-2];
		
		return curWord.modifiedWord;
	}
	
	/*
	 * Judge nodes unstrictly.
	 * */
	public boolean isNodeCandidate(Word word)
	{
		if(word == null || stopNodeList.contains(word.baseForm))
			return false;
		
		if(word.posTag.startsWith("N"))
			return true;
		if(word.mayEnt || word.mayType || word.mayLiteral || word.mayCategory)
			return true;
		
		return false;
	}
	
	public boolean isWh(Word w)
	{
		String tmp = w.baseForm;
		if(whList.contains(tmp))
			return true;
		return false;
	}
}
