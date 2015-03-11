package BSH;

public class DualSolution {
	public double q_new;
	public double q_reman;
	public double[] gamma;
	public double[][] epsilon_DC_new;
	public double[][] epsilon_DC_reman;
	public double[][] epsilon_RC;
	public double[] chi;
	
	public DualSolution(double q_new, double q_reman, double[] gamma, 
				double[][] epsilon_DC_new, double[][] epsilon_DC_reman, double[][] epsilon_RC, double[] chi){
		this.q_new = q_new;
		this.q_reman = q_reman;
		this.gamma = gamma;	
		this.epsilon_DC_new = epsilon_DC_new;
		this.epsilon_DC_reman = epsilon_DC_reman;
		this.epsilon_RC = epsilon_RC;
		this.chi = chi;
	}
	
	//TODO
	public void print() {
		
	}

}
