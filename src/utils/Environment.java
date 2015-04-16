package utils;
import java.util.ArrayList;
import java.util.Random;

public class Environment {
	//constant parameters 
	public final static double reservationPriceUB = 600; 
	public final static double reservationPriceLB = 370;
	public final static double remanDepreciation = 0.7;
//	public final static double manPrice = ;
	public final static double manCost = 370;
	public final static double remanCost = 225; 
	public final static double holdingCost = 10;
	public static double disposalCost = 50;
	public final static double FUZZ = 1.0e3;
	public final static  int POOLSIZE = 40;

	//parameters read from excel
	public static double manCapacity = 0, remanCapacity = 0, PPDensitySum = 0;
	public static double[] fixedCostDC = null, fixedCostRC= null, PPDensity= null, PPDensity1= null;
	public static double[] flowCost_plant_DC= null, flowCost_RC_plant= null;
	public static double[][] flowCost_DC_customers= null, flowCost_customers_RC= null; 

	//parameters from command line input
	public static int nDC, nRC, nCustomers;
	public static String fileName = null;
	public static Integer[] selectedFacilities;
	
	//parameters for SAA
	public static double[] minMarkets;
	public static double[] maxMarkets;
	public static double[] medianMarkets;
	
	public static double[] minReturns;
	public static double[] maxReturns;
	public static double[] medianReturns;
	
	public static double[] minRates;
	public static double[] maxRates;
	public static double[] medianRates;
	


	public static void init(int citysize, int facilitySize, String fileName){
		Environment.fileName = fileName;
		if(citysize<facilitySize){
			System.err.println("the city size should be larger than or equal to the facility size!" );
			System.exit(0);
		}
		
		nCustomers = citysize;
		nDC = nRC = facilitySize;
		
		
		PPDensity1 = new double[citysize];
		fixedCostDC = new double[facilitySize];
		fixedCostRC = new double[facilitySize];
		flowCost_plant_DC = new double[facilitySize];
		flowCost_DC_customers = new double[facilitySize][citysize];
		flowCost_customers_RC = new double[citysize][facilitySize];
		flowCost_RC_plant = new double [facilitySize];
		
		ExcelHandler handler = new ExcelHandler(fileName);
		//randomly generate facility candidates:
		Random r = new Random();
//		HashSet<Integer> fixedFacilities = new HashSet<Integer>();
//		fixedFacilities.add(10);
//		fixedFacilities.add(7);
//		fixedFacilities.add(3);
//		fixedFacilities.add(4);
//		fixedFacilities.add(13);
		ArrayList<Integer> facilityCandidates = new ArrayList<Integer>();
		for(int i = 1 ; i <= POOLSIZE; i++){
//			if(!fixedFacilities.contains(i))
				facilityCandidates.add(i);
		}
		
		if(facilitySize>POOLSIZE){
			System.err.println("the facility size should be smaller than or equal to pool size:" + POOLSIZE );
			System.exit(0);
		}
		selectedFacilities = new Integer[facilitySize];
//		selectedFacilities = new Integer[]{6,7,10,11,13,23,30,33,37,39}; //BASE CASE
//		selectedFacilities = new Integer[]{6,23,30,33,37,39}; //shorten case
//		selectedFacilities = new Integer[]{6,23,33,37,39}; //shrunk problem for optimal netowrk in alpha_{eff} analysis
//		selectedFacilities = new Integer[]{2,4,5,7,8,9,11,12,13,14};
//		selectedFacilities = new Integer[]{3,4,7};
//		selectedFacilities = new Integer[]{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15};
//		selectedFacilities[0] = 10;
//		selectedFacilities[1] = 7;
//		selectedFacilities[2] = 3;
//		selectedFacilities[3] = 4;
//		selectedFacilities[4] = 13;
		if(facilitySize< POOLSIZE) {
			for(int i = 0 ; i < facilitySize; i++){
				int index = r.nextInt(POOLSIZE - i -1) + 1;
				selectedFacilities[i] = facilityCandidates.get(index);
				facilityCandidates.remove(index);
			}
			System.out.print("slected facilities: ");
		    for (int i = 0; i < facilitySize; i++) {
		    	System.out.print(selectedFacilities[i]+" ");
		    	fixedCostDC[i] = handler.xlsread("Sheet1", 3,  3 + selectedFacilities[i]);
		    	fixedCostRC[i] = handler.xlsread("Sheet1", 5,  3 + selectedFacilities[i]);
		    	flowCost_plant_DC[i] = handler.xlsread("distance", 11, selectedFacilities[i]);
		    	for (int j = 0; j < nCustomers; j++) {
		    		flowCost_DC_customers[i][j] = handler.xlsread("transportation cost", selectedFacilities[i] - 1, j + 1);
		    	}
		    	for (int j = 0; j < nCustomers; j++) {
		    		flowCost_customers_RC[j][i] = 2.5 * handler.xlsread("transportation cost", selectedFacilities[i] - 1, j + 1);
		    	}
		    	flowCost_RC_plant[i] = 2.5 * handler.xlsread("distance", 11, selectedFacilities[i]);
		    }
		    System.out.println();
		}
		else{
			facilityCandidates.toArray(selectedFacilities);
			System.out.print("slected facilities: ");
		    for (int i = 0; i < facilitySize; i++) {
		    	System.out.print(selectedFacilities[i]+" ");
		    }
		    System.out.println();
			fixedCostDC = handler.xlsread("Sheet1", 3, 4, 3 + facilitySize);
	    	fixedCostRC = handler.xlsread("Sheet1", 5, 4, 3 + facilitySize);
	    	flowCost_plant_DC = handler.xlsread("distance", 11, 1, facilitySize);
	    	flowCost_DC_customers = handler.xlsread("transportation cost", 0, citysize - 1, 1, facilitySize);
	    	flowCost_customers_RC = handler.xlsread("transportation cost", 0, facilitySize - 1, 1, citysize);
	    	flowCost_RC_plant = handler.xlsread("distance", 11, 1, facilitySize);
		}		
		
				
		PPDensity1 = handler.xlsread("Sheet1", 0, 4, 3 + citysize);
		PPDensitySum = MatrixHelper.sum(PPDensity1);
		PPDensity = MatrixHelper.divide(PPDensity1, PPDensitySum);
		manCapacity = MatrixHelper.sum(PPDensity1) * handler.xlsread("Sheet1", 1, 0);
		remanCapacity = MatrixHelper.sum(PPDensity1) * handler.xlsread("Sheet1", 1, 1);
		handler.close();
		
		//read parameters for SAA
		minMarkets = new double[17];
		maxMarkets = new double[17];
		medianMarkets = new double[17];
		minReturns = new double[17];
		maxReturns = new double[17];
		medianReturns = new double[17];
		minRates = new double[17];
		maxRates = new double[17];
		medianRates = new double[17];
			
		String SAAFileName = "dat/strategic analysis.xls";
		ExcelHandler handlerSAA = new ExcelHandler(SAAFileName);
		
//		original version
//		for(int i = 0; i<17; i++){
//			double[] para = handlerSAA.xlsread("Sheet1", i+1, 2, 10);
//			minMarkets[i] = para[0];
//			medianMarkets[i] = para[1];
//			maxMarkets[i] = para[2];
//			minReturns[i] = para[3];
//			medianReturns[i] = para[4];
//			maxReturns[i] = para[5];
//			minRates[i] = para[6];
//			medianRates[i] = para[7];
//			maxRates[i] = para[8];
		
//		maximum regret version in response to review #3 last comment:
//		for(int i = 0; i<40; i++){
//			double[] para = handlerSAA.xlsread("regret", i+1, 2, 5);
//			minMarkets[i] = para[0];
//			medianMarkets[i] = para[0];
//			maxMarkets[i] = para[0];
//			minReturns[i] = para[1];
//			medianReturns[i] = para[1];
//			maxReturns[i] = para[1];
//			minRates[i] = para[2];
//			medianRates[i] = para[2];
//			maxRates[i] = para[2];
//		}
		
	}
}
