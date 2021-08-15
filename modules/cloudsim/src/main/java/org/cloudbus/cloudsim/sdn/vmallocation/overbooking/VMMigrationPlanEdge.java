package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;
/**
 * Migration Grouping Algorithm used in Edge computing Environments
 */
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;

public class VMMigrationPlanEdge extends VMMigrationPlanIterMIS {

	public VMMigrationPlanEdge(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter d) {
		super(vmGroups, migrationMapping, d);

		for(Map<String, Object> mig:migrationMapping) {
			this.migNum ++;
			Memcopying mc = toMemcopying(mig);
			mc.setId(migNum);
			this.migPlan.add(mc);
		}
	}
	
	
	@Override
	public Memcopying toMemcopying(Map<String, Object> migrate) {
		//"vm", "src", "host", "time", "deadline"
		SDNVm vm = (SDNVm) migrate.get("vm");
		Host host = (Host) migrate.get("host");
		Host src = (Host) migrate.get("src");
		
		double startTime;
		startTime= (double) migrate.get("time"); //request delay relative to the start time 0
		double currentTime = CloudSim.clock();
		double delay = startTime-currentTime;
		double deadline = (double) migrate.get("deadline"); //relative migration deadline to the start time 0
		
		
		vm.setRemainedDirtyPg((long) (vm.getRam()*8*Math.pow(10,9)));
		
		int srcHost = src.getId();
		int dstHost = host.getId();
		int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost); //fake container id for live migration flow
		int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
		long amountMem = vm.getRam();
		if(vm.getName().equalsIgnoreCase("c-10153")) {
			System.out.println("arrival time "+startTime+" "+vm.getName()+" src equals to dst! "+srcHost+" "+dstHost);
		}
		
		if(srcHost == dstHost) {
			System.out.println("arrival time "+startTime+" "+vm.getName()+" src equals to dst! "+srcHost+" "+dstHost);
			System.exit(1);
		}
		
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
		
		//delay-aware migration
		act.setDeadline(deadline);
		return act;
	}

	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		
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
