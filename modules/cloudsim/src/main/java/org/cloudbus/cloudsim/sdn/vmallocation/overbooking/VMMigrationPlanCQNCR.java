package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Channel;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;

import com.google.common.graph.GraphBuilder;



public class VMMigrationPlanCQNCR extends VMMigrationPlanning{
	
	SDNDatacenter dataCenter;
	private int groupNum;
	private int migNum;
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
	
	private Map<Link, List<Double>> linktoBwMap;
	private Map<List<Node>, Double> pathtoBwMap;
	private Map<Integer, Double> currentMigTimeMap; //map for current migration time
	
	private int prevWaitSize = 0;
	
	//calculate algorithm running time
	double runTime;
	
	public VMMigrationPlanCQNCR(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping);
		// TODO Auto-generated constructor stub
		this.dataCenter = dataCenter;
		this.time = CloudSim.clock();
		this.groupNum = 0;
		this.migNum = 0;
		this.currentHostResources = new HashMap<>();
		this.migGroup = new HashMap<>();
		
		this.runTime = 0;
		
		this.multiPathMap = new HashMap<>();
		this.multiPathSelected = new HashMap<>();
		this.hostInterferCap = new HashMap<>();
		
		this.migRouteTable = new HashMap<>();
		this.migGroupMigTime = new HashMap<>();
		
		this.linktoBwMap = new HashMap<>();
		this.pathtoBwMap = new HashMap<>();
		this.currentMigTimeMap = new HashMap<>();
		
		for(Map<String, Object> migrate: migrationMapping) {
			this.migNum++;
			Memcopying m = this.toMemcopying(migrate);
			m.setId(this.migNum);
			this.migWaiting.add(m);
			this.dataCenter.getNos().findAllPath(m.getSrcVm(), m.getDstVm(), m.getChId());
		}
		
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

	//TODO the main function to process migration planning
	//heuristic algorithm divides VMs into different resources independent group
	@Override
	public void processMigrationPlan() {
		double startTime = System.nanoTime();
		this.time = CloudSim.clock();
		
		
		
		@SuppressWarnings("unused")
		int round = 0;
		//TODO quite when not feasible to schedule other migrations
		while(this.migWaiting.size()!=0) {
			round++;
			//1. check current feasible migration tasks
			this.migFeasible= checkFeasible();
			
			if(this.migFeasible.size()==0) {
				break;
			}
			
			//2. renew and create the dependency graph
			//this.depGraph = GraphBuilder.undirected().build();
			List<Memcopying> nodeList = nodesNotinDepGraph(migFeasible);
			if(nodeList.size()!=0)
				this.createDepGraph(nodeList);
			
			if(migFeasible != null || migFeasible.size() != 0) {
			//3. generate migration group
			this.processGroup();
			
			double minCost = Double.MAX_VALUE;
			int minGroupNum = 0;
			int minWaitVmId = -1;
			double minWaitTime = Double.MAX_VALUE;
			
			for(Entry<Integer,List<Memcopying>> entry:this.migGroup.entrySet()) {
				int groupNum = entry.getKey();
				List<Memcopying> migGroupList = entry.getValue();
				
				double migTime = 0;
				//double maxMigTime = 0;
				
				//4.1. group migration time
				double groupMigTime = this.groupMaxMigTime(migGroupList, true);
				this.migGroupMigTime.put(groupNum, groupMigTime);


				//4.2. wait time
				//int vmId = 0;
				double minWait = Double.MAX_VALUE;
				double maxWait = Double.MIN_VALUE;
				
				if(this.migGroup.size() != 1) {
				for(Memcopying finish:migGroupList) {
					//migTime = this.estimateMigTime(finish, true);
					migTime = this.currentMigTimeMap.get(finish.getId());
					//migTime = this.getMigrationTime(finish, bw);
					for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
						if(groupNum != en.getKey())
						for(Memcopying check:en.getValue()) {
							//if(checkHost(finish, check) || checkPath(finish, check)) {
							//if(this.depGraph.hasEdgeConnecting(finish.getId(), check.getId())) 
							//if(this.checkIndep(finish, check)==false) 
							if(finish.getId()!=check.getId())
							//if(this.checkIndependent(finish, check)==false)
							{
								if(minWait>migTime) {
									minWait = migTime;
									waitVmId = finish.getMigVm();
								}	
								if(maxWait < migTime) {
									maxWait = migTime;
									//waitVmId = finish.getMigVm();
								}	
							}
						}
					}
				}}
				else {
					minWait = 0;
				}
				
				//waitTime = maxWaitTime
				double waitTime = minWait;
				//double waitTime = maxWait;
				
				//TODO 4.3. impact
				double impactTime = 0;
				double beforeTime = 0;
				@SuppressWarnings("unused")
				double currentTime = 0;
				
				//4.3.1 current group migration time
				for(Entry<Integer, List<Memcopying>> entry1:this.migGroup.entrySet()) {
					if(entry1.getKey() != groupNum) {
						Double groupTime = this.groupMaxMigTime(entry1.getValue(), true);
						//Double groupTime = this.migGroupMigTime.get(entry1.getKey());
						//if(groupTime == null) {
						//	groupTime = this.groupMaxMigTime(entry1.getValue(), true);
						//	this.migGroupMigTime.put(entry.getKey(), groupTime);
						//}
						beforeTime += groupTime;
					}	
				}
				
				
				Map<Integer, Resources> currentResources = new HashMap<>();
				currentResources.putAll(this.currentHostResources);
				Map<Integer, Resources> updatedResources = new HashMap<>(this.updateResources(migGroupList));
				this.currentHostResources.clear();
				this.currentHostResources.putAll(updatedResources);
				
				Map<Link, List<Double>> templinktoBwMap = new HashMap<>(this.updateTempLinktoBwMap(migGroupList));
				
				for(Entry<Integer, List<Memcopying>> entry1:this.migGroup.entrySet()) {
					if(entry1.getKey() != groupNum) {
						//double groupTime = this.groupMaxMigTime(entry.getValue());
						//Double groupTime = this.groupMaxMigTime(entry1.getValue(), false);
						Double groupTime = this.groupMaxMigTimeAfter(entry1.getValue(), templinktoBwMap);
						//don't store the estimate time and group migration times
						//this.migGroupMigTime.put(entry.getKey(), groupTime);
						
						impactTime += groupTime;
					}
				}
				
				impactTime = impactTime - beforeTime;
				this.currentHostResources.clear();
				this.currentHostResources.putAll(currentResources);
				
				double impact = impactTime;
				
				double a = 1, b = 1, c = 1;
				double groupCost = a*groupMigTime + b*waitTime + c*impact;
				
				if(groupCost < minCost) {
					minGroupNum = groupNum;
					minCost = groupCost;
					minWaitVmId = waitVmId;
					minWaitTime = waitTime;
				}
			}
			
			//System.out.println("Group Num:"+minGroupNum+"\n Minimum Group Cost:"+minCost);
			//System.out.println("Earliest next round start at finishment of VM "+minWaitVmId);
			
			
			//TEST print all mig group
			/*System.out.println("Round: "+round +" Time: "+time+ "  Waiting Mig Num: "+ this.migWaiting.size() +"  Feasible Num: "+this.migFeasible.size());
			for(Entry<Integer, List<Memcopying>> migGroup: this.migGroup.entrySet()) {
				System.out.println("Group: "+migGroup.getKey());
				for(Memcopying mig: migGroup.getValue()) {
					System.out.print("VM "+ ((SDNVm)mig.migrate.get("vm")).getName() +": "+mig.getSrcHost()+"->"+mig.getDstHost()+"|"+mig.getChId()+" ");
					List<Memcopying> temp = new ArrayList(migGroup.getValue());
					temp.remove(mig);
					for(Memcopying check:temp) {
						//TODO
						if(this.checkPath(mig, check)) {
							System.out.println("VMMigrationPlanningCQNCR.checkPath [ERROR]: sharing network path between other migration in the same group!!!");
						}
					}
				}
				System.out.print("\n");
			}*/
			
			this.prevWaitSize = this.migWaiting.size();
			
			//update the migration tasks group and resources
			if(minGroupNum > 0) {
				this.waitVmId = minWaitVmId;
				
				List<Memcopying> toMigGroup = this.migGroup.get(minGroupNum);
				if(toMigGroup == null) {
					throw new NullPointerException("migGroup don't exist!!"); 
				}else {
					
				for(Memcopying mig: toMigGroup) {
					double mips = 0;
					if(mig.getVmMips() != null) {
						for(int i=0;i<mig.getVmMips().size();i++) {
							mips+=mig.getVmMips().get(i);
						}
					}else {
						mips = mig.getVmTotalMips();
					}

					
					//refresh src and dst resources immediately
					
					Resources res = this.currentHostResources.get(mig.getDstHost());
					res.availableBw -= mig.getVmBw();
					res.availableDisk -= mig.getVmDisk();
					//res.maxMips -= mips;
					res.availableMips -= mips;
					res.availableRam -= mig.getVmRAM();
					
					res = this.currentHostResources.get(mig.getSrcHost());
					res.availableBw += mig.getVmBw();
					res.availableDisk += mig.getVmDisk();
					res.availableMips += mips;
					res.availableRam += mig.getVmRAM();
					
					
					// add migration to execution list prediction model
					mig.setStartTime(time);
				}
				
				LinkedList<Memcopying> linkedMig = new LinkedList<>(toMigGroup);
				this.getMigrationPlanMap().put(minGroupNum, linkedMig);
				this.getMigrationPlanList().add(minGroupNum);
				
				//remove mig from waiting list
				this.migWaiting.removeAll(toMigGroup);
				this.linktoBwMap = this.updateTempLinktoBwMap(toMigGroup);
				for(Memcopying m:toMigGroup) {
					this.removeMigDepGraph(m);
				}
				this.migGroup.clear();
				
				time += minWaitTime;
			    }
			}
			if(this.migWaiting.size() == this.prevWaitSize)
				break;
		    }
			
		}
		double endTime = System.nanoTime();
		//set runtime in milliseconds
		double runT = this.getRunTime();
		runT = runT + (endTime - startTime)/1000000;
		this.setRunTime(runT);
	}
	
	@Override
	public void printPlan() {
		System.out.println("=======CQNCR Migration Planning=========");
		for(Entry<Integer, LinkedList<Memcopying>> migGroup: this.getMigrationPlanMap().entrySet()) {
			System.out.println("Group: "+migGroup.getKey());
			for(Memcopying mig: migGroup.getValue()) {
				System.out.println("VM "+((SDNVm)mig.migrate.get("vm")).getName() +": "+mig.getSrcHost()+"->"+mig.getDstHost()+"|"+mig.getChId()+" ");
				SDNVm vm = (SDNVm) mig.migrate.get("vm");
				if(vm != null)
					System.out.println("VM State: dirty rate (Gbit/s):" + vm.getDirtyRate() + "remained: " + vm.getRemainedDirtyPg());
				System.out.println("Mig estimated: "+mig.getEstimateMigTime() + " priority: "+ mig.getPriority());
			}
			System.out.print("\n");
		}
	}
	
	//release SRC resources after the migration for dynamic re-calculation
	//TODO update the channels related to migrated VM
	public void updateAfterMig(Memcopying mig) {
		
		Resources res = this.currentHostResources.get(mig.getSrcHost());
		double mips = 0;
		for(int i=0;i<mig.getVmMips().size();i++) {
			mips+=mig.getVmMips().get(i);
		}
		res.availableBw += mig.getVmBw();
		res.availableDisk += mig.getVmDisk();
		res.availableMips += mips;
		res.availableRam += mig.getVmRAM();
		
		// remove migration in migration mapping/waiting list
		this.migrationMapping.remove(mig.getId()-1);
		this.migWaiting.remove(mig.getId()-1);
			
		if(mig.getMigVm() == this.waitVmId) {
			this.processMigrationPlan();
		}
	}
	
	private List<Memcopying> nodesNotinDepGraph(List<Memcopying> feasibleMigList){
		List<Memcopying> nodeList = new ArrayList<>();
		for(Memcopying m:feasibleMigList) {
			boolean flag = false;
			for(Integer n: this.depGraph.nodes()) {
				if(m.getId()==n) {
					flag = true;
					break;
				}
			}
			if(flag == false) {
				nodeList.add(m);
			}
		}
		return nodeList;
	}
	
	private void removeMigDepGraph(Memcopying mig) {
		Set<Integer> adjNodes = new HashSet<>(this.depGraph.adjacentNodes(mig.getId()));
		for(Integer n:adjNodes) {
			this.depGraph.removeEdge(mig.getId(), n);
		}
		this.depGraph.removeNode(mig.getId());	
	}
	
	public void updateChannelsReosurcesTemp(Memcopying mig, Map<Integer, Resources> tempRes) {
		int vmId = mig.getMigVm();
		SDNHost srcHost = this.dataCenter.findHost(mig.getSrcHost());
		Resources resSrc = tempRes.get(mig.getSrcHost());
		Resources resDst = tempRes.get(mig.getDstHost());
		
		LinkedList<Arc> allArcs = this.dataCenter.getNos().getAllArcs();
		for(Arc arc:allArcs) {
			if(arc.getSrcId() == vmId || arc.getDstId() == vmId) {
				double bw = arc.getBw();
				if(resSrc.availableBw + bw <= srcHost.getBandwidth()) {
					resSrc.availableBw += bw;
				}else {
					resSrc.availableBw = srcHost.getBandwidth();
				}
				if(resDst.availableBw - bw >= 0) {
					resDst.availableBw -= bw;
				}else {
					resDst.availableBw = 0;
				}				
			}
		}
	}
	
	public void updateChannelsResources(Memcopying mig) {
		int vmId = mig.getMigVm();
		SDNHost srcHost = this.dataCenter.findHost(mig.getSrcHost());
		Resources resSrc = this.currentHostResources.get(mig.getSrcHost());
		Resources resDst = this.currentHostResources.get(mig.getDstHost());
		
		LinkedList<Arc> allArcs = this.dataCenter.getNos().getAllArcs();
		for(Arc arc:allArcs) {
			if(arc.getSrcId() == vmId || arc.getDstId() == vmId) {
				double bw = arc.getBw();
				if(resSrc.availableBw + bw <= srcHost.getBandwidth()) {
					resSrc.availableBw += bw;
				}else {
					resSrc.availableBw = srcHost.getBandwidth();
				}
				if(resDst.availableBw - bw >= 0) {
					resDst.availableBw -= bw;
				}else {
					resDst.availableBw = 0;
				}				
			}
		}
	}
	
	//true for migrating, false for available channel
	//Map<Integer, Boolean> migFlagMap = new HashMap<>();
	
	//if the migration could be performed at current time
	private LinkedList<Memcopying> checkFeasible() {
		LinkedList<Memcopying> feasibleList = new LinkedList<>();
		for(Memcopying migrate: this.migWaiting) {
			//check the migration channel availability
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(migrate);
			for(List<Node> p:selectPaths.values()) {
				bw  += this.dataCenter.getNos().getPathBandwidth(p, this.linktoBwMap);
			}
			if(bw<=0) {
				continue;
			}
			
			Resources e = this.currentHostResources.get(migrate.getDstHost());
			//if(this.getMigrationPlanMap().get(chId) != null && this.getMigrationPlanMap().get(chId).size()!=0) { //check the migration channel is available or not
			//	break;
			//}
			if (e.availableDisk < migrate.getVmDisk()) {
					break;
			}
			else if (e.availableRam < migrate.getVmRAM()) {
				break;
			}
			else if (e.availableBw < migrate.getVmBw()) {
				break;
			}
			else if (e.availableMips < migrate.getVmTotalMips()) {
				break;
			}
			else {
				feasibleList.add(migrate);
			}
		}
		return feasibleList;
	}
	
	@Deprecated
	//function implemented in checkNetPath
	private boolean checkHost(Memcopying migrate, List<Memcopying> migGroupList) {
		// check the dst and src hosts
		int srcHostId = migrate.getSrcHost();
		int dstHostId = migrate.getDstHost();
		for(Memcopying e: migGroupList) {
			if(srcHostId == e.getSrcHost() && dstHostId == e.getDstHost())
				return false;
		}
		return true; // don't share
	}
	
	private List<Node> getMigRoute(Memcopying checkMig){
		int chId = checkMig.getChId();
		if(this.migRouteTable.containsKey(chId)) {
			return this.migRouteTable.get(chId);
		}else {
			SDNHost srcHost = this.dataCenter.findHost(checkMig.getSrcHost());
			List<Node> route = srcHost.getCompleteVmRoute(checkMig.getSrcVm(), checkMig.getDstVm(), checkMig.getChId());
			this.migRouteTable.put(chId, route);
			return route;
		}
	}
	
	private boolean checkIndependent(Memcopying checkMig, List<Memcopying> migGroupList) {
		for(Memcopying mig:migGroupList) {
			//for fully bisection datacenter network
			if(checkMig.getSrcHost() == mig.getSrcHost() || checkMig.getDstHost() == mig.getDstHost()) {
				return false;
			}
		}
		return true;
	}
	private boolean checkNetPath(Memcopying checkMig, List<Memcopying> migGroupList) {	
		for(Memcopying mig:migGroupList) {
			
			if(checkMig.getSrcHost() == mig.getSrcHost() && checkMig.getDstHost() == mig.getDstHost())
				return false; // src or dst host is the same
			if(this.checkPath(mig, checkMig))
				return false; // sharing some same link

				
		}	
		return true; //don't share any
	}
	
	//TODO
	private int checkGroup(Memcopying checkMig) {
		// operate on feasibleMigration List
		if(this.migGroup !=null)
		for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
			List<Memcopying> migGroupList = en.getValue();
			int groupNum = en.getKey();
			boolean flag = true;
			for(Memcopying m:migGroupList) {
				//boolean indep = this.checkIndependent(checkMig, m);
				//if(indep == false) {
				//	flag = false;
				//	break;
				//}
				//boolean indep = this.checkIndependent(checkMig, m);
				//if(indep == false) {
					// has edge in depGraph
				//	flag = false;
				//	break;
				//}
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
	
	
	@Override
	public boolean checkIndependent(Memcopying a, Memcopying b) {
		if(a.getSrcHost() == b.getSrcHost() && a.getDstHost() == b.getDstHost())
			return false;
		Map<Integer,List<Node>> paths1 = this.getAllPaths(a);
		Map<Integer,List<Node>> paths2 = this.getAllPaths(b);
		Collection<List<Node>> inter1 = this.getIntersect(paths1, paths2);
		Collection<List<Node>> inter2 = this.getIntersect(paths2, paths1);
		if(inter1.size() == paths1.values().size() || inter2.size() == paths2.values().size())
			return false;
		return true;
	}
	
	//old version 
	/*private int checkGroup(Memcopying checkMig) {
		// operate on feasibleMigration List
		if(this.migGroup !=null)
		for(Entry<Integer, List<Memcopying>> en:this.migGroup.entrySet()) {
			List<Memcopying> migGroupList = en.getValue();
			//if(checkNetPath(checkMig,migGroupList)){
			if(checkIndependent(checkMig,migGroupList)){
				return en.getKey(); // first fit
			}
		}
		return -1;
	}*/
	
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
		
		act.setPreMigTime(vm.getPreMigrationTime());
		act.setPostMigTime(vm.getPostMigrationTime());
		
		act.setDirtyRate(vm.getDirtyRate());
		act.setDeadline(vm.getDeadline());
		
		return act;
	}
	
	private Link getLink(Node from, Node to) {
		return this.dataCenter.getNos().getPhysicalTopology().getLink(from.getAddress(), to.getAddress());
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
	
	private void updateLinkBandwidth(List<Node> path, double bwDel) {
		for(int i =0; i<path.size()-1; i++) {
			Link l = this.getLink(path.get(i), path.get(i+1));
			List<Double> bwList = this.linktoBwMap.get(l);
			double upBw = bwList.get(0);
			double downBw = bwList.get(1);
			if(l.getLowOrder().equals(path.get(i))) {
				//upbw
				upBw = upBw - bwDel;
				bwList.set(0, upBw);
			}else {
				//lowbw
				downBw = downBw - bwDel;
				bwList.set(1, downBw);
			}
			this.linktoBwMap.put(l, bwList);
		}
	}
	
	private double getPathBandwidth(List<Node> path){
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
				//oversubscribe the bandwidth for migration and other service flows
				//linkBw = linkcap / (linkcap + linkcap - l.getFreeBandwidth(path.get(i)));
				
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
	
	private void processGroup() {
		for(Memcopying e: this.migFeasible) {
			int num = checkGroup(e);
			if( num != -1) {
				List<Memcopying> groupMigList = this.migGroup.get(num);
				groupMigList.add(e);
			} else {
				// new resource independent group
				this.groupNum++;
				List<Memcopying> newGroupMigList = new ArrayList<>();
				newGroupMigList.add(e);
				this.migGroup.put(groupNum, newGroupMigList);
			}
		}
	}
	
	private double estimateMigTime(Memcopying mig, boolean storeResult) {
		if(mig.getEstimateMigTime() != -1 && storeResult == true) {
			//want to get and store the estimate time 
			return mig.getEstimateMigTime();
		}else{
			double migTime =0;
			double preMig = 0;
			double postMig = 0;
			double migrationBw = 0;
			
			//Resources res = this.currentHostResources.get(mig.getSrcHost());
			//migrationBw = res.availableBw;
			//int chId = mig.getChId();
			//channel is temporary! use allArcs and deployFlowIdToArcTable
			//Channel checkCh = this.dataCenter.getNos().findChannel(mig.getSrcVm(), mig.getDstVm(), chId);
			//migrationBw = checkCh.getAllocatedBandwidth();
			//long dstBw = this.currentHostResources.get(mig.getDstHost()).availableBw;
			
			//lowest bw along the link
			//migrationBw = this.dataCenter.getNos().getLowestLinkBw(mig.getSrcVm(), mig.getDstVm(), mig.getChId());
			double totalbw = this.getMigrationBw(mig);
			SDNHost srchost = this.dataCenter.findHost(mig.getSrcHost());
			SDNHost dsthost = this.dataCenter.findHost(mig.getDstHost());
			double bwcap = Math.min(srchost.getBandwidth(), dsthost.getBandwidth());
			if(totalbw > bwcap)
				totalbw = bwcap;
			migrationBw = totalbw;
			
			SDNVm vm = (SDNVm) this.dataCenter.getNos().findVm(mig.getMigVm());
			
			//double dirtyRate = Math.pow(10,9)*vm.getDirtyRate();
			//TODO set the dirty page rate accordingly
			double dirtyRate = vm.getDirtyRate() * Math.pow(10,9);
			double ratio = dirtyRate/ migrationBw;
			double dtRemain = mig.getDowntimeThres()*migrationBw;
			double vmRam = Math.pow(10,9)*mig.getVmRAM()*vm.getCompressRatio();
			double iterNum =  Math.ceil(Math.log(dtRemain/vmRam)/Math.log(ratio));
			migTime = vmRam/migrationBw*(1-Math.pow(ratio,iterNum+1))/(1-ratio);
			migTime = migTime + preMig + postMig;
			if(storeResult == true)
				mig.setEstimateMigTime(migTime);
			return migTime;
		}
		
	}
	
	private double groupMaxMigTime(List<Memcopying> migGroupList, boolean current) {
		double migTime = 0;
		double maxMigTime = 0;
		if(current == true) {
		for(Memcopying mig: migGroupList) {
			//migTime = this.estimateMigTime(mig, storeResult);
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(mig);
			for(List<Node> p:selectPaths.values()) {
				bw += this.dataCenter.getNos().getPathBandwidth(p, this.linktoBwMap);
			}
			migTime = this.getMigrationTime(mig, bw);
			this.currentMigTimeMap.put(mig.getId(), migTime);
			if(migTime > maxMigTime) {
				maxMigTime = migTime;
			}
		}}
		return maxMigTime;
	}
	
	private double groupMaxMigTimeAfter(List<Memcopying> migGroupList, Map<Link, List<Double>> tempLinktoBwMap) {
		double migTime = 0;
		double maxMigTime = 0;
		for(Memcopying mig: migGroupList) {
			//migTime = this.estimateMigTime(mig, storeResult);
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(mig);
			for(List<Node> p:selectPaths.values()) {
				bw += this.getTempPathBandwidth(p, tempLinktoBwMap);
			}
			migTime = this.getMigrationTime(mig, bw);
			this.currentMigTimeMap.put(mig.getId(), migTime);
			if(migTime > maxMigTime) {
				maxMigTime = migTime;
			}
		}
		return maxMigTime;
	}
	
	//check src and dst for single migration
	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		//if(finish.getSrcHost() == check.getSrcHost() || finish.getDstHost() == check.getDstHost())
		if(finish.getSrcHost() == check.getSrcHost() && finish.getDstHost() == check.getDstHost())
			return true;
		else
			return false;
	}
	
	//check path sharing for single migration
	@Override
	public boolean checkPath(Memcopying finish, Memcopying check) {
		//test
		//if(check.getChId()== 11040 || check.getChId() == 10275)
		//	System.out.println("test");
		
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
	}
	
	private double groupWaitTime(List<Memcopying> migGroupList, int waitVmId) {
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
		
		
	}
	
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
	
	private double groupImpactTime(int groupNum, List<Memcopying> migGroupList) {
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
	}
	
	private void deleteMigflows(Memcopying mig, Map<Link, List<Double>> linktoBwMap) {
		for(Arc arc: this.dataCenter.getNos().getAllArcs()) {
			if(arc.getName()!=null)
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
		/*
			if(arc.getSrcId() == mig.getMigVm() || arc.getDstId() == mig.getMigVm()) {
			int dstvmId = arc.getDstId();	
			int srcvmId = arc.getSrcId();
			
			mig.migrate.get("host");
			Channel ch = this.dataCenter.getNos().findChannel(srcvmId, dstvmId, arc.getFlowId());
			if(ch!=null) {
				SDNVm srcvm = this.dataCenter.findVm(srcvmId);
				SDNVm dstvm = this.dataCenter.findVm(dstvmId);
				SDNHost srchost = (SDNHost) srcvm.getHost();
				SDNHost dsthost = (SDNHost) dstvm.getHost();
				if(arc.getSrcId() == mig.getMigVm()) {
					srchost = (SDNHost) mig.migrate.get("host");
				}else {
					dsthost = (SDNHost) mig.migrate.get("host");
				}
				Node nextHop = srchost;
				Node oldHop;
				while(nextHop != null && nextHop.equals(dsthost) == false) {
					List<Link> nextLinkCandidates = nextHop.getRoute(dsthost);
					Link nextLink = this.dataCenter.getNos().
							getLinkSelectionPolicy().selectLink(nextLinkCandidates, arc.getFlowId(), srchost, dsthost, nextHop);
					oldHop = nextHop;
					nextHop = nextLink.getOtherNode(nextHop);
					double bw = 0;
					if(linktoBwMap.get(nextLink)!=null) {
						bw = linktoBwMap.get(nextLink);
					}else {
						bw = nextLink.getFreeBandwidth(oldHop);
					}
					bw = bw - arc.getBw();
					linktoBwMap.put(nextLink, bw);
				}
			}
			
			}*/
	}
	
	private Map<Link, List<Double>> updateTempLinktoBwMap(List<Memcopying> migGroupList){
		Map<Link, List<Double>> templinktoBwMap = new HashMap<>(this.linktoBwMap);
		for(Memcopying mig:migGroupList) {
			this.deleteMigflows(mig, templinktoBwMap);
			this.addMigflows(mig, templinktoBwMap);
		}
		return templinktoBwMap;
		
	}
	
	private Map<Link, List<Double>> updateTempLinktoBwMap(Memcopying mig){
		//update link to bw map based on new mig location and return temporary linktoBwMap
		Map<Link, List<Double>>templinktoBwMap = new HashMap<>(this.linktoBwMap);
		this.deleteMigflows(mig, templinktoBwMap);
		this.addMigflows(mig, templinktoBwMap);
		return templinktoBwMap;	
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
	
	protected double getMigrationTime(Memcopying mig, double bw) {
		double migTime = 0;
		double t_pre = mig.getPreMigTime();
		double t_post = mig.getPostMigTime();
		double t_mem = 0;
		SDNVm vm = (SDNVm) mig.migrate.get("vm");
		double dirty_rate = Math.pow(10,9) * vm.getDirtyRate(); //bits per seconds
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
	}
	
	private double getImpact(Memcopying mig) {
		double impact = 0;
		double current = 0;
		double after = 0;
		Map<Link,List<Double>>tempLinktoBwMap = updateTempLinktoBwMap(mig);
		for(Memcopying m:this.migFeasible) {
			double curmigTime = this.currentMigTimeMap.get(m.getId());
			current = current + curmigTime + curmigTime - m.getDeadline();
			double bw = 0;
			Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(m);
			for(List<Node> p:selectPaths.values()) {
				bw += this.getTempPathBandwidth(p, tempLinktoBwMap);
			}
			double aftermigTime = 0;
			if(this.depGraph.hasEdgeConnecting(mig.getId(), m.getId()))
				aftermigTime = this.currentMigTimeMap.get(mig.getId()); //dependent migration mig,m
			double afterexeTime = this.getMigrationTime(m, bw);
			aftermigTime = aftermigTime + afterexeTime;
			after =  after + afterexeTime + aftermigTime - m.getDeadline();
		}
		impact = after - current;
		return impact;
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
	
	/*private void createDepGraph(List<Memcopying> feasibleMigList) {
		for(int i =0; i<feasibleMigList.size();i++) {
			Memcopying mig = feasibleMigList.get(i);
			this.depGraph.addNode(mig.getId());
		}
		
		for(int i =0; i<feasibleMigList.size()-1;i++) {
			Memcopying check = feasibleMigList.get(i);
			for(int j = i+1; j<feasibleMigList.size(); j++) {
				//boolean indep = checkIndep(check, feasibleMigList.get(j));
				Memcopying mc = feasibleMigList.get(j);
				boolean indep = this.checkIndependent(check, mc);
				if(indep == false) {
					if(check.getId()!=mc.getId())
					this.depGraph.putEdge(check.getId(), mc.getId());
				}
			}
		}
	}*/
	
	protected boolean checkIndep(Memcopying a, Memcopying b) {
		if(a.getSrcHost() == b.getSrcHost() && a.getDstHost() == b.getDstHost()) {
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
	
	/*private boolean checkIndep(Memcopying a, Memcopying b) {
		//check Independent for multi-path and multi-interfaces
		boolean flag = false;
		Map<Integer,List<Node>> paths1 = this.getAllPaths(a);
		Map<Integer, List<Node>>selectPaths1 =this.getSelectedPaths(a);
		Map<Integer, List<Node>>selectPaths2 =this.getSelectedPaths(b);
		Map<Integer,List<Node>> paths2 = this.getAllPaths(b);

		double bw1 = this.getPathsBandwidth(paths1);
		double bw2 = this.getPathsBandwidth(paths2);
		double sbw1 = this.getPathsBandwidth(selectPaths1);
		double sbw2 = this.getPathsBandwidth(selectPaths2);
		bw1 = sbw1;
		bw2 = sbw2;
		paths1 = selectPaths1;
		paths2 = selectPaths2;
		Collection<List<Node>> inter1 = this.getIntersect(paths1, paths2);
		Collection<List<Node>> inter2 = this.getIntersect(paths2, paths1);
		if(inter1 != null && inter2!=null) {
			double interbw1 = this.getPathsBandwidth(inter1);
			double interbw2 = this.getPathsBandwidth(inter2);
			double hostcap1 = this.getHostCapacity(a.getSrcHost(), a.getDstHost());
			double hostcap2 = this.getHostCapacity(b.getSrcHost(), b.getDstHost());
			if(bw1<hostcap1)
				hostcap1 = bw1;
			if(bw2<hostcap2)
				hostcap2 = bw2;
			if(bw1 + bw2 - Math.max(interbw1, interbw2)>= hostcap1 + hostcap2) {
				if(a.getSrcHost() != b.getSrcHost() && a.getDstHost() != b.getDstHost()) {
					flag = true;
				}
			}
		}else {
			flag = true;
		}
		return flag;
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
	
	private double getPathsBandwidth(Collection<List<Node>> paths) {
		double totalbw = 0;
		for(List<Node> p:paths) {
			totalbw += this.getPathBandwidth(p);
		}
		return totalbw;
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
	
	private Collection<List<Node>> getIntersect(Map<Integer,List<Node>> p1, Map<Integer,List<Node>> p2){
		Collection<List<Node>> paths1 = new ArrayList<>(p1.values());
		Collection<List<Node>> paths2 = p2.values();
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
	
	private double getPathsBandwidth(Map<Integer,List<Node>> paths) {
		double totalbw = 0;
		for(Entry<Integer,List<Node>>en:paths.entrySet()) {
			totalbw += this.getPathBandwidth(en.getValue());
		}
		return totalbw;
	}
	
	private Map<Integer, List<Node>> getSelectedPaths(Memcopying mig){
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
	
	public double getRunTime() {
		return this.runTime;
	}
	public void setRunTime(double runTime) {
		this.runTime = runTime;
	}
}
