package Exp;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.PrintStream;

import ilog.concert.IloException;
import BSH.BSHTask;
import utils.Environment;
import utils.Monitor;

public class BSHexp {
	public static void main(String args[]){
		String inputFileName  = "dat/Giengen.xls";
		int citysize = -1; 
		int facilitySize = -1;
		int M = 1, N = 1, Nprim = 0;

		// Check the command line arguments 
		if (args.length != 2 && args.length != 5 &&args.length != 6) {
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

		if(args.length >=5){
			try {
				M = Integer.parseInt(args[2]);
				N = Integer.parseInt(args[3]);
				Nprim = Integer.parseInt(args[4]);
			}
			catch(NumberFormatException e){
				usage();
				return;
			}
		}

		if ( args.length == 6 )  inputFileName = args[5];

		/*
		 * init the environment
		 */
		Environment.init(citysize, facilitySize, inputFileName);

		long start = 0, end = 0;
		/*
		 * Now build and solve a model using Benders decomposition.
		 */
//		for(int i = 0 ; i < Environment.minMarkets.length; i++){
		for(int i = 0 ; i < 1; i++){
			start = System.currentTimeMillis();
			String outFileName  = "out/"+citysize+"_"+facilitySize+"_"+M+"_"+N + "_"+i+"_"+ System.currentTimeMillis()+".txt";
			try {
				PrintStream out = new PrintStream(new FileOutputStream(outFileName));

				BSHTask task = new BSHTask(M,N,Nprim,
						Environment.minMarkets[i],Environment.medianMarkets[i],Environment.maxMarkets[i],
						Environment.minReturns[i],Environment.medianReturns[i],Environment.maxReturns[i],
						Environment.minRates[i],Environment.medianRates[i],Environment.maxRates[i],
						out);  
				task.run();
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
			end = System.currentTimeMillis();
			System.out.println("BSH SAA time = " + (end - start) + "ms\n");
			Monitor.runGC();

		}
	}

	public static void usage() {
		System.out.println("*** Usage: java BSHSB [citysize] [facilitySize] [filename]");
		System.out.println(" citysize: the size of the city range in [1,40]");
		System.out.println("           Size 3 is used if no citysize is provided.");
		System.out.println(" facilitySize: the size of the facility range in [1,40]");
		System.out.println("           Size 3 is used if no facilitySize is provided.");
		System.out.println(" M: the size of the samples");
		System.out.println("           Size 1 is used if no M is provided.");
		System.out.println(" N: the size of the scenarios in step one");
		System.out.println("           Size 1 is used if no N is provided.");
		System.out.println(" Nprim: the size of the scenarios in step two");
		System.out.println("           Size 1 is used if no Nprim is provided.");
		System.out.println(" Input: BSH instance file name.");
		System.out.println("           File dat/sales data.xlsx is used if no name is provided.");
	}  
}
