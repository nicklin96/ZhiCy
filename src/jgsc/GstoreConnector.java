package jgsc;

import java.io.*;
import java.net.*;
import java.lang.*;
import java.net.URLEncoder;
import java.net.URLDecoder;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Map;

public class GstoreConnector {

    public static final String defaultServerIP = "127.0.0.1";
    public static final int defaultServerPort = 9000;

    private String serverIP;
    private int serverPort;
    //private Socket socket = null;

    public GstoreConnector() {
        this.serverIP = GstoreConnector.defaultServerIP;
        this.serverPort = GstoreConnector.defaultServerPort;
    }

    public GstoreConnector(int _port) {
        this.serverIP = GstoreConnector.defaultServerIP;
        this.serverPort = _port;
    }

    public GstoreConnector(String _ip, int _port) {
        this.serverIP = _ip;
        this.serverPort = _port;
    }

	//PERFORMANCE: what if the query result is too large?  receive and save to file directly at once
	//In addition, set the -Xmx larger(maybe in scale of Gs) if the query result could be very large, 
	//this may help to reduce the GC cost
    public String sendGet(String param) {
		String url = "http://" + this.serverIP + ":" + this.serverPort;
        StringBuffer result = new StringBuffer();
        BufferedReader in = null;
		System.out.println("parameter: "+param);

		try {
			param = URLEncoder.encode(param, "UTF-8");
		}
		catch (UnsupportedEncodingException ex) {
			throw new RuntimeException("Broken VM does not support UTF-8");
		}

        try {
            String urlNameString = url + "/" + param;
            System.out.println("request: "+urlNameString);
            URL realUrl = new URL(urlNameString);
            // 闁瑰灚鎸哥槐鎴﹀椽鐎涚ΖL濞戞柨顑夊Λ鍧楁儍閸曨喚绠鹃柟鐚存嫹
            URLConnection connection = realUrl.openConnection();
            // 閻犱礁澧介悿鍡涙焻濮樿鲸鏆忛柣銊ュ椤曨剙效閸屾氨娼ｉ柟顒婃嫹
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
			//set agent to avoid: speed limited by server if server think the client not a browser
            connection.setRequestProperty("user-agent",
                    "Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // 鐎点倛娅ｉ悵娑氾拷鍦仱濡绢垶鎯冮崟顔剧闁圭尨鎷�
            connection.connect();

			long t0 = System.currentTimeMillis(); //ms

            // 闁兼儳鍢茶ぐ鍥箥閿熶粙寮垫径濠冩儥閹煎瓨鏌ㄩ妵鏃傦拷娑欘殕椤旓拷
            Map<String, List<String>> map = connection.getHeaderFields();
            // 闂侇剙绉村濠氬箥閿熶粙寮垫径灞剧暠闁告繂绉寸花鍙夊緞閺夋垹鎽熸繛鍫嫹
            //for (String key : map.keySet()) {
            //    System.out.println(key + "--->" + map.get(key));
            //}

			long t1 = System.currentTimeMillis(); //ms
			//System.out.println("Time to get header: "+(t1 - t0)+" ms");
			//System.out.println("============================================");

            // 閻庤鐭粻锟� BufferedReader閺夊牊鎸搁崣鍡椕规担瑙勯檷閻犲洩顕цぐ鍢L闁汇劌瀚幖閿嬫償閿燂拷
            in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            String line;
            while ((line = in.readLine()) != null) {
				//PERFORMANCE: this can be very costly if result is very large, because many temporary Strings are produced
				//In this case, just print the line directly will be much faster
				result.append(line+"\n");
				//System.out.println("get data size: " + line.length());
				//System.out.println(line);
            }

			long t2 = System.currentTimeMillis(); //ms
			//System.out.println("Time to get data: "+(t2 - t1)+" ms");
        } catch (Exception e) {
            System.out.println("error in get request: " + e);
            e.printStackTrace();
        }
        // 濞达綀娉曢弫顦宨nally闁秆勵殕濞肩敻宕楅幎鑺ワ紨閺夊牊鎸搁崣鍡椕归敓锟�
        finally {
            try {
                if (in != null) {
                    in.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return result.toString();
    }

    public void sendGet(String param, String filename) {
        String url = "http://" + this.serverIP + ":" + this.serverPort;
        BufferedReader in = null;
        System.out.println("parameter: "+param);
        
        if (filename == null)
            return;

        FileWriter fw = null;
        try {
            fw = new FileWriter(filename);
        } catch (IOException e) {
            System.out.println("can not open " + filename + "!");
        }

        try {
            param = URLEncoder.encode(param, "UTF-8");
        } catch (UnsupportedEncodingException ex) {
            throw new RuntimeException("Broken VM does not support UTF-8");
        }

        try {
            String urlNameString = url + "/" + param;
            System.out.println("request: "+urlNameString);
            URL realUrl = new URL(urlNameString);
            // 闁瑰灚鎸哥槐鎴﹀椽鐎涚ΖL濞戞柨顑夊Λ鍧楁儍閸曨喚绠鹃柟鐚存嫹
            URLConnection connection = realUrl.openConnection();
            // 閻犱礁澧介悿鍡涙焻濮樿鲸鏆忛柣銊ュ椤曨剙效閸屾氨娼ｉ柟顒婃嫹
            connection.setRequestProperty("accept", "*/*");
            connection.setRequestProperty("connection", "Keep-Alive");
            //set agent to avoid: speed limited by server if server think the client not a browser
            connection.setRequestProperty("user-agent","Mozilla/4.0 (compatible; MSIE 6.0; Windows NT 5.1;SV1)");
            // 鐎点倛娅ｉ悵娑氾拷鍦仱濡绢垶鎯冮崟顔剧闁圭尨鎷�
            connection.connect();
            
            long t0 = System.currentTimeMillis(); //ms
            
            // 闁兼儳鍢茶ぐ鍥箥閿熶粙寮垫径濠冩儥閹煎瓨鏌ㄩ妵鏃傦拷娑欘殕椤旓拷
            Map<String, List<String>> map = connection.getHeaderFields();
            // 闂侇剙绉村濠氬箥閿熶粙寮垫径灞剧暠闁告繂绉寸花鍙夊緞閺夋垹鎽熸繛鍫嫹
            //for (String key : map.keySet()) {
            //   System.out.println(key + "--->" + map.get(key));
            //}

            long t1 = System.currentTimeMillis(); // ms
            //System.out.println("Time to get header: "+(t1 - t0)+" ms");

            // 閻庤鐭粻锟� BufferedReader閺夊牊鎸搁崣鍡椕规担瑙勯檷閻犲洩顕цぐ鍢L闁汇劌瀚幖閿嬫償閿燂拷
            in = new BufferedReader(new InputStreamReader(connection.getInputStream(), "utf-8"));
            char chars[] = new char[2048];
            int b;
            while ((b = in.read(chars, 0, 2048)) != -1) {
                if (fw != null)
                    fw.write(chars);
                chars = new char[2048];
            }

            long t2 = System.currentTimeMillis(); //ms
            //System.out.println("Time to get data: "+(t2 - t1)+" ms");
        } catch (Exception e) {
            //System.out.println("error in get request: " + e);
            e.printStackTrace();
        }
        // 濞达綀娉曢弫顦宨nally闁秆勵殕濞肩敻宕楅幎鑺ワ紨閺夊牊鎸搁崣鍡椕归敓锟�
        finally {
            try {
                if (in != null) {
                    in.close();
                }
                if (fw != null) {
                    fw.close();
                }
            } catch (Exception e2) {
                e2.printStackTrace();
            }
        }
        return;
    }


//NOTICE: no need to connect now, HTTP connection is kept by default
    public boolean load(String _db_name, String _username, String _password) {
		boolean connect_return = this.connect();
		if (!connect_return) {
			System.err.println("connect to server error. @GstoreConnector.load");
			return false;
		}

        String cmd = "?operation=load&db_name=" + _db_name + "&username=" + _username + "&password=" + _password;
        String msg = this.sendGet(cmd);
        //if (!send_return) {
            //System.err.println("send load command error. @GstoreConnector.load");
            //return false;
        //}

        this.disconnect();

        System.out.println(msg);	
        if (msg.equals("load database done.")) {
            return true;
        }

        return false;
    }

    public boolean unload(String _db_name,String _username, String _password) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.unload");
            return false;
        }

		String cmd = "?operation=unload&db_name=" + _db_name + "&username=" + _username + "&password=" + _password;
        String msg = this.sendGet(cmd);

        this.disconnect();

        System.out.println(msg);	
        if (msg.equals("unload database done.")) {
            return true;
        }

        return false;
    }

    public boolean build(String _db_name, String _rdf_file_path, String _username, String _password) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.build");
            return false;
        }

		//TODO: also use encode to support spaces?
		//Consider change format into ?name=DBname
        String cmd = "?operation=build&db_name=" + _db_name + "&ds_path=" + _rdf_file_path  + "&username=" + _username + "&password=" + _password;;
        String msg = this.sendGet(cmd);

        this.disconnect();

        System.out.println(msg);
        if (msg.equals("import RDF file to database done.")) {
            return true;
        }

        return false;
    }

	//TODO: not implemented
    public boolean drop(String _db_name) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.drop");
            return false;
        }

        String cmd = "drop/" + _db_name;
        String msg = this.sendGet(cmd);

        this.disconnect();

        System.out.println(msg);
        return msg.equals("drop database done.");
    }

    public String query(String _username, String _password, String _db_name, String _sparql) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.query");
            return "connect to server error.";
        }

		//URL encode should be used here
		//try {
		//_sparql = URLEncoder.encode("\""+_sparql+"\"", "UTF-8");
		//}
		//catch (UnsupportedEncodingException ex) {
			//throw new RuntimeException("Broken VM does not support UTF-8");
		//}

		String cmd = "?operation=query&username=" + _username + "&password=" + _password + "&db_name=" + _db_name + "&format=txt&sparql=" + _sparql;
        //String cmd = "query/\"" + _sparql + "\"";
        String msg = this.sendGet(cmd);

        this.disconnect();

        return msg;
    }
    
    public void query(String _username, String _password, String _db_name, String _sparql, String _filename) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.query");
        }

        String cmd = "?operation=query&username=" + _username + "&password=" + _password + "&db_name=" + _db_name + "&format=json&sparql=" + _sparql;
        this.sendGet(cmd, _filename);
      
        this.disconnect();
        
        return;
    }


 //   public String show() {
  //      return this.show(false);
  //  }

	//show all databases
    public String show() {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.show");
            return "connect to server error.";
        }

        String cmd = "?operation=show";
        String msg = this.sendGet(cmd);
        
        this.disconnect();
        return msg;
    }
	 public String user(String type, String username1, String password1, String username2, String addtion) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.show");
            return "connect to server error.";
        }

        String cmd = "?operation=user&type=" + type + "&username1=" + username1 + "&password1=" + password1 + "&username2=" + username2 + "&addtion=" + addtion;
        String msg = this.sendGet(cmd);
        
        this.disconnect();
        return msg;
    }
	 public String showUser() {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.show");
            return "connect to server error.";
        }

        String cmd = "?operation=showUser";
        String msg = this.sendGet(cmd);
        
        this.disconnect();
        return msg;
    }
	 public String monitor(String db_name) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.show");
            return "connect to server error.";
        }

        String cmd = "?operation=monitor&db_name=" + db_name;
        String msg = this.sendGet(cmd);
        
        this.disconnect();
        return msg;
    }
	 public String checkpoint(String db_name) {
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.show");
            return "connect to server error.";
        }

        String cmd = "?operation=checkpoint&db_name=" + db_name;
        String msg = this.sendGet(cmd);
        
        this.disconnect();
        return msg;
    }
	public String test_download(String filepath)
	{
        boolean connect_return = this.connect();
        if (!connect_return) {
            System.err.println("connect to server error. @GstoreConnector.query");
            return "connect to server error.";
        }

		//TEST: a small file, a large file
		String cmd = "?operation=delete&download=true&filepath=" + filepath;
        String msg = this.sendGet(cmd);

        this.disconnect();

        return msg;
	}

    private boolean connect() {
		return true;
    }

    private boolean disconnect() {
		return true;
    }

    private static byte[] packageMsgData(String _msg) {
        //byte[] data_context = _msg.getBytes();
        byte[] data_context = null;
        try {
            data_context = _msg.getBytes("utf-8");
        } catch (UnsupportedEncodingException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            System.err.println("utf-8 charset is unsupported.");
            data_context = _msg.getBytes();
        }
        int context_len = data_context.length + 1; // 1 byte for '\0' at the end of the context.
        int data_len = context_len + 4; // 4 byte for one int(data_len at the data's head).
        byte[] data = new byte[data_len];

        // padding head(context_len).
        byte[] head = GstoreConnector.intToByte4(context_len);
        for (int i = 0; i < 4; i++) {
            data[i] = head[i];
        }

        // padding context.
        for (int i = 0; i < data_context.length; i++) {
            data[i + 4] = data_context[i];
        }
        // in C, there should be '\0' as the terminator at the end of a char array. so we need add '\0' at the end of sending message.
        data[data_len - 1] = 0;

        return data;
    }

    private static byte[] intToByte4(int _x) // with Little Endian format.
    {
        byte[] ret = new byte[4];
        ret[0] = (byte) (_x);
        ret[1] = (byte) (_x >>> 8);
        ret[2] = (byte) (_x >>> 16);
        ret[3] = (byte) (_x >>> 24);

        return ret;
    }

    private static int byte4ToInt(byte[] _b) // with Little Endian format.
    {
        int byte0 = _b[0] & 0xFF, byte1 = _b[1] & 0xFF, byte2 = _b[2] & 0xFF, byte3 = _b[3] & 0xFF;
        int ret = (byte0) | (byte1 << 8) | (byte2 << 16) | (byte3 << 24);

        return ret;
    }

    public static void main(String[] args) {
        // initialize the GStore server's IP address and port.
        GstoreConnector gc = new GstoreConnector("172.31.222.90", 9001);

        // build a new database by a RDF file.
        // note that the relative path is related to gserver.
        //gc.build("db_LUBM10", "example/rdf_triple/LUBM_10_GStore.n3");
		String sparql = "select ?y ?x where {"
				+ "<The_Pagan_Queen>	?y	?x."
				+ "}";

        //boolean flag = gc.load("dbpedia16", "root", "123456");
        //System.out.println(flag);
        String answer = gc.query("root", "123456", "dbpedia16", sparql);
        String[] rawLines = answer.split("\n");
        System.out.println(rawLines.length);
        System.out.println(answer);

		//To count the time cost
		//long startTime=System.nanoTime();   //ns
		//long startTime=System.currentTimeMillis();   //ms
		//doSomeThing();  //婵炴潙顑堥惁顖炴儍閸曨亜鏁╅柣顔荤劍椤旓拷
		//long endTime=System.currentTimeMillis(); //闁兼儳鍢茶ぐ鍥╃磼閹惧瓨灏嗛柡鍐ㄧ埣濡拷
		//System.out.println("缂佸顑呯花顓熸交閹邦垼鏀介柡鍐ㄧ埣濡潡鏁嶉敓锟� "+(end-start)+"ms");
    }
}

