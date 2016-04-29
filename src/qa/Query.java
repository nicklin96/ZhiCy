package qa;

import java.util.ArrayList;

import nlp.ds.Sentence;
import qa.extract.EntityRecognition;
import test.MergedWord;

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
		//��KB�е�entity & type & literalʶ��������»������ͬһʵ���Ŀո�
		
		/*
		 * �ѵȼ��滻���ں��棬�Է���ĳ��ʵ����������as well as���ȱ��滻��
		 * ���˻��Ƿ���ǰ��ɣ�as well as�ᱻʶ���net��as_well.....
		 * ˳���word�е�"."ɾ������Ϊ.��_���������ᱻparser�𿪣�
		 * ��ʵ","Ҳ��Ӱ�죬�������г��ֵ�,�зָ����ã��������ɾ��
		*/
		TransferedQuestion = getTransferedQuestion(NLQuestion);	
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
	
	//��stanfordParser������������Ķ����õȼ���ʽ���
	public String getTransferedQuestion(String question)
	{
		//rule1: ȥ��word�е�"."����Ϊ.��_���������ᱻparser��; 
		String [] words = question.split(" ");
		String ret = "";
		for(String word: words)
		{
			String retWord = word;
			//͵��ֻ�ж�word��β�Ƿ�Ϊnum
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
		
		return ret;
	}
	
	public ArrayList<String> getMergedQuestionList(String question)
	{
		ArrayList<String> mergedQuestionList = null;
		//entity & type recognize
		//TODO: ���ﱣ����ʵ�廮�ַ���
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