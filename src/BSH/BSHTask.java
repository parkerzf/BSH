package BSH;

import java.io.PrintStream;
import java.util.ArrayList;
import java.util.HashSet;

import ilog.concert.IloException;
import utils.Environment;
import utils.MatrixHelper;
import utils.Monitor;
import utils.SAA;
import utils.Triple;

/**
 * The task class is used to generate a bsh task
 * @author Zhao Feng
 *
 */
public class BSHTask {
	private int M;
	private int N;
	private int Nprim;

	private double minMarket;
	private double medianMarket;
	private double maxMarket;
	private double minReturn;
	private double medianReturn;
	private double maxReturn;
	private double minRate;
	private double medianRate;
	private double maxRate;


	private PrintStream out = null;

	private double fval_mean = 0, fval_var = 0;
	private ArrayList<Double> fvals = null;
	private HashSet<PrimalSolution> solutions = new HashSet<PrimalSolution>();
	private ArrayList<PrimalSolution> solutionList = new ArrayList<PrimalSolution>();
	private long[] times = null;
	public BSHTask(int M, int N, int Nprim, double minMarket, double medianMarket, double maxMarket,
			double minReturn, double medianReturn, double maxReturn, 
			double minRate, double medianRate, double maxRate, PrintStream out){
		this.M = M;
		this.N = N;
		this.Nprim = Nprim;

		this.minMarket = minMarket;
		this.medianMarket = medianMarket;
		this.maxMarket = maxMarket;

		this.minReturn = minReturn;
		this.medianReturn = medianReturn;
		this.maxReturn = maxReturn;

		this.minRate = minRate;
		this.medianRate = medianRate;
		this.maxRate = maxRate;

		this.times = new long[M];
		this.out = out;
	}

	public void run() throws IloException{
		stepOne();
		if(Nprim!=0) stepTwo();
	}


	private void stepOne() {
		System.out.println("start step one");
//		SAA saa = new SAA(1589271*Environment.PPDensitySum,3708299*Environment.PPDensitySum,
//				2648785*Environment.PPDensitySum,28710*Environment.PPDensitySum,
//				200970*Environment.PPDensitySum,114840*Environment.PPDensitySum,0.525,0.875,0.7,M,N);
		//		SAA saa = new SAA(1834167*Environment.PPDensitySum,1834167*Environment.PPDensitySum,
		//				1834167*Environment.PPDensitySum,91680*Environment.PPDensitySum,
		//				91680*Environment.PPDensitySum,91680*Environment.PPDensitySum,0.7,0.7,0.7,M,N);
		//		SAA saa = new SAA(minMarket*Environment.PPDensitySum,maxMarket*Environment.PPDensitySum,
		//				medianMarket*Environment.PPDensitySum,minReturn*Environment.PPDensitySum,
		//				maxReturn*Environment.PPDensitySum,medianReturn*Environment.PPDensitySum,
		//				minRate, maxRate, medianRate,M,N);
//		SAA saa = new SAA(2383906*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
//				2913663*Environment.PPDensitySum,28710*Environment.PPDensitySum,
//				114840*Environment.PPDensitySum,200970*Environment.PPDensitySum,0.525,0.7,0.875,M,N);
		// 75% of BSH total return, 2 STD
//		SAA saa = new SAA(2383906*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
//				2913663*Environment.PPDensitySum,215325*Environment.PPDensitySum,
//				861300*Environment.PPDensitySum,1507275*Environment.PPDensitySum,0.525,0.7,0.875,M,N);		fvals = new ArrayList<Double>();
		// 10% of BSH total return, 40 fixed scenario (Referee #3 last comment)
		SAA saa = new SAA(2451679*Environment.PPDensitySum,2451679*Environment.PPDensitySum,
				2451679*Environment.PPDensitySum,714289.2506*Environment.PPDensitySum,
				714289.2506*Environment.PPDensitySum,714289.2506*Environment.PPDensitySum,0.670,0.670,0.670,M,N);		fvals = new ArrayList<Double>();
		long start = 0, end = 0;
		for(int i = 0; i<M; i++){
			System.out.println("start the sample set " + i);
			Triple[] samples  = saa.getSamples(i);

			try {
				start = System.currentTimeMillis();
				BSH model = new BSH(Environment.nDC, Environment.nRC,Environment.nCustomers, 
						Environment.manCapacity,Environment.remanCapacity,
						samples,
						Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
						Environment.flowCost_plant_DC, Environment.flowCost_DC_customers,
						Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
				PrimalSolution s = model.solve();
				end = System.currentTimeMillis();
				model.clear();

				solutions.add(s);
				solutionList.add(s);
				fvals.add(s.profit);
				times[i] = end - start;
				System.out.println("end the sample set " + i);
				s.print(System.out);
			} catch (IloException ex) {
				System.err.println("\n!!!Unable to solve the BSH model:\n"
						+ ex.getMessage() + "\n!!!");
				System.exit(2);
			}
			Monitor.runGC();
		}
		System.out.println("end step one");

		fval_mean = MatrixHelper.getMean(fvals);
		fval_var = MatrixHelper.getVariance(fvals);
		System.out.println("fval_mean: " + fval_mean);
		System.out.println("fval_var: " + fval_var);
		//outputStepOne();
	}

	private void stepTwo() throws IloException {
		SAA saa = null;
		double maxfval = Double.NEGATIVE_INFINITY;
		double maxVar = Double.NEGATIVE_INFINITY;
		double[] finalUseDC = null;
		double[] finalUseRC = null;

		System.out.println("start step two");
		for(PrimalSolution s: solutions){
//			saa = new SAA(1589271*Environment.PPDensitySum,3708299*Environment.PPDensitySum,
//					2648785*Environment.PPDensitySum,28710*Environment.PPDensitySum,
//					200970*Environment.PPDensitySum,114840*Environment.PPDensitySum,0.525,0.875,0.7,1,Nprim);
			//saa = new SAA(minMarket*Environment.PPDensitySum,maxMarket*Environment.PPDensitySum,
			//		medianMarket*Environment.PPDensitySum,minReturn*Environment.PPDensitySum,
			//		maxReturn*Environment.PPDensitySum,medianReturn*Environment.PPDensitySum,
			//		minRate, maxRate, medianRate,1,Nprim);
//			saa = new SAA(2383906*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
//					2913663*Environment.PPDensitySum,28710*Environment.PPDensitySum,
//					114840*Environment.PPDensitySum,200970*Environment.PPDensitySum,0.525,0.7,0.875,1,Nprim);	
			// 75% of BSH total return, 2 STD
			saa = new SAA(2383906*Environment.PPDensitySum,2648785*Environment.PPDensitySum,
					2913663*Environment.PPDensitySum,215325*Environment.PPDensitySum,
					861300*Environment.PPDensitySum,1507275*Environment.PPDensitySum,0.525,0.7,0.875,1,Nprim);
			
			double[] meanAndVar = LargeSampleComputation(s.udc,s.urc, saa);
			
			//taking a constant value into fval
			double meanRet = 0;
			for(Triple triple: saa.getSamples(0)){
				meanRet += triple.ret;
			}
			
			meanRet /= Nprim;
			meanAndVar[0] -= (Environment.holdingCost + Environment.disposalCost)*meanRet;

			if (meanAndVar[0] > maxfval){
				maxfval = meanAndVar[0];
				maxVar = meanAndVar[1];
				finalUseDC = s.udc;
				finalUseRC = s.urc;
			}
			Monitor.runGC();
		}
		System.out.println("end step two");
		System.out.println("maxfval: "+maxfval);
		System.out.println("maxVar: "+maxVar);

		System.out.println("finalVar: "+ (fval_var + maxVar));

		System.out.println("finalUseDC: ");
		StringBuilder finalUseDCSb = new StringBuilder();
		for (int i = 0; i < finalUseDC.length; i++) {
			if(finalUseDC[i] > 0.5){
				System.out.print("\t" + String.format("DC[%d] = %3.1f", i, finalUseDC[i]));
			}
			finalUseDCSb.append((int)finalUseDC[i]);
			finalUseDCSb.append(" ");
		}
		System.out.println();

		System.out.println("finalUseRC:");
		StringBuilder finalUseRCSb = new StringBuilder();
		for (int i = 0; i < finalUseRC.length; i++) {
			if(finalUseRC[i] > 0.5){
				System.out.print("\t" + String.format("RC[%d] = %3.1f", i, finalUseRC[i]));
			}
			finalUseRCSb.append((int)finalUseRC[i]);
			finalUseRCSb.append(" ");
		}
		System.out.println();
		out.println(Environment.disposalCost + 
				"," + maxfval + 
				"," + maxVar + 
				"," + finalUseDCSb +
				"," + finalUseRCSb);
	}

	private double[] LargeSampleComputation(double[] udc,
			double[] urc, SAA saa) throws IloException {
		BSHNPrim model = new BSHNPrim(Environment.nDC, Environment.nRC,Environment.nCustomers, 
				Environment.manCapacity,Environment.remanCapacity,
				saa.getSamples(0),
				Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
				Environment.flowCost_plant_DC, Environment.flowCost_DC_customers,
				Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
		return model.getExpectfvalandVarWithNprimSamples(udc,urc);
	}


	/*
	 * print the task result to a print stream
	 */
	private void outputStepOne(){
		out.print("Selected Facilities: ");
		for (int i = 0; i < Environment.selectedFacilities.length; i++) {
			out.print(Environment.selectedFacilities[i] + " ");
		}
		out.println();
		for(int i = 0; i< M; i++){
			out.println("***");
			out.println("time: " +times[i] + "ms");
			out.println("use DC:");
			for (int j = 0; j < solutionList.get(i).udc.length; j++) {
				if(solutionList.get(i).udc[j] > 0.5)
					out.print("\t" + String.format("use_DC_%d = %3.1f", j, solutionList.get(i).udc[j]));
			}
			out.println();

			out.println("use RC:");
			for (int j = 0; j < solutionList.get(i).urc.length; j++) {
				if(solutionList.get(i).urc[j] > 0.5)
					out.print("\t" + String.format("use_RC_%d = %3.1f", j, solutionList.get(i).urc[j]));
			}
			out.println();
			out.println("manQuantity:");
			out.print("\t" + String.format("%8.5f", solutionList.get(i).manQuantity));
			out.println();
			out.println("remanQuantity:");
			out.print("\t" + String.format("%8.5f", solutionList.get(i).remanQuantity));
			out.println();
			out.println("profit: "+ fvals.get(i));
			out.println("num of Nodes: "+ solutionList.get(i).nNodes);
			out.println("num of Cuts: "+ solutionList.get(i).nCuts);

			out.println("***");
		}
		out.println("fval_mean: " + fval_mean);
		out.println("fval_var: " + fval_var);
	}


}
