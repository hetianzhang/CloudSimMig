package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning.Resources;

public class VMMigrationPlanEDF extends VMMigrationPlanSLAGroup{
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
	
	public VMMigrationPlanEDF(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping, dataCenter);
	}
	

	@Override
	public void processMigrationPlan() {
		// priority based on the slack time for SLA violation threshold
		// SLA level (99.999) 323.4s per year => migration downtime
		double startTime = System.nanoTime();
		this.time = CloudSim.clock();
		int round = 0;
		
		while(this.migWaiting.size()!=0) {
			round++;
			//check current feasible migration tasks
			this.migFeasible= checkFeasible();
			if(migFeasible != null && migFeasible.size() != 0) {
				//sort the migration list
				for(Memcopying m:migFeasible) {
					m.setPriority((int) this.getSlackTime(m));
				}
				this.processOnebyOneEDF();
			}
			
			this.prevWaitSize = this.migWaiting.size();
			
			//remove migrations in the waiting list
			List<Memcopying> toMigList = new ArrayList<>(migFeasible);
			
			if(toMigList.size()!=0) {
				this.getMigrationPlanMap().put(round, migFeasible);
				this.getMigrationPlanList().add(round);
				this.linktoBwMap = this.updateTempLinktoBwMap(migFeasible);
				this.migWaiting.removeAll(toMigList);
			}
			
			if(this.migWaiting.size() == this.prevWaitSize)
				break;
		}
		
		double endTime = System.nanoTime();
		double runT = this.getRunTime();
		runT = runT + (endTime - startTime)/1000000;
		this.setRunTime(runT);
	}

	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		if(finish.getSrcHost() == check.getSrcHost() || finish.getDstHost() == check.getDstHost())
			return true;
		else
			return false;
	}

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
	
	private Memcopying[] toArray(LinkedList<Memcopying> migFeasible) {
		Memcopying[] array = new Memcopying[migFeasible.size()];
		for(int i = 0; i < migFeasible.size(); i++) {
			array[i] = migFeasible.get(i);
		}
		return array;
	}
	
	private int partition(Memcopying[] arr, int begin, int end) {
		int pivot = arr[end].getPriority();
		int i = begin -1;
		for(int j = begin; j < end; j++) {
			if(arr[j].getPriority() <= pivot) {
				i++;
				
				Memcopying swapTemp = arr[i];
				arr[i] = arr[j];
				arr[j] = swapTemp;
			}
		}	
			Memcopying swapTemp = arr[i+1];
			arr[i+1] = arr[end];
			arr[end] = swapTemp;
			
			return i+1;
	}
		
	private void quickSort(Memcopying[] arr, int begin, int end) {
		if(begin < end) {
			int partitionindex = partition(arr, begin, end);
			
			quickSort(arr, begin, partitionindex-1);
			quickSort(arr, partitionindex+1, end);
		}
	}
	
	private void processGroupEDF() {
		//group priority based on the least deadline task in the group
	}
	
	private void processOnebyOneEDF() {
		//sorted list of migration tasks based on deadline + dynamic scheduler in NOS
		Memcopying[] arr = this.toArray(migFeasible);
		quickSort(arr, 0, migFeasible.size()-1);
		migFeasible = new LinkedList<Memcopying>(Arrays.asList(arr));
	}
	
	//Least Slack Time (LST) for EDF priority
	private double getSlackTime(Memcopying mig) {
		double bw = 0;
		Map<Integer, List<Node>>selectPaths =this.getSelectedPaths(mig);
		for(List<Node> p:selectPaths.values()) {
			bw += this.dataCenter.getNos().getPathBandwidth(p, this.linktoBwMap);
		}
		double migTime = this.getMigrationTime(mig, bw);
		return mig.getDeadline()-migTime;
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
	
	//release SRC resources after the migration for dynamic re-calculation	
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

	@Override
	public void printPlan() {
		// TODO Auto-generated method stub
		
	}	

}
