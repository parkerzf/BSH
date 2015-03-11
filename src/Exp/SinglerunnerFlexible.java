/*
 * This is a  close loop supply chain problem.
 * The purpose is to demonstrate how to code Benders decomposition using CPLEX.
 */
package Exp;

import BSH.SingleModel;
import BSH.PrimalSolution;
import utils.Environment;
import utils.SAA;
import utils.Triple;
import ilog.concert.IloException;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class SinglerunnerFlexible {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		String inputFileName  = "dat/Giengen.xls";
		int citysize = -1; 
		int facilitySize = -1;

		// Check the command line arguments 
		if (args.length != 2 && args.length != 3) {
			usage();
			return;
		}

		if(args.length >=2){
			try {
				citysize = Integer.parseInt(args[0]);
				if(citysize<=0 || citysize >40){
					usage();
					return;
				}
				facilitySize = Integer.parseInt(args[1]);
				if(facilitySize<=0 || facilitySize >40){
					usage();
					return;
				}
			}
			catch(NumberFormatException e){
				usage();
				return;
			}
		}

		if ( args.length == 3 )  inputFileName = args[2];

		Environment.init(citysize, facilitySize, inputFileName);
//		SAA saa = new SAA(2383906.5*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
//				2913663.5*Environment.PPDensitySum,28710*Environment.PPDensitySum,
//				114840*Environment.PPDensitySum,200970*Environment.PPDensitySum,0.525,0.7,0.875,1,1);
		SAA saa = new SAA(2648785*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
				2648785*Environment.PPDensitySum,114840*Environment.PPDensitySum,
				114840*Environment.PPDensitySum,114840*Environment.PPDensitySum,0.7,0.7,0.7,1,1);
//
//		//use only one sample
		Triple[] samples = saa.getSamples(0);

//		double marketSize = 1834167* Environment.PPDensitySum;
//		double ret = 91680 * Environment.PPDensitySum;
//		double recoveryRate = 0.7;
//		double marketSize = 2648785 * Environment.PPDensitySum;
//		double ret = 114840 * Environment.PPDensitySum;
//		double recoveryRate = 0.5;
		double marketSize = samples[0].marketSize;
		double ret = samples[0].ret;
		double recoveryRate = samples[0].recoveryRate;
		long start = 0, end = 0;
		/*
		 * Build a single MIP model and solve it, as a benchmark.
		 */
		start = System.currentTimeMillis();
		try {
			SingleModel model = new SingleModel(Environment.nDC, Environment.nRC,Environment.nCustomers, 
					Environment.manCapacity,Environment.remanCapacity,
					ret,recoveryRate,marketSize,
					Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
					Environment.flowCost_plant_DC, Environment.flowCost_DC_customers, 
					Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
			PrimalSolution s = model.solve();
			//flowCost_plant_DC*(plant_DC_0+plant_DC_1)
			int sum = 0;
			for(int i=0; i<Environment.flowCost_plant_DC.length;i++){
				sum += Environment.flowCost_plant_DC[i]*(s.plant_DC[0][i] + s.plant_DC[1][i]);
			}
			
			//flowCost_DC_customers*(DC_customers_0+DC_customers_1
			for(int i=0; i<Environment.flowCost_DC_customers.length;i++){
				for(int j=0; j<Environment.flowCost_DC_customers[0].length;j++){
					sum += Environment.flowCost_DC_customers[i][j]*(s.DC_customers[0][i][j] + s.DC_customers[1][i][j]);
				}
			}
			//flowCost_customers_RC*customers_RC
			for(int i=0; i<Environment.flowCost_customers_RC.length;i++){
				for(int j=0; j<Environment.flowCost_customers_RC[0].length;j++){
					sum += Environment.flowCost_customers_RC[i][j]*s.customers_RC[i][j];
				}
			}
			
			//flowCost_RC_plant*RC_plant
			for(int i=0; i<Environment.flowCost_RC_plant.length;i++){
				sum += Environment.flowCost_RC_plant[i]*s.RC_plant[i];
			}
			
			s.print(System.out);
			System.out.println("sum: " + sum);
			System.out.println("return:" + ret);
			System.out.println("recoveryRate:" + recoveryRate);
			System.out.println("effective recovery rate:" + s.remanQuantity/(ret * recoveryRate)); //alpha effective			
		} catch (IloException ex) {
			System.err.println("\n!!!Unable to solve the unified model:\n"
					+ ex.getMessage() + "\n!!!");
			System.exit(1);
		}
		end = System.currentTimeMillis();
		System.out.println("single model time = " + (end - start) + "ms\n");
	}

	public static void usage() {
		System.out.println("*** Usage: java BSH [citysize] [facilitySize] [filename]");
		System.out.println(" citysize: the size of the city range in [1,40]");
		System.out.println("           Size 3 is used if no citysize is provided.");
		System.out.println(" facilitySize: the size of the facility range in [1,40]");
		System.out.println("           Size 3 is used if no facilitySize is provided.");
		System.out.println(" filename: BSH instance file name.");
		System.out.println("           File dat/sales data.xlsx is used if no name is provided.");
	}  

}
