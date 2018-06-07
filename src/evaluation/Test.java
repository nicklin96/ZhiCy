package evaluation;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import utils.HttpRequest;

public class Test {

	public static void main(String[] args) throws UnsupportedEncodingException, JSONException {
			
		String uSpq = URLEncoder.encode("select DISTINCT ?name where { <http://dbpedia.org/resource/Charles,_Prince_of_Wales> dbp:fullName ?name. }","gbk");
		String uSpq1 = URLEncoder.encode("select ?birthdays where {?actors dbo:birthDate ?birthdays. dbr:Charmed dbo:starring ?actors. }","gbk");
		
        //GET
//        String s=HttpRequest.sendGet("http://dbpedia.org/sparql", "default-graph-uri=http%3A%2F%2Fdbpedia.org&query="+uSpq+"&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on");
//        System.out.println(s);
        String s=HttpRequest.sendPost("http://dbpedia.org/sparql", "default-graph-uri=http%3A%2F%2Fdbpedia.org&query="+uSpq+"&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on");
        System.out.println(s);
        
		String src = "";
//        System.out.println("\n\n\nPost:");
        //POST
//        String sr=HttpRequest.sendPost("https://movie.douban.com/nowplaying/beijing/",src);
        //System.out.println(sr);
	
	}
	
}
