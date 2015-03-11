package utils;

public class Triple  {

	/**
	 * The first object in the pair
	 */
	public final double marketSize;
	public final double ret;
	public final double recoveryRate;

	public Triple(double x, double y, double z) {
		this.marketSize = x;
		this.ret = y;
		this.recoveryRate = z;
	}

	public String toString() {
		return "[marketSize=" + marketSize + "\tret=" + ret + "\trecoveryRate=" + recoveryRate + "]";
	}

}
