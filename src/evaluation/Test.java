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
		
        //发送 GET 请求
//        String s=HttpRequest.sendGet("http://dbpedia.org/sparql", "default-graph-uri=http%3A%2F%2Fdbpedia.org&query="+uSpq+"&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on");
//        System.out.println(s);
        String s=HttpRequest.sendPost("http://dbpedia.org/sparql", "default-graph-uri=http%3A%2F%2Fdbpedia.org&query="+uSpq+"&format=application%2Fsparql-results%2Bjson&CXML_redir_for_subjs=121&CXML_redir_for_hrefs=&timeout=30000&debug=on");
        System.out.println(s);
        
		String src = "";
//        System.out.println("\n\n\nPost:");
        //发送 POST 请求
//        String sr=HttpRequest.sendPost("https://movie.douban.com/nowplaying/beijing/",src);
        //System.out.println(sr);
        
        
/*
 * json test
 * 
 */
//        String rootStr = "{ \"head\": { \"link\": [], \"vars\": [\"city\"] },  \"results\": { \"distinct\": false, \"ordered\": true, \"bindings\": [    { \"city\": { \"type\": \"uri\", \"value\": \"http://dbpedia.org/resource/Dallas\" }},    { \"city\": { \"type\": \"uri\", \"value\": \"http://dbpedia.org/resource/Texas\" }} ] } }";
//        String results = "";
//        JSONObject rootJson = new JSONObject(rootStr);
//        JSONObject resultsJson = rootJson.getJSONObject("results");
//        JSONArray bindingsJsonArray = resultsJson.getJSONArray("bindings");
//        for(int i=0; i<bindingsJsonArray.length(); i++)
//        {
//        	JSONObject answer = (JSONObject) bindingsJsonArray.get(i);  
//            results += answer.toString() + "\n";
//        	//String userName=(String) answer.get("loginname");  
//        }
//        System.out.println(results);
        
//        JSONObject json=new JSONObject();  
//        JSONArray jsonMembers = new JSONArray();  
//        JSONObject member1 = new JSONObject();  
//        member1.put("loginname", "zhangfan");  
//        member1.put("password", "userpass");  
//        member1.put("email","10371443@qq.com");  
//        member1.put("sign_date", "2007-06-12");  
//        jsonMembers.put(member1);  
//      
//        JSONObject member2 = new JSONObject();  
//        member2.put("loginname", "zf");  
//        member2.put("password", "userpass");  
//        member2.put("email","8223939@qq.com");  
//        member2.put("sign_date", "2008-07-16");  
//        jsonMembers.put(member2);  
//        json.put("users", jsonMembers);  
	
	}
	
}
