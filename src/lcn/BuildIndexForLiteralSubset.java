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
 *  Lucene建立索引的基本单元是document，同时其中的域filed可以更具需要自己添加
 * 
 * Document是一个记录，用来表示一个条目，相当于数据库中的一行记录，就是搜索建立的倒排索引的条目。
 * eg:你要搜索自己电脑上的文件，这个时候就可以创建field(字段,相关于数据库中的列。 然后用field组合成document，最后会变成若干文件。
 * 这个document和文件系统document不是一个概念。
 * 
 * StandardAnalyzer是lucene中内置的"标准分析器",可以做如下功能: 
 * 1、对原有句子按照空格进行了分词
 * 2、所有的大写字母都可以能转换为小写的字母 
 * 3、可以去掉一些没有用处的单词，例如"is","the","are"等单词，也删除了所有的标点
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
		
		int mergeFactor = 100000;    //默认是10
		int maxBufferedDoc = 1000;  // 默认是10
		int maxMergeDoc = Integer.MAX_VALUE;  //默认无穷大
		
		//indexWriter.DEFAULT_MERGE_FACTOR = mergeFactor;
		indexWriter_li.setMergeFactor(mergeFactor);
		indexWriter_li.setMaxBufferedDocs(maxBufferedDoc);
		indexWriter_li.setMaxMergeDocs(maxMergeDoc);		
		
		
		FileInputStream file = new FileInputStream(sourceDir_li);		
		//InputStreamReader in = new InputStreamReader(file,"UTF-8");
		InputStreamReader in = new InputStreamReader(file,"utf-16");
		BufferedReader br = new BufferedReader(in);		
		
		int count = 0;
		
		//最初是用 sc.hasNext() 判断是否有下一行
		//或者直接用 br.readLine()
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


