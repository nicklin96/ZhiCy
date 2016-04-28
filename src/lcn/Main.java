package lcn;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

import fgmt.EntityFragment;
import qa.mapping.EntityFragmentDict;


public class Main {
	public static void main(String[] aStrings) throws Exception{
		
		//SearchInLiteralSubset se = new SearchInLiteralSubset();
		//SearchInEntityFragments sf = new SearchInEntityFragments();
		SearchInTypeShortName st = new SearchInTypeShortName();
		SearchInEntityFragments sf = new SearchInEntityFragments();
		EntityFragmentDict  efd = new EntityFragmentDict();
		EntityFragmentFields eff = null;

		
		while(true)
		{
			System.out.print("input name: ");
			Scanner sc = new Scanner(System.in);
			String literal = sc.nextLine();
			System.out.println(literal);
			
			//literal = cnlp.getBaseFormOfPattern(literal);
			
//search Type	
//			ArrayList<String> result = st.searchType(literal, 0.4, 0.8, 10);
//			System.out.println("TypeShortName-->RESULT:");
//			for (String s : result) {
//				System.out.println("<"+s + ">");
//			}

//search Ent Fragment
			EntityFragment ef = efd.getEntityFragmentByName(literal);
			System.out.println(ef);

//search Ent Name
//			ArrayList<EntityNameAndScore> result = sf.searchName(literal, 0.4, 0.8, 50);
//			System.out.println("EntityName-->RESULT:");
//			for(EntityNameAndScore enas: result)
//			{
//				System.out.println(enas);
//			}
			
			//sc.close();
		}
	}	

}
