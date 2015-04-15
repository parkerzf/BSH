/*
 * This class creates master and subproblems and solves the CLSC problem 
 * using Benders decomposition.
 * 
 * The master problem (MIP) selects the DC and RC to use.
 * 
 * The subproblem (LP) determines flows among DC, RC, customers and the plant.
 */
package BSH;

//import ilog.concert.IloConstraint;
import ilog.concert.IloException;
import ilog.concert.IloLQNumExpr;
import ilog.concert.IloNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.VariableSelect;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.util.Arrays;

import utils.Environment;
import utils.MatrixHelper;
import utils.Triple;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class BSH {
	private IloCplex master;      // the master model

	private IloNumVar[] use_DC;     // use[i] = 1 if DC of i is used, 0 if not
	private IloNumVar[] use_RC;     // use[i] = 1 if RC of i is used, 0 if not
	private IloNumVar operationalProfit;   // surrogate variable for profit

	private double[] capacity_DC;   

	private int nDistributionCenter;      // number of DCs
	private int nReturnCenter;      // number of RCs
	private int nMarket;       // number of customers

	private double[][] flowPlant_DC;  // subproblem flow from plant i to DC j 
	private double[][][] flowDC_customers;  // subproblem worflow from DC i to customer j  
	private double[][] flowCustomers_RC;  //  subproblem flow from customer i to RC j
	private double[] flowRC_plant;  //  subproblem flow from RC i to plant j
	private double flowManQuantity, flowRemanQuantity;  
	private double[] fixedCostDC;
	private double[] fixedCostRC;
	private Triple[] samples = null;
	
	private int numCuts = 0;

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
	//	private IloRange[] cCapacity;   // capacity constraints
	//	private IloRange[][] cSupply_DC_new;   // DC supply constraints for new product
	//	private IloRange[][] cSupply_DC_reman;   // DC supply constraints for remanufactured product
	//	private IloRange[][] cSupply_RC;   // RC supply constraints
	//	private IloRange[] cFlowBalance_return;   // flow balance constraints
	//	private HashMap<IloConstraint, IloNumExpr> rhs;

	//	private PrintStream outModel;
	private PrintStream outLog;
	//	private PrintStream outMaster;
	//	private PrintStream outSub;

	private BSHSub[] subproblems;

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

	public BSH(int nDC, int nRC, int nCustomers, double manCapacity, double remanCapacity, 
			//			double ret, double recoveryRate, double marketSize, 
			Triple[] samples,
			double[] fixedCostDC, double[] fixedCostRC, 
			double[] PPDensity, double[] flowCost_plant_DC, double[][] flowCost_DC_customers, 
			double[][] flowCost_customers_RC, double[] flowCost_RC_plant) throws IloException{
		try {
			//					outModel = new PrintStream(new FileOutputStream("out/model.txt"));
			outLog = new PrintStream(new FileOutputStream("out/log.txt"));
			//					outMaster = new PrintStream(new FileOutputStream("out/master.txt"));
			//					outSub = new PrintStream(new FileOutputStream("out/sub.txt"));
		} catch (IOException e1) {
			e1.printStackTrace();
		}

		nDistributionCenter = nDC;
		nReturnCenter = nRC;
		nMarket = nCustomers;
		flowPlant_DC = new double[2][nDistributionCenter];
		flowDC_customers = new double[2][nDistributionCenter][nMarket];
		flowCustomers_RC = new double[nMarket][nReturnCenter];
		flowRC_plant = new double[nReturnCenter];

		// record capacities
		this.capacity_DC = new double[2];
		capacity_DC[0] = manCapacity;
		capacity_DC[1] = remanCapacity;

		// record fix costs
		this.fixedCostDC = fixedCostDC;
		this.fixedCostRC = fixedCostRC;

		// record flow costs
		//		this.flowCost_plant_DC = flowCost_plant_DC;
		//		this.flowCost_DC_customers = flowCost_DC_customers;
		//		this.flowCost_customers_RC = flowCost_customers_RC;
		//		this.flowCost_RC_plant = flowCost_RC_plant;
		//		// record the PPDensity
		//		this.PPDensity = PPDensity;

		// record the samples for sub problems
		this.samples = samples;

		// build master problem
		initMasterProblem();
		// build a set of sub problems
		initSubProblems();
	}

	private void initSubProblems() throws IloException {
		Triple currentSample;
		subproblems = new BSHSub[samples.length];
		for(int index = 0 ; index< samples.length; index++){
			currentSample = samples[index];
			subproblems[index] = new BSHSub(Environment.nDC, Environment.nRC,Environment.nCustomers, 
					Environment.manCapacity,Environment.remanCapacity,
					//					currentSample,outSub, 
					currentSample,null,
					Environment.fixedCostDC,Environment.fixedCostRC,Environment.PPDensity,
					Environment.flowCost_plant_DC, Environment.flowCost_DC_customers,
					Environment.flowCost_customers_RC,Environment.flowCost_RC_plant);
		}
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
			//			System.out.println();
			double zMaster = getValue(operationalProfit);  // get master profit estimate
			//			double profit = getObjValue();
			double[] udc =  getValues(use_DC);
			double[] urc =  getValues(use_RC);

			// record the expected ret
			// similar to node.SAACompBeforeCurrent
			// useless right now?
			double expectRet = 0;
			for(Triple sample:samples){
				expectRet += sample.ret;
			}
			expectRet = expectRet/samples.length;

			// record sub result
			// similar to node.SAACompAfterCurrent
			double currentSubObjValue = 0;
			DualSolution[] allDualSolution = new DualSolution[samples.length]; //obj.all_x
			DualSolution currentDualSolution = null;
			BSHSub currentSub;        
			IloCplex.Status status = null;
			double expectSubObjValue = 0;
			double expectflowManQuantity = 0;
			double expectflowRemanQuantity = 0;

			for(int index = 0 ; index< samples.length; index++){
				subproblems[index].updateSubProblem(udc, urc);
				currentSub = subproblems[index];
				//				outModel.println(currentSub.sub);
				// solve the subproblem
				currentSub.sub.solve();
				status = currentSub.sub.getStatus();
				//if one sub problem is not optimal, we break out from the loop 
				if(status != IloCplex.Status.Optimal) break;

				currentSubObjValue = currentSub.sub.getObjValue();
//				currentSubObjValue = currentSub.sub.getObjValue() - (Environment.holdingCost + Environment.disposalCost)*samples[index].ret;
				//				System.out.println("sub problem obj value: " + currentSubObjValue);
				expectSubObjValue += currentSubObjValue;
				//get the dual solution
				double q_new = currentSub.sub.getValue(currentSub.manQuantity);
				double q_reman = currentSub.sub.getValue(currentSub.remanQuantity);
				expectflowManQuantity += q_new;
				expectflowRemanQuantity += q_reman;
				double[] gamma = currentSub.sub.getDuals(currentSub.cCapacity);	
				double[][] epsilon_DC_new = new double[nDistributionCenter][nMarket];
				for(int i = 0 ; i < nDistributionCenter; i++){
					epsilon_DC_new[i] = currentSub.sub.getDuals(currentSub.cSupply_DC_new[i]);
				}
				double[][] epsilon_DC_reman = new double[nDistributionCenter][nMarket];
				for(int i = 0 ; i < nDistributionCenter; i++){
					epsilon_DC_reman[i] = currentSub.sub.getDuals(currentSub.cSupply_DC_reman[i]);
				}
				double[][] epsilon_RC = new double[nMarket][nReturnCenter];
				for(int i = 0 ; i < nMarket; i++){
					epsilon_RC[i] = currentSub.sub.getDuals(currentSub.cSupply_RC[i]);
				}
				double[] chi = currentSub.sub.getDuals(currentSub.cFlowBalance_return);
				currentDualSolution  = new DualSolution(q_new,q_reman,gamma,epsilon_DC_new, epsilon_DC_reman, 
						epsilon_RC, chi);
				allDualSolution[index] = currentDualSolution;
			}
			expectSubObjValue /=samples.length;//obj.expect_fval_d
			expectflowManQuantity /= samples.length;
			expectflowRemanQuantity /= samples.length;

			// double obj = -I * sub.getValue(manQuantity) * sub.getValue(manQuantity) + 2 * sub.remanDepreciation * I * manQuantity * remanQuantity
			// + remanDepreciation * I * remanQuantity * remanQuantity
			// - manA * manQuantity - remanA * remanQuantity
			// + flowCost_plant_DC + flowCost_DC_customers + flowCost_customers_RC + flowCost_RC_plant
			if (status == IloCplex.Status.Optimal) {
				// similar to node.updateO_n(obj)
				if (zMaster > expectSubObjValue + Environment.FUZZ) {
					IloNumExpr expr = master.numExpr();
					// compute the scalar product of the RHS of constraints
					// with the duals for those constraints
					for(int index = 0; index < allDualSolution.length; index++){
						currentSub = subproblems[index];
						currentDualSolution = allDualSolution[index];
						for (int j = 0; j < 4; j++) {
							expr = master.sum(expr, currentDualSolution.gamma[j]*currentSub.cCapacity[j].getUB());//master.prod(gamma[j], rhs.get(cCapacity[j])));
						}
						for (int i = 0; i < nDistributionCenter; i++) {
							for (int j = 0; j < nMarket; j++){
								expr = master.sum(expr, master.prod(currentDualSolution.epsilon_DC_new[i][j]* capacity_DC[0] , use_DC[i]));//master.prod(epsilon_DC_new[i][j], rhs.get(cSupply_DC_new[i][j])));
							}
						}
						for (int i = 0; i < nDistributionCenter; i++) {
							for (int j = 0; j < nMarket; j++){
								expr = master.sum(expr, master.prod(currentDualSolution.epsilon_DC_reman[i][j]*capacity_DC[1] , use_DC[i]));//master.prod(epsilon_DC_reman[i][j], rhs.get(cSupply_DC_reman[i][j])));
							}
						}
						for (int i = 0; i < nMarket; i++) {
							for (int j = 0; j < nReturnCenter; j++){
								expr = master.sum(expr,master.prod(currentDualSolution.epsilon_RC[i][j] * currentSub.capacity_RC[j], use_RC[j])); //master.prod(epsilon_RC[i][j], rhs.get(cSupply_RC[i][j])));
							}
						}
						for (int j = 0; j < nMarket; j++) {
							expr = master.sum(expr, currentDualSolution.chi[j]*currentSub.cFlowBalance_return[j].getUB());//master.prod(chi[j], rhs.get(cFlowBalance_return[j])));
						}
						double I = (Environment.reservationPriceUB - Environment.reservationPriceLB)/samples[index].marketSize; 
						expr = master.sum(expr, I * currentDualSolution.q_new * currentDualSolution.q_new);    //original
						expr = master.sum(expr, 2 * Environment.remanDepreciation * I * currentDualSolution.q_new    //original
								* currentDualSolution.q_reman);                                                       //original
//						expr = master.sum(expr, 1 * Environment.remanDepreciation * I * currentDualSolution.q_new 
//								* currentDualSolution.q_reman);
//						expr = master.sum(expr, Environment.remanDepreciation * I * currentDualSolution.q_reman 
//								* currentDualSolution.q_reman);
					}
					expr = master.prod(1f/samples.length, expr);
					// add the optimality cut						
					add((IloRange) master.le(operationalProfit, expr));
					numCuts++;
					
					//					System.out.println(">>> Adding optimality cut: " + r);						
				} else {
					System.out.println(">>> Accepting new incumbent with value " + getObjValue());
					outLog.println(">>> Accepting new incumbent with value " + getObjValue());

					BSHSub subOne = subproblems[0];

					flowManQuantity = expectflowManQuantity;
					flowRemanQuantity = expectflowRemanQuantity;

					for (int l = 0; l < 2; l++){
						flowPlant_DC[l] = subOne.sub.getValues(subOne.plant_DC[l]);
					}
					for (int l = 0; l < 2; l++){
						for (int i = 0; i < nDistributionCenter; i++) {
							flowDC_customers[l][i] = subOne.sub.getValues(subOne.DC_customers[l][i]);
						}
					}
					for (int i = 0; i < nMarket; i++) {
						for (int j = 0; j < nReturnCenter; j++) {
							flowCustomers_RC[i][j] = subOne.sub.getValue(subOne.customers_RC[i][j]);
						}
					}
					flowRC_plant = subOne.sub.getValues(subOne.RC_plant);		
					flowManQuantity = subOne.sub.getValue(subOne.manQuantity);
					flowRemanQuantity = subOne.sub.getValue(subOne.remanQuantity);

					PrimalSolution s = new PrimalSolution(getObjValue(), udc, urc,
							flowPlant_DC,flowDC_customers,flowCustomers_RC,flowRC_plant,flowManQuantity,
							flowRemanQuantity, subOne.sub.getCplexStatus());
					s.print(System.out);
					s.print(outLog);
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
	public PrimalSolution solve() throws IloException {		
		PrimalSolution s = new PrimalSolution();
		if (master.solve()) {
			s.profit = master.getObjValue();

			s.plant_DC = new double[2][nDistributionCenter];
			s.DC_customers = new double[2][nDistributionCenter][nMarket];
			s.customers_RC = new double[nMarket][nReturnCenter];
			s.RC_plant = new double[nReturnCenter];

			s.udc = master.getValues(use_DC);
			s.urc = master.getValues(use_RC);
			for(int l = 0; l < 2; l++) {
				s.plant_DC[l] = Arrays.copyOf(flowPlant_DC[l], nDistributionCenter);
			}
			for(int l = 0; l < 2; l++) {
				for(int i = 0; i < nDistributionCenter; i++) {
					s.DC_customers[l][i] = Arrays.copyOf(flowDC_customers[l][i], nMarket);
				}
			}
			for(int i = 0; i < nMarket; i++) {
				s.customers_RC[i] = Arrays.copyOf(flowCustomers_RC[i], nReturnCenter); 
			}
			s.RC_plant = Arrays.copyOf(flowRC_plant, nReturnCenter);
			s.manQuantity = flowManQuantity;
			s.remanQuantity = flowRemanQuantity;
		}
		s.status = master.getCplexStatus();    
		s.nNodes = master.getNnodes();
		s.nCuts = numCuts;
		System.out.println("num of nodes " +s.nNodes);
		System.out.println("num of cuts " +s.nCuts);
		return s;
	}

	public void clear() throws IloException{
		master.clearModel();
		for(BSHSub sub: subproblems){
			sub.sub.clearModel();
		}
	}

	public void finalize(){
		//		outModel.close();
		outLog.close();
		//		outMaster.close();
		//		outSub.close();
	}

	private void initMasterProblem() throws IloException{
		// init the master problem
		master = new IloCplex();
		//		master.setOut(outMaster);
		master.setOut(null);

		// set up the master problem (which initially has no constraints)
		String[] names_DC = new String[nDistributionCenter];
		for (int i = 0; i < nDistributionCenter; i++) {
			names_DC[i] = "Use_DC" + i;
		}
		String[] names_RC = new String[nReturnCenter];
		for (int i = 0; i < nReturnCenter; i++) {
			names_RC[i] = "Use_RC" + i;
		}
		use_DC = master.boolVarArray(nDistributionCenter, names_DC);
		use_RC = master.boolVarArray(nReturnCenter, names_RC);
		operationalProfit = master.numVar(Double.MIN_VALUE, Double.MAX_VALUE, "estOperationalProfit");
		IloLQNumExpr objExprMaster = master.lqNumExpr();
		objExprMaster.clear();
		for(int i = 0; i < nReturnCenter; i++) {
			objExprMaster.addTerm(1, use_RC[i]);
		}
		master.addGe(objExprMaster, 1, "c_nRC");
		
//		Fixed forward supply chain to the sequential forward optimal
		objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[0]);
	    master.addGe(objExprMaster, 1, "fixed_DC0");
	    
		objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[1]);
	    master.addGe(objExprMaster, 1, "fixed_DC1");
	    
		objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[2]);
	    master.addLe(objExprMaster, 0, "fixed_DC2");
	    
		objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[3]);
	    master.addGe(objExprMaster, 1, "fixed_DC3");
	    
		objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[4]);
	    master.addLe(objExprMaster, 0, "fixed_DC4");
	    
	    objExprMaster.clear();
		objExprMaster.addTerm(1, use_DC[5]);
	    master.addLe(objExprMaster, 0, "fixed_DC5");
	    
	  //Fixed reverse supply chain to the sequential forward optimal
	  		objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[0]);
	  	    master.addLe(objExprMaster, 0, "fixed_RC0");
	  	    
	  		objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[1]);
	  	    master.addLe(objExprMaster, 0, "fixed_RC1");
	  	    
	  		objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[2]);
	  	    master.addLe(objExprMaster, 0, "fixed_RC2");
	  	    
	  		objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[3]);
	  	    master.addGe(objExprMaster, 1, "fixed_RC3");
	  	    
	  		objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[4]);
	  	    master.addLe(objExprMaster, 0, "fixed_RC4");
	  	    
	  	    objExprMaster.clear();
	  		objExprMaster.addTerm(1, use_RC[5]);
	  	    master.addGe(objExprMaster, 1, "fixed_RC5");

		//set upper bound for est_operationalProfit
		objExprMaster.clear();
		objExprMaster.addTerm(1, operationalProfit);
		master.addRange(Double.MIN_VALUE, objExprMaster, 1.0e15, "estOperationalProfit"); 

		master.addMaximize(master.sum(operationalProfit, master.scalProd(MatrixHelper.multiple(fixedCostDC, -1), 
				use_DC), master.scalProd(MatrixHelper.multiple(fixedCostRC, -1), use_RC)),"TotalProfit");
		// attach a Benders callback to the master
		master.use(new BendersCallback());

		//		set up master parameters
		try {
			//			CplexParamSetter.set(master, "EpMrk", "0.99999");
			//			CplexParamSetter.set(sub, "EpMrk", "0.99999");
			//			CplexParamSetter.set(master, "EpInt", "1e-09");
			//			CplexParamSetter.set(sub, "EpInt", "1e-9");
			//			CplexParamSetter.set(master, "EpRHS", "1e-9");
			//			CplexParamSetter.set(sub, "EpRHS", "1e-9");
			//			CplexParamSetter.set(master, "NumericalEmphasis", "true");
			//			CplexParamSetter.set(sub, "NumericalEmphasis", "true");
			master.setParam(IloCplex.BooleanParam.NumericalEmphasis, true);
			master.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Optimality);
			//			master.setParam(IloCplex.IntParam.RINSHeur,20);
			//									master.setParam(IloCplex.IntParam.Probe,3);
			//			master.setParam(IloCplex.DoubleParam.EpGap ,0.05);
			//			master.setParam(IloCplex.DoubleParam.PolishAfterTime ,0);
			for(IloNumVar elem: use_DC){
				master.setDirection(elem, IloCplex.BranchDirection.Down);
			}
			for(IloNumVar elem: use_RC){
				master.setDirection(elem, IloCplex.BranchDirection.Down);
			}

			int algorithm = IloCplex.Algorithm.Primal;
			master.setParam(IloCplex.IntParam.RootAlg, algorithm);
			master.setParam(IloCplex.IntParam.NodeAlg, algorithm);

			//						master.setParam(IloCplex.IntParam.ParallelMode, IloCplex.ParallelMode.Opportunistic);
			master.setParam(IloCplex.DoubleParam.WorkMem, 3000);//set memory to 4G
			master.setParam(IloCplex.IntParam.VarSel,VariableSelect.Strong);
			//						master.setParam(IloCplex.IntParam.NodeSel, NodeSelect.DFS);
			master.setParam(IloCplex.IntParam.NodeFileInd ,2);
			master.setParam(IloCplex.StringParam.WorkDir ,"c:\\users\\wchen\\Documents\\model");
//			master.setParam(IloCplex.StringParam.WorkDir ,"/home/zhaofeng/BSH/model");
		} catch (NumberFormatException e) {
			e.printStackTrace();
		}

		//		outModel.println("master");
		//		outModel.println(master);
	}
}
