package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.stream.Collectors;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.Request;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning.Resources;


public class VMMigrationPlanOnebyOne extends VMMigrationPlanning{
	SDNDatacenter dataCenter;
	List<Memcopying> migList;
	Map<Integer, Object> idtoVmMap;
	Map<Integer, Object> idtoHostMap;
	List<Vm> vmList;
	private int migNum;
	//List<Host> currentHostList;
	
	//Map of host resource for feasible check
	private Map<Integer, Resources> currentHostResources;
	
	public VMMigrationPlanOnebyOne(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping, dataCenter);
		// TODO Auto-generated constructor stub
		this.dataCenter = dataCenter;
		this.currentHostResources = new HashMap<>();
		
		for(Map<String, Object> migrate: migrationMapping) {
			this.migNum++;
			Memcopying m = this.toMemcopying(migrate);
			m.setId(this.migNum);
			this.migWaiting.add(m);
			Map<Integer, List<Node>> multiPaths = this.dataCenter.getNos().findAllPath(m.getSrcVm(), m.getDstVm());
			this.dataCenter.getNos().getMultiPathTable().put(m.getChId(), multiPaths);
			//this.dataCenter.getNos().findAllPath(m.getSrcVm(), m.getDstVm(), m.getChId());
		}
		
		for(Host host:this.dataCenter.getHostList()) {
			Resources e = new Resources(host.getRam(), host.getBw(), host.getStorage(), host.getTotalMips());
			e.setAvailable(host.getRamProvisioner().getAvailableRam(), host.getBwProvisioner().getAvailableBw(), host.getStorage(), 
					host.getVmScheduler().getAvailableMips());
			e.id = host.getId();
			this.currentHostResources.put(e.id, e);
		}
	}

	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		
		// INPUT: Physical Topo, Virtual Topo, VM Mapping to Host
		// INPUT: Migration Tasks Set
		// OUTPUT: Migration sequence one by one
		
		//1. randomMigrationPlanning
		//this.randomMigrationPlan();
		
		//2. Heuristic Planning
		double startTime = System.nanoTime();
		this.heuristicPlan();
		double endTime = System.nanoTime();
		double runT = this.getRunTime();
		runT += (endTime-startTime)/1000000;
		this.setRunTime(runT);
	}
	
	@SuppressWarnings("unchecked")
	private void heuristicPlan() {
		// A simple heuristic for sequence planning: 
		//int migSize = this.migrationMapping.size();
		int migSize = this.migWaiting.size();
		Map<Memcopying, Integer> scoreMap = new LinkedHashMap<>();
		
		//random ordering of migration set
		//Collections.shuffle(this.migrationMapping);
		Collections.shuffle(this.migWaiting);
		//this.toMemcopyingList();
		
		for(Memcopying mig: this.migWaiting) {
			int score = this.getScore(mig);
			scoreMap.put(mig, score);
		}
		
		//for(Map<String, Object> mig: this.migrationMapping) {
		//	int score = this.getScore(mig);
		//	scoreMap.put(mig, score);
		//}
		Map<Memcopying, Integer> sortedMap = scoreMap.entrySet().stream().
		sorted(Map.Entry.comparingByValue(Comparator.reverseOrder())).
		collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue,
				(oldValue, newValue)->oldValue, LinkedHashMap::new));
		for(Entry<Memcopying, Integer> en:sortedMap.entrySet()) {
			this.migPlan.add(en.getKey());
		}
		//this.migrationMapping = (List<Map<String, Object>>) sortedMap.keySet();
	}
	
	private void toMemcopyingList() {
		for (Map<String, Object> migrate: this.migrationMapping) {
			Vm vm = (Vm) migrate.get("vm");
			this.vmList.add(vm);
			Host host = (Host) migrate.get("Host");
			
			int srcHost = vm.getHost().getId();
			int dstHost = host.getId();
			int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost);
			int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
			long amountMem = vm.getRam();
			double startTime = CloudSim.clock();
			double currentTime = startTime;
			
			Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
			act.setMigVm(vm.getId());
			act.setPrecopy(true);
			act.setStopandcopy(false);
			
			//restore vm information
			act.setVmMips(vm.getCurrentAllocatedMips());
			act.setVmDisk(vm.getSize());
			act.setVmBw(vm.getCurrentAllocatedBw());
			act.setVmRAM(vm.getRam());
			
			this.migPlan.add(act);
		}
	}
	
	private Vm getVMbyNum(int vmNum) {
		for(int i=0;i<this.vmList.size();i++) {
			if(vmNum == vmList.get(i).getId()) {
				return vmList.get(i);
			}
		}
		return null;
	}
	
	private int getScore(Memcopying migrate) {
		int score = 0;
		 Map<Integer, Resources> tempResources = new HashMap<>(this.currentHostResources);
		if(checkFeasible(migrate, tempResources)) {
			for(Memcopying mig: this.getMigWaitingList()) {
				if(this.checkFeasible(mig, tempResources))
					score++;
			}
		}
		return score;
	}
	
	private int getScore(Map<String, Object> migrate) {
		// if the migrate is performed, the number of feasible migrations in migration set ()
		int score = 0;
	    Map<Integer, Resources> tempResources = this.currentHostResources;
	    if(checkFeasible(migrate, tempResources)) {
	    	for(Map<String, Object> mig: this.migrationMapping) {
	    		if(this.checkFeasible(mig, tempResources))
	    			score++;
	    	}
	    }
		return score;
	}
	
	private boolean checkFeasible(Memcopying migrate, Map<Integer, Resources> currentHosts) {
		SDNVm vm = (SDNVm) migrate.migrate.get("vm");
		SDNHost host = (SDNHost) migrate.migrate.get("host");
		Resources e = currentHosts.get(host.getId());
		if (!host.getVmsMigratingIn().contains(vm)) {
			if (e.availableDisk < vm.getSize()) {
				return false;
			}

			if (e.availableRam < vm.getCurrentRequestedRam()) {
				return false;
			}

			if (e.availableBw < vm.getCurrentRequestedBw()) {
				return false;
			}

			if (e.availableMips < vm.getCurrentRequestedTotalMips()) {
				return false;
			} else {
				// work on currentHosts	
				Resources srcHost = currentHosts.get(vm.getHost().getId());
				srcHost.setAvailable(srcHost.availableRam + vm.getCurrentRequestedRam(),srcHost.availableBw+vm.getCurrentRequestedBw(),
						srcHost.availableDisk+vm.getCurrentAllocatedSize(), srcHost.availableMips+vm.getCurrentRequestedTotalMips());
				
				e.setAvailable(e.availableRam-vm.getCurrentRequestedRam(), e.availableBw-vm.getCurrentRequestedBw(),
						e.availableDisk-vm.getCurrentAllocatedSize(), e.availableMips-vm.getCurrentRequestedTotalMips());
				return true;
				}
			}else
				return false;
	}
	
	private boolean checkFeasible(Map<String, Object> migrate, Map<Integer, Resources> currentHosts) {
		Vm vm = (Vm) migrate.get("vm");
		
		Host host = (Host) migrate.get("Host");
		Resources e = currentHosts.get(host.getId());
		if (!host.getVmsMigratingIn().contains(vm)) {
			if (e.availableDisk < vm.getSize()) {
				return false;
			}

			if (e.availableRam < vm.getCurrentRequestedRam()) {
				return false;
			}

			if (e.availableBw < vm.getCurrentRequestedBw()) {
				return false;
			}

			if (e.availableMips < vm.getCurrentRequestedTotalMips()) {
				return false;
			} else {
				// work on currentHosts	
				Resources srcHost = currentHosts.get(vm.getHost().getId());
				srcHost.setAvailable(srcHost.availableRam + vm.getCurrentRequestedRam(),srcHost.availableBw+vm.getCurrentRequestedBw(),
						srcHost.availableDisk+vm.getCurrentAllocatedSize(), srcHost.availableMips+vm.getCurrentRequestedTotalMips());
				
				e.setAvailable(e.availableRam-vm.getCurrentRequestedRam(), e.availableBw-vm.getCurrentRequestedBw(),
						e.availableDisk-vm.getCurrentAllocatedSize(), e.availableMips-vm.getCurrentRequestedTotalMips());
				return true;
				}
			}else
				return false;
					
	}
	/*private boolean checkFeasible(Map<String, Object> migrate, Map<String, Object> score) {
		
		Host scoreHost = (Host) score.get("Host");
		Vm scoreVm = (Vm) migrate.get("vm");
		
		
		// get the resource requirement
		Vm vm = (Vm) migrate.get("vm");
		Host host = (Host) migrate.get("Host");
		// check available resources on dst host
		// TODO check to the resouces list usage
		if(host.getId() != scoreHost.getId()) {
		if (!host.getVmsMigratingIn().contains(vm)) {
			if (host.getStorage() < vm.getSize()) {
				return false;
			}

			if (host.getRamProvisioner().getAvailableRam() < vm.getCurrentRequestedRam()) {
				return false;
			}

			if (host.getBwProvisioner().getAvailableBw() < vm.getCurrentRequestedBw()) {
				return false;
			}

			if (host.getVmScheduler().getAvailableMips() < vm.getCurrentRequestedTotalMips()) {
				return false;
			}
		}} else {
			if ((host.getStorage() - scoreVm.getSize()) < vm.getSize()) {
				return false;
			}

			if ((host.getRamProvisioner().getAvailableRam() - scoreVm.getCurrentRequestedRam()) < vm.getCurrentRequestedRam()) {
				return false;
			}

			if ((host.getBwProvisioner().getAvailableBw() - scoreVm.getCurrentRequestedBw()) < vm.getCurrentRequestedBw()) {
				return false;
			}

			if ((host.getVmScheduler().getAvailableMips() - scoreVm.getCurrentRequestedTotalMips()) < vm.getCurrentRequestedTotalMips()) {
				return false;
			}
		}
		return true;
	}
	*/
	
	private void randomMigrationPlan() {
		Collections.shuffle(this.migrationMapping);
		for (Map<String, Object> migrate:this.migrationMapping) {
			Vm vm = (Vm) migrate.get("vm");
			Host host = (Host) migrate.get("Host");
			//host.addMigratingInVm(vm);
			
			int srcHost = vm.getHost().getId();
			int dstHost = host.getId();
			int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost);
			int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
			long amountMem = vm.getRam();
			double startTime = -1;
			double currentTime = startTime;
			
			//Memcopying Activity contains the migration task's info
			Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
			act.setMigVm(vm.getId());
			act.setPrecopy(false);
			act.setStopandcopy(false);
			//send the first Memory copying
			//Request req = new Request(this.dataCenter.getId());
			//req.addActivity(act);
			//Packet pkt = new Packet(act.getSrcVm(), act.getDstVm(), amountMem, this.dataCenter.findMigFlow(srcHost, dstHost), req);
			//this.dataCenter.getNos().addPacketToChannel(pkt);
			this.migPlan.add(act);
		}
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
