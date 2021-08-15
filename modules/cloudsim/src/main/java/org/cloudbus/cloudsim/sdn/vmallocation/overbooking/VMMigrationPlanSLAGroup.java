package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Channel;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning.Resources;

import com.google.common.graph.*;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class VMMigrationPlanSLAGroup extends VMMigrationPlanning{
	
	SDNDatacenter dataCenter;
	private int groupNum;
	protected int migNum;
	private double time;
	private int waitVmId;
	//private List<Map<String, Object>> feasibleMigrationList;
	private Map<Integer, List<Memcopying>> migGroup;
	private Map<Integer, Double> migGroupMigTime; // key: group number, value: group migration time
	private Map<Integer, List<Node>> migRouteTable; //key: channel ID 
	
	//Map of host resource for feasible check (HostId, and its resources)
	private Map<Integer, Resources> currentHostResources;
	Map<Integer, Map<Integer, List<Node>>> multiPathMap; //all paths for migration key
	Map<Integer, Map<Integer, List<Node>>> multiPathSelected; //selected paths for migration key
	Map<Integer, Double> hostInterferCap; //host bandwidth interface capacity 
	
	protected MutableGraph<Integer> depGraph;
	protected Map<Link, List<Double>> linktoBwMap;
	private Map<List<Node>, Double> pathtoBwMap;
	private Map<Integer, Double> currentMigTimeMap; //map for current migration time
	
	//Map for waiting vm to finish to potentially increase bw
	private Map<Integer, List<Integer>> potentialWaitMigMap;
	
	protected int prevWaitSize = 0;
	
	
	private Map<Integer,Boolean> visitedNode;
	private Map<Integer, List<Memcopying>> depListMap;
	Collection<List<Memcopying>> completeDepList;
	
	public VMMigrationPlanSLAGroup(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping);

		this.dataCenter = dataCenter;
		this.time = CloudSim.clock();
		this.groupNum = 0;
		this.migNum = 0;
		this.currentHostResources = new HashMap<>();
		this.migGroup = new HashMap<>();
		
		this.migRouteTable = new HashMap<>();
		this.migGroupMigTime = new HashMap<>();
		
		this.multiPathMap = new HashMap<>();
		this.multiPathSelected = new HashMap<>();
		this.hostInterferCap = new HashMap<>();
		
		this.depGraph =GraphBuilder.undirected().build();
		this.linktoBwMap = new HashMap<>();
		this.pathtoBwMap = new HashMap<>();
		this.currentMigTimeMap = new HashMap<>();
		
		this.potentialWaitMigMap = new HashMap<>();
		
		this.visitedNode = new HashMap<>();
		this.depListMap = new HashMap<>();
		this.completeDepList = new ArrayList<>();
		
		for(Map<String, Object> migrate: migrationMapping) {
			this.migNum++;
			Memcopying m = this.toMemcopying(migrate);
			m.setId(this.migNum);
			this.migWaiting.add(m);
			if(Configuration.MPTCP_SUBFLOW_MAX>1) {
				Map<Integer, List<Node>> multiPaths = this.dataCenter.getNos().findAllPath(m.getSrcVm(), m.getDstVm());
				this.dataCenter.getNos().getMultiPathTable().put(m.getChId(), multiPaths);
			}
			//this.dataCenter.getNos().findAllPath(m.getSrcVm(), m.getDstVm(), m.getChId());
		}
		
		//assign deadline according to the configuration file
		
		/*Map<Integer,Double> deadlineMap = readDeadlineFile(Configuration.deadlinefilePath);
		for(Entry<Integer,Double> en:deadlineMap.entrySet()) {
			Memcopying mig = this.getMigration(en.getKey());
			if(mig != null) {
				SDNVm vm = (SDNVm)mig.migrate.get("vm");
				vm.setDeadline(en.getValue());
				mig.setDeadline(en.getValue());
			}			
		}*/
	
		
		//Initialize current host resources
		for(Host host:this.dataCenter.getHostList()) {
			Resources e = new Resources(host.getRam(), host.getBw(), host.getStorage(), host.getTotalMips());
			e.setAvailable(host.getRamProvisioner().getAvailableRam(), host.getBwProvisioner().getAvailableBw(), host.getStorage(), 
					host.getVmScheduler().getAvailableMips());
			e.id = host.getId();
			this.currentHostResources.put(e.id, e);
		}
		
		if(Configuration.MIG_SHARE_BANDWIDTH==false) {
			this.linktoBwMap = this.dataCenter.getNos().getLinkReservedBandwidth();
		}else {
			this.linktoBwMap = this.dataCenter.getNos().getLinkFreeBandwidth();
		}
	}
	
	private Map<Integer,Double> readDeadlineFile(String fileName){
		Map<Integer,Double> vmChIdToDeadlineMap = new HashMap<>();
		Path pathToFile = Paths.get(fileName);

        // create an instance of BufferedReader
        // using try with resource, Java 7 feature to close resources
        try (BufferedReader br = Files.newBufferedReader(pathToFile,
                StandardCharsets.US_ASCII)) {

            // read the first line from the text file
            String line = br.readLine();

            // loop until all lines are read
            while (line != null) {

                // use string.split to load a string array with the values from
                // each line of
                // the file, using a comma as the delimiter
            	//TODO change it from ChId to vm ID
                String[] attributes = line.split(",");

                vmChIdToDeadlineMap.put(Integer.valueOf(attributes[0]), Double.valueOf(attributes[1]));

                // read next line before looping
                // if end of file reached, line would be null
                line = br.readLine();
            }

        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
        return vmChIdToDeadlineMap;
	}
	
	//the main function to process migration planning
	//heuristic algorithm divides VMs into different resources independent group
	@Override
	public void processMigrationPlan() {
		double startTime = System.nanoTime();
		
		while(this.migWaiting.size()!=0) {
			this.migFeasible=this.checkFeasible();
			
			if(this.migFeasible.size() == 0) {
				break;
			}
			
			//1. creating dependency graph of all feasible tasks
			this.createDepGraph(this.migFeasible);
			
			//System.out.println("dependent graph of migrations");
			/*for(int i =0; i< this.migWaiting.size(); i++) {
				Set<Integer> adjNodes = this.depGraph.adjacentNodes(this.migWaiting.get(i).getId());
				//for(Integer n:adjNodes) {
				//	System.out.println("[DEBUG]: "+ this.migWaiting.get(i).getId() + "-" + n);
				//}
			}*/
			
			//2. creating fully resource dependency subgraph
			this.createCompleteSubgraph(); //depListMap
			
			for(Memcopying m:this.migFeasible) {
				double bw = 0;
				Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(m);
				for(List<Node> p:selectPaths.values()) {
					bw += this.dataCenter.getNos().getPathBandwidth(p, this.linktoBwMap);
				}
				double migTime = this.getMigrationTime(m, bw);
				this.currentMigTimeMap.put(m.getId(), migTime);
			}
			
			//3. Scoring and Sorting each migration task
			for(Entry<Integer,List<Memcopying>>en:this.depListMap.entrySet()) {
				List<Memcopying> completeDep = new ArrayList<>();
				for(Memcopying m:en.getValue()) {
					double score = this.scoreMigration(m);
					//System.out.println("[DEBUG] "+((SDNVm)m.migrate.get("vm")).getName()+" score: "+score);
					m.setScore(score);
					completeDep.add(m);
				}
				Collections.sort(completeDep);
				this.completeDepList.add(completeDep);
			}
			
			//4. Creating concurrent migration group based on cost order
			this.processGroup(completeDepList);
			
			//5. update resources and dependency graphs
			//this.printPlan();
			
			this.prevWaitSize = this.migWaiting.size();
			
			for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
				this.updateResources(en.getValue());
				//for(Memcopying mig:en.getValue()) {
				//	this.removeMigDepGraph(mig);
				//}
				this.linktoBwMap = this.updateTempLinktoBwMap(en.getValue());
				this.migWaiting.removeAll(en.getValue());
			}
			if(this.migWaiting.size() == prevWaitSize)
				break;
		}
		double endTime = System.nanoTime();
		double runT = this.getRunTime();
		runT = runT + (endTime - startTime)/1000000;
		this.setRunTime(runT);
		this.plancovertLinkedList(); // convert value list to linked list in plan hashmap
		
		//try to release temporary heap memory GC overheads limit exceed under big experiment for
		//VmSchedulerTimeSharedOverSubscriptionDynamicVM.redistributeMipsDueToOverSubscriptionDynamic
		currentHostResources = null;
		multiPathMap = null; //all paths for migration key
		multiPathSelected = null; //selected paths for migration key
		hostInterferCap = null; //host bandwidth interface capacity 
		
		linktoBwMap = null;
		pathtoBwMap = null;
	}
	
	//TODO parallel and sequential method selector (need to create a extra map for integrated parallel migrations)
	private boolean checkParSeq(Memcopying a, Memcopying b) {
		double bw1 = 0;
		double bw2 = 0;
		Map<Integer, List<Node>>selectPaths1 =this.getSelectedPaths(a);
		for(List<Node> p:selectPaths1.values()) {
			bw1 += this.getPathBandwidth(p);
		}
		Map<Integer, List<Node>>selectPaths2 =this.getSelectedPaths(b);
		for(List<Node> p:selectPaths2.values()) {
			bw2 += this.getPathBandwidth(p);
		}
		double migTime1 = this.getMigrationTime(a, bw1);
		double migTime2 = this.getMigrationTime(b, bw2);
		double parTime1 = this.getMigrationTime(a, bw1/2);
		double parTime2 = this.getMigrationTime(b, bw1/2);
		if(migTime1 + migTime2 < Math.max(parTime1, parTime2)) {
			return false; //seq method
		}else {
			return true; //par method
		}
	}
	
	//remove migration node from dependency graph
	private void removeMigDepGraph(Memcopying mig) {
		Set<Integer> adjNodes = new HashSet<>(this.depGraph.adjacentNodes(mig.getId()));
		for(Integer n:adjNodes) {
			this.depGraph.removeEdge(mig.getId(), n);
		}
		this.depGraph.removeNode(mig.getId());	
	}
	
	protected void createDepGraph(MutableGraph<Integer> fulldepGraph, List<Memcopying> feasibleMigList) {
		for(int i =0; i<feasibleMigList.size();i++) {
			Memcopying mig = feasibleMigList.get(i);
			this.depGraph.addNode(mig.getId());
		}
		
		for(int i =0; i<feasibleMigList.size()-1; i++) {
			Memcopying check = feasibleMigList.get(i);
			for(int j = i+1; j<feasibleMigList.size(); j++) {
				if(fulldepGraph.hasEdgeConnecting(check.getChId(), feasibleMigList.get(j).getChId())) {
					this.depGraph.putEdge(check.getId(), feasibleMigList.get(j).getId());
				}
			}
		}
		
	}
	
	protected void createDepGraph(List<Memcopying> feasibleMigList) {
		for(int i =0; i<feasibleMigList.size();i++) {
			Memcopying mig = feasibleMigList.get(i);
			this.depGraph.addNode(mig.getId());
		}
		
		for(int i =0; i<feasibleMigList.size()-1;i++) {
			Memcopying check = feasibleMigList.get(i);
			//System.out.println("[DEBUG]: migVm" + check.getMigVm());
			//System.out.println("[DEBug]: ChId" + check.getChId());
			for(int j = i+1; j<feasibleMigList.size(); j++) {
				if(check.getMigVm() != feasibleMigList.get(j).getMigVm()) {
				//System.out.println("[DEBUG]: migVm" + feasibleMigList.get(j).getMigVm());
				//System.out.println("[DEBug]: ChId" + feasibleMigList.get(j).getChId());
				boolean indep = false;
				if(Configuration.MIG_SIMPLE_INDEPENDENT==false) {
					indep = checkIndep(check, feasibleMigList.get(j));
				}else {
					indep = this.checkIndependent(check, feasibleMigList.get(j));
				}//simple independent check
				if(indep == false) 
					this.depGraph.putEdge(check.getId(), feasibleMigList.get(j).getId());
			}}
		}
	}
	
	public boolean checkIndepScheduling(Memcopying a, Memcopying b) {
		boolean flag = true;
		if(this.depGraph.hasEdgeConnecting(a.getId(), b.getId()))
			flag = false;
		return flag;
	}
	
	private boolean checkIndepSimple(Memcopying a, Memcopying b) {
		boolean flag = true;
		flag = this.checkIndependent(a, b);
		return flag;
	}
	
	protected boolean checkIndep(Memcopying a, Memcopying b) {
		if(Configuration.DEAFULT_ROUTING_WAN == true) {
		if(a.getSrcHost() == b.getSrcHost() && a.getDstHost() == b.getDstHost())
			return false;
		}else {
			if(Configuration.DEFAULT_ROUTING_FATTREE==true)
			if(a.getSrcHost() == b.getSrcHost() || a.getDstHost() == b.getDstHost())
					return false;
		}
		boolean flag = false;
		//Map<Integer,List<Node>> paths1 = this.getAllPaths(a);
		Map<Integer, List<Node>>paths1 =this.getSelectedPaths(a);
		Map<Integer, List<Node>>paths2 =this.getSelectedPaths(b);
		//Map<Integer,List<Node>> paths2 = this.getAllPaths(b);

		//double bw1 = this.getPathsBandwidth(paths1);
		//double bw2 = this.getPathsBandwidth(paths2);
		double bw1 = this.getPathsBandwidth(paths1);
		double bw2 = this.getPathsBandwidth(paths2);
		//bw1 = sbw1;
		//bw2 = sbw2;
		//paths1 = selectPaths1;
		//paths2 = selectPaths2;
		Collection<List<Node>> inter1 = this.getIntersect(paths1, paths2);
		Collection<List<Node>> inter2 = this.getIntersect(paths2, paths1);
		//for(List<Node> p:selectPaths1.values()) {
		//	System.out.println("paths1: "+p);
		//}
		//for(List<Node> p:selectPaths2.values()) {
		//	System.out.println("paths2: "+p);
		//}
		//System.out.println("inter1: "+inter1);
		//System.out.println("inter2: "+inter2);
		
		if(inter1.size()!=0 && inter2.size()!=0) {
			double interbw1 = this.getPathsBandwidth(inter1);
			double interbw2 = this.getPathsBandwidth(inter2);
			double hostcap1 = this.getHostCapacity(a.getSrcHost(), a.getDstHost());
			double hostcap2 = this.getHostCapacity(b.getSrcHost(), b.getDstHost());
			if(bw1<hostcap1)
				hostcap1 = bw1;
			if(bw2<hostcap2)
				hostcap2 = bw2;		
			if(bw1 - interbw1>= hostcap1 && bw2 -interbw2 >= hostcap2) {
				flag = true;
			}
		}else {
			flag = true;
		}
		return flag;
	}
	
	private Map<Integer,List<Node>> getAllPaths(Memcopying mig){
		Map<Integer, Map<Integer, List<Node>>> multipathMap = this.dataCenter.getNos().getMultiPathTable();
		Map<Integer, List<Node>> paths = multipathMap.get(mig.getChId());
		if(paths == null) {
			this.dataCenter.getNos().findAllPath(mig.getSrcVm(), mig.getDstVm(), mig.getChId());
			multipathMap = this.dataCenter.getNos().getMultiPathTable();
			this.multiPathMap = multipathMap;
			multipathMap.get(mig.getChId());
		}
		return multipathMap.get(mig.getChId());
	}
	
	private Collection<List<Node>> getIntersect(Map<Integer,List<Node>> p1, Map<Integer,List<Node>> p2){
		Collection<List<Node>> paths1 = new ArrayList<>();
		Collection<List<Node>> paths2 = new ArrayList<>();
		
		for(List<Node> n: p1.values()) {
			List<Node> nCopy = new ArrayList<>(n);
			if(Configuration.DEAFULT_ROUTING_WAN) {
				nCopy.remove(n.size()-1);
				nCopy.remove(0);
			}
			paths1.add(nCopy);
		}
		
		for(List<Node> m: p2.values()) {
			List<Node> mCopy = new ArrayList<>(m);
			if(Configuration.DEAFULT_ROUTING_WAN) { //if in inter-datacenter network
				mCopy.remove(m.size()-1);
				mCopy.remove(0);
			}
			paths2.add(mCopy);
		}		
		
		Collection<List<Node>> intersect = new ArrayList<>(paths1);
		intersect.retainAll(paths2);
		
		for(List<Node> n:paths1) {
			for(List<Node> m:paths2) {
				boolean sharing = this.checkPath(n, m);
				if(sharing) {
					if(intersect.contains(n)==false)
						intersect.add(n);
				}
			}
		}			
		
		return intersect;
	}
	
	private boolean checkPath(List<Node> p1, List<Node> p2) {
		List<Node> path1 = new ArrayList<>(p1);
		List<Node> path2 = new ArrayList<>(p2);
		List<Node> intersect = new ArrayList<>(path1);
		intersect.retainAll(path2);
		if(intersect.size()>1)
		for(int i =0; i<intersect.size()-1; i++) {
			Node n = intersect.get(i);
			Node m = intersect.get(i+1);
			
			int lower = path1.indexOf(n);
			int upper = path1.indexOf(m);
			
			int lowerF = path2.indexOf(n);
			int upperF = path2.indexOf(m);
			
			if(upper-lower<2 && (upper-lower)*(upperF-lowerF)>0) {
				return true;
			}
		}
		return false; //return true if sharing the same link
	}
	
	private double getPathsBandwidth(Collection<List<Node>> paths) {
		double totalbw = 0;
		for(List<Node> p:paths) {
			totalbw += this.getPathBandwidth(p);
		}
		return totalbw;
	}
	
	private double getPathsBandwidth(Map<Integer,List<Node>> paths) {
		double totalbw = 0;
		for(Entry<Integer,List<Node>>en:paths.entrySet()) {
			totalbw += this.getPathBandwidth(en.getValue());
		}
		return totalbw;
	}
	
	protected Map<Integer, List<Node>> getSelectedPaths(Memcopying mig){
		//non-intersecting paths with the largest bandwidth
		Map<Integer,List<Node>> paths = new HashMap<>();
		Map<Integer, List<Node>> multipath = this.multiPathMap.get(mig.getChId());
		if(multipath == null) {
			multipath = this.getAllPaths(mig);
		}
		Map<List<Node>, Long> selected = this.dataCenter.getNos().getMultiPath(multipath);
		int i =0;
		for(Entry<List<Node>,Long> en:selected.entrySet()){
			List<Node> path = en.getKey();
			paths.put(i, path);
			//long bw = en.getValue();
			i++;
		}
		return paths;	
	}
	
	private Link getLink(Node from, Node to) {
		return this.dataCenter.getNos().getPhysicalTopology().getLink(from.getAddress(), to.getAddress());
	}
	
	protected Map<Link, List<Double>> updateTempLinktoBwMap(List<Memcopying> migGroupList){
		Map<Link, List<Double>> templinktoBwMap = new HashMap<>(this.linktoBwMap);
		for(Memcopying mig:migGroupList) {
			this.deleteMigflows(mig, templinktoBwMap);
			this.addMigflows(mig, templinktoBwMap);
		}
		return templinktoBwMap;
	}
	
	protected Map<Link, List<Double>> updateTempLinktoBwMap(Memcopying mig){
		//update link to bw map based on new mig location and return temporary linktoBwMap
		Map<Link, List<Double>>templinktoBwMap = new HashMap<>(this.linktoBwMap);
		this.deleteMigflows(mig, templinktoBwMap);
		this.addMigflows(mig, templinktoBwMap);
		return templinktoBwMap;	
	}
	
	private void deleteMigflows(Memcopying mig, Map<Link, List<Double>> linktoBwMap) {
		for(Arc arc: this.dataCenter.getNos().getAllArcs()) {
			if(arc.getName() != null)
			if(arc.getName().indexOf("default") == -1 && arc.getFlowId()<Configuration.MIG_CHANNEL_START_ID)
			if(arc.getSrcId() == mig.getMigVm() || arc.getDstId() == mig.getMigVm()) {
				List<Node> nodes = new ArrayList<>();
				List<Link> links = new ArrayList<>();
				SDNVm vm = (SDNVm) mig.migrate.get("vm");
				SDNHost srcHost = (SDNHost) vm.getHost();
				int srcvmId = arc.getSrcId();
				int dstvmId = arc.getDstId();
				int flowId = arc.getFlowId();
				Node origin;
				if(arc.getSrcId() == vm.getId()) {
					origin = srcHost;
				}else {
					origin = (Node) this.dataCenter.findVm(srcvmId).getHost();
				}
				
				List<Link> route = this.dataCenter.getNos().getVmRoute(srcvmId, dstvmId, flowId);
				if(route != null)
				for(Link l:route) {
					List<Double> bwList = new ArrayList<>(linktoBwMap.get(l));
					if(l.getLowOrder().equals(origin)) {
						double upbw = bwList.get(0)+arc.getBw();
						bwList.set(0, upbw);
						linktoBwMap.put(l, bwList);
					}else {
						double downbw = bwList.get(1)+arc.getBw();
						bwList.set(1, downbw);
						linktoBwMap.put(l, bwList);
					}
					origin = l.getOtherNode(origin);
				}		
			}
		}
	}
	
	private void addMigflows(Memcopying mig, Map<Link,List<Double>> linktoBwMap) {
		for(Arc arc: this.dataCenter.getNos().getAllArcs()) {
			if(arc.getName()!=null)
			if(arc.getName().indexOf("default") == -1 && arc.getFlowId() < Configuration.MIG_CHANNEL_START_ID)
			if(arc.getSrcId() == mig.getMigVm() || arc.getDstId() == mig.getMigVm()) {
				SDNHost dstHost = (SDNHost) mig.migrate.get("host");
				SDNHost sender;
				SDNHost receiver;
				if(arc.getSrcId()==mig.getMigVm()) {
					 sender = dstHost;
					 receiver = (SDNHost) this.dataCenter.findVm(arc.getDstId()).getHost();;
				}else {
					sender = (SDNHost) this.dataCenter.findVm(arc.getSrcId()).getHost();;
					receiver = dstHost;
				}
				//int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(sender.getId());
				//int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(receiver.getId());
				//int chId = this.dataCenter.findMigFlow(sender.getId(), receiver.getId());
				//List<Link> route = this.dataCenter.getNos().getVmRoute(srcVm, dstVm, chId);
				List<Link> route = this.dataCenter.getNos().getPhysicalRoute(sender.getId(), receiver.getId());
				
				if(route != null)
				for(Link l:route) {
					List<Double> bwList = new ArrayList<>(linktoBwMap.get(l));
					//if(bwList == null) {
					//	bwList = new ArrayList<Double>(this.linktoBwMap.get(l));
					//}
					if(l.getLowOrder().equals(sender)) {
						double upbw = bwList.get(0) - arc.getBw();
						if(upbw < 0) upbw =0;
						bwList.set(0, upbw);
					}else{
						double downbw = bwList.get(1) - arc.getBw();
						if(downbw < 0) downbw =0;
						bwList.set(1, downbw);
					}
					linktoBwMap.put(l, bwList);
				}
			}
		}
	}
	
	private double getMigrationBw(Memcopying mig) {
		double totalBw = 0;
		Map<Integer, Map<Integer, List<Node>>> multipathMap = this.dataCenter.getNos().getMultiPathTable();
		Map<Integer, List<Node>> paths = multipathMap.get(mig.getChId());
		if(paths == null) {
			this.dataCenter.getNos().findAllPath(mig.getSrcVm(), mig.getDstVm(), mig.getChId());
			multipathMap = this.dataCenter.getNos().getMultiPathTable();
			paths = multipathMap.get(mig.getChId());
		}
		for(Entry<Integer, List<Node>> en:paths.entrySet()) {
			double bw = 0;
			bw = this.dataCenter.getNos().getPathBandwidth(en.getValue(), this.linktoBwMap);
			totalBw = totalBw + bw;
		}
		return totalBw;
	}
	
	private double getTempPathBandwidth(List<Node> path, Map<Link, List<Double>> templinktoBwMap) {
		//get path bandwidth from updated temporary linktoBwMap;
		double bw = Double.POSITIVE_INFINITY;
		double linkbw = 0;
		for(int i =0; i<path.size()-1;i++) {
			Link l = this.getLink(path.get(i), path.get(i+1));
			List<Double> bwList = templinktoBwMap.get(l);
			if(bwList==null) {
				bwList = this.linktoBwMap.get(l);
			}else {	
				if(l.getLowOrder().equals(path.get(i))) {
					linkbw = bwList.get(0);
				}else {
					linkbw = bwList.get(1);
				}
				if(linkbw<0)
					linkbw = 0;
			}
			if(bw > linkbw) {
				bw = linkbw;
			}
		}
		return bw;
	}
	
	protected double getPathBandwidth(List<Node> path){
		//method for non reserved vm and migration bandwidth sharing
		double linkBw = 0.0;
		double linkcap = 0.0;
		double bw = Double.POSITIVE_INFINITY;
		
		//minimum capacity along the path of the link
		for(int i =0; i<path.size()-1; i++) {
			Link l = this.getLink(path.get(i), path.get(i+1)); 
			if(linktoBwMap.get(l)==null) {
				List<Double> bwList = new ArrayList<>();
				linkcap = l.getBw(path.get(i));
				Double upBw = linkcap;
				Double downBw = linkcap;
				linkBw = l.getFreeBandwidth(path.get(i));
				if(linkBw<0)
					linkBw = 0;
				if(l.getLowOrder().equals(path.get(i))) {
					//upBw update
					upBw = linkBw;
				}else {
					//downBw update
					downBw = linkBw;
				}
				bwList.add(upBw);
				bwList.add(downBw);
				this.linktoBwMap.put(l, bwList);
			}else {
				List<Double> bwList = this.linktoBwMap.get(l);
				if(l.getLowOrder().equals(path.get(i))) {
					linkBw = bwList.get(0);
				}else {
					linkBw = bwList.get(1);
				}
			}
			if(bw > linkBw) {
				bw = linkBw;
			}
		}
		return bw;
	}
	
	/*private void setMultiPathRoute(Memcopying mig, Map<Integer,List<Node>> selected) {
		Map<Integer,List<Node>> route = this.multiPathSelected.get(mig.getChId());
		route = new HashMap<Integer,List<Node>>(selected);
	}*/
	
	/*private Map<Integer,List<Node>> getMultiPathRoute(Memcopying mig){
		return this.multiPathSelected.get(mig.getChId());
	}*/
	
	private double getHostCapacity(int srcId, int dstId) {
		double cap = 0;
		double totalsrc = 0;
		double totaldst = 0;
		SDNHost src = this.dataCenter.findHost(srcId);
		SDNHost dst = this.dataCenter.findHost(dstId);
		Collection<Link> links1 = this.dataCenter.getNos().getPhysicalTopology().getAdjacentLinks(src);
		Collection<Link> links2 = this.dataCenter.getNos().getPhysicalTopology().getAdjacentLinks(dst);
		for(Link l:links1) {
			totalsrc += l.getFreeBandwidth(src);
		}
		for(Link l:links2) {
			totaldst += l.getFreeBandwidth(l.getHighOrder());
		}
		this.hostInterferCap.put(srcId, totalsrc);
		this.hostInterferCap.put(dstId, totaldst);
		cap = Math.min(totalsrc, totaldst);
		return cap;
	}
	
	
	private void createCompleteSubgraph() {
		for(Integer n:this.depGraph.nodes()) {
			//int i = 0;
			List<Memcopying> depList = this.depListMap.get(n);
			if(depList == null && this.visitedNode.containsKey(n) == false) {
				depList = new ArrayList<>();
				if(depList.contains(this.getMigration(n)) == false) {
					depList.add(this.getMigration(n));
					depListMap.put(n, depList);
					this.createCompleteDepGroup(n, n, depList);
				}
			}
		}
	}
	
	
	private double scoreMigration(Memcopying mig) {
		double a = 0.5;
		double b = 0; //0.4
		double c = 0.1;
		double score = 0;
		double migTime = this.currentMigTimeMap.get(mig.getId());
		double deadline = getDeadlineTime(mig);
		double impact = getImpact(mig);
		//TODO: normalize the factors slack time
		//TODO: direct impact should be more important
		//TODO: potential impact wouldn't matter if no other improvement
		score = a * migTime + b * (deadline-migTime) + c* impact;
		return score;
	}
	
	/*protected double getMigrationTime(Memcopying mig, double bw) {
		double migTime = 0;
		double t_pre = mig.getPreMigTime();
		double t_post = mig.getPostMigTime();
		double t_mem = 0;
		SDNVm vm = (SDNVm) mig.migrate.get("vm");
		double dirty_rate = Math.pow(10,9) * vm.getDirtyRate();
		//double data = 0;
		double remain = mig.getVmRAM()*8*Math.pow(10, 9);
		double dt = remain * vm.getCompressRatio() / bw;
		int n =1;
		while(dt>mig.getDowntimeThres() && n<=mig.getIterationThres()) {
			double t = remain * vm.getCompressRatio() /bw;
			remain = dirty_rate * t;
			//data += bw*t;
			t_mem += t;
			dt = t;
			n +=1;
		}
		migTime = t_pre + t_mem + t_post;
		return migTime;
	}*/
	
	
	
	private double getDeadlineTime(Memcopying mig) {
		/*
		SDNVm vm = (SDNVm) mig.migrate.get("vm");
		if(vm.getDeadline() == -1) {
			double deadline = (vm.getVioThred()-vm.getCurrentVio()) /vm.getVioSpeed();
			mig.setDeadline(deadline);
			return deadline;
		}else {
			return vm.getDeadline();
		}
		*/
		return mig.getDeadline();
	}
	
	private double getPotentialImpact(Memcopying mig) {
		double impact = 0;
		Map<Link, List<Double>> tempLinktoBwMap = updateTempLinktoBwMap(mig);
		double migTime = this.currentMigTimeMap.get(mig.getId());
		for(Memcopying m: this.migFeasible) {
			if(m.getMigVm() != mig.getMigVm()) {
				double bw = 0;
				double originbw = 0;
				//double afterbw = 0;
				double minIncrease = Double.MAX_VALUE;
				double maxDecrease = 0;
				Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(m);
				int increaseNum = 0;
				int decreaseNum = 0;
				for(List<Node> p:selectPaths.values()) {
					for(int i =0; i< p.size()-1; i++) {
						List<Node> link = p.subList(i, i+2);
						bw = this.getTempPathBandwidth(link, tempLinktoBwMap);
						originbw = this.getPathBandwidth(link);
						double bwChange = bw - originbw;
						if(bwChange != 0) {
							if(bwChange>0) {
								increaseNum += 1;
								if(minIncrease > bwChange)
									minIncrease = bwChange;
							}else {
								decreaseNum += 1;
								if(maxDecrease > bwChange) {
									maxDecrease = bwChange;
								}
							}
						}
					}
					if(decreaseNum!=0) {
						// if bw decreased, the impact is direct impact
						//impact = impact + this.getMigrationTime(m, Math.abs(maxDecrease));
						//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+ 
						//		" decrease link num: "+decreaseNum+"/"+(p.size()-1) + " for "+ ((SDNVm)m.migrate.get("vm")).getName());
						//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+ 
						//		" increase link num: "+increaseNum+"/"+(p.size()-1) + " for "+ ((SDNVm)m.migrate.get("vm")).getName());
					}else {
						if(increaseNum!=0) {
							originbw = this.getPathBandwidth(p);
							//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+ 
							//		" increase link num: "+increaseNum+"/"+(p.size()-1) + " for "+ ((SDNVm)m.migrate.get("vm")).getName());
							double afterPotential = this.getMigrationTime(m, originbw+minIncrease);
							double currentMig = this.currentMigTimeMap.get(m.getId());
							double impact_m = ((double)increaseNum/(double)(p.size()-1))*(afterPotential-currentMig);
							//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+ " potential impact on "+((SDNVm)m.migrate.get("vm")).getName()+ " is "+ impact_m);
							//impact =  impact + migTime + impact_m;
							impact =  impact + impact_m;
							if(Math.abs(impact_m)>migTime) {
								List<Integer> waitMig = this.potentialWaitMigMap.get(m.getId());
								if(waitMig==null) {
									waitMig = new ArrayList<>();
								}
								waitMig.add(mig.getId());
								//System.out.println(m.migrate.get("vm")+"potential waiting vms");
								//for(Integer vm:waitMig) {
								//	System.out.println("vm #"+vm);
								//}
								this.potentialWaitMigMap.put(m.getId(), waitMig);
							}
						}
					}
				}
			}
		}
		return impact;
	}
	
	private double getImpact(Memcopying mig) {
		double impact = 0;
		double current = 0;
		double after = 0;
		Map<Link,List<Double>>tempLinktoBwMap = updateTempLinktoBwMap(mig);
		for(Memcopying m:this.migFeasible) {
			if(m.getMigVm() != mig.getMigVm()) {
			double curmigTime = this.currentMigTimeMap.get(m.getId());
			current = current + curmigTime + curmigTime - m.getDeadline();
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(m);
			for(List<Node> p:selectPaths.values()) {
				bw += this.getTempPathBandwidth(p, tempLinktoBwMap);
			}
			double aftermigTime = 0;
			if(this.depGraph.hasEdgeConnecting(mig.getId(), m.getId()))
				aftermigTime = this.currentMigTimeMap.get(mig.getId()); //dependent migration mig
			double afterexeTime = this.getMigrationTime(m, bw);
			aftermigTime = aftermigTime + afterexeTime;
			after =  after + afterexeTime + aftermigTime - m.getDeadline();
		}}
		impact = after - current;
		//potential impact with part of the path changed
		double potentialImpact = this.getPotentialImpact(mig);
		//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+" Impact: "+impact);
		//System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+" potentialImpact: "+potentialImpact);

		return impact + potentialImpact;
	}
	
	
	private Memcopying getMigration(Integer migId) {
		for(Memcopying mig:this.migWaiting) {
			if(mig.getId() == migId) {
				return mig;
			}
		}
		return null;
	}
	
	private void createCompleteDepGroup(Integer migId, Integer originId, List<Memcopying> depList) {
		if(this.visitedNode.get(migId)==null){
			Set<Integer> nodes = this.depGraph.adjacentNodes(migId);
			this.visitedNode.put(migId, true);
			for(Integer n:nodes) {
				if(this.visitedNode.containsKey(n)==false)
				if(isCompleteGraph(depList, n)) {
					if(this.visitedNode.get(n)==null) {
						//this.visitedNode.put(n, true);
						depList.add(getMigration(n));
						createCompleteDepGroup(n,originId,depList);
					}
				}
			}
			if(this.depListMap.get(originId) != null) {
				List<Memcopying> list = this.depListMap.get(originId);
				int size = list.size();
				if(size<depList.size()) {
					this.depListMap.put(originId, depList);
				}
			}else {
				this.depListMap.put(originId, depList);
			}
			
			/*
			if(isCompleteGraph(depList, migChId)) {
				depList.add(getMigration(migChId));
				for(Integer n:nodes) {
					if(this.visitedNode.get(n) == false) {
						createCompleteDepGroup(n,depList);
					}
				}
				if(this.depListMap.get(migChId) != null) {
					if(this.depListMap.get(migChId).size()<depList.size()) {
						this.depListMap.put(migChId, depList);
					}
				}
			}*/
		}
	}
	
	private boolean isCompleteGraph(List<Memcopying> depList, Integer mig) {
		boolean flag = false;
		for(Memcopying m:depList) {
			flag = this.depGraph.hasEdgeConnecting(m.getId(), mig);
			if(flag == true) {
				continue;
			}else {
				break;
			}
		}
		return flag;
	}
	
	@Override
	public void printPlan() {
		for(Entry<Integer, List<Memcopying>> migGroup: this.migGroup.entrySet()) {
			System.out.println("Group: "+migGroup.getKey());
			for(Memcopying mig: migGroup.getValue()) {
				System.out.println("VM "+mig.getMigVm() +": "+mig.getSrcHost()+"->"+mig.getDstHost()+"|"+mig.getChId()+" ");
				SDNVm vm = (SDNVm) mig.migrate.get("vm");
				if(vm != null) {
					//System.out.println("VM State: dirty rate:" + vm.getDirtyRate() + " remained: " + vm.getRemainedDirtyPg());
					//System.out.println("Mig estimated: "+mig.getEstimateMigTime() + " priority: "+ mig.getPriority());
					System.out.println("Score: "+mig.getScore());
					System.out.println("Deadline: "+mig.getDeadline());
				}
			}
			System.out.print("\n");
		}
	}
	
	//release SRC resources after the migration for dynamic re-calculation
	public void updateAfterMig(Memcopying mig) {

	}
	
	
	//if the migration could be performed at current time
	protected LinkedList<Memcopying> checkFeasible() {
		LinkedList<Memcopying> feasibleList = new LinkedList<>();
		for(Memcopying migrate: this.migWaiting) {
			//check the migration channel availability
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(migrate);
			for(List<Node> p:selectPaths.values()) {
				bw  += this.dataCenter.getNos().getPathBandwidth(p, this.linktoBwMap);
			}
			//System.out.println(migrate.migrate.get("vm")+"current mig bw: "+bw);
			if(bw<=0) {
				continue;
			}
			
			
			Resources e = this.currentHostResources.get(migrate.getDstHost());
			//if(this.getMigrationPlanMap().get(chId) != null && this.getMigrationPlanMap().get(chId).size()!=0) { //check the migration channel is available or not
			//	break;
			//}
			if (e.availableDisk < migrate.getVmDisk()) {
				continue;
			}
			else if (e.availableRam < migrate.getVmRAM()) {
				continue;
			}
			else if (e.availableBw < migrate.getVmBw()) {
				continue;
			}
			else if (e.availableMips < migrate.getVmTotalMips()) {
				continue;
			}
			else {
				feasibleList.add(migrate);
			}
		}
		return feasibleList;
	}
	
	private void plancovertLinkedList() {
		for(Entry<Integer,List<Memcopying>>en:this.migGroup.entrySet()) {
			LinkedList<Memcopying> linkedl = new LinkedList<>(en.getValue());
			this.migPlanMap.put(en.getKey(), linkedl);
			this.getMigrationPlanList().add(en.getKey()); //migration plan list for migration group num
		}
		
	}
	
	
	private int checkGroup(Memcopying checkMig) {
		// operate on feasibleMigration List
		if(this.migGroup !=null)
		for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
			List<Memcopying> migGroupList = en.getValue();
			boolean flag = true;
			for(Memcopying m:migGroupList) {
				if(this.depGraph.hasEdgeConnecting(checkMig.getId(), m.getId())) {
					flag = false;
					break;
				}
			}
			if(flag == true) {
				return en.getKey();
			}
		}
		return -1;
	}
	
	protected Memcopying toMemcopying(Map<String, Object> migrate) {
		SDNVm vm = (SDNVm) migrate.get("vm");
		Host host = (Host) migrate.get("host");
		
		vm.setRemainedDirtyPg((long) (vm.getRam()*8*Math.pow(10,9)));
		
		int srcHost = vm.getHost().getId();
		int dstHost = host.getId();
		int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost);
		int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
		long amountMem = vm.getRam();
		double startTime = -1;
		double currentTime = CloudSim.clock();
		Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
		act.setMigVm(vm.getId());
		act.setPrecopy(false);
		act.setStopandcopy(false);
		
		//set VM/VNF resources requirement
		act.setVmRAM(vm.getRam());
		act.setVmBw(vm.getBw());
		act.setVmDisk(vm.getSize());
		act.setVmMips(vm.getCloudletScheduler().getCurrentRequestedMips());
		act.setVmTotalMips(vm.getCurrentRequestedTotalMips());
		
		int chId = this.dataCenter.findMigFlow(act.getSrcHost(), act.getDstHost());
		act.setChId(chId);
		
		// all version compatibility
		act.migrate = migrate;
		
		//calculate migration deadline
		//double deadline = this.getDeadlineTime(act);
		act.setPreMigTime(vm.getPreMigrationTime());
		act.setPostMigTime(vm.getPostMigrationTime());
		
		act.setDirtyRate(vm.getDirtyRate());
		act.setDeadline(vm.getDeadline());
		
		return act;
	}
	
	private void processGroup(Collection<List<Memcopying>> completeDepList) {
		for(List<Memcopying> completeDep:completeDepList) {
			for(Memcopying m:completeDep) {
				int num = checkGroup(m);
				if(num != -1) {
					List<Memcopying> groupMigList = this.migGroup.get(num);
					groupMigList.add(m);
				}else {
					//new resource independent group for concurrent migration
					this.groupNum++;
					List<Memcopying> newGroupMigList = new ArrayList<>();
					newGroupMigList.add(m);
					this.migGroup.put(groupNum, newGroupMigList);
				}
			}
		}
	}
	
	//check src and dst for single migration
	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		if(Configuration.MIG_SIMPLE_INDEPENDENT) {
		if(finish.getSrcHost() == check.getSrcHost() || finish.getDstHost() == check.getDstHost())
			return true;
		else
			return false;
		}else {
			if(finish.getSrcHost() == check.getSrcHost() && finish.getDstHost() == check.getDstHost())
				return true; //sharing
			else
				return false; //not
		}
	}
	
	//check path sharing for single migration
	@Override
	public boolean checkPath(Memcopying finish, Memcopying check) {
		//test
		//if(check.getChId()== 11040 || check.getChId() == 10275)
		//	System.out.println("test");
		boolean indep = this.checkIndep(finish, check);
		if(indep == true)
			return false;
		return true;
		
		/*
		List<Node> checkRoute = this.getMigRoute(check);
		List<Node> finishRoute = this.getMigRoute(finish);
		List<Node> intersect = new ArrayList<>(checkRoute);
		intersect.retainAll(finishRoute);
		if(intersect.size()>1)
		for(int i=0; i< intersect.size()-1;i++) {
			Node n = intersect.get(i);
			Node m = intersect.get(i+1);
			
			int lower = checkRoute.indexOf(n);
			int upper = checkRoute.indexOf(m);
			
			int lowerF = finishRoute.indexOf(n);
			int upperF = finishRoute.indexOf(m);
			
			if(upper-lower<2 && (upper-lower)*(upperF-lowerF)>0) {
				return true;
			}
		}
		
		//	return true sharing the same link
		return false;
		*/
	}
	
	/*private double groupWaitTime(List<Memcopying> migGroupList, int waitVmId) {
		//identify the vmid that trigger the next round calculation
		int vmId = 0;
		double migTime = 0;
		double minWaitTime = Double.MAX_VALUE;
		double maxWaitTime = Double.MIN_VALUE;
		for(Memcopying finish:migGroupList) {
			migTime = this.estimateMigTime(finish, false);
			for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
				for(Memcopying check:en.getValue()) {
					if(checkHost(finish, check) || checkPath(finish, check)) {
						if(minWaitTime>migTime) {
							minWaitTime = migTime;
							waitVmId = finish.getMigVm();
						}	
						if(maxWaitTime<migTime) {
							maxWaitTime = migTime;
							vmId = finish.getMigVm();
						}	
					}
				}
			}
		}
		waitVmId =  vmId;
		//waitTime = maxWaitTime
		return minWaitTime;
		
		
	}*/
	
	private Map<Integer, Resources> updateResources(List<Memcopying> migGroupList) {
		Map<Integer, Resources> updatedResources = new HashMap<Integer, Resources>(currentHostResources);
		
		for(Memcopying mig:migGroupList) {
			double mips = 0;
			if(mig.getVmMips()!=null) {
				for(int i=0;i<mig.getVmMips().size();i++) {
					mips+=mig.getVmMips().get(i);
				}
			}else {
				mips = mig.getVmTotalMips();
			}

			
			Resources src = new Resources(updatedResources.get(mig.getSrcHost()));
			
			src.availableBw += mig.getVmBw();
			src.availableDisk += mig.getVmDisk();
			src.availableMips += mips;
			src.availableRam = mig.getVmRAM();
			
			updatedResources.put(mig.getSrcHost(), src);
			
			Resources src1 = new Resources(updatedResources.get(mig.getDstHost()));
			src1.availableBw -= mig.getVmBw();
			src1.availableDisk -= mig.getVmDisk();
			src1.availableMips -= mips;
			src1.availableRam -= mig.getVmRAM();
			
			updatedResources.put(mig.getDstHost(), src);
		}
		return updatedResources;
	}
	
	/*private double groupImpactTime(int groupNum, List<Memcopying> migGroupList) {
		double impactTime = 0;
		double beforeTime = 0;
		for(Entry<Integer, List<Memcopying>> entry:this.migGroup.entrySet()) {
			if(entry.getKey() != groupNum) {
				//double groupTime = this.groupMaxMigTime(entry.getValue());
				Double groupTime = this.migGroupMigTime.get(groupNum);
				if(groupTime == null) {
					groupTime = this.groupMaxMigTime(entry.getValue(), true);
					this.migGroupMigTime.put(entry.getKey(), groupTime);
				}
				beforeTime += groupTime;
			}	
		}
		Map<Integer, Resources> currentResources = this.currentHostResources;
		Map<Integer, Resources> updatedResources = new HashMap<>();
		updatedResources = this.updateResources(migGroupList);
		this.currentHostResources = updatedResources;
		
		for(Entry<Integer, List<Memcopying>> entry:this.migGroup.entrySet()) {
			if(entry.getKey() != groupNum) {
				//double groupTime = this.groupMaxMigTime(entry.getValue());
				Double groupTime = this.migGroupMigTime.get(entry.getKey());
				if(groupTime == null) {
					groupTime = this.groupMaxMigTime(entry.getValue(), false);
					this.migGroupMigTime.put(entry.getKey(), groupTime);
				}
				impactTime += groupTime;
			}
		}
		
		impactTime = impactTime - beforeTime;
		this.currentHostResources = currentResources;
		
		return impactTime;
	}*/

	public Map<List<Node>, Double> getPathtoBwMap() {
		return pathtoBwMap;
	}

	public void setPathtoBwMap(Map<List<Node>, Double> pathtoBwMap) {
		this.pathtoBwMap = pathtoBwMap;
	}

	public Map<Integer, List<Integer>> getPotentialWaitMigMap() {
		return potentialWaitMigMap;
	}

	public void setPotentialWaitMigMap(Map<Integer, List<Integer>> potentialWaitMigMap) {
		this.potentialWaitMigMap = potentialWaitMigMap;
	}
	


}
