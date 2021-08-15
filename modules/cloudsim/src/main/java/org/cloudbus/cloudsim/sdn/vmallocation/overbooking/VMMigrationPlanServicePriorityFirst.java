package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.VmGroup;

public class VMMigrationPlanServicePriorityFirst extends VMMigrationPlanning{

	VMMigrationPlanServicePriorityFirst(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping) {
		super(vmGroups, migrationMapping);
		// TODO Auto-generated constructor stub
	}

	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		
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
