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
import rdf.Pair;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.Triple;
import rdf.TypeMapping;

public class SemanticItemMapping {
	
	public HashMap<Word, ArrayList<EntityMapping>> entityDictionary = new HashMap<Word, ArrayList<EntityMapping>>();
	public static int k = 10;	// 目前没用到
	public static int t = 10;	// 单个node和relation枚举深度；例如2个triple共2条边3个点，最大复杂度为t^5，实际会更小因为只枚举实体节点
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
	
	public boolean isAnswerFound = false;	// 在不进行check时找到第一个SPARQL即标记为true，直接返回
	public int tripleCheckCallCnt = 0;
	public int sparqlCheckCallCnt = 0;
	public int sparqlCheckId = 0;
	
//	public HashSet<Pair> falseEntPres = new HashSet<Pair>();
//	public HashSet<Pair> falsePreEnts = new HashSet<Pair>();
//	public HashMap<Integer,Integer> srAssocitatedNode = new HashMap<Integer, Integer>();
	SemanticRelation firstFalseSr = null;
	long tripleCheckTime = 0;
	long sparqlCheckTime = 0;
	
	/*
	 * bottomUp method, 枚举所有可能的 query graph，通过 fragment check 后按得分排序
	 * 1、先不考虑有环
	 * */
	public void process_bottomUp(QueryLogger qlog, HashMap<Integer, SemanticRelation> semRltn) 
	{
		semanticRelations = semRltn;
		this.qlog = qlog;
		long t1 = 0;
		t = 10;	// 注意static的变量，这里每次手动初始化为10，不然一旦被带环图修改为5后就一直是5了

		entityPhrasesList.clear();
		entityWordList.clear();
		currentEntityMappings.clear();
		predicatePhraseList.clear();
		predicateSrList.clear();
		currentPredicateMappings.clear();
		
		// 注意【常量版type】也是常量的一种，但是不参与top-k，直接取分数最高的type - husen
		// 1. collect names of constant(entities), and map them to the entities
		t1 = System.currentTimeMillis();
		
		Iterator<Map.Entry<Integer, SemanticRelation>> it = semanticRelations.entrySet().iterator(); 
		int srId = 0;
        while(it.hasNext())
        {
            Map.Entry<Integer, SemanticRelation> entry = it.next();
            SemanticRelation sr = entry.getValue();
            
            //ent、type、literal视为常量
            //识别为常量但是 entity|type mapping为空，则抛弃这条triple
			//换句话说：parser识别出ner但pre阶段识别的mapping没有与之对应的，也可以在这里再次mapping试试，现在为了简便直接抛弃。即只认可pre阶段找到的mapping。
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
			
			//因为type而标记常量的情况在score and ranking中处理，这里只处理ent | 2016.5.2：subject以ent为优先
			if (sr.isArg1Constant && sr.arg1Word.mayEnt) 
			{
				//因为ent而标记常量
				if (!entityDictionary.containsKey(sr.arg1Word)) 
				{
					entityDictionary.put(sr.arg1Word, sr.arg1Word.emList);
				}
				entityPhrasesList.add(sr.arg1Word.emList);
				entityWordList.add(sr.arg1Word);
//				srAssocitatedNode.put(srId, entityWordList.size()-1);	// 注意目前认为一个sr至多对应一个constant，即不处理 <ent, p, ent>的情况
			}
			if (sr.isArg2Constant && !sr.arg2Word.mayType) 
			{	
				//因为ent而标记常量
				if (!entityDictionary.containsKey(sr.arg2Word)) 
				{
					entityDictionary.put(sr.arg2Word, sr.arg2Word.emList);
				}
				entityPhrasesList.add(sr.arg2Word.emList);
				entityWordList.add(sr.arg2Word);
//				srAssocitatedNode.put(srId, entityWordList.size()-1);	// 注意目前认为一个sr至多对应一个constant，即不处理 <ent, p, ent>的情况
			}
			
			srId++;
        }
		
		// 2. join
		t1 = System.currentTimeMillis();
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			predicatePhraseList.add(sr.predicateMappings);
			predicateSrList.add(sr);
			
			// 若需枚举结构，则枚举item的深度应该减小，不然正确结构也会由于得分排名过后
			if(Globals.evaluationMethod > 1 && !sr.isSteadyEdge)
				t = 5;
		}
//TODO 模拟bottom up的好处：111由于11而失败，则不会再尝试112、113等。		

		if(semanticRelations.size()>0)
		{
			topkJoin(semanticRelations);
		}
		else
		{
			System.out.println("No Valid SemanticRelations.");
		}
		qlog.timeTable.put("TopkJoin", (int)(System.currentTimeMillis()-t1));
		qlog.timeTable.put("TripleCheck", (int)tripleCheckTime);
		qlog.timeTable.put("SparqlCheck", (int)sparqlCheckTime);

		// 3. sort and rank
		Collections.sort(rankedSparqls);		
		
		//qlog.rankedSparqls = rankedSparqls;
		qlog.rankedSparqls.addAll(rankedSparqls);
		qlog.entityDictionary = entityDictionary;
		
		System.out.println("Check query graph count: " + tripleCheckCallCnt + "\nPass: " + sparqlCheckCallCnt);
		System.out.println("TopkJoin time=" + qlog.timeTable.get("TopkJoin"));
	}
	
	/*
	 * top-down method, 枚举所有可能的 query graph，通过 fragment check 后按得分排序
	 * */
	public void process_topDown(QueryLogger qlog, HashMap<Integer, SemanticRelation> semRltn) 
	{
		semanticRelations = semRltn;
		this.qlog = qlog;
		long t1 = 0;
		t = 10;	// 注意static的变量，这里每次手动初始化为10，不然一旦被带环图修改为5后就一直是5了

		entityPhrasesList.clear();
		entityWordList.clear();
		currentEntityMappings.clear();
		predicatePhraseList.clear();
		predicateSrList.clear();
		currentPredicateMappings.clear();
		
		// 注意【常量版type】也是常量的一种，但是不参与top-k，直接取分数最高的type - husen
		// 1. collect names of constant(entities), and map them to the entities
		t1 = System.currentTimeMillis();
		
		Iterator<Map.Entry<Integer, SemanticRelation>> it = semanticRelations.entrySet().iterator(); 
        while(it.hasNext())
        {
            Map.Entry<Integer, SemanticRelation> entry = it.next();
            SemanticRelation sr = entry.getValue();
            
            //ent、type、literal视为常量
            //识别为常量但是 entity|type mapping为空，则抛弃这条triple
			//换句话说：parser识别出ner但pre阶段识别的mapping没有与之对应的，也可以在这里再次mapping试试，现在为了简便直接抛弃。即只认可pre阶段找到的mapping。
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
			
			//因为type而标记常量的情况在score and ranking中处理，这里只处理ent | 2016.5.2：subject以ent为优先
			if (sr.isArg1Constant && sr.arg1Word.mayEnt) 
			{
				//因为ent而标记常量
				if (!entityDictionary.containsKey(sr.arg1Word)) 
				{
					entityDictionary.put(sr.arg1Word, sr.arg1Word.emList);
				}
				entityPhrasesList.add(sr.arg1Word.emList);
				entityWordList.add(sr.arg1Word);
			}
			if (sr.isArg2Constant && !sr.arg2Word.mayType) 
			{	
				//因为ent而标记常量
				if (!entityDictionary.containsKey(sr.arg2Word)) 
				{
					entityDictionary.put(sr.arg2Word, sr.arg2Word.emList);
				}
				entityPhrasesList.add(sr.arg2Word.emList);
				entityWordList.add(sr.arg2Word);
			}
        }
		
		// 2. join
		t1 = System.currentTimeMillis();
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			predicatePhraseList.add(sr.predicateMappings);
			predicateSrList.add(sr);
			
			// 若需枚举结构，则枚举item的深度应该减小，不然正确结构也会由于得分排名过后
			if(Globals.evaluationMethod > 1 && !sr.isSteadyEdge)
				t = 5;
		}
		if(semanticRelations.size()>0)
		{
			topkJoin(semanticRelations);
		}
		else
		{
			System.out.println("No Valid SemanticRelations.");
		}
		qlog.timeTable.put("TopkJoin", (int)(System.currentTimeMillis()-t1));
		qlog.timeTable.put("TripleCheck", (int)tripleCheckTime);
		qlog.timeTable.put("SparqlCheck", (int)sparqlCheckTime);

		// 3. sort and rank
		Collections.sort(rankedSparqls);		
		
		//qlog.rankedSparqls = rankedSparqls;
		qlog.rankedSparqls.addAll(rankedSparqls);
		qlog.entityDictionary = entityDictionary;
		
		System.out.println("Check query graph count: " + tripleCheckCallCnt + "\nPass: " + sparqlCheckCallCnt);
		System.out.println("TopkJoin time=" + qlog.timeTable.get("TopkJoin"));
	}
	
	public ArrayList<EntityMapping> getEntityIDsAndNames(Word entity, QueryLogger qlog) {
		if (entityDictionary.containsKey(entity)) {
			return entityDictionary.get(entity);
		}
		
		//这里 n实际上就是原始形式，是用 "_" 分割的
		String n = entity.getFullEntityName();
		System.out.println(n+" " + preferDBpediaLookupOrLucene(n));
		ArrayList<EntityMapping> ret= new ArrayList<EntityMapping>();
		
		if (preferDBpediaLookupOrLucene(n) == 1) { // 判断使用DBpediaLookup优先还是Lucene
			
			ret.addAll(Globals.dblk.getEntityMappings(n, qlog));
			//如果使用Lucene得分较高（匹配度极高），也应该优先用Lucene
			ret.addAll(EntityFragment.getEntityMappingList(n));
		}
		else{//否则采用Lucene获得实体ID
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
	
	//一个 level 对应一个 word+[该word对应的entities]
	public void dfs_entityName (int level_i) 
	{
		//此时entities都放好了
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
				
				//之前确定的某对e,p不匹配，这一层p再怎么换也没用
				if(Globals.evaluationMethod == 3 && firstFalseSr != null)
				{
					if(firstFalseSr != sr)
						return;
					else
						firstFalseSr = null;
				}

			}
			
			// 若需枚举结构，则枚举predicate和node的深度应该减小，不然正确结构也会由于得分排名过后
			if(Globals.evaluationMethod == 2 && sr.isSteadyEdge == false)
			{
				currentPredicateMappings.put(sr.hashCode(), null);
				dfs_predicate(level_i+1);
				currentPredicateMappings.remove(sr.hashCode());
				tcount++;
			}
		}
	}

	/*
	 * 运行这个函数证明对于每个需要组合的元素都已选择一个确定的值；（通过currentEntityMappings、currentPredicateMappings）
	 * 即predicate、entity都已确定，在这里1、根据确定的ent和p以及一些相关性信息生成一组sparql，然后进行碎片匹配（注意碎片检测有两轮），如果通过则评分并保留
	 * 注意：在这里加入embedded type信息：
	 * 例如：为?who <height> ?how 补充  ?who <type1> <Person> 或者 ?book <author> <Tom> 补充 ?book <type1> <Book>
	 * 注意：在这里加入constant type信息：
	 * 例如：ask: <YaoMing> <type1> <BasketballPlayer> 
	 * 注意：在这里加入embedded triple信息：
	 * 例如：为 ?Canadians <residence> <Unitied_State> 补充 ?Canadians <birthPlace> <Canada>
	 * */
	public void scoringAndRanking() 
	{		
		firstFalseSr = null;
		Sparql sparql = new Sparql(semanticRelations);

		// 检验查询图是否连通，为方便，这里认为存在一条边的两个node都只出现一次(只有一条边时例外)即为不连通；只在node数小于6时正确（充分不必要）
		// 点数减少了也不行
		HashMap<Integer, Integer> count = new HashMap<Integer, Integer>();
		int edgeCnt = 0;
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			if(currentPredicateMappings.get(sr.hashCode()) == null)
				continue;
			
			edgeCnt++;
			int v1 = sr.arg1Word.hashCode(), v2 = sr.arg2Word.hashCode();
			if(!count.containsKey(v1))
				count.put(v1, 1);
			else
			{
				count.put(v1, count.get(v1)+1);
			}
			if(!count.containsKey(v2))
				count.put(v2, 1);
			else
			{
				count.put(v2, count.get(v2)+1);
			}
		}
		if(count.size() < qlog.semanticUnitList.size())
			return;
		if(edgeCnt == 0)
			return;
		if(edgeCnt > 1)
		{
			for (Integer key : semanticRelations.keySet()) 
			{
				SemanticRelation sr = semanticRelations.get(key);
				if(currentPredicateMappings.get(sr.hashCode()) == null)
					continue;
				int v1 = sr.arg1Word.hashCode(), v2 = sr.arg2Word.hashCode();
				if(count.get(v1) == 1 && count.get(v2) == 1)
					return;
			}
		}
		
		HashSet<String> typeSetFlag = new HashSet<String>();	// 保证每个变量的type1只在sparql中出现一次 |这个没用到啊
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			String sub, obj;
			int subjId = -1, objId = -1;
			int pid;
			double score = 1;
			boolean isSubjObjOrderSameWithSemRltn=true; //实际没有用到这个变量
			
// argument1 | 如果(sr.arg1Word.mayEnt || sr.arg1Word.mayType)=true但是sr.isArg1Constant=false，意味着 这是一个 变量版“type”；例如“the book of ..”中的book
			if(sr.isArg1Constant && (sr.arg1Word.mayEnt || sr.arg1Word.mayType) ) 
			{
				//2016.5.2：对于subj，还是ent优先
				if(sr.arg1Word.mayEnt)
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
					score *= (tm.score*100);	//entity评分是满分100(虽然最高只见过50多)，而type评分是满分1，所以这里乘以100
				}
			}
			else 
			{
				subjId = Triple.VAR_ROLE_ID;
				sub = "?" + sr.arg1Word.originalForm;
			}
			// argument1自身蕴含的type信息，例如 ?book <type> <Book>
			// 注意，mayType和mayExtendVariable不能共存（在constantVariableRecognition中的处理保证了这一点）
			Triple subt = null;
			//例如“Is Yao Ming a basketball player?”，basketball player被识别常量“type”，则不需要多一条三元组（basketball player <type> basketball player）
			//而对于应该出现的 <Yao Ming> <type> <basketball player>，在“识别常量/变量/extend“时，就已经把”type“作为preferred relation加入对应sr的predicate mappings。
			//这里限制sr.arg1Word.mayType=true,那么?who,?where等的type信息就不会加入到sparql
			if (!sr.isArg1Constant && sr.arg1Word.mayType && sr.arg1Word.tmList != null && sr.arg1Word.tmList.size() > 0 && !typeSetFlag.contains(sub)) 
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
				
				//即享受type待遇，但不应该出现在sparql中
				if(sr.arg1Word.tmList.get(0).prefferdRelation == -1)
					subt = null;
			}
// predicate
			SemanticRelation dep = sr.dependOnSemanticRelation;
			PredicateMapping pm = null; 
			if (dep == null) 
			{
				pm = currentPredicateMappings.get(sr.hashCode());
			}
			else 
			{
				pm = currentPredicateMappings.get(dep.hashCode());
			}
			// 当前边的mapping为null，则放弃该条semantic relation
			if(pm == null)
			{
				continue;
			}
			pid = pm.pid;
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
					score *= (tm.score*100);	//entity评分是满分100(虽然最高只见过50多)，而type评分是满分1，所以这里乘以100
				}
			}
			else 
			{
				objId = Triple.VAR_ROLE_ID;
				obj = "?" + sr.arg2Word.getFullEntityName();
			}
			// argument2的type信息
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
			
			
			// 对literal属性的处理 （好像是看如果predicate后面只能接literal）
			if (RelationFragment.isLiteral(pid) && (subt != null || objt != null)) 
			{
				// 如果有实体，则实体必在前面				
				if (sub.startsWith("?") && obj.startsWith("?")) {
					// 现在两个都是变量，即都可能是在obj位置做literal，所以在type后面都加literal
					if (subt != null) {
						subt.object += ("|" + "literal_HRZ");
					}
					if (objt != null) {
						objt.object += ("|" + "literal_HRZ");
					}
					
					if (subt==null && objt!=null) {						
						//若obj有type，sub无type则更有可能交换sub和obj的位置，因为literal一般没有type【但是可能会有yago：type】
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
					// 需要交换subj/obj的顺序
					// 但是subjt/objt的顺序可以不交换，因为反正都是要添加进sparql中
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
			
			
			
			// 为了处理一般疑问句，这里应该先不对triple做isTripleCompatibleCanSwap的检查，放在后面做 
			// 等形成sparql之后，再做检查，复杂度不变
			
			Triple t = new Triple(subjId, sub, pid, objId, obj, sr, score,isSubjObjOrderSameWithSemRltn);		
			//System.out.println("triple: "+t+" "+isTripleCompatibleCanSwap(t));
			
			sparql.addTriple(t);
			
			//对于subject和object的type信息的分数，要与triple本身的分数相关
			if (subt != null) 
			{
				subt.score += t.score*0.2;
				sparql.addTriple(subt);
				typeSetFlag.add(subt.subject);//小心，不能用sub，因为在处理literal边的时候，可能subj/obj顺序交换
			}
			if (objt != null) 
			{
				objt.score += t.score*0.2;
				sparql.addTriple(objt);
				typeSetFlag.add(objt.subject);
			}
			
			// 加入argument自身蕴含的triple信息，例如  ?canadian	<birthPlace>	<Canada>
			if(!sr.isArg1Constant && sr.arg1Word.mayExtendVariable && sr.arg1Word.embbededTriple != null)
			{
				sparql.addTriple(sr.arg1Word.embbededTriple);
			}
			if(!sr.isArg2Constant && sr.arg2Word.mayExtendVariable && sr.arg2Word.embbededTriple != null)
			{
				sparql.addTriple(sr.arg2Word.embbededTriple);
			}
		}
		
		if (!qlog.MODE_fragment) {
			// 方法一：不进行complete compatibility check
			 rankedSparqls.add(sparql);
			 isAnswerFound = true;
		}
		else {
			// 方法三：进行complete compatibility check，并且枚举subj/obj顺序 
			sparql.typesComesFirst();			

			tripleCheckCallCnt++;
			long t1 = System.currentTimeMillis();
			//single-triple check
			//这里isTripleCompatibleCanSwap就是判断triple是否符合碎片，可以交换subj和obj，如果有一条不满足，直接return（注意这里只是第一层检验，通过后还要在enumerateEubjObjOrders函数里进行第二步检验）  
			for (Triple t : sparql.tripleList)				
				if(t.predicateID!=Globals.pd.typePredicateID && !isTripleCompatibleCanSwap(t))
				{
					firstFalseSr = t.semRltn;
					return;
				}
			tripleCheckTime += (System.currentTimeMillis()-t1);	
			
			t1 = System.currentTimeMillis();
			sparqlCheckCallCnt++;
			//System.out.println("spq: "+sparql+" n:"+sparql.getVariableNumber());
			enumerateSubjObjOrders(sparql, new Sparql(sparql.semanticRelations), 0);	
			sparqlCheckTime += (System.currentTimeMillis()-t1);
		}
		
	}
	
	/*
	 * 注意：
	 * typeId=-1说明没有相关图碎片，该type不能作为碎片检查时的依据
	 * */
	TypeFragment getTypeFragmentByWord(Word word)
	{
		TypeFragment tf = null;
		//extendType 的 id=-1，因为目前KB/fragment中没有extendType的数据，所以id=-1则不提取typeFragment
		if(word.tmList!=null && word.tmList.size()>0)
		{
			int typeId = word.tmList.get(0).typeID;
			if(typeId != -1)
				tf = TypeFragment.typeFragments.get(typeId);
		}
		return tf;
	}
	
	/*
	 * （这个函数只作为”初步检测“，在后面enumerateSubjObjOrders中会进一步详细检测，后面运用了”predicate的前后【ent集合的type】信息“）2016.4.6
	 * 注意，predicate = type的三元组不会进入这个函数
	 * 这个函数只是分别check每条triple，没有结合成整体check
	 * 
	 * 2016-4-28：改为以entity id作为key，而不是原来的entity name
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
			//变量，变量
			if(t.subject.startsWith("?") && t.object.startsWith("?"))
			{
				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
				
				//根据两个变量type fragment的出入边是否包含predicate，计算是否需要调换顺序以及该triple是否能够成立
				//计算方法为简单的投票看哪个更好
				int nowOrderCnt = 0, reverseOrderCnt = 0;
				if(subjTf == null || subjTf.outEdges.contains(t.predicateID))
					nowOrderCnt ++;
				if(objTf == null || objTf.inEdges.contains(t.predicateID))
					nowOrderCnt ++;
				if(subjTf == null || subjTf.inEdges.contains(t.predicateID))
					reverseOrderCnt ++;
				if(objTf == null || objTf.outEdges.contains(t.predicateID))
					reverseOrderCnt ++;
				
				if(nowOrderCnt<2 && reverseOrderCnt<2)
					return false;
				
				else if(nowOrderCnt > reverseOrderCnt)
				{
					// 顺序不变
					//return true;
				}
				else if(reverseOrderCnt > nowOrderCnt)
				{
					//TODO:是否需要一个变量表示以后不应该再改变这条t的顺序?
					t.swapSubjObjOrder();
					//return true;
				}
				else	//now order和reverse order都通过了type fragment检测，需要选择一个
				{
					//rule1: ?inventor <occupation> ?occupation || ... <name> ?name， 即字符串越像的放在后边
					String p = Globals.pd.getPredicateById(t.predicateID);
					int ed1 = EntityFragment.calEditDistance(subjWord.baseForm, p);
					int ed2 = EntityFragment.calEditDistance(objWord.baseForm, p);
					if(ed1 < ed2)
					{
						t.swapSubjObjOrder();
					}
				}
				return true;
			}
//			//TODO:变量，实体 || 这种情况只依据ent一般就可以，先看一下效果  || ?city	<type1>	<City> 、<Chile_Route_68>	<country>	?city ；像这种，<country>对city不合法，可以在这里过滤，不过要看一下是否会出现”误伤“
//			else if(t.subject.startsWith("?") || t.object.startsWith("?"))
//			{
//				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
//				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
//				
//				
//			}
			//实体，实体 || 变量，实体
			else
			{
				if (fragmentCompatible(t.subjId, t.predicateID, t.objId)) {			
					return true;
				}			
				if (fragmentCompatible(t.objId, t.predicateID, t.subjId)) {			
					t.swapSubjObjOrder();
					return true;
				}	
				
				//记录check失败的原因
				
				return false;
			}
		
		}
	}

	/*//旧版本备份	
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
			//变量，变量
			if(t.subject.startsWith("?") && t.object.startsWith("?"))
			{
				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
				
				//按现在的顺序试一下
				boolean ok = true;
				if((subjTf != null && !subjTf.outEdges.contains(t.predicateID)) || (objTf != null && !objTf.inEdges.contains(t.predicateID)))
				{
					ok = false;
				}
				//交换顺序试一下
				if(!ok)
				{
					if((subjTf == null || subjTf.inEdges.contains(t.predicateID)) && (objTf == null || objTf.outEdges.contains(t.predicateID)))
					{	
						ok = true;
						t.swapSubjObjOrder();
						//TODO:是否需要一个变量表示以后不应该再改变这条t的顺序?
					}
				}
				
				return ok;
			}
//			//TODO:变量，实体 || 这种情况只依据ent一般就可以，先看一下效果  || ?city	<type1>	<City> 、<Chile_Route_68>	<country>	?city ；像这种，<country>对city不合法，可以在这里过滤，不过要看一下是否会出现”误伤“
//			else if(t.subject.startsWith("?") || t.object.startsWith("?"))
//			{
//				Word subjWord = t.getSubjectWord(), objWord = t.getObjectWord();
//				TypeFragment subjTf = getTypeFragmentByWord(subjWord), objTf = getTypeFragmentByWord(objWord);
//				
//				
//			}
			//实体，实体 || 变量，实体
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
	
		// 是合法的entity就必有fragment，这里认为DBpediaLookup返回的ent都会在离线数据中出现
		if (id1!=Triple.TYPE_ROLE_ID && id1!=Triple.VAR_ROLE_ID && ef1 == null) return false;
		if (id2!=Triple.TYPE_ROLE_ID && id2!=Triple.VAR_ROLE_ID && ef2 == null) return false;
		
		boolean ef1_constant = (ef1==null)?false:true;
		boolean ef2_constant = (ef2==null)?false:true;
		int entityCnt=0,compatibleCnt=0;
		if(ef1_constant) {
			entityCnt++;
			if (ef1.outEdges.contains(pid))
				compatibleCnt++;
//			else	// <e1,p> 为 false pair
//			{
//				falseEntPres.add(new Pair(id1,pid));
//			}
		}
		
		if (ef2_constant) {
			entityCnt++;
			if (ef2.inEdges.contains(pid))
				compatibleCnt++;
//			else	// <p,e2> 为false pair
//			{
//				falsePreEnts.add(new Pair(pid,id2));
//			}
		}
		
		//对于select型sparql，要严格保证predicate与subject和object的匹配；而ask型可以放宽
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)	
			return entityCnt-compatibleCnt<=1;		
		else		
			return entityCnt==compatibleCnt;
		
	}
	
	//旧版backup
//	public boolean fragmentCompatible (String en1, int pid, String en2) {
//		EntityFragment ef1 = efd.getEntityFragmentByName(en1);
//		EntityFragment ef2 = efd.getEntityFragmentByName(en2);
//	
//		// 是合法的entity就必有fragment
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
//		//对于select型sparql，要严格保证predicate与subject和object的匹配；而ask型可以放宽
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
	
	//旧版本backup
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
	
	// 枚举subject/object顺序 ，进一步进行 fragment check
	public boolean enumerateSubjObjOrders (Sparql originalSpq, Sparql currentSpq, int level) 
	{
		if (level == originalSpq.tripleList.size()) {
			if (qlog.s.sentenceType==SentenceType.GeneralQuestion) //ask where类型sparql 不需要做fragment check
			{
				rankedSparqls.add(currentSpq.copy());
				return true;
			}
			
			CompatibilityChecker cc = new CompatibilityChecker(efd);
			try 
			{
				sparqlCheckId++;
				long t1 = System.currentTimeMillis();
				if (cc.isSparqlCompatible2(currentSpq)) 
				{
					qlog.fw.write( "spq-check " + sparqlCheckId + "; [true]; time: "+ (int)(System.currentTimeMillis()-t1) + "\n");
					//System.out.println("spq-check " + sparqlCheckId + "; [true]; time: "+ (int)(System.currentTimeMillis()-t1));
					rankedSparqls.add(currentSpq.copy());
					return true;
				}
				qlog.fw.write( "spq-check " + sparqlCheckId + "; [false]; time: "+ (int)(System.currentTimeMillis()-t1) + "\n");
				//System.out.println("spq-check " + sparqlCheckId + "; [false]; time: "+ (int)(System.currentTimeMillis()-t1));
			} 
			catch (Exception e) {
				System.out.println("[CompatibilityChecker ERROR]"+currentSpq);
				e.printStackTrace();
			}
			return false;
		}
		
		Triple cur_t = originalSpq.tripleList.get(level);
		
		// 先试一发初始顺序，一般初始的顺序是compatible的
		// 初始的顺序是preferred的
		currentSpq.addTriple(cur_t);
		boolean flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
		currentSpq.removeLastTriple();
		
		//如果初始顺序是compatible的，那么还要不要继续枚举交换主宾位置的sparql?
		//if (flag) return flag;
		
		/*
		if (RelationFragment.isLiteral(cur_t.predicateID) || cur_t.predicateID == Globals.pd.typePredicateID) {	// 这两种情况根本不需要尝试进行swap
			return false;
		}
		*/
		//这个意思是 【可以接literal的谓词】不交换triple顺序？ husen
		if (RelationFragment.isLiteral(cur_t.predicateID)  ) {
			return false;
		}
		//如果当前triple的predicate是type的话，还需要枚举要不要这个type信息 by hanshuo
		else if (cur_t.predicateID == Globals.pd.typePredicateID)
		{
			flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
			return flag;
		}
		else
		{		
			// 注意，只要是 【可以接literal的谓词】or【谓词=type】，就根本进不了这块代码，也就无法 调换subj和obj
			// swap后，需要检查一遍是否compatible
			Triple swapped_t = cur_t.copySwap();
			swapped_t.score = swapped_t.score*0.8;
			if (isTripleCompatibleNotSwap(swapped_t)) // 调换subj、obj后，该triple能否通过single triple check
			{
				currentSpq.addTriple(swapped_t);
				flag = enumerateSubjObjOrders(originalSpq, currentSpq, level+1);
				currentSpq.removeLastTriple();
			}
			return flag;
		}
	}

}
