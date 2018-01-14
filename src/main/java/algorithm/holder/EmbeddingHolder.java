package main.java.algorithm.holder;

import com.yworks.yfiles.algorithms.*;
import main.java.decomposition.graph.DirectedEdge;
import main.java.decomposition.graph.MultiDirectedGraph;
import main.java.decomposition.hyperGraph.Vertex;

import java.util.*;

public class EmbeddingHolder {

    private Map<Node, Vertex>       convV2OrigV = new HashMap<>();
    private Map<Vertex, Node>       origV2ConvV = new HashMap<>();
    private Map<Edge, DirectedEdge> convE2OrigE = new HashMap<>();
    private Map<DirectedEdge, Edge> origE2ConvE = new HashMap<>();

    private Map<Vertex, List<DirectedEdge>> edgesCircualOrderings = new HashMap<>();
    private PlanarEmbedding planarEmbedding;

    public EmbeddingHolder(MultiDirectedGraph graph){

        Graph convGraph = new Graph();

        for(DirectedEdge origEdge : graph.getEdges()){

            Vertex origSource = origEdge.getSource();
            Vertex origTarget = origEdge.getTarget();

            if(!origV2ConvV.containsKey(origSource)){
                Node convSource = convGraph.createNode();
                origV2ConvV.put(origSource, convSource);
                convV2OrigV.put(convSource, origSource);
            }

            if(!origV2ConvV.containsKey(origTarget)){
                Node convTarget = convGraph.createNode();
                origV2ConvV.put(origTarget, convTarget);
                convV2OrigV.put(convTarget, origTarget);
            }

            Node convSource = origV2ConvV.get(origSource);
            Node convTarget = origV2ConvV.get(origTarget);

            Edge convEdge = convGraph.createEdge(convSource, convTarget);
            origE2ConvE.put(origEdge, convEdge);
            convE2OrigE.put(convEdge, origEdge);
        }

        planarEmbedding = new PlanarEmbedding(convGraph);
        for(Vertex vertex : graph.getVertices())
            getEdgesCircular(vertex);
    }





    public List<DirectedEdge> getEdgesCircular(Vertex vertex){

        if(edgesCircualOrderings.containsKey(vertex))
            return edgesCircualOrderings.get(vertex);

        List<Dart> outgoingDarts = planarEmbedding.getOutgoingDarts(origV2ConvV.get(vertex));
        List<DirectedEdge> edgesCircular = new ArrayList<>();

        for(Dart dart : outgoingDarts)
            edgesCircular.add(convE2OrigE.get(dart.getAssociatedEdge()));

        List<DirectedEdge> orderedEdgesCircular = orderEdgesCircular(edgesCircular, vertex);

        edgesCircualOrderings.put(vertex, orderedEdgesCircular);

        System.out.println(vertex.getName());
        for(DirectedEdge e : orderedEdgesCircular)
            System.out.println(e);

        return edgesCircular;
    }

    private List<DirectedEdge> orderEdgesCircular(List<DirectedEdge> edgesCircular, Vertex vertex){

        List<DirectedEdge> orderedEdgesCircularIncoming = new LinkedList<>();
        List<DirectedEdge> orderedEdgesCircularOutgoing = new LinkedList<>();
        List<DirectedEdge> edgesStillToAdd = new LinkedList<>();
        boolean ioAlreadySwitched = false;

        //Order all Outgoing first
        for(DirectedEdge edge : edgesCircular){
            boolean incoming = edge.getTarget().equals(vertex);
            if(incoming) {
                ioAlreadySwitched = true;
                continue;
            }
            if(!ioAlreadySwitched){
                orderedEdgesCircularIncoming.add(edge);
            }else{
                edgesStillToAdd.add(edge);
            }
        }

        for(int index = 0; index < edgesStillToAdd.size(); index++){
            orderedEdgesCircularIncoming.add(index, edgesStillToAdd.get(index));
        }

        //Order all incoming edges
        ioAlreadySwitched = false;
        edgesStillToAdd.clear();

        for(DirectedEdge edge : edgesCircular){
            boolean outgoing = edge.getSource().equals(vertex);
            if(outgoing) {
                ioAlreadySwitched = true;
                continue;
            }
            if(!ioAlreadySwitched){
                orderedEdgesCircularOutgoing.add(edge);
            }else{
                edgesStillToAdd.add(edge);
            }
        }

        for(int index = 0; index < edgesStillToAdd.size(); index++){
            orderedEdgesCircularOutgoing.add(index, edgesStillToAdd.get(index));
        }

        //add incoming to outgoing
        orderedEdgesCircularIncoming.addAll(orderedEdgesCircularOutgoing);
        return orderedEdgesCircularIncoming;
    }






    public DirectedEdge getCyclicNext(Vertex vertex, DirectedEdge directedEdge){

        List<DirectedEdge> edgesCircular = this.getEdgesCircular(vertex);
        int indexOfDirectedEdge = edgesCircular.indexOf(directedEdge);
        if(indexOfDirectedEdge < 0) return null;

        return indexOfDirectedEdge < edgesCircular.size()-1 ? edgesCircular.get(indexOfDirectedEdge+1) : edgesCircular.get(0);
    }





    public DirectedEdge getCyclicPrevious(Vertex vertex, DirectedEdge directedEdge){

        List<DirectedEdge> edgesCircular = this.getEdgesCircular(vertex);
        int indexOfDirectedEdge = edgesCircular.indexOf(directedEdge);
        if(indexOfDirectedEdge < 0) return null;

        return indexOfDirectedEdge > 0 ? edgesCircular.get(indexOfDirectedEdge-1) : edgesCircular.get(edgesCircular.size()-1);
    }
}
























