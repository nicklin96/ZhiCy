package log;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;

import nlp.ds.Sentence;
import nlp.ds.Word;
import qa.Answer;
import qa.Globals;
import qa.mapping.SemanticItemMapping;
import rdf.EntityMapping;
import rdf.PredicateMapping;
import rdf.SemanticRelation;
import rdf.Triple;

public class ResultJspFile 
{
	public QueryLogger qlog = null;
	public static int pageSize = 30;
	public static String localpath = Globals.localPath+"data/resultJsp/";
	
	public void saveToFile(QueryLogger q,String sparqlSb,String time_gStore,String time)
	{
		qlog = q;
		saveAnswerText();
		saveAnswerJspForNBganswer();
		saveDependencyTreeJsp();
		saveTextCoverageJsp();
		saveSemanticRelationsJsp();
		saveMappingJsp();
		saveCRRJsp();
		saveSparqlJsp(sparqlSb);
		saveTimeTable(time_gStore,time);
		
	}
	public void saveAnswerText()
	{
		
		FileWriter writer = null;
		try
		{
			File outputFile=new File(localpath+"answerText.txt");
			writer = new FileWriter(outputFile);
			int resultCount = qlog.answers.size();
			
			for (int i=0;i<resultCount;i++)
			{
				Answer ans = qlog.answers.get(i);
				writer.write(ans.questionFocusValue+"\n");
			}
			//writer.close();
		}
		catch (IOException e) 
		{
			System.err.println("save answer text error");					
		}finally {
			if (writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					e.printStackTrace();
				}
		}
	}
	
	public void saveAnswerJspForNBganswer() 
	{
		//System.out.println("printAnswerJsp..."+beginResult+" "+endResult+ " "+ qlog.answers.size());
		if (qlog==null || qlog.match == null 
			|| qlog.match.answers == null 
			|| qlog.match.answers.length == 0
			|| qlog.sparql == null) {
			return ;
		}
		FileWriter writer = null;
		try 
		{	
			int resultCount = qlog.answers.size();
			
			StringBuilder ret = new StringBuilder("");


			for (int i = 0; i < resultCount; i ++) 
			{
				Answer ans = qlog.answers.get(i);
				
				if (i%2 == 0)
					ret.append("<tr>");
				if (!Character.isDigit(ans.questionFocusValue.charAt(0)) && !ans.questionFocusValue.equals("false") && !ans.questionFocusValue.equals("true")) // 实体
				{
					String link = null;
					if (ans.questionFocusValue.startsWith("http")) 
					{
						link = ans.questionFocusValue;
					}
					else 
					{
						link = "http://en.wikipedia.org/wiki/"+ans.questionFocusValue;
					}
					ret.append("<td id=\"hit\"><a id=\"entity_name\" href=\""+link+"\" target=\"_blank\">");
					ret.append(ans.questionFocusValue);
					ret.append("</a><br/>");
					for (int j = 0; j < ans.otherInformationKey.size(); j ++) 
					{
						ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
								+":</span><span id=\"values\">"
								+ans.otherInformationValue.get(j)
								+"   </span>");
					}
					ret.append("</td>");
				}
				else // 常量值
				{
					ret.append("<td id=\"hit\">");
					ret.append(ans.questionFocusValue);
					ret.append("<br/>");
					for (int j = 0; j < ans.otherInformationKey.size(); j ++) {
						ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
								+":</span><span id=\"values\">"
								+ans.otherInformationValue.get(j)
								+"   </span>");
					}
					ret.append("</td>");
				}
				
				if (i%2 == 1)
					ret.append("</tr>");
				
			}
			
			String path = localpath+"ansJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(ret.toString());
			//writer.close();
			
			
		} catch (Exception e) {
			//e.printStackTrace();	
			System.err.println("save NB Answer jsp error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveAnswerJsp() {
		//QueryLogger qlog = this;
		
		//System.out.println("printAnswerJsp..."+beginResult+" "+endResult+ " "+ qlog.answers.size());
		if (qlog==null || qlog.match == null 
			|| qlog.match.answers == null 
			|| qlog.match.answers.length == 0
			|| qlog.sparql == null) {
			return ;
		}
		FileWriter writer = null;
		try {	
			int resultCount = qlog.answers.size();
			int pageCount = resultCount/pageSize; if (resultCount%pageSize!=0) pageCount++;
			
			for (int curPageNum=0;curPageNum<pageCount;curPageNum++)
			{
				StringBuilder ret = new StringBuilder("");
				int beginResult = curPageNum*pageSize;
				int endResult = (curPageNum+1)*pageSize;
				int i;
				for (i = beginResult; i < Math.min(endResult,resultCount); i ++) 
				{
	
					Answer ans = qlog.answers.get(i);
					
					if (i%2 == 0)
						ret.append("<tr>");
					if (!Character.isDigit(ans.questionFocusValue.charAt(0))) {
						String link = null;
						if (ans.questionFocusValue.startsWith("http")) {
							link = ans.questionFocusValue;
						}
						else {
							link = "http://en.wikipedia.org/wiki/"+ans.questionFocusValue;
						}
						ret.append("<td id=\"hit\" valign=\"top\" padding-left=\"0px\"><a id=\"entity_name\" href=\""+link+"\" target=\"_blank\">");
						ret.append(ans.questionFocusValue);
						ret.append("</a><br/>");
						for (int j = 0; j < ans.otherInformationKey.size(); j ++) {
							ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
									+":</span><span id=\"values\">"
									+ans.otherInformationValue.get(j)
									+"   </span>");
						}
						ret.append("</td>");
					}
					else {
						//String link = ans.questionFocusValue;
						ret.append("<td id=\"hit\" padding-left=\"0px\">");
						ret.append(ans.questionFocusValue);
						ret.append("<br/>");
						for (int j = 0; j < ans.otherInformationKey.size(); j ++) {
							ret.append("<span id=\"properties\">"+ans.otherInformationKey.get(j).substring(1)
									+":</span><span id=\"values\">"
									+ans.otherInformationValue.get(j)
									+"   </span>");
						}
						ret.append("</td>");
					}
					
					if (i%2 == 1)
						ret.append("</tr>");
					
				}
				
				while (i<endResult)
				{
					if (i%2 == 0)
						ret.append("<tr>");
					ret.append("<td valign=\"top\"> </td>");
					if (i%2 == 1)
						ret.append("</tr>");
					i++;
				}
				

				
				String path = localpath+"ansJsp\\page_"+curPageNum+".dat";
				File outputFile = new File(path);
				writer = new FileWriter(outputFile);
				writer.write(ret.toString());
				//writer.close();
			}			
		} catch (Exception e) {
			e.printStackTrace();					
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveDependencyTreeJsp() 
	{
		//QueryLogger qlog = this;
		String ret;
		
		if (qlog == null)
			ret = "";
		if (qlog.sparql == null) {
			ret =  qlog.s.dependencyTreeStanford.toString();
		}
		else
		{
			int countStanford = 0;
			int countMalt = 0;
			for (Triple t : qlog.sparql.tripleList) {
				if (t.semRltn != null) {
					if(t.semRltn.extractingMethod == 'S') countStanford ++;
					else if(t.semRltn.extractingMethod == 'M') countMalt ++;
				}
			}
			
			if (countStanford > countMalt) ret = qlog.s.dependencyTreeStanford.toString();
			else ret = qlog.s.dependencyTreeMalt.toString();
		}
		
		FileWriter writer = null;
		try
		{
			String path = localpath+"DependencyTreeJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(ret.toString());
			//writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();	
			System.err.println("save dependency tree error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveTextCoverageJsp() {		
		//QueryLogger qlog = this;
		StringBuilder sb = new StringBuilder();
		
		if (qlog == null || qlog.s == null) 
			sb.append("");
		else
		{
			Sentence s = qlog.s;			
			int notCoveredCount = 0;
			for (Word w : s.words) {
				if (w.isCovered || w.isIgnored || w.posTag.equals(".")) {
					sb.append("["+w.originalForm+"] ");
				}
				else {
					sb.append(w.originalForm + " ");
					notCoveredCount ++;
				}
			}
			sb.append(" <not covered:"+notCoveredCount + "/" +s.words.length+">");
		}
		FileWriter writer = null;
		try
		{
			String path = localpath+"TextCoverageJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(sb.toString());
			//writer.close();
		}
		catch (Exception e) 
		{
			e.printStackTrace();					
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveSemanticRelationsJsp () {
		//QueryLogger qlog = this;
		StringBuilder sb = new StringBuilder();
		
		if (qlog == null || qlog.sparql == null || qlog.sparql.semanticRelations == null) 
			sb.append("");
		else
		{		
			HashMap<Integer, SemanticRelation> semRltns = qlog.sparql.semanticRelations;
			for (Integer key : semRltns.keySet()) {
				sb.append("(\"" + semRltns.get(key).arg1Word.getFullEntityName() + "\"");
				sb.append(", \"" + semRltns.get(key).arg2Word.getFullEntityName() + "\"");
				sb.append(", \"" + semRltns.get(key).predicateMappings.get(0).parapharase + "\"");
				sb.append(", " + ((int)(semRltns.get(key).LongestMatchingScore*1000))/1000.0 + ")\n");
			}
		}
		FileWriter writer =null;
		try
		{
			String path = localpath+"SemanticRelationsJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(sb.toString());
			//writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();
			System.err.println("save semantic relations jsp error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveMappingJsp () {
		
		//QueryLogger qlog = this;
		StringBuilder sb = new StringBuilder();
		if (qlog == null || qlog.sparql == null) {
			sb.append("");
		}
		else
		{		
			HashSet<Word> printed = new HashSet<Word>();
	
			int threshold = SemanticItemMapping.t;
			if (threshold > 5) threshold = 5;
			
			for (Triple triple : qlog.sparql.tripleList) {
				if (triple.predicateID == Globals.pd.typePredicateID) continue;
				SemanticRelation sr = triple.semRltn;
				if (sr == null) continue;
				Word subjWord = triple.getSubjectWord();
				Word objWord = triple.getObjectWord();
				if (!printed.contains(subjWord)) {
					printed.add(subjWord);
					if (!triple.subject.startsWith("?")) {
						ArrayList<EntityMapping> emlist = qlog.entityDictionary.get(subjWord);
						
						int emlist_size = 0;
						if (emlist != null) emlist_size = emlist.size();
						else emlist_size = 0;
						
						sb.append("\"" + subjWord.getFullEntityName() + "\"" + "(" + emlist_size + ") --> ");
						if (emlist_size > 0) {
							int i = 0;
							for (EntityMapping em : emlist) {
								sb.append("<" + em.entityName.replace(" ", "_") + ">,");
								i ++;
								if (i == threshold) break;
							}
						}
						sb.append("\n");
					}
					else {
						sb.append("\"" + subjWord.getFullEntityName() + "\"" + "(" + 1 + ") --> "+triple.subject);
						sb.append("\n");					
					}
				}
				if (!printed.contains(objWord)) {
					printed.add(objWord);
					if (!triple.object.startsWith("?")) {
						ArrayList<EntityMapping> emlist = qlog.entityDictionary.get(objWord);
						if (emlist==null)
						{
							System.out.println("error emlist!"+subjWord+" "+objWord+" "+triple.isSubjObjOrderSameWithSemRltn);
							continue;
						}
						sb.append("\"" + objWord.getFullEntityName() + "\"" + "(" + emlist.size() + ") --> ");
						int i = 0;
						for (EntityMapping em : emlist) {
							sb.append("<" + em.entityName.replace(" ", "_") + ">,");
							i ++;
							if (i == threshold) break;
						}
						sb.append("\n");
					}
					else {
						sb.append("\"" + objWord.getFullEntityName() + "\"" + "(" + 1 + ") --> " + triple.object);
						sb.append("\n");
					}
				}
				if (sr.dependOnSemanticRelation == null) {
					sb.append("\"" + triple.semRltn.predicateMappings.get(0).parapharase + "\"" + "(" + triple.semRltn.predicateMappings.size() +") --> ");
					int i = 0;
					for (PredicateMapping pm : triple.semRltn.predicateMappings) {
						sb.append("<" + Globals.pd.getPredicateById(pm.pid) + ">,");
						i ++;
						if (i == threshold) break;
					}
					sb.append("\n");
				}
			}
		}
		
		FileWriter writer = null;
		try
		{
			String path = localpath+"MappingJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(sb.toString());
			writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();	
			System.err.println("save mapping jsp error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveCRRJsp () {
		//QueryLogger qlog = this;
		StringBuilder sb = new StringBuilder("");
		HashSet<Word> printed = new HashSet<Word>();
		for (Word w : qlog.s.words) {
			w = w.getNnHead();
			if (printed.contains(w)) 
				continue;
			if (w.crr != null) 
				sb.append("\""+w.getFullEntityName() + "\" = \"" + w.crr.getFullEntityName() + "\"");
			printed.add(w);
		}
		FileWriter writer = null;
		try
		{
			String path = localpath+"CRRJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(sb.toString());
			//writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();	
			System.err.println("save CRR jsp error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveSparqlJsp(String spq)
	{
		FileWriter writer = null;
		try
		{
			String path = localpath+"SparqlJsp.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(spq);
			//writer.close();
		}
		catch (Exception e) 
		{
			e.printStackTrace();					
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void saveTimeTable(String time_gStore,String time)
	{
		//QueryLogger qlog = this;
		String resultCount = "0";
		String time0 = "0";
		String time1 = "0";
		String time2 = "0";
		String time3 = "0";
		String time4 = "0";
		String time5 = "0";
		
		//System.out.println("time_g="+time_gStore+"time="+time);
		
		if (qlog!=null)
		{
			resultCount = String.valueOf(qlog.answers.size());
			// step0 is Node Recognition 
			time0 = ""+qlog.timeTable.get("step0");
			// step1 is DependencyTree
			time1 = ""+qlog.timeTable.get("step1");
			// step2 is Build Query Graph (including fix, top-k join;)
			time2 = ""+(qlog.timeTable.get("step2")-qlog.timeTable.get("TopkJoin"));
			// topk join
			time3 = ""+qlog.timeTable.get("TopkJoin");
			
//			time4 = ""+(qlog.timeTable.get("step3")-qlog.timeTable.get("CollectEntityNames"));
			
			if (time_gStore.length()>0) 
				time5 = time_gStore.substring(1);
			else 
				time5 = "0";
		}

		
		/*TimeTable format:
		 resultCount\n
		 time\n
		 time0\n
		 time1\n
		 time2\n
		 time3\n
		 time5\n
		*/
		String ret = resultCount+"\n"+time+"\n"+time0+"\n"+time1+"\n"+time2+"\n"+time3+"\n"+time5;
		
		FileWriter writer = null;
		try
		{
			String path = localpath+"TimeTable.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(ret);
			writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();	
			System.err.println("save time table error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	public void clearJsp()
	{
		String resultCount = "0";
		String time = "0";
		String time1 = "0";
		String time2 = "0";
		String time3 = "0";
		String time4 = "0";
		String time5 = "0";
		
		String ret = resultCount+"\n"+time+"\n"+time1+"\n"+time2+"\n"+time3+"\n"+time4+"\n"+time5;
		FileWriter writer = null;
		try
		{
			String path = localpath+"TimeTable.dat";
			File outputFile = new File(path);
			writer = new FileWriter(outputFile);
			writer.write(ret);
			writer.close();
		}
		catch (Exception e) 
		{
			//e.printStackTrace();	
			System.err.println("clearing jsp error");
		}finally {
			if(writer!=null)
				try {
					writer.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
	
	
	public String getSavedJsp(String path)
	{
		StringBuilder ret = new StringBuilder();
		BufferedReader reader = null;
		try
		{
			File inputFile = new File(path);
			reader = new BufferedReader(new FileReader(inputFile));
			
			String line;
			while ((line = reader.readLine()) != null)			
				ret.append(line+"\n");
			return ret.toString();
		}
		catch (Exception e) 
		{
			e.printStackTrace();
			return "";
		}finally {
			if(reader!=null)
				try {
					reader.close();
				} catch (IOException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
		}
	}
}
