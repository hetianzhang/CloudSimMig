package org.cloudbus.cloudsim.sdn.vmallocation;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.parsers.PhysicalTopologyParser;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemSimple;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.JSONValue;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Node;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
public class VmAllocationPolicyManual extends VmAllocationPolicy implements PowerUtilizationMaxHostInterface{

	
	protected final double hostTotalMips;
	protected final double hostTotalBw;
	protected final int hostTotalPes;
	
	/** The vm table. */
	private Map<String, Host> vmTable;

	/** The used pes. */
	private Map<String, Integer> usedPes;

	/** The free pes. */
	private List<Integer> freePes;
	
	private Map<String, Long> usedMips;
	private List<Long> freeMips;
	private Map<String, Long> usedBw;
	private List<Long> freeBw;
	
	private Map<String, String> vmPreDefinedLocTable;
	JSONArray mapping;
	
	//PhysicalTopologyParser parser;
	NetworkOperatingSystem nos;
	String mappingFileName;
	
	public VmAllocationPolicyManual(List<? extends Host> list, String mappingFileName) {
		super(list);
		// TODO Auto-generated constructor stub
		setFreePes(new ArrayList<Integer>());
		setFreeMips(new ArrayList<Long>());
		setFreeBw(new ArrayList<Long>());
		
		for (Host host : getHostList()) {
			getFreePes().add(host.getNumberOfPes());
			getFreeMips().add((long)host.getTotalMips());
			getFreeBw().add(host.getBw());
		}
		hostTotalMips = getHostList().get(0).getTotalMips();
		hostTotalBw =  getHostList().get(0).getBw();
		hostTotalPes =  getHostList().get(0).getNumberOfPes();

		setVmTable(new HashMap<String, Host>());
		setUsedPes(new HashMap<String, Integer>());
		setUsedMips(new HashMap<String, Long>());
		setUsedBw(new HashMap<String, Long>());
		
		this.mappingFileName = mappingFileName;
		this.loadMappingFile(mappingFileName);
	}

	
	public void setNos(NetworkOperatingSystem nos) {
		this.nos = nos;
	}
	protected double convertWeightedMetric(double mipsPercent, double bwPercent) {
		double ret = mipsPercent * bwPercent;
		return ret;
	}
	
	public void loadMappingFile(String mappingFileName) {
		try {
    		JSONObject doc = (JSONObject) JSONValue.parse(new FileReader(mappingFileName));
    		
    		this.mapping = (JSONArray) doc.get("mappings");

    		
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	@Override
	public boolean allocateHostForVm(Vm vm) {
		if (getVmTable().containsKey(vm.getUid())) { // if this vm was not created
			return false;
		}
		
		if(!(vm instanceof SDNVm))
			return false;
		
		System.out.println("initial allocation: "+((SDNVm)vm).getName()+" "+((SDNVm)vm).getId());
		if(((SDNVm)vm).getId() == 1800) {
			System.out.println("DEBUG allocate container for edge");
		}
		String hostName =null;
		for(int i =0; i< this.mapping.size(); i++) {
			String obj = (String) this.mapping.get(i);
			String[] map = obj.split(":");
			if(((SDNVm)vm).getName().equalsIgnoreCase(map[0])) {
				hostName = map[1];
				break;
			}
		}
		
		if(hostName == null)
			return false;
		Map<String,Node> nameNodeTable = this.nos.getNameNodeTable();
		hostName = hostName.replace(" ", "");
		SDNHost h = (SDNHost) nameNodeTable.get(hostName);
		if(!nameNodeTable.keySet().contains(hostName))
			return false;
		int requiredPes = vm.getNumberOfPes();
		double requiredMips = vm.getCurrentRequestedTotalMips();
		long requiredBw = vm.getCurrentRequestedBw();
		
		int idx = -1;
		for(int i =0; i< this.getHostList().size(); i++) {
			if(getHostList().get(i).getId() == h.getId()) {
				idx = i;
				break;
			}
		}

		boolean result = false;
		try {
		if(requiredMips > h.getAvailableMips()) {
			System.out.println("container number in host: "+h.getVmList().size());
			System.out.println("requiredMips: "+requiredMips+" availableMips: "+ h.getAvailableMips());
			System.out.println("requiredPes: "+requiredPes+" NumberOfFreePes: "+ h.getNumberOfFreePes());
			System.out.println("requiredBw: "+requiredBw+" AvailableBw: "+ h.getBwProvisioner().getAvailableBw());
			throw new Exception(hostName + " : Error - \n this entity has not enough mips "+ ((SDNVm)vm).getName()+" id: "+((SDNVm)vm).getId()+"\n");
			//return result;
		}
		if(requiredPes > h.getNumberOfFreePes())
			throw new Exception(hostName + " : Error - this entity has not enough pes " + ((SDNVm)vm).getName());
			//return result;
		if(requiredBw > h.getBwProvisioner().getAvailableBw())
			throw new Exception(hostName + " : Error - this entity has not enough bw" + ((SDNVm)vm).getName());
			//return result;
		}catch(Exception e) {
			System.out.print(e);	
		}
		
		result = h.vmCreate(vm);
		
		if(vm.getId() == 1800) {
			System.out.println("DEBUG container allocation");
		}
		
		if (result && idx != -1) { // if vm were succesfully created in the host
			getVmTable().put(vm.getUid(), h);
			getUsedPes().put(vm.getUid(), requiredPes);
			getFreePes().set(idx, getFreePes().get(idx) - requiredPes);
				
			getUsedMips().put(vm.getUid(), (long) requiredMips);
			getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - requiredMips));

			getUsedBw().put(vm.getUid(), (long) requiredBw);
			getFreeBw().set(idx,  (long) (getFreeBw().get(idx) - requiredBw));
		}
		
		if(!result) {
			System.err.println("VmAllocationPolicy: WARNING:: Cannot create VM!!!!");
		}
		
		logMaxNumHostsUsed();
		return result;
	}
	
	protected int maxNumHostsUsed=0;
	public void logMaxNumHostsUsed() {
		// Get how many are used
		int numHostsUsed=0;
		for(int freePes:getFreePes()) {
			if(freePes < hostTotalPes) {
				numHostsUsed++;
			}
		}
		if(maxNumHostsUsed < numHostsUsed)
			maxNumHostsUsed = numHostsUsed;
		System.out.println("Number of online hosts:"+numHostsUsed + ", max was ="+maxNumHostsUsed);
	}
	public int getMaxNumHostsUsed() { return maxNumHostsUsed;}

	/**
	 * Releases the host used by a VM.
	 * 
	 * @param vm the vm
	 * @pre $none
	 * @post none
	 */
	@Override
	public void deallocateHostForVm(Vm vm) {
		Host host = getVmTable().remove(vm.getUid());
		if (host != null) {
			int idx = getHostList().indexOf(host);
			host.vmDestroy(vm);
			
			Integer pes = getUsedPes().remove(vm.getUid());
			getFreePes().set(idx, getFreePes().get(idx) + pes);
			
			Long mips = getUsedMips().remove(vm.getUid());
			getFreeMips().set(idx, getFreeMips().get(idx) + mips);
			
			Long bw = getUsedBw().remove(vm.getUid());
			getFreeBw().set(idx, getFreeBw().get(idx) + bw);
		}
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vm the vm
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(Vm vm) {
		return getVmTable().get(vm.getUid());
	}

	/**
	 * Gets the host that is executing the given VM belonging to the given user.
	 * 
	 * @param vmId the vm id
	 * @param userId the user id
	 * @return the Host with the given vmID and userID; $null if not found
	 * @pre $none
	 * @post $none
	 */
	@Override
	public Host getHost(int vmId, int userId) {
		return getVmTable().get(Vm.getUid(userId, vmId));
	}

	/**
	 * Gets the vm table.
	 * 
	 * @return the vm table
	 */
	public Map<String, Host> getVmTable() {
		return vmTable;
	}

	/**
	 * Sets the vm table.
	 * 
	 * @param vmTable the vm table
	 */
	protected void setVmTable(Map<String, Host> vmTable) {
		this.vmTable = vmTable;
	}

	/**
	 * Gets the used pes.
	 * 
	 * @return the used pes
	 */
	protected Map<String, Integer> getUsedPes() {
		return usedPes;
	}

	/**
	 * Sets the used pes.
	 * 
	 * @param usedPes the used pes
	 */
	protected void setUsedPes(Map<String, Integer> usedPes) {
		this.usedPes = usedPes;
	}

	/**
	 * Gets the free pes.
	 * 
	 * @return the free pes
	 */
	protected List<Integer> getFreePes() {
		return freePes;
	}

	/**
	 * Sets the free pes.
	 * 
	 * @param freePes the new free pes
	 */
	protected void setFreePes(List<Integer> freePes) {
		this.freePes = freePes;
	}

	protected Map<String, Long> getUsedMips() {
		return usedMips;
	}
	protected void setUsedMips(Map<String, Long> usedMips) {
		this.usedMips = usedMips;
	}
	protected Map<String, Long> getUsedBw() {
		return usedBw;
	}
	protected void setUsedBw(Map<String, Long> usedBw) {
		this.usedBw = usedBw;
	}
	protected List<Long> getFreeMips() {
		return this.freeMips;
	}
	protected void setFreeMips(List<Long> freeMips) {
		this.freeMips = freeMips;
	}
	
	protected List<Long> getFreeBw() {
		return this.freeBw;
	}
	protected void setFreeBw(List<Long> freeBw) {
		this.freeBw = freeBw;
	}
	
	private Host findHost(int hostId) {
		for(Host h:this.getHostList()) {
			if(h.getId() == hostId)
				return h;
		}
		return null;
	}
	
	private SDNVm findVm(Host h, String vmName) {
		for(Vm vm:h.getVmList()) {
			if(((SDNVm)vm).getName() == vmName)
				return (SDNVm) vm;
		}
		return null;
	}
	/*
	 * (non-Javadoc)
	 * @see cloudsim.VmAllocationPolicy#optimizeAllocation(double, cloudsim.VmList, double)
	 */
	@Override
	public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
		// TODO Auto-generated method stub
		//initial the migration map for this round (scheduling interval) of multiple migrations
		List<Map<String, Object>> migrationMap = new ArrayList<>();
		try{
			BufferedReader reader = new BufferedReader(new FileReader(Configuration.migPlanFilePath));
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
				Host srcHost = this.findHost(srcHostId);
				Host dstHost = this.findHost(dstHostId);
				SDNVm vm = this.findVm(srcHost, vmName);
				Map<String, Object> mig = new HashMap<>();
				mig.put("vm", vm);
				mig.put("host", dstHost);
				migrationMap.add(mig);
			}
		}catch(Exception e) {
			System.out.println(e);
		}
		return migrationMap;
	}

	/*
	 * (non-Javadoc)
	 * @see org.cloudbus.cloudsim.VmAllocationPolicy#allocateHostForVm(org.cloudbus.cloudsim.Vm,
	 * org.cloudbus.cloudsim.Host)
	 */
	@Override
	public boolean allocateHostForVm(Vm vm, Host host) {
		if (host.vmCreate(vm)) { // if vm has been succesfully created in the host
			getVmTable().put(vm.getUid(), host);

			int pe = vm.getNumberOfPes();
			double requiredMips = vm.getCurrentRequestedTotalMips();
			long requiredBw = vm.getCurrentRequestedBw();
			
			int idx = getHostList().indexOf(host);
			
			getUsedPes().put(vm.getUid(), pe);
			getFreePes().set(idx, getFreePes().get(idx) - pe);
			
			getUsedMips().put(vm.getUid(), (long) requiredMips);
			getFreeMips().set(idx,  (long) (getFreeMips().get(idx) - requiredMips));

			getUsedBw().put(vm.getUid(), (long) requiredBw);
			getFreeBw().set(idx, (long) (getFreeBw().get(idx) - requiredBw));

			Log.formatLine(
					"%.2f: VM #" + vm.getId() + " has been allocated to the host #" + host.getId(),
					CloudSim.clock());
			return true;
		}

		return false;
	}

	public Map<String, String> getVmPreDefinedLocTable() {
		return vmPreDefinedLocTable;
	}

	public void setVmPreDefinedLocTable(Map<String, String> vmPreDefinedLocTable) {
		this.vmPreDefinedLocTable = vmPreDefinedLocTable;
	}	
}
