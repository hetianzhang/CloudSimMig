package org.cloudbus.cloudsim.sdn.netapp;

import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Node;
import org.jgrapht.Graph;
import org.jgrapht.graph.DefaultEdge;

public abstract class NorthBandInterface {
	protected Graph<Node, DefaultEdge> g;
	protected Map<Link, List<Double>> linkBandwidthMap;
	public abstract List<List<Node>> getHostPathNodes(int src, int dst);
	public abstract List<List<Link>> getHostPathLinks(int src, int dst);
	public abstract List<List<Node>> getHostRouteNodes(int src, int dst);
	public abstract List<List<Link>> getHostRouteLinks(int src, int dst);
	public abstract List<List<Node>> getVmPathNodes(int srcVm, int dstVm, int flowId);
	public abstract List<List<Node>> getVmPathLinks(int srcVm, int dstVm, int flowId);
	public abstract List<List<Node>> getVmRouteNodes(int srcVm, int dstVm, int flowId);
	public abstract List<List<Node>> getVmRouteLinks(int srcVm, int dstVm, int flowId);
	public abstract Graph<Node, DefaultEdge> getNetworkTopo(NetworkOperatingSystem nos);
	public abstract void setHostDefaultRouting();
	public abstract void setVmDefaultRouting(int srcVm, int dstVm, int flowId);
	public abstract void getFreeBw(int src, int dst);
	public abstract void getAllFreeBw(int src, int dst, int kPaths);
	public abstract void updateRequiredBw(int vmSrc, int vmDst, int flowId);
}
