package org.ecocean.grid.optimization;

import java.util.Enumeration;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

import org.json.*;

import java.nio.file.*;

import org.apache.commons.math3.*;
import org.apache.commons.math3.optim.ConvergenceChecker;
import org.apache.commons.math3.optim.SimpleValueChecker;
import org.apache.commons.math3.optim.PointValuePair;
import org.apache.commons.math3.optim.nonlinear.scalar.GoalType;
import org.apache.commons.math3.optim.SimpleBounds;
import org.apache.commons.math3.analysis.*;
import org.apache.commons.math3.optim.InitialGuess;
import org.apache.commons.math3.optim.MaxEval;
import org.apache.commons.math3.optim.MaxIter;

import org.ecocean.grid.optimization.*;

import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.SimplexOptimizer;
import org.datanucleus.enhancer.methods.WriteObject;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.BOBYQAOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer;
import org.apache.commons.math3.optim.nonlinear.scalar.gradient.NonLinearConjugateGradientOptimizer.Formula;
import org.apache.commons.math3.optim.nonlinear.scalar.noderiv.NelderMeadSimplex;
import org.apache.commons.math3.optim.nonlinear.scalar.MultivariateFunctionMappingAdapter;
import org.apache.commons.math3.optim.nonlinear.scalar.ObjectiveFunction;

public class GrothParameterOptimizer {

    //Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C}   
    double[] defaults = new double[] {0.1, 50.0, 0.9999, 10.0, 0.99};
    // pretty sure this is an array of the different steps for your variables to change by when optimized? 
    double[] steps = new double[] {0.01, 1.0, 0.001, 0.1, 0.01};

    double[] lastResults = new double[5];

    double[] upperBounds = new double[] {0.15, 50.0, 0.9999, 30.0, 0.999};
    double[] lowerBounds = new double[] {0.0005, 5.0, 0.85, 5.0, 0.9};

    //This will magnify and rescale variable inputs and guess by multiplication/division if necessary 
    double[] parameterScaling = new double[] {1.0, 1.0, 1.0, 1.0, 1.0};

    GoalType goal = GoalType.MAXIMIZE;
    GrothAnalysis ga = new GrothAnalysis();
    
    // population * iterations = evaluatons
    int maxIter = 1000;
    int maxEval = 1000;

    // Should be: n+2 <= m <= (1/2)*((n+1)(n+2)), so for this purpose 7-21 points
    int interpolationPoints = 7; //needed for BOBYQOptimizaer

    public void setParameterScaling(double[] scales) {
        // i hate that I need to feed this to both objects.. sloppy. sry yall
        ga.setParameterScales(scales);
        this.parameterScaling = scales;

        System.out.println("-------->  Was parameter scaling set? :"+Arrays.toString(parameterScaling));
    }

    public void setInitialGuess(double[] guess) {
        double[] scaledWeights = new double[5];
        for (int i=0;i<5;i++) {
            scaledWeights[i] = (guess[i] / parameterScaling[i]);
        }
        this.defaults = scaledWeights;
    }

    public void setGoalTypeAsMax() {
        goal = GoalType.MAXIMIZE;
    }

    public void setGoalTypeAsMin() {
        goal = GoalType.MINIMIZE;
    }

    public void setUpperBounds(double[] newBounds) {
        double[] scaledBounds = new double[5];
        for (int i=0;i<5;i++) {
            scaledBounds[i] = (newBounds[i] / parameterScaling[i]);
        }
        this.upperBounds = scaledBounds;
        System.out.println("Scaled upperBounds: "+Arrays.toString(upperBounds));
    }

    public void setLowerBounds(double[] newBounds) {
        double[] scaledBounds = new double[5];
        for (int i=0;i<5;i++) {
            scaledBounds[i] = (newBounds[i] / parameterScaling[i]);
        }
        this.lowerBounds = scaledBounds;
        System.out.println("Scaled lowerBounds: "+Arrays.toString(lowerBounds));
    }

    public void setMaxIter(int i) {
        this.maxIter = i;
    }

    public void setMaxEval(int i) {
        this.maxEval = i;
    }

    public GrothAnalysis getGrothAnalysis() {
        return ga;
    }

    public void setBOBYQInterpolationPoints(int pts) {
        this.interpolationPoints = pts;
    }

    public String getScoresAsJsonString() {

        JSONObject ob = new JSONObject();

        ArrayList<Double> matches = ga.getMatchScores();
        JSONArray matchArr = new JSONArray(matches);

        ArrayList<Double> nonMatches = ga.getNonMatchScores();
        JSONArray nonMatchArr = new JSONArray(nonMatches);

        ob.put("matches", matchArr);
        ob.put("nonMatches", nonMatchArr);

        return ob.toString();
    }

    public double[] doOptimize() {
        try {

            //final ConvergenceChecker<PointValuePair> cchecker = new SimpleValueChecker(1e-10, 1e-10);
            //SimplexOptimizer optimizer = new SimplexOptimizer(cchecker);

            BOBYQAOptimizer optimizer = new BOBYQAOptimizer(interpolationPoints);

            // bunch of song and dance to format the SimplexOptimizer function and made it bounded 
            //MultivariateFunction mf = (MultivariateFunction) ga;
            //MultivariateFunctionMappingAdapter mfma = new MultivariateFunctionMappingAdapter(ga, lowerBounds, upperBounds);

            ObjectiveFunction of = new ObjectiveFunction(ga); 

            // these are your opts.. different implementations of the OptimizationData interface 
            // it's pretty difficult to acertain what the different optimizers want cuz they take as many as you like even if they do nothing

            InitialGuess ig = new InitialGuess(defaults);

            SimpleBounds sb = new SimpleBounds(lowerBounds, upperBounds);
            MaxEval me = new MaxEval(maxEval);
            MaxIter mi = new MaxIter(maxIter);

            //NelderMeadSimplex nms = new NelderMeadSimplex(steps);

            System.out.println("-of: "+of+"  -goal: "+goal+"  -mi: "+mi+"  -me: "+me);

            PointValuePair result = optimizer.optimize(of, goal, me, sb, mi, ig);
            double[] resultArr = descaledResult(result.getPoint());
            lastResults = descaledResult(result.getPoint());
            System.out.println("------> Here are the default values: "+Arrays.toString(defaults));

            System.out.println("------> This also is the result of optimization: "+Arrays.toString(resultArr));

        } catch (Exception e) {
            e.printStackTrace();
        }
        logStats();
        return lastResults;
    }

    public double[] descaledResult(double[] scaledResult) {
        double[] descaledResult = new double[5];
        for (int i=0;i<5;i++) {
            descaledResult[i] = (scaledResult[i]*parameterScaling[i]);
        }
        return descaledResult;
    }

    public void logStats() {

        Double lowest = -1.0;
        Double highest = -1.0;
        Double zeros = 0.0;

        for (Double score : ga.getMatchScores()) {
            if (score>highest) highest = score;
            if (lowest==-1.0||score<lowest) lowest = score;
            if (score<1) zeros +=1;  
        }

        Double lowestNonMatch = -1.0;
        Double highestNonMatch = -1.0;
        Double falsePositives = 0.0;
        for (Double score : ga.getNonMatchScores()) {
            if (score>highestNonMatch) highestNonMatch = score;
            if (lowestNonMatch==-1.0||score<lowestNonMatch) lowestNonMatch = score;
            if (score>100) falsePositives +=1;
        }

        System.out.println("------------------------------------------------------------------------------------------------");

        System.out.println("Parameter order: {epsilon, R, sizeLim, maxTriangleRotation, C}");

        System.out.println("MATCH scores: LOWEST="+lowest+" HIGHEST="+highest);

        System.out.println("NON-MATCH scores: LOWEST="+lowestNonMatch+" HIGHEST="+highestNonMatch);

        System.out.println("Number of MATCHES with score under 1.0: "+zeros);

        System.out.println("Number of NON-MATCHES with score over 100: "+falsePositives);

        System.out.println("If WEIGHTS are applied, these numbers may be skewed.");

        System.out.println("------------------------------------------------------------------------------------------------");
    }

    public void writeResultsToFile() {
        writeResultsToFile(defaults, 250);
    }

    public void writeResultsToFile(double[] params) {
        writeResultsToFile(params, 250);
    }

    public void writeResultsToFile(int numPoints) {
        writeResultsToFile(defaults, numPoints);
    }

    public void writeResultsToFile(double[] params, int numPoints) {

        System.out.println("[INFO] Trying to export results...");

        try {
            ga.flush();
            ga.setNumComparisonsEach(numPoints);
            Double finalScore = ga.value(params);
        } catch (Exception e) {
            System.out.println("[WARN]: Could not get results for input to write to file.");
            e.printStackTrace();
        }

        BufferedWriter bw = null;
        try {
            Path dir = Paths.get("webapps/wildbook_data_dir/optimizerResults/");
            if (!Files.exists(dir)) {
                Files.createDirectories(dir);
            }
            String[] prefixes = new String[] {"epsilon:", "R:", "sizeLim:", "maxTriangleRotation:", "C:"};
            String filename = "params-";
            for (int i=0;i<lastResults.length;i++) {
                filename += prefixes[i]+String.valueOf(lastResults[i]).substring(0,5)+"-";
            }
            File f = new File("webapps/wildbook_data_dir/optimizerResults/", filename+".csv");
            f.getAbsolutePath();
            if (!f.exists()) {
                f.createNewFile();
            }
            System.out.println("Isfile? "+f.isFile()+"  IsDirectory? "+f.isDirectory()+" ABS Path: "+f.getAbsolutePath());
            bw = new BufferedWriter(new FileWriter(f));
            for (Double d : ga.getMatchScores()) {
                bw.write("M,"+String.valueOf(d));
                bw.newLine();
            }
            for (Double d : ga.getNonMatchScores()) {
                bw.write("N,"+String.valueOf(d));
                bw.newLine();
            }
            bw.close();
        } catch (IOException ioe) {
            System.out.println("[WARN]: IOException writing optimization results to file.");
            ioe.printStackTrace();
        }
        System.out.println("[SUCCESS] Wrote results for "+numPoints+" comparisons using "+Arrays.toString(params)); 
    }


}