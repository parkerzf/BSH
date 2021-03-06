/*
 * This class creates master and subproblems and solves the fixed-charge
 * transportation problem using Benders decomposition.
 * 
 * The master problem (MIP) selects the warehouses to use.
 * 
 * The subproblem (LP) determines flows from warehouses to customers.
 */
package benderexample;

import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloLinearNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

/**
 * @author Paul A. Rubin (rubin@msu.edu)
 */
public class Benders {
	private IloCplex master;      // the master model
	private IloCplex sub;         // the subproblem
	private IloNumVar[] use;      // binary variables selecting warehouses
	private IloNumVar[][] ship;   // flow variables
	private IloNumVar flowCost;   // surrogate variable for flow costs
	private double[] capacity;    // warehouse capacities
//	private double[] demand;      // customer demands
	private int nWarehouses;      // number of warehouses
	private int nCustomers;       // number of customers
	private final double FUZZ = 1.0e-7;
	// tolerance for comparing subproblem and master problem flow cost values
	private double[][] flowValues; // subproblem flow values
	/*
	 * To compute both optimality and feasibility cuts, we will need to multiply
	 * the right-hand sides of the subproblem constraints (including both constant
	 * terms and terms involving master problem variables) by the corresponding
	 * subproblem dual values (obtained either directly, if the subproblem is
	 * optimized, or via a Farkas certificate if the subproblem is infeasible).
	 * To facilitate the computations, we will construct an instance of IloNumExpr
	 * for each right-hand side, incorporating both scalars and master variables.
	 * 
	 * Since CPLEX returns the Farkas certificate in no particular order (in
	 * particular, NOT in the order the subproblem constraints are created),
	 * we need to be able to access the IloNumExpr instances in the same arbitrary
	 * order used by the Farkas certificate. To do that, we will store them in
	 * a map keyed by the subproblem constraints themselves (which we will store
	 * in arrays).
	 */
	private IloRange[] cSupply;   // supply constraints
	private IloRange[] cDemand;   // demand constraints
	private HashMap<IloConstraint, IloNumExpr> rhs;

	/**
	 * Constructor.
	 * @param nW number of potential warehouses
	 * @param nC number of customers
	 * @param capacity warehouse capacities
	 * @param demand customer demands
	 * @param fixed fixed costs to use warehouses
	 * @param unitCost unit flow costs
	 * @throws IloException if something makes CPLEX unhappy
	 */
	public Benders(int nW, int nC, double[] capacity, double[] demand,
			double[] fixed, double[][] unitCost) throws IloException {
		nWarehouses = nW;
		nCustomers = nC;
		flowValues = new double[nW][nC];
		rhs = new HashMap<IloConstraint, IloNumExpr>();
		// record capacities and demands
		this.capacity = Arrays.copyOf(capacity, nW);
//		this.demand = Arrays.copyOf(demand, nC);
		// create the master and subproblems
		master = new IloCplex();
		sub = new IloCplex();
		// set up the master problem (which initially has no constraints)
		String[] names = new String[nW];
		for (int i = 0; i < nW; i++) {
			names[i] = "Use_" + i;
		}
		use = master.boolVarArray(nW, names);
		flowCost = master.numVar(0.0, Double.MAX_VALUE, "estFlowCost");
		master.addMinimize(master.sum(flowCost, master.scalProd(fixed, use)),
		"TotalCost");
		// attach a Benders callback to the master
		master.use(new BendersCallback());
		// set up the subproblem
		ship = new IloNumVar[nW][nC];
		IloLinearNumExpr expr = sub.linearNumExpr();
		for (int i = 0; i < nW; i++) {
			for (int j = 0; j < nC; j++) {
				ship[i][j] = sub.numVar(0.0, Double.MAX_VALUE, "Flow_" + i + "_" + j);
				expr.addTerm(unitCost[i][j], ship[i][j]);
			}
		}
		sub.addMinimize(expr, "FlowCost");  // minimize total flow cost
		// constrain demand to be satisfied -- record the constraints for use later
		cDemand = new IloRange[nC];
		for (int j = 0; j < nC; j++) {
			expr.clear();
			for (int i = 0; i < nW; i++) {
				expr.addTerm(1.0, ship[i][j]);
			}
			cDemand[j] = sub.addGe(expr, demand[j], "Demand_" + j);
			rhs.put(cDemand[j], master.linearNumExpr(demand[j]));
		}
		// add supply limits (initially all zero, which makes the subproblem 
		// infeasible)
		// -- record the constraints for use later
		// -- also map each constraint to the corresponding binary variable in the
		//    master (for decoding Farkas certificates)
		cSupply = new IloRange[nW];
		for (int i = 0; i < nW; i++) {
			cSupply[i] = sub.addLe(sub.sum(ship[i]), 0.0, "Supply_" + i);
			rhs.put(cSupply[i], master.prod(capacity[i], use[i]));
		}
		// disable presolving of the subproblem (if the presolver realizes the
		// subproblem is infeasible, we do not get a dual ray)
		sub.setParam(IloCplex.BooleanParam.PreInd, false);
	}

	/**
	 * BendersCallback implements a lazy constraint callback that uses the 
	 * warehouse selection decisions in a proposed new incumbent solution to the
	 * master problem to solve the subproblem.
	 * 
	 * If the subproblem is optimized and the subproblem objective cost matches
	 * the incumbent's estimated flow cost (to within rounding tolerance), the
	 * callback exits without adding a constraint (resulting in the incumbent 
	 * being accepted in the master problem).
	 * 
	 * If the subproblem is optimized but the incumbent's estimated flow cost
	 * underestimates the true flow cost, the callback adds an optimality cut
	 * (eliminating the proposed incumbent).
	 * 
	 * If the subproblem proves to be infeasible, the callback adds a feasibility
	 * cut (eliminating the proposed incumbent).
	 * 
	 * Those should be the only possible outcomes. If something else happens
	 * (subproblem unbounded or unsolved), the callback writes a message to
	 * stderr and punts.
	 */
	class BendersCallback extends IloCplex.LazyConstraintCallback {

		@Override
		protected void main() throws IloException {
			double zMaster = getValue(flowCost);  // get master flow cost estimate
			// which warehouses does the proposed master solution use?
			double[] x = getValues(use);
			// set the supply constraint right-hand sides in the subproblem
			for (int i = 0; i < nWarehouses; i++) {
				cSupply[i].setUB((x[i] > 0.5) ? capacity[i] : 0);
			}
			// solve the subproblem
			sub.solve();
			IloCplex.Status status = sub.getStatus();
			IloNumExpr expr = master.numExpr();
			if (status == IloCplex.Status.Infeasible) {
				// subproblem is infeasible -- add a feasibility cut
				// first step: get a Farkas certificate, corresponding to a dual ray
				// along which the dual is unbounded
				IloConstraint[] constraints = new IloConstraint[nWarehouses + nCustomers];
				double[] coefficients = new double[nWarehouses + nCustomers];
				sub.dualFarkas(constraints, coefficients);
				double temp = 0;  // sum of cut terms not involving primal variables
				// process all elements of the Farkas certificate
				for (int i = 0; i < constraints.length; i++) {
					IloConstraint c = constraints[i];
					expr = master.sum(expr, master.prod(coefficients[i], rhs.get(c)));
				}
				// add a feasibility cut
				IloConstraint r = add(master.le(master.sum(temp, expr), 0));
				System.out.println(">>> Adding feasibility cut: " + r);
			} else if (status == IloCplex.Status.Optimal) {
				if (zMaster < sub.getObjValue() - FUZZ) {
					// the master problem surrogate variable underestimates the actual
					// flow cost -- add an optimality cut
					double[] lambda = sub.getDuals(cDemand);
					double[] mu = sub.getDuals(cSupply);
					// compute the scalar product of the RHS of the demand constraints
					// with the duals for those constraints
					for (int j = 0; j < nCustomers; j++) {
						expr = master.sum(expr, master.prod(lambda[j], rhs.get(cDemand[j])));
					}
					// repeat for the supply constraints
					for (int i = 0; i < nWarehouses; i++) {
						expr = master.sum(expr, master.prod(mu[i], rhs.get(cSupply[i])));
					}
					// add the optimality cut
//					IloConstraint r = add(master.ge(getValue(flowCost), expr));
					IloConstraint r = add((IloRange) master.ge(flowCost, expr));
					System.out.println(">>> Adding optimality cut: " + r);
				} else {
					System.out.println(">>> Accepting new incumbent with value " 
							+ getObjValue());
					// the master and subproblem flow costs match
					// -- record the subproblem flows in case this proves to be the 
					//    winner (saving us from having to solve the LP one more time
					//    once the master terminates)
					for (int i = 0; i < nWarehouses; i++) {
						flowValues[i] = sub.getValues(ship[i]);
					}
				}
			} else {
				// unexpected status -- report but do nothing
				System.err.println("!!! Unexpected subproblem solution status: "
						+ status);
			}
		}    
	}

	/**
	 * Solves the Benders master model.
	 * @return the solution (in an instance of Solution)
	 * @throws IloException if CPLEX encounters problems
	 */

	public Solution solve() throws IloException {
		Solution s = new Solution();
		if (master.solve()) {
			s.cost = master.getObjValue();
			s.warehouses = new HashSet<Integer>();
			s.flows = new double[nWarehouses][];
			double[] u = master.getValues(use);
			for (int i = 0; i < use.length; i++) {
				s.flows[i] = Arrays.copyOf(flowValues[i], nCustomers);
				if (u[i] > 0.5) {
					s.warehouses.add(Integer.valueOf(i));
				}
			}
		}
		s.status = master.getCplexStatus();    
		return s;
	}

}
