package Exp;

import utils.Environment;
import utils.SAA;
import utils.Triple;

public class SAAGen {
	public static void main(String args[]){
		String inputFileName  = "dat/Giengen.xls";
		int citysize = -1; 
		int facilitySize = -1;
		int M = 1, N = 1, Nprim = 0;

		// Check the command line arguments 
		if (args.length != 2 && args.length != 5 &&args.length != 6) {
			return;
		}

		if(args.length >=2){
			try {
				citysize = Integer.parseInt(args[0]);
				if(citysize<=0 || citysize >40){
					return;
				}
				facilitySize = Integer.parseInt(args[1]);
				if(facilitySize<=0 || facilitySize >40){
					return;
				}
			}
			catch(NumberFormatException e){
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
				return;
			}
		}

		if ( args.length == 6 )  inputFileName = args[5];

		/*
		 * init the environment
		 */
		Environment.init(citysize, facilitySize, inputFileName);
		SAA saa = new SAA(2383906.5*Environment.PPDensitySum,2913663.5*Environment.PPDensitySum,
				2648785*Environment.PPDensitySum,28710*Environment.PPDensitySum,
				200970*Environment.PPDensitySum,114840*Environment.PPDensitySum,
				0.525, 0.875, 0.7,1,40);
		
		Triple[] samples = saa.getSamples(0);
		
		
		for(Triple sample: samples){
			System.out.println(sample);
		}

	}

}
