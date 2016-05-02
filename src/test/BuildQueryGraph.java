package test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import fgmt.TypeFragment;
import log.QueryLogger;
import qa.Globals;
import qa.extract.CorefResolution;
import qa.extract.SimpleRelation;
import qa.extract.TypeRecognition;
import qa.mapping.SemanticItemMapping;
import rdf.PredicateMapping;
import rdf.Triple;
import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Sentence;
import nlp.ds.Sentence.SentenceType;
import nlp.ds.Word;
import rdf.SemanticRelation;
import paradict.ParaphraseDictionary;

/*
 * aggregation type:
 * 1: how many
 * 2: latest/first/...
 * */
public class BuildQueryGraph 
{
	public ArrayList<SemanticUnit> semanticUnitList = new ArrayList<SemanticUnit>();
	public ArrayList<String> whList = new ArrayList<String>();
	public ArrayList<String> stopNodeList = new ArrayList<String>();
	public ArrayList<Word> modifierList = new ArrayList<Word>();
	public HashSet<DependencyTreeNode> visited = new HashSet<DependencyTreeNode>();
	public HashMap<Integer, SemanticRelation> matchedSemanticRelations = new HashMap<Integer, SemanticRelation>();
	
	public int aggregationType = -1;
	
	public BuildQueryGraph()
	{
		whList.add("what");
		whList.add("which");
		whList.add("who");
		whList.add("whom");
		whList.add("when");
		whList.add("how");
		whList.add("where");
		
		stopNodeList.add("list");
		stopNodeList.add("give");
		stopNodeList.add("show");
		stopNodeList.add("star");
		stopNodeList.add("theme");
	}
	
	public void fixStopWord(QueryLogger qlog)
	{
		Sentence qSen = qlog.s;
		String qStr = qlog.s.plainText;
		
		//take [place]
		if(qStr.contains("take place") || qStr.contains("took place"))
			stopNodeList.add("place");
		
		//(When was Alberta admitted) as [province] 
		if(qStr.contains("as province"))
			stopNodeList.add("province");
	}

	public ArrayList<SemanticUnit> process(QueryLogger qlog)
	{
		try 
		{
			semanticUnitList = new ArrayList<SemanticUnit>();
			
			DependencyTree ds = qlog.s.dependencyTreeStanford;
			if(qlog.isMaltParserUsed)
				ds = qlog.s.dependencyTreeMalt;
		
/*在build query graph前的一些准备，包括： 
 * 0)根据词组特性选择加入一些可能的stop node
 * 1)确定从哪个点入手建图；（因为从不同的点开始会对图结构有影响）
 * 2)共指消解； 
 * 3)确定哪些词是中心词(semantic unit的核心，出现在基本的图框架中)，哪些词是修饰词（修饰中心词的ent/type/adj，不出现在基本图架构中，但可能会作为补充信息加入query;修饰词即使是ent，也只会出现在图的边缘）
 * */		
			fixStopWord(qlog);
			
			//step1:识别query target，部分共指消解 |  现在这个target只起bfs入口作用了，在生成sparql后会再确定一遍真正的target。   
			DependencyTreeNode target = detectTarget(ds,qlog);
			qlog.fw.write("++++ Target detect: "+target+"\n");
			if(target == null)
				return null;
			
			qlog.target = target.word;
			//认为target不能是ent，一般疑问句除外
			if(qlog.s.sentenceType != SentenceType.GeneralQuestion && target.word.emList!=null) 
			{
				target.word.mayEnt = false;
				target.word.emList.clear();
			}
			
			//共指消解，cr中有一系列规则；因为共指分为好几种情况需要不同的处理方式，不能简单的删掉其中一个，所以要确定图结构后再处理
			//ganswer以关系为核心，确定每一组”关系加两端变量“后做指代消解，用其中一个替换掉所有共指的变量就可以 
			CorefResolution cr = new CorefResolution();
			
			//这里随手加一个指代消解，之后应该在结构清晰的地方统一进行指代消解。
			//现在是“只在detect target”时进行了部分指代消解，下面这行是处理形如“Is Michelle Obama the wife of Barack Obama?”
			//为简便，直接将要消除的词加入stopNodeList。因为represent有时需要复制被指代词的信息，也可能影响结构，还没搞清楚
			if(qlog.s.words[0].baseForm.equals("be") && isNode(ds.getNodeByIndex(2)) && ds.getNodeByIndex(3).dep_father2child.equals("det") && isNode(ds.getNodeByIndex(4)))
				stopNodeList.add(ds.getNodeByIndex(4).word.baseForm);	
			
			//修饰词识别，依据sentence而不是dependency tree  
			for(Word word: qlog.s.words)
			{
				Word modifiedWord = getTheModifiedWordBySentence(qlog.s, word);
				if(modifiedWord != word)
				{
					modifierList.add(word);
					word.modifiedWord = modifiedWord;
					qlog.fw.write("++++ Modify detect: "+word+" --> "+modifiedWord+"\n");
				}
			}
		
/*准备完毕*/		
			
			DependencyTreeNode curCenterNode = target;
			ArrayList<DependencyTreeNode> expandNodeList;
			Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
			queue.add(target);
			visited.clear();
			
			//step2:核心，一步步扩展查询图  
			while((curCenterNode=queue.poll())!=null)
			{	
				if(curCenterNode.word.represent != null || cr.getRefWord(curCenterNode.word,ds,qlog) != null)
				{
					//被扩展SU被其他SU代表，则直接略过此次扩展; TODO: 共指消解应该在结构确定后做，直接抛弃可能会丢失部分边信息
					//[2015-12-13]这样相当于丢失了这个方向，沿该SU继续走本来能找到其他点，但直接continue就断绝了找到这些点的希望; 之所以一直没有发现问题是因为绝大多数情况被代表的SU都在query graph的边缘，即不会中断探索
					//[2015-12-13]干脆先剥夺他们在dfs中被探索到的权利，即在isNode中拒绝represent，注意先只针对 represent; 
					continue;
				}					
				
				SemanticUnit curSU = new SemanticUnit(curCenterNode.word,true);
				expandNodeList = new ArrayList<DependencyTreeNode>();
				dfs(curCenterNode, curCenterNode, expandNodeList);	
				queue.addAll(expandNodeList);
				
				semanticUnitList.add(curSU);
				for(DependencyTreeNode expandNode: expandNodeList)
				{
					String subj = curCenterNode.word.getBaseFormEntityName();
					String obj = expandNode.word.getBaseFormEntityName();
					
					//略去词组内部关系
					if(subj.equals(obj))
						continue;
					
					//TODO:略去共指关系，这里还再改
					if(expandNode.word.represent != null)
						continue;
					
					//expandNode作为一个新的SemanticUnit
					SemanticUnit expandSU = new SemanticUnit(expandNode.word,false);
					
					//作为邻居互相认识
					curSU.neighborUnitList.add(expandSU);
					expandSU.neighborUnitList.add(curSU);	//这个其实没有用，因为expandSU没有加入到semanticUnitList，所以现在这种写法两个UNIT之间只是单向边，方向就是探索扩展的方向
				}
			}
			
			//step3: 注意这时认为已经处理了 "指代消解"。确定各unit之间的relation, 这里认为只要两个unit不直接相连都可以通过ganswer的relation extract解决。 
			extractRelation(semanticUnitList, qlog);
			matchRelation(semanticUnitList, qlog);
			
//			//step4: [这步没有实际作用]找每个unit的描述词，处理describe，包括聚集函数、形容词，转化成semantic relation形式加入matchedSR进行item mapping.
//			findDescribe(semanticUnitList, qlog);
			
			//item mapping前的准备，即识别 “常量” 和 “变量”
			//TODO 这个函数要改进，将step0得到的信息考虑进来；常量、变量信息是否应该存储在WORD中而不是SR中？
			ExtractRelation er = new ExtractRelation();
			er.constantVariableRecognition(qlog.semanticRelations,qlog);
		
			//step5: item mappping & top-k join
			TypeRecognition tr = new TypeRecognition();	
			tr.recognize(qlog.semanticRelations);	//这一步可能是无用功
			
			SemanticItemMapping step5 = new SemanticItemMapping();
			step5.process(qlog, qlog.semanticRelations);
		
			
			// 输出查询图的结构，这是不进行fragment check的原始图，用于观察图结构
			printTriples_SUList(semanticUnitList, qlog);
			
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		qlog.semanticUnitList = semanticUnitList;
		return semanticUnitList;
	}
	
	private void findDescribe(ArrayList<SemanticUnit> semanticUnitList, QueryLogger qlog)
	{
		DependencyTree ds = qlog.s.dependencyTreeStanford;
		if(qlog.isMaltParserUsed)
			ds = qlog.s.dependencyTreeMalt;
		
		for(SemanticUnit curSU: semanticUnitList)
		{
			DependencyTreeNode curNode = ds.getNodeByIndex(curSU.centerWord.position);
			for(DependencyTreeNode child: curNode.childrenList)
			{
				if(child.dep_father2child.contains("mod"))
				{
					curSU.describeNodeList.add(child);
				}
			}
		}
		
		//TO DO
	}

	public void extractRelation(ArrayList<SemanticUnit> semanticUnitList, QueryLogger qlog)
	{
		ExtractRelation er = new ExtractRelation();
		ArrayList<SimpleRelation> simpleRelations = new ArrayList<SimpleRelation>();
		for(SemanticUnit curSU: semanticUnitList)
		{
			for(SemanticUnit expandSU: curSU.neighborUnitList)
			{
//				//去重 | 现在是单向边不需要去重了  
//				if(curSU.centerWord.position > expandSU.centerWord.position)
//					continue;
				
				ArrayList<SimpleRelation> tmpRelations = null;
				//get simple relations
				//先用ganswer方法求
				tmpRelations = er.findRelationsBetweenTwoUnit(curSU, expandSU, qlog);
				if(tmpRelations!=null && tmpRelations.size()>0)
					simpleRelations.addAll(tmpRelations);
				//没找到解
				else
				{
					tmpRelations = new ArrayList<SimpleRelation>();
					//处理“and”，例如“In which films did Julia_Roberts and Richard_Gere play?”，复制一份sr
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
		qlog.semanticRelations = semanticRelations;
	}
	
	public void matchRelation(ArrayList<SemanticUnit> semanticUnitList, QueryLogger qlog) 
	{
		//step1: 把用gAnswer的relation extraction方法找到的relations及对应predicates填入
		//这里使用未经de-overlap的原始semantic relations
		//并且为 semantic unit 填入 prefer type 信息
		for(int relKey: qlog.semanticRelations.keySet())
		{
			boolean matched = false;
			SemanticRelation sr = qlog.semanticRelations.get(relKey);
			for(SemanticUnit curSU: semanticUnitList)
			{
				for(SemanticUnit expandSU: curSU.neighborUnitList)
				{
					int key = curSU.centerWord.getNnHead().hashCode() ^ expandSU.centerWord.getNnHead().hashCode();
					if(relKey == key)
					{
						matched = true;
						matchedSemanticRelations.put(relKey, sr);
						
						curSU.RelationList.put(expandSU.centerWord, sr);
						expandSU.RelationList.put(curSU.centerWord, sr);
					
						if(sr.arg1Word.getBaseFormEntityName().equals(curSU.centerWord.getBaseFormEntityName()))
						{
							if(sr.arg1Word.tmList!=null && sr.arg1Word.tmList.size()>0)
								curSU.prefferdType = sr.arg1Word.tmList.get(0).typeID;
						}
						if(sr.arg2Word.getBaseFormEntityName().equals(curSU.centerWord.getBaseFormEntityName()))
						{
							if(sr.arg2Word.tmList!=null && sr.arg2Word.tmList.size()>0)
								curSU.prefferdType = sr.arg2Word.tmList.get(0).typeID;
						}
					}
				}
			}
			if(!matched)
			{
				try {
					qlog.fw.write("sr not found: "+sr+"\n");
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		//step2: 将没有确定relation的边按照ganswer relation extract的方式抽一遍信息，如果没抽到，则再根据规则尝试一下。之后转化成 semantic relation形式，方便后面进行item mapping。
		//to do
	}

	public void printTriples_SUList(ArrayList<SemanticUnit> SUList, QueryLogger qlog)
	{
		SemanticUnit curSU = null;
		SemanticUnit neighborSU = null;
		SemanticRelation sr = null;
		String subj = null;
		String obj = null;
		int rel = 0;
	
		try 
		{
			for(int i=0;i<SUList.size();i++)
			{
				curSU = SUList.get(i);
				subj = curSU.centerWord.getFullEntityName();
				
				for(int j=0;j<curSU.neighborUnitList.size();j++)
				{
					neighborSU = curSU.neighborUnitList.get(j);
// 现在是单向边，不需要去重
//					if(curSU.centerWord.position > neighborSU.centerWord.position)
//						continue;
	
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
						
					Triple next = new Triple(subj,rel,obj,null,0);
					qlog.fw.write("++++ Triple detect: "+next+"\n");
				}
				// 当前unit是否拥有type
				if(curSU.prefferdType != null)
				{
//					StringBuilder type = new StringBuilder("");				
//					for (Integer tt : sr.arg2Types) {
//						type.append(TypeFragment.typeId2ShortName.get(tt));
//						type.append('|');
//					}
					String type = TypeFragment.typeId2ShortName.get(curSU.prefferdType);
					Triple next = new Triple(curSU.centerWord.getFullEntityName(),Globals.pd.typePredicateID,type,null,0);
					qlog.fw.write("++++ Triple detect: "+next+"\n");
				}
				// 当前unit是否拥有describe
				for(DependencyTreeNode describeNode: curSU.describeNodeList)
				{
					qlog.fw.write("++++ Describe detect: "+describeNode.dep_father2child+"\t"+describeNode.word+"\t"+curSU.centerWord+"\n");
				}
			}
			//qlog.fw.write("\n");
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
	
/*	public SemanticUnit getSUbyCenterWord(ArrayList<SemanticUnit> SUList, Word centerWord) 
	{
		for(int i=0;i<SUList.size();i++)
		{
			if(SUList.get(i).centerWord == centerWord)
				return SUList.get(i);
		}
		SemanticUnit newSU = new SemanticUnit(centerWord);
		return newSU;
	}*/
	
	//带环会不会死循环？
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
	
	public boolean isNode(DependencyTreeNode cur)
	{
		if(stopNodeList.contains(cur.word.baseForm))
			return false;
		
		if(cur.word.omitNode || cur.word.represent!=null)
			return false;
		
		//修饰词不作为node；例如 Queen Elizabeth II中，queen为修饰词
		if(modifierList.contains(cur.word))
			return false;
		
//		//parser认为这个词和它指向的词共同组成一个整体词，但它指向的词已被识别为一个ent，那么这个word可能只是修饰作用，并不真的和它指向的词是一个整体词
//		//这里没有加入判断边是否为nn，是否需要加入？ | 这个条件相当于不允许两个node相连，之后可能需要修改
//		if(!cur.word.mayEnt && cur.father!=null && !cur.dep_father2child.startsWith("poss") && (cur.father.word.mayEnt||cur.father.word.mayType))
//			return false;

//以下两类可以用 “修饰词规则” 解决
//		//这是一个extendType，且无需加入triple | ”Queen Elizabeth II“ 中的 queen，并没有queen这个type，但是他的含义和type相同
//		if(cur.word.tmList!=null && cur.word.tmList.size()>0 && cur.word.tmList.get(0).prefferdRelation == -1)
//			return false;
//		//这是一个type，且后面接一个ent，例如“television show Charmed”，这时charmed是真正的node，”television show“是对他的修饰，忽略
//		//实际上 type+ent 这样的形式，type可以起到消除歧义作用，这里为简便直接不把type看做节点，当结构调整策略清晰后可以再做更好的处理
//		if(cur.word.mayType && cur.father!=null && cur.father.word.mayEnt)
//			return false;
		
		if(cur.word.posTag.startsWith("N"))
			return true;

//疑问代词认可为NODE会在dfs时遇到一些问题，先测试一下
		if(whList.contains(cur.word.baseForm))
			return true;
		
		if(cur.word.mayEnt || cur.word.mayType)
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
		//若没有找到疑问词，则找在原句中最先出现的node; 注意这里引入了”修饰词规则“，例如对 was us president obama ..., target=obama而不是us
		if(target == null)
		{
			for(Word word: words)
			{
				Word modifiedWord = getTheModifiedWordBySentence(qlog.s, word);
				if(isNodeCandidate(modifiedWord))
				{
					target = ds.getNodeByIndex(modifiedWord.position);
					break;
				}
			}
			
			/* Are [E|tree_frogs] a type of [E|amphibian] , 与type共指
			*/
			for(DependencyTreeNode dtn: target.childrenList)
			{
				if(dtn.word.baseForm.equals("type"))
				{
					dtn.word.represent = target.word;
				}
			}
			
			
		}
		//where，注意通过 wh 到 NN 变换得来的 target，没有判断 NN 能否通过 isNode
		if(target.word.baseForm.equals("where"))
		{
			int curPos = target.word.position - 1;
			
			//rule:Where is the residence of
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
				Word word1 = getTheModifiedWordBySentence(qlog.s, words[curPos+1]);
				if(isNodeCandidate(word1))
				{
					// which city ... 先将target设为city
					target.word.represent = word1;
					target = ds.getNodeByIndex(word1.position);
					int word1Pos = word1.position - 1;
					// word1 + be + (the) + word2, 且be为root，则word1和word2可能共指
					if(ds.root.word.baseForm.equals("be") && word1Pos+3 < words.length && words[word1Pos+1].baseForm.equals("be"))
					{
						// which city is [the] headquarters ...
						Word word2 = getTheModifiedWordBySentence(qlog.s, words[word1Pos+2]);
						if(words[word1Pos+2].posTag.equals("DT"))
							word2 = getTheModifiedWordBySentence(qlog.s, words[word1Pos+3]);
						int word2Pos = word2.position - 1;
						if(word2Pos+1 < words.length && isNodeCandidate(word2) && words[word2Pos+1].posTag.startsWith("IN"))
						{
							//In which city is [the] headquarters of ... 修正target为headquarters |实际上city和headquarters共指，但如果city为target，结构会错误 |注意这里如果把of替换为一个动词，则target应该为city
							//In which city was the president of Montenegro born? 作为上行的反例，即city和president为两个node
							target.word.represent = word2;
							target = ds.getNodeByIndex(word2.position);
						}
					}
				}
			}
			// target还是 which，再用dependency tree检测一下
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
			//检测：what is [the] sth1 prep. sth2?
			//what is sth? 这种句式很少出现，即使出现，一般不会大于5个词。
			if(target.father != null && ds.nodesList.size()>=4)
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
					//顺便消除共指
					int curPos = target.word.position - 1;
					if(curPos+3<words.length && words[curPos+1].baseForm.equals("be")&&words[curPos+3].posTag.startsWith("IN") && words.length > 6)
					{
						words[curPos+2].represent = target.word;
					}
					
				}
			}
			
			
		}
		//who
		else if(target.word.baseForm.equals("who"))
		{
			//检测：who is [the] sth1 prep. sth2?  || Who was the pope that founded the Vatican_Television ? 
			//其他诸如 who is sth? who do sth? 的target都为who
			//形如 Who is the daughter of Robert_Kennedy married to的query在stanford tree中，who和is不是父子关系而是并列，所以who还是target
			if(target.father != null && ds.nodesList.size()>=5)
			{	//who
				DependencyTreeNode tmp1 = target.father;
				if(tmp1.word.baseForm.equals("be"))
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
							//检测 who is the sht1's sth2? 这种情况在parser中，who和sth2都指向be
							//简单的认为 ”who和sth2都指向be“ 就行，所以到这里无论有没有prep都认为共指
//							if(hasPrep)
//							{
								target.word.represent = child.word;
								target = child;
								break;
//							}
						}
					}
				}
			}
			//再用sentence检测一下
			if(target.word.baseForm.equals("who"))
			{
				int curPos = target.word.position - 1;
				// who在句中出现一般为共指
				if(curPos - 1 >= 0 && isNodeCandidate(words[curPos-1]))
				{
					target.word.represent = words[curPos-1];
					target = ds.getNodeByIndex(words[curPos-1].position);
				}
				else
				{
					//Who produced films starring Natalie_Portman，target设为film图才正确，否则就是who和Natalie相连
					//注意目前 Who produced Goofy? 这种，target还是设为 who，因为 “规则：特殊疑问句的target不为ent”，所以target还是用来代表最终要问的那个东西
					//TODO:是否另起一个标记“start”，之后在考虑，现在用长度判断来区分开以上两例情况
					if(curPos+2<words.length && words[curPos+1].posTag.startsWith("V"))
					{
						Word modifiedWord = getTheModifiedWordBySentence(qlog.s, words[curPos+2]);
						if(isNodeCandidate(modifiedWord) && words.length>=5)
						{
							target = ds.getNodeByIndex(modifiedWord.position);
						}
					}
				}
			}
		}
		//how
		else if(target.word.baseForm.equals("how"))
		{	
			//检测：how many sth ...  |eg: how many popular Chinese director are there
			int curPos = target.word.position-1;
			if(curPos+2 < words.length && words[curPos+1].baseForm.equals("many"))
			{
				Word modifiedWord = getTheModifiedWordBySentence(qlog.s, words[curPos+2]);
				if(isNodeCandidate(modifiedWord))
				{
					target.word.represent = modifiedWord;
					target = ds.getNodeByIndex(modifiedWord.position);
				}
			}
			//检测：how much ... 
			else if(curPos+2 < words.length && words[curPos+1].baseForm.equals("much"))
			{
				Word modifiedWord = getTheModifiedWordBySentence(qlog.s, words[curPos+2]);
				// How much carbs does peanut_butter have 
				if(isNodeCandidate(modifiedWord))
				{
					target.word.represent = modifiedWord;
					target = ds.getNodeByIndex(modifiedWord.position);
				}
				// How much did Pulp_Fiction cost | 用dependency tree
				else
				{
					if(target.father!=null && isNodeCandidate(target.father.word))
					{
						target.word.represent = target.father.word;
						target = target.father;
					}
				}
			}
			
//dependncy tree有时错误，所以直接在sentence中检测
//			if(target.father != null)
//			{
//				DependencyTreeNode fa1 = target.father;
//				if(fa1.word.baseForm.equals("many") && fa1.father!=null)
//				{
//					DependencyTreeNode fa2 = fa1.father;
//					if(fa1.dep_father2child.contains("mod"))
//					{
//						target.word.represent = fa2.word;
//						target = fa2;
//						aggregationType = 1; // "how many"
//					}
//				}
//			}
		}
		return target;
	}
	
	/*
	 * 修饰的概念：在正确的dependency tree中，一个word(ent/type)指向另一个word，边通常为mod系列，它们之间没有其他点，并且是独立的两个object
	 * 例如：Chinese teacher --> Chinese修饰teacher；the Chinese teacher Wang Wei --> Chinese和teacher都修饰Wang Wei；
	 * 注意：the Television Show Charmed，因为属于一个object的word sequence会被提前识别出来并用下划线连接为一个word，所以Television_Show修饰Charmed
	 * 找到当前word所修饰的那个word（如果它不修饰别人，返回它自己）
	 * 通过sentence而不是dependency tree (因为后者常生成错误)
	 * test case:
	 * 1) the highest Chinese mountain
	 * 2) the Chinese popular director
	 * */
	public Word getTheModifiedWordBySentence(Sentence s, Word modifier)
	{
		//简单的认为：连续出现的node都是修饰最后一个node的
		Word modifiedWord = modifier;
		
		//既不是形容词也不是node，就直接返回 
		if(!isNodeCandidate(modifier) && !modifier.posTag.startsWith("JJ") && !modifier.posTag.startsWith("R"))
			return modifier;
		
//		//判断：... dose the Yenisei_river flow? || did Pulp_Fiction cost? 的形式，因为这样形式最后的verb常被错认为是名词变量
//		int rightNodeCnt = 0;
//		for(int i=modifier.position;i<s.words.length;i++)
//			if(isNodeCandidate(s.words[i]))
//				rightNodeCnt++;
//		if(rightNodeCnt == 1)
//		{
//			//Does ... 一般疑问句不考虑
//			for(int i=modifier.position-2;i>0;i--)
//				if(s.words[i].baseForm.equals("do"))
//					return modifiedWord;
//		}
		
		//[ent1] 's [ent2], 则ent1是ent2的修饰词，且通常不需要出现在sparql中。| eg：Show me all books in Asimov 's Foundation_series
		if(modifier.position+1 < s.words.length && modifier.mayEnt && s.words[modifier.position].baseForm.equals("'s") && s.words[modifier.position+1].mayEnt)
		{
			modifiedWord = s.words[modifier.position+1];
			return modifiedWord;
		}

		//干脆认为 ent+noun 的形式，ent不是修饰词并且后面的noun不是node；eg：Does the [Isar] [flow] into a lake?
		if(modifier.position<s.words.length && modifier.mayEnt && !s.words[modifier.position].mayEnt && !s.words[modifier.position].mayType && !s.words[modifier.position].mayLiteral)
		{
			s.words[modifier.position].omitNode = true;
			return modifier;
		}
		
		//注意这里position是下标从1开始，所以从modifier的后一个词开始扫描不用加1
		for(int i=modifier.position;i<s.words.length;i++)
		{
			Word word = s.words[i];
			if(isNodeCandidate(word))
			{
				modifiedWord = word;
			}
			//像 "popular","largest"等修饰词，不会成为被修饰词；若出现既不是node又不是形容词，则停止
			else if(!word.posTag.startsWith("JJ"))
			{
				break;
			}
		}
		return modifiedWord;
	}
	
	/*
	 * NodeCandidate：有资格成为Node，但不一定要放入query graph中
	 * */
	public boolean isNodeCandidate(Word word)
	{
		if(stopNodeList.contains(word.baseForm))
			return false;
		
		if(word.posTag.startsWith("N"))
			return true;
		if(word.mayEnt || word.mayType || word.mayLiteral)
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
