/*
 * This implements a single MIP model to solve the fixed-charge transportation
 * problem.
 */
package benderexample;

import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import java.util.HashSet;

/**
 * @author Paul A. Rubin (rubin@msu.edu)
 */
public class SingleModel {
  private IloCplex cplex;      // the MIP model
  private IloNumVar[] use;     // use[i] = 1 if warehouse i is used, 0 if not
  private IloNumVar[][] ship;  // ship[i][j] is flow from warehouse i to customer j

  /**
   * Constructor.
   * @param nW  number of (potential) warehouses
   * @param nC  number of customers
   * @param capacity  capacities of warehouses
   * @param demand  demands of customers
   * @param fixed  fixed costs to open warehouses
   * @param flow  unit flow costs
   * @throws IloException if CPLEX is unhappy
   */
  public SingleModel(int nW, int nC, double[] capacity, double[] demand,
                     double[] fixed, double[][] flow) throws IloException {
    cplex = new IloCplex();
    use = new IloNumVar[nW];
    ship = new IloNumVar[nW][nC];
    IloLinearNumExpr expr = cplex.linearNumExpr();
    // declare the variables and simultaneously assemble the objective function
    for (int i = 0; i < nW; i++) {
      use[i] = cplex.boolVar("Use" + i);
      expr.addTerm(fixed[i], use[i]);
      for (int j = 0; j < nC; j++) {
        ship[i][j] = cplex.numVar(0.0, Math.min(capacity[i], demand[j]), 
                                  "Ship_" + i + "_" + j);
        expr.addTerm(flow[i][j], ship[i][j]);
      }
    }
    cplex.addMinimize(expr, "TotalCost");  // minimize total cost
    // add demand constraints
    for (int j = 0; j < nC; j++) {
      expr.clear();
      for (int i = 0; i < nW; i++) {
        expr.addTerm(1.0, ship[i][j]);
      }
      cplex.addGe(expr, demand[j], "Demand_" + j);
    }
    // add supply constraints
    for (int i = 0; i < nW; i++) {
      cplex.addLe(cplex.sum(ship[i]), cplex.prod(capacity[i], use[i]),
                  "Supply_" + i);
    }
  }
  
  /**
   * Solves the unified model.
   * @return the solution (in an instance of Solution)
   * @throws IloException if CPLEX encounters problems
   */
  public Solution solve() throws IloException {
    Solution s = new Solution();
    // try to solve the model
    if (cplex.solve()) {
      s.cost = cplex.getObjValue();
      s.warehouses = new HashSet<Integer>();
      s.flows = new double[use.length][];
      double[] u = cplex.getValues(use);
      for (int i = 0; i < use.length; i++) {
        s.flows[i] = cplex.getValues(ship[i]);
        if (u[i] > 0.5) {
          s.warehouses.add(Integer.valueOf(i));
        }
      }
    }
    s.status = cplex.getCplexStatus();
    return s;
  }
}
