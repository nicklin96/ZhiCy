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
		
/*��build query graphǰ��һЩ׼���������� 
 * 0)���ݴ�������ѡ�����һЩ���ܵ�stop node
 * 1)ȷ�����ĸ������ֽ�ͼ������Ϊ�Ӳ�ͬ�ĵ㿪ʼ���ͼ�ṹ��Ӱ�죩
 * 2)��ָ���⣻ 
 * 3)ȷ����Щ�������Ĵ�(semantic unit�ĺ��ģ������ڻ�����ͼ�����)����Щ�������δʣ��������Ĵʵ�ent/type/adj���������ڻ���ͼ�ܹ��У������ܻ���Ϊ������Ϣ����query;���δʼ�ʹ��ent��Ҳֻ�������ͼ�ı�Ե��
 * */		
			fixStopWord(qlog);
			
			//step1:ʶ��query target�����ֹ�ָ���� |  �������targetֻ��bfs��������ˣ�������sparql�����ȷ��һ��������target��   
			DependencyTreeNode target = detectTarget(ds,qlog);
			qlog.fw.write("++++ Target detect: "+target+"\n");
			if(target == null)
				return null;
			
			qlog.target = target.word;
			//��Ϊtarget������ent��һ�����ʾ����
			if(qlog.s.sentenceType != SentenceType.GeneralQuestion && target.word.emList!=null) 
			{
				target.word.mayEnt = false;
				target.word.emList.clear();
			}
			
			//��ָ���⣬cr����һϵ�й�����Ϊ��ָ��Ϊ�ü��������Ҫ��ͬ�Ĵ�����ʽ�����ܼ򵥵�ɾ������һ��������Ҫȷ��ͼ�ṹ���ٴ���
			//ganswer�Թ�ϵΪ���ģ�ȷ��ÿһ�顱��ϵ�����˱���������ָ�����⣬������һ���滻�����й�ָ�ı����Ϳ��� 
			CorefResolution cr = new CorefResolution();
			
			//�������ּ�һ��ָ�����⣬֮��Ӧ���ڽṹ�����ĵط�ͳһ����ָ�����⡣
			//�����ǡ�ֻ��detect target��ʱ�����˲���ָ�����⣬���������Ǵ������硰Is Michelle Obama the wife of Barack Obama?��
			//Ϊ��㣬ֱ�ӽ�Ҫ�����Ĵʼ���stopNodeList����Ϊrepresent��ʱ��Ҫ���Ʊ�ָ���ʵ���Ϣ��Ҳ����Ӱ��ṹ����û�����
			if(qlog.s.words[0].baseForm.equals("be") && isNode(ds.getNodeByIndex(2)) && ds.getNodeByIndex(3).dep_father2child.equals("det") && isNode(ds.getNodeByIndex(4)))
				stopNodeList.add(ds.getNodeByIndex(4).word.baseForm);	
			
			//���δ�ʶ������sentence������dependency tree  
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
		
/*׼�����*/		
			
			DependencyTreeNode curCenterNode = target;
			ArrayList<DependencyTreeNode> expandNodeList;
			Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
			queue.add(target);
			visited.clear();
			
			//step2:���ģ�һ������չ��ѯͼ  
			while((curCenterNode=queue.poll())!=null)
			{	
				if(curCenterNode.word.represent != null || cr.getRefWord(curCenterNode.word,ds,qlog) != null)
				{
					//����չSU������SU��������ֱ���Թ��˴���չ; TODO: ��ָ����Ӧ���ڽṹȷ��������ֱ���������ܻᶪʧ���ֱ���Ϣ
					//[2015-12-13]�����൱�ڶ�ʧ����������ظ�SU�����߱������ҵ������㣬��ֱ��continue�ͶϾ����ҵ���Щ���ϣ��; ֮����һֱû�з�����������Ϊ������������������SU����query graph�ı�Ե���������ж�̽��
					//[2015-12-13]�ɴ��Ȱ���������dfs�б�̽������Ȩ��������isNode�оܾ�represent��ע����ֻ��� represent; 
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
					
					//��ȥ�����ڲ���ϵ
					if(subj.equals(obj))
						continue;
					
					//TODO:��ȥ��ָ��ϵ�����ﻹ�ٸ�
					if(expandNode.word.represent != null)
						continue;
					
					//expandNode��Ϊһ���µ�SemanticUnit
					SemanticUnit expandSU = new SemanticUnit(expandNode.word,false);
					
					//��Ϊ�ھӻ�����ʶ
					curSU.neighborUnitList.add(expandSU);
					expandSU.neighborUnitList.add(curSU);	//�����ʵû���ã���ΪexpandSUû�м��뵽semanticUnitList��������������д������UNIT֮��ֻ�ǵ���ߣ��������̽����չ�ķ���
				}
			}
			
			//step3: ע����ʱ��Ϊ�Ѿ������� "ָ������"��ȷ����unit֮���relation, ������ΪֻҪ����unit��ֱ������������ͨ��ganswer��relation extract����� 
			extractRelation(semanticUnitList, qlog);
			matchRelation(semanticUnitList, qlog);
			
//			//step4: [�ⲽû��ʵ������]��ÿ��unit�������ʣ�����describe�������ۼ����������ݴʣ�ת����semantic relation��ʽ����matchedSR����item mapping.
//			findDescribe(semanticUnitList, qlog);
			
			//item mappingǰ��׼������ʶ�� �������� �� ��������
			//TODO �������Ҫ�Ľ�����step0�õ�����Ϣ���ǽ�����������������Ϣ�Ƿ�Ӧ�ô洢��WORD�ж�����SR�У�
			ExtractRelation er = new ExtractRelation();
			er.constantVariableRecognition(qlog.semanticRelations,qlog);
		
			//step5: item mappping & top-k join
			TypeRecognition tr = new TypeRecognition();	
			tr.recognize(qlog.semanticRelations);	//��һ�����������ù�
			
			SemanticItemMapping step5 = new SemanticItemMapping();
			step5.process(qlog, qlog.semanticRelations);
		
			
			// �����ѯͼ�Ľṹ�����ǲ�����fragment check��ԭʼͼ�����ڹ۲�ͼ�ṹ
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
//				//ȥ�� | �����ǵ���߲���Ҫȥ����  
//				if(curSU.centerWord.position > expandSU.centerWord.position)
//					continue;
				
				ArrayList<SimpleRelation> tmpRelations = null;
				//get simple relations
				//����ganswer������
				tmpRelations = er.findRelationsBetweenTwoUnit(curSU, expandSU, qlog);
				if(tmpRelations!=null && tmpRelations.size()>0)
					simpleRelations.addAll(tmpRelations);
				//û�ҵ���
				else
				{
					tmpRelations = new ArrayList<SimpleRelation>();
					//������and�������硰In which films did Julia_Roberts and Richard_Gere play?��������һ��sr
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
		//step1: ����gAnswer��relation extraction�����ҵ���relations����Ӧpredicates����
		//����ʹ��δ��de-overlap��ԭʼsemantic relations
		//����Ϊ semantic unit ���� prefer type ��Ϣ
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
		
		//step2: ��û��ȷ��relation�ı߰���ganswer relation extract�ķ�ʽ��һ����Ϣ�����û�鵽�����ٸ��ݹ�����һ�¡�֮��ת���� semantic relation��ʽ������������item mapping��
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
// �����ǵ���ߣ�����Ҫȥ��
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
						
					Triple next = new Triple(-1, subj,rel, -1, obj,null,0);
					qlog.fw.write("++++ Triple detect: "+next+"\n");
				}
				// ��ǰunit�Ƿ�ӵ��type
				if(curSU.prefferdType != null)
				{
//					StringBuilder type = new StringBuilder("");				
//					for (Integer tt : sr.arg2Types) {
//						type.append(TypeFragment.typeId2ShortName.get(tt));
//						type.append('|');
//					}
					String type = TypeFragment.typeId2ShortName.get(curSU.prefferdType);
					Triple next = new Triple(-1, curSU.centerWord.getFullEntityName(),Globals.pd.typePredicateID,Triple.TYPE_ROLE_ID, type,null,0);
					qlog.fw.write("++++ Triple detect: "+next+"\n");
				}
				// ��ǰunit�Ƿ�ӵ��describe
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
	
	//�����᲻����ѭ����
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
		
		//���δʲ���Ϊnode������ Queen Elizabeth II�У�queenΪ���δ�
		if(modifierList.contains(cur.word))
			return false;
		
//		//parser��Ϊ����ʺ���ָ��Ĵʹ�ͬ���һ������ʣ�����ָ��Ĵ��ѱ�ʶ��Ϊһ��ent����ô���word����ֻ���������ã�������ĺ���ָ��Ĵ���һ�������
//		//����û�м����жϱ��Ƿ�Ϊnn���Ƿ���Ҫ���룿 | ��������൱�ڲ���������node������֮�������Ҫ�޸�
//		if(!cur.word.mayEnt && cur.father!=null && !cur.dep_father2child.startsWith("poss") && (cur.father.word.mayEnt||cur.father.word.mayType))
//			return false;

//������������� �����δʹ��� ���
//		//����һ��extendType�����������triple | ��Queen Elizabeth II�� �е� queen����û��queen���type���������ĺ����type��ͬ
//		if(cur.word.tmList!=null && cur.word.tmList.size()>0 && cur.word.tmList.get(0).prefferdRelation == -1)
//			return false;
//		//����һ��type���Һ����һ��ent�����硰television show Charmed������ʱcharmed��������node����television show���Ƕ��������Σ�����
//		//ʵ���� type+ent ��������ʽ��type�����������������ã�����Ϊ���ֱ�Ӳ���type�����ڵ㣬���ṹ������������������������õĴ���
//		if(cur.word.mayType && cur.father!=null && cur.father.word.mayEnt)
//			return false;
		
		if(cur.word.posTag.startsWith("N"))
			return true;

//���ʴ����Ͽ�ΪNODE����dfsʱ����һЩ���⣬�Ȳ���һ��
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
		//��û���ҵ����ʴʣ�������ԭ�������ȳ��ֵ�node; ע�����������ˡ����δʹ��򡰣������ was us president obama ..., target=obama������us
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
			
			//����û�ҵ���ֱ��ָ�ɵ�һ��word
			if(target == null)
				target = ds.nodesList.get(0);
			
			/* Are [E|tree_frogs] a type of [E|amphibian] , ��type��ָ
			*/
			for(DependencyTreeNode dtn: target.childrenList)
			{
				if(dtn.word.baseForm.equals("type"))
				{
					dtn.word.represent = target.word;
				}
			}
			
			
		}
		//where��ע��ͨ�� wh �� NN �任������ target��û���ж� NN �ܷ�ͨ�� isNode
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
					// which city ... �Ƚ�target��Ϊcity
					target.word.represent = word1;
					target = ds.getNodeByIndex(word1.position);
					int word1Pos = word1.position - 1;
					// word1 + be + (the) + word2, ��beΪroot����word1��word2���ܹ�ָ
					if(ds.root.word.baseForm.equals("be") && word1Pos+3 < words.length && words[word1Pos+1].baseForm.equals("be"))
					{
						// which city is [the] headquarters ...
						Word word2 = getTheModifiedWordBySentence(qlog.s, words[word1Pos+2]);
						if(words[word1Pos+2].posTag.equals("DT"))
							word2 = getTheModifiedWordBySentence(qlog.s, words[word1Pos+3]);
						int word2Pos = word2.position - 1;
						if(word2Pos+1 < words.length && isNodeCandidate(word2) && words[word2Pos+1].posTag.startsWith("IN"))
						{
							//In which city is [the] headquarters of ... ����targetΪheadquarters |ʵ����city��headquarters��ָ�������cityΪtarget���ṹ����� |ע�����������of�滻Ϊһ�����ʣ���targetӦ��Ϊcity
							//In which city was the president of Montenegro born? ��Ϊ���еķ�������city��presidentΪ����node
							target.word.represent = word2;
							target = ds.getNodeByIndex(word2.position);
						}
					}
				}
			}
			// target���� which������dependency tree���һ��
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
			//��⣺what is [the] sth1 prep. sth2?
			//what is sth? ���־�ʽ���ٳ��֣���ʹ���֣�һ�㲻�����5���ʡ�
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
					//˳��������ָ
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
			//��⣺who is [the] sth1 prep. sth2?  || Who was the pope that founded the Vatican_Television ? 
			//�������� who is sth? who do sth? ��target��Ϊwho
			//���� Who is the daughter of Robert_Kennedy married to��query��stanford tree�У�who��is���Ǹ��ӹ�ϵ���ǲ��У�����who����target
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
							//��� who is the sht1's sth2? ���������parser�У�who��sth2��ָ��be
							//�򵥵���Ϊ ��who��sth2��ָ��be�� ���У����Ե�����������û��prep����Ϊ��ָ
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
			//����sentence���һ��
			if(target.word.baseForm.equals("who"))
			{
				int curPos = target.word.position - 1;
				// who�ھ��г���һ��Ϊ��ָ
				if(curPos - 1 >= 0 && isNodeCandidate(words[curPos-1]))
				{
					target.word.represent = words[curPos-1];
					target = ds.getNodeByIndex(words[curPos-1].position);
				}
				else
				{
					//Who produced films starring Natalie_Portman��target��Ϊfilmͼ����ȷ���������who��Natalie����
					//ע��Ŀǰ Who produced Goofy? ���֣�target������Ϊ who����Ϊ �������������ʾ��target��Ϊent��������target����������������Ҫ�ʵ��Ǹ�����
					//TODO:�Ƿ�����һ����ǡ�start����֮���ڿ��ǣ������ó����ж������ֿ������������
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
			//��⣺how many sth ...  |eg: how many popular Chinese director are there
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
			//��⣺how much ... 
			else if(curPos+2 < words.length && words[curPos+1].baseForm.equals("much"))
			{
				Word modifiedWord = getTheModifiedWordBySentence(qlog.s, words[curPos+2]);
				// How much carbs does peanut_butter have 
				if(isNodeCandidate(modifiedWord))
				{
					target.word.represent = modifiedWord;
					target = ds.getNodeByIndex(modifiedWord.position);
				}
				// How much did Pulp_Fiction cost | ��dependency tree
				else
				{
					if(target.father!=null && isNodeCandidate(target.father.word))
					{
						target.word.represent = target.father.word;
						target = target.father;
					}
				}
			}
			
//dependncy tree��ʱ��������ֱ����sentence�м��
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
	 * ���εĸ������ȷ��dependency tree�У�һ��word(ent/type)ָ����һ��word����ͨ��Ϊmodϵ�У�����֮��û�������㣬�����Ƕ���������object
	 * ���磺Chinese teacher --> Chinese����teacher��the Chinese teacher Wang Wei --> Chinese��teacher������Wang Wei��
	 * ע�⣺the Television Show Charmed����Ϊ����һ��object��word sequence�ᱻ��ǰʶ����������»�������Ϊһ��word������Television_Show����Charmed
	 * �ҵ���ǰword�����ε��Ǹ�word������������α��ˣ��������Լ���
	 * ͨ��sentence������dependency tree (��Ϊ���߳����ɴ���)
	 * test case:
	 * 1) the highest Chinese mountain
	 * 2) the Chinese popular director
	 * */
	public Word getTheModifiedWordBySentence(Sentence s, Word modifier)
	{
		//�򵥵���Ϊ���������ֵ�node�����������һ��node��
		Word modifiedWord = modifier;
		
		//�Ȳ������ݴ�Ҳ����node����ֱ�ӷ��� 
		if(!isNodeCandidate(modifier) && !modifier.posTag.startsWith("JJ") && !modifier.posTag.startsWith("R"))
			return modifier;
		
//		//�жϣ�... dose the Yenisei_river flow? || did Pulp_Fiction cost? ����ʽ����Ϊ������ʽ����verb��������Ϊ�����ʱ���
//		int rightNodeCnt = 0;
//		for(int i=modifier.position;i<s.words.length;i++)
//			if(isNodeCandidate(s.words[i]))
//				rightNodeCnt++;
//		if(rightNodeCnt == 1)
//		{
//			//Does ... һ�����ʾ䲻����
//			for(int i=modifier.position-2;i>0;i--)
//				if(s.words[i].baseForm.equals("do"))
//					return modifiedWord;
//		}
		
		//�ɴ���Ϊ ent+noun ����ʽ��ent�������δʲ��Һ����noun����node��eg��Does the [Isar] [flow] into a lake?
		if(modifier.position<s.words.length && modifier.mayEnt && !s.words[modifier.position].mayEnt && !s.words[modifier.position].mayType && !s.words[modifier.position].mayLiteral)
		{
			s.words[modifier.position].omitNode = true;
			return modifier;
		}
		
		//ע������position���±��1��ʼ�����Դ�modifier�ĺ�һ���ʿ�ʼɨ�費�ü�1
		for(int i=modifier.position;i<s.words.length;i++)
		{
			Word word = s.words[i];
			if(isNodeCandidate(word))
			{
				modifiedWord = word;
			}
			//�� "popular","largest"�����δʣ������Ϊ�����δʣ������ּȲ���node�ֲ������ݴʣ���ֹͣ
			else if(!word.posTag.startsWith("JJ"))
			{
				break;
			}
		}
		return modifiedWord;
	}
	
	/*
	 * NodeCandidate�����ʸ��ΪNode������һ��Ҫ����query graph��
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