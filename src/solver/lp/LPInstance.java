package solver.lp;

import ilog.cplex.*;
import ilog.concert.*;

import java.io.File;
import java.io.FileNotFoundException;

import java.util.Scanner;
import java.util.function.DoublePredicate;

public class LPInstance
{
  // Supply Chain Management (SCM) Input Parameters
  int numCustomers;        		// the number of customers	   
  int numFacilities;           	// the number of facilities
  double[][] allocCostCF;   	// allocCostCF[c][f] is the service cost paid each time customer c is served by facility f
  double[] demandC;     		// demandC[c] is the demand of customer c
  double[] openingCostF;        // openingCostF[f] is the opening cost of facility f
  double[] capacityF;        	// capacityF[f] is the capacity of facility f
  int numMaxVehiclePerFacility; // maximum number of vehicles to use at an open facility 
  double truckDistLimit;        // total driving distance limit for trucks
  double truckUsageCost;		// fixed usage cost paid if a truck is used 
  double[][] distanceCF;        // distanceCF[c][f] is the roundtrip distance between customer c and facility f 
  
  // IBM Ilog Cplex Solver 
  IloCplex cplex;
    
  // Linear Programming (LP) Objective value
  double objectiveValue;
  
  public void solve() throws IloException
  {
    try
    {
      cplex = new IloCplex();
    
    //    // Diet Problem from Lecture Notes
    //   IloNumVar[] vars = cplex.numVarArray(2, 0, 1000, IloNumVarType.Float);

    //   IloNumExpr carbs = cplex.numExpr();
    //   carbs = cplex.sum(carbs, cplex.prod(100, vars[0]));
    //   carbs = cplex.sum(carbs, cplex.prod(250, vars[1]));
  
    //   cplex.addGe(carbs, 500);
    //   cplex.addGe(cplex.scalProd(new int[]{100, 50}, vars), 250);	// Fat
    //   cplex.addGe(cplex.scalProd(new int[]{150, 200}, vars), 600);	// Protein

    //   // Objective function 
    //   cplex.addMinimize(cplex.scalProd(new int[]{25, 15}, vars));

    //   if(cplex.solve())
    //   {
    //     objectiveValue = Math.ceil(cplex.getObjValue());
		
    //     System.out.println("Meat:  " + cplex.getValue(vars[0]));
    //     System.out.println("Bread:  " + cplex.getValue(vars[1]));
    //     System.out.println("Objective value: " + cplex.getObjValue());
    //   }
    //   else
    //   {
    //     System.out.println("No Solution found!");
    //   }

        IloNumVar[] numVehicles = cplex.numVarArray(this.numFacilities, 0, numMaxVehiclePerFacility, IloNumVarType.Float);

        IloNumVar[] open = cplex.numVarArray(this.numFacilities, 0, 1, IloNumVarType.Float);
        IloNumVar[][] facilUsed = new IloNumVar[numCustomers][numFacilities];
        for(int i=0; i<this.numCustomers; i++){
            facilUsed[i] = cplex.numVarArray(this.numFacilities,0,1,IloNumVarType.Float);
        }
        for(int i=0; i<this.numFacilities; i++){
            IloNumVar[] distPerCust = cplex.numVarArray(this.numCustomers, 0, Integer.MAX_VALUE, IloNumVarType.Float);
            IloNumVar[] amtPerCust = cplex.numVarArray(this.numCustomers, 0, Integer.MAX_VALUE, IloNumVarType.Float);
            for(int j = 0; j<this.numCustomers; j++){
                cplex.addEq(distPerCust[j], cplex.prod(facilUsed[j][i], distanceCF[j][i]));
                cplex.addEq(amtPerCust[j], cplex.prod(demandC[j], facilUsed[j][i]));
            }
            cplex.addLe(cplex.sum(amtPerCust), capacityF[i]);
            cplex.addLe(cplex.sum(distPerCust), cplex.prod(numVehicles[i], truckDistLimit));
            cplex.addEq(cplex.prod(open[i],capacityF[i]), cplex.sum(amtPerCust));
        }

        for(int i = 0; i<numCustomers; i++){
            cplex.addEq(cplex.sum(facilUsed[i]),1);
        }

        IloNumExpr allocCost = cplex.numExpr();
        IloNumExpr openingCost = cplex.numExpr();
        IloNumExpr vehicleCost = cplex.numExpr();
        IloNumExpr truckUsage = cplex.numExpr();

        IloNumVar[][] scaledAlloc = new IloNumVar[numCustomers][numFacilities];
        for(int i=0; i<this.numCustomers; i++){
            scaledAlloc[i] = cplex.numVarArray(this.numFacilities,0,Integer.MAX_VALUE,IloNumVarType.Float);
            for(int j= 0; j<this.numFacilities; j++){
                cplex.addEq(scaledAlloc[i][j], cplex.prod(facilUsed[i][j],allocCostCF[i][j]));
            }
        }
        IloNumVar[] scaledOpen = cplex.numVarArray(this.numFacilities, 0, Integer.MAX_VALUE, IloNumVarType.Float);
        for(int i = 0; i<numFacilities; i++){
            cplex.addEq(scaledOpen[i], cplex.prod(open[i],this.openingCostF[i]));
        }

        IloNumVar[] flattened = cplex.numVarArray(this.numCustomers, 0, Integer.MAX_VALUE, IloNumVarType.Float);
        for(int i = 0; i<numCustomers; i++){
            cplex.addEq(flattened[i], cplex.sum(scaledAlloc[i]));
        } 

        allocCost = cplex.sum(flattened);
        openingCost = cplex.sum(scaledOpen);
        vehicleCost = cplex.prod(cplex.sum(numVehicles), this.truckUsageCost);
        cplex.addMinimize(cplex.sum(cplex.sum(allocCost, openingCost), vehicleCost));

        if(cplex.solve())
       {
         objectiveValue = Math.ceil(cplex.getObjValue());
		
         System.out.println("Objective value: " + cplex.getObjValue());
       }
    }
    catch(IloException e)
    {
      System.out.println("Error " + e);
    }
  }

  public LPInstance(String fileName)
  {
    Scanner read = null;
    try
    {
      read = new Scanner(new File(fileName));
    } catch (FileNotFoundException e)
    {
      System.out.println("Error: in LPInstance() " + fileName + "\n" + e.getMessage());
      System.exit(-1);
    }

    numCustomers = read.nextInt(); 
    numFacilities = read.nextInt();
    numMaxVehiclePerFacility = numCustomers; // At worst case visit every customer with one vehicle
    
    System.out.println("numCustomers: " + numCustomers);
    System.out.println("numFacilities: " + numFacilities);
    System.out.println("numVehicle: " + numMaxVehiclePerFacility);
      
    allocCostCF = new double[numCustomers][];
    for (int i = 0; i < numCustomers; i++)
      allocCostCF[i] = new double[numFacilities];

    demandC = new double[numCustomers];
    openingCostF = new double[numFacilities];
    capacityF = new double[numFacilities];

    for (int i = 0; i < numCustomers; i++)
      for (int j = 0; j < numFacilities; j++)
        allocCostCF[i][j] = read.nextDouble();

    for (int i = 0; i < numCustomers; i++)
      demandC[i] = read.nextDouble();

    for (int i = 0; i < numFacilities; i++)
      openingCostF[i] = read.nextDouble();

    for (int i = 0; i < numFacilities; i++)
      capacityF[i] = read.nextDouble();
    
    truckDistLimit = read.nextDouble();
    truckUsageCost = read.nextDouble();
 
    distanceCF = new double[numCustomers][];
    for (int i = 0; i < numCustomers; i++)
      distanceCF[i] = new double[numFacilities];
      
    for (int i = 0; i < numCustomers; i++)
      for (int j = 0; j < numFacilities; j++)
        distanceCF[i][j] = read.nextDouble();
    }

}
