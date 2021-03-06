package main.java.algorithm;

import com.yworks.yfiles.algorithms.GraphChecker;
import com.yworks.yfiles.graph.IGraph;
import com.yworks.yfiles.layout.YGraphAdapter;
import main.java.algorithm.embedding.GraphEmbedding;
import main.java.algorithm.typeDetermination.*;
import main.java.algorithm.exception.GraphConditionsException;
import main.java.algorithm.exception.LDrawingNotPossibleException;
import main.java.algorithm.utils.*;
import main.java.algorithm.utils.coordinates.XCoordinates;
import main.java.algorithm.utils.coordinates.YCoordinates;
import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.graph.MultiDirectedGraph;
import main.java.decomposition.hyperGraph.Vertex;
import main.java.decomposition.spqrTree.TCTree;
import main.java.decomposition.spqrTree.TCTreeNode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Set;

public class LDrawing {

    private static final Logger LOGGER = LoggerFactory.getLogger(LDrawing.class);

    private IGraph initialGraph;
    private MultiDirectedGraph convertedGraph;
    private DirectedEdge backEdge;
    private Vertex source;
    private Vertex target;
    private TCTree<DirectedEdge, Vertex> tcTree;


    public void lDrawing(IGraph graph) throws GraphConditionsException, LDrawingNotPossibleException {


        LOGGER.debug("Amount of nodes: " + graph.getNodes().size());
        LOGGER.debug("Amount of edges: " + graph.getEdges().size());


        int numberTimeSamples = 100;
        double[] firstTimes = new double[numberTimeSamples];
        double[] secondTimes = new double[numberTimeSamples];
        double[] thirdTimes = new double[numberTimeSamples];
        double[] fourthTimes = new double[numberTimeSamples];
        double[] fifthTimes = new double[numberTimeSamples];

        for(int i = 0; i < numberTimeSamples+1; i++) {
            TimeMeasurement.reset();

            TimeMeasurement.startMeasure(this);
            this.initialGraph = graph;
            this.checkIfLDrawingPossible(initialGraph);
            this.convertedGraph = GraphConverter.createGraphConverter(graph).getConvertedGraph();
            Augmentation.createAugmentation(convertedGraph);
            calculateSourceAndTarget();
            firstTimes[i % numberTimeSamples] = TimeMeasurement.endMeasure(this);

            TimeMeasurement.startMeasure(this);
            this.augmentGraphWithNewSource();
            this.tcTree = new TCTree<>(convertedGraph, backEdge);
            AbstractPertinentGraph.tcTree = tcTree;
            AbstractPertinentGraph.pertinentGraphsOfTCTreeNodes = new HashMap<>();
            NodesPostOrder.createNodesPostOrder(tcTree);
            secondTimes[i % numberTimeSamples] = TimeMeasurement.endMeasure(this);

            TimeMeasurement.startMeasure(this);
            for (TCTreeNode<DirectedEdge, Vertex> node : NodesPostOrder.getNodesPostOrder()) {
                switch (node.getType()) {
                    case TYPE_Q:
                        new QPertinentGraph(node);
                        break;
                    case TYPE_S:
                        new SPertinentGraph(node);
                        break;
                    case TYPE_P:
                        new PPertinentGraph(node);
                        break;
                    case TYPE_R:
                        new RPertinentGraph(node);
                        break;
                }
            }
            thirdTimes[i % numberTimeSamples] = TimeMeasurement.endMeasure(this);

            TimeMeasurement.startMeasure(this);
            GraphEmbedding.createEmbedding(convertedGraph);
            AbstractPertinentGraph.pertinentGraphsOfTCTreeNodes.get(tcTree.getRoot()).reconstructOutgoingEmbedding();
            AbstractPertinentGraph.pertinentGraphsOfTCTreeNodes.get(tcTree.getRoot()).reconstructIncomingEmbedding();
            fourthTimes[i % numberTimeSamples] = TimeMeasurement.endMeasure(this);

            TimeMeasurement.startMeasure(this);
            STOrdering.createSTOrdering(convertedGraph, source);
            Augmentation.getAugmentation().removeAugmentedParts();
            XCoordinates.createCoordinates(convertedGraph);
            YCoordinates.createCoordinates(convertedGraph);
            fifthTimes[i % numberTimeSamples] = TimeMeasurement.endMeasure(this);
        }

        double firstTime = 0;
        double secondTime = 0;
        double thirdTime = 0;
        double fourthTime = 0;
        double fifthTime = 0;

        for(int i = 0; i < numberTimeSamples; i++){
            firstTime += firstTimes[i];
            secondTime += secondTimes[i];
            thirdTime += thirdTimes[i];
            fourthTime += fourthTimes[i];
            fifthTime += fifthTimes[i];
        }

        firstTime /= (double) numberTimeSamples;
        secondTime /= (double) numberTimeSamples;
        thirdTime /= (double) numberTimeSamples;
        fourthTime /= (double) numberTimeSamples;
        fifthTime /= (double) numberTimeSamples;

        LOGGER.debug("1. " + firstTime);
        LOGGER.debug("2. " + secondTime);
        LOGGER.debug("3. " + thirdTime);
        LOGGER.debug("4. " + fourthTime);
        LOGGER.debug("5. " + fifthTime);
    }


    public void checkIfLDrawingPossible(IGraph graph) throws GraphConditionsException {

        YGraphAdapter graphAdapter = new YGraphAdapter(graph);
        if(initialGraph.getNodes().size() < 2)
            throw new GraphConditionsException("The input graph contains less than two nodes. Please add nodes to the graph until it contains at least two nodes.");
        if(initialGraph.getEdges().size() < 1)
            throw new GraphConditionsException("The input graph contains no edges. Please add edges to the graph until it contains at least one edges.");
        if(!GraphChecker.isConnected(graphAdapter.getYGraph()))
            throw new GraphConditionsException("The input graph is not connected. Please connect all nodes of the graph to a biconnected graph.");
        if(GraphChecker.isCyclic(graphAdapter.getYGraph()))
            throw new GraphConditionsException("The input graph is cyclic. Please remove or change edges to make the graph acyclic.");
        if(!GraphChecker.isPlanar(graphAdapter.getYGraph()))
            throw new GraphConditionsException("The input graph is not planar. Please make sure the graph admits a planar embedding.");
    }


    private void augmentGraphWithNewSource(){

        Vertex newSource = convertedGraph.addVertex(new Vertex("s'"));
        DirectedEdge augmentedE1 = convertedGraph.addEdge(newSource, source);
        DirectedEdge augmentedE2 = convertedGraph.addEdge(newSource, target);
        DirectedEdge augmentedE3 = backEdge = convertedGraph.addEdge(newSource, target);
        source = newSource;

        Augmentation.getAugmentation().setAugmentedSource(newSource);
        Augmentation.getAugmentation().getAugmentedEdges().add(augmentedE1);
        Augmentation.getAugmentation().getAugmentedEdges().add(augmentedE2);
        Augmentation.getAugmentation().getAugmentedEdges().add(augmentedE3);
    }


    private void calculateSourceAndTarget() throws GraphConditionsException {

        Set<Vertex> sources = convertedGraph.vertexSet();
        Set<Vertex> targets = convertedGraph.vertexSet();

        for(DirectedEdge edge : convertedGraph.getEdges()){
            sources.remove(edge.getTarget());
            targets.remove(edge.getSource());
        }

        if(sources.size() != 1) {
            throw new GraphConditionsException("The input graph contains more than one source. Please add edges to the graph until it contains exactly one source.");
        }
        if(targets.size() != 1) {
            throw new GraphConditionsException("The input graph contains more than one target. Please add edges to the graph until it contains exactly one target.");
        }
        this.source = sources.iterator().next();
        this.target = targets.iterator().next();

    }
}