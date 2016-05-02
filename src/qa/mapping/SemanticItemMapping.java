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
	
	public boolean isAnswerFound = false;	// TODO 这是干嘛的？
	
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
		
		// 注意【常量版type】也是常量的一种，但是不参与top-k，直接取分数最高的type husen
		// 1. collect names of constant(entities), and map them to the entities
		t = System.currentTimeMillis();
		
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
	 * */
	public void scoringAndRanking() 
	{			
		Sparql sparql = new Sparql(semanticRelations);

		HashSet<String> typeSetFlag = new HashSet<String>();	// 保证每个变量的type1只在sparql中出现一次 |这个没用到啊
		for (Integer key : semanticRelations.keySet()) 
		{
			SemanticRelation sr = semanticRelations.get(key);
			String sub, obj;
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
					sub = em.entityID;
					score *= em.score;
				}
				else
				{
					TypeMapping tm = sr.arg1Word.tmList.get(0);
					sub = tm.typeName;
					score *= (tm.score*100);	//entity评分是满分100(虽然最高只见过50多)，而type评分是满分1，所以这里乘以100
				}
			}
			else 
			{
				sub = "?" + sr.arg1Word.originalForm;
			}
			// argument1的type信息
			Triple subt = null;
			//例如“Is Yao Ming a basketball player?”，basketball player被识别常量“type”，则不需要多一条三元组（basketball player <type> basketball player）
			//而对于应该出现的 <Yao Ming> <type> <basketball player>，之后再做考虑
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
				subt = new Triple(sub, Globals.pd.typePredicateID, ttt, null, 10);
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
					obj = em.entityID;
					score *= em.score;
				}
				else
				{
					TypeMapping tm = sr.arg2Word.tmList.get(0);
					obj = tm.typeName;
					score *= (tm.score*100);	//entity评分是满分100(虽然最高只见过50多)，而type评分是满分1，所以这里乘以100
				}
			}
			else 
			{
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
				objt = new Triple(obj, Globals.pd.typePredicateID, ttt, null, 10);
				objt.typeSubjectWord = sr.arg2Word;
				
				if(sr.arg2Word.tmList.get(0).prefferdRelation == -1)
					objt = null;
			}
			
			
			// 对literal属性的处理 （好像是看如果predicate后面只能接literal）
			if (RelationFragment.isLiteral(pid) && (subt != null || objt != null)) 
			{
				// 如果有实体，则实体必在前面				
				if (sub.startsWith("?") && obj.startsWith("?")) {
					// 在type后面加literal？
					if (subt != null) {
						subt.object += ("|" + "literal_HRZ");
					}
					if (objt != null) {
						objt.object += ("|" + "literal_HRZ");
					}
					
					if (subt==null && objt!=null) {						
						//若obj有type，sub无type则更有可能交换sub和obj的位置
						String temp = sub;
						sub = obj;
						obj = temp;
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
					sub = obj;
					obj = temp;
					isSubjObjOrderSameWithSemRltn=!isSubjObjOrderSameWithSemRltn;
					//System.out.println("here: "+sub+obj);
					
				}
				else if (obj.startsWith("?") && !sub.startsWith("?")) {
					if (objt != null) {
						objt.object += ("|" + "literal_HRZ");
					}				
				}
			}
			
			
			
			// 初步检查Triple是否是compatible的，如果不是，直接返回（这也算是一个剪枝吧） banned!
			// 为了处理一般疑问句，这里应该先不对triple做isTripleCompatibleCanSwap的检查，放在后面做
			// 等形成sparql之后，再做检查，复杂度不变
			
			Triple t = new Triple(sub, pid, obj, sr, score,isSubjObjOrderSameWithSemRltn);		
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
		}
		
		if (!qlog.MODE_fragment) {
			// 方法一：不进行complete compatibility check
			 rankedSparqls.add(sparql);
			 isAnswerFound = true;
		}
		else {
			// 方法三：进行complete compatibility check，并且枚举subj/obj顺序 
			sparql.typesComesFirst();			

			//这里isTripleCompatibleCanSwap就是判断triple是否符合碎片，可以交换subj和obj，如果有一条不满足，直接return（注意这里只是第一层检验，通过后还要在enumerateEubjObjOrders函数里进行第二步检验）
			for (Triple t : sparql.tripleList)				
				if(t.predicateID!=Globals.pd.typePredicateID && !isTripleCompatibleCanSwap(t))
					return;
			
			//System.out.println("spq: "+sparql+" n:"+sparql.getVariableNumber());
			enumerateSubjObjOrders(sparql, new Sparql(sparql.semanticRelations), 0);						
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
	 * （发现原版本这个函数只作为”初步检测“，在后面enumerateSubjObjOrders中会进一步详细检测，后面运用了”predicate的前后【ent集合的type】信息“）2016.4.6
	 * 注意，predicate = type的三元组不会进入这个函数
	 * 这个函数要加入一些操作：
	 * 1、判断时考虑type信息
	 * */
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
	
	public boolean isTripleCompatibleNotSwap (Triple t) {
		if (t.predicateID == Globals.pd.typePredicateID) {
			return true;
		}
		else if (fragmentCompatible(t.subject, t.predicateID, t.object)) {
			return true;
		}
		else {
			return false;
		}
	}

	public boolean fragmentCompatible (String en1, int pid, String en2) {
		EntityFragment ef1 = efd.getEntityFragmentByName(en1);
		EntityFragment ef2 = efd.getEntityFragmentByName(en2);
	
		// 是合法的entity就必有fragment
		if (!en1.startsWith("?") && ef1 == null) return false;
		if (!en2.startsWith("?") && ef2 == null) return false;
		
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
		
		//对于select型sparql，要严格保证predicate与subject和object的匹配；而ask型可以放宽
		if (qlog.s.sentenceType==SentenceType.GeneralQuestion)	
			return entityCnt-compatibleCnt<=1;		
		else		
			return entityCnt==compatibleCnt;
		
	}
	
	public int fragmentCompatible2 (String en1, int pid, String en2) {
		EntityFragment ef1 = efd.getEntityFragmentByName(en1);
		EntityFragment ef2 = efd.getEntityFragmentByName(en2);	

		int entityCnt=0,compatibleCnt=0;
		if(!en1.startsWith("?")) {
			entityCnt++;
			if (ef1!=null && ef1.outEdges.contains(pid))
				compatibleCnt++;
		}
		
		if (!en2.startsWith("?")) {
			entityCnt++;
			if (ef2!=null && ef2.inEdges.contains(pid))
				compatibleCnt++;
		}		

		return entityCnt-compatibleCnt;
	}
		
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
	
	// 枚举subject/object顺序  || 这个函数有必要吗？
	public boolean enumerateSubjObjOrders (Sparql originalSpq, Sparql currentSpq, int level) {
		if (level == originalSpq.tripleList.size()) {
			if (qlog.s.sentenceType==SentenceType.GeneralQuestion) //ask where类型sparql 不需要做fragment check
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
		
		// 一般初始的顺序是compatible的
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
			//只要是 【可以接literal的谓词】or【谓词=type】，就根本进不了这块代码
			// swap后，需要检查一遍是否compatible
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
