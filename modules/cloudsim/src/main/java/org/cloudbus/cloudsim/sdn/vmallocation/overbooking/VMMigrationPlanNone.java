package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;

public class VMMigrationPlanNone extends VMMigrationPlanning {

	SDNDatacenter dataCenter;
	private int groupNum;
	private int migNum;
	private Map<Integer, List<Node>> migRouteTable;
	
	public VMMigrationPlanNone(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
		super(vmGroups, migrationMapping);
		// TODO Auto-generated constructor stub
		this.dataCenter = dataCenter;
		this.groupNum = 0;
		this.migNum = this.migrationMapping.size();
		this.migRouteTable = new HashMap<>();
		
		for(Map<String, Object> migrate: migrationMapping) {
			this.migNum++;
			Memcopying m = this.toMemcopying(migrate);
			m.setId(this.migNum);
			this.migWaiting.add(m);
		}
	}

	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		// all migration in one migration group
		LinkedList<Memcopying> groupMigList = new LinkedList<>();
		groupMigList.addAll(this.migWaiting);
		this.migPlanMap.put(groupNum, groupMigList);
		
	}
	
	protected Memcopying toMemcopying(Map<String, Object> migrate) {
		SDNVm vm = (SDNVm) migrate.get("vm");
		Host host = (Host) migrate.get("host");
		
		vm.setRemainedDirtyPg((long) (vm.getRam()*Math.pow(10,9)));
		
		int srcHost = vm.getHost().getId();
		int dstHost = host.getId();
		int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost);
		int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
		long amountMem = vm.getRam();
		double startTime = CloudSim.clock(); // start all at the same time
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
		
		return act;
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
	
	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		if(finish.getSrcHost() == check.getSrcHost() && finish.getDstHost() == check.getDstHost())
			return true;
		else
			return false;
	}

	@Override
	public boolean checkPath(Memcopying finish, Memcopying check) {
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

	@Override
	public void printPlan() {
		// TODO Auto-generated method stub
		
	}

}
