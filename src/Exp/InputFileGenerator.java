package Exp;

import utils.Environment;
import utils.SAA;
import utils.Triple;

public class InputFileGenerator {
	public static void main(String[] args) {
		double minMarket = 2383907;
		double medianMarket = 2648785;
		double maxMarket = 2913664;
		
		double minReturn = 28710;
		double medianReturn = 114840;
		double maxReturn = 200970;
		
		double minRate = 0.525;
		double medianRate = 0.7;
		double maxRate = 0.875;
		
		SAA saa = new SAA(
				minMarket, medianMarket, maxMarket, 
				minReturn, medianReturn, maxReturn,
				minRate,medianRate,maxRate,1,40);
		for(Triple sample: saa.getSamples(0)){
			System.out.println(sample.marketSize+ "," + sample.ret+ "," + sample.recoveryRate);
		}
	}

}
