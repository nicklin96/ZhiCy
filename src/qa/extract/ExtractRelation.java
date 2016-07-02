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
import nlp.ds.Word;
import paradict.ParaphraseDictionary;
import qa.Globals;
import rdf.SimpleRelation;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.SemanticUnit;

public class ExtractRelation {

	public static final int notMatchedCountThreshold = 1;// 该threshold越大，匹配的程度越小（越放松）
	public static final int notCoverageCountThreshold = 2; 
	
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
				
		// 将shortest path中“非停用词”作为它的SubBag of Words
		for(DependencyTreeNode curNode: shortestPath)
		{
			String text = curNode.word.baseForm;
			if(!curNode.word.isIgnored && !Globals.stopWordsList.isStopWord(text))
			{
				//例：soccer club经过预处理合成为一个word：soccer_club，找relation可能要用到原词，所以要再切开
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
		// 两个节点间关系的作用范围可能不止最短路径，具体范围不好找，就把整个ds tree视为可能范围
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
		// 找到SubBoW_T中的词对应的patterns，这些pattern至少包含了question中的一个word
		HashSet<String> candidatePatterns = new HashSet<String>();
		for (String curWord : SubBoW_T) 
		{
			ArrayList<String> postingList = Globals.pd.invertedIndex.get(curWord);
			if (postingList != null) 
			{
				candidatePatterns.addAll(postingList);
			}
		}
		
		// 检验这些patterns是否真的是T的Bag of Words的子集
		// 如果是，是否能找到匹配的子树
		int notMatchedCount = 0;
		HashSet<String> validCandidatePatterns = new HashSet<String>();
		for (String p : candidatePatterns) 
		{
			String[] BoW_P = p.split(" ");
			notMatchedCount = 0;	// notMatchedCount记录了当前NL pattern与question不匹配的单词数
			for (String s : BoW_P) {	//如果该string中（BoW_P中）的每个词都出现在BoW_T中
				if (s.length() < 2) //0, 1
					continue;
				if (s.startsWith("["))
					continue;
				if (Globals.stopWordsList.isStopWord(s))
					continue;
				if (!BoW_T.contains(s)) {
					notMatchedCount ++;	// 遇到一个不匹配的word，isSubSet就++
					if (notMatchedCount > notMatchedCountThreshold)
						break;
				}
				
			}
			if (notMatchedCount <= notMatchedCountThreshold) 
			{
				validCandidatePatterns.add(p);
				//System.out.println("[" + (++i) + "]" + p);
				// 到这里，说明p至多包含了一个不在BoW_T中出现的非停用词
				// 检验这些pattens是否真的是T的Bag of Words的子集
				// 如果是，是否能找到匹配的子树
				
				//TODO:注意这个函数还是不能处理  soccer club 变成 soccer_club 后的关系匹配
				subTreeMatching(p, BoW_P, shortestPath, T, qlog, ret, 'S');
			}
		}
		
		//专门为 soccer club 变为 soccer_club 导致匹配失败的情况再一次机会
		if(validCandidatePatterns.size() > 0)
		{
			if(n1.word.originalForm.contains("_") || n2.word.originalForm.contains("_"))
			{
				for (String p : validCandidatePatterns) 
				{
					String[] BoW_P = p.split(" ");
					notMatchedCount = 0;	// notMatchedCount记录了当前NL pattern与question不匹配的单词数
					int mappedCharacterCount = 0;
					int matchedWordInArg = 0;

					boolean[] matchedFlag = new boolean[BoW_P.length];
					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}
					int idx = 0;
					for (String s : BoW_P) 
					{	
						if(n1.word.baseForm.contains(s) || n2.word.baseForm.contains(s))
							matchedWordInArg++;
						if(BoW_T.contains(s))
						{
							mappedCharacterCount += s.length();
							matchedFlag[idx] = true;
						}
						idx++;
						
						if (s.length() < 2) //0, 1
							continue;
						if (s.startsWith("["))
							continue;
						if (Globals.stopWordsList.isStopWord(s))
							continue;
						
						if (!BoW_T.contains(s)) 
							notMatchedCount ++;	// 遇到一个不匹配的word，isSubSet就++
					}
					//有两个词落在arg上，直接认为成功，要编一下得分构造sr
					if(matchedWordInArg >= 2)
					{
						double matched_score = ((double)(BoW_P.length-notMatchedCount))/((double)(BoW_P.length));	//匹配的比例越多，得分越高
						if (matched_score > 0.95) 
							matched_score *= 10;// 奖励完全匹配，实际上这里没有考虑[[]]等，并不是真的完全匹配
						
						//下面这个字符数得分，会让一些奇怪的长的pattenr比如 ”be bear die in“的得分较高
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
	
	private void subTreeMatching (String pattern, String[] BoW_P, 
			ArrayList<DependencyTreeNode> shortestPath,
			DependencyTree T, QueryLogger qlog, 
			ArrayList<SimpleRelation> ret, char extractingMethod) 
	{
		// relation 的两个变量
		DependencyTreeNode n1 = shortestPath.get(0);
		DependencyTreeNode n2 = shortestPath.get(shortestPath.size()-1);
		
		ParaphraseDictionary pd = Globals.pd;
		Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
		queue.add(T.getRoot());
		//DependencyTreeNode curOuterNode = null;
				
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
							// pattern中的词只能match一次
							if (!matchedFlag[idx]) 
							{
								// 词匹配 
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
								// 词性匹配
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
					int unMatchedNoneStopWordCount = 0;	// Pattern中不匹配的非停用词个数
					int matchedNoneStopWordCount = 0;
					for (int idx = 0; idx < BoW_P.length; idx ++) {
						if (BoW_P[idx].startsWith("[")) continue;
						if (!matchedFlag[idx]) {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// 不匹配的非停用词
								unMatchedNoneStopWordCount ++;
						}
						else {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// 匹配的非停用词
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
					
					// 介词扩展
					// 假设每个短语中只有1个介词
					// TODO 不一定只有一个介词，或者第一个介词可能是错误的
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

					
					// 此时，关系抽取成功
					// 标记子树上的结点：已覆盖
					for (DependencyTreeNode dtn : subTreeNodes) 
					{
						dtn.word.isCovered = true;
					}
					
					int cnt = 0;
					double matched_score = ((double)(BoW_P.length-unMappedLeft))/((double)(BoW_P.length));	//匹配的比例越多，得分越高
					if (matched_score > 0.95) 
						matched_score *= 10;// 奖励完全匹配
					
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
					
					//下面这个字符数得分，会让一些奇怪的长的pattenr比如 ”be bear die in“的得分较高
					matched_score = matched_score * Math.sqrt(mappedCharacterCount) - mappedCharacterCountPunishment;	// 匹配的"字符数"越多（偶然性越小），显然得分越高	//开方平滑一下 
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
						
			//只是记录了一下最高的matching score和它对应的pattern用来充门面（out时展示这两个东西），实际上与predicate mapping没有必然联系。而后者才是真正左右结果的。
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
			for (Integer pid : pasMap.keySet()) {
				semr.predicateMappings.add(new PredicateMapping(pid, pasMap.get(pid).score, pasMap.get(pid).str));
				//System.out.print("[" + Globals.pd.getPredicateById(pid) + "," + pasMap.get(pid).str + "," + pasMap.get(pid).score + "]");
			}
			Collections.sort(semr.predicateMappings);
		}
		System.out.println("=========================================");
		return ret;
	}	
	
	/*
	 * 1、优先级（首字母大写的ent）>mayType>mayEnt
	 * 2、mayEnt=1,则为常量
	 * 3、mayType=1，分为两种情况：
	 * （1）认为该word为变量，在top-k阶段会为这种情况加一条该word的type三元组。
	 * 例如：Which books by Kerouac were published by Viking Press? 中的“books”。
	 * （2）认为该word为常量。这只在它被用来修饰其他word时，即谓词是<type1>时。
	 * 例如：Are tree frogs a type of amphibian? 中的“amphibian”。How many [countries] are there in [exT:Europe]。
	 * 
	 * [2016-6-17] 开始检测 <带triple的变量> | 主要为”xx国人/xx国的“在不同的条件下不同的识别
	 * 
	 * */
	public void constantVariableRecognition(HashMap<Integer, SemanticRelation> semanticRelations, QueryLogger qlog, TypeRecognition tr) 
	{
		Word[] words = qlog.s.words;
		//目前是只识别 “被semantic relaiton覆盖的节点“。即一些 modifier节点没有做以下”常/变量检测“以及”embedded信息扩充“
		for (Integer it : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(it);
			int arg1WordPos = sr.arg1Word.position - 1;
			int arg2WordPos = sr.arg2Word.position - 1;
			
//			// [2016-6-17]有时用户会大写变量，如： all [Canadians] that... 决定不依赖于大写，只依赖于node recognition阶段的mapping结果。
//			// [2016-6-17]因为即使这里根据大写设为常量，在后面top-k join的时候没有对应mapping，也会被抛弃
//			// 如果包含首字母大写(非句首单词)，或是命名实体，则视作常量
//			if (sr.arg1Word.isNER() != null && !sr.arg1Word.isNER().equals("PERSON")
//				|| containsUpperCharacter(sr.arg1Word.getFullEntityName(), sr.arg1Word.getNnHead().position)) 
//			{
//				sr.isArg1Constant = true;
//				
//				//[2015-12-12]首字母大写不太可能是type，这里mayType修正为false  | [2015-12-13]反例 How many countries are there in [exType|Europe]，改为针对word sequence
//				//[2016-6-17] 反例In which U.S. state is Mount McKinley located -> US_state[type:<yago:StatesOfTheUnitedStates>].去掉此修正
//				if(sr.arg1Word.baseForm.contains("_"))
//					sr.arg1Word.mayType = false;
//			}

/*
 * extend variable识别
 * */
			tr.recognizeExtendVariable(sr.arg1Word);
			tr.recognizeExtendVariable(sr.arg2Word);
			
/*
 * 常变量识别，默认的 isArgConstant = false
 * */			
			// extendVariable优先级最高，先判断可能是它的情况
			if(sr.arg1Word.mayExtendVariable)
			{
				//eg: 既是extendVariable：?canadian <birthPlace> <Canada> 又存在<type: canadian>；这时我们放弃type
				if(sr.arg1Word.mayType)
					sr.arg1Word.mayType = false;
				
				//可能是ent，需要进行规则判断
				if(sr.arg1Word.mayEnt)
				{
					//rule: [extendVaraible&&ent]+noun,则认为是ent || Canadian movies -> ent:Canada
					if(arg1WordPos+1 < words.length && words[arg1WordPos+1].posTag.startsWith("N"))
					{
						sr.arg1Word.mayExtendVariable = false;
						sr.isArg1Constant = true;
					}
					//否则认为是变量，放弃mayEnt
					else
					{
						sr.arg1Word.mayEnt = false;
					}
				}
			}
			// type优先级较高，判断type
			else if(sr.arg1Word.mayType)
			{
				//rule：in/of [type]，则认为是作常量  ||How many [countries] are there in [exT:Europe] -> ?uri rdf:type yago:EuropeanCountries
				if(arg1WordPos >= 2 && (words[arg1WordPos-1].baseForm.equals("in") || words[arg1WordPos-1].baseForm.equals("of"))  && !words[arg1WordPos-2].posTag.startsWith("V"))
				{
					sr.isArg1Constant = true;
					//选择作为”常量type“，则preferred relation = <type1>
					double largerScore = 1000;
					if(sr.predicateMappings!=null && sr.predicateMappings.size()>0)
						largerScore = sr.predicateMappings.get(0).score * 2;
					PredicateMapping nPredicate = new PredicateMapping(Globals.pd.typePredicateID, largerScore, "[type]");
					sr.predicateMappings.add(0,nPredicate);
					
					//作为常量的type应该放在后面
					sr.preferredSubj = sr.arg2Word;
				}
				//又是type又是ent的情况，还是以type为主，但这里先不修改mayEnt
			}
			//只判断出ent，那就是常量了
			else if(sr.arg1Word.mayEnt)
			{
				sr.isArg1Constant = true;
			}
			
//			// 如果包含首字母大写(非句首单词)，或是命名实体，则视作常量
//			if (sr.arg2Word.isNER() != null && !sr.arg2Word.isNER().equals("PERSON")
//				|| containsUpperCharacter(sr.arg2Word.getFullEntityName(), sr.arg2Word.getNnHead().position)) 
//			{
//				sr.isArg2Constant = true;
//				
//				//[2015-12-12]首字母大写不太可能是type，这里mayType修正为false  | [2015-12-13]反例 How many countries are there in [exType|Europe]，改为针对word sequence
//				if(sr.arg2Word.baseForm.contains("_"))
//					sr.arg2Word.mayType = false;
//			}
			
			// extendVariable优先级最高，先判断可能是它的情况
			if(sr.arg2Word.mayExtendVariable)
			{
				//eg: 既是extendVariable：?canadian <birthPlace> <Canada> 又存在<type: canadian>；这时我们放弃type
				if(sr.arg2Word.mayType)
					sr.arg2Word.mayType = false;
				
				//可能是ent，需要进行规则判断
				if(sr.arg2Word.mayEnt)
				{
					//rule: [extendVaraible&&ent]+noun,则认为是ent || Canadian movies -> ent:Canada
					if(arg2WordPos+1 < words.length && words[arg2WordPos+1].posTag.startsWith("N"))
					{
						sr.arg2Word.mayExtendVariable = false;
						sr.isArg2Constant = true;
					}
					//否则认为是变量，放弃mayEnt
					else
					{
						sr.arg2Word.mayEnt = false;
					}
				}
			}
			// type优先级较高，判断type
			else if(sr.arg2Word.mayType)
			{
				//rule：非动词+in/of [type]，则认为是作常量  ||How many [countries] are there in [exT:Europe] -> ?uri rdf:type yago:EuropeanCountries
				if(arg2WordPos >= 2 && (words[arg2WordPos-1].baseForm.equals("in") || words[arg2WordPos-1].baseForm.equals("of")) && !words[arg2WordPos-2].posTag.startsWith("V") )
				{
					sr.isArg2Constant = true;
					//选择作为”常量type“，则preferred relation = <type1>
					double largerScore = 1000;
					if(sr.predicateMappings!=null && sr.predicateMappings.size()>0)
						largerScore = sr.predicateMappings.get(0).score * 2;
					PredicateMapping nPredicate = new PredicateMapping(Globals.pd.typePredicateID, largerScore, "[type]");
					sr.predicateMappings.add(0,nPredicate);
					
					//作为常量的type应该放在后面
					sr.preferredSubj = sr.arg1Word;
				}
			}
			else if(sr.arg2Word.mayEnt)
			{
				sr.isArg2Constant = true;
			}
			
			if(sr.arg1Word != sr.preferredSubj)
				sr.swapArg1Arg2();
		}
	}
	private boolean containsUpperCharacter (String str, int beginIdx) {
		String[] array = str.split(" ");
		for (String s: array) {
			if (Character.isUpperCase(s.charAt(0)) && beginIdx!=1) {
				return true;
			}
			beginIdx++;
		}
		return false;
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
