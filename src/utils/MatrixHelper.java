package utils;

import java.util.ArrayList;

public class MatrixHelper {

	public static double sum(double[] values) {
		double result = 0;
		for (double value:values)
			result += value;
		return result;
	}

	public static double[] divide (double[] values, double numerator) {
		double[] result = new double[values.length];
		for (int i = 0; i< values.length; i++)
			result[i] = values[i]/numerator;
		return result;
	}
	public static double[][] transpose (double[][] values) {
		double[][] result = new double[values[0].length][values.length];
		for (int i = 0; i< values.length; i++)
			for(int j = 0; j < values[0].length; j++)
				result[j][i] = values[i][j];
		return result;
	}

	public static double[] multiple (double[] values, double multipler) {
		double[] result = new double[values.length];
		for (int i = 0; i< values.length; i++)
			result[i] = values[i]*multipler;
		return result;
	}

	public static double getMean(ArrayList<Double> values){
		double sum = 0.0;
		for(double v : values)
			sum += v;
		return sum/values.size();
	}
	public static double getVariance(ArrayList<Double> values){
		double mean = getMean(values);
		double temp = 0;
		for(double v :values)
			temp += (mean-v)*(mean-v);
		return temp/(values.size()*(values.size()-1));
	}
	public static double getStdDev(ArrayList<Double> values){
		return Math.sqrt(getVariance(values));
	}

}
