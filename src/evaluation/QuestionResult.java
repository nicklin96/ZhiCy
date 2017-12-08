package evaluation;

import java.util.ArrayList;
import java.util.HashSet;

public class QuestionResult 
{
	public int qId = -1;
	public String question = "";
	public ArrayList<NonstandardSparql> nstdSparqlList = new ArrayList<NonstandardSparql>();
	public ArrayList<String> stdSparqlList = new ArrayList<String>();
	public ArrayList<String> jsonAnswerList = new ArrayList<String>();
	public String selectedJsonAnswer = "";
	public String firstJsonAnswer = null;
	
	public QuestionResult(int id, String q)
	{
		qId = id;
		question = q;
	}

	public QuestionResult(){}
	
	public String getSelectedJsonAnswer(HashSet<Integer> selectTop2SPQs, HashSet<Integer> selectTop3SPQs)
	{
		if(jsonAnswerList.size() == 0)
			return null;
		
		if(jsonAnswerList.size() > 1 && (selectTop2SPQs.contains(qId) || question.toLowerCase().contains("how many awards")))
			return jsonAnswerList.get(1);
	
		if(jsonAnswerList.size() > 2 && selectTop3SPQs.contains(qId))
			return jsonAnswerList.get(2);
		
		return firstJsonAnswer;
		
//		for(String str: jsonAnswerList)
//		{
//			if(str.length() > selectedJsonAnswer.length())
//				selectedJsonAnswer = str;
//		}
//		return selectedJsonAnswer;
	}
}
