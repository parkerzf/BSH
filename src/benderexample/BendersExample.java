/*
 * This is a small fixed-charge transportation problem.
 * The purpose is to demonstrate how to code Benders decomposition using CPLEX.
 * 
 */
package benderexample;

import ilog.concert.IloException;
import java.util.Random;

/**
 *
 * @author Paul A. Rubin (rubin@msu.edu)
 */
public class BendersExample {
  /* Configure model dimensions here */
  private static final int nWarehouses = 5;  // number of warehouses
  private static final int nCustomers = 20;  // number of customers
  private static final long SEED = 72612L;   // random number seed
  
  
  /**
   * @param args the command line arguments
   */
  public static void main(String[] args) {
    /*
     * seed the random number generator
     */
    Random rng = new Random(SEED);
    /*
     * generate problem parameters randomly
     */
    double[] demand = new double[nCustomers];  // demands
    double totalDemand = 0;
    for (int i = 0; i < nCustomers; i++) {
      demand[i] = rng.nextDouble();
      totalDemand += demand[i];
    }
    double meanCapacity = 2.5*totalDemand/nWarehouses;
      // mean capacity of any one warehouse
    double[] capacity = new double[nWarehouses];   // capacity of each warehouse
    double[] fixedCost = new double[nWarehouses];  // fixed cost of each warehouse
    for (int i = 0; i < nWarehouses; i++) {
      capacity[i] = meanCapacity*2*rng.nextDouble();
      fixedCost[i] = 100*rng.nextDouble();
    }
    double[][] flowCost = new double[nWarehouses][nCustomers];
      // unit flow cost from any possible warehouse to any customer
    for (int i = 0; i < nWarehouses; i++) {
      for (int j = 0; j < nCustomers; j++) {
        flowCost[i][j] = rng.nextDouble();
      }
    }
    /*
     * Build a single MIP model and solve it, as a benchmark.
     */
    long start = 0, end = 0;
    start = System.currentTimeMillis();
    try {
      SingleModel model = new SingleModel(nWarehouses, nCustomers, capacity,
                                          demand, fixedCost, flowCost);
      Solution s = model.solve();
      System.out.println("***\nThe unified model's solution has total cost "
                         + String.format("%10.5f", s.cost)
                         + ".\nWarehouses used: "
                         + s.warehouses.toString());
      System.out.println("Flows:");
      for (int i = 0; i < nWarehouses; i++) {
        for (int j = 0; j < nCustomers; j++) {
          System.out.print("\t" + String.format("%8.5f", s.flows[i][j]));
        }
        System.out.println();
      }
      System.out.println("***");
    } catch (IloException ex) {
      System.err.println("\n!!!Unable to solve the unified model:\n"
                         + ex.getMessage() + "\n!!!");
      System.exit(1);
    }
    end = System.currentTimeMillis();
    System.out.println("single model time = " + (end - start) + "ms\n");
    /*
     * Now build and solve a model using Benders decomposition.
     */
    start = System.currentTimeMillis();
    
    try {
      Benders model2 = new Benders(nWarehouses, nCustomers, capacity, 
                                   demand, fixedCost, flowCost);
      Solution s = model2.solve();
      System.out.println("***\nThe unified model's solution has total cost "
                         + String.format("%10.5f", s.cost)
                         + ".\nWarehouses used: "
                         + s.warehouses.toString());
      System.out.println("Flows:");
      for (int i = 0; i < nWarehouses; i++) {
        for (int j = 0; j < nCustomers; j++) {
          System.out.print("\t" + String.format("%8.5f", s.flows[i][j]));
        }
        System.out.println();
      }
      System.out.println("***");
    } catch (IloException ex) {
      System.err.println("\n!!!Unable to solve the Benders model:\n"
                         + ex.getMessage() + "\n!!!");
      System.exit(2);
    }
    end = System.currentTimeMillis();
    System.out.println("bender decomposition time = " + (end - start) + "ms\n");
    
  }
  
}
