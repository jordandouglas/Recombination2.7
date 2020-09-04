package recombination.likelihood;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

import beast.core.Logger;
import recombination.network.BreakPoints;
import recombination.network.RecombinationNetworkEdge;
import recombination.network.RecombinationNetworkNode;

/**
 * standard likelihood core, uses no caching *
 */
public class BeerNetworkLikelihoodCore extends NetworkLikelihoodCore {
    protected int nrOfStates;
    protected int nrOfNodes;
    protected int nrOfPatterns;
    protected int partialsSize;
    protected int matrixSize;
    protected int nrOfMatrices;

    protected boolean integrateCategories;


    protected int[] currentMatrixIndex;
    protected int[] storedMatrixIndex;
    protected int[] currentPartialsIndex;
    protected int[] storedPartialsIndex;
    
    public HashMap<Double, double[]> partials;
    protected HashMap<Double, double[]> storedPartials;

    protected HashMap<Double, double[]> matrix;
    protected HashMap<Double, double[]> storedMatrix;
    
    public HashMap<Integer, int[]> states;

    protected boolean useScaling = false;

    protected double[][][] scalingFactors;

    private double scalingThreshold = 1.0E-100;
    double SCALE = 2;

    public BeerNetworkLikelihoodCore(int nrOfStates) {
        this.nrOfStates = nrOfStates;
        matrix = new HashMap<>();
        partials = new HashMap<>();
        states = new HashMap<>();
        
    } // c'tor


    /**
     * Calculates partial likelihoods at a node when both children have exactly known states (e.g. for leaves).
     */
    protected void calculateStatesStatesPruning(RecombinationNetworkEdge edge1, RecombinationNetworkEdge edge2, RecombinationNetworkNode node,
    		BreakPoints computeFor, BreakPoints compute1, BreakPoints compute2) {
        
    	
        // compute the breakpoints that are on both edges or only on either edge
        BreakPoints joint = compute1.copy();
        BreakPoints e1 = compute1.copy();
        BreakPoints e2 = compute2.copy();
        
        joint.and(compute2);
        e1.andNot(compute2);
        e2.andNot(compute1);      
        
        double[] mat1 = this.matrix.get(edge1.getLength());
        double[] mat2 = this.matrix.get(edge2.getLength());
        
        double[] partials_parent = this.partials.get(node.getHeight());
        int[] states_child1 = this.states.get(edge1.childNode.getTaxonIndex());
        int[] states_child2 = this.states.get(edge2.childNode.getTaxonIndex());



    	for (int m = 0; m < joint.size(); m++) {
    		for (int l = 0; l < nrOfMatrices; l++) {
        		int v = l*nrOfPatterns + joint.getRange(m).from*nrOfStates;

	            for (int k = joint.getRange(m).from; k <= joint.getRange(m).to; k++) {
	                int state1 = states_child1[k];
	                int state2 = states_child2[k];
	
	                int w = l * matrixSize;
	
	                if (state1 < nrOfStates && state2 < nrOfStates) {
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                    	partials_parent[v] = mat1[w + state1] * mat2[w + state2];
	                    	
	
	                        v++;
	                        w += nrOfStates;
	                    }
	
	                } else if (state1 < nrOfStates) {
	                    // child 2 has a gap or unknown state so treat it as unknown
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                    	partials_parent[v] = mat1[w + state1];
	
	                        v++;
	                        w += nrOfStates;
	                    }
	                } else if (state2 < nrOfStates) {
	                    // child 2 has a gap or unknown state so treat it as unknown
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                    	partials_parent[v] = mat2[w + state2];
	
	                        v++;
	                        w += nrOfStates;
	                    }
	                } else {
	                    // both children have a gap or unknown state so set partials to 1
	
	                    for (int j = 0; j < nrOfStates; j++) {
	                    	partials_parent[v] = 1.0;
	                        v++;
	                    }
	                }
	            }
    		}
        }
    	
    	calculateStatesPruning(e1, edge1, node);
    	calculateStatesPruning(e2, edge2, node);    	
    }
    
    /**
     * Calculates partial likelihoods at a node when one child has states and one has partials.
     */
    protected void calculateStatesPartialsPruning(RecombinationNetworkEdge edge1, RecombinationNetworkEdge edge2, RecombinationNetworkNode node,
    		BreakPoints computeFor, BreakPoints compute1, BreakPoints compute2) { 
        double sum, tmp;
        
        // compute the breakpoints that are on both edges or only on either edge
        BreakPoints joint = compute1.copy();
        BreakPoints e1 = compute1.copy();
        BreakPoints e2 = compute2.copy();
        
        joint.and(compute2);
        e1.andNot(compute2);
        e2.andNot(compute1); 
        
        double[] mat1 = this.matrix.get(edge1.getLength());
        double[] mat2 = this.matrix.get(edge2.getLength());
        
        double[] partials_parent = this.partials.get(node.getHeight());
        int[] states_child1 = this.states.get(edge1.childNode.getTaxonIndex());
        double[] partials_child2 = this.partials.get(edge2.childNode.getHeight());

        
    	for (int m = 0; m < joint.size(); m++) {
    		for (int l = 0; l < nrOfMatrices; l++) {
        		int v = l*nrOfPatterns + joint.getRange(m).from*nrOfStates;
        		int u = v;

	            for (int k = joint.getRange(m).from; k <= joint.getRange(m).to; k++) {
	
	                int state1 = states_child1[k];
	
	                int w = l * matrixSize;
	
	                if (state1 < nrOfStates) {
	
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                        tmp = mat1[w + state1];
	
	                        sum = 0.0;
	                        for (int j = 0; j < nrOfStates; j++) {
	                            sum += mat2[w] * partials_child2[v + j];
	                            w++;
	                        }
	                        partials_parent[u] = tmp * sum;
	                        u++;
	                    }
	
	                    v += nrOfStates;
	                } else {
	                    // Child 1 has a gap or unknown state so don't use it
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                        sum = 0.0;
	                        for (int j = 0; j < nrOfStates; j++) {
	                            sum += mat2[w] * partials_child2[v + j];
	                            w++;
	                        }
	                        partials_parent[u] = sum;
	                        u++;
	                    }
	
	                    v += nrOfStates;
	                }
	            }
	        }
    	}
    	
    	calculateStatesPruning(e1, edge1, node);
    	calculatePartialsPruning(e2, edge2, node);
    }

    /**
     * Calculates partial likelihoods at a node when both children have partials.
     */
    protected void calculatePartialsPartialsPruning(RecombinationNetworkEdge edge1, RecombinationNetworkEdge edge2, RecombinationNetworkNode node,
    		BreakPoints computeFor, BreakPoints compute1, BreakPoints compute2) {

    	double sum1, sum2;
    	
        // compute the breakpoints that are on both edges or only on either edge
        BreakPoints joint = compute1.copy();
        BreakPoints e1 = compute1.copy();
        BreakPoints e2 = compute2.copy();
        
        joint.and(compute2);
        e1.andNot(compute2);
        e2.andNot(compute1);
        
        double[] mat1 = this.matrix.get(edge1.getLength());
        double[] mat2 = this.matrix.get(edge2.getLength());
        
        double[] partials_parent = this.partials.get(node.getHeight());
        double[] partials_child1 = this.partials.get(edge1.childNode.getHeight());
        double[] partials_child2 = this.partials.get(edge2.childNode.getHeight());


                
    	for (int m = 0; m < joint.size(); m++) {
    		for (int l = 0; l < nrOfMatrices; l++) {
        		int v = l*nrOfPatterns + joint.getRange(m).from*nrOfStates;
        		int u = v;

	            for (int k = joint.getRange(m).from; k <= joint.getRange(m).to; k++) {
	                int w = l * matrixSize;
	
	                for (int i = 0; i < nrOfStates; i++) {
	
	                    sum1 = sum2 = 0.0;
	
	                    for (int j = 0; j < nrOfStates; j++) {
	                        sum1 += mat1[w] * partials_child1[v + j];
	                        sum2 += mat2[w] * partials_child2[v + j];	                        
	
	                        w++;
	                    }
	                    
	                    partials_parent[u] = sum1 * sum2;
	                    u++;
	                }
	                v += nrOfStates;
	            }
	        }
    	}
    	
    	
    	calculatePartialsPruning(e1, edge1, node);
    	calculatePartialsPruning(e2, edge2, node);
    }

    /**
     * Integrates partials across categories.
     *
     * @param inPartials  the array of partials to be integrated
     * @param proportions the proportions of sites in each category
     * @param outPartials an array into which the partials will go
     */
    @Override
	protected void calculateIntegratePartials(RecombinationNetworkNode node, double[] proportions, double[] outPartials) {

        int u = 0;
        int v = 0;
        double[] inPartials = partials.get(node.getHeight());
        for (int k = 0; k < nrOfPatterns; k++) {

            for (int i = 0; i < nrOfStates; i++) {

                outPartials[u] = inPartials[v] * proportions[0];
                u++;
                v++;
            }
        }


        for (int l = 1; l < nrOfMatrices; l++) {
            u = 0;

            for (int k = 0; k < nrOfPatterns; k++) {

                for (int i = 0; i < nrOfStates; i++) {

                    outPartials[u] += inPartials[v] * proportions[l];
                    u++;
                    v++;
                }
            }
        }
    }

    /**
     * Calculates partial likelihoods at a node when both children have exactly known states (e.g. for leaves).
     */
    protected void calculateStatesPruning(BreakPoints carries, RecombinationNetworkEdge edge, RecombinationNetworkNode node) {
    	
        double[] mat = this.matrix.get(edge.getLength());
        
        double[] partials_parent = this.partials.get(node.getHeight());
        int[] states_child = states.get(edge.childNode.getTaxonIndex());


    	for (int m = 0; m < carries.size(); m++) {
    		for (int l = 0; l < nrOfMatrices; l++) {
        		int v = l*nrOfPatterns + carries.getRange(m).from*nrOfStates;
	            for (int k = carries.getRange(m).from; k <= carries.getRange(m).to; k++) {
	            	
	                int state1 = states_child[k];
	                if (state1 < nrOfStates) {
	                	int w = l * matrixSize;
	
	                    for (int i = 0; i < nrOfStates; i++) {
	
	                    	partials_parent[v] = mat[w + state1];
	                        v++;
	                        w += nrOfStates;
	                    }
	
	                } else {
	                    // both children have a gap or unknown state so set partials to 1
	
	                    for (int j = 0; j < nrOfStates; j++) {
	                    	partials_parent[v] = 1.0;
	                        v++;
	                    }
	                }
	            }
        	}
        }
    }

    /**
     * Calculates partial likelihoods at a node when both children have partials.
     */
    protected void calculatePartialsPruning(BreakPoints carries, RecombinationNetworkEdge edge, RecombinationNetworkNode node) {

    	double sum1;

        double[] mat = this.matrix.get(edge.getLength());
        double[] partials_parent = this.partials.get(node.getHeight());
        double[] partials_child = this.partials.get(edge.childNode.getHeight());

    	for (int m = 0; m < carries.size(); m++) {
    		for (int l = 0; l < nrOfMatrices; l++) {
        		int v = l*nrOfPatterns + carries.getRange(m).from*nrOfStates;
        		int u = v;
	            for (int k = carries.getRange(m).from; k <= carries.getRange(m).to; k++) {

	                int w = l * matrixSize;
	
	                for (int i = 0; i < nrOfStates; i++) {
	
	                    sum1 =  0.0;
	
	                    for (int j = 0; j < nrOfStates; j++) {
	                        sum1 += mat[w] * partials_child[v + j];	
	                        w++;
	                    }
	
	                    partials_parent[u] = sum1;
	                    u++;
	                }
	                v += nrOfStates;
	            }
            }
        }
    	partials.replace(node.getHeight(), partials_parent);
    }
   
    /**
     * Calculates pattern log likelihoods at a node.
     *
     * @param partials          the partials used to calculate the likelihoods
     * @param frequencies       an array of state frequencies
     * @param outLogLikelihoods an array into which the likelihoods will go
     */
    @Override
	public void calculateLogLikelihoods(double[] partials, double[] frequencies, double[] outLogLikelihoods) {
        int v = 0;
        for (int k = 0; k < nrOfPatterns; k++) {

            double sum = 0.0;
            for (int i = 0; i < nrOfStates; i++) {

                sum += frequencies[i] * partials[v];
                v++;
            }
            outLogLikelihoods[k] = Math.log(sum) + getLogScalingFactor(k);
        }
    }


    /**
     * initializes partial likelihood arrays.
     *
     * @param nodeCount           the number of nodes in the tree
     * @param patternCount        the number of patterns
     * @param matrixCount         the number of matrices (i.e., number of categories)
     * @param integrateCategories whether sites are being integrated over all matrices
     */
    @Override
	public void initialize(int patternCount, int matrixCount, boolean integrateCategories, boolean useAmbiguities) {
        this.nrOfPatterns = patternCount;
        this.nrOfMatrices = matrixCount;
        this.integrateCategories = integrateCategories;
        matrixSize = nrOfStates * nrOfStates;
        
        
    }

    /**
     * cleans up and deallocates arrays.
     */
    @Override
	public void finalize() throws java.lang.Throwable {
        nrOfPatterns = 0;
        scalingFactors = null;
    }

    @Override
    public void setUseScaling(double scale) {
        useScaling = (scale != 1.0);

        if (useScaling) {
            scalingFactors = new double[2][nrOfNodes][nrOfPatterns];
        }
    }


    /**
     * Calculates partial likelihoods at a node.
     *
     * @param nodeIndex1 the 'child 1' node
     * @param nodeIndex2 the 'child 2' node
     * @param nodeIndex3 the 'parent' node
     */
    @Override
	public void calculatePartials(RecombinationNetworkEdge edge1, RecombinationNetworkEdge edge2, RecombinationNetworkNode node, BreakPoints computeFor, BreakPoints compute1, BreakPoints compute2) {
        if (states.containsKey(edge1.childNode.getTaxonIndex())) {
            if (states.containsKey(edge2.childNode.getTaxonIndex())) {
                calculateStatesStatesPruning(edge1,edge2,node,computeFor,compute1,compute2);
            } else {
                calculateStatesPartialsPruning(edge1,edge2,node,computeFor,compute1,compute2);
            }
        } else {
            if (states.containsKey(edge2.childNode.getTaxonIndex())) {
                calculateStatesPartialsPruning(edge2,edge1,node,computeFor,compute2,compute1);
            } else {
                calculatePartialsPartialsPruning(edge1,edge2,node,computeFor,compute1,compute2);
            }
        }
        
//        System.out.println(node.getHeight() + " " + computeFor);
//        System.out.println(Arrays.toString(partials.get(node.getHeight())));
//        System.out.println(states.keySet());
//        System.out.println(edge1.childNode);
//        System.out.println(edge1.childNode.isLeaf());

        
//        System.out.println(matrix.get(edge1)[0]);
        
//        System.out.println(node.getHeight() + " " + matrix.size());
//        System.out.println();
//
//        for (RecombinationNetworkEdge e : matrix.keySet())
//        	System.out.println(matrix.get(e)[0]);
        

//        if (useScaling) {
//            scalePartials(edge3);
//        }
//
//
//        int k =0;
//        for (int i = 0; i < nrOfPatterns; i++) {
//            double f = 0.0;
//
//            for (int j = 0; j < nrOfStates; j++) {
//                f += node.partials[k];
//                k++;
//            }
//            if (f == 0.0) {
//                Logger.getLogger("error").severe("A partial likelihood (node index = " + node.getHeight() + ", pattern = "+ i +") is zero for all states.");
//            }
//        }
    }
    
    @Override
	public void calculatePartialsRecombination(RecombinationNetworkEdge edge, RecombinationNetworkNode node, BreakPoints compute1) {
        if (states.containsKey(edge.childNode.getTaxonIndex())) {
        	calculateStatesPruning(compute1, edge, node);
        } else {
        	calculatePartialsPruning(compute1, edge, node);
        }
//        System.exit(0);

//        if (useScaling) {
//            scalePartials(edge3);
//        }
//
//
//        int k =0;
//        for (int i = 0; i < patternCount; i++) {
//            double f = 0.0;
//
//            for (int j = 0; j < stateCount; j++) {
//                f += partials[currentPartialsIndices[nodeIndex3]][nodeIndex3][k];
//                k++;
//            }
//            if (f == 0.0) {
//                Logger.getLogger("error").severe("A partial likelihood (node index = " + nodeIndex3 + ", pattern = "+ i +") is zero for all states.");
//            }
//        }
    }

    @Override
	public void integratePartials(RecombinationNetworkEdge edge, double[] proportions, double[] outPartials) {
        calculateIntegratePartials(edge.childNode, proportions, outPartials);
    }


    /**
     * Scale the partials at a given node. This uses a scaling suggested by Ziheng Yang in
     * Yang (2000) J. Mol. Evol. 51: 423-432
     * <p/>
     * This function looks over the partial likelihoods for each state at each pattern
     * and finds the largest. If this is less than the scalingThreshold (currently set
     * to 1E-40) then it rescales the partials for that pattern by dividing by this number
     * (i.e., normalizing to between 0, 1). It then stores the log of this scaling.
     * This is called for every internal node after the partials are calculated so provides
     * most of the performance hit. Ziheng suggests only doing this on a proportion of nodes
     * but this sounded like a headache to organize (and he doesn't use the threshold idea
     * which improves the performance quite a bit).
     *
     * @param nodeIndex
     */
    protected void scalePartials(int nodeIndex) {
//        int v = 0;
//    	double [] partials = m_fPartials[m_iCurrentPartialsIndices[nodeIndex]][nodeIndex];
//        for (int i = 0; i < m_nPatternCount; i++) {
//            for (int k = 0; k < m_nMatrixCount; k++) {
//                for (int j = 0; j < m_nStateCount; j++) {
//                	partials[v] *= SCALE;
//                	v++;
//                }
//            }
//        }
        int u = 0;

        for (int i = 0; i < nrOfPatterns; i++) {

            double scaleFactor = 0.0;
            int v = u;
            for (int k = 0; k < nrOfMatrices; k++) {
                for (int j = 0; j < nrOfStates; j++) {
                    if (partials[currentPartialsIndex[nodeIndex]][nodeIndex][v] > scaleFactor) {
                        scaleFactor = partials[currentPartialsIndex[nodeIndex]][nodeIndex][v];
                    }
                    v++;
                }
                v += (nrOfPatterns - 1) * nrOfStates;
            }

            if (scaleFactor < scalingThreshold) {

                v = u;
                for (int k = 0; k < nrOfMatrices; k++) {
                    for (int j = 0; j < nrOfStates; j++) {
                        partials[currentPartialsIndex[nodeIndex]][nodeIndex][v] /= scaleFactor;
                        v++;
                    }
                    v += (nrOfPatterns - 1) * nrOfStates;
                }
                scalingFactors[currentPartialsIndex[nodeIndex]][nodeIndex][i] = Math.log(scaleFactor);

            } else {
                scalingFactors[currentPartialsIndex[nodeIndex]][nodeIndex][i] = 0.0;
            }
            u += nrOfStates;


        }
    }

    
    
    /**
     * This function returns the scaling factor for that pattern by summing over
     * the log scalings used at each node. If scaling is off then this just returns
     * a 0.
     *
     * @return the log scaling factor
     */
    @Override
	public double getLogScalingFactor(int patternIndex_) {
//    	if (m_bUseScaling) {
//    		return -(m_nNodeCount/2) * Math.log(SCALE);
//    	} else {
//    		return 0;
//    	}        
        double logScalingFactor = 0.0;
        if (useScaling) {
            for (int i = 0; i < nrOfNodes; i++) {
                logScalingFactor += scalingFactors[currentPartialsIndex[i]][i][patternIndex_];
            }
        }
        return logScalingFactor;
    }


    /**
    /**
     * Store current state
     */
    @Override
    public void restore() {
        // Rather than copying the stored stuff back, just swap the pointers...
        HashMap<Double, double[]> tmp1 = matrix;
        matrix = storedMatrix;
        storedMatrix = tmp1;

        HashMap<Double, double[]> tmp2 = partials;
        partials = storedPartials;
        storedPartials = tmp2;
    }

    @Override
	public void unstore() {
//        System.arraycopy(storedMatrixIndex, 0, currentMatrixIndex, 0, nrOfNodes);
//        System.arraycopy(storedPartialsIndex, 0, currentPartialsIndex, 0, nrOfNodes);
    }

    /**
     * Restore the stored state
     */
    @Override
    public void store() {
    	storedMatrix = new HashMap<>();
    	for (Double e : matrix.keySet()) {
    		double[] oldmat = matrix.get(e);
            double[] newmat = new double[oldmat.length];
    		System.arraycopy(oldmat, 0, newmat, 0, oldmat.length);
    		storedMatrix.put(e, newmat);
    	}
    	
    	storedPartials = new HashMap<>();
    	for (Double n : partials.keySet()) {
    		double[] oldp = partials.get(n);
            double[] newp = new double[oldp.length];
    		System.arraycopy(oldp, 0, newp, 0, oldp.length);
    		storedPartials.put(n, newp);
    	}
//    	
//        System.arraycopy(currentMatrixIndex, 0, storedMatrixIndex, 0, nrOfNodes);
//        System.arraycopy(currentPartialsIndex, 0, storedPartialsIndex, 0, nrOfNodes);
    }


	@Override
	public void setEdgeMatrix(RecombinationNetworkEdge edge, int i, double[] matrix) {
        double[] newmat = new double[matrix.length];
		System.arraycopy(matrix, 0, newmat, 0, matrix.length);
		if (this.matrix.containsKey(edge)) {
			this.matrix.replace(edge.getLength(), newmat);
		}else {
			this.matrix.put(edge.getLength(), newmat);
		}
	}
	
	
	
	@Override
    public void initPartials(RecombinationNetworkNode node, int length) {
		if (!partials.containsKey(node.getHeight())) {
			partials.put(node.getHeight(), new double[length]);
		}
	}
	
	@Override
	public void setStates(RecombinationNetworkNode node, int[] states) {
        int[] newstates = new int[states.length];
		System.arraycopy(states, 0, newstates, 0, states.length);
		this.states.put(node.getTaxonIndex(), newstates);


	}


	@Override
	public void cleanMatrix(List<RecombinationNetworkEdge> edges) {
		matrix.clear();
//		List<Double> remove = new ArrayList<>();
//		for (Double e : matrix.keySet()) {
//			if (!edges.contains(e))
//				remove.add(e);
//		}
//			matrix.remove(e);

	}


	@Override
	public void cleanPartials(List<RecombinationNetworkNode> nodes) {
		partials.clear();
//		List<RecombinationNetworkNode> remove = new ArrayList<>();
//		for ( n : partials.keySet()) {
//			if (!nodes.contains(n))
//				remove.add(n);
//		}
//		for (RecombinationNetworkNode n : remove)
//			partials.remove(n);

	}

} // class BeerLikelihoodCore
