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
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEntity;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParser;
import org.cloudbus.cloudsim.sdn.parsers.VirtualTopologyParserTest;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunction;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionAutoScaler;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionForwarder;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.OverbookingVmAllocationPolicy;

/**
 * NOS calculates and estimates network behaviour. It also mimics SDN Controller functions.  
 * It manages channels between allSwitches, and assigns packages to channels and control their completion
 * Once the transmission is completed, forward the packet to the destination.
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public abstract class NetworkOperatingSystem extends SimEntity {
	protected SDNDatacenter datacenter;
	protected LinkSelectionPolicy linkSelector;

	// Physical topology
	protected String physicalTopologyFileName; 
	protected PhysicalTopology topology;
	protected List<SDNHost> allHosts;
	protected List<Switch> allSwitches;
	protected Map<String, Node> nameNodeTable;
	protected Map<Node, String> nodeNameTable;
	
	// Virtual topology
	protected HashMap<Integer, Vm> allVms = new HashMap<Integer, Vm>();
	protected LinkedList<Arc> allArcs = new LinkedList<Arc>();
	protected boolean isApplicationDeployed = false;
	
	// Mapping tables for searching
	protected Map<String, Integer> deployVmNameToIdTable;
	protected Map<String, Integer> deployFlowNameToIdTable;
	protected Map<Integer, Arc> deployFlowIdToArcTable;

	// Processing requests
	protected HashMap<String, Channel> channelTable;	// getKey(fromVM, toVM, flowID) -> Channel
	protected List<Channel> tempRemovedChannels;
	
	protected ServiceFunctionForwarder sfcForwarder;
	protected ServiceFunctionAutoScaler sfcScaler;

	// Debug only
	public static Map<Integer, String> debugVmIdName = new HashMap<Integer, String>();
	public static Map<Integer, String> debugFlowIdName = new HashMap<Integer, String>();

	// Resolution of the result. What's the unit of Bandwidth??
	public static long bandwidthWithinSameHost = Configuration.WITHINHOST_BW; // bandwidth between VMs within a same host: 12Gbps = 1.5GBytes/sec
	public static double latencyWithinSameHost = 0.1; //0.1 msec latency 
	
	private double lastMigration = 0;

	
//	private LinkedList<Channel> allChannels = new LinkedList<Channel>();	// this is only to track all channels.
	
	/**
	 * 1. map VMs and middleboxes to hosts, add the new vm/mb to the vmHostTable, advise host, advise dc
	 * 2. set channels and bws
	 * 3. set routing tables to restrict hops to meet latency
	 * @param sfcPolicy 
	 */
	protected abstract boolean deployApplication(List<Vm> vms, List<Middlebox> middleboxes, List<Arc> links, List<ServiceFunctionChainPolicy> sfcPolicy);
	protected abstract Middlebox deployMiddlebox(String type, Vm vm);

	public NetworkOperatingSystem(String physicalTopologyFilename) {
		super("NOS");
		
		this.physicalTopologyFileName = physicalTopologyFilename;
		this.channelTable = new HashMap<String, Channel>();
		this.sfcForwarder = new ServiceFunctionForwarder(this);
		this.sfcScaler = new ServiceFunctionAutoScaler(this, sfcForwarder);
		resetTempRemovedChannel();
		initPhysicalTopology();
	}
	
	private void resetTempRemovedChannel() {
		tempRemovedChannels = new LinkedList<Channel>();
	}

	public static double getMinTimeBetweenNetworkEvents() {
	    return CloudSim.getMinTimeBetweenEvents();
	}
	
	public static double round(double value) {
		/*
		int places = Configuration.resolutionPlaces;
	    if (places < 0) throw new IllegalArgumentException();

		if(Configuration.timeUnit >= 1000) value = Math.floor(value*Configuration.timeUnit);
		
	    BigDecimal bd = new BigDecimal(value);
	    bd = bd.setScale(places, RoundingMode.CEILING);
	    return bd.doubleValue();
	    */
	    return value;
	}
	
	private boolean monitorEnabled = true;
	
	public void setMonitorEnable(boolean monitorEnable) {
		monitorEnabled = monitorEnable;
	}
	
	public void setLinkSelectionPolicy(LinkSelectionPolicy linkSelectionPolicy) {
		this.linkSelector = linkSelectionPolicy;
	}
	
	public LinkSelectionPolicy getLinkSelectionPolicy() {
		return this.linkSelector;
	}

	
	@Override
	public void startEntity() {
		if(monitorEnabled)
			send(this.getId(), Configuration.monitoringTimeInterval, Constants.MONITOR_UPDATE_UTILIZATION);
	}

	@Override
	public void shutdownEntity() {
		
	}
	
	protected void debugPrintMonitoredValues() {
		//////////////////////////////////////////////////////////////		
		//////////////////////////////////////////////////////////////
		// For debug only
		
		Collection<Link> links = this.topology.getAllLinks();
		for(Link l:links) {
			System.err.println(l);
			MonitoringValues mv = l.getMonitoringValuesLinkUtilizationUp();
			System.err.print(mv);
			mv = l.getMonitoringValuesLinkUtilizationDown();
			System.err.print(mv);
		}
//		
//		for(Channel ch:this.allChannels) {
//			System.err.println(ch);
//			MonitoringValues mv = ch.getMonitoringValuesLinkUtilization();
//			System.err.print(mv);
//		}
		
		for(SDNHost h:datacenter.<SDNHost>getHostList()) {
			System.err.println(h);
			MonitoringValues mv = h.getMonitoringValuesHostCPUUtilization();
			System.err.print(mv);			
		}

		for(Vm vm:allVms.values()) {
			SDNVm tvm = (SDNVm)vm;
			System.err.println(tvm);
			MonitoringValues mv = tvm.getMonitoringValuesVmCPUUtilization();
			System.err.print(mv);			
		}
	}
	boolean migFlag = Configuration.MIG_ENABLE_FLAG;
	@Override
	public void processEvent(SimEvent ev) {
		int tag = ev.getTag();
		
		switch(tag){
			case Constants.SDN_INTERNAL_CHANNEL_PROCESS:
				adjustAllChannels();
				break;				
			case Constants.SDN_INTERNAL_PACKET_PROCESS: 
				internalPacketProcess(); 
				break;
			case CloudSimTags.VM_CREATE_ACK:
				processVmCreateAck(ev);
				break;
			case CloudSimTags.VM_DESTROY:
				processVmDestroyAck(ev);
				break;
			case Constants.SDN_VM_CREATE_DYNAMIC:
				processVmCreateDynamic(ev);
				break;
			case Constants.MONITOR_UPDATE_UTILIZATION:
				this.datacenter.processUpdateProcessing();
				updatePacketProcessing();
				
				this.updateBWMonitor(Configuration.monitoringTimeInterval);
				this.updateHostMonitor(Configuration.monitoringTimeInterval);
				this.updateSwitchMonitor(Configuration.monitoringTimeInterval);				
				
				if(CloudSim.clock() >= lastMigration + Configuration.migrationTimeInterval) {
					//this.datacenter.startAutoScale(); // Auto Scaling
					if(migFlag){
						this.datacenter.startMigrate(); // Live Migration
						//only start the first one
						if(Configuration.MIG_ONLY_FIRST_ROUND)
							migFlag = false;
					}
					lastMigration = CloudSim.clock(); 
				}
				this.updateVmMonitor(CloudSim.clock());
				
				if(CloudSimEx.getNumFutureEvents() > 0) {
					//TODO test set next monitor delay to 1800, add new event for migration interval
					double nextMonitorDelay = Configuration.monitoringTimeInterval;
					//double nextMonitorDelay = 1800;
					double nextEventDelay = CloudSimEx.getNextEventTime() - CloudSim.clock();
					
					// If there's no event between now and the next monitoring time, skip monitoring until the next event time. 
					if(nextEventDelay > nextMonitorDelay) {
						nextMonitorDelay = nextEventDelay;	
					}
					
					int numPackets=0;
					for(Channel ch:channelTable.values()) {
						numPackets += ch.getActiveTransmissionNum();
					}
					
					System.err.println(CloudSim.clock() + ": Elasped time="+ CloudSimEx.getElapsedTimeString()+", "
					+CloudSimEx.getNumFutureEvents()+" more events,"+" # packets="+numPackets+", next monitoring in "+nextMonitorDelay);
					send(this.getId(), nextMonitorDelay, Constants.MONITOR_UPDATE_UTILIZATION);
				}
				break;
			default: System.out.println("Unknown event received by "+super.getName()+". Tag:"+ev.getTag());
		}
	}

	protected void processVmCreateAck(SimEvent ev) {
//		SDNVm vm = (SDNVm) ev.getData();
//		Host host = findHost(vm.getId());
//		vm.setSDNHost(host);
	}
	
	protected void processVmCreateDynamic(SimEvent ev) {
		SDNVm newVm = (SDNVm) ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processVmCreateDynamic: Dynamic VM creation completed!"+newVm);
		
		if(newVm instanceof ServiceFunction)
			sfcForwarder.processVmCreateDyanmicAck((ServiceFunction)newVm);
	}
	
	// Migrate network flow from previous routing
	protected void processVmMigrate(Vm vm, SDNHost oldHost, SDNHost newHost) {
		// Find the virtual route associated with the migrated VM
		// VM is already migrated to the new host
		for(Arc arc:allArcs) {
			if(arc.getSrcId() == vm.getId()
					|| arc.getDstId() == vm.getId() )
			{
				SDNHost sender = findHost(arc.getSrcId());	// Sender will be the new host after migrated
				if(arc.getSrcId() == vm.getId())
					sender = oldHost;	// In such case, sender should be changed to the old host
				
				rebuildForwardingTable(arc.getSrcId(), arc.getDstId(), arc.getFlowId(), sender);
			}
		}
		
		// Move the transferring data packets in the old channel to the new one.
		migrateChannel(vm, oldHost, newHost);
		
		// Print all routing tables.
//		for(Node node:this.topology.getAllNodes()) {
//			node.printVMRoute();
//		}
	}
	
	@Deprecated
	//[NEW] Updated all channels transferring data from and to this VM.
	protected void updateVmTransmission(int updateVmId, long newBandwidth, int chId) {
		double oldBw = 0;
		for(Channel ch: this.channelTable.values()) {
			if(ch.getSrcId() == updateVmId || ch.getDstId() == updateVmId) {
				oldBw = ch.getRequestedBandwidth();
				ch.setReseveredBandwidth(oldBw);
				
				this.updateChannelBandwidth(ch.getSrcId(), ch.getDstId(), ch.getChId(), newBandwidth);
				System.out.println("Channel Updated:"+ch.getSrcId()+"-"+ch.getDstId()+"-"+ch.getChId()+" from"
						+ oldBw +" to "+ newBandwidth);
			}
		}
	}
	
	@Deprecated
	//[NEW] Resume the channels transmission from and to the VM
	protected void resumeVmTransmission(int updateVmId) {
		double oldBw = 0;
		for(Channel ch:this.channelTable.values()) {
			if(ch.getSrcId() == updateVmId || ch.getDstId() == updateVmId) {
				oldBw = ch.getRequestedBandwidth();
				if(oldBw == 0) {
					ch.setReseveredBandwidth(0);
					this.updateChannelBandwidth(ch.getSrcId(), ch.getDstId(), ch.getChId(), (long) oldBw);
					ch.updateRequestedBandwidth(oldBw);
				}		
			}
		}
	}
	
	protected void processVmDestroyAck(SimEvent ev) {
		Vm destroyedVm = (Vm) ev.getData();
		// remove all channels transferring data from or to this vm.
		for(Vm vm:this.allVms.values()) {
			Channel ch = this.findChannel(vm.getId(), destroyedVm.getId(), -1);
			if(ch != null) {
				this.removeChannel(getKey(vm.getId(), destroyedVm.getId(), -1));
			}

			ch = this.findChannel(destroyedVm.getId(), vm.getId(), -1);
			if(ch != null) {
				this.removeChannel(getKey(destroyedVm.getId(), vm.getId(), -1));
			}

		}
		
		sendInternalEvent();
		
	}

	public boolean buildForwardingTable(int srcVm, int dstVm, int flowId) {
		SDNHost srchost = (SDNHost) findHost(srcVm);
		SDNHost dsthost = (SDNHost) findHost(dstVm);
		if(srchost == null || dsthost == null) {
			return false;
		}
		
		if(srchost.equals(dsthost)) {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Source SDN Host is same as Destination. Go loopback");
			srchost.addVMRoute(srcVm, dstVm, flowId, dsthost);
		}
		else {
			Log.printLine(CloudSim.clock() + ": " + getName() + ": VMs are in different hosts:"+ srchost+ "("+srcVm+")->"+dsthost+"("+dstVm+")");
			boolean findRoute = buildForwardingTableRec(srchost, srcVm, dstVm, flowId);
			
			if(!findRoute) {
				System.err.println("NetworkOperatingSystem.deployFlow: Could not find route!!" + 
						NetworkOperatingSystem.debugVmIdName.get(srcVm) + "->"+NetworkOperatingSystem.debugVmIdName.get(dstVm));
				return false;
			}
		}
		
		return true;
	}
	
	private Long getPathLowestBw(List<Node> path) {
		double smallestBw = Double.MAX_VALUE;
		for(int i=0;i<path.size()-1;i++) {
			Link link = this.topology.getLink(path.get(i).getAddress(), path.get(i+1).getAddress());
			long bw = (long) link.getFreeBandwidth(path.get(i));
			if(bw<smallestBw) {
				smallestBw = bw;
			}
		}
		return (long) smallestBw;
	}
	
	private boolean isPathIntersect(Map<List<Node>, Long> multipath, List<Node> check) {
		
		for(Entry<List<Node>, Long> entry:multipath.entrySet()) {
			List<Node> path = entry.getKey();
			List<Node> intersect = new ArrayList<Node>(path);
			intersect.retainAll(check);
			if(intersect.size()>2)
			for(int i=0; i<intersect.size()-1;i++) {
				Node n = intersect.get(i);
				Node m = intersect.get(i+1);
				
				int lower = path.indexOf(n);
				int upper = path.indexOf(m);
				
				int lowerF = check.indexOf(n);
				int upperF = check.indexOf(m);
				
				if(upper-lower<2 && (upper-lower)*(upperF-lowerF)>0)
					return true;
			}
		}
		
		return false;
	}
	
	public Map<List<Node>, Long> getMultiPath(Map<Integer, List<Node>> multipath) {
		Map<List<Node>, Long> selectedPaths = new LinkedHashMap<>();
		Map<List<Node>, Long> paths = new LinkedHashMap<>();
		for(Entry<Integer, List<Node>> entry: multipath.entrySet()) {
			long bw =0;
			if(Configuration.MIG_SHARE_BANDWIDTH == false) {
				bw = (long) this.getPathNonReservedBandwidth(entry.getValue());
			}else {
				bw = this.getPathLowestBw(entry.getValue());
			}			
			if(bw>0)
				paths.put(entry.getValue(), bw);
		}
		//List<Integer> selected = new ArrayList<>();
		// sort Map based on path bw
		Map<List<Node>, Long> sorted = paths.entrySet()
				.stream()
				.sorted(Map.Entry.<List<Node>, Long>comparingByValue().reversed())
				.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue, (e1, e2) -> e1, LinkedHashMap::new));
		
		// add un-intersect paths into multipaths group
		for(Entry<List<Node>, Long> path: sorted.entrySet()) {
			List<Node> checkPath = new ArrayList<>(path.getKey());
			if(Configuration.DEAFULT_ROUTING_WAN==true) {
				checkPath.remove(checkPath.size()-1);
				checkPath.remove(0);
			}
			boolean intersect = this.isPathIntersect(selectedPaths, checkPath);
			if(intersect == false)
				selectedPaths.put(path.getKey(), path.getValue());
		}
		return selectedPaths;
	}
	
	private void deploySubFlowForwardingTable(List<Node> path, int srcVm, int dstVm, int subflowId) {
		//subflow path does not contain src and dst node
		SDNHost srcHost = this.findHost(srcVm);
		SDNHost dstHost = this.findHost(dstVm);
		srcHost.addVMRoute(srcVm, dstVm, subflowId, path.get(0));
		for(int i=0;i<path.size()-1;i++) {
			if(path.get(i).equals(dstHost))
				break;
			path.get(i).addVMRoute(srcVm, dstVm, subflowId, path.get(i+1));
		}
		path.get(path.size()-1).addVMRoute(srcVm, dstVm, subflowId, dstHost);
	}
	
	protected boolean buildForwardingTableRec(Node node, int srcVm, int dstVm, int flowId) {
		//[NEW] There are multiple paths between srcVm and dstVm, selective MPTCP
		/*if(flowId>=10000) {
			this.findAllPath(srcVm, dstVm, flowId);
			Map<Integer, List<Node>> multipath = this.multiPathTable.get(flowId);
			
			//get multiple paths based on available bandwidth
			Map<List<Node>, Long> selected = this.getMultiPath(multipath);
			
			//set subflow rout based on the selected multiple paths			
			if(selected.entrySet().size() > 1) {
				long totalbw = 0;
				for(long bw:selected.values()) {
					totalbw += bw;
				}
				//create arc for subflow of this flowId
				int i=0;
				for(Entry<List<Node>, Long> entry: selected.entrySet()) {
					int subflowId = flowId*10+i;
					long ratio = entry.getValue()/totalbw;
					Arc arc = this.deployFlowIdToArcTable.get(flowId);
					Arc subarc = new Arc(arc.getSrcId(), arc.getDstId(), subflowId, arc.getBw() * ratio, arc.getLatency());
					this.deployFlowIdToArcTable.put(subflowId, subarc);
					i++;
					//deploy subflow forwarding table
					this.deploySubFlowForwardingTable(entry.getKey(), srcVm, dstVm, subflowId);
				}
				return true;
			}
		}*/
		
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = findHost(dstVm);
		if(node.equals(desthost))
			return true;
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, findHost(srcVm), desthost, node);
		Node nextHop = nextLink.getOtherNode(node);
		
		node.addVMRoute(srcVm, dstVm, flowId, nextHop);
		buildForwardingTableRec(nextHop, srcVm, dstVm, flowId);
		
		return true;
	}
	
	public List<Link> getPhysicalRoute(int srcHost, int dstHost){
		List<Link> route = new ArrayList<>();
		Node src = this.datacenter.findHost(srcHost);
		Node dst = this.datacenter.findHost(dstHost);
		Node origin = src;
		List<Link> lList = origin.getRoute(dst);
		while(!origin.equals(dst)) {
			Link l = this.getLinkSelectionPolicy().selectLink(lList, -1, src, dst, origin);
			route.add(l);
			origin = l.getOtherNode(origin);
			lList = origin.getRoute(dst);
		}
		return route;
	}
	
	public List<Node> getVmNodeRoute(int srcVm, int dstVm, int flowId){
		if(this.findVm(srcVm).getId()==this.findVm(dstVm).getId()) {
			return null;
		}
		Channel ch = this.findChannel(srcVm, dstVm, flowId);
		if(ch == null) {
			ch = this.createChannel(srcVm, dstVm, flowId, this.findHost(srcVm));
		}
		return ch.getNodeList();
	}
	
	//[NEW]
	public List<Link> getVmRoute(int srcVm, int dstVm, int flowId){
		if(this.findVm(srcVm).getId()==this.findVm(dstVm).getId()) {
			return null;
		}
		Channel ch = this.findChannel(srcVm, dstVm, flowId);
		if(ch == null) {
			ch = this.createChannel(srcVm, dstVm, flowId, this.findHost(srcVm));
		}
		return ch.getLinkList();
	}
	
	//[NEW]
	public void testAllPath(int srcVm, int dstVm, int flowId) {
		//TODO test findAllPath for multipath transmission of migration
		if(flowId>=Configuration.MIG_CHANNEL_START_ID) {
			this.findAllPath(srcVm, dstVm, flowId);
			Map<Integer, List<Node>> multipath = this.multiPathTable.get(flowId);
			System.out.println("flowId: "+ flowId + " Paths: "+multipath.size());
			if(multipath.size()>1) {
				System.out.println("test");
			}
			for(Entry<Integer, List<Node>> route: multipath.entrySet()) {
				for(Node node :route.getValue()) {
					System.out.print(node+"->");
				}
				System.out.print("\n");
			}
			Map<List<Node>, Long> paths = this.getMultiPath(multipath);
			for(Entry<List<Node>, Long> entry: paths.entrySet()) {
				for(Node n: entry.getKey()) {
					System.out.print(n+"->");
				}
				System.out.print("\n");
				System.out.println("Avaiable BW: "+ entry.getValue());
			}
		}
	}
	
	
	//[NEW]
	Map<Integer, Map<Integer, List<Node>>> multiPathTable = new HashMap<>();
	
	public Map<Integer, Map<Integer, List<Node>>> getMultiPathTable() {
		return this.multiPathTable;
	}
	
	public Map<Integer, List<Node>> findAllPath(int srcVm, int dstVm) {
		SDNHost srcHost = (SDNHost) this.findHost(srcVm);
		SDNHost dstHost = (SDNHost) this.findHost(dstVm);
		Map<Integer, List<Node>> multiPaths = this.getPhysicalTopology().kShortestPath(srcHost, dstHost);
		/*if(Configuration.DEAFULT_ROUTING_WAN==true) {
			//ignore the dc to gateway links
			for(List<Node> p: multiPaths.values()) {
				p.remove(srcHost);
				p.remove(dstHost);
			}
		}*/
		return multiPaths;
	}
	
	public boolean findAllPath(int srcVm, int dstVm, int flowId) {
		//find all simple paths between two nodes
		SDNHost srcHost = (SDNHost) this.findHost(srcVm);
		SDNHost dstHost = (SDNHost) this.findHost(dstVm);
		if(srcHost == null || dstHost == null) {
			return false;
		}
		
		Map<Integer, Boolean> isVisited = new HashMap<>();
		List<Node> routeList = new ArrayList<>();
		
		routeList.add(srcHost);
		
		findAllPathRec(srcHost, srcVm, dstVm, flowId, isVisited, routeList);
		
		//no duplicate path
		Map<Integer, List<Node>> multipath = this.multiPathTable.get(flowId);
		Set<List<Node>> s = new HashSet<>();
		Map<Integer, List<Node>> hashMap = new HashMap<>(multipath);
		for(Entry<Integer, List<Node>> en:hashMap.entrySet()) {
			s.add(en.getValue());
		}
		multipath.clear();
		int i = 0;
		for(List<Node> p:s) {
			multipath.put(i, p);
			i++;
		}
		return true;
	}
	
	//[NEW]
	public boolean findAllPathRec(Node node, int srcVm, int dstVm, int flowId, Map<Integer, Boolean> isVisited, List<Node> localpath) {
		//marked the current node
		Map<Integer, List<Node>> routes;
	    isVisited.put(node.getAddress(), true);
		
		SDNHost destHost = findHost(dstVm);
		//Node dstEdge = destHost.getRoute(findHost(srcVm)).get(0).getOtherNode(findHost(srcVm));
		Node dstEdge = destHost;
		if(node.equals(dstEdge)) {
			routes = multiPathTable.get(flowId);
			int pathNum = 0;;
			if(multiPathTable.get(flowId)==null) {
				routes = new HashMap<>();
				pathNum = 0;
			}else {	
				pathNum = routes.entrySet().size();
			}
			List<Node> route = new ArrayList<>(localpath);
			pathNum++;
			routes.put(pathNum, route);
			multiPathTable.put(flowId, routes);
			isVisited.put(node.getAddress(), false);
			return true;
		}
		
		List<Link> nextLinkCandidates = node.getRoute(destHost);
		if(nextLinkCandidates == null) {
			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+destHost);
		}
		//Recure for all the nodes linked to current node
		for(Link nextLink: nextLinkCandidates) {
			
			Node nextHop = nextLink.getOtherNode(node);
			if(isVisited.get(nextHop.getAddress())==null) {
				isVisited.put(nextHop.getAddress(), false);
			}
			if(!isVisited.get(nextHop.getAddress())) {
				localpath.add(nextHop);
				findAllPathRec(nextHop, srcVm, dstVm, flowId, isVisited, localpath);
				
				//remove current node in path
				localpath.remove(nextHop);
			}			
		}
		
		isVisited.put(node.getAddress(), false);		
		return true;
	}
	
	// This function rebuilds the forwarding table only for the specific VM
	protected void rebuildForwardingTable(int srcVmId, int dstVmId, int flowId, Node srcHost) {
		// Remove the old routes.
		List<Node> oldNodes = new ArrayList<Node>();
		List<Link> oldLinks = new ArrayList<Link>();
		buildNodesLinks(srcVmId, dstVmId, flowId, srcHost, oldNodes, oldLinks);
		
		for(Node node:oldNodes) {
			//System.err.println("Removing routes for: "+node + "("+arc+")");
			node.removeVMRoute(srcVmId, dstVmId, flowId);
		}
		
		// Build a forwarding table for the new route.
		if(buildForwardingTable(srcVmId, dstVmId, flowId) == false) {
			System.err.println("NetworkOperatingSystem.processVmMigrate: cannot build a new forwarding table!!");
			System.exit(0);
		}
	}
	
	private boolean updateDynamicForwardingTableRec(Node node, int srcVm, int dstVm, int flowId, boolean isNewRoute) {
		// There are multiple links. Determine which hop to go.
		SDNHost desthost = findHost(dstVm);
		if(node.equals(desthost))
			return false;	// Nothing changed
		
		List<Link> nextLinkCandidates = node.getRoute(desthost);
		
		if(nextLinkCandidates == null) {
			throw new RuntimeException("Cannot find next links for the flow:"+srcVm+"->"+dstVm+"("+flowId+") for node="+node+", dest node="+desthost);
		}
		
		// Choose which link to follow
		Link nextLink = linkSelector.selectLink(nextLinkCandidates, flowId, findHost(srcVm), desthost, node);
		Node nextHop = nextLink.getOtherNode(node);
		
		Node oldNextHop = node.getVMRoute(srcVm, dstVm, flowId);
		if(isNewRoute || !nextHop.equals(oldNextHop)) {
			// Create a new route
			//node.removeVMRoute(srcVm, dstVm, flowId);
			node.addVMRoute(srcVm, dstVm, flowId, nextHop);
			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Updating VM route for flow:"+srcVm+"->"+dstVm+"("+flowId+") From="+node+", Old="+oldNextHop+", New="+nextHop);
			
			updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, true);
			return true;
		}
		else {
			// Nothing changed for this node.
			return updateDynamicForwardingTableRec(nextHop, srcVm, dstVm, flowId, false);
		}
	}
	
	//TODO [NEW] update subflow forwarding table
	private void updateSubFlowForwardingTable(List<Node> path, int srcVm, int dstVm, int subflowId) {
		//subflow path does not contain src and dst node
				SDNHost srcHost = this.findHost(srcVm);
				SDNHost dstHost = this.findHost(dstVm);
				srcHost.removeVMRoute(srcVm, dstVm, subflowId);
				srcHost.addVMRoute(srcVm, dstVm, subflowId, path.get(0));
				for(int i=0;i<path.size()-1;i++) {
					if(path.get(i).equals(dstHost))
						break;
					path.get(i).removeVMRoute(srcVm, dstVm, subflowId);
					path.get(i).addVMRoute(srcVm, dstVm, subflowId, path.get(i+1));
				}
				path.get(path.size()-1).removeVMRoute(srcVm, dstVm, subflowId);
				path.get(path.size()-1).addVMRoute(srcVm, dstVm, subflowId, dstHost);
	}
	
	
	public Packet addPacketToChannel(Packet orgPkt) {
		Packet pkt = orgPkt;
		/*
		if(sender.equals(sender.getVMRoute(src, dst, flowId))) {
			// For loopback packet (when src and dst is on the same host)
			//Log.printLine(CloudSim.clock() + ": " + getName() + ".addPacketToChannel: Loopback package: "+pkt +". Send to destination:"+dst);
			sendNow(sender.getAddress(),Constants.SDN_PACKAGE,pkt);
			return;
		}
		*/
		
		pkt = sfcForwarder.enforceSFC(pkt);
		updatePacketProcessing();
		
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
		
		// Check if VM is removed by auto-scaling
		if(findVm(src) == null) {
			src = getSFForwarderOriginalVm(src).getId();
			pkt.changeOrigin(src); 
		}		
		if(findVm(dst) == null) {
			dst = getSFForwarderOriginalVm(dst).getId();
			pkt.changeDestination(dst); 
		}
		
		/*
		if(pkt.getFlowId()>=Configuration.MIG_CHANNEL_START_ID) {
			//[NEW] create subflow Channel for migration transmission
			int flowNum = 0;
			for(int i =0; i<Configuration.MPTCP_SUBFLOW_MAX; i++) {
				Arc arc = this.deployFlowIdToArcTable.get(pkt.getFlowId()*Configuration.MPTCP_SUBFLOW_MAX+i);
				if(arc == null) {
					flowNum = i;
					break;
				}
			}
			if(flowNum>=0) {
				//this.findAllPath(src, dst, flowId);
				// delete current arcs, then create new arcs
				Map<Integer, List<Node>> multipath = this.multiPathTable.get(flowId);
				
				//get multiple paths based on available bandwidth
				Map<List<Node>, Long> selected = this.getMultiPath(multipath);				
				
				Map<Integer, Integer> subFlowMap = this.getMultiFlowCheckMap().get(flowId);
				if(subFlowMap == null) {
					subFlowMap = new HashMap<>();
				}
				int subflowIdToken = subFlowMap.entrySet().size()+1;
				subFlowMap.put(subflowIdToken, selected.size());
				this.getMultiFlowCheckMap().put(flowId, subFlowMap);
				
				//set subflow route based on the selected multiple paths			
				if(selected.entrySet().size() >= 1) {
					long totalbw = 0;
					
					for(long bw:selected.values()) {
						totalbw += bw;
					}
					
					//create arc for subflow of this flowId
					int token=0;
					Arc arc = this.deployFlowIdToArcTable.get(flowId);
					arc.updateReqiredBandwidth(totalbw);
					for(Entry<List<Node>, Long> entry: selected.entrySet()) {
						int subflowId = flowId*Configuration.MPTCP_SUBFLOW_MAX*1000
								+Configuration.MPTCP_SUBFLOW_MAX*subflowIdToken + token;
						long bw = entry.getValue();
						double ratio = (double) bw/totalbw;
						//1st method load balancing the main flow
						Arc subarc = new Arc(arc.getSrcId(), arc.getDstId(), subflowId, (long) (arc.getBw() * ratio), arc.getLatency());
						//2nd method allocate all available bw to subflow
						//Arc subarc = new Arc(arc.getSrcId(), arc.getDstId(), subflowId, bw, arc.getLatency());
						this.deployFlowIdToArcTable.put(subflowId, subarc);
						token++;
						//deploy subflow forwarding table
						this.updateSubFlowForwardingTable(entry.getKey(), pkt.getOrigin(), pkt.getDestination(), subflowId);
						
						//create channel and send transmission
						Channel channel = findChannel(src, dst, subflowId);
						if(channel == null) {
							//No subflow channel established, create a new one
							SDNHost sender = findHost(src);
							channel = createChannel(src, dst, subflowId, sender);
							
							Packet subpkt = new Packet(pkt.getOrigin(), pkt.getDestination(), (long)(pkt.getSize()*ratio), subflowId, pkt.getPayload(), pkt);
							subpkt.setTotalFlowNum(selected.size());
							
							if(channel == null) {
								// failed to create channel
								System.err.println("ERROR!! Cannot create subflow channel!" + subpkt);
								return pkt;
							}
							addChannel(src, dst, subflowId, channel);
							channel.addTransmission(new Transmission(subpkt));
							
							sendInternalEvent();
						}
					}
					
					//remove the extra subflow arcs
					if(flowNum>selected.size()) {
						//delete extra subflow arcs
						for(int i = selected.size();i<flowNum; i++) {
							int subflowId = flowId*Configuration.MPTCP_SUBFLOW_MAX+i;
							this.deployFlowIdToArcTable.remove(subflowId);
						}
					}
				}else {
					//just one flow no need to create multiple flows
					Channel channel = findChannel(src, dst, flowId);
					if(channel == null) {
						//No channel established. Create a new channel.
						SDNHost sender = findHost(src);
						channel = createChannel(src, dst, flowId, sender);
						
						if(channel == null) {
							// failed to create channel
							System.err.println("ERROR!! Cannot create channel!" + pkt);
							return pkt;
						}
						addChannel(src, dst, flowId, channel);
					}
					
					channel.addTransmission(new Transmission(pkt));
					sendInternalEvent();
				}
			}
		}else {
			Channel channel = findChannel(src, dst, flowId);
			//if(src==1801 && dst==1800) {
			//	System.out.println("DEBUG");
			//}
			if(channel == null) {
				//No channel established. Create a new channel.
				SDNHost sender = findHost(src);
				channel = createChannel(src, dst, flowId, sender);
				
				if(channel == null) {
					// failed to create channel
					System.err.println("ERROR!! Cannot create channel!" + pkt);
					return pkt;
				}
				addChannel(src, dst, flowId, channel);
			}
			
			channel.addTransmission(new Transmission(pkt));
//			Log.printLine(CloudSim.clock() + ": " + getName() + ".addPacketToChannel ("+channel
//					+"): Transmission added:" + 
//					NetworkOperatingSystem.debugVmIdName.get(src) + "->"+
//					NetworkOperatingSystem.debugVmIdName.get(dst) + ", flow ="+flowId + " / eft="+eft);

			sendInternalEvent();
		}	
		*/
		
		Channel channel = findChannel(src, dst, flowId);
		if(channel == null) {
			//No channel established. Create a new channel.
			SDNHost sender = findHost(src);
			channel = createChannel(src, dst, flowId, sender);
			
			if(channel == null) {
				// failed to create channel
				System.err.println("ERROR!! Cannot create channel!" + pkt);
				return pkt;
			}
			addChannel(src, dst, flowId, channel);
		}
		
		channel.addTransmission(new Transmission(pkt));

		sendInternalEvent();
		
		return pkt;
	}
	
	
	public double getRequestedBandwidth(Packet pkt) {
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
		Channel channel=findChannel(src, dst, flowId);
		double bw = channel.getRequestedBandwidth();
		
		return bw;
	}
	

	private void internalPacketProcess() {
		if(updatePacketProcessing()) {
			sendInternalEvent();
		}
	}
	
	private double nextEventTime = -1;
	
	private void sendInternalEvent() {
		if(channelTable.size() != 0) {
			if(nextEventTime == CloudSim.clock() + NetworkOperatingSystem.getMinTimeBetweenNetworkEvents())
				return;
			
			// More to process. Send event again
			double delay = this.nextFinishTime();

			// Shape the delay
			delay=NetworkOperatingSystem.round(delay);

			if (delay < NetworkOperatingSystem.getMinTimeBetweenNetworkEvents()) { 
				//Log.printLine(CloudSim.clock() + ":Channel: delay is too short: "+ delay);
				delay = NetworkOperatingSystem.getMinTimeBetweenNetworkEvents();
			}

			//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): delay for next event="+ delay);

			if((nextEventTime > CloudSim.clock() + delay) || nextEventTime <= CloudSim.clock() ) 
			{
				//Log.printLine(CloudSim.clock() + ": " + getName() + ".sendInternalEvent(): next event time changed! old="+ nextEventTime+", new="+(CloudSim.clock()+delay));
				
				CloudSim.cancelAll(getId(), new PredicateType(Constants.SDN_INTERNAL_PACKET_PROCESS));
				send(this.getId(), delay, Constants.SDN_INTERNAL_PACKET_PROCESS);
				nextEventTime = CloudSim.clock()+delay;
			}
		}
	}
	
	private double nextFinishTime() {
		double earliestEft = Double.POSITIVE_INFINITY;
		for(Channel ch:channelTable.values()){
			if(ch.getActiveTransmissionNum()==0 && Configuration.CHANNEL_UPDATE_NO_TRANSMISSION_DELTE==false) {}
			else{
			double eft = ch.nextFinishTime();
			if (eft<earliestEft){
				earliestEft=eft;
			}
			}
		}
		
		if(earliestEft == Double.POSITIVE_INFINITY) {
			throw new IllegalArgumentException("NOS.nextFinishTime(): next finish time is infinite!");
		}
		return earliestEft;
		
	}
	
	private boolean updatePacketProcessing() {
		boolean needSendEvent = false;
		
		LinkedList<Channel> completeChannels = new LinkedList<Channel>();
		
		// Check every channel
		for(Channel ch:channelTable.values()){
			boolean isCompleted = ch.updatePacketProcessing();
			
			if(isCompleted) {
				completeChannels.add(ch);
				
				if(ch.getActiveTransmissionNum() != 0)
				{
					// There are more transmissions even after completing these transmissions.
					needSendEvent = true;
				}

			} else {
				// Something is not completed. Need to send an event. 
				needSendEvent = true;
			}
		}
		
		if(completeChannels.size() != 0) {
			processCompletePackets(completeChannels);
			updateChannel();
		}

		return needSendEvent;
	}
	
	private Map<Integer, Map<Integer,Integer>> multiflowCheckMap = new HashMap<>();
	//origin FlowId, <token, sub #>
	
	public Map<Integer, Map<Integer, Integer>> getMultiFlowCheckMap() {
		return this.multiflowCheckMap;
	}
	
	private void processCompletePackets(List<Channel> channels){
		//TODO IF the srcVM or dstVm is paused VM, re-send the packet

		for(Channel ch:channels) {
			for (Transmission tr:ch.getArrivedPackets()){
				Packet pkt = tr.getPacket();
				//Node sender = pkgTable.remove(pkt);
				//Node nextHop = sender.getRoute(pkt.getOrigin(),pkt.getDestination(),pkt.getFlowId());
				
				//[NEW] pkt need to be dropped when VM is PAUSED.
				if( (this.findVm(pkt.getOrigin()).isInPause() && this.findVm(pkt.getOrigin()).isInMigration()) 
						|| (this.findVm(pkt.getDestination()).isInPause() && this.findVm(pkt.getDestination()).isInMigration()) ) {
					System.out.println(CloudSim.clock()+": "+ getName()+ ": Packet: "+ pkt.getPacketId() + " dropped Channel("
						+pkt.getOrigin()+"-"+pkt.getDestination()+"|"+ pkt.getFlowId() + ") VM in stop and copy phase!!");
					//double bw = ch.getAllocatedBandwidth();
					//double rrt = (pkt.getSize()/bw+ch.getTotalLatency())*2;
					send(this.datacenter.getId(), Configuration.RETRANSMISSION_TIMEOUT, Constants.SDN_PACKET_DROPPED, pkt);
				}else if(pkt.getTotalFlowNum()>0){
					//processing the completion of subflow
					send(this.datacenter.getId(), ch.getTotalLatency(), Constants.SDN_PACKET_SUBFLOW_COMPLETE, pkt);
				}else{
				//Log.printLine(CloudSim.clock() + ": " + getName() + ": Packet completed: "+pkt +". Send to destination:"+ch.getLastNode());
				send(this.datacenter.getId(), ch.getTotalLatency(), Constants.SDN_PACKET_COMPLETE, pkt);
				}
			}
			
			for (Transmission tr:ch.getFailedPackets()){
				Packet pkt = tr.getPacket();
				System.out.println("packet failed Channel No"+ch.getChId()+"from"+ch.getSrcId()+"to"+ch.getDstId());
				send(this.datacenter.getId(), ch.getTotalLatency(), Constants.SDN_PACKET_FAILED, pkt);
				
			}
		}
	}
	
	public Map<String, Integer> getVmNameIdTable() {
		return this.deployVmNameToIdTable;
	}
	public Map<String, Integer> getFlowNameIdTable() {
		return this.deployFlowNameToIdTable;
	}
	
	public Channel findChannel(int from, int to, int channelId) {
		// check if there is a pre-configured channel for this application
		Channel channel=channelTable.get(getKey(from, to, channelId));

		if (channel == null) {
			//there is no channel for specific flow, find the default channel for this link
			channel=channelTable.get(getKey(from,to));
		}
		return channel;
	}
	
	private void addChannel(int src, int dst, int chId, Channel ch) {
		//System.err.println("NOS.addChannel:"+getKey(src, dst, chId));
		
		this.channelTable.put(getKey(src, dst, chId), ch);
		ch.initialize();
		
		ch.adjustDedicatedBandwidthAlongLink();
		ch.adjustSharedBandwidthAlongLink();
		
		sendAdjustAllChannel();		
//		allChannels.add(ch);
	}
	
	private Channel removeChannel(String key) {
		//System.err.println("NOS.removeChannel:"+key);
		Channel ch = this.channelTable.remove(key);
		ch.terminate();
		sendAdjustAllChannel();
		tempRemovedChannels.add(ch);
		return ch;
	}
	
	//[NEW]
	public void updateArcBandwidth(int src, int dst, int flowId, long newBandwidth) {
		for(Arc arc:this.allArcs) {
			if(arc.getSrcId()==src && arc.getDstId()==dst && arc.getFlowId() == flowId) {
				arc.updateReqiredBandwidth(newBandwidth);
				break;
			}
		}
	}
	
	public void updateChannelBandwidth(int src, int dst, int flowId, long newBandwidth) {
		Channel ch = this.channelTable.get(getKey(src, dst, flowId));
		if(ch != null) {
			ch.updateRequestedBandwidth(newBandwidth);

			// As the requested bandwidth updates, find alternative path if the current path cannot provide the new bandwidth.
			SDNHost sender = findHost(src);
			updateDynamicForwardingTableRec(sender, src, dst, flowId, false);

			sendAdjustAllChannel();
		}
	}
	
	private double lastAdjustAllChannelTime = -1;
	
	private void sendAdjustAllChannel() {
		if(CloudSim.clock() != lastAdjustAllChannelTime) {
			send(getId(), 0, Constants.SDN_INTERNAL_CHANNEL_PROCESS);
			lastAdjustAllChannelTime = CloudSim.clock();
		}
	}
	
	private void adjustAllChannels() {
		for(Channel ch:this.channelTable.values()) {
			if(ch.adjustDedicatedBandwidthAlongLink()) {
				// Channel BW is changed. send event.
			}
		}
		
		for(Channel ch:this.channelTable.values()) {
			if(ch.adjustSharedBandwidthAlongLink()) {
				// Channel BW is changed. send event.
			}
		}
	}

	/**
	 * Gets the list of nodes and links that a channel will pass through.
	 * 
	 * @param src source VM id
	 * @param dst destination VM id
	 * @param flowId flow id
	 * @param srcNode source node (host of src VM)
	 * @param nodes empty list to get return of the nodes on the route
	 * @param links empty list to get return of the links on the route
	 * @return none
	 * @pre $none
	 * @post $none
	 */
	private void buildNodesLinks(int src, int dst, int flowId, Node srcNode,
			List<Node> nodes, List<Link> links) {
		
		// Build the list of nodes and links that this channel passes through
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest==null) {
			System.err.println("buildNodesLinks() Cannot find dest!");
			return;	
		}

		nodes.add(origin);

		while(dest != null) {
			Link link = this.topology.getLink(origin.getAddress(), dest.getAddress());
			
			links.add(link);
			nodes.add(dest);
			
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		}
	}
	
	Map<Link, List<Double>> reservedLinkBwMap = new HashMap<>();
	
	public Map<Link, List<Double>> getLinkFreeBandwidth(){
		Map<Link, List<Double>> freeLinkBwMap = new HashMap<>();
		for(Link l: this.topology.getAllLinks()) {
			Node lowN = l.getLowOrder();
			Node highN = l.getHighOrder();
			double upbw = l.getFreeBandwidth(lowN);
			double downbw = l.getFreeBandwidth(highN);
			List<Double> bwList = new ArrayList<>();
			bwList.add(upbw);
			bwList.add(downbw);
			freeLinkBwMap.put(l, bwList);
		}
		return freeLinkBwMap;
	}
	
	public Map<Link, List<Double>> getLinkReservedBandwidth(){
		Map<Link, List<Double>> reservedLinkBwMap = new HashMap<>();
		for(Arc arc: this.allArcs) {
			//check if the arc is default one
			if(arc.getName() != null)
			if(arc.getName().indexOf("default") == -1 && arc.getFlowId()<Configuration.MIG_CHANNEL_START_ID) { //if not default
				SDNHost srcHost = this.findHost(arc.getSrcId());
				SDNHost dstHost = this.findHost(arc.getDstId());
				SDNHost srcHost_c = (SDNHost) this.datacenter.findVm(arc.getSrcId()).getHost();
				SDNHost dstHost_c = (SDNHost) this.datacenter.findVm(arc.getDstId()).getHost();
				List<Link> hostRoute = srcHost.getRoute(dstHost); // this only get the next node not the whole route
				Channel ch = this.findChannel(arc.getSrcId(), arc.getDstId(), arc.getFlowId());
				if(ch==null) {
					ch = this.createChannel(arc.getSrcId(), arc.getDstId(), arc.getFlowId(), srcHost);
				}
				List<Link> route = ch.getLinkList();
				
				double reservedBw = arc.getBw();
				Node origin = srcHost;
				for(int i =0; i< route.size(); i++) {
					Link l = route.get(i);
					Node next = l.getOtherNode(origin);
					List<Double> bwList = reservedLinkBwMap.get(l);
					Double upbw;
					Double downbw;
					if(bwList == null){
						bwList = new ArrayList<>();
						upbw = l.getBw();
						downbw = l.getBw();
						bwList.add(upbw); //low->high
						bwList.add(downbw); //high->low
					}else {
						upbw = bwList.get(0);
						downbw = bwList.get(1);
					}
					if(origin.equals(l.getLowOrder())) {
						Double bw = upbw-reservedBw;
						bwList.set(0, bw);
					}else {
						Double bw = downbw-reservedBw;
						bwList.set(1, bw);
					}
					reservedLinkBwMap.put(l, bwList);
					origin = next;
				}
			}
		}
		for(Link l: this.topology.getAllLinks()) {
			List<Double> bwList = reservedLinkBwMap.get(l);
			if(bwList == null) {
				bwList = new ArrayList<>();
				bwList.add(l.getBw());
				bwList.add(l.getBw());
			}
			reservedLinkBwMap.put(l, bwList);
		}
		return reservedLinkBwMap;
	}
	
	public double getPathBandwidth(List<Node> path, Map<Link, List<Double>> linktoBwMap) {
		double availBw = 0;
		//SDNHost srcHost = (SDNHost) path.get(0);
		Double lowestBw = Double.MAX_VALUE;
		Node origin = path.get(0);
		for(int i=0; i<path.size()-1; i++) {	
			Link l = this.topology.getLink(path.get(i).getAddress(), path.get(i+1).getAddress());
			Node next = l.getOtherNode(origin);
			List<Double> bwList = linktoBwMap.get(l);
			if(l.getLowOrder().getAddress() == origin.getAddress()) {
				Double upbw = bwList.get(0);
				if(lowestBw > upbw) {lowestBw = upbw;}
			}else {
				Double downbw = bwList.get(1);
				if(lowestBw > downbw) {lowestBw = downbw;}
			}
			origin = next;
		}
		availBw += lowestBw;
		if(availBw < 0) {
			System.out.println("No more free bw: " + availBw);
			availBw = 0;
		}
		return availBw;
	}
	
	public double getPathNonReservedBandwidth(List<Node> path) {
		double availBw = 0;
		this.reservedLinkBwMap = this.getLinkReservedBandwidth();
		//SDNHost srcHost = (SDNHost) path.get(0);
		Double lowestBw = Double.MAX_VALUE;
		Node origin = path.get(0);
		for(int i=0; i<path.size()-1; i++) {	
			Link l = this.topology.getLink(path.get(i).getAddress(), path.get(i+1).getAddress());
			Node next = l.getOtherNode(origin);
			List<Double> bwList = this.reservedLinkBwMap.get(l);
			if(l.getLowOrder().getAddress() == origin.getAddress()) {
				Double upbw = bwList.get(0);
				if(lowestBw > upbw) {lowestBw = upbw;}
			}else {
				Double downbw = bwList.get(1);
				if(lowestBw > downbw) {lowestBw = downbw;}
			}
			origin = next;
		}
		availBw += lowestBw;
		if(availBw < 0) {
			System.out.println("No more non reserved bw: " + availBw);
			availBw = 0;
		}
		return availBw;
	}
	
	//TODO [NEW] get all free available bandwidth of multiple paths from source to destination node
	public double getFreeBandwidth(int srcVm, int dstVm, int flowId) {
		double availBw = 0;
		this.findAllPath(srcVm, dstVm, flowId);
		// delete current arcs, then create new arcs
		Map<Integer, List<Node>> multipath = this.multiPathTable.get(flowId);
		
		//get multiple paths based on available bandwidth
		Map<List<Node>, Long> selected = this.getMultiPath(multipath);				
		
		//this.multiflowCheckMap.put(flowId, selected.size());
		//set subflow route based on the selected multiple paths
		
		if(selected.entrySet().size() >= 1) {
			if(flowId >= Configuration.MIG_CHANNEL_START_ID) {
				for(List<Node> route: selected.keySet()) {
				//migration flow: use ArcList to check non reserved bw for migration
				//consider current route from src Host to dst　Host
				this.reservedLinkBwMap = this.getLinkReservedBandwidth();
				SDNHost srcHost = this.findHost(srcVm);
				//SDNHost dstHost = this.findHost(dstVm);
				Double lowestBw = Double.MAX_VALUE;
				Node origin = srcHost;
				for(int i=0; i<route.size()-1; i++) {	
					Link l = this.topology.getLink(route.get(i).getAddress(), route.get(i+1).getAddress());
					Node next = l.getOtherNode(origin);
					List<Double> bwList = this.reservedLinkBwMap.get(l);
					if(l.getLowOrder().getAddress() == origin.getAddress()) {
						Double upbw = bwList.get(0);
						if(lowestBw > upbw) {lowestBw = upbw;}
					}else {
						Double downbw = bwList.get(1);
						if(lowestBw > downbw) {lowestBw = downbw;}
					}
					origin = next;
				}
				availBw += lowestBw;
				}
			}else {
				//application flow
				Set<List<Node>> paths= selected.keySet();
				for(List<Node> p : paths) {availBw += this.getPathLowestBw(p);}
			}
		}
		//SDNHost srcHost = this.findHost(srcVm);
		//SDNHost dstHost = this.findHost(dstVm);
		
		//double hostCap = this.getHostInterfaceCap(srcHost.getId(), dstHost.getId());
		//if(availBw > hostCap)
		//	availBw = hostCap;
		return availBw;
	}
	
	public double getHostInterfaceCap(int srcHost, int dstHost) {
		double cap = 0;
		double totalsrc = 0;
		double totaldst = 0;
		SDNHost src = this.datacenter.findHost(srcHost);
		SDNHost dst = this.datacenter.findHost(dstHost);
		Collection<Link> links1 = this.getPhysicalTopology().getAdjacentLinks(src);
		Collection<Link> links2 = this.getPhysicalTopology().getAdjacentLinks(dst);
		for(Link l:links1) {
			totalsrc += l.getFreeBandwidth(src);
		}
		for(Link l:links2) {
			totaldst += l.getFreeBandwidth(l.getHighOrder());
		}

		cap = Math.min(totalsrc, totaldst);
		return cap;
	}
	
	private Channel createChannel(int src, int dst, int flowId, Node srcNode) {
		// For dynamic routing, rebuild forwarding table (select which link to use).
		//if(linkSelector.isDynamicRoutingEnabled() && flowId<Configuration.MIG_CHANNEL_START_ID)
		if(linkSelector.isDynamicRoutingEnabled())
			updateDynamicForwardingTableRec(srcNode, src, dst, flowId, false);
		
		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		//[NEW] robust bug for forwarding table error
		if(dest == null) {
			System.out.println("something wrong with the forwarding rules");
			updateDynamicForwardingTableRec(srcNode, src, dst, flowId, false);
			dest = origin.getVMRoute(src, dst, flowId);
		}
		
		if(dest==null) {
			throw new IllegalArgumentException("createChannel(): dest is null, cannot create channel! "+findVm(src)+"->"+findVm(dst)+"|"+flowId);
		}
		
		double lowestBw = Double.POSITIVE_INFINITY;
		double reqBw = 0;
		if(flowId != -1) {
			Arc flow = deployFlowIdToArcTable.get(flowId);
			reqBw = flow.getBw();
			if(reqBw == 0)
				throw new RuntimeException("reqBW cannot be zero for dedicated channels!!"+flow);
		}
		
		nodes.add(origin);
		
		// Find the lowest available bandwidth along the link.
		while(true) {
			Link link = this.topology.getLink(origin.getAddress(), dest.getAddress());
			//Log.printLine(CloudSim.clock() + ": createChannel() :(" +getKey(src,dst,flowId)+"): "+link);
			
			links.add(link);
			nodes.add(dest);
			double originBwCheck = link.getFreeBandwidth(origin);
			if(lowestBw > originBwCheck) {
				lowestBw = originBwCheck;
			}
		
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		} 
		
		// If currently free bandwidth is less than required one.
		if(flowId != -1 && lowestBw < reqBw) {
			// Cannot make channel.
			System.out.println(CloudSim.clock() + ": " + getName() + ": Free bandwidth is less than required.("+getKey(src,dst,flowId)+"): ReqBW="+ reqBw + "/ Free="+lowestBw);
			//Log.printLine(CloudSim.clock() + ": " + getName() + ": Free bandwidth is less than required.("+getKey(src,dst,flowId)+"): ReqBW="+ reqBw + "/ Free="+lowestBw);
			//return null;
		}
		
		Channel channel=new Channel(flowId, src, dst, nodes, links, reqBw, 
				(SDNVm)findVm(src), (SDNVm)findVm(dst));
		//Log.printLine(CloudSim.clock() + ": " + getName() + ".createChannel:"+channel);

		return channel;
	}
	
	//TODO [NEW]
	public double getLowestLinkBw(int src, int dst, int flowId) {
		
		//SDNHost srcNode = findHost(src);
		Vm srcVm = findVm(src);
		SDNHost srcNode = (SDNHost) srcVm.getHost();
		
		List<Node> nodes = new ArrayList<Node>();
		List<Link> links = new ArrayList<Link>();
		
		Node origin = srcNode;
		Node dest = origin.getVMRoute(src, dst, flowId);
		
		if(dest == null) {
			throw new IllegalArgumentException("createChannel(): dest is null, cannot create channel! "+findVm(src)+"->"+findVm(dst)+"|"+flowId);
		}
		
		double lowestBw = Double.POSITIVE_INFINITY;
		double reqBw = 0;
		
		if(flowId != -1) {
			Arc flow = deployFlowIdToArcTable.get(flowId);
			reqBw = flow.getBw();
			if(reqBw == 0) {
				throw new RuntimeException("reqBw cannot be zero for dedicated channels!!"+flow);
			}
		}
		
		nodes.add(origin);
		
		// Find the lowest available bandwidth along the link.
		while(true) {
			Link link = this.topology.getLink(origin.getAddress(), dest.getAddress());
			//Log.printLine(CloudSim.clock() + ": createChannel() :(" +getKey(src,dst,flowId)+"): "+link);
			
			links.add(link);
			nodes.add(dest);
			
			if(lowestBw > link.getFreeBandwidth(origin)) {
				lowestBw = link.getFreeBandwidth(origin);
			}
		
			if(dest instanceof SDNHost)
				break;
			
			origin = dest;
			dest = origin.getVMRoute(src, dst, flowId);
		}
			
		return lowestBw;
	}
	
	private void updateChannel() {
		List<String> removeCh = new ArrayList<String>();  
		for(String key:this.channelTable.keySet()) {
			Channel ch = this.channelTable.get(key);
			if(ch.getActiveTransmissionNum() == 0) {
				// No more job in channel. Delete
				if(ch.getChId() >= Configuration.MIG_CHANNEL_START_ID) {
					removeCh.add(key);
				}else {
				if(Configuration.CHANNEL_UPDATE_NO_TRANSMISSION_DELTE)
					removeCh.add(key);
				}
			}
		}
		
		for(String key:removeCh) {
			removeChannel(key);
		}
	}
	
	private void migrateChannel(Vm vm, SDNHost oldHost, SDNHost newHost) {
		for(Channel ch:channelTable.values()) {
			if(ch.getSrcId() == vm.getId()
					|| ch.getDstId() == vm.getId()) {
				List<Node> nodes = new ArrayList<Node>();
				List<Link> links = new ArrayList<Link>();

				SDNHost sender = findHost(ch.getSrcId());	// After migrated
				
				buildNodesLinks(ch.getSrcId(), ch.getDstId(), 
						ch.getChId(), sender, nodes, links);
				
				// update with the new nodes and links
				ch.updateRoute(nodes, links);			
			}
		}
	}
	
	public void startAutoScale() {
		sfcScaler.scaleSFC();
	}	

	public void addExtraVm(SDNVm vm) {
		allVms.put(vm.getId(), vm);
		
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Add extra VM #" + vm.getId()
			+ " in " + datacenter.getName() + ", (" + vm.getStartTime() + "~" +vm.getFinishTime() + ")");
		
		send(datacenter.getId(), vm.getStartTime(), Constants.SDN_VM_CREATE_DYNAMIC, vm);
	}
	

	public void removeExtraVm(SDNVm vm) {
		allVms.remove(vm.getId());
		
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Remove extra VM #" + vm.getId()
			+ " in " + datacenter.getName() + ", (" + vm.getStartTime() + "~" +vm.getFinishTime() + ")");
		
		send(datacenter.getId(), vm.getStartTime(), CloudSimTags.VM_DESTROY, vm);
	}
	
	public void addExtraPath(int orgVmId, int newVmId) {
		List<Arc> newArcList = new ArrayList<Arc>();
		// This function finds all ARCs involving orgVmId and add another virtual path for newVmId. 
		for(Arc arc:allArcs) {
			int srcId = arc.getSrcId();
			int dstId = arc.getDstId();
			int flowId = arc.getFlowId();
			
			// Find an Arc including the original VM
			if( srcId == orgVmId || dstId == orgVmId )
			{
				// Replace the source or destination with the new VM
				if(srcId == orgVmId)
					srcId = newVmId;
				if(dstId == orgVmId)
					dstId = newVmId;
				if(findVm(srcId) == null || findVm(dstId) == null)
					continue;
				
				Arc extraArc = new Arc(srcId, dstId, flowId, arc.getBw(), arc.getLatency());
				newArcList.add(extraArc);
				
				if(buildForwardingTable(srcId, dstId, flowId) == false) {
					throw new RuntimeException("Cannot build a forwarding table!");
				}
			}
		}
		allArcs.addAll(newArcList);

	}
	
	public void updateVmMips(SDNVm orgVm, int newPe, double newMips) {
		Host host = orgVm.getHost();
		this.datacenter.getVmAllocationPolicy().deallocateHostForVm(orgVm);
		
		orgVm.updatePeMips(newPe, newMips);
		if(!this.datacenter.getVmAllocationPolicy().allocateHostForVm(orgVm, host)) {
			System.err.println("ERROR!! VM cannot be resized! "+orgVm+" (new Pe "+newPe+", Mips "+newMips+") in host: "+host);
			System.exit(-1);
		}
	}
	
	public long getRequestedBandwidth(int srcVm, int dstVm, int flowId) {
		if(flowId == -1) {
			return 0L;
		}
		
		Arc flow = deployFlowIdToArcTable.get(flowId);
		return flow.getBw();
	}
	
	public void updateBandwidthArc(int srcVm, int dstVm, int flowId, long newBw) {
		if(flowId == -1) {
			return;
		}
		
		Arc flow = deployFlowIdToArcTable.get(flowId);
		if(newBw > flow.getBw())
			flow.updateReqiredBandwidth(newBw);
	}

	public static String getKey(int origin, int destination) {
		return origin+"-"+destination;
	}
	
	public static String getKey(int origin, int destination, int appId) {
		return getKey(origin,destination)+"-"+appId;
	}


	public void setDatacenter(SDNDatacenter dc) {
		this.datacenter = dc;
	}

//	public List<Host> getHostList() {
//		return this.hosts;		
//	}
//
//	public List<SDNHost> getSDNHostList() {
//		return this.sdnhosts;		
//	}

	public List<Switch> getSwitchList() {
		return this.allSwitches;
	}

	public boolean isApplicationDeployed() {
		return isApplicationDeployed;
	}

	public Vm findVm(int vmId) {
		return allVms.get(vmId);
	}
	protected SDNHost findHost(int vmId) {
		Vm vm = findVm(vmId);
		return (SDNHost)this.datacenter.getVmAllocationPolicy().getHost(vm);
	}
	
//	protected SDNHost findSDNHost(Host host) {
//		for(SDNHost sdnhost:sdnhosts) {
//			if(sdnhost.equals(host)) {
//				return sdnhost;
//			}
//		}
//		return null;
////	}
//	protected SDNHost findSDNHost(int vmId) {
//		Vm vm = findVm(vmId);
//		if(vm == null)
//			return null;
//		
//		for(SDNHost sdnhost:sdnhosts) {
//			if(sdnhost.equals(vm.getHost())) {
//				return sdnhost;
//			}
//		}
//		//System.err.println("NOS.findSDNHost: Host is not found for VM:"+ vmId);
//		return null;
//	}
//	
//	public int getHostAddressByVmId(int vmId) {
//		Vm vm = findVm(vmId);
//		if(vm == null) {
//			Log.printLine(CloudSim.clock() + ": " + getName() + ": Cannot find VM with vmId = "+ vmId);
//			return -1;
//		}
//		
//		Host host = vm.getHost();
//		SDNHost sdnhost = findSDNHost(host);
//		if(sdnhost == null) {
//			Log.printLine(CloudSim.clock() + ": " + getName() + ": Cannot find SDN Host with vmId = "+ vmId);
//			return -1;
//		}
//		
//		return sdnhost.getAddress();
//	}
	
	public abstract SDNHost createHost(int ram, long bw, long storage, long pes, double mips);
	
	protected void initPhysicalTopology() {
		this.topology = new PhysicalTopology();
//		this.hosts = new ArrayList<Host>();
		this.allHosts = new ArrayList<SDNHost>();
		this.allSwitches= new ArrayList<Switch>();
		
		PhysicalTopologyParser parser = new PhysicalTopologyParser(this.physicalTopologyFileName, this);
		
		for(SDNHost sdnHost: parser.getHosts()) {
			topology.addNode(sdnHost);
//			this.hosts.add(sdnHost);
			this.allHosts.add(sdnHost);
		}
		
		for(Switch sw:parser.getSwitches()) {
			topology.addNode(sw);
			this.allSwitches.add(sw);
		}

		for(Link link:parser.getLinks()) {
			Node highNode = link.getHighOrder();
			Node lowNode = link.getLowOrder();
			
			if(highNode.getRank() > lowNode.getRank()) {
				Node temp = highNode;
				highNode = lowNode;
				lowNode = temp;
			}
			double latency = link.getLatency();
			if(!topology.linkTable.contains(highNode.getAddress(), lowNode.getAddress())){
			if(link.getBw()>0) {
				topology.addLink(highNode, lowNode, latency, link.getBw(), link.getBw());
			}else {
				topology.addLink(highNode, lowNode, latency);
			}}
			
		}
		
		
		this.nameNodeTable = parser.getNameNodeTable();
		this.nodeNameTable = parser.getNodeNameTable();
		topology.setNameNodeTable(this.nameNodeTable);
		topology.setNodeNameTable(this.nodeNameTable);
		
		if(Configuration.DEFAULT_ROUTING_FATTREE == true) {
			topology.buildDefaultRoutingFatTree();
		}else {
			if(Configuration.DEAFULT_ROUTING_WAN == true) {
				if(Configuration.DEAFULT_ROUTING_EDGE == true) {
					if(Configuration.DEAFULT_ROUTING_EDGE_ALL == true)
						topology.buildDefaultRoutingEdgeAll();
					else
						topology.buildDefaultRoutingEdge();
				}else {
					topology.buildDefaultRoutingWAN();
				}
			}else{
				topology.buildDefaultRouting();
			}
			
		}
		
		
	}
	
	public Map<String, Node> getNameNodeTable(){
		return this.nameNodeTable;
	}
	
	public boolean deployApplication(int userId, String vmsFileName){
		LinkedList<Middlebox> mbList = new LinkedList<Middlebox>();
		deployVmNameToIdTable = new HashMap<String, Integer>();
		deployFlowIdToArcTable = new HashMap<Integer, Arc>();
		deployFlowNameToIdTable = new HashMap<String, Integer>();
		deployFlowNameToIdTable.put("default", -1);
		
		VirtualTopologyParserTest parser = new VirtualTopologyParserTest(vmsFileName, userId);
		for(SDNVm vm:parser.getVmList()) {
			
			// Middle-boxes are treated as a VM.
			// Modify here if middle-boxes need to be deployed by a separate algorithm 
			if(vm.getMiddleboxType() != null ) {
				// For Middle box
				Middlebox m = deployMiddlebox(vm.getMiddleboxType(), vm);
				mbList.add(m);
			}
			
			allVms.put(vm.getId(), vm);
			
			deployVmNameToIdTable.put(vm.getName(), vm.getId());
			debugVmIdName.put(vm.getId(), vm.getName());
		}
		
		for(Arc arc:parser.getArcList()) {
			allArcs.add(arc);
			
			deployFlowNameToIdTable.put(arc.getName(), arc.getFlowId());
			if(arc.getFlowId() != -1) {
				deployFlowIdToArcTable.put(arc.getFlowId(), arc);
			}
		}

		// Add parsed ServiceFunctionChainPolicy
		for(ServiceFunctionChainPolicy policy:parser.getSFCPolicyList()) {
			sfcForwarder.addPolicy(policy);
			allArcs.addAll(createExtraArcSFCPolicy(policy));
		}
		List<Vm> vms = new ArrayList<Vm>(allVms.values());
		boolean result = deployApplication(vms, mbList, parser.getArcList(), parser.getSFCPolicyList());
		
		isApplicationDeployed = result;	
		
		//[NEW] create vms and migration arcs for migration
		//this.datacenter.processMigrationNet();
		return result;
	}
	
	private List<Arc> createExtraArcSFCPolicy(ServiceFunctionChainPolicy policy) {
		// Add extra Arc for ServiceFunctionChain
		
		List<Arc> arcList = new LinkedList<Arc>();
		int flowId = policy.getFlowId();
		
		long bw = 0;
		double latency = 0.0;
		
		if(flowId != -1)
		{
			Arc orgArc = deployFlowIdToArcTable.get(flowId);
			bw = orgArc.getBw();
			latency = orgArc.getLatency();
		}
		
		List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
		for(int i=0; i < vmIds.size()-1; i++) {
			// Build channel chain: SrcVM ---> SF1 ---> SF2 ---> DstVM
			int fromId = vmIds.get(i);
			int toId = vmIds.get(i+1);
			
			Arc sfcArc = new Arc(fromId, toId, flowId, bw, latency);
			arcList.add(sfcArc);
		}

		policy.setInitialBandwidth(bw);
		return arcList;
	}
	
	// for monitoring
	private void updateBWMonitor(double monitoringTimeUnit2) {
		double highest=0;
		// Update utilization of all links
		Set<Link> links = new HashSet<Link>(this.topology.getAllLinks());
		for(Link l:links) {
			double util = l.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
			if(util > highest) highest=util;
		}
		//System.err.println(CloudSim.clock()+": Highest utilization of Links = "+highest);
		
		// Update bandwidth consumption of all channels
		for(Channel ch:channelTable.values()) {
			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
			sfcForwarder.updateSFCMonitor(ch, processedBytes);
		}
		
		for(Channel ch:tempRemovedChannels) {
			long processedBytes = ch.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
			sfcForwarder.updateSFCMonitor(ch, processedBytes);
		}
		this.resetTempRemovedChannel();
	}

	private void updateHostMonitor(double monitoringTimeUnit2) {
		for(SDNHost h: datacenter.<SDNHost>getHostList()) {
			h.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
		}
	}
	
	private void updateSwitchMonitor(double monitoringTimeUnit2) {
		for(Switch s:getSwitchList()) {
			s.updateMonitor(CloudSim.clock(), monitoringTimeUnit2);
		}
	}
	
	private void updateVmMonitor(double logTime) {
		VmAllocationPolicy vmAlloc = datacenter.getVmAllocationPolicy();
		if(vmAlloc instanceof OverbookingVmAllocationPolicy) {
			for(Vm v: this.allVms.values()) {
				SDNVm vm = (SDNVm)v;
				double mipsOBR = ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioMips((SDNVm) vm);
				LogWriter log = LogWriter.getLogger("vm_OBR_mips.csv");
				log.printLine(vm.getName()+","+logTime+","+mipsOBR);
				
				double bwOBR =  ((OverbookingVmAllocationPolicy)vmAlloc).getCurrentOverbookingRatioBw((SDNVm) vm);
				log = LogWriter.getLogger("vm_OBR_bw.csv");
				log.printLine(vm.getName()+","+logTime+","+bwOBR);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	public <T extends Host> List<T> getHostList() {
		return (List<T>)allHosts;
	}
	
	public PhysicalTopology getPhysicalTopology() {
		return this.topology;
	}
	public Vm getSFForwarderOriginalVm(int vmId) {
		return this.sfcForwarder.getOriginalSF(vmId);
	}
	
	public LinkedList<Arc> getAllArcs(){
		return this.allArcs;
	}
	
	//[TODO] Whether VM/VNF is in a group
	public int isinMigGroup(int vmId) {
		return -1;
	}
	
	//[TODO] decide whether a VM is in a group or not. If yes, return group number; If not, return -1.
	public int isinVMGroup(int vmId) {
		this.isApplicationDeployed();
		return -1;
	}
	
	//[TODO] Whether a VNF is in a SFC. If yes, return SFC policy number.
	public int isinSFC(int vmId) {
		this.sfcForwarder.getAllPolicies();
		return -1;
	}
}
