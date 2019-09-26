package org.ecocean.grid.optimization;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Arrays;

import org.apache.commons.math3.*;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.OptimizationData;
// import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateOptimizer;
// import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
// import org.apache.commons.math3.optimization.SimpleValueChecker;
// import org.apache.commons.math3.optim.nonlinear.scalar.GradientMultivariateOptimizerMultivariateFunctionMappingAdapter);

import org.ecocean.grid.GrothAnalysis;

import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;

public class GrothParameterOptimizer {

    //Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C}   

    final static double[] defaults = new double[] {0.1, 50.0, 0.9999, 10.0, 0.99};

    double[] upperBounds = new double[] {0.15, 50.0, 0.9999, 30.0, 0.99};
    double[] lowerBounds = new double[] {0.0005, 5.0, 0.85, 5.0, 0.9};

    GoalType goal = GoalType.MAXIMIZE;;
    GrothAnalysis ga = new GrothAnalysis();
    
    int MAXITER = 1000;
    int MAXEVAL = 1000;

    public void setGoalTypeAsMax() {
        goal = GoalType.MAXIMIZE;
    }

    public void setGoalTypeAsMin() {
        goal = GoalType.MINIMIZE;
    }

    public void setUpperBounds(double[] newBounds) {
        this.upperBounds = newBounds;
    }

    public void setLowerBounds(double[] newBounds) {
        this.lowerBounds = newBounds;
    }



    public double[] doOptimize() {

        double[] optimumVals = new double[4];  
        try {

            final ConvergenceChecker<PointValuePair> cchecker = new SimpleValueChecker(-1, 0.001);
            SimplexOptimizer optimizer = new SimplexOptimizer(cchecker);

            // bunch of song and dance to format the function and made it bounded 
            MultivariateFunction mf = (MultivariateFunction) ga;
            MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(mf, lowerBounds, upperBounds);
            ObjectiveFunction of = new ObjectiveFunction(mfma); 

            // these are your opts.. different implementations of the OptimizationData interface 
            // it's pretty difficult to acertain what the different optimizers want cuz they take as many as you like even if they do nothing
            InitialGuess ig = new InitialGuess(defaults);
            SimpleBounds sb = new SimpleBounds(lowerBounds, upperBounds);
            
            PointValuePair result = optimizer.optimize(of, goal, sb, ig);

            System.out.println("Here is the result of optimization: "+result.toString()`);


        } catch (Exception e) {
            e.printStackTrace();
        }



        return optimumVals;
    }


}