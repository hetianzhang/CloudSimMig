package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
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
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;

import com.google.common.graph.*;

public abstract class VMMigrationPlanning {
	protected SDNDatacenter dataCenter;
	protected Map<Vm, VmGroup> vmGroups = new HashMap<Vm, VmGroup>();
	protected List<VmGroup> groups;
	protected List<Map<String, Object>> migrationMapping = new LinkedList<Map<String, Object>>();
	protected LinkedList<Memcopying> migWaiting = new LinkedList<>();
	protected LinkedList<Memcopying> migFeasible = new LinkedList<>();
	//TODO change migPlan to LinkedList, each memcopying list indicates one migration path
	protected LinkedList<Memcopying> migPlan = new LinkedList<>(); // used in one by one only
	
	protected Map<Integer, LinkedList<Memcopying>> migPlanMap = new LinkedHashMap<>(); //mig group, group list
	protected LinkedList<Integer> migPlanList = new LinkedList<>(); // sequence of migration group number
	
	private double runTime; //runTime for planning algorithm
	
	protected MutableGraph<Integer> depGraph;
	
	public class Resources{
		public int id;
		
		public int ram;
		public long bw;
		public long disk;
		//public List<Double> mipsList;
		public double maxMips;
		
		public int availableRam;
		public long availableBw;
		public long availableDisk;
		public double availableMips;
		
		public Resources(int ram, long bw, long disk, double maxMips){
			this.ram = ram;
			this.bw = bw;
			this.disk = disk;
			this.maxMips = maxMips;
		}
		
		public Resources(Resources resources) {
			this.ram = resources.ram;
			this.bw = resources.bw;
			this.disk = resources.disk;
			this.maxMips = resources.maxMips;
			this.availableRam = resources.availableRam;
			this.availableBw = resources.availableBw;
			this.availableDisk = resources.availableDisk;
			this.availableMips = resources.availableMips;
		}

		public void setAvailable(int ram, long bw, long disk, double mips) {
			this.availableRam = ram;
			this.availableBw = bw;
			this.availableDisk = disk;
			this.availableMips = mips;
		}	
		
	}
	
	VMMigrationPlanning(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping){
		this.vmGroups = vmGroups;
		this.migrationMapping = migrationMapping;
		this.runTime = 0;
		if(vmGroups != null && vmGroups.size() != 0)
			this.groups = new ArrayList<VmGroup>(this.vmGroups.values());
		
		this.depGraph =GraphBuilder.undirected().build();
	}
	
	VMMigrationPlanning(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter d){
		this.vmGroups = vmGroups;
		this.migrationMapping = migrationMapping;
		this.dataCenter = d;
		this.runTime = 0;
		if(vmGroups != null && vmGroups.size() != 0)
			this.groups = new ArrayList<VmGroup>(this.vmGroups.values());
		
		this.depGraph =GraphBuilder.undirected().build();
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
		
		//delay-aware migration
		act.setDeadline(vm.getDeadline());
		return act;
	}
	
	
	// play with the vmGroups, groups, migrationMapping into a migPlan
	// based on the network path, available BW and the size of migration
	// may call this function when a migration is finished
	public abstract void processMigrationPlan();
	
	public abstract boolean checkHost(Memcopying finish, Memcopying check);
	
	public abstract boolean checkPath(Memcopying finish, Memcopying check);
	
	public boolean checkIndependent(Memcopying a, Memcopying b) {
		//if(Configuration.DEAFULT_ROUTING_WAN) {
			if(a.getSrcHost() == b.getSrcHost() && a.getDstHost() == b.getDstHost())
				return false;
		//}else {
		//	if(a.getSrcHost() == b.getSrcHost() || a.getDstHost() == b.getDstHost() || a.getChId() == b.getChId())
		//		return false;
		//}
		return true;
	}
	
	public boolean checkIndepScheduling(Memcopying a, Memcopying b) {
		boolean flag = true;
		if(this.depGraph.hasEdgeConnecting(a.getId(), b.getId()))
			flag = false;
		return flag;
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
	
	public void setMigWaitingList(List<Memcopying> migWaiting) {
		this.migWaiting = new LinkedList<>(migWaiting);
	}
	
	public List<Memcopying> getMigWaitingList() {
		return migWaiting;
	}
	
	public List<Memcopying> getMigrationPlan() {
		return this.migPlan;
	}
	
	public Map<Integer,LinkedList<Memcopying>> getMigrationPlanMap() {
		return this.migPlanMap;
	}
	
	public abstract void printPlan();
	
	public LinkedList<Integer> getMigrationPlanList(){
		return this.migPlanList;
	}

	public double getRunTime() {
		return runTime;
	}

	public void setRunTime(double runTime) {
		this.runTime = runTime;
	}
	
}
