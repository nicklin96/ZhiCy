package lcn;

public class LiteralSubsetFields {
	public String entityName;
	public int pred_id;
	public String literal;
	public double score;
	
	public LiteralSubsetFields(String n, int p, String l, double s) {
		entityName = n;
		pred_id = p;
		literal = l;
		score = s;
	}
}
