package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Stack;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning.Resources;

public class VMMigrationPlanFPTA extends VMMigrationPlanning{
	private SDNDatacenter dataCenter;
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
	
	//for this algorithm
	private int v_node;
	private Map<Link, List<Double>> linktoBwMap;
	private Map<Link, List<Double>> linktoLengthMap;
	private Map<List<Node>, Double> pathtoBwMap;
	private Map<Integer, Map<Integer, List<Node>>> flowPathMap;
	
	private double acc = 0.2;
	//length of link e -> u(e) = sigma
	private double sigma;
	
	public VMMigrationPlanFPTA(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping);
		// TODO Auto-generated constructor stub
		this.dataCenter = dataCenter;
		this.time = CloudSim.clock();
		this.groupNum = 0;
		this.migNum = 0;
		this.v_node = dataCenter.getNos().getPhysicalTopology().getAllNodes().size();
		this.sigma = (1+acc)/Math.pow((1+acc)*v_node, 1/acc);
		
		this.currentHostResources = new HashMap<>();
		this.migGroup = new HashMap<>();
		
		this.migRouteTable = new HashMap<>();
		this.migGroupMigTime = new HashMap<>();
		
		//algorithm initialization
		this.linktoBwMap = new HashMap<>();
		this.linktoLengthMap = new HashMap<>();
		this.pathtoBwMap = new HashMap<>();
		this.flowPathMap = new HashMap<>();
		
		for(Map<String, Object> migrate: migrationMapping) {
			this.migNum++;
			Memcopying m = this.toMemcopying(migrate);
			m.setId(this.migNum);
			this.migWaiting.add(m);
		}
		
		this.v_node = this.dataCenter.getNos().getPhysicalTopology().getAllNodes().size();
		
		//Initialize current host resources
		if(Configuration.MIG_SHARE_BANDWIDTH==true) {
			this.linktoBwMap = this.dataCenter.getNos().getLinkFreeBandwidth();
		}else {
			this.linktoBwMap = this.dataCenter.getNos().getLinkReservedBandwidth();
		}
		
		for(Host host:this.dataCenter.getHostList()) {
			double totalVmmips = 0;
			for(int i = 0; i<host.getVmList().size(); i++) {
				Vm vm = host.getVmList().get(i);
				double vmmips = vm.getMips();
				totalVmmips += vmmips;
			}
			Resources e = new Resources(host.getRam(), host.getBw(), host.getStorage(), host.getTotalMips());
			e.setAvailable(host.getRamProvisioner().getAvailableRam(), host.getBwProvisioner().getAvailableBw(), host.getStorage(), 
					host.getTotalMips()-totalVmmips);
			e.id = host.getId();
			this.currentHostResources.put(e.id, e);
		}
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
		double dirtyRate = vm.getDirtyRate();
		double startTime = -1;
		double currentTime = CloudSim.clock();
		Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
		act.setMigVm(vm.getId());
		act.setPrecopy(false);
		act.setStopandcopy(false);
		act.setDirtyRate(dirtyRate);
		act.setDeadline(vm.getDeadline());
		
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
		
		return act;
	}
	
	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		//double acc = 0.4;
		//length of link e -> u(e) = sigma
		//double sigma = (1+acc)/Math.pow((1+acc)*v_node, 1/acc);
		
		//clear previous round migPlan and initialization
		
		double startTime = System.nanoTime();
		this.migPlan = new LinkedList<>();
		this.linktoBwMap = new HashMap<>();
		this.linktoLengthMap = new HashMap<>();
		this.flowPathMap = new HashMap<>();
		this.pathtoBwMap = new HashMap<>();
		int upper_bound = (int) Math.ceil(Math.log((1+acc)/sigma)/Math.log(1+acc));
		for(int r = 1; r<=upper_bound; r++) {
			for(int j=0; j<this.migWaiting.size(); j++) {
				List<Node> shortestPath = this.getShortestPath(this.migWaiting.get(j));
				if(shortestPath == null)
					continue;
				double distance = getPathDistance(shortestPath);
				Double bw = 0.0;
				double threshold = sigma*Math.pow(1+acc,r);
				while(distance < Math.min(1, threshold)) {
					double c = this.getPathBandwidth(shortestPath);
					bw = pathtoBwMap.get(shortestPath);
					if(bw == null) 
						bw = 0.0;
					bw = bw + c;
					//this.updateLinkBandwidth(shortestPath, c); //update bw of each link along the path
					this.pathtoBwMap.put(shortestPath, bw); //store the allocated bw
					this.updateLinkLength(shortestPath, c, acc); //
					shortestPath = this.getShortestPath(this.migWaiting.get(j));
					if(shortestPath == null)
						break;
					distance = getPathDistance(shortestPath);
				}
			}
		}
		
		//for each path do the adjustment for available bw allocated for path p
		//x(p) = x(p)/sigma
		
		//iterative all migration
		LinkedList<Memcopying> migWaitingIter = new LinkedList<>(migWaiting);
		for(List<Node> path: this.pathtoBwMap.keySet()) {
			double totalBw = 0;
			double ratio = Math.log((1+acc)/this.sigma)/Math.log(1+acc);
			double bw = this.pathtoBwMap.get(path);
			bw = bw/ratio;
			this.pathtoBwMap.put(path, bw);
		}
		
		for(int i = 0; i<migWaitingIter.size(); i++) {
			Memcopying mig = migWaitingIter.get(i);
			double bw = this.getMigrationBw(mig);
			double dirtyRate = Math.pow(10,9) * mig.getDirtyRate();
			//origin algorithm without this constraint
			if(bw !=0) { //dirtyRate
			if(bw > dirtyRate) {
				//this.migWaiting.remove(mig);
				mig.setRequestedBw(bw);
				this.migPlan.add(mig);
				System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+" "+ "X=1 "+ "x(p): "+ bw);
			}else {
				System.out.println(((SDNVm)mig.migrate.get("vm")).getName()+" "+ "X=0 "+ "x(p): "+ bw);
			}
			}
		}
		
		double endTime = System.nanoTime();
		double runT = this.getRunTime();
		runT = runT + (endTime - startTime)/1000000;
		this.setRunTime(runT);
	}
	
	
	private double getMigrationBw(Memcopying mig) {
		double totalBw = 0;
		double ratio = 1; //Math.log((1+acc)/this.sigma)/Math.log(1+acc)
		//this.dataCenter.getNos().findAllPath(mig.getSrcVm(), mig.getDstVm(), mig.getChId());
		Map<Integer, Map<Integer, List<Node>>> multipathMap = this.dataCenter.getNos().getMultiPathTable();
		Map<Integer, List<Node>> paths = multipathMap.get(mig.getChId());
		for(Entry<Integer, List<Node>> route: paths.entrySet()) {
			List<Node> path = route.getValue();
			double bw = 0;
			if(this.pathtoBwMap.get(path) != null)
				bw = this.pathtoBwMap.get(path)/ratio;
			totalBw += bw;
			this.pathtoBwMap.put(path, 0.0);
		}
		
		return totalBw;
	}
	
	private Link getLink(Node from, Node to) {
		return this.dataCenter.getNos().getPhysicalTopology().getLink(from.getAddress(), to.getAddress());
	}
	
	private void updateLinkLength(List<Node> path, double c, double acc) {
		for(int i =0; i<path.size()-1; i++) {
			Link link = this.getLink(path.get(i), path.get(i+1));
			double len = 0;
			double cap= 0;
			List<Double> lenList = this.linktoLengthMap.get(link);
			List<Double> bwList = linktoBwMap.get(link);

			if(link.getLowOrder().equals(path.get(i))) {
				len = lenList.get(0);
				cap = bwList.get(0);
				len = len*(1+ acc*c/cap);
				lenList.set(0, len);
			}else {
				len = lenList.get(1);
				cap = bwList.get(1);
				len = len*(1+ acc*c/cap);
				lenList.set(1, len);
			}
			this.linktoLengthMap.put(link, lenList);
		}
	}
	
	private void updateLinkBandwidth(List<Node> path, double bwDel) {
		for(int i =0; i<path.size()-1; i++) {
			Link l = this.getLink(path.get(i), path.get(i+1));
			List<Double> bwList = this.linktoBwMap.get(l);
			if(l.getLowOrder().equals(path.get(i))) {
				double upbw = bwList.get(0);
				if(upbw-bwDel != 0) {
					bwList.set(0, upbw- bwDel);
				}else {
					bwList.set(0, 0.0);
				}
			}else {
				double downbw = bwList.get(1);
				if(downbw-bwDel != 0) {
					bwList.set(1, downbw-bwDel);
				}else {
					bwList.set(1, 0.0);
				}
			}
		}
	}
	
	/*private double getPathBandwidth(List<Node> path){
		boolean first = true;
		double linkBw = 0.0;
		double linkcap = 0.0;
		double bw = Double.POSITIVE_INFINITY;
		
		//minimum capacity along the path of the link
		for(int i =0; i<path.size()-1; i++) {
			Link l = this.getLink(path.get(i), path.get(i+1)); 
			if(linktoBwMap.get(l)==null) {
				linkcap = l.getBw(path.get(i));
				//bw_multipler = 10 results all 2.0E8 available BW used
				linkBw = l.getFreeBandwidth(path.get(i));
				//oversubscribe the bandwidth for migration and other service flows
				//linkBw = linkcap / (linkcap + linkcap - l.getFreeBandwidth(path.get(i)));
				if(linkBw<0)
					linkBw = 0;
				this.linktoBwMap.put(l, linkBw);
			}else {
				linkBw = this.linktoBwMap.get(l);
			}
			if(bw > linkBw) {
				bw = linkBw;
			}
		}
		return bw;
	}*/
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
	
	private double getPathDistance(List<Node> path) {
		double distance = 0.0;
		for(int i=0; i<path.size()-1; i++) {
			Link l = this.getLink(path.get(i), path.get(i+1));
			distance = distance + this.getLengthofLink(l, path.get(i));
		}
		return distance;
	}
	
	private List<Node> getShortestPath(Memcopying mig){
		List<Node> path = null;
		double maxBw =  0.0;
		double minLen = Double.POSITIVE_INFINITY;
		int maxBwNum = -1;
		int minLenNum = -1;
		//all paths p belongs to Pj
		
		Map<Integer, Map<Integer, List<Node>>> multipathMap = this.dataCenter.getNos().getMultiPathTable();
		Map<Integer, List<Node>> paths = multipathMap.get(mig.getChId());
		if(paths == null) {
			this.dataCenter.getNos().findAllPath(mig.getSrcVm(), mig.getDstVm(), mig.getChId());
			multipathMap = this.dataCenter.getNos().getMultiPathTable();
			this.flowPathMap = multipathMap;
		}
			
		paths = multipathMap.get(mig.getChId());
		for(Entry<Integer, List<Node>> p:paths.entrySet()) {
			List<Node> route = p.getValue();
			double bw = this.getPathBandwidth(route);
			double len = this.getPathDistance(route);
			if(maxBw<bw) {
				maxBw = bw;
				maxBwNum = p.getKey();
			}
			if(minLen>len) {
				minLen = len;
				minLenNum = p.getKey();
			}
		}
		
		//path = paths.get(maxBwNum);
		path = paths.get(minLenNum);

		return path;
	}
	
	private double getLengthofLink(Link l, Node from) {
		double len = 0;
		List<Double> lenList =  this.linktoLengthMap.get(l);
		if(lenList!=null) {
			if(l.getLowOrder().equals(from)) {
				len = lenList.get(0);
			}else {
				len = lenList.get(1);
			}
		}else {
			lenList = new ArrayList<>();
			lenList.add(this.sigma);
			lenList.add(this.sigma);
			this.linktoLengthMap.put(l, lenList);
			len = sigma;
		}
		return len;
	}
	
	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public boolean checkPath(Memcopying finish, Memcopying check) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void printPlan() {
		// TODO Auto-generated method stub
		
	}

}
