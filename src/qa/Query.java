package qa;

import java.util.ArrayList;

import nlp.ds.Sentence;
import qa.extract.EntityRecognition;
import rdf.MergedWord;

/**
 * Query类主要功能：
 * 1、对question进行预处理
 * 2、对question进行Node Recognition
 * @author husen
 */
public class Query 
{
	public String NLQuestion = null;
	public String TransferedQuestion = null;
	public ArrayList<String> MergedQuestionList = null;
	public ArrayList<Sentence> sList  = null;
	
	public String queryId = null;
	public String preLog = "";
	
	public ArrayList<MergedWord> mWordList = null;
	
	public Query(){}
	public Query(String _question)
	{
		NLQuestion = _question;
		NLQuestion = removeQueryId(NLQuestion);
				
		/*
		 * 把等价替换放在后面，以防有某个实体名包含”as well as“等被替换词
		 * 算了还是放在前面吧，as well as会被识别成net：as_well.....
		 * 顺便把word中的"."删除，因为.和_连起来，会被parser拆开；
		 * 其实","也会影响，但句子中出现的,有分割作用，不能随便删除
		*/
		TransferedQuestion = getTransferedQuestion(NLQuestion);	
		
		// step1： NODE Recognition
		MergedQuestionList = getMergedQuestionList(TransferedQuestion);
		
		// build Sentence
		sList = new ArrayList<Sentence>();
		for(String mergedQuestion: MergedQuestionList)
		{
			Sentence sentence = new Sentence(this, mergedQuestion);
			sList.add(sentence);
		}
	}
	
	public boolean isDigit(char ch)
	{
		if(ch>='0' && ch<='9')
			return true;
		return false;
	}
	
	public boolean isUpperWord(char ch)
	{
		if(ch>='A' && ch<='Z')
			return true;
		return false;
	}
	
	/**
	 * 将question中部分words用等价形式替代，包括：
	 * 1、stanfordParser经常解析错误的短语和符号
	 * 2、同义词统一，movie->film
	 * @param question
	 * @return 替换后的question
	 */
	public String getTransferedQuestion(String question)
	{
		//rule1: 去掉word中的"."，因为.和_连起来，会被parser拆开; 去掉word末尾的"'"，因为会影响ner
		question = question.replace("' ", " ");
		String [] words = question.split(" ");
		String ret = "";
		for(String word: words)
		{
			String retWord = word;
			//偷懒只判断word首尾是否为num
			if(word.length()>=2 && !isDigit(word.charAt(0)) && !isDigit(word.charAt(word.length()-1)))
			{
				retWord = retWord.replace(".", "");
			}
			ret += retWord + " ";
		}
		if(ret.length()>1)
			ret = ret.substring(0,ret.length()-1);
		
		//rule2: as well as -> and
		ret = ret.replace("as well as", "and");
		
		//rule3: movie -> film
		ret = ret.replace(" movie", " film");
		ret = ret.replace(" movies", " films");
		
		//rule4: last
		ret = ret.replace("last Winter Paralympics", "2014 Winter Paralympics");
		
		return ret;
	}
	
	/**
	 * 将KB中的entity & type & literal识别出并用下划线替代同一实体间的空格
	 * @param question
	 * @return merged question list，即多种node recognition后的question
	 */
	public ArrayList<String> getMergedQuestionList(String question)
	{
		ArrayList<String> mergedQuestionList = null;
		//entity & type recognize
		EntityRecognition er = new EntityRecognition(); 
		mergedQuestionList = er.process(question);
		preLog = er.preLog;
		mWordList = er.mWordList;

		return mergedQuestionList;
	}
	
	public String removeQueryId(String question)
	{
		String ret = question;
		int st = question.indexOf("\t");
		if(st!=-1 && question.length()>1 && question.charAt(0)>='0' && question.charAt(0)<='9')
		{
			queryId = question.substring(0,st);
			ret = question.substring(st+1);
			System.out.println("Extract QueryId :"+queryId);
		}
		return ret;
	}
}
