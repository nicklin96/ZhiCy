package lcn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStreamReader;
import java.util.Date;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.index.IndexWriter;

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
public class BuildIndexForLiteralSubset{
	public void indexforliteral() throws Exception
	{
		long startTime = new Date().getTime();
	
		//File indexDir_li = new File("E:\\huangruizhe\\dataset_DBpedia\\wenqiang\\literalSubset_index");
		//File sourceDir_li = new File("E:\\huangruizhe\\dataset_DBpedia\\wenqiang\\literalSubset.txt");
		File indexDir_li = new File("E:\\Hanshuo\\DBpedia3.9\\reducedDBpedia3.9\\fragments\\literalSubset_index");
		File sourceDir_li = new File("E:\\Hanshuo\\DBpedia3.9\\reducedDBpedia3.9\\fragments\\literalSubset.txt");
		
		Analyzer luceneAnalyzer_li = new StandardAnalyzer();  
		IndexWriter indexWriter_li = new IndexWriter(indexDir_li, luceneAnalyzer_li,true); 
		
		int mergeFactor = 100000;    //Ĭ����10
		int maxBufferedDoc = 1000;  // Ĭ����10
		int maxMergeDoc = Integer.MAX_VALUE;  //Ĭ�������
		
		//indexWriter.DEFAULT_MERGE_FACTOR = mergeFactor;
		indexWriter_li.setMergeFactor(mergeFactor);
		indexWriter_li.setMaxBufferedDocs(maxBufferedDoc);
		indexWriter_li.setMaxMergeDocs(maxMergeDoc);		
		
		
		FileInputStream file = new FileInputStream(sourceDir_li);		
		//InputStreamReader in = new InputStreamReader(file,"UTF-8");
		InputStreamReader in = new InputStreamReader(file,"utf-16");
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
			if(count %10000 == 0)
				System.out.println(count);				
			
			String line = _line;		
			String temp[] = line.split("\t");
			
			if(temp.length<3)
				continue;
			else
			{
				String entity_name = temp[0];
				String predicate_id = temp[1];
				String literal = temp[2];
				entity_name = entity_name.substring(1, entity_name.length()-1).replace('_', ' ');
				literal = literal.substring(1, literal.length()-1);
							
				Document document = new Document(); 
				
				Field EntityName = new Field("EntityName", entity_name,
						Field.Store.YES, Field.Index.NO);
				Field PredicateIdFiled = new Field("PredicateId", predicate_id,
						Field.Store.YES, Field.Index.NO);
				Field Literal = new Field("Literal", literal, 
						Field.Store.YES,
						Field.Index.TOKENIZED,
						Field.TermVector.WITH_POSITIONS_OFFSETS);			
				
				document.add(EntityName);		
				document.add(PredicateIdFiled);
				document.add(Literal);
				indexWriter_li.addDocument(document);
			}			
		}
		
		indexWriter_li.optimize();
		indexWriter_li.close();

		// input the time of Build index
		long endTime = new Date().getTime();
		System.out.println("Literal index has build ->" + count + " " + "Time:" + (endTime - startTime));
	}
	
	public static void main(String[] args)
	{
		BuildIndexForLiteralSubset bls = new BuildIndexForLiteralSubset();
		
		try
		{
			bls.indexforliteral();
		}
		catch (Exception e) 
		{
			e.printStackTrace();
		}
	}
}

