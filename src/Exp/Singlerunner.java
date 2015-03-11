/*
 * This is a  close loop supply chain problem.
 * The purpose is to demonstrate how to code Benders decomposition using CPLEX.
 */
package Exp;

import BSH.SingleModel;
import BSH.PrimalSolution;
import utils.ExcelHandler;
import utils.MatrixHelper;
import ilog.concert.IloException;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class Singlerunner {

	/**
	 * @param args the command line arguments
	 */
	public static void main(String[] args) {
		String fileName  = "dat/Giengen.xls";

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

		if ( args.length == 2 )  fileName = args[1];


		/*
		 * Read the model parameters from the excel file
		 */
		int nDC, nRC, nCustomers;
		nDC = nRC =  nCustomers = citysize;
		double manCapacity = 0, remanCapacity = 0;
		double ret = 0;
		double recoveryRate = 0;
		double marketSize = 0;
		double[] fixedCostDC = null, fixedCostRC= null, PPDensity= null, PPDensity1= null;
		double[] flowCost_plant_DC= null, flowCost_RC_plant= null;
		double[][] flowCost_DC_customers= null, flowCost_customers_RC= null; 
		ExcelHandler handler = new ExcelHandler(fileName);
		PPDensity1 = handler.xlsread("Sheet1", 0, 4, 3 + citysize);
		PPDensity = MatrixHelper.divide(PPDensity1, MatrixHelper.sum(PPDensity1));
		manCapacity = MatrixHelper.sum(PPDensity1) * handler.xlsread("Sheet1", 1, 0);
		remanCapacity = MatrixHelper.sum(PPDensity1) * handler.xlsread("Sheet1", 1, 1);
		ret = MatrixHelper.sum(PPDensity1) * handler.xlsread("SG2 experiment data (1)", 7, 3);
		marketSize = MatrixHelper.sum(PPDensity1) * handler.xlsread("SG2 experiment data (1)", 6, 3);
		recoveryRate = handler.xlsread("SG2 experiment data (1)", 5, 3);

		//ret, marketSize and recoveryRate should be randomly generated after debug;
		fixedCostDC = handler.xlsread("Sheet1", 3,  4, 3 + citysize);
		fixedCostRC = handler.xlsread("Sheet1", 5,  4, 3 + citysize);
		flowCost_plant_DC = handler.xlsread("distance", 11, 1, citysize);
		flowCost_DC_customers = handler.xlsread("transportation cost", 0,  citysize - 1, 1, citysize);
		flowCost_customers_RC = handler.xlsread("transportation cost", 0,  citysize - 1, 1, citysize);
		flowCost_RC_plant = handler.xlsread("distance", 11, 1, citysize);

		handler.close();
		long start = 0, end = 0;
		/*
		 * Build a single MIP model and solve it, as a benchmark.
		 */
		start = System.currentTimeMillis();
		try {
			SingleModel model = new SingleModel(nDC, nRC,nCustomers, manCapacity,remanCapacity,
					ret,recoveryRate,marketSize,fixedCostDC,fixedCostRC,PPDensity,
					flowCost_plant_DC, flowCost_DC_customers, flowCost_customers_RC,flowCost_RC_plant);
			PrimalSolution s = model.solve();
			s.print(System.out);
		} catch (IloException ex) {
			System.err.println("\n!!!Unable to solve the unified model:\n"
					+ ex.getMessage() + "\n!!!");
			System.exit(1);
		}
		end = System.currentTimeMillis();
		System.out.println("single model time = " + (end - start) + "ms\n");
	}

	public static void usage() {
		System.out.println("*** Usage: java BSH [citysize] [filename]");
		System.out.println(" citysize: the size of the CLSC problem range in [1,40]");
		System.out.println("           Size 3 is used if no citysize is provided.");
		System.out.println(" filename: BSH instance file name.");
		System.out.println("           File dat/sales data.xlsx is used if no name is provided.");
	}  

}
