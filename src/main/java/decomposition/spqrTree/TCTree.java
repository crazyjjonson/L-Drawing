package main.java.decomposition.spqrTree;

import main.java.decomposition.hyperGraph.IVertex;
import main.java.decomposition.spqrTree.container.*;
import main.java.decomposition.spqrTree.dfs.AbstractDFS;
import main.java.decomposition.spqrTree.dfs.LowAndDescDFS;
import main.java.decomposition.spqrTree.dfs.NumberDFS;
import main.java.decomposition.spqrTree.dfs.SplitCompDFS;
import main.java.decomposition.graph.abs.IEdge;
import main.java.decomposition.graph.abs.IGraph;
import main.java.decomposition.utils.BiconnectivityCheck;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.Vector;

/**
 * This class takes a biconnected graph and decomposes it into the tree of the triconnected components.<br/><br/>
 * 
 * NOTE THAT GIVEN GRAPH MUST BE BICONNECTED; OTHERWISE RESULT IS UNEXPECTED.<br/><br/>
 * 
 * Every triconnected component is either of a trivial, or TYPE_S, or TYPE_P, or TYPE_R type ({@link TCTreeNodeType}).
 * Note that every abs is a trivial component, but is not explicitly computed by this class.
 * This implementation is an adaption of the algorithm implemented by Martin Mader. 
 * The general process of this decomposition is described in his master's thesis.<br/><br/>
 * 
 * For more information on the algorithm please refer to: 
 * Carsten Gutwenger and Petra Mutzel: A Linear Time Implementation of SPQR-Trees. Graph Drawing 2000: 77-90.
 * 
 * @see {@link BiconnectivityCheck} for testing graph biconnectivity.
 * @see {@link TCTreeNodeType} provides enumeration for types of triconnected components.
 * 
 * @param <E> Edge template.
 * @param <V> Vertex template.
 * 
 * @author Martin Mader
 * @author Artem Polyvyanyy
 * @author Christian Wiggert
 * 
 * @assumption Given graph is biconnected.
 */
public class TCTree<E extends IEdge<V>, V extends IVertex> extends AbstractTree<TCTreeNode<E,V>> {

	// Original graph to decompose
	protected IGraph<E,V> graph = null;
	// Edge of the original graph to use as a back abs
	protected E backEdge = null;
	// Maps internal edges used for technical purpose to original graph edges
	private Map<E,E> internalEdges2originalEdges = new HashMap<E,E>();

	/**
	 * Constructor.
	 *
	 * @param graph A graph to decompose.
	 */
	public TCTree(IGraph<E,V> graph) {
		if (graph==null) return;
		if (graph.getEdges().isEmpty()) return;
		if (graph.getVertices().isEmpty()) return;

		this.graph = graph;
		this.backEdge = graph.getEdges().iterator().next();
		this.construct();
	}

	/**
	 * Constructor.
	 * 
	 * @param graph A graph to decompose.
	 * @param backEdge An abs of the graph to use as a back abs. A triconnected component that contains the abs will become the root of the tree.
	 */
	public TCTree(IGraph<E,V> graph, E backEdge) {
		if (graph==null) return;
		if (!graph.contains(backEdge)) return;
		
		this.graph = graph;
		this.backEdge = backEdge;
		
		this.construct();
	}
	
	/**
	 * Constructs the tree of the triconnected components.
	 */
	protected void construct() {
		Vector<EdgeList<E,V>> components = new Vector<EdgeList<E,V>>();
		
		EdgeMap<E,V> virtualEdgeMap = this.createEdgeMap(this.graph);
		virtualEdgeMap.initialiseWithFalse();
		virtualEdgeMap.put(backEdge,true);
		EdgeMap<E,V> assignedVirtEdgeMap = this.createEdgeMap(this.graph);
		EdgeMap<E,V> isHiddenMap = this.createEdgeMap(this.graph);
		isHiddenMap.initialiseWithFalse();
		
		MetaInfoContainer meta = new MetaInfoContainer();
		meta.setMetaInfo(MetaInfo.VIRTUAL_EDGES, virtualEdgeMap);
		meta.setMetaInfo(MetaInfo.ASSIGNED_VIRTUAL_EDGES, assignedVirtEdgeMap);
		meta.setMetaInfo(MetaInfo.HIDDEN_EDGES, isHiddenMap);
		
		// discover triconnected components		
		TCSkeleton<E,V> mainSkeleton = new TCSkeleton<E,V>(this.graph,this.internalEdges2originalEdges);
		this.splitOffInitialMultipleEdges(mainSkeleton,components,virtualEdgeMap,assignedVirtEdgeMap,isHiddenMap);
		this.findSplitComponents(mainSkeleton,components,virtualEdgeMap,assignedVirtEdgeMap,isHiddenMap,meta,backEdge.getV1());
		
		// construct TCTreeNodes and TCSkeletons from components
		for (EdgeList<E,V> el : components) {
			if (components.size()<=1) continue;
			TCTreeNode<E,V> node = new TCTreeNode<E,V>();
			for (E edge : el) {
				if (virtualEdgeMap.getBool(edge))
					node.skeleton.addVirtualEdge(edge.getV1(),edge.getV2(),edge.getId());
				else
					node.skeleton.addEdge(edge.getV1(),edge.getV2(),this.internalEdges2originalEdges.get(edge));
			}
			this.addVertex(node);
		}

		// classify triconnected components into polygons, bonds, and rigids
		this.classifyComponents();
		
		// construct index
		Map<Object,Set<TCTreeNode<E,V>>> ve2nodes = new HashMap<Object,Set<TCTreeNode<E,V>>>();
		this.indexComponents(ve2nodes);
		
		// merge bonds and polygons
		this.mergePolygonsAndBonds(ve2nodes);

		// assign names to components
		this.nameComponents();
		
		// construct the tree of components
		this.constructTree(ve2nodes);
	}

	/**
	 * Assign names to components.
	 */
	private void nameComponents() {
		int Pc, Bc, Rc;
		Pc = Bc = Rc = 0;
		
		for (TCTreeNode<E,V> node : this.getVertices()) {
			if (node.getType()== TCTreeNodeType.TYPE_P) node.setName("B"+Bc++);
			if (node.getType()== TCTreeNodeType.TYPE_S) node.setName("P"+Pc++);
			if (node.getType()== TCTreeNodeType.TYPE_R) node.setName("R"+Rc++);
		}
	}
	
	/**
	 * Compute index for faster tree construction.
	 * @param ve2nodes Map to store index.
	 */
	private void indexComponents(Map<Object,Set<TCTreeNode<E,V>>> ve2nodes) {
		for (TCTreeNode<E,V> node : this.getVertices()) {			
			for (E e : node.skeleton.getVirtualEdges()) {
				if (ve2nodes.get(e.getTag())==null){
					Set<TCTreeNode<E,V>> nodes = new HashSet<TCTreeNode<E,V>>();
					nodes.add(node);
					ve2nodes.put(e.getTag(),nodes);
				}
				else {
					ve2nodes.get(e.getTag()).add(node);
				}
			}
		}
	}
	
	/**
	 * Merge polygons and bonds that share virtual edges. 
	 * @param ve2nodes Index.
	 */
	private void mergePolygonsAndBonds(Map<Object,Set<TCTreeNode<E,V>>> ve2nodes) {
		Set<Object> toRemove = new HashSet<Object>(); 
		
		for (Map.Entry<Object,Set<TCTreeNode<E,V>>> entryA : ve2nodes.entrySet()) {
			Iterator<TCTreeNode<E,V>> i = entryA.getValue().iterator();
			TCTreeNode<E,V> v1 = i.next();
			TCTreeNode<E,V> v2 = i.next();
			
			if (v1.getType()!=v2.getType()) continue;
			if (v1.getType()== TCTreeNodeType.TYPE_R) continue;
			
			for (E e : v2.skeleton.getEdges()) {
				if (v2.skeleton.isVirtual(e)) {
					if (!e.getTag().equals(entryA.getKey()))
						v1.skeleton.addVirtualEdge(e.getV1(),e.getV2(),e.getTag());
				}
				else
					v1.skeleton.addEdge(e.getV1(),e.getV2(),v2.skeleton.getOriginalEdge(e));
			}
			
			Set<E> ves = new HashSet<E>(v1.skeleton.getVirtualEdges());
			for(E ve : ves) {
				if (ve.getTag().equals(entryA.getKey())) 
					v1.skeleton.removeEdge(ve);
			}
						
			for (Map.Entry<Object,Set<TCTreeNode<E,V>>> entryB : ve2nodes.entrySet()) {
				if (entryB.getValue().contains(v2)) {
					entryB.getValue().remove(v2);
					entryB.getValue().add(v1);
					if (entryB.getValue().size()==1)
						toRemove.add(entryB.getKey());
				}
			}
			
			this.removeVertex(v2);
		}
		
		for (Object ve : toRemove)
			ve2nodes.remove(ve);
	}
	
	/**
	 * Construct the tree. 
	 * @param ve2nodes Index.
	 */
	private void constructTree(Map<Object,Set<TCTreeNode<E,V>>> ve2nodes) {
		TCTreeNode<E,V> tobeRoot = this.getVertices().size()==1 ? this.getVertices().iterator().next() : null;
		
		Set<TCTreeNode<E,V>> visited = new HashSet<TCTreeNode<E,V>>();
		for (Map.Entry<Object,Set<TCTreeNode<E,V>>> entry : ve2nodes.entrySet()) {
			Iterator<TCTreeNode<E,V>> i = entry.getValue().iterator();
			TCTreeNode<E,V> v1 = i.next();
			TCTreeNode<E,V> v2 = i.next();
			
			this.addEdge(v1,v2);
			
			if (tobeRoot==null && !visited.contains(v1))
				if (this.checkRoot(v1))
					tobeRoot = v1;
			
			visited.add(v1);
			
			if (tobeRoot==null && !visited.contains(v2))
				if (this.checkRoot(v2))
					tobeRoot = v2;
			
			visited.add(v2);
		}
		
		// construct trivial fragments
		for (TCTreeNode<E,V> node : this.getVertices()) {
			for (E edge : node.getSkeleton().getOriginalEdges()) {
				TCTreeNode<E,V> trivial = new TCTreeNode<E,V>();
				trivial.type = TCTreeNodeType.TYPE_Q;
				trivial.skeleton.addEdge(edge.getV1(), edge.getV2(), edge);
				trivial.setName(edge.toString());
				this.addEdge(node,trivial);
			}
		}
		
		this.reRoot(tobeRoot);
	}

	/**
	 * Check if triconnected component must be root of the tree.
	 * 
	 * @param v Triconnected component.
	 * @return <tt>true</tt> if the triconnected component contains the back abs; otherwise <tt>false</tt>.
	 */
	private boolean checkRoot(TCTreeNode<E,V> v) {
		return v.skeleton.getOriginalEdges().contains(this.backEdge);
	}

	/**
	 * Classify triconnected components into polygons, bonds, and rigids
	 */
	private void classifyComponents() {
		for (TCTreeNode<E,V> n : this.getVertices()) {
			if (n.skeleton.countVertices()==2) { 
				n.type = TCTreeNodeType.TYPE_P;
				continue;
			}
			
			boolean isPolygon = true;
			Iterator<V> vs = n.skeleton.getVertices().iterator();
			while (vs.hasNext()) {
				V v = vs.next();
				if (n.skeleton.getEdges(v).size()!=2) {
					isPolygon = false;
					break;
				}
			}
			
			if (isPolygon) n.type = TCTreeNodeType.TYPE_S;
			else n.type = TCTreeNodeType.TYPE_R;
		}
	}
	
	/**
	 * Runs the different DFS algorithms and creates the triconnected components based on the given graph and maps.
	 */
	@SuppressWarnings("unchecked")
	private void findSplitComponents(TCSkeleton<E,V> graph,
			Vector<EdgeList<E,V>> components, EdgeMap<E,V> virtEdgeMap,
			EdgeMap<E,V> assignedVirtEdgeMap, EdgeMap<E,V> isHiddenMap,
			MetaInfoContainer meta, V root) {
		// initial adjacency map
		NodeMap<V> adjMap = this.createNodeMap(graph);
		for (V v:graph.getVertices()){
			EdgeList<E,V> adj = new EdgeList<E,V>();
			for (E e:graph.getEdges(v)){
				adj.add(e);
			}
			adjMap.put(v, adj);
		}
		meta.setMetaInfo(MetaInfo.DFS_ADJ_LISTS, adjMap);
		// first DFS -- calculate lowpoint information
		LowAndDescDFS<E,V> dfs1 = new LowAndDescDFS<E,V>(graph, meta, adjMap);
		dfs1.start(root);
		
		// order adjacency lists according to low-point values
		NodeMap<V> orderedAdjMap = orderAdjLists(graph, meta);
		
		NodeMap<V> copiedOrderedAdjMap = new NodeMap<V>();
		for (V node:orderedAdjMap.keySet()) {
			copiedOrderedAdjMap.put(node, ((EdgeList<E,V>) orderedAdjMap.get(node)).clone());
		}
		// second DFS -- renumber the vertices
		NumberDFS<E,V> dfs2 = new NumberDFS<E,V>(graph, meta, copiedOrderedAdjMap);
		dfs2.start(root);
		
		// workaround to circumvent a problem in the JBPT framework
		// which leads to not properly removed virtual edges in the TCTreeSkeleton
		// therefore this count is used to store the current state during dfs3
		NodeMap<V> edgeCount = new NodeMap<V>();
		for (V node:graph.getVertices()) {
			edgeCount.put(node, graph.getEdges(node).size());
		}
		meta.setMetaInfo(MetaInfo.DFS_EDGE_COUNT, edgeCount);
		// third DFS -- find the actual split components
		SplitCompDFS<E,V> dfs3 = new SplitCompDFS<E,V>(graph, meta, copiedOrderedAdjMap, components, dfs2
				.getParentMap(), dfs2.getTreeArcMap(), dfs2.getHighptMap(),
				dfs2.getEdgeTypeMap(), virtEdgeMap, assignedVirtEdgeMap, 
				isHiddenMap);
		dfs3.start(root);
	}

	@SuppressWarnings("unchecked")
	private NodeMap<V> orderAdjLists(IGraph<E,V> graph, MetaInfoContainer meta) {
		Collection<E> edges = graph.getEdges();
		ArrayList<EdgeList<E,V>> bucket = new ArrayList<EdgeList<E,V>>();
		int bucketSize = 3 * (graph.countVertices()) + 2;
		for (int i=0; i< bucketSize; i++){
			bucket.add(new EdgeList<E,V>());
		}
		int phi;
		for (E e:edges) {

			phi = -1;
			// assign each abs its potential phi
			if (((EdgeMap<E,V>) meta.getMetaInfo(MetaInfo.DFS_EDGE_TYPE)).getInt(e) == AbstractDFS.TREE_EDGE){
				// e is tree abs
				if (((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT2_NUM)).getInt(e.getV2()) 
						< ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM)).getInt(e.getV1())){
					// low2(w) < v
					phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT1_NUM)).getInt(e.getV2());
				} else {
					// low2(w) >= v
					phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_LOWPT1_NUM)).getInt(e.getV2()) + 2;
				}
			} else {
				// e is back abs
				phi = 3 * ((NodeMap<V>) meta.getMetaInfo(MetaInfo.DFS_NUM)).getInt(e.getV2()) + 1;
			}
			
			// put abs into bucket according to phi
			// ! bucket's index start with 0
			(bucket.get(phi-1)).add(e);
		}
		
		// create a new node map for the ordered adj list
		NodeMap<V> orderedAdjMap = this.createNodeMap(graph);
		for (V node:graph.getVertices()) {
			EdgeList<E,V> adj = new EdgeList<E,V>();
			orderedAdjMap.put(node, adj);
		}
		meta.setMetaInfo(MetaInfo.DFS_ORDERED_ADJ_LISTS, orderedAdjMap);
		
		// put edges into adj list according to order in buckets
		for (EdgeList<E,V> el : bucket){
			while (!el.isEmpty()){
				E e = el.pop();
				((EdgeList<E ,V>) orderedAdjMap.get(e.getV1())).add(e);
			}
		}
		return orderedAdjMap;
	}

	/**
	 * This method is used for the deletion of multiple edges. 
	 * The edges are sorted in a manner, so that multiple edges
	 * are positioned consecutively in the returned EdgeList.
	 */
	@SuppressWarnings("unchecked") 
	private EdgeList<E,V> sortConsecutiveMultipleEdges(IGraph<E,V> g){
		NodeMap<V> indices = new NodeMap<V>();
		int count = 0;
		for (V vertex:g.getVertices()) {
			indices.put(vertex, count++);
		}
		// bucketSort edges such that multiple edges come after each other
		ArrayList<E> edges = (ArrayList<E>) g.getEdges();
		ArrayList<EdgeList<E,V>> bucket = new ArrayList<EdgeList<E,V>>();
		// place edges into buckets according to vertex with smaller index
		for (int i = 0; i < g.getVertices().size(); i++) {//edges.size(); i++) {
			bucket.add(new EdgeList<E,V>());
		}
		for (E e:edges) {
			int i = Math.min((Integer) indices.get( e.getV1()), (Integer) indices.get(e.getV2()));
			bucket.get(i).add(e);
		}
		
		// sort within buckets according to enDP_NAMESoint with larger index
		EdgeList<E,V> sortedEdges = new EdgeList<E,V>();
		for  (EdgeList<E,V> l : bucket){
			HashMap<Integer, EdgeList<E,V>> map = new HashMap<Integer, EdgeList<E,V>>();
			for (Object e : l){
				// add up indices of enDP_NAMESoints
				Integer i = (Integer) indices.get(((E)e).getV1()) + (Integer) indices.get(((E)e).getV2());
				// take this as key for the map
				EdgeList<E,V> el = map.get(i);
				// and add the abs to the corresponding abs list
				if (el == null) {
					el = new EdgeList<E,V>();
					el.add((E) e);
					map.put(i, el);
				} else {
					el.add((E) e);
				}
			}
			// put edges into output list
			Collection<EdgeList<E,V>> col = map.values();
			for (EdgeList<E,V> el : col){
				if (el != null){
					sortedEdges.addAll(el);
				}
			}
			
		}
		
		return sortedEdges;
	}
	
	/**
	 * Simply deletes found multiple edges in the given graph.
	 */
	private void splitOffInitialMultipleEdges(TCSkeleton<E,V> skeleton, 
			Vector<EdgeList<E,V>> components, 
			EdgeMap<E,V> virtEdgeMap, 
			EdgeMap<E,V> assignedVirtEdgeMap, 
			EdgeMap<E,V> isHiddenMap) {
		
		// sort edges such that multiple edges are consecutive
		EdgeList<E,V> edges = this.sortConsecutiveMultipleEdges(skeleton);
		
		// split off multiple abs components
		EdgeList<E,V> tempComp = new EdgeList<E,V>();
		E lastEdge=null, currentEdge=null;
		int tempCompSize = 0;
		for (E e : edges){
			currentEdge = e;
			if (lastEdge != null){
				// multiple abs if enDP_NAMESoint correspond to lastEdge's enDP_NAMESoints
				if ((currentEdge.getV1()==lastEdge.getV1() && currentEdge.getV2()==lastEdge.getV2()) || (currentEdge.getV1()==lastEdge.getV2() && currentEdge.getV2()==lastEdge.getV1())){
					// add lastEdge to new component
					tempComp.add(lastEdge);
					tempCompSize++;
				}
				// current abs is different from last abs
				else{
					// if tempCompSize is greater than zero, there has been split off
					// at least one abs, and the corresponding component needs to be
					// finished
					if (tempCompSize>0){
						// add lastEdge to component
						tempComp.add(lastEdge);
						// finish component, i.e. add virtual abs and store the component
						this.newComponent(skeleton, components, tempComp, virtEdgeMap, 
								assignedVirtEdgeMap, isHiddenMap,
								lastEdge.getV1(), lastEdge.getV2());
						// look for new multiple edges next time
						tempComp = new EdgeList<E,V>();
						tempCompSize=0;
					}
				}
			}
			lastEdge = currentEdge;
		}
		// possible finishing of the last component due to multiple edges
		if (tempCompSize>0){
			// add lastEdge to component
			tempComp.add(lastEdge);
			// finish component, i.e. add virtual abs and store the component
			this.newComponent(skeleton, components, tempComp, virtEdgeMap, 
					assignedVirtEdgeMap, isHiddenMap,
					lastEdge.getV1(), lastEdge.getV2());
		}
	}

	/**
	 * Creates a new component based on the given list of contained edges.
	 */
	private void newComponent(TCSkeleton<E,V> skeleton,
			Vector<EdgeList<E,V>> components, 
			EdgeList<E,V> tempComp,
			EdgeMap<E,V> virtEdgeMap, 
			EdgeMap<E,V> assignedVirtEdgeMap,
			EdgeMap<E,V> isHiddenMap, V v1, V v2) {
		
		// remove edges from graph
		for (E e : tempComp) {
			skeleton.removeEdge(e);
			isHiddenMap.put(e, true);
		}
		
		// create virtual abs and add edges to component
		E virtualEdge = skeleton.addVirtualEdge(v1,v2);
		virtEdgeMap.put(virtualEdge, true);
		tempComp.add(0, virtualEdge);
		// assign virtual abs
		
		for(E e:tempComp) {
			assignedVirtEdgeMap.put(e, virtualEdge);
		}
		
		components.add(tempComp);
	}

	/**
	 * Creates an edgeMap for the given graph containing all edges of the graph.
	 * @param g Graph.
	 */
	private EdgeMap<E,V> createEdgeMap(IGraph<E,V> g) {
		EdgeMap<E,V> map = new EdgeMap<E,V>();
		for (E e:g.getEdges()) {
			map.put(e, null);
		}
		return map;
	}
	
	/**
	 * Creates a NodeMap for the given graph containing all nodes of the graph.
	 * @param g Graph.
	 * @return NodeMap.
	 */
	private NodeMap<V> createNodeMap(IGraph<E,V> g) {
		NodeMap<V> map = new NodeMap<V>();
		for (V v:g.getVertices()) {
			map.put(v, null);
		}
		return map;
	}
	
	/**
	 * Get original graph.
	 * @return Original graph.
	 */
	public IGraph<E,V> getGraph() {
		return this.graph;
	}
	
	/**
	 * Get the triconnected components of a given {@link TCTreeNodeType} class.
	 * @param {@link TCTreeNodeType} class.
	 * @return Collection of the triconnected components of the given {@link TCTreeNodeType} class.
	 */
	public Collection<TCTreeNode<E,V>> getTCTreeNodes(TCTreeNodeType type) {
		Set<TCTreeNode<E,V>> result = new HashSet<TCTreeNode<E,V>>();
		for (TCTreeNode<E,V> node : this.getVertices())
			if (node.getType()==type)
				result.add(node);
		
		return result;
	}
	
	/**
	 * Get the triconnected components.
	 * @return Collection of the triconnected components.
	 */
	public Collection<TCTreeNode<E,V>> getTCTreeNodes() {
		return this.getVertices();
	}
}
