package utils;

/**
 * The class to generate SAA samples
 * @author Zhao Feng
 *
 */

public class SAA {
	private Triple[][] sampleMatrix;

	public SAA(double M_a, double M_c, double M_b, double r_a, double r_c,
			double r_b, double alpha_a, double alpha_c, double alpha_b, int M,int N){
		sampleMatrix = new Triple[M][N];
		for(int i = 0; i < M; i++){
			for(int j = 0; j < N; j++){
				sampleMatrix[i][j] =  createTriple(M_a, M_c, M_b, r_a, r_c, r_b, alpha_a,alpha_c,alpha_b);
			}
		}

	}
	
	public Triple getSample(int row, int col){
		if(row < 0 || row > sampleMatrix.length-1)
			System.err.println("the row "+row+" out of sample matrix bound!");
		if(col < 0 || col > sampleMatrix[0].length-1)
			System.err.println("the col "+col+" out of sample matrix bound!");
		return sampleMatrix[row][col];
	}
	
	public Triple[]   getSamples(int row){
		if(row < 0 || row > sampleMatrix.length-1)
			System.err.println("the row "+row+" out of sample matrix bound!");
		return sampleMatrix[row];
	}

	private Triple createTriple(double M_a, double M_c, double M_b, double r_a,
			double r_c, double r_b, double alpha_a, double alpha_c,
			double alpha_b) {
		double M = trand(M_a, M_c, M_b);
		double r = trand(r_a, r_c, r_b);
		double alpha = trand(alpha_a, alpha_c, alpha_b);

		return new Triple(M, r, alpha);
	}

	private double trand(double a, double c, double b) {
		double sample = c+Math.sqrt(Math.random())*(a-c+Math.random()*(b-a));
		return sample;
	}
}
