package qa.extract;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Sentence;
import nlp.ds.Word;
import rdf.SemanticRelation;

/**
 * ��recognizeSemanticRelationsǰʹ�ã���ʶ��Ϊspecial patterns���﷨����㲻�ٲ���relation extraction
 * @author huangruizhe
 *
 */
public class SpecialRelationRecognition {
	
	public ArrayList<ArrayList<Word>> specialRelations = null;
	public ArrayList<Integer> specialRelationsType = null;

	public Sentence sentence = null;
	
	public SpecialRelationRecognition(Sentence s) {
		specialRelations = new ArrayList<ArrayList<Word>>();
		specialRelationsType = new ArrayList<Integer>();
		sentence = s;
	}

	/**
	 * ʶ���﷨���ϵ�special patterns������Щspecial patterns�еĽ����ΪshouldIgnore����special patterns�е���Ϣ��������
	 * @param dependencyTree
	 */
	public void recognize () {
		ArrayList<Word> ret;

		while ((ret = recognize1_And(sentence.dependencyTreeStanford)) != null) {
			specialRelations.add(ret);
			specialRelationsType.add(1);
		}

		while ((ret = recognize3_TheSameAs(sentence.dependencyTreeStanford)) != null) {
			specialRelations.add(ret);
			specialRelationsType.add(3);
		}
		
		while ((ret = recognize2_TheSame(sentence.dependencyTreeStanford)) != null) {
			specialRelations.add(ret);
			specialRelationsType.add(2);			
		}
		
		for (DependencyTreeNode sNode : sentence.dependencyTreeStanford.nodesList)
			for (DependencyTreeNode mNode : sentence.dependencyTreeMalt.nodesList)
				if (sNode.equals(mNode) && (sNode.word.isIgnored||mNode.word.isIgnored))
					sNode.word.isIgnored = mNode.word.isIgnored = true;
	}
	
	/**
	 * ʶ�� and��as well as��ϵ
	 * AND: Who is a scientist and politician?
	 * AS WELL AS: Who is an athlete as well as an actor?
	 * 
	 * word1 and word2
	 * word1 as well as word2
	 * 
	 * RULE:
	 * (1) word1ӵ�е���Ԫ��(type1����)��word2������һ��
	 * (2) word2�����Լ���type1
	 * 
	 * type = 1
	 * 
	 * @param dependencyTree
	 * @return ret[0]: word1,  ret[1]: word2
	 */
	public ArrayList<Word> recognize1_And (DependencyTree dependencyTree) {
		for (DependencyTreeNode dtn : dependencyTree.getNodesList()) {
			if (dtn.word.isIgnored) continue;
			if ((dtn.word.baseForm.equals("and") || dtn.word.baseForm.equals("well"))
				&& dtn.dep_father2child.equals("cc")
				&& !dtn.father.word.posTag.startsWith("V")) {
				DependencyTreeNode son = dtn.father.containDependencyWithChildren("conj");
				if (son != null && !son.word.isIgnored) {
					dtn.word.isIgnored = true;
					son.word.isIgnored = true;
					
					ArrayList<Word> ret = new ArrayList<Word>();
					Word w1 = dtn.father.word.getNnHead();
					Word w2 = son.word.getNnHead();
					ret.add(w1);
					ret.add(w2);
					System.out.println("[SPECIAL RELATION]"
							+ w1.getFullEntityName()
							+ " and "
							+ w2.getFullEntityName());
					return ret;
				}
			}
		}
		return null;
	}

	/**
	 * ʶ��the same��ϵ
	 * THE SAME 1: Which countries use the same official languages?
	 * THE SAME 2: Which two countries use the same offical language?
	 * 
	 * the same word1, �Ҳ�����as
	 * 
	 * RULE:
	 * (1) word1ӵ�е���Ԫ��(type1����)��������һ�ݣ���subject��object���Ӻ�׺1&2
	 * (2) 1&2��type1��ͬ
	 * 
	 * type = 2
	 * 
	 * @param dependencyTree
	 * @return ret[0]: word1
	 */
	public ArrayList<Word> recognize2_TheSame (DependencyTree dependencyTree) {
		for (DependencyTreeNode dtn : dependencyTree.getNodesList()) {
			if (dtn.word.isIgnored) continue;
			if (dtn.word.baseForm.equals("same")
				&& dtn.dep_father2child.equals("amod")
				&& dtn.father.containDependencyWithChildren("det")!=null) {
				boolean flag = true;
				DependencyTreeNode son1 = null;
				DependencyTreeNode son2 = null;
				DependencyTreeNode son3 = null;
				if (dtn.father != null) {
					son1 = dtn.father.containWordBaseFormInChildren("as");
					if (son1 != null && !son1.word.isIgnored) {
						flag = false;
					}

					if (dtn.father.father != null) {
						son2 = dtn.father.father.containWordBaseFormInChildren("as");
						if (son2 != null && !son2.word.isIgnored) {
							flag = false;
						}
						
						if (dtn.father.father.father != null) {
							son3 = dtn.father.father.father.containWordBaseFormInChildren("as");
							if (son3 != null && !son3.word.isIgnored) {
								flag = false;
							}
						}

					}
				}
				if (flag) {
					dtn.word.isIgnored = true;
					
					ArrayList<Word> ret = new ArrayList<Word>();
					Word w1 = dtn.father.word.getNnHead();
					ret.add(w1);
					System.out.println("[SPECIAL PATTERN]"
							+ "the same "
							+ w1.getFullEntityName());
					return ret;
				}				
			}
		}
		return null;
	}
	
	/**
	 * ʶ��the same as��ϵ
	 * THE SAME AS 1: Which country use the same language as the United States? [the same���ζ���]
	 * THE SAME AS 2: Who was born in the same city as Athlete? [the same��������]
	 * THE SAME AS 3: Who starred in the same movie as Gong Li? [the same���δ���ʵĶ���]
	 * 
	 * the same word1 as word2, ����as
	 * 
	 * RULE:
	 * (1) word1ӵ�е���Ԫ��(type1����)��������һ�ݣ���subject��object����Ϊword2
	 * (2) word2�Լ�ʶ��type1
	 * 
	 * type = 3
	 * 
	 * update2013/05/07:������the same with, the same like
	 * 
	 * @param dependencyTree
	 * @return ret[0]: word1   ret[1]: word2
	 */
	public ArrayList<Word> recognize3_TheSameAs (DependencyTree dependencyTree) {
		for (DependencyTreeNode dtn : dependencyTree.getNodesList()) {
			if (dtn.word.baseForm.equals("same")
				&& dtn.dep_father2child.equals("amod")
				&& dtn.father.containDependencyWithChildren("det")!=null) {
				if (dtn.word.isIgnored) continue;
				
				if (dtn.father != null) {
					// THE SAME AS 1
					DependencyTreeNode son = dtn.father.containWordBaseFormInChildren("as");
					if (son == null) son = dtn.father.containWordBaseFormInChildren("with");	// the same with
					if (son == null) son = dtn.father.containWordBaseFormInChildren("like");	// the same like
					if (son != null && !son.word.isIgnored && son.dep_father2child.equals("prep")) {
						DependencyTreeNode sonson = son.containDependencyWithChildren("pobj");
						if (sonson != null && !sonson.word.isIgnored) {
							dtn.word.isIgnored = true;
							son.word.isIgnored = true;
							sonson.word.isIgnored = true;
							
							ArrayList<Word> ret = new ArrayList<Word>();
							Word w1 = dtn.father.word.getNnHead();
							Word w2 = sonson.word.getNnHead();
							ret.add(w1);
							ret.add(w2);
							System.out.println("[SPECIAL PATTERN]"
									+ "the same "
									+ w1.getFullEntityName()
									+ " " + son.word.baseForm + " "
									+ w2.getFullEntityName());
							return ret;
						}
					}
					
					if (dtn.father.father != null) {
						// THE SAME AS 2
						son = dtn.father.father.containWordBaseFormInChildren("as");
						if (son == null) son = dtn.father.father.containWordBaseFormInChildren("with");
						if (son == null) son = dtn.father.father.containWordBaseFormInChildren("like");
						if (son != null && !son.word.isIgnored && son.dep_father2child.equals("prep")) {
							DependencyTreeNode sonson = son.containDependencyWithChildren("pobj");
							if (sonson != null && !sonson.word.isIgnored) {
								dtn.word.isIgnored = true;
								son.word.isIgnored = true;
								sonson.word.isIgnored = true;

								ArrayList<Word> ret = new ArrayList<Word>();
								Word w1 = dtn.father.word.getNnHead();
								Word w2 = sonson.word.getNnHead();
								ret.add(w1);
								ret.add(w2);
								System.out.println("[SPECIAL PATTERN]"
										+ "the same "
										+ w1.getFullEntityName()
										+ " " + son.word.baseForm + " "
										+ w2.getFullEntityName());
								return ret;
							}
						}
						
						if (dtn.father.father.father != null) {
							// THE SAME AS 3
							son = dtn.father.father.father.containWordBaseFormInChildren("as");
							if (son == null) son = dtn.father.father.father.containWordBaseFormInChildren("with");
							if (son == null) son = dtn.father.father.father.containWordBaseFormInChildren("like");
							if (son != null && !son.word.isIgnored && son.dep_father2child.equals("prep") && dtn.father.father.word.posTag.equals("IN")) {
								DependencyTreeNode sonson = son.containDependencyWithChildren("pobj");
								if (sonson != null && !sonson.word.isIgnored) {
									dtn.word.isIgnored = true;
									son.word.isIgnored = true;
									sonson.word.isIgnored = true;

									ArrayList<Word> ret = new ArrayList<Word>();
									Word w1 = dtn.father.word.getNnHead();
									Word w2 = sonson.word.getNnHead();
									ret.add(w1);
									ret.add(w2);
									System.out.println("[SPECIAL PATTERN]"
											+ "the same "
											+ w1.getFullEntityName()
											+ " " + son.word.baseForm + " "
											+ w2.getFullEntityName());
									return ret;
								}
							}
						}
					}
				}
			}
		}
		return null;
	}
	
	public void execute (HashMap<Integer, SemanticRelation> relations, TypeRecognition tr) {
		int size = specialRelations.size();
		for (int i = 0; i < size; i ++) {
			switch (specialRelationsType.get(i)) {
			case 1:				
				execute1_And(relations, specialRelations.get(i), tr);
				break;
			case 2:		
				execute2_TheSame(relations, specialRelations.get(i), tr);
				break;
			case 3:				
				execute3_TheSameAs(relations, specialRelations.get(i), tr);
				break;
			default:
				System.err.println("Case error in SpecialPatternRecognizer.execute().");
				break;
			}
		}
	}
	
	public void execute1_And (HashMap<Integer, SemanticRelation> relations, ArrayList<Word> patternInfo, TypeRecognition tr) {
		Word word1 = patternInfo.get(0);
		Word word2 = patternInfo.get(1);
		HashSet<SemanticRelation> newRelations = new HashSet<SemanticRelation>();
		for (Integer in : relations.keySet()) {
			SemanticRelation sr = relations.get(in);
			if (sr.arg1Word == word1) {
				SemanticRelation srnew = new SemanticRelation(sr);
				
				srnew.arg1Word = word2;
				srnew.extractingMethod = 'R';
				srnew.dependOnSemanticRelation = sr;
				
//				����type��Ϣ����word���ˣ����Բ���Ҫ�����������
//				srnew.arg1Types = tr.recognize(word2.getBaseFormEntityName());
				
				newRelations.add(srnew);
			}
			else if (sr.arg2Word == word1) {
				SemanticRelation srnew = new SemanticRelation(sr);
	
				srnew.arg2Word = word2;
				srnew.extractingMethod = 'R';
				srnew.dependOnSemanticRelation = sr;
				
//				����type��Ϣ����word���ˣ����Բ���Ҫ�����������
//				srnew.arg2Types = tr.recognize(word2.getBaseFormEntityName());
				
				newRelations.add(srnew);
			}
		}
		for (SemanticRelation sr : newRelations) {
			relations.put(sr.hashCode(), sr);
		}
	}
	
	public void execute2_TheSame (HashMap<Integer, SemanticRelation> relations, ArrayList<Word> patternInfo, TypeRecognition tr) {
		Word word1 = patternInfo.get(0);
		ArrayList<SemanticRelation> deleteList = new ArrayList<SemanticRelation>();
		HashSet<SemanticRelation> newRelations = new HashSet<SemanticRelation>();
		for (Integer in : relations.keySet()) {
			SemanticRelation sr = relations.get(in);
			if (sr.arg1Word == word1) {
				SemanticRelation srnew1 = new SemanticRelation(sr);
				SemanticRelation srnew2 = new SemanticRelation(sr);
				
				srnew1.arg2SuffixId = 1;//����ֱ�Ӹ�sr, ���򱨴�java.util.ConcurrentModificationException
				srnew2.arg2SuffixId = 2;
				srnew2.extractingMethod = 'R';
				srnew2.dependOnSemanticRelation = srnew1;
				
				newRelations.add(srnew1);
				newRelations.add(srnew2);
				deleteList.add(sr);
			}
			else if (sr.arg2Word == word1) {
				SemanticRelation srnew1 = new SemanticRelation(sr);
				SemanticRelation srnew2 = new SemanticRelation(sr);

				srnew1.arg1SuffixId = 1;
				srnew2.arg1SuffixId = 2;
				srnew2.extractingMethod = 'R';
				srnew2.dependOnSemanticRelation = srnew1;

				newRelations.add(srnew1);
				newRelations.add(srnew2);
				deleteList.add(sr);
			}
		}
		for (SemanticRelation sr : deleteList) {
			relations.remove(sr.hashCode());
		}
		for (SemanticRelation sr : newRelations) {
			relations.put(sr.hashCode(), sr);
		}
	}
	
	public void execute3_TheSameAs (HashMap<Integer, SemanticRelation> relations, ArrayList<Word> patternInfo, TypeRecognition tr) {
		Word word1 = patternInfo.get(0);
		Word word2 = patternInfo.get(1);
		HashSet<SemanticRelation> newRelations = new HashSet<SemanticRelation>();
		for (Integer in : relations.keySet()) 
		{
			SemanticRelation sr = relations.get(in);
			if (sr.arg1Word == word1) 
			{
				SemanticRelation srnew = new SemanticRelation(sr);

				srnew.arg2Word = word2;
				srnew.extractingMethod = 'R';
				srnew.dependOnSemanticRelation = sr;
				
//				����type��Ϣ����word���ˣ����Բ���Ҫ�����������
//				srnew.arg2Types = tr.recognize(word2.getBaseFormEntityName());

				newRelations.add(srnew);
			}
			else if (sr.arg2Word == word1) 
			{
				SemanticRelation srnew = new SemanticRelation(sr);

				srnew.arg1Word = word2;
				srnew.extractingMethod = 'R';
				srnew.dependOnSemanticRelation = sr;
				
//				����type��Ϣ����word���ˣ����Բ���Ҫ�����������
//				srnew.arg1Types = tr.recognize(word2.getBaseFormEntityName());
				
				newRelations.add(srnew);
			}
		}
		for (SemanticRelation sr : newRelations) {
			relations.put(sr.hashCode(), sr);
		}
	}
}