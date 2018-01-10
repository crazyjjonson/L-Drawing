package main.java.typeDetermination.typeDeterminationUtils;

import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.hyperGraph.Vertex;
import main.java.decomposition.spqrTree.TCTree;
import main.java.decomposition.spqrTree.TCTreeNode;
import main.java.decomposition.spqrTree.TCTreeNodeType;
import main.java.typeDetermination.holder.HolderProvider;
import main.java.typeDetermination.holder.SuccessorPathTypeHolder;

public class QTypeDetermination implements ITypeDetermination{

    @Override
    public void determineType(TCTree<DirectedEdge, Vertex> tcTree, TCTreeNode<DirectedEdge, Vertex> tcTreeNode) {

        if(!tcTreeNode.getType().equals(TCTreeNodeType.TYPE_Q)) return;
        HolderProvider.getSuccessorPathTypeHolder().getNodeTypes().put(tcTreeNode, Type.TYPE_M);
    }
}
