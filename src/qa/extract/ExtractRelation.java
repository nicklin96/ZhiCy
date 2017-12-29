package qa.extract;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.Queue;

import log.QueryLogger;
import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
//import nlp.ds.Word;
import paradict.ParaphraseDictionary;
import qa.Globals;
import rdf.SimpleRelation;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.SemanticUnit;

public class ExtractRelation {

	public static final int notMatchedCountThreshold = 1; // the bigger, the looser (more relations can be extracted)
	public static final int notCoverageCountThreshold = 2; 
	
	/*
	 * Find relations by dependency tree & paraphrases.
	 * */
	public ArrayList<SimpleRelation> findRelationsBetweenTwoUnit(SemanticUnit su1, SemanticUnit su2, QueryLogger qlog)
	{
		DependencyTree T = qlog.s.dependencyTreeStanford;
		if(qlog.isMaltParserUsed)
			T = qlog.s.dependencyTreeMalt;
		
		DependencyTreeNode n1 = T.getNodeByIndex(su1.centerWord.position), n2 = T.getNodeByIndex(su2.centerWord.position);
		ArrayList<DependencyTreeNode> shortestPath = T.getShortestNodePathBetween(n1,n2);
		
		ArrayList<SimpleRelation> ret = new ArrayList<SimpleRelation>();
		HashSet<String> BoW_T = new HashSet<String>();
		HashSet<String> SubBoW_T = new HashSet<String>();
				
		// Shortest path -> SubBag of Words
		for(DependencyTreeNode curNode: shortestPath)
		{
			String text = curNode.word.baseForm;
			if(!curNode.word.isIgnored && !Globals.stopWordsList.isStopWord(text))
			{
				//!split words |eg, soccer club -> soccer_club(after node recognition) -> soccer club(used in matching paraphrase)
				if(curNode.word.mayEnt || curNode.word.mayType)
				{
					String [] strArray = curNode.word.baseForm.split("_");
					for(String str: strArray)
						SubBoW_T.add(str);
				}
				else
				{
					SubBoW_T.add(text);
				}
			}
		}
		// DS tree -> Bag of Words
		for (DependencyTreeNode curNode : T.getNodesList()) 
		{
			if (!curNode.word.isIgnored) 
			{
				String text = curNode.word.baseForm;
				if(curNode.word.mayEnt || curNode.word.mayType)
				{
					String [] strArray = curNode.word.baseForm.split("_");
					for(String str: strArray)
						BoW_T.add(str);
				}
				else
				{
					BoW_T.add(text);	
				}
			}
		}
		// Find candidate patterns by SubBoW_T & inveretdIndex
		HashSet<String> candidatePatterns = new HashSet<String>();
		for (String curWord : SubBoW_T) 
		{
			ArrayList<String> postingList = Globals.pd.invertedIndex.get(curWord);
			if (postingList != null) 
			{
				candidatePatterns.addAll(postingList);
			}
		}
		
		// Check patterns by BoW_P & subtree matching
		int notMatchedCount = 0;
		HashSet<String> validCandidatePatterns = new HashSet<String>();
		for (String p : candidatePatterns) 
		{
			String[] BoW_P = p.split(" ");
			notMatchedCount = 0;	// not match number between pattern & question
			for (String s : BoW_P) 
			{
				if (s.length() < 2)
					continue;
				if (s.startsWith("["))
					continue;
				if (Globals.stopWordsList.isStopWord(s))
					continue;
				if (!BoW_T.contains(s)) 
				{
					notMatchedCount ++;
					if (notMatchedCount > notMatchedCountThreshold)
						break;
				}
			}
			if (notMatchedCount <= notMatchedCountThreshold) 
			{
				validCandidatePatterns.add(p);
				//TODO: to support matching like [soccer_club]
				subTreeMatching(p, BoW_P, shortestPath, T, qlog, ret, 'S');
			}
		}
		
		// Another chance for [soccer_club] (the relation embedded in nodes)
		if(validCandidatePatterns.size() > 0)
		{
			if(n1.word.originalForm.contains("_") || n2.word.originalForm.contains("_"))
			{
				for (String p : validCandidatePatterns) 
				{
					String[] BoW_P = p.split(" ");
					notMatchedCount = 0;
					int mappedCharacterCount = 0;
					int matchedWordInArg = 0;

					boolean[] matchedFlag = new boolean[BoW_P.length];
					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}
					int idx = 0;
					for (String s : BoW_P) 
					{	
						if(n1.word.baseForm.contains(s) || n2.word.baseForm.contains(s)) // Hit nodes
							matchedWordInArg++;
						if(BoW_T.contains(s))
						{
							mappedCharacterCount += s.length();
							matchedFlag[idx] = true;
						}
						idx++;
						if (s.length() < 2) 
							continue;
						if (s.startsWith("["))
							continue;
						if (Globals.stopWordsList.isStopWord(s))
							continue;
						if (!BoW_T.contains(s)) 
							notMatchedCount ++;
					}
					// Success if has 2 hits
					if(matchedWordInArg >= 2)
					{
						double matched_score = ((double)(BoW_P.length-notMatchedCount))/((double)(BoW_P.length));
						if (matched_score > 0.95) 
							matched_score *= 10; // award for WHOLE match 
						
						// TODO: this will make LONGER one has LARGER score, sometimes unsuitable | eg, be bear die in
						matched_score = matched_score * Math.sqrt(mappedCharacterCount);
						
						SimpleRelation sr = new SimpleRelation();
						sr.arg1Word = n1.word;
						sr.arg2Word = n2.word;
						sr.relationParaphrase = p;
						sr.matchingScore = matched_score;
						sr.extractingMethod = 'X';
						
						if (n1.dep_father2child.endsWith("subj"))
							sr.preferredSubj = sr.arg1Word;
						
						sr.arg1Word.setIsCovered();
						sr.arg2Word.setIsCovered();
						
						sr.setPasList(p, matched_score, matchedFlag);
						sr.setPreferedSubjObjOrder(T);
						
						ret.add(sr);
					}
				}
			}
		}
		return ret;
	}
	
	// Core function of paraphrase matching
	private void subTreeMatching (String pattern, String[] BoW_P, 
			ArrayList<DependencyTreeNode> shortestPath,
			DependencyTree T, QueryLogger qlog, 
			ArrayList<SimpleRelation> ret, char extractingMethod) 
	{
		DependencyTreeNode n1 = shortestPath.get(0);
		DependencyTreeNode n2 = shortestPath.get(shortestPath.size()-1);
		
		ParaphraseDictionary pd = Globals.pd;
		Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
		queue.add(T.getRoot());
				
		for(DependencyTreeNode curOuterNode: shortestPath)
		{
			outer:
			for(String s: BoW_P)
			{
				if(s.equals(curOuterNode.word.baseForm))
				{
					// 开始尝试匹配所有点
					ArrayList<DependencyTreeNode> subTreeNodes = new ArrayList<DependencyTreeNode>();
					Queue<DependencyTreeNode> queue2 = new LinkedList<DependencyTreeNode>();
					queue2.add(curOuterNode);
					
					int unMappedLeft = BoW_P.length;	//尚未匹配的单词数
					int mappedCharacterCount = 0;	// 匹配的"字符数"
					int hitPathCnt = 0;	//pattern中的word落在shortest path上，显然比落在外面更可信
					int hitPathBetweenTwoArgCnt = 0; //pattern中的word落在shortest path上，并且不包括两端的两个变量
					double mappedCharacterCountPunishment = 0;	// 我们不希望[[]]这种排在前面，给点惩罚
					
					DependencyTreeNode curNode;
					boolean[] matchedFlag = new boolean[BoW_P.length];
					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}			

					while (unMappedLeft > 0 && (curNode=queue2.poll())!=null) 
					{
						if (curNode.word.isIgnored) continue;
						int idx = 0;
						for (String ss : BoW_P) 
						{
							// words in pattern only can be matched once
							if (!matchedFlag[idx]) 
							{
								// check word 
								if (ss.equals(curNode.word.baseForm)) 
								{	
									unMappedLeft --;
									subTreeNodes.add(curNode);
									queue2.addAll(curNode.childrenList);
									matchedFlag[idx] = true;
									mappedCharacterCount += ss.length();
									if(shortestPath.contains(curNode))
									{
										hitPathCnt++;
										if(curNode!=n1 && curNode!=n2)
											hitPathBetweenTwoArgCnt++;
									}
									break;
								}
								// check POS tag
								else if (ss.startsWith("[") && posSame(curNode.word.posTag, ss)) 
								{	
									unMappedLeft --;
									subTreeNodes.add(curNode);
									queue2.addAll(curNode.childrenList);
									matchedFlag[idx] = true;
									mappedCharacterCount += curNode.word.baseForm.length();//稍微和上面不同
									mappedCharacterCountPunishment += 0.01;
									break;
								}
							}
							idx ++;
						}
					}
					int unMatchedNoneStopWordCount = 0;
					int matchedNoneStopWordCount = 0;
					for (int idx = 0; idx < BoW_P.length; idx ++) {
						if (BoW_P[idx].startsWith("[")) continue;
						if (!matchedFlag[idx]) {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// unmatched
								unMatchedNoneStopWordCount ++;
						}
						else {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// matched
								matchedNoneStopWordCount ++;
						}							
					}

					if (unMatchedNoneStopWordCount > notMatchedCountThreshold) {
						if(qlog.MODE_debug) System.out.println("----But the pattern\"" + pattern + "\" is not a subtree.");
						break outer;
					}
					
					// 匹配的部分必须有实词，不能全是停用词
					// 匹配的非停用词个数大于0
					if (matchedNoneStopWordCount == 0){
						if(qlog.MODE_debug) System.out.println("----But the matching for pattern \"" + pattern + "\" does not have content words.");
						break outer;
					}
					
					// 如果是“不完全匹配”，若匹配的部分恰好是另一个完整pattern，则当前pattern就不考虑了
					if (unMappedLeft > 0) {
						StringBuilder subpattern = new StringBuilder();
						for (int idx = 0; idx < BoW_P.length; idx ++) {
							if (matchedFlag[idx]) {
								subpattern.append(BoW_P[idx]);
								subpattern.append(' ');
							}
						}
						subpattern.deleteCharAt(subpattern.length()-1);
						if (pd.nlPattern_2_predicateList.containsKey(subpattern)) {
							if(qlog.MODE_debug) System.out.println("----But the partially matched pattern \"" + pattern + "\" is another pattern.");
							break outer;
						}
					}
					
					// !Preposition | suppose only have one preposition
					// TODO: consider more preposition | the first preposition may be wrong
					DependencyTreeNode prep = null;
					for (DependencyTreeNode dtn : subTreeNodes) {
						outer2:
						for (DependencyTreeNode dtn_child : dtn.childrenList) {							
							if(pd.prepositions.contains(dtn_child.word.baseForm)) {
								prep = dtn_child;
								break outer2;
							}
						}
					}
					boolean isContained = false;
					for(DependencyTreeNode dtn_contain : subTreeNodes) {
						if(dtn_contain == prep) isContained = true;
					}
					if(!isContained && prep != null) {
						subTreeNodes.add(prep);
					}
					
					// Relation extracted, set COVER flags
					for (DependencyTreeNode dtn : subTreeNodes) 
					{
						dtn.word.isCovered = true;
					}
					
					int cnt = 0;
					double matched_score = ((double)(BoW_P.length-unMappedLeft))/((double)(BoW_P.length));
					if (matched_score > 0.95) 
						matched_score *= 10; // Award for WHOLE match
					
					//pattern与path重合的部分比例越大，分数越高；如果没有于两边的arg重合，分数更高
					if(hitPathCnt != 0)
					{
						double hitScore = 1 + (double)hitPathCnt/(double)BoW_P.length;
						if(hitPathBetweenTwoArgCnt == hitPathCnt)
							hitScore += 1;
						else if(shortestPath.size() >= 4)	//如果path足够长，pattern却依然与arg重合，认为要扣分
						{
							//hitScore = 0.5;
							if(hitPathBetweenTwoArgCnt == 0) //path足够长，但pattern完全与arg重合,扣分更多
								hitScore = 0.25;
						}
						matched_score *= hitScore;
					}
					
					matched_score = matched_score * Math.sqrt(mappedCharacterCount) - mappedCharacterCountPunishment;	// the longer, the better (unsuitable in some cases)
					if (qlog.MODE_debug) System.out.println("☆" + pattern + ", score=" + matched_score);

					DependencyTreeNode subject = n1;
					DependencyTreeNode object = n2;
					if (subject != object) 
					{	
						SimpleRelation sr = new SimpleRelation();
						sr.arg1Word = subject.word;
						sr.arg2Word = object.word;
						sr.relationParaphrase = pattern;
						sr.matchingScore = matched_score;
						sr.extractingMethod = extractingMethod;
						
						if (subject.dep_father2child.endsWith("subj"))
							sr.preferredSubj = sr.arg1Word;
						
						sr.arg1Word.setIsCovered();
						sr.arg2Word.setIsCovered();
						
						sr.setPasList(pattern, matched_score, matchedFlag);
						sr.setPreferedSubjObjOrder(T);
						
						ret.add(sr);
						cnt ++;
						//String binaryRelation = "<" + subjectString + "> <" + pattern + "> <" + objectString + ">";
					}
					if (cnt == 0) break outer;
				}
			}
		}
		
	}
	
	// [[det]], [[num]], [[adj]], [[pro]], [[prp]], [[con]], [[mod]]
	public boolean posSame(String tag, String posWithBracket) {
		if (	(posWithBracket.charAt(2) == 'd' && tag.equals("DT"))
			||	(posWithBracket.charAt(2) == 'n' && tag.equals("CD"))
			||	(posWithBracket.charAt(2) == 'a' && (tag.startsWith("JJ") || tag.startsWith("RB")))
			||	(posWithBracket.charAt(2) == 'c' && tag.startsWith("CC"))//TODO: how about "IN: subordinating conjunction"?
			||	(posWithBracket.charAt(2) == 'm' && tag.equals("MD"))) {
			return true;
		}
		else if (posWithBracket.charAt(2) == 'p') {
			if (	(posWithBracket.charAt(4) == 'o' && tag.startsWith("PR"))
				||	(posWithBracket.charAt(4) == 'p' && (tag.equals("IN") || tag.equals("TO")))) {
				return true;
			}
		}
		return false;
	}
	
	public HashMap<Integer, SemanticRelation> groupSimpleRelationsByArgsAndMapPredicate (ArrayList<SimpleRelation> simpleRelations) {
		System.out.println("==========Group Simple Relations=========");
		
		HashMap<Integer, SemanticRelation> ret = new HashMap<Integer, SemanticRelation>();
		HashMap<Integer, HashMap<Integer, StringAndDouble>>  key2pasMap = new HashMap<Integer, HashMap<Integer, StringAndDouble>>();
		for(SimpleRelation simr : simpleRelations) 
		{
			int key = simr.getHashCode();
			if (!ret.keySet().contains(key)) 
			{
				ret.put(key, new SemanticRelation(simr));
				key2pasMap.put(key, new HashMap<Integer, StringAndDouble>());
			}
			SemanticRelation semr = ret.get(key);
			HashMap<Integer, StringAndDouble> pasMap = key2pasMap.get(key);
						
			// Just use to display.
			if (simr.matchingScore > semr.LongestMatchingScore) 
			{
				semr.LongestMatchingScore = simr.matchingScore;
				semr.relationParaphrase = simr.relationParaphrase;
			}
			
			//这里只考虑“pattern和pid之间的匹配分数“而不考虑之前抽取时的”matching score“合理吗？ 自答：抽取分数已经在pasList的得分里了，paslist的得分=抽取得分乘以匹配得分乘以一个修正的东西
			//这里的意思是，对于一个特定的pid=x，不管你是哪个pattern来的，也不管你抽取时的匹配程度，我就记录下最大”综合得分“和它对应的pattern。
			for (int pid : simr.pasList.keySet()) {
				double score = simr.pasList.get(pid);
				if (!pasMap.containsKey(pid)) {
					pasMap.put(pid, new StringAndDouble(simr.relationParaphrase, score));
				}
				else if (score > pasMap.get(pid).score) {
					pasMap.put(pid, new StringAndDouble(simr.relationParaphrase, score));
				}
			}
		}
		
		for (Integer key : key2pasMap.keySet()) {
			SemanticRelation semr = ret.get(key);
			HashMap<Integer, StringAndDouble> pasMap = key2pasMap.get(key);
			semr.predicateMappings = new ArrayList<PredicateMapping>();
			//System.out.print("<"+semr.arg1Word.getFullEntityName() + "," + semr.arg2Word.getFullEntityName() + ">:");
			for (Integer pid : pasMap.keySet()) 
			{	
				semr.predicateMappings.add(new PredicateMapping(pid, pasMap.get(pid).score, pasMap.get(pid).str));
				//System.out.print("[" + Globals.pd.getPredicateById(pid) + "," + pasMap.get(pid).str + "," + pasMap.get(pid).score + "]");
			}
			Collections.sort(semr.predicateMappings);
		}
		System.out.println("=========================================");
		return ret;
	}	
	
	
}

class StringAndDouble {
	public String str;
	public double score;
	public StringAndDouble (String str, double score) {
		this.str = str;
		this.score = score;
	}
}
