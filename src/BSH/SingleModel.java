/*
 * This implements a single MIP model to solve the close loop supply chain problem.
 */
package BSH;

import ilog.concert.IloException;
import ilog.concert.IloLQNumExpr;
import ilog.concert.IloNumVar;
import ilog.cplex.IloCplex;
import ilog.cplex.IloCplex.VariableSelect;

/**
 * @author Wenyi Chen (wchen@zlc.edu.es)
 * @author Feng Zhao (zhaofeng@nus.edu.sg)
 */
public class SingleModel {
	private IloCplex cplex;      // the MIP model
	private IloNumVar[] use_DC;     // use[i] = 1 if DC of i is used, 0 if not
	private IloNumVar[] use_RC;     // use[i] = 1 if RC of i is used, 0 if not

	private IloNumVar[][] plant_DC;  // flow from plant i to DC j  //wenyi: change dimension
	private IloNumVar[][][] DC_customers;  // flow from DC i to customer j   //wenyi: change dimension
	private IloNumVar[][] customers_RC;  //  flow from customer i to RC j
	private IloNumVar[] RC_plant;  //  flow from RC i to plant j

	private IloNumVar manQuantity;	// manufacturing production quantity
	private IloNumVar remanQuantity;	// remanufacturing production quantity

	/**
	 * Constructor.
	 * @param nW  number of (potential) warehouses
	 * @param nC  number of customers
	 * @param capacity  capacities of warehouses
	 * @param demand  demands of customers
	 * @param fixed  fixed costs to open warehouses
	 * @param flow  unit flow 
	 * @throws IloException if CPLEX is unhappy
	 */
	public SingleModel(int nDC, int nRC, int nCustomers, double manCapacity, double remanCapacity, 
			double ret, double recoveryRate, double marketSize, 
			double[] fixedCostDC, double[] fixedCostRC, 
			double[] PPDensity, double[] flowCost_plant_DC, double[][] flowCost_DC_customers, 
			double[][] flowCost_customers_RC, double[] flowCost_RC_plant) throws IloException {
		cplex = new IloCplex();
		use_DC = new IloNumVar[nDC];
		use_RC = new IloNumVar[nRC];
		plant_DC = new IloNumVar[2][nDC];    
		DC_customers = new IloNumVar[2][nDC][nCustomers];    
		customers_RC = new IloNumVar[nCustomers][nRC];
		RC_plant = new IloNumVar[nRC];
		manQuantity = cplex.numVar(0, manCapacity, "manQuantity");
		remanQuantity = cplex.numVar(0, remanCapacity, "remanQuantity");

		double reservationPriceUB = 600; 
		double reservationPriceLB = 370;
		double remanDepreciation = 0.7;
		double manCost = 370;
		double remanCost = 225;
		double holdingCost = 10;
		//double sortingCost = 20;
		double disposalCost = 50;
		double I = (reservationPriceUB - reservationPriceLB)/marketSize;   //I=(resPriceUB - resPriceLB) / marketSize;
		double manA = reservationPriceUB - manCost - holdingCost;
		double remanA = remanDepreciation * reservationPriceUB - remanCost - holdingCost + disposalCost;

		//minimize I * manQuantity * manQuantity + 2 * remanDepreciation * I * manQuantity * remanQuantity
		//          + remanDepreciation * I * remanQuantity * remanQuantity
		//          - manA * manQuantity - remanA * remanQuantity
		//          + fixedCostDC + fixedCostRC
		//          + flowCost_plant_DC + flowCost_DC_customers + flowCost_customers_RC + flowCost_RC_plant

		//wenyi
		IloLQNumExpr objExpr = cplex.lqNumExpr();
		objExpr.addTerm(-I, manQuantity, manQuantity);
		objExpr.addTerm(-2 * remanDepreciation * I, manQuantity, remanQuantity);
		objExpr.addTerm(-remanDepreciation * I, remanQuantity, remanQuantity);
		objExpr.addTerm(manA, manQuantity);
		objExpr.addTerm(remanA, remanQuantity);

		//IloLinearNumExpr expr = cplex.linearNumExpr();
		// declare the variables and simultaneously assemble the objective function
		for (int i = 0; i < nDC; i++) {
			use_DC[i] = cplex.boolVar("Use_DC_" + i);
			objExpr.addTerm(-fixedCostDC[i], use_DC[i]);
		}
		for (int i = 0; i < nRC; i++) {
			use_RC[i] = cplex.boolVar("Use_RC_" + i);
			objExpr.addTerm(-fixedCostRC[i], use_RC[i]);
		}

		
//		for (int j = 0; j < 2; j++){      
//			for (int k = 0; k < nDC; k++) {
//				//wenyi: add dimension l
//				this.plant_DC[j][k] = cplex.numVar(0.0, Double.MAX_VALUE, 
//						"plant_DC_" + j + "_" + k);
//				objExpr.addTerm(flowCost_plant_DC[j], plant_DC[j][k]); 
//			}
//		}
		for (int i = 0; i < nDC; i++) {
			plant_DC[0][i] = cplex.numVar(0.0, Double.MAX_VALUE, 
					"plant_DC_" + i + "(n)");
			objExpr.addTerm(-flowCost_plant_DC[i], plant_DC[0][i]);
		}
		for (int i = 0; i < nDC; i++) {
			plant_DC[1][i] = cplex.numVar(0.0, Double.MAX_VALUE, 
					"plant_DC_" + i + "(r)");
			objExpr.addTerm(-flowCost_plant_DC[i], plant_DC[1][i]);	
		}
		for (int i = 0; i < nDC; i++) {
			for (int j = 0; j < nCustomers; j++) {                  
				this.DC_customers[0][i][j] = cplex.numVar(0.0, Double.MAX_VALUE, 
						"DC_" + i + "_Customer_" + j + "(n)");
				objExpr.addTerm(-flowCost_DC_customers[i][j], DC_customers[0][i][j]);
			}
		}
		for (int i = 0; i < nDC; i++) {
			for (int j = 0; j < nCustomers; j++) {                  
				this.DC_customers[1][i][j] = cplex.numVar(0.0, Double.MAX_VALUE, 
						"DC_" + i + "_Customer_" + j + "(r)");
				objExpr.addTerm(-flowCost_DC_customers[i][j], DC_customers[1][i][j]);
			}
		}
		for (int i = 0; i < nCustomers; i++) {
			for (int j = 0; j < nRC; j++) {
				this.customers_RC[i][j] = cplex.numVar(0.0, Double.MAX_VALUE, 
						"customers_" + i + "_RC_" + j);
				objExpr.addTerm(-flowCost_customers_RC[i][j], customers_RC[i][j]);
			}
		}
		for (int i = 0; i < nRC; i++) {
			this.RC_plant[i] = cplex.numVar(0.0, Double.MAX_VALUE, 
					"RC_" + i + "plant");
			objExpr.addTerm(-flowCost_RC_plant[i], RC_plant[i]);
		}
		cplex.addMaximize(objExpr, "totalProfit");
	

		// add capacity constraints
		objExpr.clear();
		objExpr.addTerm(1.0, manQuantity);
		cplex.addLe(objExpr, manCapacity, "capacity_manCapacity");
		objExpr.clear();
		objExpr.addTerm(1.0, remanQuantity);
		cplex.addLe(objExpr, remanCapacity, "capacity_remanCapacity");
		objExpr.clear();
		objExpr.addTerm(1.0, manQuantity);
		objExpr.addTerm(1.0, remanQuantity);
		cplex.addLe(objExpr, marketSize, "capacity_marketSize");
		objExpr.clear();
		objExpr.addTerm(1.0, remanQuantity);
		cplex.addLe(objExpr, recoveryRate*ret, "capacity_remanufacturable");
	
		//wenyi
		for (int i = 0; i < nDC; i++) {
			for (int j = 0; j < nCustomers; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, DC_customers[0][i][j]);
				objExpr.addTerm(-manCapacity, use_DC[i]);
				cplex.addLe(objExpr, 0, "supply_DC(n): DC_" + i + "_Customer" + j);
			}
		}	
		
		//wenyi
		for (int i = 0; i < nDC; i++) {
			for (int j = 0; j < nCustomers; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, DC_customers[1][i][j]);
				objExpr.addTerm(-remanCapacity, use_DC[i]);
				cplex.addLe(objExpr, 0, "supply_DC(r): DC_" + i + "_Customer" + j);
			}
		}
		
		//wenyi
		for (int i = 0; i < nCustomers; i++) {
			for (int j = 0; j < nRC; j++) {
				objExpr.clear();
				objExpr.addTerm(1.0, customers_RC[i][j]);
				objExpr.addTerm(-ret, use_RC[j]);
				cplex.addLe(objExpr, 0, "supply_RC: Customer_" + i + "_RC_"+ j);
			}
		}

		// add flow balance constraints
		// wenyi: RC_plant[i][0]-recoveryRate*\sum_j customers_RC[j][i] <= 0 for all i
		for (int i = 0; i < nRC; i++) {
			objExpr.clear();
			for(int j = 0; j < nCustomers; j++){
				objExpr.addTerm(-recoveryRate, customers_RC[j][i]);
			}
			objExpr.addTerm(1, RC_plant[i]);
			cplex.addLe(objExpr, 0,"flowBalance_RC_" + i);
		}

		// wenyi: manQuantity - \sum_j plant_DC[0][j][0] = 0
		objExpr.clear();
		objExpr.addTerm(1, manQuantity);
		for (int i = 0; i < nDC; i++) {
			objExpr.addTerm(-1, plant_DC[0][i]);	
		}
		cplex.addEq(objExpr, 0, "flowBalance_plant(n)");	
		
		// wenyi: remanQuantity - \sum_j plant_DC[0][j][1] = 0
		objExpr.clear();
		objExpr.addTerm(1, remanQuantity);
		for (int i = 0; i < nDC; i++) {
			objExpr.addTerm(-1, plant_DC[1][i]);	
		}
		cplex.addEq(objExpr, 0, "flowBalance_plant(r)");	
	
		// wenyi: plant_DC[0][i][l] - \sum_j DC_customers[i][j][l] = 0  for all i & l
		for (int i = 0; i < nDC; i++) {
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[0][i]);
			for (int j = 0; j < nCustomers; j++) {	
				objExpr.addTerm(-1, DC_customers[0][i][j]);
			}
			cplex.addEq(objExpr, 0,"flowBalance_DC_" + i + "(n)");
		}
		for (int i = 0; i < nDC; i++) {
			objExpr.clear();
			objExpr.addTerm(1, plant_DC[1][i]);
			for (int j = 0; j < nCustomers; j++) {	
				objExpr.addTerm(-1, DC_customers[1][i][j]);
			}
			cplex.addEq(objExpr, 0,"flowBalance_DC_" + i + "(r)");
		}
	

		// wenyi: PPDensity[j] * manQuantity - \sum_i DC_customers[i][j][1] = 0  for all j
		for (int j = 0; j < nCustomers; j++) {
			objExpr.clear();
			objExpr.addTerm(PPDensity[j], manQuantity);     //new product demand for customer j
			for (int i = 0; i < nDC; i++) {	
				objExpr.addTerm(-1, DC_customers[0][i][j]);
			}
			cplex.addEq(objExpr, 0, "flowBalance_demand_" + j + "(n)");
		}

		// wenyi: PPDensity[j] * remanQuantity - \sum_i DC_customers[i][j][2] = 0  for all j
		for (int j = 0; j < nCustomers; j++) {
			objExpr.clear();
			objExpr.addTerm(PPDensity[j], remanQuantity);
			for (int i = 0; i < nDC; i++) {	
				objExpr.addTerm(-1, DC_customers[1][i][j]);
			}
			cplex.addEq(objExpr, 0, "flowBalance_demand_" + j + "(r)");
		}
		
		// wenyi: \sum_i customers_RC[j][i] = returns*PPDensity[j]   for all j
		for (int j = 0; j < nCustomers; j++) {
			objExpr.clear();
			for (int i = 0; i < nRC; i++) {
				objExpr.addTerm(1, customers_RC[j][i]);
			}
			cplex.addEq(objExpr, ret*PPDensity[j], "flowBalance_return_Customer_" + j);
		}

		// wenyi: remanQuantity - \sum_i RC_plant[i][0] = 0
		objExpr.clear();
		objExpr.addTerm(1, remanQuantity);
		for (int i = 0; i < nRC; i++) {
			objExpr.addTerm(-1, RC_plant[i]);
		}
		cplex.addEq(objExpr, 0, "flowBalance_RC_plant");
		
		//added constraints to fix location for alpha_{eff} analysis
		//DC: 0, 5, 7
		//RC: 7, 9
//		objExpr.clear();
//		use_DC[0] = cplex.boolVar("Use_DC_" + 0);
//		objExpr.addTerm(1, use_DC[0]);
//		cplex.addEq(objExpr, 1, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[1] = cplex.boolVar("Use_DC_" + 1);
//		objExpr.addTerm(1, use_DC[1]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[2] = cplex.boolVar("Use_DC_" + 2);
//		objExpr.addTerm(1, use_DC[2]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[3] = cplex.boolVar("Use_DC_" + 3);
//		objExpr.addTerm(1, use_DC[3]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[4] = cplex.boolVar("Use_DC_" + 4);
//		objExpr.addTerm(1, use_DC[4]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[5] = cplex.boolVar("Use_DC_" + 5);
//		objExpr.addTerm(1, use_DC[5]);
//		cplex.addEq(objExpr, 1, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[6] = cplex.boolVar("Use_DC_" + 6);
//		objExpr.addTerm(1, use_DC[6]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[7] = cplex.boolVar("Use_DC_" + 7);
//		objExpr.addTerm(1, use_DC[7]);
//		cplex.addEq(objExpr, 1, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[8] = cplex.boolVar("Use_DC_" + 8);
//		objExpr.addTerm(1, use_DC[8]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_DC[9] = cplex.boolVar("Use_DC_" + 9);
//		objExpr.addTerm(1, use_DC[9]);
//		cplex.addEq(objExpr, 0, "useDC fixed value");
//		
//		objExpr.clear();
//		use_RC[0] = cplex.boolVar("Use_RC_" + 0);
//		objExpr.addTerm(1, use_RC[0]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[1] = cplex.boolVar("Use_RC_" + 1);
//		objExpr.addTerm(1, use_RC[1]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[2] = cplex.boolVar("Use_RC_" + 2);
//		objExpr.addTerm(1, use_RC[2]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[3] = cplex.boolVar("Use_RC_" + 3);
//		objExpr.addTerm(1, use_RC[3]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[4] = cplex.boolVar("Use_RC_" + 4);
//		objExpr.addTerm(1, use_RC[4]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[5] = cplex.boolVar("Use_RC_" + 5);
//		objExpr.addTerm(1, use_RC[5]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[6] = cplex.boolVar("Use_RC_" + 6);
//		objExpr.addTerm(1, use_RC[6]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[7] = cplex.boolVar("Use_RC_" + 7);
//		objExpr.addTerm(1, use_RC[7]);
//		cplex.addEq(objExpr, 1, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[8] = cplex.boolVar("Use_RC_" + 8);
//		objExpr.addTerm(1, use_RC[8]);
//		cplex.addEq(objExpr, 0, "useRC fixed value");
//		
//		objExpr.clear();
//		use_RC[9] = cplex.boolVar("Use_RC_" + 9);
//		objExpr.addTerm(1, use_RC[9]);
//		cplex.addEq(objExpr, 1, "useRC fixed value");
		
		//output the model 
		System.out.println(cplex);
	}
	/**
	 * Solves the unified model.
	 * @return the solution (in an instance of Solution)
	 * @throws IloException if CPLEX encounters problems
	 */
	public PrimalSolution solve() throws IloException {
		PrimalSolution s = new PrimalSolution();
		// try to solve the model
		if (cplex.solve()) {
			s.profit = cplex.getObjValue();

			s.udc = cplex.getValues(use_DC);
			s.urc = cplex.getValues(use_RC);
			
			s.plant_DC = new double[2][plant_DC[0].length];
			for(int i = 0; i< plant_DC.length;i++){
				s.plant_DC[i] = cplex.getValues(plant_DC[i]);
			}

			s.DC_customers = new double[2][DC_customers[0].length][DC_customers[0][0].length];
			for(int i = 0; i< DC_customers.length;i++){
				for(int j = 0; j< DC_customers[0].length;j++){
					s.DC_customers[i][j] = cplex.getValues(DC_customers[i][j]);
				}
			}

			s.customers_RC = new double[customers_RC.length][customers_RC[0].length];
			for(int i = 0; i< customers_RC.length;i++){
				s.customers_RC[i] = cplex.getValues(customers_RC[i]);
			}

			s.RC_plant = new double[RC_plant.length];
			s.RC_plant = cplex.getValues(RC_plant);
			
			s.manQuantity = cplex.getValue(manQuantity);;
			s.remanQuantity = cplex.getValue(remanQuantity);
			
		}
		s.status = cplex.getCplexStatus();
		return s;

	}
}
