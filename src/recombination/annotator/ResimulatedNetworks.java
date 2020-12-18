/*
 * Copyright (C) 2015 Tim Vaughan <tgvaughan@gmail.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package recombination.annotator;

import beast.core.parameter.RealParameter;
import beast.core.util.Log;
import beast.evolution.alignment.Taxon;
import beast.evolution.alignment.TaxonSet;
import beast.evolution.tree.TraitSet;
import beast.evolution.tree.coalescent.ConstantPopulation;
import beast.util.Randomizer;
import recombination.network.BreakPoints;
import recombination.network.RecombinationNetwork;
import recombination.network.RecombinationNetworkNode;
import recombination.simulator.SimulatedCoalescentRecombinationNetwork;
import recombination.statistics.DotConverter;

import javax.swing.*;
import javax.swing.border.EtchedBorder;
import java.awt.*;
import java.io.*;
import java.lang.reflect.InvocationTargetException;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * A rewrite of TreeAnnotator targeted at summarizing ACG logs
 * generated by bacter.
 * 
 * @author Tim Vaughan <tgvaughan@gmail.com>
 * @author Nicola Felix Müller <nicola.felix.mueller@gmail.com>
 */
public class ResimulatedNetworks extends RecombinationAnnotator {

    BufferedReader reader;


    private static class NetworkAnnotatorOptions {
        File inFile;
        File outFile = new File("summary.tree");
        File logFile;
        double burninPercentage = 10.0;
        BreakPoints breakPoints = new BreakPoints();
        boolean useDotFormat = false;

        @Override
        public String toString() {
            return "Active options:\n" +
                    "Input file: " + inFile + "\n" +
                    "Output file: " + outFile + "\n" +
                    "log file: " + logFile + "\n" +
                    "Burn-in percentage: " + burninPercentage + "\n" +
            		"Remove Loci for summary: " + breakPoints + "\n" +
            		"dot format output: " + useDotFormat + "\n";
       }
    }

    public ResimulatedNetworks(NetworkAnnotatorOptions options) throws IOException {
        // Display options:
        System.out.println(options + "\n");
        
        // Initialise reader
        RecombinationLogReader logReader = new RecombinationLogReader(options.inFile,
                options.burninPercentage);
        
        List<Double> popSize = getDoubleValue(options.logFile, "popSize");
        List<Double> recomb = getDoubleValue(options.logFile, "recombinationRate");
        
        int i=0;
        
        try (PrintStream ps = new PrintStream(options.outFile)) {	
             
        	ps.print(logReader.getPreamble());

        
	        for (RecombinationNetwork network : logReader ) {
	        	SimulatedCoalescentRecombinationNetwork sims = new SimulatedCoalescentRecombinationNetwork();
	        	
	            ConstantPopulation populationFunction = new ConstantPopulation();
	            populationFunction.initByName("popSize", new RealParameter("" + popSize.get(i)));
	            
	            
	            List<Taxon> taxa = new ArrayList<>();
	            List<String> dates = new ArrayList<>();
	
	            for (RecombinationNetworkNode node : network.getLeafNodes()) {
	                taxa.add(new Taxon(node.getTaxonLabel()));
	                dates.add(node.getTaxonLabel() +"="+node.getHeight());
	            }
	
	            TraitSet traitSet = new TraitSet();
	
	
	            traitSet.initByName("traitname", "date-backward",
	                    "taxa", new TaxonSet(taxa),
	                    "value", dates.stream()
	                            .collect(Collectors.joining(",")));
	
	
	
	            sims.initByName(
	                    "recombinationRate", new RealParameter(""+recomb.get(i)),
	                    "populationModel", populationFunction,
	                    "traitSet", traitSet,
	                    "conditionCoalescence", true,
	                    "totalLength", network.totalLength);
	
	        	i++;
	        	
	        	ps.println("tree STATE_" + i + " = " + sims.getExtendedNewickVerbose());	        	
	        }
	        
	    	String postamble = logReader.getPostamble();
	    	if (postamble.length() > 0)
	    		ps.println(postamble);
	    	else
	    		ps.println("End;");

        }


        System.out.println("\nDone!");
    }    
    
    private List<Double> getDoubleValue(File logFile, String paramName) throws IOException{
    	List<Double> param =  new ArrayList<>();
    	
        reader = new BufferedReader(new FileReader(logFile));
        String[] nextLine = reader.readLine().split("\\s+");
        int index = -1;
        for (int i = 0; i < nextLine.length; i++)
        	if (nextLine[i].equals(paramName))
        		index = i;
        
        System.out.println(Arrays.toString(nextLine));
        
        
        while(true) {
        	String line = reader.readLine();
        	
            if (line == null) {
                break;
            }

            nextLine = line.split("\\s+");            
            param.add(Double.parseDouble(nextLine[index]));
            

        }
     
    	return param;
    }

    public static String helpMessage =
            "ACGAnnotator - produces summaries of Bacter ACG log files.\n"
                    + "\n"
                    + "Usage: appstore ACGAnnotator [-help | [options] logFile [outputFile]\n"
                    + "\n"
                    + "Option                   Description\n"
                    + "--------------------------------------------------------------\n"
                    + "-help                    Display usage info.\n"
                    + "-positions {mean,median} Choose position summary method.\n"
                    + "                         (default mean)\n"
                    + "-burnin percentage       Choose _percentage_ of log to discard\n"
                    + "                         in order to remove burn-in period.\n"
                    + "                         (Default 10%)\n"
                    + "-threshold percentage    Choose minimum posterior probability\n"
                    + "                         for including conversion in summary.\n"
                    + "                         (Default 50%)\n"
                    + "-recordGeneFlow gfFile   Record posterior distribution of gene\n"
                    + "                         flow in given file.\n"
                    + "\n"
                    + "If no output file is specified, output is written to a file\n"
                    + "named 'summary.tree'.";

    /**
     * Print usage info and exit.
     */
    public static void printUsageAndExit() {
        System.out.println(helpMessage);
        System.exit(0);
    }

    /**
     * Display error, print usage and exit with error.
     */
    public static void printUsageAndError(String errMsg) {
        System.err.println(errMsg);
        System.err.println(helpMessage);
        System.exit(1);
    }

    /**
     * Retrieve ACGAnnotator options from command line.
     *
     * @param args command line arguments
     * @param options object to populate with options
     */
    public static void getCLIOptions(String[] args, NetworkAnnotatorOptions options) {
        int i=0;
        while (args[i].startsWith("-")) {
            switch(args[i]) {
                case "-help":
                    printUsageAndExit();
                    break;

                case "-burnin":
                    if (args.length<=i+1)
                        printUsageAndError("-burnin must be followed by a number (percent)");

                    try {
                        options.burninPercentage = Double.parseDouble(args[i+1]);
                    } catch (NumberFormatException e) {
                        printUsageAndError("Error parsing burnin percentage.");
                    }

                    if (options.burninPercentage<0 || options.burninPercentage>100) {
                        printUsageAndError("Burnin percentage must be >= 0 and < 100.");
                    }

                    i += 1;
                    break;

                case "-subsetRange":
                    if (args.length<=i+1) {
                        printUsageAndError("-subsetRange must be a range in the format of 0-100.");
                    }

                    try {
                    	String[] argarray = args[i + 1].split(",");
                    	List<Integer> bp_list = new ArrayList<>();
                    	for (int j = 0; j < argarray.length; j++) {
                    		String[] tmp = argarray[j].split("-");
                    		bp_list.add(Integer.parseInt(tmp[0]));
                    		bp_list.add(Integer.parseInt(tmp[1]));
                    	}
                		options.breakPoints.init(bp_list);
                    } catch (NumberFormatException e) {
                        printUsageAndError("removeSegments must be an array of integers separated by commas if more than one");
                     }

                    i += 1;
                    break;
                    
                case "-log":
                    if (args.length<=i+1) {
                        printUsageAndError("-log must be followed by a network file.");
                    }

                    try {
                		options.logFile = new File(args[i + 1]);
                    } catch (NumberFormatException e) {
                        printUsageAndError("removeSegments must be an array of integers separated by commas if more than one");
                     }

                    i += 1;
                    break;
                    
                case "-dotFormat":
                    if (args.length<=i+1) {
                        printUsageAndError("-dotFormat must be followed by true or false.");
                    }

                    try {
                		options.useDotFormat = Boolean.parseBoolean(args[i + 1]);
                    } catch (NumberFormatException e) {
                        printUsageAndError("dotFormat must be followed by true or false");
                     }

                    i += 1;
                    break;

                default:
                    printUsageAndError("Unrecognised command line option '" + args[i] + "'.");
            }

            i += 1;
        }

        if (i >= args.length)
            printUsageAndError("No input file specified.");
        else
            options.inFile = new File(args[i]);

        if (i+1<args.length)
            options.outFile = new File(args[i+1]);
    }

    /**
     * Main method for ACGAnnotator.  Sets up GUI if needed then
     * uses the ACGAnnotator constructor to actually perform the analysis.
     *
     * @param args command line arguments
     */
    public static void main(String[] args) {
    	NetworkAnnotatorOptions options = new NetworkAnnotatorOptions();
        getCLIOptions(args, options);
       
        // Run ACGAnnotator
        try {
            new ResimulatedNetworks(options);

        } catch (Exception e) {
            if (args.length == 0) {
                JOptionPane.showMessageDialog(null, e.getMessage(),
                        "Error", JOptionPane.ERROR_MESSAGE);
            } else {
                System.err.println("Error: " + e.getMessage());
                e.printStackTrace();
                System.err.println();
                System.err.println(helpMessage);
            }

            System.exit(1);
        }
    }
}