/*
 * This is a container class to hold solutions to the problem.
 */
package benderexample;

import ilog.cplex.IloCplex;
import java.util.HashSet;

/**
 * @author Paul A. Rubin (rubin@msu.edu)
 */
public class Solution {
  public double cost;  // total cost
  public double[][] flows;  // flow volumes
  public HashSet<Integer> warehouses;  // warehouses used
  public IloCplex.CplexStatus status;  // status returned by CPLEX
}
