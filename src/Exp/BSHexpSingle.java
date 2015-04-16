package Exp;

import ilog.concert.IloException;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintStream;
import java.util.ArrayList;

import utils.Environment;
import utils.Monitor;
import utils.SAA;
import utils.Triple;
import BSH.PrimalSolution;
import BSH.SingleModel;

/*
 * read 40 set of  from file commodity price and save maxval, maxvar, finalUseDC, finalUseRC
 */
public class BSHexpSingle {
	public static void main(String args[]){
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

		/*
		 * init the environment
		 */
		Environment.init(citysize, facilitySize, inputFileName);

		/*
		 * read commodity prices
		 */
		ArrayList<Double> marketsList = new ArrayList<Double>();
		ArrayList<Double> returnList = new ArrayList<Double>();
		ArrayList<Double> rateList = new ArrayList<Double>();
		BufferedReader br = null;
		try {
			String line;
			br = new BufferedReader(new FileReader("dat/strategic analysis.txt"));
			while ((line = br.readLine()) != null) {
				String[] elems = line.split(",");
				marketsList.add(Double.parseDouble(elems[0]));
				returnList.add(Double.parseDouble(elems[1]));
				rateList.add(Double.parseDouble(elems[2]));
			}
		} catch (IOException e) {
			e.printStackTrace();
		} finally {
			try {
				if (br != null)br.close();
			} catch (IOException ex) {
				ex.printStackTrace();
			}
		}

		long start = 0, end = 0;
		/*
		 * Now build and solve a model using Benders decomposition.
		 */
		String outFileName  = "out/"+citysize+"_"+facilitySize+"_.txt";
		try {
			PrintStream out = new PrintStream(new FileOutputStream(outFileName));
			for(int i = 0; i < marketsList.size(); i++){
				start = System.currentTimeMillis();
				
				SAA saa = new SAA(marketsList.get(i)*Environment.PPDensitySum,
								  marketsList.get(i)*Environment.PPDensitySum,
								  marketsList.get(i)*Environment.PPDensitySum,
								  returnList.get(i)*Environment.PPDensitySum,
								  returnList.get(i)*Environment.PPDensitySum,
								  returnList.get(i)*Environment.PPDensitySum,
								  rateList.get(i), rateList.get(i), rateList.get(i),1,1);

				//use only one sample
				Triple[] samples = saa.getSamples(0);

				double marketSize = samples[0].marketSize;
				double ret = samples[0].ret;
				double recoveryRate = samples[0].recoveryRate;
				/*
				 * Build a single MIP model and solve it, as a benchmark.
				 */
				start = System.currentTimeMillis();

				SingleModel model = new SingleModel(Environment.nDC, Environment.nRC,Environment.nCustomers, 
						Environment.manCapacity,Environment.remanCapacity,
						ret,recoveryRate,marketSize,
						Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
						Environment.flowCost_plant_DC, Environment.flowCost_DC_customers, 
						Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
				PrimalSolution s = model.solve();
				//flowCost_plant_DC*(plant_DC_0+plant_DC_1)
				int sum = 0;
				for(int j=0; j<Environment.flowCost_plant_DC.length;j++){
					sum += Environment.flowCost_plant_DC[j]*(s.plant_DC[0][j] + s.plant_DC[1][j]);
				}

				//flowCost_DC_customers*(DC_customers_0+DC_customers_1
				for(int j=0; j<Environment.flowCost_DC_customers.length;j++){
					for(int k=0; k<Environment.flowCost_DC_customers[0].length;k++){
						sum += Environment.flowCost_DC_customers[j][k]*(s.DC_customers[0][j][k] + s.DC_customers[1][j][k]);
					}
				}
				//flowCost_customers_RC*customers_RC
				for(int j=0; j<Environment.flowCost_customers_RC.length;j++){
					for(int k=0; k<Environment.flowCost_customers_RC[0].length;k++){
						sum += Environment.flowCost_customers_RC[j][k]*s.customers_RC[j][k];
					}
				}

				//flowCost_RC_plant*RC_plant
				for(int j=0; j<Environment.flowCost_RC_plant.length;j++){
					sum += Environment.flowCost_RC_plant[j]*s.RC_plant[j];
				}

				s.print(System.out);
				System.out.println("sum: " + sum);
				System.out.println("return:" + ret);
				System.out.println("recoveryRate:" + recoveryRate);
				System.out.println("effective recovery rate:" + s.remanQuantity/(ret * recoveryRate)); //alpha effective			

				out.println(marketsList.get(i) + 
						"," + returnList.get(i) + 
						"," + rateList.get(i) + 
						"," + sum + 
						"," + ret + 
						"," + recoveryRate +
						"," + s.remanQuantity/(ret * recoveryRate));
				end = System.currentTimeMillis();
				System.out.println("single model time = " + (end - start) + "ms\n");
				Monitor.runGC();
			}
			out.close();
		} catch (IloException ex) {
			System.err.println("\n!!!Unable to solve the BSH model:\n"
					+ ex.getMessage() + "\n!!!");
			Monitor.showMemoryUse();
			System.exit(2);
		}catch(FileNotFoundException e1){
			System.err.println("\n!!!File not found exception:\n"
					+ e1.getMessage() + "\n!!!");
		}
	}

	public static void usage() {
		System.out.println("*** Usage: java BSH [citysize] [facilitySize] [filename]");
		System.out.println(" citysize: the size of the city range in [1,40]");
		System.out.println("           Size 3 is used if no citysize is provided.");
		System.out.println(" facilitySize: the size of the facility range in [1,40]");
		System.out.println("           Size 3 is used if no facilitySize is provided.");
		System.out.println(" Input: BSH instance file name.");
		System.out.println("           File dat/sales data.xlsx is used if no name is provided.");
	}  
}
