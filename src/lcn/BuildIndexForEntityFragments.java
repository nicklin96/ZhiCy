package lcn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

import qa.Globals;

/** */
/**
 *  Lucene���������Ļ�����Ԫ��document��ͬʱ���е���filed���Ը�����Ҫ�Լ�����
 * 
 * Document��һ����¼��������ʾһ����Ŀ���൱�����ݿ��е�һ�м�¼���������������ĵ�����������Ŀ��
 * eg:��Ҫ�����Լ������ϵ��ļ������ʱ��Ϳ��Դ���field(�ֶ�,��������ݿ��е��С� Ȼ����field��ϳ�document�������������ļ���
 * ���document���ļ�ϵͳdocument����һ�����
 * 
 * StandardAnalyzer��lucene�����õ�"��׼������",���������¹���: 
 * 1����ԭ�о��Ӱ��տո�����˷ִ�
 * 2�����еĴ�д��ĸ��������ת��ΪСд����ĸ 
 * 3������ȥ��һЩû���ô��ĵ��ʣ�����"is","the","are"�ȵ��ʣ�Ҳɾ�������еı��
 */
public class BuildIndexForEntityFragments{
	public void indexforentity() throws Exception
	{
		if(EntityFragmentFields.entityId2Name == null)
			EntityFragmentFields.load();
		
		long startTime = new Date().getTime();
		
		//File indexDir_en = new File("E:\\Hanshuo\\DBpedia3.9\\reducedDBpedia3.9\\fragments\\entity_fragment_index");
		//File sourceDir_en = new File("E:\\Hanshuo\\DBpedia3.9\\reducedDBpedia3.9\\fragments\\entity_fragment.txt");
		
//Try update offline to DBpedia2015. by husen 2016-04-08	// use DBpedia2014, by husen 2016-04-22
		File indexDir_en = new File("D:\\husen\\DBpedia2014\\reducedDBpedia2014\\fragments\\entity_fragment_index");
		File sourceDir_en = new File("D:\\husen\\DBpedia2014\\reducedDBpedia2014\\fragments\\entity_fragment.txt");
		
		Analyzer luceneAnalyzer_en = new StandardAnalyzer();  
		IndexWriter indexWriter_en = new IndexWriter(indexDir_en, luceneAnalyzer_en,true); 
		
		int mergeFactor = 100000;    //Ĭ����10
		int maxBufferedDoc = 1000;  // Ĭ����10
		int maxMergeDoc = Integer.MAX_VALUE;  //Ĭ�������
		
		//indexWriter.DEFAULT_MERGE_FACTOR = mergeFactor;
		indexWriter_en.setMergeFactor(mergeFactor);
		indexWriter_en.setMaxBufferedDocs(maxBufferedDoc);
		indexWriter_en.setMaxMergeDocs(maxMergeDoc);		
		
		
		FileInputStream file = new FileInputStream(sourceDir_en);		
		InputStreamReader in = new InputStreamReader(file,"UTF-8");	
		//InputStreamReader in = new InputStreamReader(file,"utf-16");	
		BufferedReader br = new BufferedReader(in);		
		
		int count = 0;
		
		//������� sc.hasNext() �ж��Ƿ�����һ��
		//����ֱ���� br.readLine()
		//while(br.readLine() != null)
		while(true)
		{			
			String _line = br.readLine();
			{
				if(_line == null) break;
			}
			count++;
			if(count % 100000 == 0)
				System.out.println(count);				
			
			String line = _line;		
			String temp[] = line.split("\t");
			
			if(temp.length != 2)
				continue;
			else
			{
				int entity_id = Integer.parseInt(temp[0]);
				if(!EntityFragmentFields.entityId2Name.containsKey(entity_id))
					continue;
				
				String entity_name = EntityFragmentFields.entityId2Name.get(entity_id);
				String entity_fragment = temp[1];
				entity_name = entity_name.replace("____", " ");
				entity_name = entity_name.replace("__", " ");
				entity_name = entity_name.replace("_", " ");
			
					
				Document document = new Document(); 
				
				Field EntityName = new Field("EntityName", entity_name, Field.Store.YES,
						Field.Index.TOKENIZED,
						Field.TermVector.WITH_POSITIONS_OFFSETS);	
				Field EntityId = new Field("EntityId", String.valueOf(entity_id),
						Field.Store.YES, Field.Index.NO);
				Field EntityFragment = new Field("EntityFragment", entity_fragment,
						Field.Store.YES, Field.Index.NO);
				
				document.add(EntityName);
				document.add(EntityId);
				document.add(EntityFragment);
				indexWriter_en.addDocument(document);
			}			
		}
		
		indexWriter_en.optimize();
		indexWriter_en.close();
		br.close();

		// input the time of Build index
		long endTime = new Date().getTime();
		System.out.println("entity_name index has build ->" + count + " " + "Time:" + (endTime - startTime));
	}
	
	public static void main(String[] args)
	{
		BuildIndexForEntityFragments bef = new BuildIndexForEntityFragments();
		
		try
		{
			bef.indexforentity();
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
}

