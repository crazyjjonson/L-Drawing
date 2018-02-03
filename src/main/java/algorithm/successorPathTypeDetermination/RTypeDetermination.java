package main.java.algorithm.successorPathTypeDetermination;

import main.java.printer.PrintColors;
import main.java.algorithm.exception.LDrawingNotPossibleException;
import main.java.algorithm.holder.HolderProvider;
import main.java.algorithm.types.FaceType;
import main.java.algorithm.types.SuccessorPathType;
import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.graph.MultiDirectedGraph;
import main.java.decomposition.hyperGraph.Vertex;
import main.java.decomposition.spqrTree.TCTree;
import main.java.decomposition.spqrTree.TCTreeNode;

import java.util.*;

public class RTypeDetermination implements ITypeDetermination {

    private TCTree<DirectedEdge, Vertex> tcTree;
    private TCTreeNode<DirectedEdge, Vertex> tcTreeNode;
    private MultiDirectedGraph skeletonGraph;
    private MultiDirectedGraph augmentedGraph;

    private List<List<DirectedEdge>> skeletonFaces;

    private Map<List<DirectedEdge>, Vertex> sourceOfFace;
    private Map<Vertex, List<List<DirectedEdge>>> facesOfSource;
    private Map<List<DirectedEdge>, Vertex> targetOfFace;

    private Map<List<DirectedEdge>, DirectedEdge> leftEdge;
    private Map<List<DirectedEdge>, DirectedEdge> rightEdge;

    private Map<DirectedEdge, TCTreeNode<DirectedEdge, Vertex>> virtualEdgeToTCTreeNode;

    private Map<List<DirectedEdge>, FaceType> faceTypes;
    private SuccessorPathType successorPathType = SuccessorPathType.TYPE_M;


    public void determineType(TCTree<DirectedEdge, Vertex> tcTree, TCTreeNode<DirectedEdge, Vertex> tcTreeNode) throws LDrawingNotPossibleException {
        System.out.println(PrintColors.ANSI_RED + "---------------------------");
        System.out.println(PrintColors.ANSI_RED + "RType Determination! Source Vertex is " + HolderProvider.getSourceTargetPertinentGraphsHolder().getSourceNode(tcTreeNode));



        this.tcTreeNode = tcTreeNode;
        this.tcTree = tcTree;
        this.skeletonGraph = convertSkeletonToGraph();
        this.augmentedGraph = HolderProvider.getAugmentationHolder().getAugmentedGraph();
        this.skeletonFaces = HolderProvider.getEmbeddingHolder().getFacesOfRNode(tcTreeNode, skeletonGraph);

        this.sourceOfFace = new HashMap<>();
        this.facesOfSource = new HashMap<>();
        this.targetOfFace = new HashMap<>();

        this.leftEdge = new HashMap<>();
        this.rightEdge = new HashMap<>();

        this.virtualEdgeToTCTreeNode = calcTCNodeOfVirtualEdge();

        this.faceTypes = new HashMap<>();

        calcSourceAndTargetOfFaces();

        System.out.println(PrintColors.ANSI_YELLOW + "    ConnectVertices");
        for(Vertex vertex : skeletonGraph.getVertices()){
            connectVertices(vertex);
        }

        HolderProvider.getSuccessorPathTypeHolder().getNodeTypes().put(tcTreeNode, successorPathType);
        System.out.println(PrintColors.ANSI_GREEN + "    SucessorPathType: " + successorPathType);
    }





    private MultiDirectedGraph convertSkeletonToGraph(){

        MultiDirectedGraph skeletonGraph = new MultiDirectedGraph();
        for(TCTreeNode<DirectedEdge, Vertex> child : tcTree.getChildren(tcTreeNode)){
            Vertex source = HolderProvider.getSourceTargetPertinentGraphsHolder().getSourceNode(child);
            Vertex target = HolderProvider.getSourceTargetPertinentGraphsHolder().getTargetNode(child);
            skeletonGraph.addEdge(source, target);
        }
        System.out.println(PrintColors.ANSI_RED + "    Skeleton: " + skeletonGraph);
        return skeletonGraph;
    }






    private Map<DirectedEdge, TCTreeNode<DirectedEdge, Vertex>> calcTCNodeOfVirtualEdge(){

        Map<DirectedEdge, TCTreeNode<DirectedEdge, Vertex>> virtualEdgeToTCTreeNode = new HashMap<>();

        for(TCTreeNode<DirectedEdge, Vertex> child : tcTree.getChildren(tcTreeNode)){
            Vertex pertSource = HolderProvider.getSourceTargetPertinentGraphsHolder().getSourceNode(child);
            Vertex pertTarget = HolderProvider.getSourceTargetPertinentGraphsHolder().getTargetNode(child);
            DirectedEdge virtualEdge = skeletonGraph.getEdge(pertSource, pertTarget);
            virtualEdgeToTCTreeNode.put(virtualEdge, child);
        }

        return virtualEdgeToTCTreeNode;
    }




    private void calcSourceAndTargetOfFaces(){

        for(Vertex vertex : skeletonGraph.getVertices()){
            facesOfSource.put(vertex, new LinkedList<>());
        }

        for(List<DirectedEdge> face : skeletonFaces){

            Vertex source = null;
            Vertex target = null;

            for(int i = 0; i < face.size(); i++){

                DirectedEdge edge1 = face.get((i+0)%face.size());
                DirectedEdge edge2 = face.get((i+1)%face.size());
                if(edge1.getSource().equals(edge2.getSource())) {
                    source = edge1.getSource();
                    leftEdge.put(face, edge1);
                    rightEdge.put(face, edge2);
                    assignLabelsToFace(face);
                }
                if(edge1.getTarget().equals(edge2.getTarget())) {
                    target = edge1.getTarget();
                }
            }
            sourceOfFace.put(face, source);
            targetOfFace.put(face, target);
            facesOfSource.get(source).add(face);
        }
    }




    private void assignLabelsToFace(List<DirectedEdge> face){

        Vertex lVertex = leftEdge.get(face).getTarget();
        Vertex rVertex = rightEdge.get(face).getTarget();
        DirectedEdge lrEdge = skeletonGraph.getEdge(lVertex, rVertex);

        if(lrEdge == null) {
            faceTypes.put(face, FaceType.UNDEFINED);
            return;
        }

        if (lrEdge.getTarget().equals(lVertex)) {
            faceTypes.put(face, FaceType.TYPE_L);
        } else if (lrEdge.getTarget().equals(rVertex)) {
            faceTypes.put(face, FaceType.TYPE_R);
        } else {
            faceTypes.put(face, FaceType.UNDEFINED);
        }
    }






    private List<DirectedEdge> getOutgoingEdgesOfSkeleton(Vertex vertex){

        List<DirectedEdge> outgoingEdges = new LinkedList<>();
        List<List<DirectedEdge>> facesOfVertex = facesOfSource.get(vertex);

        for(List<DirectedEdge> face : facesOfVertex){
            for(int j = 0; j < face.size(); j++) {
                DirectedEdge e1 = face.get((j + 0) % face.size());
                DirectedEdge e2 = face.get((j + 1) % face.size());
                if (e1.getSource().equals(vertex) && !outgoingEdges.contains(e1))
                    outgoingEdges.add(e1);
                if (e2.getSource().equals(vertex) && !outgoingEdges.contains(e2))
                    outgoingEdges.add(e2);
            }

        }
        return outgoingEdges;
    }







    //TODO: OK
    private void connectVertices(Vertex vertex) throws LDrawingNotPossibleException {

        List<List<DirectedEdge>> outgoingFaces = facesOfSource.get(vertex);
        if(outgoingFaces.isEmpty()) return;
        List<List<DirectedEdge>> outgoingFacesOrdered = new LinkedList<>();
        TCTreeNode<DirectedEdge, Vertex> optTypeBNode = null;
        boolean bothTypeOfFacesContained = false;

        //order outgoing faces from left to right
        int index = 0;
        outgoingFacesOrdered.add(outgoingFaces.get(0));
        while(index < outgoingFaces.size()){
            List<DirectedEdge> face = outgoingFaces.get(index++);
            List<DirectedEdge> first = outgoingFacesOrdered.get(0);
            List<DirectedEdge> last = outgoingFacesOrdered.get(outgoingFacesOrdered.size()-1);
            if(rightEdge.get(face).equals(leftEdge.get(first))){
                outgoingFacesOrdered.add(0, face);
                index = 0;
            }
            if(rightEdge.get(last).equals(leftEdge.get(face))){
                outgoingFacesOrdered.add(face);
                index = 0;
            }
        }

        System.out.println(PrintColors.ANSI_YELLOW + "      Vertex " + vertex + ": ");
        for(List<DirectedEdge> face : outgoingFacesOrdered){
            System.out.print(PrintColors.ANSI_YELLOW + "        Face: ");
            for(DirectedEdge edge : face)
                System.out.print(PrintColors.ANSI_YELLOW + edge + "  ");
            System.out.println();
        }

        //check if there is more than one type B child.
        for(DirectedEdge edge : getOutgoingEdgesOfSkeleton(vertex)){
            TCTreeNode<DirectedEdge, Vertex> child = virtualEdgeToTCTreeNode.get(edge);
            if(HolderProvider.getSuccessorPathTypeHolder().getNodeTypes().get(child).equals(SuccessorPathType.TYPE_B)){
                if(optTypeBNode != null)
                    throw new LDrawingNotPossibleException("R-Node contains two children assigned with Type-B that have the same source.");
                optTypeBNode = child;
                successorPathType = SuccessorPathType.TYPE_B;
            }
        }

        //check if all R faces are before typeB node and all L faces are after typeB node.
        if(optTypeBNode != null){
            for(List<DirectedEdge> face : outgoingFacesOrdered){
                DirectedEdge left = leftEdge.get(face);
                DirectedEdge right = rightEdge.get(face);
                if(faceTypes.get(face).equals(FaceType.TYPE_L) && optTypeBNode.equals(virtualEdgeToTCTreeNode.get(right)))
                        throw new LDrawingNotPossibleException("R-Node contains a child assigned with Type-B that is the right edge of a face assigned with Type-L.");
                if(faceTypes.get(face).equals(FaceType.TYPE_R) && optTypeBNode.equals(virtualEdgeToTCTreeNode.get(left)))
                        throw new LDrawingNotPossibleException("R-Node contains a child assigned with Type-B that is the left edge of a face assigned with Type-R.");
            }
        }

        //check if all faces with type L are after all faces with type R.
        TCTreeNode<DirectedEdge, Vertex> nodeWithApex = null;
        for(int i = 0; i < outgoingFacesOrdered.size()-1; i++) {
            List<DirectedEdge> face1 = outgoingFacesOrdered.get(i);
            List<DirectedEdge> face2 = outgoingFacesOrdered.get(i+1);
            if (faceTypes.get(face1).equals(FaceType.TYPE_L) && faceTypes.get(face2).equals(FaceType.TYPE_R))
                throw new LDrawingNotPossibleException("R-Node contains a vertex with a face assigned with Type-L placed before a face assigned with Type-R.");
            if (faceTypes.get(face1).equals(FaceType.TYPE_R) && faceTypes.get(face2).equals(FaceType.TYPE_L)) {
                bothTypeOfFacesContained = true;
                successorPathType = SuccessorPathType.TYPE_B;
                nodeWithApex = virtualEdgeToTCTreeNode.get(rightEdge.get(face1));
            }
        }

        if(optTypeBNode != null){
            SuccessorConnector.connectWithTypeB(augmentedGraph, tcTree, tcTreeNode, vertex);
            System.out.println(PrintColors.ANSI_YELLOW + "        Finished with TypeB in successors!");
        }else if(bothTypeOfFacesContained){
            SuccessorConnector.connectWithBothTypes(augmentedGraph, tcTree, tcTreeNode, nodeWithApex, vertex);
            System.out.println(PrintColors.ANSI_YELLOW + "        Finished with both types of faces!");
        }else{
            SuccessorConnector.connectWithOnlyOneType(augmentedGraph, tcTree, tcTreeNode, facesOfSource, faceTypes, vertex);
            System.out.println(PrintColors.ANSI_YELLOW + "        Finished with only one type of faces!");
        }
    }
}
