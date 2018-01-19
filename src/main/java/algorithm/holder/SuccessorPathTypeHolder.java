package main.java.algorithm.holder;

import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.hyperGraph.Vertex;
import main.java.decomposition.spqrTree.TCTreeNode;
import main.java.algorithm.typeDeterminationUtils.SuccessorPathType;

import java.util.HashMap;
import java.util.Map;

public class SuccessorPathTypeHolder {

    private Map<TCTreeNode<DirectedEdge, Vertex>, SuccessorPathType> nodeTypes;

    public SuccessorPathTypeHolder(){
        this.nodeTypes = new HashMap<>();
    }

    public Map<TCTreeNode<DirectedEdge, Vertex>, SuccessorPathType> getNodeTypes(){
        return nodeTypes;
    }
}