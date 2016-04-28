package paradict;

public class PredicateIDAndSupport implements Comparable<PredicateIDAndSupport> {
	public int predicateID;
	public int support;
	public double[] wordSelectivity = null;	// wordSelectivity是为了让PATTY的patterns排序排得更好而引入的；事实上，如果support足够准确，就不需要wordSelectivity
	
	public PredicateIDAndSupport(int _pid, int _support, double[] _slct) {
		predicateID = _pid;
		support = _support;
		wordSelectivity = _slct;
	}

	public int compareTo(PredicateIDAndSupport o) {
		return o.support - this.support;
	}

	// 只用于predicate itself和handwrite paraphrase
	public static double[] genSlct(int size) {
		double[] ret = new double[size];
		for (int i=0;i<size;i++) ret[i] = 1.0;
		return ret;
	}
}
