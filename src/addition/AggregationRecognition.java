package addition;

import nlp.ds.DependencyTree;
import nlp.ds.DependencyTreeNode;
import nlp.ds.Word;
import qa.Globals;
import rdf.SemanticRelation;
import rdf.Sparql;
import rdf.Triple;
import log.QueryLogger;

public class AggregationRecognition {

	//---------------------------------------------定义变量------------------------------------------------------
    static String x[]={"zero","one","two","three","four","five","six","seven","eight","nine"};
	static String y[]={"ten","eleven","twelve","thirteen","fourteen","fifteen","sixteen","seventeen","eighteen","nineteen"};
	static String z[]={"twenty","thirty","forty","fifty","sixty","seventy","eighty","ninety"};
	static int b;
	//---------------------------------------------主方法---------------------------------------------------------
    public static Integer translateNumbers(String str) // 1~100
    {
    	int flag;
    	try {		
    	     b=Integer.valueOf(str);//把字符串强制转换为数字
    	     flag=1; //如果是数字，flag=1;
    	} 
    	catch (Exception e){
    	      flag=2; //如果不是数字，抛出异常，且flag=2;
    	}
    	int i,j;
    	switch(flag) //有两种情况
    	{
			case 1: //数字转换为英语单词
				return b;          		
			case 2:	                     //英语单词转换为数字
			   boolean flag1=true;
			   for(i=0;i<8;i++)                //转换20~99的单词
			    {
			    	for(j=0;j<10;j++)
			    	{
			    		String str1=z[i],str2=x[j];
			    		if(str.equals((str1))){         //判断字符串内容是否相等      
			    			return i*10+20; //输出20~99中是10倍数的单词数字   		
			    	    }       
			    		           		
			    		else if(str.equals((str1+" "+str2))){    //判断字符串内容是否相等     
			    			return i*10+j+20;
			            }     
			        }
			    }
			   
				for(i=0;i<10;i++){             
					if(str.equals(x[i])){       //判断字符串内容是否相等    
						return i;
			     	}            	
			     	else if(str.equals(y[i])){       //判断字符串内容是否相等    
			     		return 10+i;
			     	}                	
				} 
				
				//若输入字符串不是英文数字，则输出信息提示
				System.out.println("Warning: Can not Translate Number: " + str);
		 }
    	return 1;
    }

	
	public void recognize(QueryLogger qlog)
	{
		DependencyTree ds = qlog.s.dependencyTreeStanford;
		if(qlog.isMaltParserUsed)
			ds = qlog.s.dependencyTreeMalt;
		
		Word[] words = qlog.s.words;
		
		// how often | how many
		if(qlog.s.plainText.indexOf("How many")!=-1||qlog.s.plainText.indexOf("How often")!=-1||qlog.s.plainText.indexOf("how many")!=-1||qlog.s.plainText.indexOf("how often")!=-1)
		{
			for(Sparql sp: qlog.rankedSparqls)
			{
				sp.countTarget = true;
				//  How many pages does War and Peace have? --> res:War_and_Peace dbo:numberOfPages ?n . 
				//	 ?uri dbo:populationTotal ?inhabitants . 
				for(Triple triple: sp.tripleList)
				{
					String p = Globals.pd.getPredicateById(triple.predicateID).toLowerCase();
					if(p.contains("number") || p.contains("total") || p.contains("calories") || p.contains("satellites"))
					{
						sp.countTarget = false;
					}
				}
			}
		}
		
		// more than [num] [node]
		for(DependencyTreeNode dtn: ds.nodesList)
		{
			if(dtn.word.baseForm.equals("more"))
			{
				if(dtn.father!=null && dtn.father.word.baseForm.equals("than"))
				{
					DependencyTreeNode tmp = dtn.father;
					if(tmp.father!=null && tmp.father.word.posTag.equals("CD") && tmp.father.father!=null && tmp.father.father.word.posTag.startsWith("N"))
					{
						DependencyTreeNode target = tmp.father.father;
						
						// Which caves have more than 3 entrances | entranceCount | filter
						
						if(target.father !=null && target.father.word.baseForm.equals("have"))
						{
							qlog.moreThanStr = "GROUP BY ?" + qlog.target.originalForm + "\nHAVING (COUNT(?"+target.word.originalForm + ") > "+tmp.father.word.baseForm+")";
						}
						else
						{
							int num = translateNumbers(tmp.father.word.baseForm);
							qlog.moreThanStr = "FILTER (?"+target.word.originalForm+"> " + num + ")";
						}
					}
				}
			}
		}
		
		// most
		for(Word word: words)
		{
			if(word.baseForm.equals("most"))
			{
				Word modifiedWord = word.modifiedWord;
				if(modifiedWord != null)
				{
					for(Sparql sp: qlog.rankedSparqls)
					{
						//  Which Indian company has the most employees? --> ... dbo:numberOfEmployees ?n . || ?employees dbo:company ...
						sp.mostStr = "ORDER BY DESC(COUNT(?"+modifiedWord.originalForm+"))\nOFFSET 0 LIMIT 1";
						for(Triple triple: sp.tripleList)
						{
							String p = Globals.pd.getPredicateById(triple.predicateID).toLowerCase();
							if(p.contains("number") || p.contains("total"))
							{
								sp.mostStr = "ORDER BY DESC(?"+modifiedWord.originalForm+")\nOFFSET 0 LIMIT 1";
							}
						}
					}
				}
			}
		}
	}
	
	public static void main(String[] args) {
		System.out.println(translateNumbers("Twelve"));
		System.out.println(translateNumbers("thirty two"));
	}

}
