/*
 * This is a  close loop supply chain problem.
 * The purpose is to demonstrate how to code Benders decomposition using CPLEX.
 */
package Exp;

import BSH.BSH;
import BSH.PrimalSolution;
import utils.Environment;
import utils.Monitor;
import utils.SAA;
import utils.Triple;
import ilog.concert.IloException;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class BSHrunner {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		String inputFileName  = "dat/Giengen.xls";
//		String inputFileName  = "dat/Giengen.xls";

		// Check the command line arguments 
		if ( args.length != 1 && args.length != 2) {
			usage();
			return;
		}
		int citysize = -1; //the size of the CLSC problem
		try {
			citysize = Integer.parseInt(args[0]);
			if(citysize<=0 || citysize >40){
				usage();
				return;
			}
		}
		catch(NumberFormatException e){
			usage();
			return;
		}

		if ( args.length == 2 )  inputFileName = args[1];


		Environment.init(citysize, citysize, inputFileName);
		SAA saa = new SAA(2648785*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
				2648785*Environment.PPDensitySum,114840*Environment.PPDensitySum,
				114840*Environment.PPDensitySum,114840*Environment.PPDensitySum,0.7,0.7,0.7,1,1);

		//use only one sample
		Triple[] samples = saa.getSamples(0);

		long start = 0, end = 0;
		/*
		 * Now build and solve a model using Benders decomposition.
		 */
		start = System.currentTimeMillis();
		try {
			BSH model = new BSH(Environment.nDC, Environment.nRC,Environment.nCustomers, 
					Environment.manCapacity,Environment.remanCapacity,
					samples,
					Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
					Environment.flowCost_plant_DC, Environment.flowCost_DC_customers,
					Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
			PrimalSolution s = model.solve();
			s.print(System.out);
		} catch (IloException ex) {
			System.err.println("\n!!!Unable to solve the BSH model:\n"
					+ ex.getMessage() + "\n!!!");
			Monitor.showMemoryUse();
			System.exit(2);
		}
		end = System.currentTimeMillis();
		System.out.println("BSH time = " + (end - start) + "ms\n");
		Monitor.showMemoryUse();
	}

	public static void usage() {
		System.out.println("*** Usage: java BSH [citysize] [filename]");
		System.out.println(" citysize: the size of the CLSC problem range in [1,40]");
		System.out.println("           Size 3 is used if no citysize is provided.");
		System.out.println(" filename: BSH instance file name.");
		System.out.println("           File dat/sales data.xlsx is used if no name is provided.");
	}  

}
