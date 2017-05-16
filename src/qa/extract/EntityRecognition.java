package qa.extract;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

import lcn.EntityFragmentFields;
import fgmt.EntityFragment;
import nlp.ds.Word;
import qa.Globals;
import rdf.EntityMapping;
import rdf.NodeSelectedWithScore;
import rdf.TypeMapping;
import rdf.MergedWord;
import addition.*;

/**
 * 主要为Node Recognition相关功能
 * @author husen
 */
public class EntityRecognition {
	public String preLog = "";
	public String stopEntFile = Globals.localPath + "data/DBpedia2014/parapharse/stopEntDict.txt";
	
	double EntAcceptedScore = 26;
	double TypeAcceptedScore = 0.5;
	double AcceptedDiffScore = 1;
	
	public HashMap<String, String> m2e = null;
	public ArrayList<MergedWord> mWordList = null;
	public ArrayList<String> stopEntList = null;
	public ArrayList<String> badTagListForEntAndType = null;
	ArrayList<ArrayList<Integer>> selectedList = null;
	
	AddtionalFix af = null;
	
	public EntityRecognition() 
	{
		// 清空日志
		preLog = "";
		loadStopEntityDict();
		
		// Bad posTag for entity
		badTagListForEntAndType = new ArrayList<String>();
		badTagListForEntAndType.add("RBS");
		badTagListForEntAndType.add("JJS");
		badTagListForEntAndType.add("W");
		badTagListForEntAndType.add(".");
		badTagListForEntAndType.add("VBD");
		badTagListForEntAndType.add("VBN");
		badTagListForEntAndType.add("VBZ");
		badTagListForEntAndType.add("VBP");
		badTagListForEntAndType.add("POS");
		
		// Handwrite entity linking; (lower case)
		m2e = new HashMap<String, String>();
		m2e.put("bipolar_syndrome", "Bipolar_disorder");
		m2e.put("battle_in_1836_in_san_antonio", "Battle_of_San_Jacinto");
		
		// additional fix for CATEGORY
		af = new AddtionalFix();
		
		System.out.println("EntityRecognizer Initial : ok!");
	}
	
	public void loadStopEntityDict()
	{
		stopEntList = new ArrayList<String>();
		
		try 
		{
			File file = new File(stopEntFile);
			InputStreamReader in = new InputStreamReader(new FileInputStream(file), "utf-8");
			BufferedReader br = new BufferedReader(in);

			String line = null;
			
			while ((line = br.readLine())!= null) 
			{
				if(line.startsWith("#"))
					continue;
				stopEntList.add(line);
			}	
			br.close();
			
		} catch (Exception e) {
			e.printStackTrace();
		}
	}
	
	public ArrayList<String> process(String question)
	{
		TypeRecognition tr = new TypeRecognition();
		ArrayList<String> fixedQuestionList = new ArrayList<String>();
		ArrayList<Integer> literalList = new ArrayList<Integer>();
		HashMap<Integer, Double> entityScores = new HashMap<Integer, Double>();
		HashMap<Integer, Integer> entityMappings = new HashMap<Integer, Integer>();
		HashMap<Integer, Double> typeScores = new HashMap<Integer, Double>();
		HashMap<Integer, String> typeMappings = new HashMap<Integer, String>();
		HashMap<Integer, Double> mappingScores = new HashMap<Integer, Double>();
		ArrayList<Integer> mustSelectedList = new ArrayList<Integer>();
		
		System.out.println("--------- entity/type recognition start ---------");
		
		Word[] words = Globals.coreNLP.getTaggedWords(question);
		mWordList = new ArrayList<MergedWord>();
		
		long t1 = System.currentTimeMillis();
		//这里的hit是指通过lucene或者dbpedia lookup找到了结果，但不保证结果正确（事实上有很多时候是错误结果，例如 than 都有对应的 实体，显然是不对的） 
		int checkEntCnt = 0, checkTypeCnt = 0, hitEntCnt = 0, hitTypeCnt = 0, allCnt = 0, hitLuceneEntCnt = 0;
		boolean needRemoveCommas = false;
		
		//check entity & type，之后看要不要分开检测 
		//注意len由小到大的顺序不能变，因为一些长ent是否保留或得分策略依赖于短ent的识别情况
		for(int len=1;len<=words.length;len++)
		{
			for(int st=0,ed=st+len; ed<=words.length; st++,ed++)
			{
				String originalWord = "",baseWord = "", allUpperWord = "", posTagSequence = "";
				String[] posTagArr = new String[len];
				for(int j=st;j<ed;j++)
				{
					posTagArr[j-st] = words[j].posTag;
					posTagSequence += words[j].posTag;
					originalWord += words[j].originalForm;
					baseWord += words[j].baseForm;
					String tmp = words[j].originalForm;
					if(tmp.length()>0 && tmp.charAt(0)>='a' && tmp.charAt(0)<='z')
					{
						String pre = tmp.substring(0,1).toUpperCase();
						tmp = pre + tmp.substring(1);
					}
					allUpperWord += tmp;
					
					if(j < ed-1)
					{
						originalWord += "_";
						baseWord += "_";
						posTagSequence += " ";
					}
				}
				allCnt++;
				
/*
 * 加一些规则减少find次数，但是这些规则可能牺牲某些entity 
 * 一系列经过不断增加和修正的手写规则
*/				
				boolean entOmit = false, typeOmit = false;
				int prep_cnt=0;
				
				//如果这一串word都是大写字母开头（符号如果在两个word中间，则也算作大写，eg："Melbourne , Florida"），则必定做mapping，否则进行规则判断是否做mapping 
				int UpperWordCnt = 0;
				for(int i=st;i<ed;i++)
					if((words[i].originalForm.charAt(0)>='A' && words[i].originalForm.charAt(0)<='Z') || ((words[i].posTag.equals(",") || words[i].originalForm.equals("'")) && i>st && i<ed-1))
						UpperWordCnt++;
				
				//如果符合一些 "基本不可能用作entity"的规则，则不进行ent检测 
				if(UpperWordCnt<len || st==0)
				{
					if(st==0)
					{
						if(!words[st].posTag.startsWith("DT") && !words[st].posTag.startsWith("N"))
						{
							entOmit = true;
							typeOmit = true;
						}
					}
					
					//这些rule需要判断上一个词，所以要求st大于0；注意这些rule只针对ent 
					if(st>0)
					{
						Word formerWord = words[st-1];
						//as princess
						if(formerWord.baseForm.equals("as"))
							entOmit = true;
						//how many dogs?
						if(formerWord.baseForm.equals("many"))
							entOmit = true;
						
						//obama's daughter ; your height | 2016.5.2,增加len=1限制，因为Asimov's Foundation series这种会识别错
						if(len == 1 && (formerWord.posTag.startsWith("POS") || formerWord.posTag.startsWith("PRP")))
							entOmit = true;
						//the father of you
						if(ed<words.length)
						{
							Word nextWord = words[ed];
							if(formerWord.posTag.equals("DT") && nextWord.posTag.equals("IN"))
								entOmit = true;
						}
						//the area code of ; the official language of
						boolean flag1=false,flag2=false;
						for(int i=0;i<=st;i++)
							if(words[i].posTag.equals("DT"))
								flag1 = true;
						for(int i=ed-1;i<words.length;i++)
							if(words[i].posTag.equals("IN"))
								flag2 = true;
						if(flag1 && flag2)
							entOmit = true;
						
					//	//如果前面一个词不是句首且是大写，就认为前面的词是一个实体（或实体的前缀），那么当前词是他的后缀或者它之后的词。而我们认为不会有两个实体完全相邻（但是type可以相邻）
					//	if(formerWord.originalForm.charAt(0)>='A' && formerWord.originalForm.charAt(0)<='Z' && st>1)
					//		entOmit = true;
					}
					
					if(ed < words.length)
					{
						Word nextWord = words[ed];
						
						//如果后面一个词是大写，就认为后面的词是一个实体（或实体的后缀），那么当前词是他的前缀或者它之前的词。而我们认为不会有两个实体完全相邻（但是type可以相邻）
						if(nextWord.originalForm.charAt(0)>='A' && nextWord.originalForm.charAt(0)<='Z')
							entOmit = true;
					}
					
					for(int i=st;i<ed;i++)
					{
						if(words[i].posTag.startsWith("I"))
							prep_cnt++;
						
						for(String badTag: badTagListForEntAndType)
						{
							if(words[i].posTag.startsWith(badTag))
							{
								entOmit = true;
								typeOmit = true;
								break;
							}
						}
						if(words[i].posTag.startsWith("P") && (i!=ed-1 || len==1)){
							entOmit = true;
							typeOmit = true;
						}
						//对首词的判断
						if(i==st)
						{
							if(words[i].posTag.startsWith("I") || words[i].posTag.startsWith("EX") || words[i].posTag.startsWith("TO"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].posTag.startsWith("D") && len==2){
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.startsWith("list") || words[i].baseForm.startsWith("many"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.equals("and"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
						//对尾词的判断
						if(i==ed-1)
						{
							if(words[i].posTag.startsWith("I") || words[i].posTag.startsWith("D") || words[i].posTag.startsWith("TO"))
							{
								entOmit = true;
								typeOmit = true;
							}
							if(words[i].baseForm.equals("and"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
						//词序列只有一个词
						if(len==1)
						{
							//TODO: 只允许 名词 进行check，但是常有 应该做变量的“通用名词”被检测为ent，如father等
							if(!words[i].posTag.startsWith("N"))
							{
								entOmit = true;
								typeOmit = true;
							}
						}
					}
					//此序列有太多的介词不太可能是ent
					if(prep_cnt >= 3)
					{
						entOmit = true;
						typeOmit = true;
					}
				}
/*
 * 过滤规则完毕
*/
				
/*
 * 实验：pos tag pattern 检测
 * 用pos tag pattern 检测代替上述手写rules，分析 “准确率” 和  ”时间效率“ 的变化  2016-2-22 
 * */
				// use trie search
//				if(!Globals.pp.entTrie.search(posTagArr))
//					entOmit = true;
//				if(!Globals.pp.typeTrie.search(posTagArr))
//					typeOmit = true;
				
				// use arrayList contain
//				if(!Globals.pp.entPosTagPatternList.contains(posTagSequence))
//					entOmit = true;
//				if(!Globals.pp.typePosTagPatternList.contains(posTagSequence))
//					typeOmit = true;
/*
 * pos tag pattern 检测完毕
 * */
			
/*
 * 实验：entity extraction
 * 分析不同的策略对entity extraction结果的影响
 * */
				// do not use any rules & stopEntList
//				typeOmit = false;
//				entOmit = false;
//				stopEntList.clear();
/*
 * 实验：entity extraction 完毕
 * */
				//search category | 优先级最高
				String category = null;
				if(af.pattern2category.containsKey(baseWord))
				{
					typeOmit = true;
					entOmit = true;
					category = af.pattern2category.get(baseWord);
				}
				
				//search type
				int hitMethod = 0; // 1= dbo(baseWord), 2=dbo(originalWord), 3=yago|extend()
				ArrayList<TypeMapping> tmList = new ArrayList<TypeMapping>();
				if(!typeOmit)
				{
					System.out.println("Type Check:  "+originalWord);
					checkTypeCnt++;
					//search standard type  
					tmList = tr.getTypeIDsAndNamesByStr(baseWord);
					if(tmList == null || tmList.size() == 0)
					{
						tmList = tr.getTypeIDsAndNamesByStr(originalWord);
						if(tmList != null && tmList.size()>0)
							hitMethod = 2;
					}
					else
						hitMethod = 1;
					
					//search extend type|现在认为extend type优先级更高（因为手动添加的数量少也更靠谱）|现在extend type主要是yago type了
					if(tmList == null || tmList.size() == 0)
					{
						tmList = tr.getExtendTypeByStr(allUpperWord);
						if(tmList != null && tmList.size() > 0)
						{
							preLog += "++++ Extend Type detect: "+baseWord+": "+" prefferd relaiton:"+tmList.get(0).prefferdRelation+"\n";
							hitMethod = 3;
						}
					}
				}
				
				//search entity
				ArrayList<EntityMapping> emList = new ArrayList<EntityMapping>();
				if(!entOmit && !stopEntList.contains(baseWord))
				{
					System.out.println("Ent Check: "+originalWord);
					checkEntCnt++;
					//注意这里的第二个参数是启用dblk的条件
					emList = getEntityIDsAndNamesByStr(originalWord,(UpperWordCnt>=len-1 || len==1),len);
					if(emList == null || emList.size() == 0)
					{
						emList = getEntityIDsAndNamesByStr(baseWord, (UpperWordCnt>=len-1 || len==1), len);
					}
					if(emList!=null && emList.size()>10)
					{
						ArrayList<EntityMapping> tmpList = new ArrayList<EntityMapping>();
						for(int i=0;i<10;i++)
						{
							tmpList.add(emList.get(i));
						}
						emList = tmpList;
					}
				}
				
				MergedWord mWord = new MergedWord(st,ed,originalWord);
				
				//add category
				if(category != null)
				{
					mWord.mayCategory = true;
					mWord.category = category;
					int key = st*(words.length+1) + ed;
					mustSelectedList.add(key);
				}
				
				//add literal
				if(len==1 && checkLiteralWord(words[st]))
				{
					mWord.mayLiteral = true;
					int key = st*(words.length+1) + ed;
					literalList.add(key);
				}
				
				//add type mappings
				if(tmList!=null && tmList.size()>0)
				{
					//如果最高分太低，直接抛弃
					if(tmList.get(0).score < TypeAcceptedScore)
						typeOmit = true;

					//method=1或2时如果非精确匹配，抛弃（这个版本只接受精确匹配，type的模糊匹配或taxonomy高层节点匹配之后再尝试）
					//method=3是yago|extend，是完全字符串匹配的
					String likelyType = tmList.get(0).typeName.toLowerCase();
					String candidateBase = baseWord.replace("_", ""), candidateOriginal = originalWord.replace("_", "").toLowerCase();
					if(!candidateBase.equals(likelyType) && hitMethod == 1)
						typeOmit = true;
					if(!candidateOriginal.equals(likelyType) && hitMethod == 2)
						typeOmit = true;
					
					if(!typeOmit)
					{
						mWord.mayType = true;
						mWord.tmList = tmList;
						
						int key = st*(words.length+1) + ed;
						typeMappings.put(key, tmList.get(0).typeName);
						typeScores.put(key, tmList.get(0).score);
					}
				}
				
				//add entity mappings
				if(emList!=null && emList.size()>0)
				{
					//如果最高分太低，直接抛弃
					if(emList.get(0).score < EntAcceptedScore)
						entOmit = true;
					
					//针对形如 the German Shepherd dog形式，防止the German Shepherd dog“被识别为一个ent
					else if(len > 2)
					{
						for(int key: entityMappings.keySet())
						{
							int te=key%(words.length+1),ts=key/(words.length+1);
							//此序列的第一个word是“ent”
							if(ts == st+1 && ts <= ed)
							{
								//第一个词是DT，且是小写 || The Pillars of the Earth或者The_Storm on the Sea_of_Galilee应该是一个ent，the首字母大写
								if(words[st].posTag.startsWith("DT") && !(words[st].originalForm.charAt(0)>='A'&&words[st].originalForm.charAt(0)<='Z'))
								{
									entOmit = true;
								}
							}
						}
					}
					
					//匹配信息记入merge word
					if(!entOmit)
					{
						mWord.mayEnt = true;
						mWord.emList = emList;
					
						//用于之后的remove duplicate and select
						int key = st*(words.length+1) + ed;
						entityMappings.put(key, emList.get(0).entityID);
						
						//这里对ent的评分进行一些修正 | 属于conflict resolution
						double score = emList.get(0).score;
						String likelyEnt = emList.get(0).entityName.toLowerCase().replace(" ", "_");
						String lowerOriginalWord = originalWord.toLowerCase();
						//如果字符串完全匹配，则得分乘以word的数量；单个word的评分已经很高并且经常会出现不想要的情况，所以相当于没有增加单个word得分
						if(likelyEnt.equals(lowerOriginalWord))
							score *= len;
						//如果这个Ent被一些小的ent完全覆盖，那么它的可信度应该比这些小的ent的和更高。例如：Robert Kennedy，[Robert]和[Kennedy]都能找到对应ent，但显然这里应该是[Robert Kennedy]
						//像Social_Democratic_Party，这三个word任意组合都是ent，导致方案太多；相比较“冲突选哪个”，“连or不应该连”显得更重要（而且实际错误多为连或不连的错误），所以这里直接抛弃被覆盖的小ent
						//像Abraham_Lincoln，在“不连接”的方案中，会把他们识别成两个node，最后得分超过了正确答案的得分；故对于这种词设置为必选
						if(len>1)
						{
							boolean[] flag = new boolean[words.length+1];
							ArrayList<Integer> needlessEntList = new ArrayList<Integer>();
							double tmpScore=0;
							for(int preKey: entityMappings.keySet())
							{
								if(preKey == key)
									continue;
								int te=preKey%(words.length+1),ts=preKey/(words.length+1);
								for(int i=ts;i<te;i++)
									flag[i] = true;
								if(st<=ts && ed>= te)
								{
									needlessEntList.add(preKey);
									tmpScore += entityScores.get(preKey);
								}
							}
							int hitCnt = 0;
							for(int i=st;i<ed;i++)
								if(flag[i])
									hitCnt++;
							//将完全覆盖的条件改为 完全覆盖 ||大部分覆盖并且大部分大写 || 全部大写
							if(hitCnt == len || ((double)hitCnt/(double)len > 0.6 && (double)UpperWordCnt/(double)len > 0.6) || UpperWordCnt == len || len>=4)
							{
								//如中间有逗号，则要求两边的词都在mapping的entity中出现
								//例如 Melbourne_,_Florida: Melbourne, Florida 是必须选的，而 California_,_USA: Malibu, California，认为不一定正确
								boolean commaTotalRight = true;
								if(originalWord.contains(","))
								{
									String candidateCompactString = originalWord.replace(",","").replace("_", "").toLowerCase();
									String likelyCompactEnt = likelyEnt.replace(",","").replace("_", "");
									if(!candidateCompactString.equals(likelyCompactEnt))
										commaTotalRight = false;
									else
									{
										mWord.name = mWord.name.replace("_,_","_");
										needRemoveCommas = true;
									}
								}
									
								if(commaTotalRight)
								{
									mustSelectedList.add(key);
									if(tmpScore>score)
										score = tmpScore+1;
									for(int preKey: needlessEntList)
									{
										entityMappings.remove(preKey);
										mustSelectedList.remove(Integer.valueOf(preKey));
									}
								}
							}
						}
						
						entityScores.put(key,score);
						//注意mWord中的score并没有改，因为还没考虑好这里的评分是否应该带入后面的环节
					}
				}
				
				if(mWord.mayCategory || mWord.mayEnt || mWord.mayType || mWord.mayLiteral)
					mWordList.add(mWord);
			}
		}
		
		/*输出所有候选匹配，注意这里输出的评分是上述修正过的评分，并不是真正存在mWord里的评分*/
		System.out.println("------- Result ------");
		for(MergedWord mWord: mWordList)
		{
			int key = mWord.st * (words.length+1) + mWord.ed;
			if(mWord.mayCategory)
			{
				System.out.println("Detect category mapping: "+mWord.name+": "+ mWord.category +" score: 100.0");
	        	preLog += "++++ Category detect: "+mWord.name+": "+mWord.category+" score: 100.0\n";
			}
			if(mWord.mayEnt)
			{
				System.out.println("Detect entity mapping: "+mWord.name+": "+mWord.emList.get(0).entityName +" score:"+entityScores.get(key));
	        	preLog += "++++ Entity detect: "+mWord.name+": "+mWord.emList.get(0).entityName+" score:"+entityScores.get(key)+"\n";
	        	//统计hit
				hitEntCnt++;
			}
			if(mWord.mayType)
			{
				System.out.println("Detect type mapping: "+mWord.name+": "+mWord.tmList.get(0).typeName +" score:"+typeScores.get(key));
	    		preLog += "++++ Type detect: "+mWord.name+": "+mWord.tmList.get(0).typeName +" score:"+typeScores.get(key)+"\n";
	    		//统计hit
				hitTypeCnt++;
			}
			if(mWord.mayLiteral)
			{
				System.out.println("Detect literal: "+mWord.name);
				preLog += "++++ Literal detect: "+mWord.name+"\n";
			}
		}
		
		/*
		 * sort by score and remove duplicate
		 * <"video_game" "ent:Video game" "50.0"> <"a_video_game" "ent:Video game" "45.0">, 则组合成多种方案，每个方案内部不冲突。
		 * 按照得分最高的对应实体的得分排序，砍掉重复的低分。注意实际上并没有在mWordList中删除任何信息。
		 * type的判断较严，认为不会出现这种情况啊
		 * 
		 * 2015-11-28
		 * 对于每一个需要连下滑线的词序列（即Node），选择连线（将他们的识别信息保留下去）或不连线（放弃这个词序列的识别信息）
		 * 因为type是全匹配而且很少有噪音，所以识别出type的那个word是必选的；
		 * 因为literal只识别纯数字，所以识别出literal的那个word也是必选的；
		 * KB中同一ent对应query中不同mergedWord的，取得分高的
		*/
		// KB中同一ent对应query中不同mergedWord的，取得分高的
		ByValueComparator bvc = new ByValueComparator(entityScores,words.length+1);
		List<Integer> keys = new ArrayList<Integer>(entityMappings.keySet());
        Collections.sort(keys, bvc);
        for(Integer key : keys)
        {
        	if(!mappingScores.containsKey(entityMappings.get(key)))
        		mappingScores.put(entityMappings.get(key), entityScores.get(key));
        	else
        		entityMappings.remove(key);
        }
        
        // 2015-11-28 枚举不冲突的策略
        selectedList = new ArrayList<ArrayList<Integer>>();
        ArrayList<Integer> selected = new ArrayList<Integer>();
        
        // 先放入肯定要选的key
        selected.addAll(mustSelectedList);
        for(Integer key: typeMappings.keySet())
        {
        	//因为type是全匹配而且很少有噪音，所以识别出type的那个word基本是必选的；
        	//注意这只针对word sequence，单word的type可能和其他名词组成一个Ent，例如“Brooklyn Bridge”，这里type:Bridge就应该被抛弃；
        	int ed = key%(words.length+1), st = key/(words.length+1);
        	if(st+1 < ed)
        	{
        		boolean beCovered = false;
        		//[prime_minister of Spain],一个ent完全覆盖了这个type，这时可能会取ent
				for(int preKey: entityMappings.keySet())
				{
					int te=preKey%(words.length+1),ts=preKey/(words.length+1);
					//ent必须要比这个type长才算覆盖
					if(ts <= st && te >= ed && ed-st < te-ts)
					{
						beCovered = true;
					}
				}
				
				if(!beCovered)
					selected.add(key);
        	}
        }
//        for(Integer key: literalList)
//        {
//        	//因为literal只识别纯数字，所以识别出literal的那个word也是必选的；
//        	//有一些实体是包含数字的，例如 Chile Route 68，所以literal不能提前固定
//        	selected.add(key);
//        }
        
 /*
  * 实验：conflict resolution
  * 不同冲突处理策略对最终node Recognition结果的影响
  * */
        //1,longest entity principle 2,shortest entity principle
//        selected.addAll(keys);
/*
 * 实验：conflict resolution
 * */       
        
        //由于之前的策略问题，必选区段有可能冲突，这里按最长原则消除冲突
        ArrayList<Integer> noConflictSelected = new ArrayList<Integer>();
    	
		//select longer one when conflict  || 2015-11-28 ”最长原则“ 被  ”允许多种策略“ 替换  || 2015-12-13 在多种策略基础上进行最长原则
		boolean[] flag = new boolean[words.length];
		ByLenComparator blc = new ByLenComparator(words.length+1);
		Collections.sort(selected,blc);
		  
		for(Integer key : selected) 
		{
			int ed = key%(words.length+1), st = (key-ed)/(words.length+1);
		  	boolean omit = false;
		  	for(int i=st;i<ed;i++)
		  	{
		  		if(flag[i])
		  		{
		  			omit = true;
		  			break;
		  		}
		  	}
		  	if(omit)
		  		continue;
		  	for(int i=st;i<ed;i++)
		  		flag[i]=true;
		  	noConflictSelected.add(key);
		}
		
//scoring and ranking --> top-k decision
        dfs(keys,0,noConflictSelected,words.length+1);
        // get score and sort
        ArrayList<NodeSelectedWithScore> nodeSelectedWithScoreList = new ArrayList<NodeSelectedWithScore>();
        for(ArrayList<Integer> select: selectedList)
        {
        	double score = 0;
        	for(Integer key: select)
        	{
        		if(entityScores.containsKey(key))
        			score += entityScores.get(key);
        		if(typeScores.containsKey(key))
        			score += typeScores.get(key);
        	}
        	NodeSelectedWithScore tmp = new NodeSelectedWithScore(select, score);
        	nodeSelectedWithScoreList.add(tmp);
        }
        Collections.sort(nodeSelectedWithScoreList);
        
        
        //replace
        int cnt = 0;
        for(int k=0; k<nodeSelectedWithScoreList.size(); k++)
        {
        	if(k >= nodeSelectedWithScoreList.size())
        		break;
        	selected = nodeSelectedWithScoreList.get(k).selected;
   
        	Collections.sort(selected);
	        int j = 0;
	        String res = question;
	        if(selected.size()>0)
	        {
		        res = words[0].originalForm;
		        int tmp = selected.get(j++), st = tmp/(words.length+1), ed = tmp%(words.length+1);
		        for(int i=1;i<words.length;i++)
		        {
		        	if(i>st && i<ed)
		        	{
		        		res = res+"_"+words[i].originalForm;
		        	}
		        	else
		        	{
		        		res = res+" "+words[i].originalForm;
		        	}
		        	if(i >= ed && j<selected.size())
		        	{
		        		tmp = selected.get(j++);
		        		st = tmp/(words.length+1);
		        		ed = tmp%(words.length+1);
		        	}
		        }
	        }
	        else
	        {
	        	res = words[0].originalForm;
		        for(int i=1;i<words.length;i++)
		        {
		        	res = res+" "+words[i].originalForm;
		        }
	        }
	        
	        boolean ok = true;
	        for(String str: fixedQuestionList)
	        	if(str.equals(res))
	        		ok = false;
	        if(!ok)
	        	continue;
	        
	        if(needRemoveCommas)
	        	res = res.replace("_,_","_");
	        
	        System.out.println("Merged: "+res);
	        preLog += "plan "+cnt+": "+res+"\n";
	        fixedQuestionList.add(res);
	        cnt++;
	        if(cnt >= 3)
	        	break;
        }
        long t2 = System.currentTimeMillis();
        preLog += "Total hit/check/all ent num: "+hitEntCnt+" / "+checkEntCnt+" / "+allCnt+"\n";
        preLog += "Total hit/check/all type num: "+hitTypeCnt+" / "+checkTypeCnt+" / "+allCnt+"\n";
        preLog += "Total Node Recognition time: "+ (t2-t1) + "ms\n";
		System.out.println("Total check time: "+ (t2-t1) + "ms");
		System.out.println("--------- pre entity/type recognition end ---------");
		
		return fixedQuestionList;
	}
	
	public void dfs(List<Integer> keys,int dep,ArrayList<Integer> selected,int size)
	{
		if(dep == keys.size())
		{
			ArrayList<Integer> tmpList = (ArrayList<Integer>) selected.clone();
			selectedList.add(tmpList);
		}
		else
		{
			//off 第dep个mWord
			dfs(keys,dep+1,selected,size);
			//不冲突则on
			boolean conflict = false;
			for(int preKey: selected)
			{
				int curKey = keys.get(dep);
				int preEd = preKey%size, preSt = (preKey-preEd)/size;
				int curEd = curKey%size, curSt = (curKey-curEd)/size;
				if(!(preSt<preEd && preEd<=curSt && curSt<curEd) && !(curSt<curEd && curEd<=preSt && preSt<preEd))
					conflict = true;
			}
			if(!conflict)
			{
				selected.add(keys.get(dep));
				dfs(keys,dep+1,selected,size);
				selected.remove(keys.get(dep));
			}
		}
		
	}
	
	public ArrayList<EntityMapping> getEntityIDsAndNamesByStr(String entity, boolean useDblk, int len) 
	{	
		String n = entity;
		ArrayList<EntityMapping> ret= new ArrayList<EntityMapping>();
		
		//首先看handwrite 
		if(m2e.containsKey(entity))
		{
			String eName = m2e.get(entity);
			EntityMapping em = new EntityMapping(EntityFragmentFields.entityName2Id.get(eName), eName, 1000);
			ret.add(em);
			return ret; //目前认为handwrite一定对，直接返回
		}
		
		//主要依据Lucene，因为噪音很小。大多数情况可以给出正确结果。
		ret.addAll(EntityFragment.getEntityMappingList(n));
		
		//在一些情况下，也使用DBpediaLookup进行补充
		if (useDblk) 
		{ 
			ret.addAll(Globals.dblk.getEntityMappings(n, null));
		}
		
		Collections.sort(ret);
		
		if (ret.size() > 0) return ret;
		else return null;
	}
	
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
	
	static class ByValueComparator implements Comparator<Integer> {
        HashMap<Integer, Double> base_map;
        int base_size;
        double eps = 1e-8;
        
        int dblcmp(double a,double b)
        {
        	if(a+eps < b)
        		return -1;
        	return b+eps<a ? 1:0;
        }
 
        public ByValueComparator(HashMap<Integer, Double> base_map, Integer size) {
            this.base_map = base_map;
            this.base_size = size;
        }
 
        public int compare(Integer arg0, Integer arg1) {
            if (!base_map.containsKey(arg0) || !base_map.containsKey(arg1)) {
                return 0;
            }
 
            if (dblcmp(base_map.get(arg0),base_map.get(arg1))<0) {
                return 1;
            } 
            else if (dblcmp(base_map.get(arg0),base_map.get(arg1))==0) 
            {
            	int len0 = (arg0%base_size)-arg0/base_size , len1 = (arg1%base_size)-arg1/base_size;
                if (len0 < len1) {
                    return 1;
                } else if (len0 == len1) {
                    return 0;
                } else {
                    return -1;
                }
            } 
            else {
                return -1;
            }
        }
    }
	
	static class ByLenComparator implements Comparator<Integer> {
        int base_size;
 
        public ByLenComparator(int size) {
            this.base_size = size;
        }
 
        public int compare(Integer arg0, Integer arg1) {
        	int len0 = (arg0%base_size)-arg0/base_size , len1 = (arg1%base_size)-arg1/base_size;
            if (len0 < len1) {
                return 1;
            } else if (len0 == len1) {
                return 0;
            } else {
                return -1;
            }
        }
    }
	 
	public boolean isDigit(char ch)
	{
		if(ch>='0' && ch<='9')
			return true;
		return false;
	}
	
	public boolean checkLiteralWord(Word word)
	{
		boolean ok = false;
		//目前就只认为 数值 是literal
		if(word.posTag.equals("CD"))
			ok = true;
		return ok;
	}
	
	public static void main (String[] args) 
	{
		Globals.init();
		EntityRecognition er = new EntityRecognition();
		try 
		{
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			while (true) 
			{	
				System.out.print("Please input the question: ");
				String question = br.readLine();
				
				er.process(question);
			}
			
//			File inputFile = new File("D:\\husen\\gAnswer\\data\\test\\test_in.txt");
//			File outputFile = new File("D:\\husen\\gAnswer\\data\\test\\test_out.txt");
//			BufferedReader fr = new BufferedReader(new InputStreamReader(new FileInputStream(inputFile),"utf-8"));
//			OutputStreamWriter fw = new OutputStreamWriter(new FileOutputStream(outputFile,true),"utf-8");
//
//			String input;
//			while((input=fr.readLine())!=null)
//			{
//				String[] strArray = input.split("\t");
//				String id = "";
//				String question = strArray[0];
//				if(strArray.length>1)
//				{
//					question = strArray[1];
//					id = strArray[0];
//				}
//				//去掉句尾符号，另外注意"?"会导致lucene/dbpedia lookup报错
//				if(question.length()>1 && question.charAt(question.length()-1)=='.' || question.charAt(question.length()-1)=='?')
//					question = question.substring(0,question.length()-1);
//				if(question.isEmpty())
//					continue;
//				er.process(question);
//				fw.write("Id: "+id+"\nQuery: "+question+"\n");
//				fw.write(er.preLog+"\n");
//			}
//			
//			fr.close();
//			fw.close();
			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

}
