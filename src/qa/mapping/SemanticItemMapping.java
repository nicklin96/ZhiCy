package qa.mapping;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;

import nlp.ds.Word;
import nlp.ds.Sentence.SentenceType;
import fgmt.EntityFragment;
import fgmt.RelationFragment;
import fgmt.TypeFragment;
import log.QueryLogger;
import qa.Globals;
import rdf.EntityMapping;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.Triple;
import rdf.TypeMapping;

public class SemanticItemMapping {
	
	public HashMap<Word, ArrayList<EntityMapping>> entityDictionary = new HashMap<Word, ArrayList<EntityMapping>>();
	public static int k = 10;
	public static int t = 10;
	ArrayList<Sparql> rankedSparqls = new ArrayList<Sparql>();
	
	public ArrayList<ArrayList<EntityMapping>> entityPhrasesList = new ArrayList<ArrayList<EntityMapping>>();
	public ArrayList<Word> entityWordList = new ArrayList<Word>();
	public HashMap<Integer, EntityMapping> currentEntityMappings = new HashMap<Integer, EntityMapping>();
	
	public ArrayList<ArrayList<PredicateMapping>> predicatePhraseList = new ArrayList<ArrayList<PredicateMapping>>();
	public ArrayList<SemanticRelation> predicateSrList = new ArrayList<SemanticRelation>();
	public HashMap<Integer, PredicateMapping> currentPredicateMappings = new HashMap<Integer, PredicateMapping>();
	
	public HashMap<Integer, SemanticRelation> semanticRelations = null;
	public QueryLogger qlog = null;
	
	public EntityFragmentDict efd = new EntityFragmentDict();
	
	public boolean isAnswerFound = false;	// TODO ���Ǹ���ģ�
	
	public void process(QueryLogger qlog, HashMap<Integer, SemanticRelation> semRltn) {
		semanticRelations = semRltn;
		this.qlog = qlog;
		long t = 0;

		entityPhrasesList.clear();
		entityWordList.clear();
		currentEntityMappings.clear();
		predicatePhraseList.clear();
		predicateSrList.clear();
		currentPredicateMappings.clear();
		
		// ע�⡾������type��Ҳ�ǳ�����һ�֣����ǲ�����top-k��ֱ��ȡ������ߵ�type husen
		// 1. collect names of constant(entities), and map them to the entities
		t = System.currentTimeMillis();
		
		Iterator<Map.Entry<Integer, SemanticRelation>> it = semanticRelations.entrySet().iterator();
        while(it.hasNext())
        {
            Map.Entry<Integer, SemanticRelation> entry = it.next();
            SemanticRelation sr = entry.getValue();
            
            //ent��type��literal��Ϊ����
            //ʶ��Ϊ�������� entity|type mappingΪ�գ�����������triple
			//���仰˵��parserʶ���ner��pre�׶�ʶ���mappingû����֮��Ӧ�ģ�Ҳ�����������ٴ�mapping���ԣ�����Ϊ�˼��ֱ����������ֻ�Ͽ�pre�׶��ҵ���mapping��
			if (sr.isArg1Constant && !sr.arg1Word.mayType && !sr.arg1Word.mayEnt) 
			{
				it.remove();
				continue;
			}
			if (sr.isArg2Constant && !sr.arg2Word.mayType && !sr.arg2Word.mayEnt) 
			{
				it.remove();
				continue;
			}
			
			//��Ϊtype����ǳ����������score and ranking�д���������ֻ����ent
			if (sr.isArg1Constant && !sr.arg1Word.mayType) 
			{
				//��Ϊent����ǳ���
				if (!entityDictionary.containsKey(sr.arg1Word)) 
				{
					entityDictionary.put(sr.arg1Word, sr.arg1Word.emList);
				}
				entityPhrasesList.add(sr.arg1Word.emList);
				entityWordList.add(sr.arg1Word);
			}
			if (sr.isArg2Constant && !sr.arg2Word.mayType) 
			{	
				//��Ϊent����ǳ���
				if (!entityDictionary.containsKey(sr.arg2Word)) 
				{
					entityDictionary.put(sr.arg2Word, sr.arg2Word.emList);
				}
				entityPhrasesList.add(sr.arg2Word.emList);
				entityWordList.add(sr.arg2Word);
			}
        }
		
		if (!qlog.timeTable.containsKey("CollectEntityNames")) {
			qlog.timeTable.put("CollectEntityNames", 0);
		}
		qlog.timeTable.put("CollectEntityNames", qlog.timeTable.get("CollectEntityNames")+(int)(System.currentTimeMillis()-t));
		
		//debug...
		/*
		System.out.println("entityPhrasesList: ");
		for (int i=0;i<entityPhrasesList.size();i++)
			System.out.println(entityPhrasesList.get(i));
		*/
		
		// 2. join
		t = System.currentTimeMillis();
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			predicatePhraseList.add(sr.predicateMappings);
			predicateSrList.add(sr);
		}
		if(semanticRelations.size()>0)
		{
			topkJoin(semanticRelations);
		}
		else
		{
			System.out.println("No Valid SemanticRelations.");
		}
		qlog.timeTable.put("TopkJoin", (int)(System.currentTimeMillis()-t));

		// 3. sort and rank
		Collections.sort(rankedSparqls);
		
		//qlog.rankedSparqls = rankedSparqls;
		qlog.rankedSparqls.addAll(rankedSparqls);
		qlog.entityDictionary = entityDictionary;
		
		System.out.println("CollectEntityNames time=" + qlog.timeTable.get("CollectEntityNames"));
		System.out.println("TopkJoin time=" + qlog.timeTable.get("TopkJoin"));
	}
	
	public ArrayList<EntityMapping> getEntityIDsAndNames(Word entity, QueryLogger qlog) {
		if (entityDictionary.containsKey(entity)) {
			return entityDictionary.get(entity);
		}
		
		//���� nʵ���Ͼ���ԭʼ��ʽ������ "_" �ָ��
		String n = entity.getFullEntityName();
		System.out.println(n+" " + preferDBpediaLookupOrLucene(n));
		ArrayList<EntityMapping> ret= new ArrayList<EntityMapping>();
		
		if (preferDBpediaLookupOrLucene(n) == 1) { // �ж�ʹ��DBpediaLookup���Ȼ���Lucene
			
			ret.addAll(Globals.dblk.getEntityMappings(n, qlog));
			//���ʹ��Lucene�÷ֽϸߣ�ƥ��ȼ��ߣ���ҲӦ��������Lucene
			ret.addAll(EntityFragment.getEntityMappingList(n));
		}
		else{//�������Lucene���ʵ��ID
			ret.addAll(EntityFragment.getEntityMappingList(n));
			
			if (ret.size() == 0 || ret.get(0).score<40) {
				ret.addAll(Globals.dblk.getEntityMappings(n, qlog));
			}
		}
		
		Collections.sort(ret);
		
		System.out.println(n +"("+t+")"+" -->");
		int cnt = t;
		for (EntityMapping em : ret) {
			System.out.println(em.entityID);
			if ((--cnt) <= 0) break;
		}
		
		if (ret.size() > 0) return ret;
		else return null;
	}
	
	//added by hanshuo, 2013-09-08
	//return 1 means using DBpediaLookup would be better, return 0 means Lucene.
	public int preferDBpediaLookupOrLucene(String entityName)
	{
		int cntUpperCase = 0;
		int cntLowerCase = 0;
		int cntSpace = 0;
		int cntPoint = 0;
		int length = entityName.length();
		for (int i=0; i<length; i++)
		{
			char c = entityName.charAt(i);
			if (c==' ')
				cntSpace++;
			else if (c=='.')
				cntPoint++;
			else if (c>='a' && c<='z')
				cntLowerCase++;
			else if (c>='A' && c<='Z')
				cntUpperCase++;
		}
		
		if ((cntUpperCase>0 || cntPoint>0) && cntSpace<3)
			return 1;
		if (cntUpperCase == length)
			return 1;
		return 0;		
	}
	
	public void topkJoin (HashMap<Integer, SemanticRelation> semanticRelations) 
	{
		dfs_entityName(0);
	}
	
	//һ�� level ��Ӧһ�� word+[��word��Ӧ��entities]
	public void dfs_entityName (int level_i) 
	{
		//��ʱentities���ź���
		if (level_i == entityPhrasesList.size()) 
		{
			dfs_predicate(0);
			return;
		}
		
		ArrayList<EntityMapping> list = entityPhrasesList.get(level_i);
		Word w = entityWordList.get(level_i);
		int tcount = 0;
		for(EntityMapping em : list) 
		{
			if (tcount == t || isAnswerFound) break;
			currentEntityMappings.put(w.hashCode(), em);
			dfs_entityName(level_i+1);
			currentEntityMappings.remove(w.hashCode());
			tcount ++;
		}
	}
	
	public void dfs_predicate(int level_i) 
	{
		if (level_i == predicatePhraseList.size()) 
		{
			scoringAndRanking();
			return;
		}
		
		ArrayList<PredicateMapping> list = predicatePhraseList.get(level_i);
		SemanticRelation sr = predicateSrList.get(level_i);
		if (sr.dependOnSemanticRelation != null) 
		{
			dfs_predicate(level_i+1);
		}
		else 
		{
			int tcount=0;
			for (PredicateMapping pm : list) 
			{
				if (tcount==t || isAnswerFound) break;
				currentPredicateMappings.put(sr.hashCode(), pm);
				dfs_predicate(level_i+1);
				currentPredicateMappings.remove(sr.hashCode());
				tcount++;
			}
		}
	}

	/*
	 * �����������֤������ÿ����Ҫ��ϵ�Ԫ�ض���ѡ��һ��ȷ����ֵ����ͨ��currentEntityMappings��currentPredicateMappings��
	 * ��predicate��entity����ȷ����������1������ȷ����ent��p�Լ�һЩ�������Ϣ����һ��sparql��Ȼ�������Ƭƥ�䣨ע����Ƭ��������֣������ͨ�������ֲ�����
	 * ע�⣺���������embedded type��Ϣ��
	 * ���磺Ϊ?who <height> ?how ����  ?who <type1> <Person> ���� ?book <author> <Tom> ���� ?book <type1> <Book>
	 * ע�⣺���������constant type��Ϣ��
	 * ���磺ask: <YaoMing> <type1> <BasketballPlayer>
	 * */
	public void scoringAndRanking() 
	{			
		Sparql sparql = new Sparql(semanticRelations);

		HashSet<String> typeSetFlag = new HashSet<String>();	// ��֤ÿ��������type1ֻ��sparql�г���һ�� |���û�õ���
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			String sub, obj;
			int subjId = -1, objId = -1;
			int pid;
			double score = 1;
			boolean isSubjObjOrderSameWithSemRltn=true; //ʵ��û���õ��������
			
			// argument1 | ���(sr.arg1Word.mayEnt || sr.arg1Word.mayType)=true����sr.isArg1Constant=false����ζ�� ����һ�� �����桰type�������硰the book of ..���е�book
			if(sr.isArg1Constant && (sr.arg1Word.mayEnt || sr.arg1Word.mayType) ) 
			{
				if(!sr.arg1Word.mayType)
				{
					EntityMapping em = currentEntityMappings.get(sr.arg1Word.hashCode());
					subjId = em.entityID;
					sub = em.entityName;
					score *= em.score;
				}
				else
				{
					TypeMapping tm = sr.arg1Word.tmList.get(0);
					subjId = Triple.TYPE_ROLE_ID;
					sub = tm.typeName;
					score *= (tm.score*100);	//entity����������100(��Ȼ���ֻ����50��)����type����������1�������������100
				}
			}
			else 
			{
				subjId = Triple.VAR_ROLE_ID;
				sub = "?" + sr.arg1Word.originalForm;
			}
			// argument1�����̺���type��Ϣ������ ?book <type> <Book>
			Triple subt = null;
			//���硰Is Yao Ming a basketball player?����basketball player��ʶ������type��������Ҫ��һ����Ԫ�飨basketball player <type> basketball player��
			//������Ӧ�ó��ֵ� <Yao Ming> <type> <basketball player>��֮����������
			if (sr.arg1Word.tmList != null && sr.arg1Word.tmList.size() > 0 && !typeSetFlag.contains(sub) && !sr.isArg1Constant) 
			{
				StringBuilder type = new StringBuilder("");
				for (TypeMapping tm: sr.arg1Word.tmList) 
				{
					Integer tt = tm.typeID;
					if(tt != -1)
						type.append(TypeFragment.typeId2ShortName.get(tt));
					else
						type.append(tm.typeName);
					type.append('|');
				}
				String ttt = type.substring(0, type.length()-1);
				subt = new Triple(subjId, sub, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, ttt, null, 10);
				subt.typeSubjectWord = sr.arg1Word;
				
				if(sr.arg1Word.tmList.get(0).prefferdRelation == -1)
					subt = null;
			}
			
			// predicate
			SemanticRelation dep = sr.dependOnSemanticRelation;
			PredicateMapping pm = null; 
			if (dep == null) 
			{
				pm = currentPredicateMappings.get(sr.hashCode());
				pid = pm.pid;
			}
			else 
			{
				pm = currentPredicateMappings.get(dep.hashCode());
				pid = pm.pid;
			}
			score *= pm.score;
				
			// argument2
			if(sr.isArg2Constant && (sr.arg2Word.mayEnt || sr.arg2Word.mayType) )
			{
				if(!sr.arg2Word.mayType)
				{
					EntityMapping em = currentEntityMappings.get(sr.arg2Word.hashCode());
					objId = em.entityID;
					obj = em.entityName;
					score *= em.score;
				}
				else
				{
					TypeMapping tm = sr.arg2Word.tmList.get(0);
					objId = Triple.TYPE_ROLE_ID;
					obj = tm.typeName;
					score *= (tm.score*100);	//entity����������100(��Ȼ���ֻ����50��)����type����������1�������������100
				}
			}
			else 
			{
				objId = Triple.VAR_ROLE_ID;
				obj = "?" + sr.arg2Word.getFullEntityName();
			}
			// argument2��type��Ϣ
			Triple objt = null;
			if (sr.arg2Word.tmList != null && sr.arg2Word.tmList.size() > 0 && !typeSetFlag.contains(obj) && !sr.isArg2Constant) 
			{
				StringBuilder type = new StringBuilder("");				
				for (TypeMapping tm : sr.arg2Word.tmList) 
				{
					Integer tt = tm.typeID;
					if(tt != -1)
						type.append(TypeFragment.typeId2ShortName.get(tt));
					else
						type.append(tm.typeName);
					type.append('|');
				}
				String ttt = type.substring(0, type.length()-1);
				objt = new Triple(objId, obj, Globals.pd.typePredicateID, Triple.TYPE_ROLE_ID, ttt, null, 10);
				objt.typeSubjectWord = sr.arg2Word;
				
				if(sr.arg2Word.tmList.get(0).prefferdRelation == -1)
					objt = null;
			}
			
			
			// ��literal���ԵĴ��� �������ǿ����predicate����ֻ�ܽ�literal��
			if (RelationFragment.isLiteral(pid) && (subt != null || objt != null)) 
			{
				// �����ʵ�壬��ʵ�����ǰ��				
				if (sub.startsWith("?") && obj.startsWith("?")) {
					// ��type�����literal��
					if (subt != null) {
						subt.object += ("|" + "literal_HRZ");
					}
					if (objt != null) {
						objt.object += ("|" + "literal_HRZ");
					}
					
					if (subt==null && objt!=null) {						
						//��obj��type��sub��type����п��ܽ���sub��obj��λ��
						String temp = sub;
						int tmpId = subjId;
						sub = obj;
						subjId = objId;
						obj = temp;
						objId = tmpId;
						isSubjObjOrderSameWithSemRltn=!isSubjObjOrderSameWithSemRltn;
					}
					
				}
				else if (sub.startsWith("?") && !obj.startsWith("?")) {
					// ��Ҫ����subj/obj��˳��
					// ����subjt/objt��˳����Բ���������Ϊ��������Ҫ���ӽ�sparql��
					if (subt != null) {
						subt.object += ("|" + "literal_HRZ");
					}					
					String temp = sub;
					int tmpId = subjId;
					sub = obj;
					subjId = objId;
					obj = temp;
					objId = tmpId;
					isSubjObjOrderSameWithSemRltn=!isSubjObjOrderSameWithSemRltn;
					//System.out.println("here: "+sub+obj);
					
				}
				else if (obj.startsWith("?") && !sub.startsWith("?")) {
					if (objt != null) {
						objt.object += ("|" + "literal_HRZ");
					}				
				}
			}
			
			
			
			// Ϊ�˴���һ�����ʾ䣬����Ӧ���Ȳ���triple��isTripleCompatibleCanSwap�ļ�飬���ں����� 
			// ���γ�sparql֮��������飬���ӶȲ���
			
			Triple t = new Triple(subjId, sub, pid, objId, obj, sr, score,isSubjObjOrderSameWithSemRltn);		
			//System.out.println("triple: "+t+" "+isTripleCompatibleCanSwap(t));
			
			sparql.addTriple(t);
			
			//����subject��object��type��Ϣ�ķ�����Ҫ��triple�����ķ������
			if (subt != null) 
			{
				subt.score += t.score*0.2;
				sparql.addTriple(subt);
				typeSetFlag.add(subt.subject);//С�ģ�������sub����Ϊ�ڴ���literal�ߵ�ʱ�򣬿���subj/obj˳�򽻻�
			}
			if (objt != null) 
			{
				objt.score += t.score*0.2;
				sparql.addTriple(objt);
				typeSetFlag.add(objt.subject);
			}
		}
		
		if (!qlog.MODE_fragment) {
			// ����һ��������complete compatibility check
			 rankedSparqls.add(sparql);
			 isAnswerFound = true;
		}
		else {
			// ������������complete compatibility check������ö��subj/obj˳�� 
			sparql.typesComesFirst();			

			//����isTripleCompatibleCanSwap�����ж�triple�Ƿ������Ƭ�����Խ���subj��obj�������һ�������㣬ֱ��return��ע������ֻ�ǵ�һ����飬ͨ����Ҫ��enumerateEubjObjOrders��������еڶ������飩
			for (Triple t : sparql.tripleList)				
				if(t.predicateID!=Globals.pd.typePredicateID && !isTripleCompatibleCanSwap(t))
					return;
			
			//System.out.println("spq: "+sparql+" n:"+sparql.getVariableNumber());
			enumerateSubjObjOrders(sparql, new Sparql(sparql.semanticRelations), 0);						
		}
		
	}
	
	/*
	 * ע�⣺
	 * typeId=-1˵��û�����ͼ��Ƭ����type������Ϊ��Ƭ���ʱ������
	 * */
	TypeFragment getTypeFragmentByWord(Word word)
	{
		TypeFragment tf = null;
		//extendType �� id=-1����ΪĿǰKB/fragment��û��extendType�����ݣ�����id=-1����ȡtypeFragment
		if(word.tmList!=null && word.tmList.size()>0)
		{
			int typeId = word.tmList.get(0).typeID;
			if(typeId != -1)
				tf = TypeFragment.typeFragments.get(typeId);
		}
		return tf;
	}
	
	/*
	 * ���������ֻ��Ϊ��������⡰���ں���enumerateSubjObjOrders�л��һ����ϸ��⣬���������ˡ�predicate��ǰ��ent���ϵ�type����Ϣ����2016.4.6
	 * ע�⣬predicate = type����Ԫ�鲻������������
	 * �������Ҫ����һЩ������
	 * 1���ж�ʱ����type��Ϣ
	 * 
	 * 2016-4-28����Ϊ��entity id��Ϊkey��������ԭ����entity name
	 * */
	public boolean isTripleCompatibleCanSwap (Triple t) {
		
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)
		{	
			if (fragmentCompatible2(t.subjId, t.predicateID, t.objId) >
				fragmentCompatible2(t.objId, t.predicateID, t.subjId)) 
				t.swapSubjObjOrder();
				
			if (fragmentCompatible(t.subjId, t.predicateID, t.objId))			
				return true;
			return false;
			
		}
		else
		{	
			//����������
			if(t.subject.startsWith("?") && t.object.startsWith("?"))
			{
				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
				
				//�����ڵ�˳����һ��
				boolean ok = true;
				if((subjTf != null && !subjTf.outEdges.contains(t.predicateID)) || (objTf != null && !objTf.inEdges.contains(t.predicateID)))
				{
					ok = false;
				}
				//����˳����һ��
				if(!ok)
				{
					if((subjTf == null || subjTf.inEdges.contains(t.predicateID)) && (objTf == null || objTf.outEdges.contains(t.predicateID)))
					{	
						ok = true;
						t.swapSubjObjOrder();
						//TODO:�Ƿ���Ҫһ��������ʾ�Ժ�Ӧ���ٸı�����t��˳��?
					}
				}
				
				return ok;
			}
//			//TODO:������ʵ�� || �������ֻ����entһ��Ϳ��ԣ��ȿ�һ��Ч��  || ?city	<type1>	<City> ��<Chile_Route_68>	<country>	?city �������֣�<country>��city���Ϸ���������������ˣ�����Ҫ��һ���Ƿ����֡����ˡ�
//			else if(t.subject.startsWith("?") || t.object.startsWith("?"))
//			{
//				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
//				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
//				
//				
//			}
			//ʵ�壬ʵ�� || ������ʵ��
			else
			{
				if (fragmentCompatible(t.subjId, t.predicateID, t.objId)) {			
					return true;
				}			
				if (fragmentCompatible(t.objId, t.predicateID, t.subjId)) {			
					t.swapSubjObjOrder();
					return true;
				}	
				return false;
			}
		
		}
	}

	/*//�ɰ汾����	
	public boolean isTripleCompatibleCanSwap (Triple t) {
		
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)
		{	
			if (fragmentCompatible2(t.subject, t.predicateID, t.object) >
				fragmentCompatible2(t.object, t.predicateID, t.subject)) 
				t.swapSubjObjOrder();
				
			if (fragmentCompatible(t.subject, t.predicateID, t.object))			
				return true;
			return false;
			
		}
		else
		{	
			//����������
			if(t.subject.startsWith("?") && t.object.startsWith("?"))
			{
				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
				
				//�����ڵ�˳����һ��
				boolean ok = true;
				if((subjTf != null && !subjTf.outEdges.contains(t.predicateID)) || (objTf != null && !objTf.inEdges.contains(t.predicateID)))
				{
					ok = false;
				}
				//����˳����һ��
				if(!ok)
				{
					if((subjTf == null || subjTf.inEdges.contains(t.predicateID)) && (objTf == null || objTf.outEdges.contains(t.predicateID)))
					{	
						ok = true;
						t.swapSubjObjOrder();
						//TODO:�Ƿ���Ҫһ��������ʾ�Ժ�Ӧ���ٸı�����t��˳��?
					}
				}
				
				return ok;
			}
//			//TODO:������ʵ�� || �������ֻ����entһ��Ϳ��ԣ��ȿ�һ��Ч��  || ?city	<type1>	<City> ��<Chile_Route_68>	<country>	?city �������֣�<country>��city���Ϸ���������������ˣ�����Ҫ��һ���Ƿ����֡����ˡ�
//			else if(t.subject.startsWith("?") || t.object.startsWith("?"))
//			{
//				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
//				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
//				
//				
//			}
			//ʵ�壬ʵ�� || ������ʵ��
			else
			{
				if (fragmentCompatible(t.subject, t.predicateID, t.object)) {			
					return true;
				}			
				if (fragmentCompatible(t.object, t.predicateID, t.subject)) {			
					t.swapSubjObjOrder();
					return true;
				}	
				return false;
			}
		
		}
	}
*/	
	public boolean isTripleCompatibleNotSwap (Triple t) {
		if (t.predicateID == Globals.pd.typePredicateID) {
			return true;
		}
		else if (fragmentCompatible(t.subjId, t.predicateID, t.objId)) {
			return true;
		}
		else {
			return false;
		}
	}

	public boolean fragmentCompatible (int id1, int pid, int id2) {
		EntityFragment ef1 = efd.getEntityFragmentByEid(id1);
		EntityFragment ef2 = efd.getEntityFragmentByEid(id2);
	
		// �ǺϷ���entity�ͱ���fragment��������ΪDBpediaLookup���ص�ent���������������г���
		if (id1!=Triple.TYPE_ROLE_ID && id1!=Triple.VAR_ROLE_ID && ef1 == null) return false;
		if (id2!=Triple.TYPE_ROLE_ID && id2!=Triple.VAR_ROLE_ID && ef2 == null) return false;
		
		boolean ef1_constant = (ef1==null)?false:true;
		boolean ef2_constant = (ef2==null)?false:true;
		int entityCnt=0,compatibleCnt=0;
		if(ef1_constant) {
			entityCnt++;
			if (ef1.outEdges.contains(pid))
				compatibleCnt++;
		}
		
		if (ef2_constant) {
			entityCnt++;
			if (ef2.inEdges.contains(pid))
				compatibleCnt++;
		}
		
		//����select��sparql��Ҫ�ϸ�֤predicate��subject��object��ƥ�䣻��ask�Ϳ��Էſ�
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)	
			return entityCnt-compatibleCnt<=1;		
		else		
			return entityCnt==compatibleCnt;
		
	}
	
	//�ɰ�backup
//	public boolean fragmentCompatible (String en1, int pid, String en2) {
//		EntityFragment ef1 = efd.getEntityFragmentByName(en1);
//		EntityFragment ef2 = efd.getEntityFragmentByName(en2);
//	
//		// �ǺϷ���entity�ͱ���fragment
//		if (!en1.startsWith("?") && ef1 == null) return false;
//		if (!en2.startsWith("?") && ef2 == null) return false;
//		
//		boolean ef1_constant = (ef1==null)?false:true;
//		boolean ef2_constant = (ef2==null)?false:true;
//		int entityCnt=0,compatibleCnt=0;
//		if(ef1_constant) {
//			entityCnt++;
//			if (ef1.outEdges.contains(pid))
//				compatibleCnt++;
//		}
//		
//		if (ef2_constant) {
//			entityCnt++;
//			if (ef2.inEdges.contains(pid))
//				compatibleCnt++;
//		}
//		
//		//����select��sparql��Ҫ�ϸ�֤predicate��subject��object��ƥ�䣻��ask�Ϳ��Էſ�
//		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)	
//			return entityCnt-compatibleCnt<=1;		
//		else		
//			return entityCnt==compatibleCnt;
//		
//	}
	
	public int fragmentCompatible2 (int id1, int pid, int id2) {
		EntityFragment ef1 = efd.getEntityFragmentByEid(id1);
		EntityFragment ef2 = efd.getEntityFragmentByEid(id2);	

		int entityCnt=0,compatibleCnt=0;
		if(id1 != Triple.VAR_ROLE_ID && id1 != Triple.TYPE_ROLE_ID) {
			entityCnt++;
			if (ef1!=null && ef1.outEdges.contains(pid))
				compatibleCnt++;
		}
		
		if (id2 != Triple.VAR_ROLE_ID && id2 != Triple.TYPE_ROLE_ID) {
			entityCnt++;
			if (ef2!=null && ef2.inEdges.contains(pid))
				compatibleCnt++;
		}		

		return entityCnt-compatibleCnt;
	}
	
	//�ɰ汾backup
//	public int fragmentCompatible2 (String en1, int pid, String en2) {
//		EntityFragment ef1 = efd.getEntityFragmentByName(en1);
//		EntityFragment ef2 = efd.getEntityFragmentByName(en2);	
//
//		int entityCnt=0,compatibleCnt=0;
//		if(!en1.startsWith("?")) {
//			entityCnt++;
//			if (ef1!=null && ef1.outEdges.contains(pid))
//				compatibleCnt++;
//		}
//		
//		if (!en2.startsWith("?")) {
//			entityCnt++;
//			if (ef2!=null && ef2.inEdges.contains(pid))
//				compatibleCnt++;
//		}		
//
//		return entityCnt-compatibleCnt;
//	}
		
	public boolean checkConstantConsistency (Sparql spql) {
		HashMap<String, String> constants = new HashMap<String, String>();
		for (Triple t : spql.tripleList) {
			if (!t.subject.startsWith("?")) {
				String e = t.getSubjectWord().getFullEntityName();
				if (!constants.containsKey(e))
					constants.put(e, t.subject);
				else {
					if (!constants.get(e).equals(t.subject))
						return false;
				}				
			}
			if (!t.object.startsWith("?")) {
				String e = t.getObjectWord().getFullEntityName();
				if (!constants.containsKey(e))
					constants.put(e, t.object);
				else {
					if (!constants.get(e).equals(t.object))
						return false;
				}
			}

		}
		return true;
	}
	
	// ö��subject/object˳��  || ��������б�Ҫ��
	public boolean enumerateSubjObjOrders (Sparql originalSpq, Sparql currentSpq, int level) {
		if (level == originalSpq.tripleList.size()) {
			if (qlog.s.sentenceType==SentenceType.GeneralQuestion) //ask where����sparql ����Ҫ��fragment check
			{
				rankedSparqls.add(currentSpq.copy());
				return true;
			}
			
			CompatibilityChecker cc = new CompatibilityChecker(efd);
			try {
				//System.out.println("spq:"+currentSpq);  
				if (cc.isSparqlCompatible2(currentSpq)) {
					rankedSparqls.add(currentSpq.copy());
					return true;
				}					
			} catch (Exception e) {
				System.out.println("[CompatibilityChecker ERROR]"+currentSpq);
				e.printStackTrace();
			}
			return false;
		}
		
		Triple cur_t = originalSpq.tripleList.get(level);
		
		// һ���ʼ��˳����compatible��
		// ��ʼ��˳����preferred��
		currentSpq.addTriple(cur_t);
		boolean flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
		currentSpq.removeLastTriple();
		
		//�����ʼ˳����compatible�ģ���ô��Ҫ��Ҫ����ö�ٽ�������λ�õ�sparql?
		//if (flag) return flag;
		
		/*
		if (RelationFragment.isLiteral(cur_t.predicateID) || cur_t.predicateID == Globals.pd.typePredicateID) {	// �����������������Ҫ���Խ���swap
			return false;
		}
		*/
		//�����˼�� �����Խ�literal��ν�ʡ�������triple˳�� husen
		if (RelationFragment.isLiteral(cur_t.predicateID)  ) {
			return false;
		}
		//�����ǰtriple��predicate��type�Ļ�������Ҫö��Ҫ��Ҫ���type��Ϣ by hanshuo
		else if (cur_t.predicateID == Globals.pd.typePredicateID)
		{
			flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
			return flag;
		}
		else
		{		
			//ֻҪ�� �����Խ�literal��ν�ʡ�or��ν��=type�����͸���������������
			// swap����Ҫ���һ���Ƿ�compatible
			Triple swapped_t = cur_t.copySwap();
			swapped_t.score = swapped_t.score*0.8;
			if (isTripleCompatibleNotSwap(swapped_t)) {
				currentSpq.addTriple(swapped_t);
				flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
				currentSpq.removeLastTriple();
			}
			return flag;
		}
	}

}