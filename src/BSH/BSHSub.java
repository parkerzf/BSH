/*
 * This class creates master and subproblems and solves the CLSC problem 
 * using Benders decomposition.
 * 
 * The master problem (MIP) selects the DC and RC to use.
 * 
 * The subproblem (LP) determines flows among DC, RC, customers and the plant.
 */
package BSH;

import ilog.concert.IloException;
import ilog.concert.IloLQNumExpr;
import ilog.concert.IloNumVar;
import ilog.concert.IloRange;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.NodeSelect;
import ilog.cplex.IloCplex.VariableSelect;

import java.io.PrintStream;

import utils.Environment;
import utils.Triple;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class BSHSub {

	public IloNumVar[][] plant_DC;  // flow from plant i to DC j 
	public IloNumVar[][][] DC_customers;  // flow from DC i to customer j   
	public IloNumVar[][] customers_RC;  //  flow from customer i to RC j
	public IloNumVar[] RC_plant;  //  flow from RC i to plant j

	public IloNumVar manQuantity;	// manufacturing production quantity
	public IloNumVar remanQuantity;	// remanufacturing production quantity

	public double[] capacity_DC;   
	public double[] capacity_RC;

	public int nDistributionCenter;      // number of DCs
	public int nReturnCenter;      // number of RCs
	public int nMarket;       // number of customers

	public double[][] flowPlant_DC;  // subproblem flow from plant i to DC j 
	public double[][][] flowDC_customers;  // subproblem flow from DC i to customer j  
	public double[][] flowCustomers_RC;  //  subproblem flow from customer i to RC j
	public double[] flowRC_plant;  //  subproblem flow from RC i to plant j
	public double flowManQuantity, flowRemanQuantity;  
	public double[] fixedCostDC;
	public double[] fixedCostRC;
	public double[] flowCost_plant_DC;
	public double[][] flowCost_DC_customers;
	public double[][] flowCost_customers_RC;
	public double[] flowCost_RC_plant;
	public double[] PPDensity;


	public IloRange[] cCapacity;   // capacity constraints
	public IloRange[] cFlowBalance_return;   // flow balance constraints
	
	public IloRange[][] cSupply_DC_new;   // DC supply constraints for new product
	public IloRange[][] cSupply_DC_reman;   // DC supply constraints for remanufactured product
	public IloRange[][] cSupply_RC;   // RC supply constraints
	
//	public IloLPMatrix[] cSupply_DC_new;
	
	public IloCplex sub;

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
	public BSHSub(int nDC, int nRC, int nCustomers, double manCapacity, double remanCapacity, 
			Triple sample, PrintStream outSub,
			double[] fixedCostDC, double[] fixedCostRC, 
			double[] PPDensity, double[] flowCost_plant_DC, double[][] flowCost_DC_customers, 
			double[][] flowCost_customers_RC, double[] flowCost_RC_plant) throws IloException{

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
		this.flowCost_plant_DC = flowCost_plant_DC;
		this.flowCost_DC_customers = flowCost_DC_customers;
		this.flowCost_customers_RC = flowCost_customers_RC;
		this.flowCost_RC_plant = flowCost_RC_plant;
		// record the PPDensity
		this.PPDensity = PPDensity;

		buildSubProblem(sample, outSub);
	}


	// build the sub problem with repsect to the sample and usedc userc
	private void buildSubProblem(Triple sample, PrintStream outSub) throws IloException{
		sub = new IloCplex();

		double marketSize = sample.marketSize;
		double ret = sample.ret;
		double recoveryRate = sample.recoveryRate;

		this.capacity_RC = new double[nReturnCenter];
		for(int i = 0; i< nReturnCenter ;i++) {
			this.capacity_RC[i] = ret;
		}
//		sub.setOut(outSub);
		sub.setOut(null);
		plant_DC = new IloNumVar[2][nDistributionCenter];  // flow from plant i to DC j 
		DC_customers = new IloNumVar[2][nDistributionCenter][nMarket];  // flow from DC i to customer j 
		customers_RC = new IloNumVar[nMarket][nReturnCenter];  //  flow from customer i to RC j
		RC_plant = new IloNumVar[nReturnCenter]; 

		double I = (Environment.reservationPriceUB - Environment.reservationPriceLB)/marketSize;   //I=(resPriceUB - resPriceLB) / marketSize;
		double manA = Environment.reservationPriceUB - Environment.manCost - Environment.holdingCost;
		double remanA = Environment.remanDepreciation * Environment.reservationPriceUB - 
				Environment.remanCost - Environment.holdingCost + Environment.disposalCost;

		//minimize I * manQuantity * manQuantity + 2 * remanDepreciation * I * manQuantity * remanQuantity
		//          + remanDepreciation * I * remanQuantity * remanQuantity
		//          - manA * manQuantity - remanA * remanQuantity
		//          + flowCost_plant_DC + flowCost_DC_customers + flowCost_customers_RC + flowCost_RC_plant
		IloLQNumExpr objExpr = sub.lqNumExpr();
		manQuantity = sub.numVar(0, capacity_DC[0], "manQuantity");    
		remanQuantity = sub.numVar(0, capacity_DC[1], "remanQuantity");  
		objExpr.addTerm(-I, manQuantity, manQuantity);  //original
		objExpr.addTerm(-2 * Environment.remanDepreciation * I, manQuantity, remanQuantity);   //original
//		objExpr.addTerm(-1 * Environment.remanDepreciation * I, manQuantity, remanQuantity);
//		objExpr.addTerm(-Environment.remanDepreciation * I, remanQuantity, remanQuantity);
		objExpr.addTerm(manA, manQuantity);
		objExpr.addTerm(remanA, remanQuantity);

		for (int i = 0; i < nDistributionCenter; i++) {
			plant_DC[0][i] = sub.numVar(0.0, Double.MAX_VALUE, 
					"plant_DC_" + i + "(n)");
			objExpr.addTerm(-flowCost_plant_DC[i], plant_DC[0][i]);
		}
		for (int i = 0; i < nDistributionCenter; i++) {
			plant_DC[1][i] = sub.numVar(0.0, Double.MAX_VALUE, 
					"plant_DC_" + i + "(r)");
			objExpr.addTerm(-flowCost_plant_DC[i], plant_DC[1][i]);	
		}
		for (int i = 0; i < nDistributionCenter; i++) {
			for (int j = 0; j < nMarket; j++) {                  
				this.DC_customers[0][i][j] = sub.numVar(0.0, Double.MAX_VALUE, 
						"DC_" + i + "_Customer_" + j + "(n)");
				objExpr.addTerm(-flowCost_DC_customers[i][j], DC_customers[0][i][j]);
			}
		}
		for (int i = 0; i < nDistributionCenter; i++) {
			for (int j = 0; j < nMarket; j++) {                  
				this.DC_customers[1][i][j] = sub.numVar(0.0, Double.MAX_VALUE, 
						"DC_" + i + "_Customer_" + j + "(r)");
				objExpr.addTerm(-flowCost_DC_customers[i][j], DC_customers[1][i][j]);
			}
		}
		for (int i = 0; i < nMarket; i++) {
			for (int j = 0; j < nReturnCenter; j++) {
				this.customers_RC[i][j] = sub.numVar(0.0, Double.MAX_VALUE, 
						"customers_" + i + "_RC_" + j);
				objExpr.addTerm(-flowCost_customers_RC[i][j], customers_RC[i][j]);
			}
		}
		for (int i = 0; i < nReturnCenter; i++) {
			this.RC_plant[i] = sub.numVar(0.0, Double.MAX_VALUE, 
					"RC_" + i + "plant");
			objExpr.addTerm(-flowCost_RC_plant[i], RC_plant[i]);
		}

		sub.addMaximize(objExpr, "operationalProfit");   
		for (int i = 0; i < nDistributionCenter; i++) {
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[0][i]);
			sub.addGe(objExpr, 0, "c_NonnegativityPlant_DC_" + i + "(n)");
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[1][i]);
			sub.addGe(objExpr, 0, "c_NonnegativityPlant_DC_" + i + "(r)");
		}
		for (int i = 0; i < nDistributionCenter; i++) {
			for (int j = 0; j < nMarket; j++) { 
				objExpr.clear();
				objExpr.addTerm(1, DC_customers[0][i][j]);
				sub.addGe(objExpr, 0, "c_NonnegativityDC_" + i + "Customer_" + j + "(n)");
				objExpr.clear();
				objExpr.addTerm(1, DC_customers[1][i][j]);
				sub.addGe(objExpr, 0, "c_NonnegativityDC_" + i + "Customer_" + j + "(r)");
			}
		}
		for (int i = 0; i < nMarket; i++) {
			for (int j = 0; j < nReturnCenter; j++) {
				objExpr.clear();
				objExpr.addTerm(1, customers_RC[i][j]);
				sub.addGe(objExpr, 0, "c_NonnegativityCustomers_" + i + "RC_" + j);
			}
		}
		for (int i = 0; i < nReturnCenter; i++) {
			objExpr.clear();
			objExpr.addTerm(1, RC_plant[i]);
			sub.addGe(objExpr, 0, "c_NonnegativityRC_" + i + "_plant");
		}

		// add capacity constraints to be satisfied -- record the constraints for use later
		cCapacity = new IloRange[4];

		// manQuantity = sub.numVar(0, Double.MAX_VALUE, "ManQuantity");   
		objExpr.clear();
		objExpr.addTerm(1.0, manQuantity);
		cCapacity[0] = sub.addLe(objExpr, capacity_DC[0], "capacity_manCapacity");
		//		rhs.put(cCapacity[0], master.linearNumExpr(manCapacity));

		// remanQuantity = sub.numVar(0, Double.MAX_VALUE, "RemanQuantity");   
		objExpr.clear();
		objExpr.addTerm(1.0, remanQuantity);
		cCapacity[1] = sub.addLe(objExpr, capacity_DC[1], "capacity_remanCapacity");
		//		rhs.put(cCapacity[1], master.linearNumExpr(remanCapacity));

		objExpr.clear();
		objExpr.addTerm(1.0, manQuantity);
		objExpr.addTerm(1.0, remanQuantity);
		cCapacity[2] = sub.addLe(objExpr, marketSize, "capacity_marketSize");

		objExpr.clear();
		objExpr.addTerm(1.0, remanQuantity);
		cCapacity[3] = sub.addLe(objExpr, recoveryRate*ret, "capacity_remanufacturable");

		//supply constraints
		cSupply_DC_new = new IloRange[nDistributionCenter][nMarket];
		for (int i = 0; i < nDistributionCenter; i++) {
			for (int j = 0; j < nMarket; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, DC_customers[0][i][j]);
				cSupply_DC_new[i][j] = sub.addLe(objExpr, 0, "supply_DC(n): DC_" + i + "_Customer" + j);
			}
		}	
		
		cSupply_DC_reman = new IloRange[nDistributionCenter][nMarket];
		for (int i = 0; i < nDistributionCenter; i++) {
			for (int j = 0; j < nMarket; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, DC_customers[1][i][j]);
				cSupply_DC_reman[i][j] = sub.addLe(objExpr, 0, "supply_DC(r): DC_" + i + "_Customer" + j);
			}
		}
		cSupply_RC = new IloRange[nMarket][nReturnCenter];
		for (int i = 0; i < nMarket; i++) {
			for (int j = 0; j < nReturnCenter; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, customers_RC[i][j]);
				cSupply_RC[i][j] = sub.addLe(objExpr, 0, "supply_RC: Customer_" + i + "_RC_"+ j);
			}
		}

		// add flow balance constraints to be satisfied -- record the constraints for use later
		// RC_plant[i]-recoveryRate*\sum_j customers_RC[j][i] <= 0 for all i
		for (int i = 0; i < nReturnCenter; i++) {
			objExpr.clear();
			for(int j = 0; j < nMarket; j++){
				objExpr.addTerm(-recoveryRate, customers_RC[j][i]);
			}
			objExpr.addTerm(1, RC_plant[i]);
			sub.addLe(objExpr, 0,"flowBalance_RC_" + i);
		}

		// manQuantity - \sum_j plant_DC[0][j][0] = 0
		objExpr.clear();
		objExpr.addTerm(1, manQuantity);
		for (int i = 0; i < nDistributionCenter; i++) {
			objExpr.addTerm(-1, plant_DC[0][i]);	
		}
		sub.addEq(objExpr, 0, "flowBalance_plant(n)");	

		// remanQuantity - \sum_j plant_DC[0][j][1] = 0
		objExpr.clear();
		objExpr.addTerm(1, remanQuantity);
		for (int i = 0; i < nDistributionCenter; i++) {
			objExpr.addTerm(-1, plant_DC[1][i]);	
		}
		sub.addEq(objExpr, 0, "flowBalance_plant(r)");		

		// plant_DC[l][i] - \sum_j DC_customers[l][i][j] = 0  for all i & l
		for (int i = 0; i < nDistributionCenter; i++) {
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[0][i]);
			for (int j = 0; j < nMarket; j++) {	
				objExpr.addTerm(-1, DC_customers[0][i][j]);
			}
			sub.addEq(objExpr, 0,"flowBalance_DC_" + i + "(n)");
		}
		for (int i = 0; i < nDistributionCenter; i++) {
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[1][i]);
			for (int j = 0; j < nMarket; j++) {	
				objExpr.addTerm(-1, DC_customers[1][i][j]);
			}
			sub.addEq(objExpr, 0,"flowBalance_DC_" + i + "(r)");
		}

		// PPDensity[j] * manQuantity - \sum_i DC_customers[i][j][1] = 0  for all j
		for (int j = 0; j < nMarket; j++) {
			objExpr.clear();
			objExpr.addTerm(PPDensity[j], manQuantity);     //new product demand for customer j
			for (int i = 0; i < nDistributionCenter; i++) {	
				objExpr.addTerm(-1, DC_customers[0][i][j]);
			}
			sub.addEq(objExpr, 0, "flowBalance_demand_" + j + "(n)");
		}
		// PPDensity[j] * remanQuantity - \sum_i DC_customers[i][j][2] = 0  for all j
		for (int j = 0; j < nMarket; j++) {
			objExpr.clear();
			objExpr.addTerm(PPDensity[j], remanQuantity);
			for (int i = 0; i < nDistributionCenter; i++) {	
				objExpr.addTerm(-1, DC_customers[1][i][j]);
			}
			sub.addEq(objExpr, 0, "flowBalance_demand_" + j + "(r)");
		}
		// \sum_i customers_RC[j][i] = returns*PPDensity[j]   for all j
		cFlowBalance_return = new IloRange[nMarket];
		for (int j = 0; j < nMarket; j++) {
			objExpr.clear();
			for (int i = 0; i < nReturnCenter; i++) {
				objExpr.addTerm(1, customers_RC[j][i]);
			}
			cFlowBalance_return[j] = sub.addEq(objExpr, ret*PPDensity[j], "flowBalance_return_Customer_" + j);
			//			rhs.put(cFlowBalance_return[j], master.linearNumExpr(ret*PPDensity[j]));
		}
		// remanQuantity - \sum_i RC_plant[i][0] = 0
		objExpr.clear();
		objExpr.addTerm(1, remanQuantity);
		for (int i = 0; i < nReturnCenter; i++) {
			objExpr.addTerm(-1, RC_plant[i]);
		}
		sub.addEq(objExpr, 0, "flowBalance_RC_plant");

		// disable presolving of the subproblem (if the presolver realizes the
		// subproblem is infeasible, we do not get a dual ray)
		//		sub.setParam(IloCplex.BooleanParam.PreInd, false);
		sub.setParam(IloCplex.IntParam.RootAlg, IloCplex.Algorithm.Network);

		sub.setParam(IloCplex.IntParam.MIPEmphasis, IloCplex.MIPEmphasis.Optimality);
		sub.setParam(IloCplex.BooleanParam.NumericalEmphasis, true);
//		sub.setParam(IloCplex.DoubleParam.PolishAfterTime ,0);
//		sub.setParam(IloCplex.IntParam.RINSHeur,1);
		sub.setParam(IloCplex.IntParam.NodeAlg, IloCplex.Algorithm.Barrier);
//		sub.setParam(IloCplex.IntParam.ParallelMode, IloCplex.ParallelMode.Opportunistic);
		sub.setParam(IloCplex.DoubleParam.WorkMem, 3000);//set memory to 4G
		sub.setParam(IloCplex.IntParam.VarSel, VariableSelect.Strong);
//		sub.setParam(IloCplex.IntParam.NodeSel, NodeSelect.DFS);
		sub.setParam(IloCplex.IntParam.NodeFileInd ,2);
		sub.setParam(IloCplex.StringParam.WorkDir ,"c:\\users\\wchen\\Documents\\model");
//		sub.setParam(IloCplex.StringParam.WorkDir ,"/home/zhaofeng/BSH/model");
	}

	public void updateSubProblem(double[] udc, double[] urc) throws IloException{
		// set the supply constraint right-hand sides in the subproblem
//		cSupply_DC_new = new IloRange[nDistributionCenter][nMarket];
//		for (int i = 0; i < nDistributionCenter; i++) {
//			for (int j = 0; j < nMarket; j++) {
//				objExpr.clear();
//				objExpr.addTerm(1.0, DC_customers[0][i][j]);
//				cSupply_DC_new[i][j] = sub.addLe(objExpr, 0, "supply_DC(n): DC_" + i + "_Customer" + j);
//			}
//		}	
		
		
//		IloLQNumExpr objExpr = sub.lqNumExpr();
//		for (int i = 0; i < nDistributionCenter; i++) {
//				cSupply_DC_new[i].clear();
//			for(int j = 0; j < nMarket; j++){
//				objExpr.clear();
//				objExpr.addTerm(1.0, DC_customers[0][i][j]);
//				cSupply_DC_new[i].addRow(sub.addLe(objExpr, 0, "supply_DC(n): DC_" + i + "_Customer" + j));
//			}
//			sub.addLPMatrix()
//		}	
		
		for (int i = 0; i < nDistributionCenter; i++) {
			for(int j = 0; j < nMarket; j++){
				cSupply_DC_new[i][j].setUB((udc[i] > 0.5) ? capacity_DC[0] : 0);  
				cSupply_DC_reman[i][j].setUB((udc[i] > 0.5) ? capacity_DC[1] : 0);					
			}
		}
		for (int i = 0; i < nMarket; i++) {
			for(int j = 0; j < nReturnCenter; j++){
				cSupply_RC[i][j].setUB((urc[j] > 0.5) ? capacity_RC[j] : 0);
			}
		}
		
	}
}
