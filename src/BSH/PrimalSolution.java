/*
 * This is a container class to hold solutions to the problem.
 */
package BSH;

import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.CplexStatus;

import java.io.PrintStream;
import java.util.Arrays;

import utils.Environment;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class PrimalSolution {
	public double profit;  // total profit

	public double manQuantity;
	public double remanQuantity;  
	public double[][] plant_DC;  // flow from plant i to DC j
	public double[][][] DC_customers;  // flow from DC i to customer j
	public double[][] customers_RC;  //  flow from customer i to RC j
	public double[] RC_plant;  //  flow from RC i to plant j
	public double[] udc;  // DC used
	public double[] urc;  // RC used
	public IloCplex.CplexStatus status;  // status returned by CPLEX
	
	public int nNodes;
	public int nCuts;

	public PrimalSolution (){

	}
	public PrimalSolution (double profit, double[] udc, double[] urc, double[][] flowPlant_DC,double[][][] flowDC_customers,
			double[][] flowCustomers_RC, double[] flowRC_plant,double flowManQuantity, double flowRemanQuantity, CplexStatus cplexStatus){
		plant_DC = new double[2][Environment.nDC];
		DC_customers = new double[2][Environment.nDC][Environment.nCustomers];
		customers_RC = new double[Environment.nCustomers][Environment.nRC];
		RC_plant = new double[Environment.nRC];

		this.profit = profit;
		this.udc = udc;
		this.urc = urc;
		for(int l = 0; l < 2; l++) {
			plant_DC[l] = Arrays.copyOf(flowPlant_DC[l], udc.length);
		}
		for(int l = 0; l < 2; l++) {
			for(int i = 0; i < Environment.nDC; i++) {
				DC_customers[l][i] = Arrays.copyOf(flowDC_customers[l][i], Environment.nCustomers);
			}
		}
		for(int i = 0; i < Environment.nRC; i++) {
			customers_RC[i] = Arrays.copyOf(flowCustomers_RC[i], urc.length); 
		}
		RC_plant = Arrays.copyOf(flowRC_plant, urc.length);
		manQuantity = flowManQuantity;
		remanQuantity = flowRemanQuantity;
		
		this.status = cplexStatus;
	}

	public void print(PrintStream out) {		
		out.print(String.format("%10.5f", profit));
		out.print(",{");
		for (int i = 0; i < udc.length; i++) {
			if(udc[i] > 0.5) out.print(i + ";");
		}
		
		out.print("},{");
		for (int i = 0; i < udc.length; i++) {
			if(urc[i] > 0.5) out.print(i + ";");
		}
		
		out.println("}");
		
//		out.println("***\nThe incumbent has total profit "
//				+ String.format("%10.5f", profit));
//		
//		out.println("use DC:");
//		for (int i = 0; i < udc.length; i++) {
//			if(udc[i] > 0.5)
//				out.print("\t" + String.format("use_DC_%d = %8.5f", i, udc[i]));
//		}
//		out.println();
//
//		out.println("use RC:");
//		for (int i = 0; i < urc.length; i++) {
//			if(urc[i] > 0.5)
//				out.print("\t" + String.format("use_RC_%d = %8.5f", i, urc[i]));
//		}
//		out.println();
//
//		out.println("plant_DC:");
//		for (int i = 0; i < plant_DC.length; i++) {
//			for( int j = 0; j < plant_DC[0].length; j++) {
//				if(plant_DC[i][j] > 0)
//					out.print("\t" + String.format("plant_DC_%d_%d = %8.5f", i, j, plant_DC[i][j]));
//			}
//		}
//		out.println();
//		out.println("DC_customers:");
//		for (int i = 0; i < DC_customers.length; i++) {
//			for( int j = 0; j < DC_customers[0].length; j++) {
//				for( int k = 0; k < DC_customers[0][0].length; k++) {
//					if(DC_customers[i][j][k] > 0)
//						out.print("\t" + String.format("DC_customers_%d_%d_%d = %8.5f", i, j, k, DC_customers[i][j][k]));
//				}
//			}
//		}
//		out.println();
//		out.println("customers_RC:");
//		for (int i = 0; i < customers_RC.length; i++) {
//			for( int j = 0; j < customers_RC[0].length; j++) {
//				if(customers_RC[i][j] > 0)
//					out.print("\t" + String.format("customers_RC_%d_%d = %8.5f", i, j, customers_RC[i][j]));
//			}
//		}
//		out.println();
//		out.println("RC_plant:");
//		for (int i = 0 ; i < RC_plant.length; i++){
//			if(RC_plant[i] > 0)
//				out.print("\t" + String.format("RC_plant_%d = %8.5f", i, RC_plant[i]));
//		}
//		out.println();
//		out.println("manQuantity:");
//		out.print("\t" + String.format("%8.5f", manQuantity));
//		out.println();
//		out.println("remanQuantity:");
//		out.print("\t" + String.format("%8.5f", remanQuantity));
//		out.println();
//		out.println("***");
	}
	
	@Override
	public boolean equals(Object other){
		if(other instanceof PrimalSolution){
			PrimalSolution s = (PrimalSolution) other;
			if(s.udc.length!= udc.length || s.urc.length != urc.length) return false;
			double eps = 0.00001;
			for(int i=0; i < udc.length; i++){
				if(Math.abs(udc[i]-s.udc[i])> eps) return false;
			}
			for(int i=0; i < urc.length; i++){
				if(Math.abs(urc[i]-s.urc[i])> eps) return false;
			}
			return true;
		}
		else{
			return false;
		}
	}
	
	@Override
	public int hashCode(){
		return udc.hashCode() + urc.hashCode();
	}
	

}
