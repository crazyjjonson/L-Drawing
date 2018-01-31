package main.java.algorithm.successorPathTypeDetermination;

import main.java.algorithm.exception.LDrawingNotPossibleException;
import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.hyperGraph.Vertex;
import main.java.decomposition.spqrTree.TCTree;
import main.java.decomposition.spqrTree.TCTreeNode;

public interface ITypeDetermination {

    void determineType(TCTree<DirectedEdge, Vertex> tcTree, TCTreeNode<DirectedEdge, Vertex> tcTreeNode) throws LDrawingNotPossibleException;
}