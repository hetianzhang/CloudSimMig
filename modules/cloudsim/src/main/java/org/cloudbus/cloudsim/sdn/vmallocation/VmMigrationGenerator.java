package org.cloudbus.cloudsim.sdn.vmallocation;

import java.io.BufferedReader;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning.Resources;

public class VmMigrationGenerator {
	
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
	
	//NEW for edge user mobility
	List<Map<String, Object>> usermobilityMap;
	List<Map<String, Object>> migrationMap; //migration requests
	private Map<Integer, Resources> currentHostResources;
	
	public VmMigrationGenerator() {
		this.currentHostResources = new HashMap<>();
		this.migrationMap = new ArrayList<>();
		if(Configuration.DEAFULT_ROUTING_EDGE)
			this.usermobilityMap = new ArrayList<>();
	}
	
	private void initResource(SDNDatacenter dataCenter) {
		//Initialize current host resources
		for(Host host:dataCenter.getHostList()) {
			Resources e = new Resources(host.getRam(), host.getBw(), host.getStorage(), host.getTotalMips());
			e.setAvailable(host.getRamProvisioner().getAvailableRam(), host.getBwProvisioner().getAvailableBw(), host.getStorage(), host.getVmScheduler().getAvailableMips());
			e.id = host.getId();
			this.currentHostResources.put(e.id, e);
		}
	}
	
	private boolean checkFeasible(Integer dstHost, Vm migVm) {
		Resources e = this.currentHostResources.get(dstHost);
		if(e.availableDisk > migVm.getSize() && e.availableMips > migVm.getMips() &&
				e.availableRam > migVm.getRam())
			return true;
		return false;
	}
	
	private Map<Integer, Resources> updateResources(Map<String, Object> mig) {
		Map<Integer, Resources> updatedResources = new HashMap<Integer, Resources>(currentHostResources);
		Vm vm = (Vm) mig.get("vm");
		Host h = (Host) mig.get("host");
		Host srcHost = vm.getHost();

		Resources src = new Resources(updatedResources.get(srcHost.getId()));
			
		src.availableBw += vm.getBw();
		src.availableDisk += vm.getSize();
		src.availableMips += vm.getMips();
		src.availableRam = vm.getRam();
			
		updatedResources.put(srcHost.getId(), src);
			
		Resources src1 = new Resources(updatedResources.get(h.getId()));
		src1.availableBw -= vm.getBw();
		src1.availableDisk -= vm.getSize();
		src1.availableMips -= vm.getMips();
		src1.availableRam -= vm.getRam();
			
		updatedResources.put(h.getId(), src);
		return updatedResources;
	}
	
	public void getHostSchedulerInfo(SDNDatacenter d) {
		List<Host> hList = d.getHostList();
		for(Host h:hList) {
			System.out.println("Host "+h.getId()+" :");
			List<Vm> vList = h.getVmList();
			for(Vm v:vList) {
				System.out.println("vm: "+v.getId());
				List<Double> allocatedMips = h.getVmScheduler().getAllocatedMipsForVm(v);
				for(int i =0; i < allocatedMips.size(); i++) {
					System.out.println("mips: "+allocatedMips.get(i));
				}
			}}
	}
	
	public List<Map<String, Object>> generateMigrations(SDNDatacenter d) {
		List<Vm> vmList = d.getVmList();
		for(int i =0; i<vmList.size()/20; i = i+1) {
			Map<String, Object> mig = new HashMap<>();
			Vm vm = vmList.get(i);
			mig.put("vm", vm);
			Host srcHost = vm.getHost();
			int dstHostNum = this.fattreeCrossAggre(srcHost.getId(), 8);
			//int dstHostNum = this.fattreeCrossCore(srcHost.getId(), 8);
			if(dstHostNum != -1) {
				Host dstHost = d.findHost(dstHostNum);
				mig.put("host", dstHost);
				this.addMigration(mig);
			}
		}
		return this.getMigrationMap();
	}
	
	public List<Map<String, Object>> getUserMobilityMap(){
		return this.usermobilityMap;
	}
	
	public List<Map<String, Object>> getMigrationMap(){
		return this.migrationMap;
	}
	
	private boolean isFit(Host h, Vm vm) {
		long vmsize = 0;
		double vmram = 0;
		List<Vm> vmList = h.getVmList();
		for(int j=0; j<vmList.size(); j++) {
			Vm v = vmList.get(j);
			vmsize += v.getSize();
			vmram += v.getRam();
		}
		if(h.getAvailableMips() > vm.getMips() && (h.getStorage()-vmsize) > vm.getSize() && (h.getRam()-vmram) > vm.getRam())
			return true;
		return false;
	}
	
	//TODO
	public List<Map<String, Object>> generateMostFullFirst(SDNDatacenter d){
		this.initResource(d);
		List<Host> hList = this.getMostFullList(d);
		for(int j =0; j < d.getVmList().size(); j++) {
			Vm vm = d.getVmList().get(j);
			for(int i = 0; i<hList.size(); i++) {
				if(vm.getHost().getId() != hList.get(i).getId())
				if(this.checkFeasible(hList.get(i).getId(), vm)) {
					Map<String, Object> mig = new HashMap<>();
					mig.put("vm",vm);
					mig.put("host", hList.get(i));
					this.addMigration(mig);
					this.updateResources(mig);
					break;
				}					
			}
		}
		return this.getMigrationMap();
	}
	
	public List<Map<String, Object>> generateConsolidate(SDNDatacenter d){
		//consolidate the VM in the same pod if possible
		this.initResource(d);
		List<Host> hList = this.getMostFullList(d);
		for(int j =0; j < d.getVmList().size(); j++) {
			
			Vm vm = d.getVmList().get(j);
			for(int i = 0; i<hList.size(); i++) {
				if(vm.getHost().getId() == hList.get(i).getId())
					break;
				if(this.checkFeasible(hList.get(i).getId(), vm)) {
					Map<String, Object> mig = new HashMap<>();
					mig.put("vm",vm);
					mig.put("host", hList.get(i));
					this.addMigration(mig);
					this.updateResources(mig);
					break;
				}					
			}
		}
		return this.getMigrationMap();
	}
	public List<Map<String, Object>> generateLeastFullFirst(SDNDatacenter d){
		this.initResource(d);
		List<Host> hList = this.getLeastFullList(d);
		for(int j =0; j < d.getVmList().size(); j++) {
			Vm vm = d.getVmList().get(j);
			for(int i = 0; i<hList.size(); i++) {
				if(vm.getHost().getId() != hList.get(i).getId())
				if(this.checkFeasible(hList.get(i).getId(), vm)) {
					Map<String, Object> mig = new HashMap<>();
					mig.put("vm",vm);
					mig.put("host", hList.get(i));
					this.addMigration(mig);
					this.updateResources(mig);
					break;
				}					
			}
		}
		return this.getMigrationMap();
	}
	
    public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
        List<Entry<K, V>> list = new ArrayList<>(map.entrySet());
        list.sort(Entry.comparingByValue());

        Map<K, V> result = new LinkedHashMap<>();
        for (Entry<K, V> entry : list) {
            result.put(entry.getKey(), entry.getValue());
        }

        return result;
    }
    
	public List<Host> getMostFullList(SDNDatacenter d){
		List<Host> mostfullList = new ArrayList<>(d.getHostList());
		Map<Host, Double> hostScoreMap = new HashMap<>();
		for(int i =0;i<mostfullList.size();i++) {
			Host h = mostfullList.get(i);
			long disk = h.getStorage();
			double totalmips = h.getTotalMips();
			double mips = h.getAvailableMips();
			double ram = h.getRam();
			long vmsize = 0;
			double vmram = 0;
			List<Vm> vmList = h.getVmList();
			for(int j=0; j<vmList.size(); j++) {
				Vm vm = vmList.get(j);
				vmsize += vm.getSize();
				vmram += vm.getRam();
			}
			double utilization = vmsize/disk + vmram/ram + mips/totalmips;
			hostScoreMap.put(h, utilization);
		}
		Map<Host, Double> sortedMap = sortByValue(hostScoreMap);
		List<Host> sortMostFullList = new ArrayList<>(sortedMap.keySet());
		return sortMostFullList;
	}
	
	public List<Host> getLeastFullList(SDNDatacenter d){
		List<Host> mostfullList = new ArrayList<>(d.getHostList());
		Map<Host, Double> hostScoreMap = new HashMap<>();
		for(int i =0;i<mostfullList.size();i++) {
			Host h = mostfullList.get(i);
			long disk = h.getStorage();
			double totalmips = h.getTotalMips();
			double mips = h.getAvailableMips();
			double ram = h.getRam();
			long vmsize = 0;
			double vmram = 0;
			List<Vm> vmList = h.getVmList();
			for(int j=0; j<vmList.size(); j++) {
				Vm vm = vmList.get(j);
				vmsize += vm.getSize();
				vmram += vm.getRam();
			}
			double utilization = vmsize/disk + vmram/ram + mips/totalmips;
			hostScoreMap.put(h, -utilization);
		}
		Map<Host, Double> sortedMap = sortByValue(hostScoreMap);
		List<Host> sortLeastFullList = new ArrayList<>(sortedMap.keySet());
		return sortLeastFullList;
	}
	
	private int fattreeCrossCore(int srcHost, int k) {
		//k port number hosts = (k^3)/4, pod = (k/2)^2=16, each aggregation switch - 8/2 core
		// 8/2 edge
		int dstHost = -1;
		int total = (int) (Math.pow(k, 3)/4);
		int podN = (int) (Math.pow(k/2, 2));
		int mod = srcHost % podN;
		//System.out.println(mod);
		int pod = srcHost / podN;
		//System.out.println(pod);
		dstHost = mod + (pod+1)*podN%total;
		return dstHost;
	}
	
	private int fattreeCrossAggre(int srcHost, int k) {
		int dstHost = -1;
		//int total = (int) (Math.pow(k, 3)/4);
		int podN = (int) (Math.pow(k/2, 2));
		int mod = srcHost % podN;
		int pod = srcHost / podN;
		// host k/2 - edge k/2 - aggregation k/2 - core k/2
		int edge = mod/(k/2);
		int edgeMod = mod%(k/2);
		dstHost = pod*podN + (edge+1)%(k/2)*(k/2)+edgeMod; 
		return dstHost;
	}
	
	private int fattreeCrossEdge(int srcHost, int k) {
		int dstHost = -1;
		//int total = (int) (Math.pow(k, 3)/4);
		int podN = (int) (Math.pow(k/2, 2));
		int mod = srcHost % podN;
		int pod = srcHost / podN;
		// host k/2 - edge k/2 - aggregation k/2 - core k/2
		int edge = mod/(k/2);
		int edgeMod = mod%(k/2);
		dstHost = pod*podN + edge*(k/2) + (edgeMod+1)%(k/2);
		return dstHost;
	}
	
	
	//NEW edge user mobility
	private void addUserMobility(Map<String, Object> mov) {
		this.usermobilityMap.add(mov);
	}
	
	
	private void addMigration(Map<String, Object> mig) {
		this.migrationMap.add(mig);
	}
	
	/**
	 * generate one-off migration plan for small-scale dep mig experiments, don't check currentTime, read all lines
	 * @param d
	 * @param vmList
	 * @param migrationPlanFile
	 * @return
	 */
	public List<Map<String, Object>> generateDepMigMigrationExp(SDNDatacenter d, List<SDNVm> vmList, String migrationPlanFile){	
		//initial the migration map for this round (scheduling interval) of multiple migrations
		this.migrationMap.clear();
		try{
			BufferedReader reader = new BufferedReader(new FileReader(migrationPlanFile));
			String row;
			//vmId, Time, srcHostId, dstHostId, memorySize
			//double currentTime = CloudSim.clock();
			while((row = reader.readLine())!=null) {
				String[] data = row.split(",");
				//double migTime = Double.valueOf(data[1]) - 0.1;
				//if(currentTime != migTime)
				//	continue;
				String vmName = data[0];
				int srcHostId = Integer.valueOf(data[1]);
				int dstHostId = Integer.valueOf(data[2]);
				//check whether vm is in srcHost
				
				//vmName may be different from the vmId
				int vmId = d.getVmNameIdTable().get(vmName);
				SDNVm vm = d.findVm(vmId);
				if(!d.findHost(srcHostId).getVmList().contains(vm)) {
					System.out.println("vm" + vm.getId()+ " is not in the src Host");
					continue;
				}
				if(vm.getHost().getId() != srcHostId) {
					System.out.println("vm "+vm.getId()+" is not in the src Host");
					continue;
				}
				Map<String, Object> mig = new HashMap<>();
				mig.put("vm", vm);
				mig.put("host", d.findHost(dstHostId));
				this.addMigration(mig);
			}
		}catch(Exception e) {
			System.out.println(e);
		}
		return this.getMigrationMap();
	}
	
	
	/**
	 * generate Migrations HashMap List based on the migrationPlanFile input and current CloudSim Simulation TIme
	 * @param d
	 * @param vmList
	 * @param migrationPlanFile
	 * @return
	 */
	public List<Map<String, Object>> generatePowerMigrationExp(SDNDatacenter d, List<SDNVm> vmList, String migrationPlanFile){	
		//initial the migration map for this round (scheduling interval) of multiple migrations
		this.migrationMap.clear();
		try{
			BufferedReader reader = new BufferedReader(new FileReader(migrationPlanFile));
			String row;
			//vmId, Time, srcHostId, dstHostId, memorySize
			double currentTime = CloudSim.clock();
			while((row = reader.readLine())!=null) {
				String[] data = row.split(",");
				double migTime = Double.valueOf(data[1]) - 0.1;
				if(currentTime != migTime)
					continue;
				String vmName = data[0];
				int srcHostId = Integer.valueOf(data[2]);
				int dstHostId = Integer.valueOf(data[3]);
				//check whether vm is in srcHost
				
				//vmName may be different from the vmId
				int vmId = d.getVmNameIdTable().get(vmName);
				SDNVm vm = d.findVm(vmId);
				if(!d.findHost(srcHostId).getVmList().contains(vm)) {
					System.out.println("vm" + vm.getId()+ " is not in the src Host");
					continue;
				}
				if(vm.getHost().getId() != srcHostId) {
					System.out.println("vm "+vm.getId()+" is not in the src Host");
					continue;
				}
				Map<String, Object> mig = new HashMap<>();
				mig.put("vm", vm);
				mig.put("host", d.findHost(dstHostId));
				this.addMigration(mig);
			}
		}catch(Exception e) {
			System.out.println(e);
		}
		return this.getMigrationMap();
	}
	
	public List<Map<String, Object>> generatePaperMigrationsExpSingle(SDNDatacenter d, List<SDNVm> vmList){
		
		for(SDNVm vm:vmList) {
			if(vm.getHost().getId()!=0) {
				Map<String, Object> mig = new HashMap<>();
				mig.put("vm", vm);
				mig.put("host", d.findHost(0));
				this.addMigration(mig);
			}else {
				//Map<String, Object> mig = new HashMap<>();
				//mig.put("vm", vm);
				//mig.put("host", d.findHost(3));
				//this.addMigration(mig);
			}
		}
		return this.getMigrationMap();
	}
	
	public List<Map<String, Object>> generatePaperMigrations(SDNDatacenter d) {
		//this.initResource(d);
		
		List<Host> hList = d.getHostList();
		List<SDNVm> vmList = d.getVmList();
		List<String> vmMigList = Arrays.asList("v11","v14","v21","v22","v31","v41","v51","v61");
		List<String> dstHostList = Arrays.asList("h_0_0_1","h_0_0_0","h_0_1_1","h_0_1_1","h_0_1_1","h_0_1_1","h_0_1_1","h_0_1_1");
		
		for(int i = 0; i< vmList.size(); i++) {
			System.out.println(vmList.get(i).getName());
			System.out.println("SDNHost "+vmList.get(i).getHost().getId());
		}
		
		for(int i =0; i< vmMigList.size(); i++) {
			for(int j =0; i< vmList.size(); j++) {
				String checkName = vmList.get(j).getName();
				if(vmMigList.get(i).equalsIgnoreCase(checkName)) {
					Map<String, Object> mig = new HashMap<>();
					mig.put("vm", vmList.get(j));
					mig.put("host", d.getNos().getNameNodeTable().get(dstHostList.get(i)));
					this.addMigration(mig);
					break;
				}
			}
		}
		
		return this.getMigrationMap();
	}
	
	//[NEW] edge live container migration paper
	private boolean checkEdgeMigrationFeasibility(String mig1, String mig2) {
		boolean feasible = true;
		String[] data1 = mig1.split(",");
		String[] data2 = mig2.split(",");
		if(data1[0].equalsIgnoreCase("11369") || data2[0].equalsIgnoreCase("11369"))
			return false;
		if(data1[0].equalsIgnoreCase(data2[0])) {
			if(data1[1].equalsIgnoreCase("-1") || data1[2].equalsIgnoreCase("-1"))
				feasible = false;
			if(data2[1].equalsIgnoreCase("-1") || data2[2].equalsIgnoreCase("-1"))
				feasible = false;
			double t1 = Double.valueOf(data1[3]);
			double t2 = Double.valueOf(data2[3]);
			double d1;
			double d2;
			if(data1[4].equalsIgnoreCase("inf"))
				d1 = 1800;
			else
				d1 = Double.valueOf(data1[4]);
			if(data2[4].equalsIgnoreCase("inf"))
				d2 = 1800;
			else
				d2 = Double.valueOf(data2[4]);
			//double d2 = Double.valueOf(data2[4]);
			//if(t1+20*2>t2+d2)
			//	feasible = false;
		}else {
			feasible = true;
		}
		return feasible;
	}
	
	private boolean checkEdgeMigrationFeasibility(String mig1) {
		String[] data1 = mig1.split(",");
		if(data1[0].equalsIgnoreCase("11369"))
			return false;
		if(data1[1].equalsIgnoreCase("-1") || data1[2].equalsIgnoreCase("-1"))
			return false;
		else
			return true;
	}
	
	
	public List<Map<String, Object>> generateEdgePaperMigrations(SDNDatacenter d, List<SDNVm> containerList, String migrationPlanFile) {
		//read all Edge live container migration CSV file regardless current time
		//container id, source id, destination id, arrival time, deadline
		
		//initial the migration map for this round (scheduling interval) of multiple migrations
				this.migrationMap.clear();
				try{
					BufferedReader reader = new BufferedReader(new FileReader(migrationPlanFile));
					String row;
					String prevSrcId = "-1";
					
					
					//vmId, Time, srcHostId, dstHostId, memorySize
					double currentTime = CloudSim.clock();
					while((row = reader.readLine())!=null) {
						String[] data = row.split(",");
						
						//new id for live container migration
						if(data[1].equalsIgnoreCase("-1")) {
							prevSrcId = data[2];
						}
						
						
						if(d.getVmNameIdTable().get("c-"+data[0]) == null)
							continue;
						
						//check and filter the migration item is feasible based on time
						reader.mark(2000);
						String row1 =null;
						boolean feasible = false;
						if((row1 = reader.readLine())!=null) {
							feasible = this.checkEdgeMigrationFeasibility(row, row1);
						}else {
							feasible = this.checkEdgeMigrationFeasibility(row);
						}
						reader.reset();
						if(feasible) {
							//TODO if not feasible, keep the src for mig1 and dst for mig2, but change dst for mig1
							//src for mig2
							
							//all live container migrations
							//double migTime = Double.valueOf(data[3]) - 0.1;
							
							
							//if(currentTime+Configuration.migrationTimeInterval < migTime)
							//	continue;
							
							
							
							String id = data[0];
							String containerName = "c-" +id;
							String userName = "u-" + id;
							String srcId = data[1];							
							String dstId = data[2];
							srcId = prevSrcId;
							prevSrcId = dstId;
							
							String srcEdge = "edge"+srcId;
							String srcBase = "base"+srcId;
							String dstEdge = "edge"+dstId;
							String dstBase = "base"+dstId;
							
							
							int srcEdgeId = d.getNos().getNameNodeTable().get(srcEdge).getAddress();
							int srcBaseId = d.getNos().getNameNodeTable().get(srcBase).getAddress();
							int dstEdgeId = d.getNos().getNameNodeTable().get(dstEdge).getAddress();
							int dstBaseId = d.getNos().getNameNodeTable().get(dstBase).getAddress();
							//check whether vm is in srcHost
							
							//vmName may be different from the vmId
							int containerId = d.getVmNameIdTable().get(containerName);
							int userId = d.getVmNameIdTable().get(userName);
							SDNVm c = d.findVm(containerId);
							SDNVm u = d.findVm(userId);
							
							/*
							SDNHost srcHost = d.findHost(srcEdgeId);
							SDNHost srcHost1 = (SDNHost) d.getNos().getNameNodeTable().get(srcEdge);
							
							//check src edge data center and base station for live container migration
							if(!d.findHost(srcEdgeId).getVmList().contains(c)) {
								System.out.println("container " + c.getId()+ " is not in the src Edge Data Center");
								continue;
							}
							if(c.getHost().getId() != srcEdgeId) {
								System.out.println("container "+c.getId()+" is not in the src Edge Data Center");
								continue;
							}
							
							if(!d.findHost(srcBaseId).getVmList().contains(u)) {
								System.out.println("user " + u.getId()+ " is not in the src Base Station");
								continue;
							}
							if(u.getHost().getId() != srcBaseId) {
								System.out.println("user "+u.getId()+" is not in the src Base Station");
								continue;
							}
							*/
							
							Map<String, Object> mig = new HashMap<>();
							mig.put("vm", c);
							//must add src Host to migration, compared to previous periodically triggered scheduling
							mig.put("src", d.findHost(srcEdgeId));
							mig.put("host", d.findHost(dstEdgeId));
							mig.put("time", currentTime+Double.valueOf(data[3])); //time offset
							if(data[4].equalsIgnoreCase("inf"))
								mig.put("deadline", 1800.0);
							else
								mig.put("deadline", Double.valueOf(data[4]));
							this.addMigration(mig);
							
							//user mobility reallocate immediately
							Map<String, Object> usermov = new HashMap<>();
							usermov.put("user", u);
							usermov.put("base", d.findHost(dstBaseId));
							usermov.put("time", currentTime+Double.valueOf(data[3]));
							this.addUserMobility(usermov);
						}					
					}
				}catch(Exception e) {
					e.printStackTrace();
					System.out.println(e);
				}
				return this.getMigrationMap();
	}
}
