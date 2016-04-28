//package qa.extract;
//
//import java.util.ArrayList;
//import java.util.Collections;
//import java.util.HashMap;
//import java.util.HashSet;
//import java.util.LinkedList;
//import java.util.Queue;
//
//import paradict.ParaphraseDictionary;
//
//import nlp.ds.DependencyTree;
//import nlp.ds.DependencyTreeNode;
//import nlp.ds.Sentence;
//import nlp.ds.Word;
//
//import qa.Globals;
//import rdf.PredicateMapping;
//import rdf.SemanticRelation;
//
//import log.QueryLogger;
//
//public class RelationExtraction {
//	
//	public static final int notMatchedCountThreshold = 1;// 该threshold越大，匹配的程度越小（越放松）
//	public static final int notCoverageCountThreshold = 2; 
//
//	public void process (QueryLogger qlog) {
//		long t;
//		
//		// 1. special relations recognition by rules
//		t = System.currentTimeMillis();
//		SpecialRelationRecognition srrcg = new SpecialRelationRecognition(qlog.s);
//		srrcg.recognize();
//		qlog.timeTable.put("SpecialRelation", (int)(System.currentTimeMillis()-t));
//		
//				
//		// 2. simple binary semantic relations extraction
//		// simple: could be duplicated, not co-reference resoluted, not grouped ...
//		t = System.currentTimeMillis();
//		
//		
//		//use StanfordParser first, then MaltParser.
//		ArrayList<SimpleRelation> simpleRelations = extractSimpleRelations(qlog, 'S');
//		if (!isWellCovered(qlog.s) || simpleRelations.isEmpty()) {
//			simpleRelations.addAll(extractSimpleRelations(qlog, 'M'));
//			qlog.isMaltParserUsed = true;
//			if(!isWellCovered(qlog.s)) {
//				// N-gram
//				// TODO
//			}
//		}
//		
//		
//		qlog.timeTable.put("SimpleRelation", (int)(System.currentTimeMillis()-t));
//		
//		//output simple ralations 
//		/*
//		System.out.println("=====================================");
//		System.out.println("Simple relations:");
//		for (SimpleRelation sr: simpleRelations)
//			System.out.println(sr);
//		System.out.println("=====================================");
//		*/
//		
//		// 3. co-reference resolution
//		t = System.currentTimeMillis();
//		CorefResolution cr = new CorefResolution();
//		cr.process(simpleRelations, qlog);
//		qlog.timeTable.put("CoreferenceResolution", (int)(System.currentTimeMillis()-t));
//		
//		
//		// 4. group simple relations by arguments
//		t = System.currentTimeMillis();
//		HashMap<Integer, SemanticRelation> semanticRelations = groupSimpleRelationsByArgsAndMapPredicate(simpleRelations);
//		qlog.timeTable.put("GroupSimpleRelations", (int)(System.currentTimeMillis()-t));
//		
//
//		// 5. type recognition
//		t = System.currentTimeMillis();
//		TypeRecognition tr = new TypeRecognition();
//		tr.recognize(semanticRelations);
//		qlog.timeTable.put("GroupSimpleRelations", (int)(System.currentTimeMillis()-t));
//		
//		// 将未 de-overlap 的原始 semantic relations 放入qlog husen
//		qlog.semanticRelations = semanticRelations;
//		
//		// 6. de-overlap
//		t = System.currentTimeMillis();
//		ArrayList<HashMap<Integer, SemanticRelation>> semanticRelationsList = dePhraseOverlap(semanticRelations, qlog);
//		qlog.timeTable.put("DeOverlap", (int)(System.currentTimeMillis()-t));
//		
//		
//		// 7. put into the special relations
//		t = System.currentTimeMillis();
//		for (HashMap<Integer, SemanticRelation> srmap : semanticRelationsList) {
//			srrcg.execute(srmap, tr);
//		}
//		qlog.timeTable.put("SpecialRelation", qlog.timeTable.get("SpecialRelation")+(int)(System.currentTimeMillis()-t));
//
//		// 8. variable/constant recognition
//		t = System.currentTimeMillis();
//		for (HashMap<Integer, SemanticRelation> srmap : semanticRelationsList) {
//			constantVariableRecognition(srmap);
//		}
//		qlog.timeTable.put("SpecialRelation", qlog.timeTable.get("SpecialRelation")+(int)(System.currentTimeMillis()-t));
//		
//		
//		qlog.semanticRelationsList = semanticRelationsList;
//		
//		//System.out.println("=============");
//		//for (SimpleRelation sr : simpleRelations) {
//		//	System.out.println(sr);
//		//}
//		System.out.println("=============");
//		int groupCount = 1;
//		for (HashMap<Integer, SemanticRelation> semRltns : semanticRelationsList) {
//			System.out.println("De-overlap group " + groupCount);
//			for (Integer key : semRltns.keySet()) {
//				System.out.println("☆"+semRltns.get(key));
//				System.out.print("\t--->");
//				for (PredicateMapping pm : semRltns.get(key).predicateMappings) {
//					System.out.print("<" + Globals.pd.getPredicateById(pm.pid) + "("+pm.pid+")" + ":" + pm.score + "(" + pm.parapharase + ")>");
//				}
//				System.out.println();
//			}
//			groupCount ++;
//		}
//		System.out.println("=============");
//		
//		System.out.println("SpecialRelation: t=" + qlog.timeTable.get("SpecialRelation") + "ms");
//		System.out.println("SimpleRelation: t=" + qlog.timeTable.get("SimpleRelation") + "ms");
//		System.out.println("CoreferenceResolution: t=" + qlog.timeTable.get("CoreferenceResolution") + "ms");
//		System.out.println("GroupSimpleRelations: t=" + qlog.timeTable.get("GroupSimpleRelations") + "ms");
//	}
//	
//	public ArrayList<SimpleRelation> extractSimpleRelations (QueryLogger qlog, char extractingMethod) {		
//		ArrayList<SimpleRelation> ret = new ArrayList<SimpleRelation>();
//		
//		DependencyTree T;
//		if (extractingMethod == 'S') T = qlog.s.dependencyTreeStanford;
//		else if (extractingMethod == 'M') T = qlog.s.dependencyTreeMalt;
//		else return ret;
//		
//		HashSet<String> BoW_T = new HashSet<String>();
//		HashSet<String> SubBoW_T = new HashSet<String>();
//				
//		// 将T中“非停用词”作为它的SubBag of Words
//		for (DependencyTreeNode curNode : T.getNodesList()) {
//			if (!curNode.word.isIgnored) {
//				String text = curNode.word.baseForm;
//				BoW_T.add(text);
//				if (!Globals.stopWordsList.isStopWord(text))
//					SubBoW_T.add(text);				
//			}
//		}
//
//		// 找到SubBoW_T中的词对应的patterns，这些pattern至少包含了question中的一个word
//		HashSet<String> candidatePatterns = new HashSet<String>();
//		for (String curWord : SubBoW_T) {
//			ArrayList<String> postingList = Globals.pd.invertedIndex.get(curWord);
//			if (postingList != null) {
//				candidatePatterns.addAll(postingList);
//			}
//		}
//		
//		qlog.error_num = 1;	// 如果能检测到NL pattern匹配，则在函数中会将它再设为1
//		
//		// 检验这些patterns是否真的是T的Bag of Words的子集
//		// 如果是，是否能找到匹配的子树
//		int notMatchedCount = 0;
//		for (String p : candidatePatterns) {
//			String[] BoW_P = p.split(" ");
//			notMatchedCount = 0;	// notMatchedCount记录了当前NL pattern与question不匹配的单词数
//			for (String s : BoW_P) {	//如果该string中（BoW_P中）的每个词都出现在BoW_T中
//				if (s.length() < 2) //0, 1
//					continue;
//				if (s.startsWith("["))
//					continue;
//				if (Globals.stopWordsList.isStopWord(s))
//					continue;
//				if (!BoW_T.contains(s)) {
//					notMatchedCount ++;	// 遇到一个不匹配的word，isSubSet就++
//					if (notMatchedCount > notMatchedCountThreshold)
//						break;
//				}
//			}
//			if (notMatchedCount <= notMatchedCountThreshold) {
//				//System.out.println("[" + (++i) + "]" + p);
//				// 到这里，说明p至多包含了一个不在BoW_T中出现的非停用词
//				// 检验这些pattens是否真的是T的Bag of Words的子集
//				// 如果是，是否能找到匹配的子树
//				
//				subTreeMatching(p, BoW_P, T, qlog, ret, extractingMethod);
//			}
//		}
//		return ret;
//	}
//	
//	private void subTreeMatching (String pattern, String[] BoW_P, 
//			DependencyTree T, QueryLogger qlog, 
//			ArrayList<SimpleRelation> ret, char extractingMethod) {
//		
//		ParaphraseDictionary pd = Globals.pd;
//		Queue<DependencyTreeNode> queue = new LinkedList<DependencyTreeNode>();
//		queue.add(T.getRoot());
//		DependencyTreeNode curOuterNode = null;
//				
//		// 匹配第一个结点后不能跳出，因为后面还有可能有机会！
//		while ((curOuterNode=queue.poll())!=null) {
//			queue.addAll(curOuterNode.childrenList);
//			if (curOuterNode.word.isIgnored) continue;
//			outer:
//			for (String s : BoW_P)
//				if (s.equals(curOuterNode.word.baseForm)) {
//					if(qlog.MODE_debug) System.out.println("First map at <" + curOuterNode + "> and <" + s + ">");
//					
//					// 开始尝试匹配所有点
//					ArrayList<DependencyTreeNode> subTreeNodes = new ArrayList<DependencyTreeNode>();
//					Queue<DependencyTreeNode> queue2 = new LinkedList<DependencyTreeNode>();
//					queue2.add(curOuterNode);
//					int unMappedLeft = BoW_P.length;	//尚未匹配的单词数
//					int mappedCharacterCount = 0;	// 匹配的"字符数"
//					double mappedCharacterCountPunishment = 0;	// 我们不希望[[]]这种排在前面，给点惩罚
//					DependencyTreeNode curNode;
//					boolean[] matchedFlag = new boolean[BoW_P.length];
//					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}			
//
//					while (unMappedLeft > 0 && (curNode=queue2.poll())!=null) {
//						if (curNode.word.isIgnored) continue;
//						int idx = 0;
//						for (String ss : BoW_P) {
//							if (!matchedFlag[idx]) {// pattern中的词只能match一次
//								if (ss.equals(curNode.word.baseForm)) {	// 词匹配 
//									unMappedLeft --;
//									subTreeNodes.add(curNode);
//									queue2.addAll(curNode.childrenList);
//									matchedFlag[idx] = true;
//									mappedCharacterCount += ss.length();
//									break;
//								}
//								else if (ss.startsWith("[") && posSame(curNode.word.posTag, ss)) {	// 词性匹配
//									unMappedLeft --;
//									subTreeNodes.add(curNode);
//									queue2.addAll(curNode.childrenList);
//									matchedFlag[idx] = true;
//									mappedCharacterCount += curNode.word.baseForm.length();//稍微和上面不同
//									mappedCharacterCountPunishment += 0.01;
//									break;
//								}
//							}
//							idx ++;
//						}
//					}
//					int unMatchedNoneStopWordCount = 0;	// Pattern中不匹配的非停用词个数
//					int matchedNoneStopWordCount = 0;
//					for (int idx = 0; idx < BoW_P.length; idx ++) {
//						if (BoW_P[idx].startsWith("[")) continue;
//						if (!matchedFlag[idx]) {
//							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// 不匹配的非停用词
//								unMatchedNoneStopWordCount ++;
//						}
//						else {
//							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// 匹配的非停用词
//								matchedNoneStopWordCount ++;
//						}							
//					}
//
//					if (unMatchedNoneStopWordCount > notMatchedCountThreshold) {
//						if(qlog.MODE_debug) System.out.println("----But the pattern\"" + pattern + "\" is not a subtree.");
//						break outer;
//					}
//
//					//ArrayList<IndexedWord> ExtendedSubTreeNodes = new ArrayList<IndexedWord>();
//					//queue.clear();
//					//for
//					
//					// 匹配的部分必须有实词，不能全是停用词
//					// 匹配的非停用词个数大于0
//					if (matchedNoneStopWordCount == 0){
//						if(qlog.MODE_debug) System.out.println("----But the matching for pattern \"" + pattern + "\" does not have content words.");
//						break outer;
//					}
//					
//					// 不匹配的部分,不能包含实词(非停用词)
//					/*for (int idx = 0; idx < BoW_P.length; idx ++) {
//						if (!matchedFlag[idx]
//						    && !BoW_P[idx].startsWith("[")
//						    && !StopWordsList.isStopWord(BoW_P[idx])) {
//							if(qlog.MODE_debug) System.out.println("----But the content word \""+BoW_P[idx]+ "\" does not match in pattern \"" + pattern + "\"");
//							break outer;							
//						}
//					}*/// 为了回答"When is China founded?"（映射到foundingDate）而注释掉了
//					
//					// 如果是“不完全匹配”，若匹配的部分恰好是另一个完整pattern，则当前pattern就不考虑了
//					if (unMappedLeft > 0) {
//						StringBuilder subpattern = new StringBuilder();
//						for (int idx = 0; idx < BoW_P.length; idx ++) {
//							if (matchedFlag[idx]) {
//								subpattern.append(BoW_P[idx]);
//								subpattern.append(' ');
//							}
//						}
//						subpattern.deleteCharAt(subpattern.length()-1);
//						if (pd.nlPattern_2_predicateList.containsKey(subpattern)) {
//							if(qlog.MODE_debug) System.out.println("----But the partially matched pattern \"" + pattern + "\" is another pattern.");
//							break outer;
//						}
//					}
//					
//					
//					// 介词扩展
//					// 假设每个短语中只有1个介词
//					// TODO 不一定只有一个介词，或者第一个介词可能是错误的
//					DependencyTreeNode prep = null;
//					for (DependencyTreeNode dtn : subTreeNodes) {
//						outer2:
//						for (DependencyTreeNode dtn_child : dtn.childrenList) {							
//							if(pd.prepositions.contains(dtn_child.word.baseForm)) {
//								prep = dtn_child;
//								break outer2;
//							}
//						}
//					}
//					boolean isContained = false;
//					for(DependencyTreeNode dtn_contain : subTreeNodes) {
//						if(dtn_contain == prep) isContained = true;
//					}
//					if(!isContained && prep != null) {
//						subTreeNodes.add(prep);
//					}
//									
//										
//					// 找到匹配的子树后，再找找有没有可用的subject/object
//					
//					// 先找subjects
//					ArrayList<DependencyTreeNode> subjects = new ArrayList<DependencyTreeNode>();
//					for (DependencyTreeNode dtn : subTreeNodes) {
//						for (DependencyTreeNode dtn_child : dtn.childrenList) {
//							if (dtn_child.word.isIgnored) continue;
//							if (pd.relns_subject.contains(dtn_child.dep_father2child)) {
//								subjects.add(dtn_child);
//							}
//						}
//					}
//					if (subjects.size() == 0) {
//						for (DependencyTreeNode dtn : subTreeNodes) {
//							for (DependencyTreeNode dtn_child : dtn.childrenList) {
//								if (dtn_child.word.isIgnored) continue;
//								if (dtn_child.word.posTag.startsWith("W")
//								||	dtn_child.dep_father2child.equals("dep")) {
//									subjects.add(dtn_child);
//								}
//							}
//						}						
//					}
//					
//					
//					// subtree的根节点看看它和它的父亲的关系
//					if (subjects.size() == 0 
//						&& subTreeNodes.get(0).father != null 
//						&& !subTreeNodes.get(0).father.word.isIgnored &&
//							(  pd.relns_subject.contains(subTreeNodes.get(0).dep_father2child)
//							|| subTreeNodes.get(0).word.posTag.startsWith("W")
//							|| subTreeNodes.get(0).dep_father2child.equals("dep")
//							|| pd.relns_object.contains(subTreeNodes.get(0).dep_father2child) //Tell me the athletes that married the daughter of a politician. 此时宾语成分可能充当subject
//							) 
//						) {
//							subjects.add(subTreeNodes.get(0));
//					}
//					// subtree的根节点看看它的父亲有没有subj儿子
//					if (subjects.size() == 0 && subTreeNodes.get(0).father != null && !subTreeNodes.get(0).father.word.isIgnored) {
//						for (DependencyTreeNode dtn : subTreeNodes.get(0).father.childrenList) {
//							if (dtn.word.isIgnored) continue;
//							if (pd.relns_subject.contains(dtn.dep_father2child)) {
//								subjects.add(dtn);
//							}
//						}
//					}	
//					
//					//看看 subtree的根节点的父亲能不能充当subject by hanshuo
//					if (subjects.size() == 0 
//						&& subTreeNodes.get(0).father != null 
//						&& !subTreeNodes.get(0).father.word.isIgnored 
//						&& subTreeNodes.get(0).dep_father2child.endsWith("mod")
//						&& subTreeNodes.get(0).father.word.posTag.startsWith("N")) //give me all actors starring in XXX...
//					{						
//						subjects.add(subTreeNodes.get(0).father);
//					}
//						
//					
//					// 再找objects
//					ArrayList<DependencyTreeNode> objects = new ArrayList<DependencyTreeNode>();
//					for (DependencyTreeNode dtn : subTreeNodes) {
//						for (DependencyTreeNode dtn_child : dtn.childrenList) {
//							if (dtn_child.word.isIgnored) continue;
//							if (pd.relns_object.contains(dtn_child.dep_father2child)
//								|| dtn_child.word.posTag.startsWith("W")
//								|| dtn_child.dep_father2child.equals("dep")) {
//								objects.add(dtn_child);
//							}
//						}
//					}
//					
//					//看看subtree的根节点的父亲能不能充当object by hanshuo
//					if (objects.size() == 0 
//						&& subTreeNodes.get(0).father != null 
//						&& !subTreeNodes.get(0).father.word.isIgnored 
//						&& pd.relns_object.contains(subTreeNodes.get(0).father.dep_father2child))
//						{						
//							objects.add(subTreeNodes.get(0).father);
//						}					
//					//System.out.println("pattern:"+pattern+"  sub:"+subjects+"  obj:"+objects);
//					// TODO
//					// 一般来说，至少都会找到一个subject或object，如果哪一个为空，那么就拿第一个名词/代词充场面
//					if (subjects.size() == 0 && objects.size() == 0) {						
//						if(qlog.MODE_debug) System.out.println("----But the pattern\"" + pattern + "\" cannot find a subject/object: sub_cnt=" + subjects.size() + ", obj_cnt=" + objects.size());
//						break outer;
//					}
//					ArrayList<DependencyTreeNode> subject_or_object = null; 
//					if (subjects.size() == 0) subject_or_object = subjects;
//					if (objects.size() == 0) subject_or_object = objects;
//					
//					if (subject_or_object != null) {
//						for (DependencyTreeNode dtn : subTreeNodes) {
//							if (dtn.word.isIgnored) continue;
//							if (dtn.word.posTag.startsWith("NN") 
//								|| dtn.word.posTag.startsWith("PR")
//								|| dtn.word.posTag.startsWith("W")) {
//								subject_or_object.add(dtn);
//								break;
//							}
//						}
//					}
//					
//					
//					if (subjects.size() == 0 || objects.size() == 0) {
//						qlog.error_num = 2;
//						break outer;
//					}
//					
//					// 如果phrase+subj+obj加起来只有两个树结点，我们会觉得这样似乎不太可能，因此拒绝掉这种情形
//					HashSet<DependencyTreeNode> checkSet = new HashSet<DependencyTreeNode>();
//					checkSet.addAll(subTreeNodes);
//					checkSet.addAll(subjects);
//					checkSet.addAll(objects);
//					if (checkSet.size() <= 1 || (checkSet.size()==2 && subjects.get(0).containPosInChildren("POS", true)==null)) {// 排除掉Jiabao's son用be son来匹配时的情况
//						if(qlog.MODE_debug) System.out.println("----But the subtree for pattern \"" + pattern + "\" has less than 2 nodes.");
//						qlog.error_num = 1;
//						break outer;
//					}
//
//					
//					// 此时，关系抽取成功
//					// 标记子树上的结点：已覆盖
//					for (DependencyTreeNode dtn : subTreeNodes) {
//						dtn.word.isCovered = true;
//					}
//					
//					int cnt = 0;
//					double matched_score = ((double)(BoW_P.length-unMappedLeft))/((double)(BoW_P.length));	//匹配的比例越多，得分越高
//					if (matched_score > 0.95) matched_score *= 10;// 奖励完全匹配
//					matched_score = matched_score * Math.sqrt(mappedCharacterCount) - mappedCharacterCountPunishment;	// 匹配的"字符数"越多（偶然性越小），显然得分越高	//开方平滑一下
//					if (qlog.MODE_debug) System.out.println("☆" + pattern + ", score=" + matched_score);
//					for (DependencyTreeNode subject : subjects) {
//						for (DependencyTreeNode object : objects) {
//							if (subject != object) {
//								qlog.error_num = 0;
//								
//								SimpleRelation sr = new SimpleRelation();
//								sr.arg1Word = subject.word.getNnHead();
//								sr.arg2Word = object.word.getNnHead();
//								sr.relationParaphrase = pattern;
//								sr.matchingScore = matched_score;
//								sr.extractingMethod = extractingMethod;
//								
//								if (subject.dep_father2child.endsWith("subj"))
//									sr.preferredSubj = sr.arg1Word;
//								
//								sr.arg1Word.setIsCovered();
//								sr.arg2Word.setIsCovered();
//								
//								sr.setPasList(pattern, matched_score, matchedFlag);
//								sr.setPreferedSubjObjOrder(T);
//								
//								ret.add(sr);
//								cnt ++;
//								//String binaryRelation = "<" + subjectString + "> <" + pattern + "> <" + objectString + ">";
//							}
//						}
//					}
//					if (cnt == 0) break outer;
//				}
//		}		
//	}
//
//	// [[det]], [[num]], [[adj]], [[pro]], [[prp]], [[con]], [[mod]]
//	public boolean posSame(String tag, String posWithBracket) {
//		if (	(posWithBracket.charAt(2) == 'd' && tag.equals("DT"))
//			||	(posWithBracket.charAt(2) == 'n' && tag.equals("CD"))
//			||	(posWithBracket.charAt(2) == 'a' && (tag.startsWith("JJ") || tag.startsWith("RB")))
//			||	(posWithBracket.charAt(2) == 'c' && tag.startsWith("CC"))//TODO: how about "IN: subordinating conjunction"?
//			||	(posWithBracket.charAt(2) == 'm' && tag.equals("MD"))) {
//			return true;
//		}
//		else if (posWithBracket.charAt(2) == 'p') {
//			if (	(posWithBracket.charAt(4) == 'o' && tag.startsWith("PR"))
//				||	(posWithBracket.charAt(4) == 'p' && (tag.equals("IN") || tag.equals("TO")))) {
//				return true;
//			}
//		}
//		return false;
//	}
//
//	// TODO 现在的覆盖率计算是不准确的，尽管simple relation覆盖了句子，但是它未必加到最后的Semantic Relation中
//	public boolean isWellCovered (Sentence s) {
//		int notCoveredCount = 0;
//		System.out.println("=====Text Coverage Calculation=======");
//		for (Word w : s.words) {
//			if (w.isCovered || w.isIgnored || w.posTag.equals(".")) {
//				System.out.print("["+w.originalForm+"] ");
//			}
//			else {
//				System.out.print(w.originalForm + " ");
//				if (!Globals.stopWordsList.isStopWord(w.baseForm)) {
//					notCoveredCount ++;
//				}
//			}
//		}
//		System.out.println(" <not covered:"+notCoveredCount + "/" +s.words.length+">");
//		System.out.println("=====================================");
//		if (notCoveredCount > notCoverageCountThreshold) return false;
//		else return true;
//	}
//	
//	public HashMap<Integer, SemanticRelation> groupSimpleRelationsByArgsAndMapPredicate (ArrayList<SimpleRelation> simpleRelations) {
//		System.out.println("==========Group Simple Relations=========");
//		
//		HashMap<Integer, SemanticRelation> ret = new HashMap<Integer, SemanticRelation>();
//		HashMap<Integer, HashMap<Integer, StringAndDouble>>  key2pasMap = new HashMap<Integer, HashMap<Integer, StringAndDouble>>();
//		for(SimpleRelation simr : simpleRelations) {
//			int key = simr.getHashCode();
//			if (!ret.keySet().contains(key)) {
//				ret.put(key, new SemanticRelation(simr));
//				key2pasMap.put(key, new HashMap<Integer, StringAndDouble>());
//			}
//			SemanticRelation semr = ret.get(key);
//			HashMap<Integer, StringAndDouble> pasMap = key2pasMap.get(key);
//						
//			if (simr.matchingScore > semr.LongestMatchingScore) {
//				semr.LongestMatchingScore = simr.matchingScore;
//				semr.relationParaphrase = simr.relationParaphrase;
//			}
//			
//			for (int pid : simr.pasList.keySet()) {
//				double score = simr.pasList.get(pid);
//				if (!pasMap.containsKey(pid)) {
//					pasMap.put(pid, new StringAndDouble(simr.relationParaphrase, score));
//				}
//				else if (score > pasMap.get(pid).score) {
//					pasMap.put(pid, new StringAndDouble(simr.relationParaphrase, score));
//				}
//			}
//		}
//		
//		for (Integer key : key2pasMap.keySet()) {
//			SemanticRelation semr = ret.get(key);
//			HashMap<Integer, StringAndDouble> pasMap = key2pasMap.get(key);
//			semr.predicateMappings = new ArrayList<PredicateMapping>();
//			//System.out.print("<"+semr.arg1Word.getFullEntityName() + "," + semr.arg2Word.getFullEntityName() + ">:");
//			for (Integer pid : pasMap.keySet()) {
//				semr.predicateMappings.add(new PredicateMapping(pid, pasMap.get(pid).score, pasMap.get(pid).str));
//				//System.out.print("[" + Globals.pd.getPredicateById(pid) + "," + pasMap.get(pid).str + "," + pasMap.get(pid).score + "]");
//			}
//			Collections.sort(semr.predicateMappings);
//		}
//		System.out.println("=========================================");
//		return ret;
//	}
//
//	public void constantVariableRecognition(HashMap<Integer, SemanticRelation> semanticRelations) 
//	{
//		for (Integer it : semanticRelations.keySet()) 
//		{
//			SemanticRelation sr = semanticRelations.get(it);
//			// 如果包含首字母大写(非句首单词)，或是命名实体，则视作常量
//			if (sr.arg1Word.isNER() != null && !sr.arg1Word.isNER().equals("PERSON")
//				|| containsUpperCharacter(sr.arg1Word.getFullEntityName(), sr.arg1Word.getNnHead().position)) {
//				sr.isArg1Constant = true;
//			}
//			if (sr.arg2Word.isNER() != null && !sr.arg2Word.isNER().equals("PERSON")
//				|| containsUpperCharacter(sr.arg2Word.getFullEntityName(), sr.arg2Word.getNnHead().position)) {
//				sr.isArg2Constant = true;
//			}
//		}
//	}
//	
//	private boolean containsUpperCharacter (String str, int beginIdx) {
//		String[] array = str.split(" ");
//		for (String s: array) {
//			if (Character.isUpperCase(s.charAt(0)) && beginIdx!=1) {
//				return true;
//			}
//			beginIdx++;
//		}
//		return false;
//	}
//	
//	public ArrayList<HashMap<Integer, SemanticRelation>> dePhraseOverlap(
//			HashMap<Integer, SemanticRelation> semanticRelations,
//			QueryLogger qlog) {
//	
//	// 初始化confict矩阵
//	int size = semanticRelations.size();
//	boolean[][] confict = new boolean[size][];
//	for(int i = 0; i < size; i ++) {
//		confict[i] = new boolean[size];
//	}
//	
//	// 为每个ArgumentPair分配一个id
//	HashMap<Integer, Integer> idmap = new HashMap<Integer, Integer>();
//	for (Integer key : semanticRelations.keySet()) {
//		idmap.put(idmap.size(), key);
//	}
//		
//	// 计算在依存树上每对entities之间的最短路径
//	HashMap<Integer, ArrayList<DependencyTreeNode>> shortestPaths = new HashMap<Integer, ArrayList<DependencyTreeNode>>();
//	DependencyTree tree = null;
//	for(Integer key : semanticRelations.keySet()) {
//		SemanticRelation sr = semanticRelations.get(key); 
//		Word apw1 = sr.arg1Word_beforeCRR == null ? sr.arg1Word : sr.arg1Word_beforeCRR;
//		Word apw2 = sr.arg2Word_beforeCRR == null ? sr.arg2Word : sr.arg2Word_beforeCRR;
//		if (sr.extractingMethod == 'S') 
//			tree = qlog.s.dependencyTreeStanford;
//		else if (sr.extractingMethod == 'M') {
//			tree = qlog.s.dependencyTreeMalt;
//		}
//		else {
//			// TODO
//			continue;
//		}
//		shortestPaths.put(key, 
//				tree.getShortestNodePathBetween(
//						tree.getNodeByIndex(apw1.position), 
//						tree.getNodeByIndex(apw2.position)));
//	}
//	
//	// 根据最短路径，判断ArgumentPair之间的confict情况
//	boolean confictExist = false;
//	for (int i = 0; i < size; i ++) {
//		confict[i][i] = true;
//		SemanticRelation ap1 = semanticRelations.get(idmap.get(i));
//		ArrayList<DependencyTreeNode> sp1 = shortestPaths.get(idmap.get(i));
//		for (int j = i+1; j < size; j ++) {
//			SemanticRelation ap2 = semanticRelations.get(idmap.get(j));
//			ArrayList<DependencyTreeNode> sp2 = shortestPaths.get(idmap.get(j));
//			int smaller = sp1.size() < sp2.size() ? sp1.size() : sp2.size();
//			int bigger = sp1.size() > sp2.size() ? sp1.size() : sp2.size();
//			int overlap = sizeOfIntersection(sp1, sp2);
//			if(	(smaller <= 2 && smaller - overlap == 0 )
//				|| (smaller == 3 && bigger <= 4 && smaller - overlap <= 1)
//				|| (smaller ==4 && bigger == 4 && smaller - overlap == 0)
//				|| (smaller >= 4 && bigger >4 && smaller - overlap <= 1)) {
//				confict[i][j] = true;
//				confictExist = true;
//				System.out.println("Confict: " + ap1 + " & " + ap2);
//			}
//			else {
//				confict[i][j] = false;
//			}
//		}
//	}
//	
//	// 如果不存在confict，直接返回
//	if (!confictExist) {
//		ArrayList<HashMap<Integer, SemanticRelation>>
//			ret = new ArrayList<HashMap<Integer, SemanticRelation>>();
//		ret.add(semanticRelations);
//		if(qlog.MODE_debug) System.out.println("No conflict!");
//		return ret;
//	}
//	
//	// 存在confict，则需获得所有极大相容子集
//	HashSet<Integer> curSet = new HashSet<Integer>();
//	ArrayList<HashSet<Integer>> maximalList = new ArrayList<HashSet<Integer>>();
//	dfsMaximalNonconflictSubset(0, size, confict, curSet, maximalList);//深搜	
//	ArrayList<HashMap<Integer, SemanticRelation>>
//		ret = new ArrayList<HashMap<Integer, SemanticRelation>>();
//	System.out.println("============DePhraseOverlap============");
//	int cnt = 1;
//	for (HashSet<Integer> hsi : maximalList) {
//		System.out.println("Triples combination " + (cnt++) + ".");
//		HashMap<Integer, SemanticRelation> hm = new HashMap<Integer, SemanticRelation>();
//		for (Integer i : hsi) {
//			hm.put(idmap.get(i), semanticRelations.get(idmap.get(i)));
//			System.out.println(idmap.get(i));
//		}
//		ret.add(hm);
//	}
//
//	System.out.println("====================================");
//	return ret;
//}
//	
//	private void dfsMaximalNonconflictSubset (int level, int size,
//			boolean[][] confict,
//			HashSet<Integer> curSet,
//			ArrayList<HashSet<Integer>> maximalList) {
//		// 递归出口: 判断curList是否是极大的子集
//		if (level == size) {
//			for (HashSet<Integer> si : maximalList) {
//				if (si.containsAll(curSet)) {
//					return;
//				}
//				else if (curSet.containsAll(si)){
//					maximalList.remove(si);
//					HashSet<Integer> newSet = new HashSet<Integer>(curSet);//复制一个新的
//					maximalList.add(newSet);
//					return;
//				}
//			}
//			HashSet<Integer> newSet = new HashSet<Integer>(curSet);//复制一个新的
//			maximalList.add(newSet);
//			return;
//		}
//		
//		// 判断第level个元素是否和curList相容
//		boolean curConflict = false;
//		for (Integer i : curSet) {
//			if (confict[i][level]) {
//				curConflict = true;
//				break;
//			}
//		}
//
//		// 视情况将第level个元素加入curList中
//		if (!curConflict) {
//			curSet.add(level);
//			dfsMaximalNonconflictSubset(level+1, size, confict, curSet, maximalList);
//			curSet.remove(level);
//		}
//		dfsMaximalNonconflictSubset(level+1, size, confict, curSet, maximalList);
//	}
//	
//	public int sizeOfIntersection(ArrayList<DependencyTreeNode> l1, ArrayList<DependencyTreeNode> l2) {
//		int cnt = 0;
//		for(DependencyTreeNode o1 : l1) {
//			for(DependencyTreeNode o2 : l2) {
//				if(o1 == o2) {
//					cnt ++;
//				}
//			}
//		}
//		return cnt;
//	}
//	
//};
//
//class StringAndDouble {
//	public String str;
//	public double score;
//	public StringAndDouble (String str, double score) {
//		this.str = str;
//		this.score = score;
//	}
//}