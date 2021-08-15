package org.cloudbus.cloudsim.sdn.example;
/**
 * Simulation Experiments for 
 * efficient large-scale live container migration in Edge and Cloud Computing
 * 
 * @author tianzhangh
 *
 */

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.CloudSimEx;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.Switch;
import org.cloudbus.cloudsim.sdn.example.DepMigPaperSDNExample.VmAllocationPolicyEnum;
import org.cloudbus.cloudsim.sdn.example.DepMigPaperSDNExample.VmAllocationPolicyFactory;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicyFlowCapacity;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemOverbookableGroup;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemOverbookableGroupPriority;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemSimple;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyGroupConnectedFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyManual;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyMipsLeastFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyEdge;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyGroupConnectedFirstEx;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyLeastCorrelated;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.priority.VmAllocationPolicyPriorityFirst;

//TODO
/**
 * 1. convert csv file to physcial, virutal, and mapping JSON file
 * 2. modify the live container migration reading file at the beginning with delay event at once
 * 3. double check the live container migration when occurred
 * 4. duplicate the implementation of live VM migration from VM module to container? (maybe not necessary)
 * 5. implement the GWIN heuristic grouping algorithm
 * 6. buffer for migration requests for planning and requests for scheduling after
 * 7. least time first scheduler
 * 8. 
 * @author tianzhangh
 *
 */
public class EdgeMigPaperExample {
	//configuration file
	protected static String physicalTopologyFile 	= "edge-paper/edge.physcial-basestation-5000pe-50ms-5ms-test.json";
	protected static String deploymentFile 		= "edge-paper-random/edge.virtualrandom-4000.json";
	protected static String [] workload_files 			= {};
	protected static String vmhostMappingFile = "edge-paper/edge.virtual-mapping-4000.json";
	protected static String migrationFilePath = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
	protected static String migrationRequestFile = migrationFilePath + "mig_00.csv";
	protected static String workloadFileDir = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\edge-paper-random\\";
	protected static String migAlg = "edgeNoSch"; //edgeEDF, edgeHeur, edgeNoMig, edgeNoSch, edgeNoUserMov
	protected static String taxiSize = "4000";
	protected static int workloadGroupSize = 40;
	//reading the arguments
	
	//initial container to edge server mapping according to the data
	//vmAllocPolicy Manual
	
	//link selection policy for packet sending
	
	//link selection policy for migration flow routing
	
	//WAN environment for default routing between different edge servers (otherwise the default FatTree one is not suitable)
	
	protected static List<String> workloads;
	
	private  static boolean logEnabled = false;

	public interface VmAllocationPolicyFactory {
		public VmAllocationPolicy create(List<? extends Host> list,
				HostSelectionPolicy hostSelectionPolicy,
				VmMigrationPolicy vmMigrationPolicy
				);
	}
	enum VmAllocationPolicyEnum{
		Manual,
		LFF, 
		MFF, 
		HPF,
		MFFGroup,
		LFFFlow, 
		MFFFlow, 
		HPFFlow,
		MFFGroupFlow,
		Random,
		RandomFlow,
		MFFBW, MFFCPU,
		END
		}	
	
	private static void printUsage() {
		String runCmd = "java SDNExample";
		System.out.format("Usage: %s <LFF|MFF> <0|1> <physical.json> <virtual.json> <working_dir> [workload1.csv] [workload2.csv] [...]\n", runCmd);
	}
	
	public static String policyName = "";
	public static String migAlgName = "";

	public static void setExpName(String policy, String sfOn, String migAlgName) {
		if(Configuration.SFC_AUTOSCALE_ENABLE) {
			Configuration.experimentName = String.format("EDGE_%d_%s_%s_%s_", (int)Configuration.MIG_EDGE_PLAN_INTERVAL, taxiSize,
					policy, migAlgName
				);
		}
		else {
			Configuration.experimentName = String.format("EDGE_%d_%s_%s_%s_", (int)Configuration.MIG_EDGE_PLAN_INTERVAL, taxiSize,
					policy, migAlgName
				);
		}
	}

	/**
	 * Creates main() to run this example.
	 *
	 * @param args the args
	 * @throws FileNotFoundException 
	 */
	@SuppressWarnings("unused")
	public static void main(String[] args) throws FileNotFoundException {
		int n = 0;
			
		//SDNBroker.experimentStartTime = 73800;
		//SDNBroker.experimentFinishTime = 77400;
		
		//initial container and end users entities mapping 
		String migPlanFolder = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
		//container migration within a hour
		Configuration.migPlanFilePath = migPlanFolder + "mig_00.csv";
		Configuration.workingDirectory = workloadFileDir;//"edge-test\\";		
		
		
		//set routing for WAN (EDGE) environments
		Configuration.DEFAULT_ROUTING_FATTREE = false;
		Configuration.DEAFULT_ROUTING_WAN = true;
		Configuration.DEAFULT_ROUTING_EDGE = true;
		Configuration.DEAFULT_ROUTING_EDGE_ALL = true;
		Configuration.MIG_ENABLE_FLAG = true;
		Configuration.migrationTimeInterval = 1;
		Configuration.MIG_ONLY_FIRST_ROUND = true;
		
		//Configuration.TIME_OUT = 60.0;
		
		CloudSimEx.setStartTime();
		
		// Parse system arguments
		if(args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		//1. Policy: Manual
		String policy = args[n++];
		
		//algorithm for live container migration planning and scheduling
		//IterMIS, EdgeHeur, Optimal, CQNCR, SLAMIG, EDF, FPTAS
		migAlg = args[n++];
		
		String sfcOn = args[n++];
		if("1".equals(sfcOn)) {
			Configuration.SFC_AUTOSCALE_ENABLE = true;
		} 
		else {
			//false for live container migration experiments in edge environments
			Configuration.SFC_AUTOSCALE_ENABLE = false;
		}
		
		//Configuration.OVERBOOKING_RATIO_INIT = Double.parseDouble(args[n++]);

		setExpName(policy, sfcOn, migAlg);
		VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(policy);
		String migResultFile = Configuration.workingDirectory + Configuration.experimentName+"_00_MIG_log.out.txt";
		String migResultFileCSV = Configuration.workingDirectory + Configuration.experimentName+"_00_MIG_log.out.csv";
		
		//Configuration.deadlinefilePath = "deadline/deadline-8-24-2.csv";
		
		//2. Physical Topology filename
		if(args.length > n)
			physicalTopologyFile = args[n++];

		//3. Virtual Topology filename
		if(args.length > n)
			deploymentFile = args[n++];
		// for manual VM Host mapping
		//if(vmAllocPolicy == VmAllocationPolicyEnum.Manual)
		if(vmAllocPolicy == VmAllocationPolicyEnum.Manual && args.length > n)
			vmhostMappingFile = args[n++];
		//4. Workload files 
		//list of workload files of each containers
		
		//workload configuration for large scale dep mig experiments
		workloads = new ArrayList<String>();
		String workloadPreFix = "edge-workload";
		int totalContainerGroup = workloadGroupSize; //10 for first 1000 container
		for(int i=0; i< totalContainerGroup; i++) {
			String fileName = workloadPreFix +"-"+i+".csv";
			workloads.add(fileName);
		}
		
		System.out.println(Configuration.workingDirectory+Configuration.experimentName+"log.out.txt");
		FileOutputStream output = new FileOutputStream(Configuration.workingDirectory+Configuration.experimentName+"log.out.txt");
		Log.setOutput(output);
		
		printArguments(physicalTopologyFile, deploymentFile, Configuration.workingDirectory, workloads);
		Log.printLine("Starting CloudSim SDN live container migration...");

		try {
			// Initialize
			int num_user = 1; // number of cloud users
			Calendar calendar = Calendar.getInstance();
			boolean trace_flag = false; // mean trace events
			CloudSim.init(num_user, calendar, trace_flag);
			
			VmAllocationPolicyFactory vmAllocationFac = null;
			NetworkOperatingSystem nos = null;
			HostSelectionPolicy hostSelectionPolicy = null;
			VmMigrationPolicy vmMigrationPolicy = null;
			//LinkSelectionPolicy ls = new LinkSelectionPolicyBandwidthAllocation();
			LinkSelectionPolicy ls = new LinkSelectionPolicyFlowCapacity();
			
			switch(vmAllocPolicy) {
			case Manual:
				//allocate container and end users based on the data trace
				nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list, HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy) {
						return new VmAllocationPolicyManual(list, vmhostMappingFile);
					}
				};
				vmMigrationPolicy = new VmMigrationPolicyEdge();
				break;
			
			default:
				System.err.println("Choose proper VM placement polilcy!");
				printUsage();
				System.exit(1);
			}
			
			//set link selection policy in NOS
			nos.setLinkSelectionPolicy(ls);

			// Create a Datacenter
			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac,
					hostSelectionPolicy, vmMigrationPolicy);
			
			// Set migration planning and scheduling algorithm
			datacenter.setAlgSelect(migAlg);
			
			// Broker
			SDNBroker broker = createBroker();
			int brokerId = broker.getId();

			// Submit virtual topology
			broker.submitDeployApplication(datacenter, deploymentFile);
			
			// Submit individual workloads
			submitWorkloads(broker);
			
			// Starts the simulation
			if(!EdgeMigPaperExample.logEnabled) 
				Log.disable();
			
			double finishTime = CloudSim.startSimulation();
			CloudSim.stopSimulation();
			Log.enable();

			Log.printLine(finishTime+": ========== EXPERIMENT FINISHED ===========");
			
			// Print results when simulation is over
			List<Workload> wls = broker.getWorkloads();
			if(wls != null)
				LogPrinter.printWorkloadList(wls);
			
			
			
			// Print hosts' and switches' total utilization.
			List<Host> hostList = datacenter.getHostList();
			List<Switch> switchList = nos.getSwitchList();
			LogPrinter.printEnergyConsumption(hostList, switchList, finishTime);
			
			LogPrinter.printConfiguration();
			LogPrinter.printTotalEnergy();

			Log.printLine("Simultanously used hosts:"+maxHostHandler.getMaxNumHostsUsed());
			
			broker.printResult();
			
			Log.printLine("CloudSim SDN finished!");
			
			//Print migration results
			FileOutputStream outputMig = new FileOutputStream(migResultFile);
			Log.setOutput(outputMig);
			
			List<Memcopying> migList = datacenter.getMemcopyingList();
			System.out.println("Total migration number: "+migList.size());
			LogPrinter.printMigrationSummary(migList);
			LogPrinter.printAlgRunTime(datacenter.getAlgSelect(),datacenter.getAlgTime());
			
			//Print migration results csv
			FileOutputStream outputMigCSV = new FileOutputStream(migResultFileCSV);
			Log.setOutput(outputMigCSV);
			LogPrinter.printMigrationSummaryCSV(migList);
			
			System.out.println("Elapsed time for simulation: " + CloudSimEx.getElapsedTimeString());
			System.out.println(Configuration.experimentName+" simulation finished.");

		} catch (Exception e) {
			e.printStackTrace();
			Log.printLine("Unwanted errors happen");
		}
	}
	
	public static void submitWorkloads(SDNBroker broker) {
		// Submit workload files individually
		if(workloads != null) {
			for(String workload:workloads)
				broker.submitRequests(workload);
		}
	}
	
	public static void printArguments(String physical, String virtual, String dir, List<String> workloads) {
		Log.printLine("Data center infrastructure (Physical Topology) : "+ physical);
		Log.printLine("Virtual Machine and Network requests (Virtual Topology) : "+ virtual);
		Log.printLine("Workloads in "+dir+" :");
		for(String work:workloads)
			Log.printLine("  "+work);		
	}
	
	/**
	 * Creates the datacenter.
	 *
	 * @param name the name
	 *
	 * @return the datacenter
	 */
	protected static NetworkOperatingSystem nos;
	protected static PowerUtilizationMaxHostInterface maxHostHandler = null;
	protected static SDNDatacenter createSDNDatacenter(String name, 
			String physicalTopology, 
			NetworkOperatingSystem snos, 
			VmAllocationPolicyFactory vmAllocationFactory,
			HostSelectionPolicy hostSelectionPolicy,
			VmMigrationPolicy vmMigrationPolicy) {
		// In order to get Host information, pre-create NOS.
		nos=snos;
		List<Host> hostList = nos.getHostList();
		
		String arch = "x86"; // system architecture
		String os = "Linux"; // operating system
		String vmm = "Xen";
		
		double time_zone = 10.0; // time zone this resource located
		double cost = 3.0; // the cost of using processing in this resource
		double costPerMem = 0.05; // the cost of using memory in this resource
		double costPerStorage = 0.001; // the cost of using storage in this
										// resource
		double costPerBw = 0.0; // the cost of using bw in this resource
		LinkedList<Storage> storageList = new LinkedList<Storage>(); // we are not adding SAN
													// devices by now

		DatacenterCharacteristics characteristics = new DatacenterCharacteristics(
				arch, os, vmm, hostList, time_zone, cost, costPerMem,
				costPerStorage, costPerBw);

		// Create Datacenter with previously set parameters
		SDNDatacenter datacenter = null;
		try {
			VmAllocationPolicy vmPolicy = vmAllocationFactory.create(hostList, hostSelectionPolicy, vmMigrationPolicy);
			maxHostHandler = (PowerUtilizationMaxHostInterface)vmPolicy;
			datacenter = new SDNDatacenter(name, characteristics, vmPolicy, storageList, 0, nos);
			
			
			nos.setDatacenter(datacenter);
		} catch (Exception e) {
			e.printStackTrace();
		}
		
		return datacenter;
	}

	// We strongly encourage users to develop their own broker policies, to
	// submit vms and cloudlets according
	// to the specific rules of the simulated scenario
	/**
	 * Creates the broker.
	 *
	 * @return the datacenter broker
	 */
	protected static SDNBroker createBroker() {
		SDNBroker broker = null;
		try {
			broker = new SDNBroker("Broker");
		} catch (Exception e) {
			e.printStackTrace();
			return null;
		}
		return broker;
	}
	

	private static List<String> createGroupWorkloads(int start, int end, String filename_suffix_group) {
		List<String> filenameList = new ArrayList<String>();
		
		for(int set=start; set<=end; set++) {
			String filename = Configuration.workingDirectory+set+"_" + filename_suffix_group;
			filenameList.add(filename);
		}
		return filenameList;
	}

	
	/// Under development
	/*
	static class WorkloadGroup {
		static int autoIdGenerator = 0;
		final int groupId;
		
		String groupFilenamePrefix;
		int groupFilenameStart;
		int groupFileNum;
		
		WorkloadGroup(int id, String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			this.groupId = id;
			this.groupFilenamePrefix = groupFilenamePrefix;
			this.groupFileNum = groupFileNum;
		}
		
		List<String> getFileList() {
			List<String> filenames = new LinkedList<String>();
			
			for(int fileId=groupFilenameStart; fileId< this.groupFilenameStart+this.groupFileNum; fileId++) {
				String filename = groupFilenamePrefix + fileId;
				filenames.add(filename);
			}
			return filenames;
		}
		
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, 0);
		}
		public static WorkloadGroup createWorkloadGroup(String groupFilenamePrefix, int groupFileNum, int groupFilenameStart) {
			return new WorkloadGroup(autoIdGenerator++, groupFilenamePrefix, groupFileNum, groupFilenameStart);
		}
	}
	
	static LinkedList<WorkloadGroup> workloadGroups = new LinkedList<WorkloadGroup>();
	 */
	
	public static boolean isInteger(String string) {
	    try {
	        Integer.valueOf(string);
	        return true;
	    } catch (NumberFormatException e) {
	        return false;
	    }
	}
}
