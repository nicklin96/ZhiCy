package test;

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
import qa.extract.SimpleRelation;
import rdf.PredicateMapping;
import rdf.SemanticRelation;

public class ExtractRelation {

	public static final int notMatchedCountThreshold = 1;// ��thresholdԽ��ƥ��ĳ̶�ԽС��Խ���ɣ�
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
				
		// ��shortest path�С���ͣ�ôʡ���Ϊ����SubBag of Words
		for(DependencyTreeNode curNode: shortestPath)
		{
			String text = curNode.word.baseForm;
			if(!curNode.word.isIgnored && !Globals.stopWordsList.isStopWord(text))
			{
				//����soccer club����Ԥ�����ϳ�Ϊһ��word��soccer_club����relation����Ҫ�õ�ԭ�ʣ�����Ҫ���п�
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
		// �����ڵ���ϵ�����÷�Χ���ܲ�ֹ���·�������巶Χ�����ң��Ͱ�����ds tree��Ϊ���ܷ�Χ
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
		// �ҵ�SubBoW_T�еĴʶ�Ӧ��patterns����Щpattern���ٰ�����question�е�һ��word
		HashSet<String> candidatePatterns = new HashSet<String>();
		for (String curWord : SubBoW_T) 
		{
			ArrayList<String> postingList = Globals.pd.invertedIndex.get(curWord);
			if (postingList != null) 
			{
				candidatePatterns.addAll(postingList);
			}
		}
		
		// ������Щpatterns�Ƿ������T��Bag of Words���Ӽ�
		// ����ǣ��Ƿ����ҵ�ƥ�������
		int notMatchedCount = 0;
		HashSet<String> validCandidatePatterns = new HashSet<String>();
		for (String p : candidatePatterns) 
		{
			String[] BoW_P = p.split(" ");
			notMatchedCount = 0;	// notMatchedCount��¼�˵�ǰNL pattern��question��ƥ��ĵ�����
			for (String s : BoW_P) {	//�����string�У�BoW_P�У���ÿ���ʶ�������BoW_T��
				if (s.length() < 2) //0, 1
					continue;
				if (s.startsWith("["))
					continue;
				if (Globals.stopWordsList.isStopWord(s))
					continue;
				if (!BoW_T.contains(s)) {
					notMatchedCount ++;	// ����һ����ƥ���word��isSubSet��++
					if (notMatchedCount > notMatchedCountThreshold)
						break;
				}
				
			}
			if (notMatchedCount <= notMatchedCountThreshold) 
			{
				validCandidatePatterns.add(p);
				//System.out.println("[" + (++i) + "]" + p);
				// �����˵��p���������һ������BoW_T�г��ֵķ�ͣ�ô�
				// ������Щpattens�Ƿ������T��Bag of Words���Ӽ�
				// ����ǣ��Ƿ����ҵ�ƥ�������
				
				//TODO:ע������������ǲ��ܴ���  soccer club ��� soccer_club ��Ĺ�ϵƥ��
				subTreeMatching(p, BoW_P, shortestPath, T, qlog, ret, 'S');
			}
		}
		
		//ר��Ϊ soccer club ��Ϊ soccer_club ����ƥ��ʧ�ܵ������һ�λ���
		if(validCandidatePatterns.size() > 0)
		{
			if(n1.word.originalForm.contains("_") || n2.word.originalForm.contains("_"))
			{
				for (String p : validCandidatePatterns) 
				{
					String[] BoW_P = p.split(" ");
					notMatchedCount = 0;	// notMatchedCount��¼�˵�ǰNL pattern��question��ƥ��ĵ�����
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
							notMatchedCount ++;	// ����һ����ƥ���word��isSubSet��++
					}
					//������������arg�ϣ�ֱ����Ϊ�ɹ���Ҫ��һ�µ÷ֹ���sr
					if(matchedWordInArg >= 2)
					{
						double matched_score = ((double)(BoW_P.length-notMatchedCount))/((double)(BoW_P.length));	//ƥ��ı���Խ�࣬�÷�Խ��
						if (matched_score > 0.95) 
							matched_score *= 10;// ������ȫƥ�䣬ʵ��������û�п���[[]]�ȣ������������ȫƥ��
						
						//��������ַ����÷֣�����һЩ��ֵĳ���pattenr���� ��be bear die in���ĵ÷ֽϸ�
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
		// relation ����������
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
					// ��ʼ����ƥ�����е�
					ArrayList<DependencyTreeNode> subTreeNodes = new ArrayList<DependencyTreeNode>();
					Queue<DependencyTreeNode> queue2 = new LinkedList<DependencyTreeNode>();
					queue2.add(curOuterNode);
					
					int unMappedLeft = BoW_P.length;	//��δƥ��ĵ�����
					int mappedCharacterCount = 0;	// ƥ���"�ַ���"
					int hitPathCnt = 0;	//pattern�е�word����shortest path�ϣ���Ȼ���������������
					int hitPathBetweenTwoArgCnt = 0; //pattern�е�word����shortest path�ϣ����Ҳ��������˵���������
					double mappedCharacterCountPunishment = 0;	// ���ǲ�ϣ��[[]]��������ǰ�棬����ͷ�
					
					DependencyTreeNode curNode;
					boolean[] matchedFlag = new boolean[BoW_P.length];
					for(int idx = 0; idx < BoW_P.length; idx ++) {matchedFlag[idx] = false;}			

					while (unMappedLeft > 0 && (curNode=queue2.poll())!=null) 
					{
						if (curNode.word.isIgnored) continue;
						int idx = 0;
						for (String ss : BoW_P) 
						{
							// pattern�еĴ�ֻ��matchһ��
							if (!matchedFlag[idx]) 
							{
								// ��ƥ�� 
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
								// ����ƥ��
								else if (ss.startsWith("[") && posSame(curNode.word.posTag, ss)) 
								{	
									unMappedLeft --;
									subTreeNodes.add(curNode);
									queue2.addAll(curNode.childrenList);
									matchedFlag[idx] = true;
									mappedCharacterCount += curNode.word.baseForm.length();//��΢�����治ͬ
									mappedCharacterCountPunishment += 0.01;
									break;
								}
							}
							idx ++;
						}
					}
					int unMatchedNoneStopWordCount = 0;	// Pattern�в�ƥ��ķ�ͣ�ôʸ���
					int matchedNoneStopWordCount = 0;
					for (int idx = 0; idx < BoW_P.length; idx ++) {
						if (BoW_P[idx].startsWith("[")) continue;
						if (!matchedFlag[idx]) {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// ��ƥ��ķ�ͣ�ô�
								unMatchedNoneStopWordCount ++;
						}
						else {
							if (!Globals.stopWordsList.isStopWord(BoW_P[idx]))	// ƥ��ķ�ͣ�ô�
								matchedNoneStopWordCount ++;
						}							
					}

					if (unMatchedNoneStopWordCount > notMatchedCountThreshold) {
						if(qlog.MODE_debug) System.out.println("----But the pattern\"" + pattern + "\" is not a subtree.");
						break outer;
					}
					
					// ƥ��Ĳ��ֱ�����ʵ�ʣ�����ȫ��ͣ�ô�
					// ƥ��ķ�ͣ�ôʸ�������0
					if (matchedNoneStopWordCount == 0){
						if(qlog.MODE_debug) System.out.println("----But the matching for pattern \"" + pattern + "\" does not have content words.");
						break outer;
					}
					
					// ����ǡ�����ȫƥ�䡱����ƥ��Ĳ���ǡ������һ������pattern����ǰpattern�Ͳ�������
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
					
					// �����չ
					// ����ÿ��������ֻ��1�����
					// TODO ��һ��ֻ��һ����ʣ����ߵ�һ����ʿ����Ǵ����
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

					
					// ��ʱ����ϵ��ȡ�ɹ�
					// ��������ϵĽ�㣺�Ѹ���
					for (DependencyTreeNode dtn : subTreeNodes) 
					{
						dtn.word.isCovered = true;
					}
					
					int cnt = 0;
					double matched_score = ((double)(BoW_P.length-unMappedLeft))/((double)(BoW_P.length));	//ƥ��ı���Խ�࣬�÷�Խ��
					if (matched_score > 0.95) 
						matched_score *= 10;// ������ȫƥ��
					
					//pattern��path�غϵĲ��ֱ���Խ�󣬷���Խ�ߣ����û�������ߵ�arg�غϣ���������
					if(hitPathCnt != 0)
					{
						double hitScore = 1 + (double)hitPathCnt/(double)BoW_P.length;
						if(hitPathBetweenTwoArgCnt == hitPathCnt)
							hitScore += 1;
						else if(shortestPath.size() >= 4)	//���path�㹻����patternȴ��Ȼ��arg�غϣ���ΪҪ�۷�
						{
							//hitScore = 0.5;
							if(hitPathBetweenTwoArgCnt == 0) //path�㹻������pattern��ȫ��arg�غ�,�۷ָ���
								hitScore = 0.25;
						}
						matched_score *= hitScore;
					}
					
					//��������ַ����÷֣�����һЩ��ֵĳ���pattenr���� ��be bear die in���ĵ÷ֽϸ�
					matched_score = matched_score * Math.sqrt(mappedCharacterCount) - mappedCharacterCountPunishment;	// ƥ���"�ַ���"Խ�ࣨżȻ��ԽС������Ȼ�÷�Խ��	//����ƽ��һ�� 
					if (qlog.MODE_debug) System.out.println("��" + pattern + ", score=" + matched_score);

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
						
			//ֻ�Ǽ�¼��һ����ߵ�matching score������Ӧ��pattern���������棨outʱչʾ��������������ʵ������predicate mappingû�б�Ȼ��ϵ�������߲����������ҽ���ġ�
			if (simr.matchingScore > semr.LongestMatchingScore) 
			{
				semr.LongestMatchingScore = simr.matchingScore;
				semr.relationParaphrase = simr.relationParaphrase;
			}
			
			//����ֻ���ǡ�pattern��pid֮���ƥ���������������֮ǰ��ȡʱ�ġ�matching score�������� �Դ𣺳�ȡ�����Ѿ���pasList�ĵ÷����ˣ�paslist�ĵ÷�=��ȡ�÷ֳ���ƥ��÷ֳ���һ�������Ķ���
			//�������˼�ǣ�����һ���ض���pid=x�����������ĸ�pattern���ģ�Ҳ�������ȡʱ��ƥ��̶ȣ��Ҿͼ�¼������ۺϵ÷֡�������Ӧ��pattern��
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
	 * 1�����ȼ�������ĸ��д��ent��>mayType>mayEnt
	 * 2��mayEnt=1,��Ϊ����
	 * 3��mayType=1����Ϊ���������
	 * ��1����Ϊ��wordΪ��������top-k�׶λ�Ϊ���������һ����word��type��Ԫ�顣
	 * ���磺Which books by Kerouac were published by Viking Press? �еġ�books����
	 * ��2����Ϊ��wordΪ��������ֻ������������������wordʱ����ν����<type1>ʱ��
	 * ���磺Are tree frogs a type of amphibian? �еġ�amphibian����How many [countries] are there in [exT:Europe]��
	 * */
	public void constantVariableRecognition(HashMap<Integer, SemanticRelation> semanticRelations, QueryLogger qlog) 
	{
		Word[] words = qlog.s.words;
		for (Integer it : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(it);
			int arg1WordPos = sr.arg1Word.position - 1;
			int arg2WordPos = sr.arg2Word.position - 1;
			
			// �����������ĸ��д(�Ǿ��׵���)����������ʵ�壬����������
			if (sr.arg1Word.isNER() != null && !sr.arg1Word.isNER().equals("PERSON")
				|| containsUpperCharacter(sr.arg1Word.getFullEntityName(), sr.arg1Word.getNnHead().position)) 
			{
				sr.isArg1Constant = true;
				
				//[2015-12-12]����ĸ��д��̫������type������mayType����Ϊfalse  | [2015-12-13]���� How many countries are there in [exType|Europe]����Ϊ���word sequence
				if(sr.arg1Word.baseForm.contains("_"))
					sr.arg1Word.mayType = false;
			}
			// type���ȼ��ϸߣ����ж�type
			if(sr.arg1Word.mayType)
			{
				//rule��in/of [type]������Ϊ��������  ||How many [countries] are there in [exT:Europe] -> ?uri rdf:type yago:EuropeanCountries
				if(arg1WordPos >= 1 && words[arg1WordPos-1].baseForm.equals("in") || words[arg1WordPos-1].baseForm.equals("of"))
				{
					sr.isArg1Constant = true;
					//ѡ����Ϊ������type������preferred relation = <type1>
					double largerScore = 1000;
					if(sr.predicateMappings!=null && sr.predicateMappings.size()>0)
						largerScore = sr.predicateMappings.get(0).score * 2;
					PredicateMapping nPredicate = new PredicateMapping(Globals.pd.typePredicateID, largerScore, "[type]");
					sr.predicateMappings.add(0,nPredicate);
					
					//��Ϊ������typeӦ�÷��ں���
					sr.preferredSubj = sr.arg2Word;
				}
			}
			else if(sr.arg1Word.mayEnt)
			{
				sr.isArg1Constant = true;
			}
			
			// �����������ĸ��д(�Ǿ��׵���)����������ʵ�壬����������
			if (sr.arg2Word.isNER() != null && !sr.arg2Word.isNER().equals("PERSON")
				|| containsUpperCharacter(sr.arg2Word.getFullEntityName(), sr.arg2Word.getNnHead().position)) 
			{
				sr.isArg2Constant = true;
				
				//[2015-12-12]����ĸ��д��̫������type������mayType����Ϊfalse  | [2015-12-13]���� How many countries are there in [exType|Europe]����Ϊ���word sequence
				if(sr.arg2Word.baseForm.contains("_"))
					sr.arg2Word.mayType = false;
			}
			if(sr.arg2Word.mayType)
			{
				//rule��in/of [type]������Ϊ��������  ||How many [countries] are there in [exT:Europe] -> ?uri rdf:type yago:EuropeanCountries
				if(arg2WordPos >= 1 && words[arg2WordPos-1].baseForm.equals("in") || words[arg2WordPos-1].baseForm.equals("of"))
				{
					sr.isArg2Constant = true;
					//ѡ����Ϊ������type������preferred relation = <type1>
					double largerScore = 1000;
					if(sr.predicateMappings!=null && sr.predicateMappings.size()>0)
						largerScore = sr.predicateMappings.get(0).score * 2;
					PredicateMapping nPredicate = new PredicateMapping(Globals.pd.typePredicateID, largerScore, "[type]");
					sr.predicateMappings.add(0,nPredicate);
					
					//��Ϊ������typeӦ�÷��ں���
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