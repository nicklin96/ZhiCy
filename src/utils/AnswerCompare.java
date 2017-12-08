package utils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.HashSet;

public class AnswerCompare {
	
	static File testMergeFile = new File("D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/JointInference/Test/joint_inference.predicted-merge.final");
	static File testIntersectFile = new File("D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/JointInference/Test/joint_inference.predicted.final");
	static File stdFile = new File("D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/WebQuestions/test.data");
	static String comparePath = "D:/Documents/husen/Java/QuestionAnsweringOverFB-master/resources/JointInference/Test/merge-intersect.compare";
	
	public static boolean correct(HashSet<String> stdSet, HashSet<String> testSet)
	{
		boolean flag = true;
		for(String str: stdSet)
		{
			if(!testSet.contains(str))
			{
				flag = false;
				break;
			}
		}
		for(String str: testSet)
		{
			if(!stdSet.contains(str))
			{
				flag = false;
				break;
			}
		}
		return flag;
	}
	
	public static boolean correct(String stdStr, String testStr)
	{
		if(stdStr.length() > 1 && testStr.length() < 4)
			return false;
		
		HashSet<String> stdSet = new HashSet<>();
		HashSet<String> testSet = new HashSet<>();
		
		for(String str: stdStr.split("\t"))
			stdSet.add(str);
		
		testStr = testStr.substring(1,testStr.length()-1);
		for(String str: testStr.split(","))
		{
			if(str.length() <= 2)
				System.out.println(stdStr + testStr);
			testSet.add(str.substring(1,str.length()-1));
		}
		
		return correct(stdSet, testSet);
	}
	
	public static ArrayList<String> compare(File testFile)
	{
		ArrayList<String> correctList = new ArrayList<>();
		try 
		{
			ArrayList<String> qList = new ArrayList<>();
			ArrayList<String> stdList = new ArrayList<String>();
			ArrayList<String> testList = new ArrayList<String>();
			BufferedReader br = new BufferedReader(new InputStreamReader(new FileInputStream(stdFile), "utf-8"));
			String input = "";
			int cnt = 0;
			while((input = br.readLine()) != null)
			{
				cnt++;
				if(cnt % 4 == 3)
				{
					stdList.add(input);
				}
			}
			br.close();
			
			br = new BufferedReader(new InputStreamReader(new FileInputStream(testFile), "utf-8"));
			while((input = br.readLine()) != null)
			{
				String tmp = input.split("\t")[1];
				String q = input.split("\t")[0];
				testList.add(tmp);
				qList.add(q);
			}
			br.close();
			
			int correctNum = 0, allNum = stdList.size();
			for(int i=0; i<stdList.size(); i++)
			{
				if(correct(stdList.get(i), testList.get(i)))
				{
					correctList.add(qList.get(i));
					correctNum ++;
				}
			}
			
			double acc = (double)correctNum / (double)allNum;
			System.out.println(correctNum + " / " + allNum);
			System.out.println(acc);
			
		} catch (Exception e) {
			// TODO: handle exception
			e.printStackTrace();
		}
		
		return correctList;
	}
	
	public static void main(String[] args) {
		
		ArrayList<String> mergeCorrectList = compare(testMergeFile);
		ArrayList<String> intersectCorrectList = compare(testIntersectFile);
		ArrayList<String> mCiW = new ArrayList<>();
		ArrayList<String> mWiC = new ArrayList<>();
		ArrayList<String> output = new ArrayList<>();
		
		for(String mc: mergeCorrectList)
			if(!intersectCorrectList.contains(mc))
				mCiW.add(mc);
		
		for(String ic: intersectCorrectList)
			if(!mergeCorrectList.contains(ic))
				mWiC.add(ic);
		
		output.addAll(mCiW);
		output.add("\n\n");
		output.addAll(mWiC);
 		
		FileUtil.writeFile(output, comparePath);
	}

}
