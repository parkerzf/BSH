package utils;

/**
 * monitor memory usage
 * @author Zhao Feng
 *
 */

public class Monitor {
	private static final Runtime rt = Runtime.getRuntime();
	private static int mb= 1024*1024;

	//	Display the memory use 
	public static void showMemoryUse(){
		

		//Getting the runtime reference from system
		Runtime runtime = Runtime.getRuntime();

		System.out.println("##### Heap utilization statistics #####");

		//Print used memory
		System.out.println("Used Memory:"
				+ (runtime.totalMemory() - runtime.freeMemory()) / mb + "MB");

		//Print free memory
		System.out.println("Free Memory:"
				+ runtime.freeMemory() / mb + "MB");

		//Print total available memory
		System.out.println("Total Memory:" + runtime.totalMemory() / mb + "MB");

		//Print Maximum available memory
		System.out.println("Max Memory:" + runtime.maxMemory() / mb + "MB");
	}

	//	Run the garbage collector
	public static void runGC(){
		rt.gc();
	}

}
