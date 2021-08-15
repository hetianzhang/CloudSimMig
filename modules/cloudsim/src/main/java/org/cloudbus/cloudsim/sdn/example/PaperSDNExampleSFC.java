/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.example;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.Arrays;
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
import org.cloudbus.cloudsim.sdn.example.SDNExampleSFC.VmAllocationPolicyEnum;
import org.cloudbus.cloudsim.sdn.example.SDNExampleSFC.VmAllocationPolicyFactory;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyCombinedLeastFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyCombinedMostFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyGroupConnectedFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyManual;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyMipsLeastFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyMipsMostFullFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.OverBookingVmAllocationPolicyDistributeConnected;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyGroupConnectedFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyGroupConnectedFirstEx;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyLeastCorrelated;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyMostFull;
import org.cloudbus.cloudsim.sdn.vmallocation.priority.VmAllocationPolicyPriorityFirst;
import org.cloudbus.cloudsim.sdn.monitor.power.PowerUtilizationMaxHostInterface;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicy;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicyBandwidthAllocation;
import org.cloudbus.cloudsim.sdn.policies.LinkSelectionPolicyFlowCapacity;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemOverbookableGroup;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemOverbookableGroupPriority;
import org.cloudbus.cloudsim.sdn.policies.NetworkOperatingSystemSimple;

/**
 * CloudSimSDN example main program. It loads physical topology file, application
 * deployment configuration file and workload files, and run simulation.
 * Simulation result will be shown on the console 
 * 
 * SLA-Aware multiple migration planning and scheduling (SLAMIG)
 *
 * @author TianZhang He
 * @since CloudSimSDN 1.0
 */
public class PaperSDNExampleSFC {
	protected static String physicalTopologyFile 	= "dataset-energy/energy-physical.json";
	protected static String deploymentFile 		= "dataset-energy/energy-virtual.json";
	protected static String [] workload_files 			= { 
		"dataset-energy/energy-workload.csv",
		//"sdn-example-workload-normal-user.csv",	
		//"sdn-example-workload-prio-user-prio-ch.csv",
		//"sdn-example-workload-prio-user-normal-ch.csv",
		};
	protected static String vmhostMappingFile = "paper-mapping.json";
	
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
			Configuration.experimentName = String.format("SFC_On_%s_%d_%s_%s_", sfOn, (int)Configuration.migrationTimeInterval, 
					policy, migAlgName
				);
		}
		else {
			Configuration.experimentName = String.format("SFC_Off_%s_%d_%s_%s_", sfOn, (int)Configuration.migrationTimeInterval,
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
		
		CloudSimEx.setStartTime();
		
		// Parse system arguments
		if(args.length < 1) {
			printUsage();
			System.exit(1);
		}
		
		//default routing for fattree
		Configuration.DEFAULT_ROUTING_FATTREE = true;
		Configuration.MIG_ENABLE_FLAG = true;
		//QoS setting: default Configuration.PACKET_TIME_OUT = Double.POSITIVE_INFINITY
		//Configuration.PACKET_TIME_OUT = 3.0;
		
		//1. Policy: MFF, LFF, ...
		String policy = args[n++];
		
		String migAlg = args[n++];

		if("NoMig".equalsIgnoreCase(migAlg)) {
			Configuration.MIG_ENABLE_FLAG = false;
		}
		
		String sfcOn = args[n++];
		if("1".equals(sfcOn)) {
			Configuration.SFC_AUTOSCALE_ENABLE = true;
		} 
		else {
			Configuration.SFC_AUTOSCALE_ENABLE = false;
		}
		
		//Configuration.OVERBOOKING_RATIO_INIT = Double.parseDouble(args[n++]);

		setExpName(policy, sfcOn, migAlg);
		VmAllocationPolicyEnum vmAllocPolicy = VmAllocationPolicyEnum.valueOf(policy);
		
		Configuration.deadlinefilePath = "deadline/deadline-8-24-2.csv";
		
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
		//4-1. Group workloads: <start_index_1> <end_index_1> <file_suffix_1> ...
		//4-2. Normal workloads: <working_directory> <filename1> <filename2> ...
		if(args.length > n) {
			workloads = new ArrayList<String>();
			if(isInteger(args[n])) {
				// args: <startIndex1> <endIndex1> <filename_suffix1> ... 
				int i = n;
				while(i < args.length) {
					Integer startNum = Integer.parseInt(args[i++]);
					Integer endNum = Integer.parseInt(args[i++]);
					String filenameSuffix = args[i++];
					List<String> names = createGroupWorkloads(startNum, endNum, filenameSuffix);
					workloads.addAll(names);					
				}
			}
			else
			{
				int i=n;
				if(args.length > n+1) {
					// 4th arg is workload directory.
					Configuration.workingDirectory = args[n++];
					i=n;
				}
				// args: <filename1> <filename2> ...
				for(; i<args.length; i++) {
					workloads.add(args[i]);
				}
			}
		}
		else {
			workloads = (List<String>) Arrays.asList(workload_files);
		}
		System.out.println(Configuration.workingDirectory+Configuration.experimentName+"log.out.txt");
		FileOutputStream output = new FileOutputStream(Configuration.workingDirectory+Configuration.experimentName+"log.out.txt");
		Log.setOutput(output);
		
		printArguments(physicalTopologyFile, deploymentFile, Configuration.workingDirectory, workloads);
		Log.printLine("Starting CloudSim SDN...");

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
			LinkSelectionPolicy ls = new LinkSelectionPolicyBandwidthAllocation();
			
			switch(vmAllocPolicy) {
			case Manual:
				nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list, HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy) {
						return new VmAllocationPolicyManual(list, vmhostMappingFile);
					}
				};	
				break;
			case Random:
			case RandomFlow:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyCombinedLeastFullFirst(list, hostSelectionPolicy, vmMigrationPolicy); 
					}
				};
				nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				//TODO
				vmMigrationPolicy = new VmMigrationPolicyMostFull();
				break;			
			case MFF:
			case MFFFlow:
			case MFFCPU:
			case MFFBW:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) {
						return new VmAllocationPolicyCombinedMostFullFirst(list, hostSelectionPolicy, vmMigrationPolicy); 
					}
				};
				nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				//TODO
				//vmMigrationPolicy = new VmMigrationPolicyGroupConnectedFirst();
				vmMigrationPolicy = new VmMigrationPolicyLeastCorrelated();
				break;
			case LFF:
			case LFFFlow:
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						//return new VmAllocationPolicyCombinedLeastFullFirst(list, hostSelectionPolicy, vmMigrationPolicy);
						return new VmAllocationPolicyMipsLeastFullFirst(list, hostSelectionPolicy, vmMigrationPolicy);
					}
				};
				nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				//vmMigrationPolicy = new VmMigrationPolicyMostFull();
				//TODO: optimizeAllocation() does not return migration Map properly
				vmMigrationPolicy = new VmMigrationPolicyGroupConnectedFirstEx();
				break;
			case MFFGroup:
			case MFFGroupFlow: //OverSeparate_ConnectedFirst
				// Initial placement: overbooking, MFF
				// Initial placement connectivity: Connected VMs in one host
				// Migration: none
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyGroupConnectedFirst(list, hostSelectionPolicy, vmMigrationPolicy);
						//return new OverBookingVmAllocationPolicyDistributeConnected(list, hostSelectionPolicy, vmMigrationPolicy);
					}
				};
				//nos = new NetworkOperatingSystemSimple(physicalTopologyFile);
				nos = new NetworkOperatingSystemOverbookableGroup(physicalTopologyFile);
				hostSelectionPolicy = new HostSelectionPolicyMostFull();
				//vmMigrationPolicy = null;
				//TODO
				//vmMigrationPolicy = new VmMigrationPolicyGroupConnectedFirst();
				vmMigrationPolicy = new VmMigrationPolicyLeastCorrelated();
				break;				
			case HPF:	// High Priority First
			case HPFFlow:
				// Initial placement: overbooking, MFF
				// Initial placement connectivity: Connected VMs in one host
				// Migration: none
				vmAllocationFac = new VmAllocationPolicyFactory() {
					public VmAllocationPolicy create(List<? extends Host> list,
							HostSelectionPolicy hostSelectionPolicy,
							VmMigrationPolicy vmMigrationPolicy
							) { 
						return new VmAllocationPolicyPriorityFirst(list, hostSelectionPolicy, vmMigrationPolicy); 
					}
				};
				nos = new NetworkOperatingSystemOverbookableGroupPriority(physicalTopologyFile);
				hostSelectionPolicy = new HostSelectionPolicyMostFull();
				vmMigrationPolicy = null;
				break;				
			default:
				System.err.println("Choose proper VM placement polilcy!");
				printUsage();
				System.exit(1);
			}
			
			switch(vmAllocPolicy) {
			case MFFFlow:
			case LFFFlow:
			case MFFGroupFlow:
			case HPFFlow:
			case RandomFlow:
				ls = new LinkSelectionPolicyFlowCapacity();
				break;
			default:
				break;
			}
			
			switch(vmAllocPolicy) {
			case MFFCPU:
				Configuration.SFC_AUTOSCALE_ENABLE_VM = true;
				Configuration.SFC_AUTOSCALE_ENABLE_VM_VERTICAL = true;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_VM = true;
				Configuration.SFC_AUTOSCALE_ENABLE_BW = false;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW = false;
				break;
			case MFFBW:
				Configuration.SFC_AUTOSCALE_ENABLE_VM = false;
				Configuration.SFC_AUTOSCALE_ENABLE_VM_VERTICAL = false;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_VM = false;
				Configuration.SFC_AUTOSCALE_ENABLE_BW = true;
				Configuration.SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW = true;
				break;
			default:
				break;
			}

			
			nos.setLinkSelectionPolicy(ls);
//			snos.setMonitorEnable(false);

			// Create a Datacenter
			SDNDatacenter datacenter = createSDNDatacenter("Datacenter_0", physicalTopologyFile, nos, vmAllocationFac,
					hostSelectionPolicy, vmMigrationPolicy);
			
			// Set migration planning algorithm
			datacenter.setAlgSelect(migAlg);
			
			// Broker
			SDNBroker broker = createBroker();
			int brokerId = broker.getId();

			// Submit virtual topology
			broker.submitDeployApplication(datacenter, deploymentFile);
			
			// Submit individual workloads
			submitWorkloads(broker);
			
			// Sixth step: Starts the simulation
			if(!PaperSDNExampleSFC.logEnabled) 
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
			FileOutputStream outputMig = new FileOutputStream(Configuration.workingDirectory+Configuration.experimentName+"MIG_log.out.txt");
			Log.setOutput(outputMig);
			
			List<Memcopying> migList = datacenter.getMemcopyingList();
			LogPrinter.printMigrationSummary(migList);
			LogPrinter.printAlgRunTime(datacenter.getAlgSelect(),datacenter.getAlgTime());
			
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
