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
//	public static final int notMatchedCountThreshold = 1;// ��thresholdԽ��ƥ��ĳ̶�ԽС��Խ���ɣ�
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
//		// ��δ de-overlap ��ԭʼ semantic relations ����qlog husen
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
//				System.out.println("��"+semRltns.get(key));
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
//		// ��T�С���ͣ�ôʡ���Ϊ����SubBag of Words
//		for (DependencyTreeNode curNode : T.getNodesList()) {
//			if (!curNode.word.isIgnored) {
//				String text = curNode.word.baseForm;
//				BoW_T.add(text);
//				if (!Globals.stopWordsList.isStopWord(text))
//					SubBoW_T.add(text);				
//			}
//		}
//
//		// �ҵ�SubBoW_T�еĴʶ�Ӧ��patterns����Щpattern���ٰ�����question�е�һ��word
//		HashSet<String> candidatePatterns = new HashSet<String>();
//		for (String curWord : SubBoW_T) {
//			ArrayList<String> postingList = Globals.pd.invertedIndex.get(curWord);
//			if (postingList != null) {
//				candidatePatterns.addAll(postingList);
//			}
//		}
//		
//		qlog.error_num = 1;	// ����ܼ�⵽NL patternƥ�䣬���ں����лὫ������Ϊ1
//		
//		// ������Щpatterns�Ƿ������T��Bag of Words���Ӽ�
//		// ����ǣ��Ƿ����ҵ�ƥ�������
//		int notMatchedCount = 0;
//		for (String p : candidatePatterns) {
//			String[] BoW_P = p.split(" ");
//			notMatchedCount = 0;	// notMatchedCount��¼�˵�ǰNL pattern��question��ƥ��ĵ�����
//			for (String s : BoW_P) {	//�����string�У�BoW_P�У���ÿ���ʶ�������BoW_T��
//				if (s.length() < 2) //0, 1
//					continue;
//				if (s.startsWith("["))
//					continue;
//				if (Globals.stopWordsList.isStopWord(s))
//					continue;
//				if (!BoW_T.contains(s)) {
//					notMatchedCount ++;	// ����һ����ƥ���word��isSubSet��++
//					if (notMatchedCount > notMatchedCountThreshold)
//						break;
//				}
//			}
//			if (notMatchedCount <= notMatchedCountThreshold) {
//				//System.out.println("[" + (++i) + "]" + p);
//				// �����˵��p���������һ������BoW_T�г��ֵķ�ͣ�ô�
//				// ������Щpattens�Ƿ������T��Bag of Words���Ӽ�
//				// ����ǣ��Ƿ����ҵ�ƥ�������
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
//		// ƥ���һ����������������Ϊ���滹�п����л��ᣡ
//		while ((curOuterNode=queue.poll())!=null) {
//			queue.addAll(curOuterNode.childrenList);
//			if (curOuterNode.word.isIgnored) continue;
//			outer:
//			for (String s : BoW_P)
//				if (s.equals(curOuterNode.word.baseForm)) {
//					if(qlog.MODE_debug) System.out.println("First map at <" + curOuterNode + "> and <" + s + ">");
//					
//					// ��ʼ����ƥ�����е�
//					ArrayList<DependencyTreeNode> subTreeNodes = new ArrayList<DependencyTreeNode>();
//					Queue<DependencyTreeNode> queue2 = new LinkedList<DependencyTreeNode>();
//					queue2.add(curOuterNode);
//					int unMappedLeft = BoW_P.length;	//��δƥ��ĵ�����
//					int mappedCharacterCount = 0;	// ƥ���"�ַ���"
//					double mappedCharacterCountPunishment = 0;	// ���ǲ�ϣ��[[]]��������ǰ�棬����ͷ�
//					DependencyTreeNode curNode;
//					boolean[] matchedFlag = new boolean[BoW_P.length];
//					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}			
//
//					while (unMappedLeft > 0 && (curNode=queue2.poll())!=null) {
//						if (curNode.word.isIgnored) continue;
//						int idx = 0;
//						for (String ss : BoW_P) {
//							if (!matchedFlag[idx]) {// pattern�еĴ�ֻ��matchһ��
//								if (ss.equals(curNode.word.baseForm)) {	// ��ƥ�� 
//									unMappedLeft --;
//									subTreeNodes.add(curNode);
//									queue2.addAll(curNode.childrenList);
//									matchedFlag[idx] = true;
//									mappedCharacterCount += ss.length();
//									break;
//								}
//								else if (ss.startsWith("[") && posSame(curNode.word.posTag, ss)) {	// ����ƥ��
//									unMappedLeft --;
//									subTreeNodes.add(curNode);
//									queue2.addAll(curNode.childrenList);
//									matchedFlag[idx] = true;
//									mappedCharacterCount += curNode.word.baseForm.length();//��΢�����治ͬ
//									mappedCharacterCountPunishment += 0.01;
//									break;
//								}
//							}
//							idx ++;
//						}
//					}
//					int unMatchedNoneStopWordCount = 0;	// Pattern�в�ƥ��ķ�ͣ�ôʸ���
//					int matchedNoneStopWordCount = 0;
//					for (int idx = 0; idx < BoW_P.length; idx ++) {
//						if (BoW_P[idx].startsWith("[")) continue;
//						if (!matchedFlag[idx]) {
//							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// ��ƥ��ķ�ͣ�ô�
//								unMatchedNoneStopWordCount ++;
//						}
//						else {
//							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// ƥ��ķ�ͣ�ô�
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
//					// ƥ��Ĳ��ֱ�����ʵ�ʣ�����ȫ��ͣ�ô�
//					// ƥ��ķ�ͣ�ôʸ�������0
//					if (matchedNoneStopWordCount == 0){
//						if(qlog.MODE_debug) System.out.println("----But the matching for pattern \"" + pattern + "\" does not have content words.");
//						break outer;
//					}
//					
//					// ��ƥ��Ĳ���,���ܰ���ʵ��(��ͣ�ô�)
//					/*for (int idx = 0; idx < BoW_P.length; idx ++) {
//						if (!matchedFlag[idx]
//						    && !BoW_P[idx].startsWith("[")
//						    && !StopWordsList.isStopWord(BoW_P[idx])) {
//							if(qlog.MODE_debug) System.out.println("----But the content word \""+BoW_P[idx]+ "\" does not match in pattern \"" + pattern + "\"");
//							break outer;							
//						}
//					}*/// Ϊ�˻ش�"When is China founded?"��ӳ�䵽foundingDate����ע�͵���
//					
//					// ����ǡ�����ȫƥ�䡱����ƥ��Ĳ���ǡ������һ������pattern����ǰpattern�Ͳ�������
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
//					// �����չ
//					// ����ÿ��������ֻ��1�����
//					// TODO ��һ��ֻ��һ����ʣ����ߵ�һ����ʿ����Ǵ����
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
//					// �ҵ�ƥ�����������������û�п��õ�subject/object
//					
//					// ����subjects
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
//					// subtree�ĸ��ڵ㿴���������ĸ��׵Ĺ�ϵ
//					if (subjects.size() == 0 
//						&& subTreeNodes.get(0).father != null 
//						&& !subTreeNodes.get(0).father.word.isIgnored &&
//							(  pd.relns_subject.contains(subTreeNodes.get(0).dep_father2child)
//							|| subTreeNodes.get(0).word.posTag.startsWith("W")
//							|| subTreeNodes.get(0).dep_father2child.equals("dep")
//							|| pd.relns_object.contains(subTreeNodes.get(0).dep_father2child) //Tell me the athletes that married the daughter of a politician. ��ʱ����ɷֿ��ܳ䵱subject
//							) 
//						) {
//							subjects.add(subTreeNodes.get(0));
//					}
//					// subtree�ĸ��ڵ㿴�����ĸ�����û��subj����
//					if (subjects.size() == 0 && subTreeNodes.get(0).father != null && !subTreeNodes.get(0).father.word.isIgnored) {
//						for (DependencyTreeNode dtn : subTreeNodes.get(0).father.childrenList) {
//							if (dtn.word.isIgnored) continue;
//							if (pd.relns_subject.contains(dtn.dep_father2child)) {
//								subjects.add(dtn);
//							}
//						}
//					}	
//					
//					//���� subtree�ĸ��ڵ�ĸ����ܲ��ܳ䵱subject by hanshuo
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
//					// ����objects
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
//					//����subtree�ĸ��ڵ�ĸ����ܲ��ܳ䵱object by hanshuo
//					if (objects.size() == 0 
//						&& subTreeNodes.get(0).father != null 
//						&& !subTreeNodes.get(0).father.word.isIgnored 
//						&& pd.relns_object.contains(subTreeNodes.get(0).father.dep_father2child))
//						{						
//							objects.add(subTreeNodes.get(0).father);
//						}					
//					//System.out.println("pattern:"+pattern+"  sub:"+subjects+"  obj:"+objects);
//					// TODO
//					// һ����˵�����ٶ����ҵ�һ��subject��object�������һ��Ϊ�գ���ô���õ�һ������/���ʳ䳡��
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
//					// ���phrase+subj+obj������ֻ����������㣬���ǻ���������ƺ���̫���ܣ���˾ܾ�����������
//					HashSet<DependencyTreeNode> checkSet = new HashSet<DependencyTreeNode>();
//					checkSet.addAll(subTreeNodes);
//					checkSet.addAll(subjects);
//					checkSet.addAll(objects);
//					if (checkSet.size() <= 1 || (checkSet.size()==2 && subjects.get(0).containPosInChildren("POS", true)==null)) {// �ų���Jiabao's son��be son��ƥ��ʱ�����
//						if(qlog.MODE_debug) System.out.println("----But the subtree for pattern \"" + pattern + "\" has less than 2 nodes.");
//						qlog.error_num = 1;
//						break outer;
//					}
//
//					
//					// ��ʱ����ϵ��ȡ�ɹ�
//					// ��������ϵĽ�㣺�Ѹ���
//					for (DependencyTreeNode dtn : subTreeNodes) {
//						dtn.word.isCovered = true;
//					}
//					
//					int cnt = 0;
//					double matched_score = ((double)(BoW_P.length-unMappedLeft))/((double)(BoW_P.length));	//ƥ��ı���Խ�࣬�÷�Խ��
//					if (matched_score > 0.95) matched_score *= 10;// ������ȫƥ��
//					matched_score = matched_score * Math.sqrt(mappedCharacterCount) - mappedCharacterCountPunishment;	// ƥ���"�ַ���"Խ�ࣨżȻ��ԽС������Ȼ�÷�Խ��	//����ƽ��һ��
//					if (qlog.MODE_debug) System.out.println("��" + pattern + ", score=" + matched_score);
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
//	// TODO ���ڵĸ����ʼ����ǲ�׼ȷ�ģ�����simple relation�����˾��ӣ�������δ�ؼӵ�����Semantic Relation��
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
//			// �����������ĸ��д(�Ǿ��׵���)����������ʵ�壬����������
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
//	// ��ʼ��confict����
//	int size = semanticRelations.size();
//	boolean[][] confict = new boolean[size][];
//	for(int i = 0; i < size; i ++) {
//		confict[i] = new boolean[size];
//	}
//	
//	// Ϊÿ��ArgumentPair����һ��id
//	HashMap<Integer, Integer> idmap = new HashMap<Integer, Integer>();
//	for (Integer key : semanticRelations.keySet()) {
//		idmap.put(idmap.size(), key);
//	}
//		
//	// ��������������ÿ��entities֮������·��
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
//	// �������·�����ж�ArgumentPair֮���confict���
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
//	// ���������confict��ֱ�ӷ���
//	if (!confictExist) {
//		ArrayList<HashMap<Integer, SemanticRelation>>
//			ret = new ArrayList<HashMap<Integer, SemanticRelation>>();
//		ret.add(semanticRelations);
//		if(qlog.MODE_debug) System.out.println("No conflict!");
//		return ret;
//	}
//	
//	// ����confict�����������м��������Ӽ�
//	HashSet<Integer> curSet = new HashSet<Integer>();
//	ArrayList<HashSet<Integer>> maximalList = new ArrayList<HashSet<Integer>>();
//	dfsMaximalNonconflictSubset(0, size, confict, curSet, maximalList);//����	
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
//		// �ݹ����: �ж�curList�Ƿ��Ǽ�����Ӽ�
//		if (level == size) {
//			for (HashSet<Integer> si : maximalList) {
//				if (si.containsAll(curSet)) {
//					return;
//				}
//				else if (curSet.containsAll(si)){
//					maximalList.remove(si);
//					HashSet<Integer> newSet = new HashSet<Integer>(curSet);//����һ���µ�
//					maximalList.add(newSet);
//					return;
//				}
//			}
//			HashSet<Integer> newSet = new HashSet<Integer>(curSet);//����һ���µ�
//			maximalList.add(newSet);
//			return;
//		}
//		
//		// �жϵ�level��Ԫ���Ƿ��curList����
//		boolean curConflict = false;
//		for (Integer i : curSet) {
//			if (confict[i][level]) {
//				curConflict = true;
//				break;
//			}
//		}
//
//		// ���������level��Ԫ�ؼ���curList��
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