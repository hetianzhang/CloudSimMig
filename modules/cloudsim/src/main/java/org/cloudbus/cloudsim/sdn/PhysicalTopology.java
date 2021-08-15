/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Hashtable;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.jgrapht.Graph;
import org.jgrapht.GraphPath;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.alg.shortestpath.KShortestSimplePaths;
import org.jgrapht.alg.shortestpath.DijkstraShortestPath;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.google.common.collect.Table;


/**
 * Network connection maps including switches, hosts, and links between them
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @author Tianzhang He
 * @since CloudSimSDN 1.0
 */
public class PhysicalTopology {
	public enum NodeType {
		Core,
		Aggr,
		Edge,
		Host,
	};
	final int RANK_CORE = 0;
	final int RANK_AGGR = 1;
	final int RANK_EDGE = 2;
	final int RANK_HOST = 3;
	
	Hashtable<Integer,Node> nodesTable;	// Address -> Node
	Table<Integer, Integer, Link> linkTable; 	// From : To -> Link
	Multimap<Node,Link> nodeLinks;	// Node -> all Links
	Map<String, Node> nameNodeTable = new Hashtable<String, Node>();
	Map<Node, String> nodeNameTable = new Hashtable<Node, String>();
	
	Graph<Node, DefaultEdge> g; //physical topo graph

	public PhysicalTopology() {
		nodesTable = new Hashtable<Integer,Node>();
		nodeLinks = HashMultimap.create();
		linkTable = HashBasedTable.create();
	}
	
	public Link getLink(int from, int to) {
		return linkTable.get(from, to);
	}
	public Node getNode(int id) {
		return nodesTable.get(id);
	}
	public double getLinkBandwidth(int from, int to){
		return getLink(from, to).getBw(getNode(from));
	}
	
	public double getLinkLatencyInSeconds(int from, int to){
		return getLink(from, to).getLatencyInSeconds();
	}
	
	public void addNode(Node node){
		nodesTable.put(node.getAddress(), node);
		if (node instanceof CoreSwitch){//coreSwitch is rank 0 (root)
			node.setRank(RANK_CORE);
		} else if (node instanceof AggregationSwitch){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(RANK_AGGR);
		} else if (node instanceof EdgeSwitch){//Edge switches are just before hosts in the hierarchy
			node.setRank(RANK_EDGE);
		} else if (node instanceof SDNHost){//Hosts are on the bottom of hierarchy (leaf)
			node.setRank(RANK_HOST);
		} else {
			throw new IllegalArgumentException();
		}
		
		addLoopbackLink(node);
	}
	
	public void setNameNodeTable(Map<String, Node> nameNodeTable) {
		this.nameNodeTable = nameNodeTable;
	}
	
	public void setNodeNameTable(Map<Node, String> nodeNameTable) {
		this.nodeNameTable = nodeNameTable;
	}
	
	public void buildDefaultRouting() {
		Collection<Node> nodes = getAllNodes();
		
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == 3) {	// Rank3 = SDN Host
				Collection<Link> links = getAdjacentLinks(sdnhost);
				for(Link l:links) {
					if(l.getLowOrder().equals(sdnhost)) {
						sdnhost.addRoute(null, l);
						Node edge = l.getHighOrder();
						edge.addRoute(sdnhost, l);
					}
				}
			}
		}
		// For Edge: build path to aggregate switch
		// For Aggregate: build path to edge switch
		for(Node agg:nodes) {
			if(agg.getRank() == 2) {	// Rank2 = Edge switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					//if(l.getLowOrder().equals(agg)) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		// For Agg: build path to core switch
		// For Core: build path to aggregate switch
		for(Node agg:nodes) {
			if(agg.getRank() == 1) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		
		for(Node n:nodes) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}

	}
	
	private Graph<Node, DefaultEdge> buildSDNTopoGraph() {
		Graph<Node, DefaultEdge> g = new DefaultDirectedGraph<>(DefaultEdge.class);
		Collection<Node> nodes = getAllNodes();
		for(Node n:nodes) {
			g.addVertex(n);
		}
		for(Node n:nodes) {
			Collection<Link> lList = getAdjacentLinks(n);
			for(Link l:lList) {
				Node m =l.getOtherNode(n);
				g.addEdge(n, m);
			}
		}
		return g;
	}
	
	public List<Node> shortestPath(Node src, Node dst) {
		DijkstraShortestPath<Node, DefaultEdge> simplepath = new DijkstraShortestPath<>(this.g);
		GraphPath<Node, DefaultEdge> path = simplepath.getPath(src, dst);		
		return path.getVertexList();
	}
	
	public Map<Integer,List<Node>> kShortestPath(Node src, Node dst) {
		Map<Integer, List<Node>> multiPaths = new HashMap<>();
		KShortestSimplePaths<Node, DefaultEdge> simplepaths = new KShortestSimplePaths<>(this.g);
		List<GraphPath<Node, DefaultEdge>> kpaths = simplepaths.getPaths(src, dst, Configuration.MPTCP_SUBFLOW_MAX);
		if(kpaths!=null && kpaths.size()!=0) {
			int i = 0;
			for(GraphPath<Node, DefaultEdge> p:kpaths) {
				List<Node> path = p.getVertexList();
				multiPaths.put(i, path);
				i++;
			}}
		return multiPaths;
	}
	
	
	public void shortestPath(Graph<Node, DefaultEdge> g, Node src, Node dst) {
		DijkstraShortestPath<Node, DefaultEdge> simplepath = new DijkstraShortestPath<>(g);
		GraphPath<Node, DefaultEdge> path = simplepath.getPath(src, dst);
		List<Node> nList = path.getVertexList();
		for(int i =0;i< nList.size()-1; i++) {
			Node n = nList.get(i);
			Node nNext = nList.get(i+1);
			Link l = this.getLink(n.getAddress(), nNext.getAddress());
			//System.out.print(n+"->");
			//if(n.getRank()!=3) {
				List<Link> currentLinks = n.getRoute(dst);
				if(currentLinks==null) {
					n.addRoute(dst, l);
				}else {
				if(!currentLinks.contains(l))
					n.addRoute(dst, l);
				}
			//}
		}
	}
	
	public void kShortestPath(Graph<Node, DefaultEdge> g, Node src, Node dst) {
		KShortestSimplePaths<Node, DefaultEdge> simplepaths = new KShortestSimplePaths<>(g);
		List<GraphPath<Node, DefaultEdge>> kpaths = simplepaths.getPaths(src, dst, Configuration.DEFAULT_ROUTING_PATH_K);
		if(kpaths!=null && kpaths.size()!=0) {
		for(GraphPath<Node, DefaultEdge> p:kpaths) {
			List<Node> nList = p.getVertexList();
			for(int i =0;i< nList.size()-2; i++) {
				Node n = nList.get(i);
				Node nNext = nList.get(i+1);
				Link l = this.getLink(n.getAddress(), nNext.getAddress());
				//System.out.print(n+"->");
				if(n.getRank()!=3) {
					List<Link> currentLinks = n.getRoute(dst);
					if(currentLinks==null) {
						n.addRoute(dst, l);
					}else {
					if(!currentLinks.contains(l))
						n.addRoute(dst, l);
					}
				}
			}
		}}
		else {
			System.out.print("bug: no path between ");
			System.out.print(src);
			System.out.print("<->");
			System.out.println(dst);
			throw new ArithmeticException("no path between nodes");	
		}
			//System.out.println();
		
	}
	
	
	public void buildEdgeNeighborForwarding(Graph<Node, DefaultEdge> g, Node src, List<Node> hList, int range) {
		//closed by base stations and edge data center forwarding table building
		
	}
	
	public void buildEdgeSimpleForwarding(Graph<Node, DefaultEdge> g, Node src, List<Node> hList) {
		for(Node dst:hList) {
			if(!dst.equals(src)) {
				if(dst.getRank()==3) {
						System.out.println(src+"->"+dst+" :");
						this.shortestPath(g, src, dst);
				}
			}
		}
	}
	
	public void buildSDNForwarding(Graph<Node, DefaultEdge> g, Node src, List<Node> hList) {
		for(Node dst:hList) {
			if(!dst.equals(src)) {
				if(dst.getRank()==3) {
						System.out.println(src+"->"+dst+" :");
						this.kShortestPath(g, src, dst);
				}
			}
		}	
	}
	
	public void checkTopoConnectivity() {
		
	}
	
	//[NEW] for graph API
	public Graph<Node, DefaultEdge> getTopoGraph(){
		return this.g;
	}
	
	private String getEdgeNodeId(Node src) {
		String name = this.nodeNameTable.get(src);
		String id;
		if(name ==null) {
			EdgeSwitch sw = (EdgeSwitch) src;
			id = sw.getName();
		}
		
		id = name.replace("gw", "");
		id = id.replace("sw", "");
		id = id.replace("base", "");
		id = id.replace("edge", "");

		return id;
	}
	
	public List<Node> getNeighborEdgeDataCenters(Node src) {
		List<Node> eList = new ArrayList<>();
		
		int id = Integer.valueOf(getEdgeNodeId(src));
		String gwName = "gw"+ id;
		EdgeSwitch gw = (EdgeSwitch) this.nameNodeTable.get(gwName);
		List<Link> links = gw.links;
		for(Link l: links) {
			Node n1 = l.getHighOrder();
			Node n2 = l.getLowOrder();
			int id1 = Integer.valueOf(this.getEdgeNodeId(n1));
			int id2 = Integer.valueOf(this.getEdgeNodeId(n2));
			
			String neighborEdgeName = "edge";
			if(id1 != id) {
				neighborEdgeName = neighborEdgeName + id1;
			}else {
				neighborEdgeName = neighborEdgeName + id2;
			}
			Node edgeNode = this.nameNodeTable.get(neighborEdgeName);
			
			if(!eList.contains(edgeNode)) {
				eList.add(edgeNode);
			}	
		}
		Node localEdgeNode = this.nameNodeTable.get("edge"+id);
		if(!eList.contains(localEdgeNode)) {
			eList.add(localEdgeNode);
		}	
		return eList;
	}
	
	public List<Node> getNeighborBaseStations(Node src) {
		List<Node> bList = new ArrayList<>();
		int id = Integer.valueOf(getEdgeNodeId(src));
		String gwName = "gw" + id;
		EdgeSwitch gw = (EdgeSwitch) this.nameNodeTable.get(gwName);
		List<Link> links = gw.links;
		for(Link l: links) {
			Node n1 = l.getHighOrder();
			Node n2 = l.getLowOrder();
			int id1 = Integer.valueOf(this.getEdgeNodeId(n1));
			int id2 = Integer.valueOf(this.getEdgeNodeId(n2));
			
			String neighborEdgeName = "base";
			if(id1 != id) {
				neighborEdgeName = neighborEdgeName + id1;
			}else {
				neighborEdgeName = neighborEdgeName + id2;
			}
			Node edgeNode = this.nameNodeTable.get(neighborEdgeName);
			
			if(!bList.contains(edgeNode)) {
				bList.add(edgeNode);
			}	
		}
		Node localBaseNode = this.nameNodeTable.get("base"+id);
		if(!bList.contains(localBaseNode)) {
			bList.add(localBaseNode);
		}
		
		return bList;
	}
	
	public void buildDefaultRoutingEdgeAll() {
		this.g = this.buildSDNTopoGraph();
		
		Collection<Node> nodes = getAllNodes();
		//List<Node> hList = new ArrayList<>(nodes);
		
		List<Node> gwList = new ArrayList<>();
		List<Node> swList = new ArrayList<>();
		List<Node> bList = new ArrayList<>(); //base stations
		List<Node> eList = new ArrayList<>(); //edge data centers
		
		for(String name: nameNodeTable.keySet()) {
			Node n = nameNodeTable.get(name);
			if(name.contains("gw")) {
				gwList.add(n);
			}else if(name.contains("sw")) {
				swList.add(n);
			}else if(name.contains("base")) {
				bList.add(n);
			}else if(name.contains("edge")) {
				eList.add(n);
			}
		}
		
		//route for base stations (base<->sw)
		for(Node base:bList) {
			Collection<Link> links = getAdjacentLinks(base);
			for(Link l:links) {
				if(l.getLowOrder().equals(base)) {
					base.addRoute(null, l);
					Node sw = l.getHighOrder();
					sw.addRoute(base, l);
				}
			}
		}
		
		for(Node base:bList) {
			//base -> edge
			this.buildEdgeSimpleForwarding(g, base, eList);
		}
		
		for(Node edge:eList) {
			//edge->base
			this.buildEdgeSimpleForwarding(g, edge, bList);
		}
		
		for(Node edge:eList) {
			//edge -> edge
			this.buildEdgeSimpleForwarding(g, edge, eList);
		}
	}
	
	//generate default routing only for near by base stations and edge data centers
	public void buildDefaultRoutingEdge() {
		int maxhop1 = 5; //edge dc to base stations
		int maxhop2 = 3; //edge dc to edge dc
		
		this.g = this.buildSDNTopoGraph();
		
		Collection<Node> nodes = getAllNodes();
		List<Node> hList = new ArrayList<>(nodes);
		
		List<Node> gwList = new ArrayList<>();
		List<Node> swList = new ArrayList<>();
		List<Node> bList = new ArrayList<>(); //base stations
		List<Node> eList = new ArrayList<>(); //edge data centers
		
		for(String name: nameNodeTable.keySet()) {
			Node n = nameNodeTable.get(name);
			if(name.contains("gw")) {
				gwList.add(n);
			}else if(name.contains("sw")) {
				swList.add(n);
			}else if(name.contains("base")) {
				bList.add(n);
			}else if(name.contains("edge")) {
				eList.add(n);
			}
		}
		
		//route for base stations (base<->sw)
		for(Node base:bList) {
			Collection<Link> links = getAdjacentLinks(base);
			for(Link l:links) {
				if(l.getLowOrder().equals(base)) {
					base.addRoute(null, l);
					Node sw = l.getHighOrder();
					sw.addRoute(base, l);
				}
			}
		}
		
		for(Node base:bList) {
			//base -> edge
			List<Node> directEdgeList = this.getNeighborEdgeDataCenters(base);
			this.buildEdgeSimpleForwarding(g, base, directEdgeList);
		}
		
		for(Node edge:eList) {
			//edge->base
			List<Node> directBaseList = this.getNeighborBaseStations(edge);
			this.buildEdgeSimpleForwarding(g, edge, directBaseList);
		}
		
		for(Node edge:eList) {
			//edge -> edge
			List<Node> directEdgeList = this.getNeighborEdgeDataCenters(edge);
			this.buildEdgeSimpleForwarding(g, edge, directEdgeList);
		}
	}
	
	//[NEW]
	public void buildDefaultRoutingWAN() {
		this.g = this.buildSDNTopoGraph();
		
		Collection<Node> nodes = getAllNodes();
		List<Node> hList = new ArrayList<>(nodes);
		
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == 3) {	// Rank3 = SDN Host
			Collection<Link> links = getAdjacentLinks(sdnhost);
			for(Link l:links) {
				if(l.getLowOrder().equals(sdnhost)) {
					sdnhost.addRoute(null, l);
					Node edge = l.getHighOrder();
					edge.addRoute(sdnhost, l);
				}
			}
			}
		}		
		
		for(Node n:nodes) {
			if(n.getRank()==3)
				//build default routing table for
				this.buildSDNForwarding(g, n, hList);
				//this.buildEdgeSimpleForwarding(g, n, hList);
		}
		
		
		/*
		for(Node core:nodes) {
			if(core.getRank()==2) { //Rank0 = Core switch between different DCs
				Collection<Link> links = getAdjacentLinks(core);
				for(Link l:links) {
					if(l.getOtherNode(core).getRank()!=3) {
						core.addRoute(null, l);
						Node otherCore = l.getOtherNode(core);
						// Add all children hosts to
						
						for(Node destination: core.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								otherCore.addRoute(destination, l);
						}
					}
				}
				
			}
		}*/
		
		
		for(Node n:nodes) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}

	}
	
	public Collection<Node> getNodesType(NodeType tier) {
		Collection<Node> allNodes = getAllNodes();
		Collection<Node> nodes = new LinkedList<Node>();
		for(Node node:allNodes) {
			if(tier == NodeType.Core && node.getRank() == RANK_CORE) {
				nodes.add(node);
			}
			else if(tier == NodeType.Aggr && node.getRank() == RANK_AGGR) {
				nodes.add(node);
			}
			else if(tier == NodeType.Edge && node.getRank() == RANK_EDGE) {
				nodes.add(node);
			}
			else if(tier == NodeType.Host && node.getRank() == RANK_HOST) {
				nodes.add(node);
			}
		}
		return nodes;
	}
	
	public Collection<Node> getConnectedNodesLow(Node node) {
		// Get the list of lower order
		Collection<Node> nodes = new LinkedList<Node>();
		Collection<Link> links = getAdjacentLinks(node);
		for(Link l:links) {
			if(l.getHighOrder().equals(node))
				nodes.add(l.getLowOrder());
		}
		return nodes;
	}

	public Collection<Node> getConnectedNodesHigh(Node node) {
		// Get the list of higher order
		Collection<Node> nodes = new LinkedList<Node>();
		Collection<Link> links = getAdjacentLinks(node);
		for(Link l:links) {
			if(l.getLowOrder().equals(node))
				nodes.add(l.getHighOrder());
		}
		return nodes;
	}

	
	public void buildDefaultRoutingFatTree() {
		this.g = this.buildSDNTopoGraph();
		
		Collection<Node> nodes = getAllNodes();
		
		/********************************************
		 * FatTree:: Building routing table for downlinks 
		 ********************************************/
		// For SDNHost: build path to edge switch
		// For Edge: build path to SDN Host
		// For Agg: build path to SDN Host through edge
		for(Node sdnhost:nodes) {
			if(sdnhost.getRank() == RANK_HOST) {	// Rank3 = SDN Host
				Collection<Link> links = getAdjacentLinks(sdnhost);
				for(Link l:links) {
					if(l.getLowOrder().equals(sdnhost)) {
						sdnhost.addRoute(null, l);
						Node edge = l.getHighOrder();
						edge.addRoute(sdnhost, l);
						
						Collection<Link> links2 = getAdjacentLinks(edge);
						for(Link l2:links2) {
							if(l2.getLowOrder().equals(edge)) {
								Node agg = l2.getHighOrder();
								agg.addRoute(sdnhost, l2);
							}
						}
						
					}
				}
			}
		}
		// For Core: build path to SDN Host through agg
		for(Node agg:nodes) {
			if(agg.getRank() == RANK_AGGR) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						Node core = l.getHighOrder();
						
						// Add all children hosts to
						for(Node destination: agg.getRoutingTable().getKnownDestination()) {
							if(destination != null)
								core.addRoute(destination, l);
						}
					}
				}
			}
		}
		
		/********************************************
		 * FatTree:: Building routing table for uplinks 
		 ********************************************/
		// For Edge: build path to aggregate switch
		for(Node edge:nodes) {
			if(edge.getRank() == RANK_EDGE) {	// Rank2 = Edge switch
				Collection<Link> links = getAdjacentLinks(edge);
				for(Link l:links) {
					if(l.getLowOrder().equals(edge)) {
						// Link is between Edge and Aggregate
						edge.addRoute(null, l);
					}
				}
			}
		}
		// For Agg: build path to core switch
		for(Node agg:nodes) {
			if(agg.getRank() == RANK_AGGR) {	// Rank1 = Agg switch
				Collection<Link> links = getAdjacentLinks(agg);
				for(Link l:links) {
					if(l.getLowOrder().equals(agg)) {
						// Link is between Edge and Aggregate
						agg.addRoute(null, l);
					}
				}
			}
		}
		
		for(Node n:nodes) {
			System.out.println("============================================");
			System.out.println("Node: "+n);
			n.getRoutingTable().printRoutingTable();
		}

	}
	public void addLink(int from, int to, double latency){
		Node fromNode = nodesTable.get(from);
		Node toNode = nodesTable.get(to);
		
		addLink(fromNode, toNode, latency);
	}
	
	public void addLink(Node fromNode, Node toNode, double latency, double upbw, double downbw) {
		int from = fromNode.getAddress();
		int to = toNode.getAddress();
		Link l = new Link(fromNode, toNode, latency, upbw, downbw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(from, to, l);
		linkTable.put(to, from, l);
		
		nodeLinks.put(fromNode, l);
		nodeLinks.put(toNode, l);
		
		fromNode.addLink(l);
		toNode.addLink(l);
	}
	
	public void addLink(Node fromNode, Node toNode, double latency){
		int from = fromNode.getAddress();
		int to = toNode.getAddress();
		
		long bw = (fromNode.getBandwidth()<toNode.getBandwidth())? fromNode.getBandwidth():toNode.getBandwidth();
		
		if(!nodesTable.containsKey(from)||!nodesTable.containsKey(to)){
			throw new IllegalArgumentException("Unknown node on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		if (linkTable.contains(fromNode.getAddress(), toNode.getAddress())){
			throw new IllegalArgumentException("Link added twice:"+fromNode.getAddress()+"->"+toNode.getAddress());
		}
		
		if(fromNode.getRank()==-1&&toNode.getRank()==-1){
			throw new IllegalArgumentException("Unable to establish orders for nodes on link:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
		}
		
		if (fromNode.getRank()>=0 && toNode.getRank()>=0){
			//we know the rank of both nodes; easy to establish topology
			if ((toNode.getRank()-fromNode.getRank())!=1) {
				//throw new IllegalArgumentException("Nodes need to be parent and child:"+nodesTable.get(from).getAddress()+"->"+nodesTable.get(to).getAddress());
			}
		}
		
		if(fromNode.getRank()>=0&&toNode.getRank()==-1){
			//now we know B is children of A
			toNode.setRank(fromNode.getRank()+1);
		}
		
		if(fromNode.getRank()==-1&&toNode.getRank()>=1){
			//now we know A is parent of B
			fromNode.setRank(toNode.getRank()-1);
		}
		Link l = new Link(fromNode, toNode, latency, bw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(from, to, l);
		linkTable.put(to, from, l);
		
		nodeLinks.put(fromNode, l);
		nodeLinks.put(toNode, l);
		
		fromNode.addLink(l);
		toNode.addLink(l);
	}
	
	private void addLoopbackLink(Node node) {
		int nodeId = node.getAddress();
		long bw = NetworkOperatingSystem.bandwidthWithinSameHost;
		double latency = NetworkOperatingSystem.latencyWithinSameHost;
		
		Link l = new Link(node, node, latency, bw);
		
		// Two way links (From -> to, To -> from)
		linkTable.put(nodeId, nodeId, l);
	}
	
	public Collection<Link> getAdjacentLinks(Node node) {
		return nodeLinks.get(node);
	}
	
	public Collection<Node> getAllNodes() {
		return nodesTable.values();
	}
	
	public Collection<Link> getAllLinks() {
		HashSet<Link> allLinks = new HashSet<Link>();
		allLinks.addAll(nodeLinks.values());
		return allLinks;
	}

}
