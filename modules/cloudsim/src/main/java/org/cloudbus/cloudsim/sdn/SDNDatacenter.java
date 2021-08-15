/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import static org.junit.Assert.assertNotNull;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Datacenter;
import org.cloudbus.cloudsim.DatacenterCharacteristics;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.ResCloudlet;
import org.cloudbus.cloudsim.Storage;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.sdn.policies.CloudletSchedulerMonitor;
import org.cloudbus.cloudsim.sdn.policies.CloudletSchedulerSpaceSharedMonitor;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationGenerator;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMIgrationPlanDepGraph;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanCQNCR;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanEDF;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanEdge;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanEdgeDepGraphHeur;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanEdgeDepGraphMISsingleIter;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanEdgeFPTA;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanFPTA;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanNone;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanOnebyOne;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanSLAGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanning;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanGWIN;
import org.cloudbus.cloudsim.sdn.vmallocation.priority.VmAllocationPolicyPriorityFirst;

/**
 * Extended class of SDNDatacenter that supports processing SDN-specific events.
 * In addition to the default Data Center of processing Request submission to VM,
 * and application deployment request, it simulates the live VM migration event
 * based on the pre-copy method. It could process VM resume and pause due to the
 * down-time of migration and scheduling the migration tasks based on the multiple
 * migration planning algorithm.
 * 
 * @author TianZhang He
 * @since CloudSimSDN 2.0
 */
public class SDNDatacenter extends Datacenter {
	private static final double PROCESSING_DELAY= 0.1;
	private NetworkOperatingSystem nos;
	private HashMap<Integer,Request> requestsTable;
	private static boolean isMigrateEnabled = true;
	
	//[NEW]
	VMMigrationPlanning planning;
	private List<Channel> migPausedChannelList = null;
	private List<Memcopying> inMigrationList = null;
	private List<Memcopying> plannedMigrationList = null;
	private double algTime = 0;
	private List<Memcopying> mcList; // migration statistic includes all finished migration
	private Map<Integer, LinkedList<Memcopying>> migPlan; // migration planning buffer based on each channel/group
	private double migStartTime; // start time for migration planning
	private double migFinishTime;
	private String algSelect = "";
	
	public SDNDatacenter(String name, DatacenterCharacteristics characteristics, VmAllocationPolicy vmAllocationPolicy, List<Storage> storageList, double schedulingInterval, NetworkOperatingSystem nos) throws Exception {
		super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);
		
		this.setNos(nos);
		this.mcList = new ArrayList<>();
		this.requestsTable = new HashMap<Integer, Request>();
		this.inMigrationList = new ArrayList<>();	
		this.plannedMigrationList = new ArrayList<>();
		//nos.init();
		if(vmAllocationPolicy instanceof VmAllocationPolicyPriorityFirst) {
			((VmAllocationPolicyPriorityFirst)vmAllocationPolicy).setTopology(nos.getPhysicalTopology());
		}
	}
	
	public void addVm(Vm vm){
		getVmList().add(vm);
		if (vm.isBeingInstantiated()) vm.setBeingInstantiated(false);
		vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler().getAllocatedMipsForVm(vm));
	}
		
	@Override
	protected void processVmCreate(SimEvent ev, boolean ack) {
		super.processVmCreate(ev, ack);
		if(ack) {
			send(getNos().getId(), 0/*CloudSim.getMinTimeBetweenEvents()*/, CloudSimTags.VM_CREATE_ACK, ev.getData());
		}
	}
	
	protected void processVmCreateDynamic(SimEvent ev) {
		super.processVmCreate(ev, true);
		send(getNos().getId(), 0/*CloudSim.getMinTimeBetweenEvents()*/, Constants.SDN_VM_CREATE_DYNAMIC, ev.getData());
	}

	protected void processVmCreateInGroup(SimEvent ev, boolean ack) {
		@SuppressWarnings("unchecked")
		List<Object> params =(List<Object>)ev.getData();
		
		Vm vm = (Vm)params.get(0);
		VmGroup vmGroup=(VmGroup)params.get(1);

		boolean result = ((VmAllocationInGroup)getVmAllocationPolicy()).allocateHostForVmInGroup(vm, vmGroup);

		if (ack) {
			int[] data = new int[3];
			data[0] = getId();
			data[1] = vm.getId();

			if (result) {
				data[2] = CloudSimTags.TRUE;
			} else {
				data[2] = CloudSimTags.FALSE;
			}
			send(vm.getUserId(), 0, CloudSimTags.VM_CREATE_ACK, data);
			send(getNos().getId(), 0, CloudSimTags.VM_CREATE_ACK, vm);
		}

		if (result) {
			getVmList().add(vm);

			if (vm.isBeingInstantiated()) {
				vm.setBeingInstantiated(false);
			}

			vm.updateVmProcessing(CloudSim.clock(), getVmAllocationPolicy().getHost(vm).getVmScheduler()
					.getAllocatedMipsForVm(vm));
		}
	}	
	
	private List<SDNVm> migHostVmList = new LinkedList<SDNVm>(); // migration fake VM list
	private List<Arc> migArcList = new LinkedList<Arc>();

	private Hashtable<Integer, Integer> migHostIdToVmIdTable = new Hashtable<Integer, Integer>();
	Hashtable<String, Integer> migVmNameToIdTable = new Hashtable<String, Integer>();
	
	Hashtable<String, Integer> migFlowNameToIdTable = new Hashtable<String, Integer>();
	Hashtable<Integer, Arc> migFlowIdToArcTable = new Hashtable<Integer, Arc>();
	
	/**
	 * create dummy vms and arcs between srcHost to dstHost for vm migrations
	 * @param migrationMapList
	 */
	protected void processMigrationNet(List<Map<String, Object>> migrationMapList) {		
		for(Map<String, Object> mig:migrationMapList) {
			//SDNVm toMigVm = (SDNVm) mig.get("vm");	
			SDNHost dstHost = (SDNHost) mig.get("host");
			SDNHost srcHost = (SDNHost) mig.get("src");
			if(dstHost != null)
			if(getMigHostIdToVmIdTable().get(dstHost.getId()) == null) {
				//create dummy migVm inside the dstHost
				this.addDummyMigVmToHost(dstHost);
			}
			if(srcHost != null)
			if(getMigHostIdToVmIdTable().get(srcHost.getId()) == null) {
				//create dummy migVm inside the srcHost
				this.addDummyMigVmToHost(srcHost);
			}
			int flowId = this.findMigFlow(srcHost.getId(), dstHost.getId());
			if(flowId == -1)
				this.createArcBetweenDummyVms(srcHost, dstHost);
		}
	}
	
	static int flowIdCurrent = 10000;
	private void createArcBetweenDummyVms(SDNHost src, SDNHost dst) {
		String arcName = null;
		//int flowIdCurrent = this.getFlowNameIdTable().size();
		//int flowIdCurrent = this.nos.allArcs.size();
		 //id for migration channel start at 10000
		
		
		int srcVmId = this.migHostIdToVmIdTable.get(src.getId());
		int dstVmId = this.migHostIdToVmIdTable.get(dst.getId());
		Vm srcVm = this.findVm(srcVmId);
		Vm dstVm = this.findVm(dstVmId);
		
		if(srcVm==null || dstVm==null)
			System.out.println(CloudSim.clock()+": "+ "arc create error "+src.getId()+"->"+dst.getId());
		
		//default migration arc bandwidth configuration
		int srcId = srcVm.getId();
		int dstId = -1;	
		long arcBw = 200000000; //0.2Gbps
		arcBw = (long) (arcBw * Configuration.MIG_FLOW_REQUIREMENT); //0.2*5=1Gbps
		double lat = 0.0;
		
		if(dstVm.getId() != srcId) {
			dstId = dstVm.getId();
			flowIdCurrent = flowIdCurrent + 1;
			Arc arc = new Arc(srcId, dstId, flowIdCurrent, arcBw, lat);
			if(flowIdCurrent != -1) {
				//THE ARCNAME IS MADE OF HOST IDs
				arcName = "mig"+Integer.toString(srcVm.getHost().getId()) +"-"
						+ Integer.toString(dstVm.getHost().getId());
				arc.setName(arcName);
			}
			this.migArcList.add(arc);
			this.getFlowNameIdTable().put(arcName, flowIdCurrent);
			this.migFlowNameToIdTable.put(arcName, flowIdCurrent);
			if(arcName.equalsIgnoreCase("mig18-7"))
				System.out.println("debug");
			this.getNos().deployFlowNameToIdTable.put(arcName, flowIdCurrent);
			if(flowIdCurrent != -1) {
				this.getNos().deployFlowIdToArcTable.put(flowIdCurrent, arc);
				this.migFlowIdToArcTable.put(flowIdCurrent, arc);
			}
		//NOS holds AllArcs, deployFlowNameToIdTable, deployFlowIdToArcTable
		this.getNos().allArcs.add(arc);
		}
	}
	
	private void addDummyMigVmToHost(SDNHost host) {
		String vmName = "mig"+Integer.toString(host.getId());
		if(migVmNameToIdTable.get(vmName)==null) {
		double starttime = 0;
		double endtime = Double.POSITIVE_INFINITY;
		long mips = 0;
		int pes = 0;
		int ram = 0;
		long bw = 0;
		long size = 0;
		CloudletScheduler clSch = new CloudletSchedulerSpaceSharedMonitor(Configuration.TIME_OUT);
		SDNVm vm = null;
		int vmId = SDNVm.getUniqueVmId();
		vm = new SDNVm(vmId, this.getId(),mips,pes,ram,bw,size,"VMM", clSch, starttime, endtime);
		if(vm != null) {
			vmName = "mig"+Integer.toString(host.getId());
			vm.setName(vmName);
		}
		getVmList().add(vm);
		this.migHostVmList.add(vm); 
		migVmNameToIdTable.put(vmName, vmId);
		vm.setHost(host);
		this.getVmAllocationPolicy().allocateHostForVm(vm, host);
		getMigHostIdToVmIdTable().put(host.getId(), vmId);
		this.getNos().allVms.put(vmId, vm);
		}
	}
	
	//create fake VMs and its Arcs for migration purpose in every Host
	protected void processMigrationNet() {
		/*String vmName = null;
		double starttime = 0;
		double endtime = Double.POSITIVE_INFINITY;
		long mips = 0;
		int pes = 0;
		int ram = 0;
		long bw = 0;
		long size = 0;
		CloudletScheduler clSch = new CloudletSchedulerSpaceSharedMonitor(Configuration.TIME_OUT);
		
		List<SDNHost> hostList = this.getNos().getHostList();
		//this.nos.getPhysicalTopology().getAllNodes();
		for(Host host:hostList) {
			// get the migration vm list here
			SDNVm vm = null;
			int vmId = SDNVm.getUniqueVmId();
			vm = new SDNVm(vmId, this.getId(),mips,pes,ram,bw,size,"VMM", clSch, starttime, endtime);
			if(vm != null) {
				vmName = "mig"+Integer.toString(host.getId());
				vm.setName(vmName);
			}
			
			getVmList().add(vm);
			this.migHostVmList.add(vm); 
			migVmNameToIdTable.put(vmName, vmId);
			vm.setHost(host);
			//host.getVmList().add(vm);
			this.getVmAllocationPolicy().allocateHostForVm(vm, host);
			getMigHostIdToVmIdTable().put(host.getId(), vmId);
			this.getNos().allVms.put(vmId, vm);
			
		}
		
		String arcName = null;
		//int flowIdCurrent = this.getFlowNameIdTable().size();
		//int flowIdCurrent = this.nos.allArcs.size();
		int flowIdCurrent = 10000; //id for migration channel start at 10000
		
		for (Vm srcVm:migHostVmList) {
			int srcId = srcVm.getId();
			int dstId = -1;	
			long arcBw = 200000000; //0.2Gbps
			arcBw = (long) (arcBw * Configuration.MIG_FLOW_REQUIREMENT); //0.2*5=1Gbps
			double lat = 0.0;
			for (Vm dstVm:migHostVmList)
				if(dstVm.getId() != srcId) {
					dstId = dstVm.getId();
					flowIdCurrent = flowIdCurrent + 1;
					Arc arc = new Arc(srcId, dstId, flowIdCurrent, arcBw, lat);
					if(flowIdCurrent != -1) {
						//THE ARCNAME IS MADE OF HOST IDs
						arcName = "mig"+Integer.toString(srcVm.getHost().getId()) +"-"
								+ Integer.toString(dstVm.getHost().getId());
						arc.setName(arcName);
					}
					this.migArcList.add(arc);
					this.getFlowNameIdTable().put(arcName, flowIdCurrent);
					this.migFlowNameToIdTable.put(arcName, flowIdCurrent);
					this.getNos().deployFlowNameToIdTable.put(arcName, flowIdCurrent);
					if(flowIdCurrent != -1) {
						this.getNos().deployFlowIdToArcTable.put(flowIdCurrent, arc);
						this.migFlowIdToArcTable.put(flowIdCurrent, arc);
						//[NEW] build forwarding table for migration flow, build subflow route for migration
						//if(srcId == 45 && dstId == 60 && flowIdCurrent == 10399)
						//	System.out.println("test migration main flow route");
						//TODO comment the buildForwardingTable for all migration arc between src and dst hosts
						//boolean result = this.getNos().buildForwardingTable(srcId, dstId, flowIdCurrent);
						//this.getNos().testAllPath(srcId, dstId, flowIdCurrent);
						//if(result == false)
						//	System.out.println("building result"+srcId+"->"+dstId+"|"+flowIdCurrent+": "+result);
					}
				}		
		}
		
		//NOS holds AllArcs, deployFlowNameToIdTable, deployFlowIdToArcTable
		this.getNos().allArcs.addAll(migArcList);
		*/
	}
	
	public static int migrationCompleted = 0;

	@Override
	//TODO [NEW] SimEvent ev need to transfer
	protected void processVmMigrate(SimEvent ev, boolean ack) {
		
		migrationCompleted++;
		
		
		@SuppressWarnings("unchecked")
		Map<String, Object> migrate = (HashMap<String, Object>) ev.getData();

		Vm vm = (Vm) migrate.get("vm"); 
		Host newHost = (Host) migrate.get("host");
		Host oldHost = vm.getHost();
		System.out.println(CloudSim.clock());
		System.out.println("migration or user movement finish "+((SDNVm)vm).getName());
		System.out.println("src: "+oldHost+" dst: "+newHost);
		
		// Migrate the VM to another host.
		super.processVmMigrate(ev, ack);
		
		// Change network routing.
		getNos().processVmMigrate(vm, (SDNHost)oldHost, (SDNHost)newHost);
	}
	
	@Override
	public void processOtherEvent(SimEvent ev){
		switch(ev.getTag()){
			case Constants.REQUEST_SUBMIT: 
				processRequestSubmit((Request) ev.getData());
				break;
			case Constants.APPLICATION_SUBMIT: 
				processApplication(ev.getSource(),(String) ev.getData()); 
				if(isMigrateEnabled) {
					this.processMigrationNet();
				}
				break;
			case Constants.SDN_PACKET_SUBFLOW_COMPLETE: //[NEW]
				this.processSubFlowCompleted((Packet)ev.getData());
				break;
			case Constants.SDN_PACKET_COMPLETE: 
				processPacketCompleted((Packet)ev.getData()); 
				break;
			case Constants.SDN_PACKET_FAILED: 
				processPacketFailed((Packet)ev.getData()); 
				break;
			case Constants.SDN_PACKET_DROPPED:
				processPacketDropped((Packet)ev.getData());
				break;
			case Constants.SDN_VM_CREATE_IN_GROUP:
				processVmCreateInGroup(ev, false); 
				break;
			case Constants.SDN_VM_CREATE_IN_GROUP_ACK: 
				processVmCreateInGroup(ev, true); 
				break;
			case Constants.SDN_VM_CREATE_DYNAMIC:
				processVmCreateDynamic(ev);
				break;
			case Constants.SDN_VM_MIGRATION_START:
				this.processMigrationStart((Memcopying)ev.getData());
				break;
			case Constants.SDN_VM_MIGRATION_PRE:
				this.processMigrationPre((Memcopying)ev.getData());
				break;
			case Constants.SDN_VM_PAUSE: //[NEW]
				this.processVmPause((Memcopying) ev.getData());
				break;
			case Constants.SDN_VM_MIGRATION_POST:
				this.processMigrationPost((Memcopying) ev.getData());
				break;
			case Constants.SDN_VM_MIGRATION_SCHEDULE:
				this.processVmSchedule((Memcopying) ev.getData());
				break;
			case Constants.SDN_VM_MIGRATION_FAIL:
				this.processMigrationFail((Memcopying)ev.getData());
				break;
			case Constants.SDN_VM_MIGRATION_EDGE_WAIT:
				this.processEdgeMigrationWait((Memcopying)ev.getData());;
				break;
			case Constants.SDN_VM_MIGRATION_EDGE_PLAN:
				this.processEdgeMigrationPlan();
				break;
			case Constants.SDN_VM_MIGRATION_EDGE_FINISH:
				this.processEdgeMigrationFinish((Memcopying)ev.getData());
				break;
			//[NEW] implemented in processPacketCompleted
			case Constants.SDN_VM_RESUME:
				processVmResume((Memcopying) ev.getData());
				break;
			default: 
				System.out.println("Unknown event recevied by SdnDatacenter. Tag:"+ev.getTag());
		}
	}

	public void processUpdateProcessing() {
		updateCloudletProcessing(); // Force Processing - TRUE!
		checkCloudletCompletion();
	}
	
	protected void processCloudletSubmit(SimEvent ev, boolean ack) {

		// gets the Cloudlet object
		Cloudlet cl = (Cloudlet) ev.getData();
		
		// Clear out the processed data for the previous time slot before Cloudlet submitted
		updateCloudletProcessing();

		try {
			// checks whether this Cloudlet has finished or not
			if (cl.isFinished()) {
				String name = CloudSim.getEntityName(cl.getUserId());
				Log.printLine(getName() + ": Warning - Cloudlet #" + cl.getCloudletId() + " owned by " + name
						+ " is already completed/finished.");
				Log.printLine("Therefore, it is not being executed again");
				Log.printLine();

				// NOTE: If a Cloudlet has finished, then it won't be processed.
				// So, if ack is required, this method sends back a result.
				// If ack is not required, this method don't send back a result.
				// Hence, this might cause CloudSim to be hanged since waiting
				// for this Cloudlet back.
				if (ack) {
					int[] data = new int[3];
					data[0] = getId();
					data[1] = cl.getCloudletId();
					data[2] = CloudSimTags.FALSE;

					// unique tag = operation tag
					int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
					sendNow(cl.getUserId(), tag, data);
				}

				sendNow(cl.getUserId(), CloudSimTags.CLOUDLET_RETURN, cl);

				return;
			}

			// process this Cloudlet to this CloudResource
			cl.setResourceParameter(getId(), getCharacteristics().getCostPerSecond(), getCharacteristics()
					.getCostPerBw());

			int userId = cl.getUserId();
			int vmId = cl.getVmId();
			// time to transfer the files
			double fileTransferTime = predictFileTransferTime(cl.getRequiredFiles());

			SDNHost host = (SDNHost)getVmAllocationPolicy().getHost(vmId, userId);
			Vm vm = host.getVm(vmId, userId);
			CloudletScheduler scheduler = vm.getCloudletScheduler();
			
			double estimatedFinishTime = scheduler.cloudletSubmit(cl, fileTransferTime); // This estimated time is useless

			//host.adjustMipsShare();
			//estimatedFinishTime = scheduler.getNextFinishTime(CloudSim.clock(), scheduler.getCurrentMipsShare());

			// Check the new estimated time by using host's update VM processing funciton.
			// This function is called only to check the next finish time
			estimatedFinishTime = host.updateVmsProcessing(CloudSim.clock());
			
			double estimatedFinishDelay = estimatedFinishTime - CloudSim.clock();
			//estimatedFinishTime -= CloudSim.clock();

			// if this cloudlet is in the exec queue
			if (estimatedFinishDelay > 0.0 && estimatedFinishTime < Double.MAX_VALUE) {
				estimatedFinishTime += fileTransferTime;
				//Log.printLine(getName() + ".processCloudletSubmit(): " + "Cloudlet is going to be processed at: "+(estimatedFinishTime + CloudSim.clock()));
				
				// gurantees a minimal interval before scheduling the event
				if (estimatedFinishDelay < CloudSim.getMinTimeBetweenEvents()) {
					estimatedFinishDelay = CloudSim.getMinTimeBetweenEvents();
				}				
				
				send(getId(), estimatedFinishDelay, CloudSimTags.VM_DATACENTER_EVENT);
			}

			if (ack) {
				int[] data = new int[3];
				data[0] = getId();
				data[1] = cl.getCloudletId();
				data[2] = CloudSimTags.TRUE;

				// unique tag = operation tag
				int tag = CloudSimTags.CLOUDLET_SUBMIT_ACK;
				sendNow(cl.getUserId(), tag, data);
			}
		} catch (ClassCastException c) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "ClassCastException error.");
			c.printStackTrace();
		} catch (Exception e) {
			Log.printLine(getName() + ".processCloudletSubmit(): " + "Exception error.");
			e.printStackTrace();
		}

		checkCloudletCompletion();
	}

	@Override
	protected void checkCloudletCompletion() {
		if(!getNos().isApplicationDeployed())
		{
			super.checkCloudletCompletion();
			return;
		}

		List<? extends Host> list = getVmAllocationPolicy().getHostList();
		for (int i = 0; i < list.size(); i++) {
			Host host = list.get(i);
			for (Vm vm : host.getVmList()) {
				
				// Check all completed Cloudlets
				while (vm.getCloudletScheduler().isFinishedCloudlets()) {
					Cloudlet cl = vm.getCloudletScheduler().getNextFinishedCloudlet();
					if (cl != null) {
						// For completed cloudlet -> process next activity.
						Request req = requestsTable.remove(cl.getCloudletId());
						req.getPrevActivity().setFinishTime(CloudSim.clock());
					
						if (req.isFinished()){
							// All requests are finished, no more activities to do. Return to user
							send(req.getUserId(), PROCESSING_DELAY, Constants.REQUEST_COMPLETED, req);
						} else {
							//consume the next activity from request. It should be a transmission.
							processNextActivity(req);
						}
					}
				}
				
				// Check all failed Cloudlets (time out)
				List<Cloudlet> failedCloudlet = ((CloudletSchedulerMonitor) (vm.getCloudletScheduler())).getFailedCloudlet();
				for(Cloudlet cl:failedCloudlet) {
					processCloudletFailed(cl);
				}
			}
		}
	}
	
	private void processRequestSubmit(Request req) {
		Activity ac = req.getNextActivity();
		
		if(ac instanceof Processing) {
			processNextActivity(req);
		}
		else {
			System.err.println("Request should start with Processing!!");
		}
	}
	
	private void processCloudletFailed(Cloudlet cl) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processCloudletFailed(): Cloudlet failed: "+cl);
		
		Request req = requestsTable.remove(cl.getCloudletId());
		if(req.getPrevActivity() != null)
			req.getPrevActivity().setFailedTime(CloudSim.clock()); // Set as finished.
		if(req.getNextActivity() != null)
			req.getNextActivity().setFailedTime(CloudSim.clock()); // Set as finished.
		
		Request lastReq = req.getTerminalRequest(); 
		send(req.getUserId(), PROCESSING_DELAY, Constants.REQUEST_FAILED, lastReq);
	}
	
	
	//[NEW] processing the subflow completion
	private void processSubFlowCompleted(Packet pkt) {
		//check if all subflow is finished
		if(pkt.getFlowId() == 100330) {
			System.out.println("[DEBUG]: v51 sub channel finish.");
		}
		int originflowId = pkt.getPktEncapsulated().getFlowId();
		int subflowbase = (pkt.getFlowId()
				-originflowId*Configuration.MPTCP_SUBFLOW_MAX*1000)/Configuration.MPTCP_SUBFLOW_MAX;
		
		pkt.setPacketFinishTime(CloudSim.clock());
		Map<Integer, Integer> flowCheckMap = this.getNos().getMultiFlowCheckMap().get(originflowId);
		Integer lastFlow = flowCheckMap.get(subflowbase);
		//TODO: bug for checking all sub channel flow are finished when several migrations use same origin channel
		lastFlow = lastFlow - 1;
		flowCheckMap.put(subflowbase, lastFlow);
		if(lastFlow==0) {
			// pktEncapsulated is the original packet
			this.send(this.getId(), 0, Constants.SDN_PACKET_COMPLETE, pkt.getPktEncapsulated());
		}
			
		//In nos after processCompletePackets(completeChannels), updateChannel kills all finished channels
		/*boolean finish = true;
		for(int i=0; i<pkt.getTotalFlowNum(); i++) {
			//this.getNos().findChannel(pkt.origin, pkt.destination, pkt.getFlowId()*10+i);
			Channel subCh = this.getNos().channelTable.get(NetworkOperatingSystem.getKey(pkt.origin, pkt.destination, originflowId * Configuration.MPTCP_SUBFLOW_MAX + i));
			if(subCh != null) {
				finish = false;
				break;
			}
		}
		if(finish) {
			// pktEncapsulated is the original packet
			this.send(this.getId(), 0, Constants.SDN_PACKET_COMPLETE, pkt.getPktEncapsulated());
		}*/
	}
	
	//TODO [NEW] re-send the packet when it dropped
	//ARQ (Automatic Repeat reQuest): 1. Stop-and-wait 2. Go-Back-N 3. Selective Repeat.
	//Timeouts are measured in RTT, Upper bound of retries
	private void processPacketDropped(Packet pkt) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processPacketDropped(): Packet failed: "+pkt);
		
		pkt = getNos().addPacketToChannel(pkt);
		int droppedTimes = pkt.getDroppedTimes()+1;
		pkt.setDroppedTimes(droppedTimes);
		//pkt.setPacketFinishTime(-1);
		//do not reset the start time of the packet
	}
	
	private void processPacketFailed(Packet pkt) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ".processPacketFailed(): Packet failed: "+pkt);		

		pkt.setPacketFailedTime(CloudSim.clock());
		Request req = pkt.getPayload();
		
		Request lastReq = req.getTerminalRequest(); 
		send(req.getUserId(), PROCESSING_DELAY, Constants.REQUEST_FAILED, lastReq);
	}

	private void processPacketCompleted(Packet pkt) {
		//int vmId = pkt.getDestination();
		pkt.setPacketFinishTime(CloudSim.clock());
		Request req = pkt.getPayload();
		
		//if(!req.isFinished()) {
		if(req.getNextActivity() instanceof Memcopying) {
			Activity ac = req.getNextActivity();
			((Memcopying) ac).setAmountMemCopied(((Memcopying) ac).getAmountMemCopied() + pkt.getSize()); //Transferred data
		}
		processNextActivity(req);
		//}
	}
	
	private void processNextActivity(Request req) {
//		Log.printLine(CloudSim.clock() + ": " + getName() + ": Process next activity: " +req);		
		Activity ac = req.removeNextActivity();
		
		if(ac instanceof Memcopying) {
			// new activity specify for pre-copy migration
			processNextActivityMemcopying((Memcopying) ac);
		}else {
			ac.setStartTime(CloudSim.clock());

			if(ac instanceof Transmission) {
				processNextActivityTransmission((Transmission)ac);
			}
			else if(ac instanceof Processing) {
				processNextActivityProcessing(((Processing) ac), req);
			} else {
				Log.printLine(CloudSim.clock() + ": " + getName() + ": Activity is unknown..");
			}
		}
		
	}

	
	// [NEW]
	private void processNextActivityMemcopying(Memcopying mc) {
		mc.increaseIterationNum();
		
		long remainedPg = 0;
		double bw = 0;
		bw = this.getEstimatedBw(mc.getMigVm(), mc);
		System.out.println("estimated Bandwidth:"+bw);
		
		remainedPg = getRemainedDirtyPage(mc.getMigVm(), mc);
		double pgBudget = (CloudSim.clock() - mc.getServeTime()) * bw;
		System.out.println(CloudSim.clock()+": Vm"+mc.getMigVm()+" migration remained pages: "+remainedPg);
		
		
		//update serve time to current time
		mc.setServeTime(CloudSim.clock());
		
		if(mc.isPrecopy() && mc.isStopandcopy()) {
			send(getId(), 0, Constants.SDN_VM_RESUME, mc);
			//this.processVmResume(mc);			
		} else if (mc.isPrecopy() || mc.isStopandcopy()) {
			// check the remained dirty page
			// srcVM and dstVM are the fake VMs, use migVM to get the migrating VM
			
			
			//TODO the down time threshold
			if(bw !=0) {
				double currentThres = remainedPg/bw;
				
			if(pgBudget <remainedPg || currentThres < mc.getDowntimeThres() || mc.getIterationNum() > mc.getIterationThres()) {
				// send the MIGRATION EVENT to stop and copy phase
				mc.setStopandcopy(true);
				send(getId(), 0, Constants.SDN_VM_PAUSE, mc);
				Request req = new Request(this.getId());
				req.addActivity(mc);
				Packet pkt = new Packet(mc.getSrcVm(), mc.getDstVm(), remainedPg, this.findMigFlow(mc.getSrcHost(), mc.getDstHost()), req);
				processUpdateMemcopying(pkt);
			}		
			else {
				// if not continue the pre-copy iteration
				Request req = new Request(this.getId());
				req.addActivity(mc);
				Packet pkt = new Packet(mc.getSrcVm(), mc.getDstVm(), remainedPg, this.findMigFlow(mc.getSrcHost(), mc.getDstHost()), req);
				processUpdateMemcopying(pkt);
			}}
		}
			
	}
	
	//TODO [NEW]
	private long getRemainedDirtyPage(int srcId, Memcopying mc) {
		SDNVm vm = (SDNVm) this.findVm(srcId);
		double currentTime = CloudSim.clock();
		// previous remained dirty pages
		long remainedPg = vm.getRemainedDirtyPg();
		double interval = currentTime - mc.getServeTime();
		remainedPg = (long) (Math.pow(10,9) * vm.getDirtyRate() * vm.getCompressRatio() * interval);
		vm.setRemainedDirtyPg(remainedPg);
		// set migration update time to current clock
		vm.setMigrationUpdateTime(currentTime);
		return remainedPg;
	}
	
	//NEW
	private long getEstimatedBw(int srcId, Memcopying mc) {
		SDNVm vm = (SDNVm) this.findVm(srcId);
		double currentTime = CloudSim.clock();
		// previous remained dirty pages
		double remainedPg = vm.getRemainedDirtyPg();
		double serveTime = mc.getServeTime();
		
		if(currentTime - serveTime !=0) {
			return (long) (remainedPg/(currentTime - serveTime));
		}	
		else
			return 0;
	}
	
	//[NEW]
	private void processUpdateMemcopying(Packet pkt) {
		pkt = getNos().addPacketToChannel(pkt);
		pkt.setPacketStartTime(CloudSim.clock());
	}
	
	
	
	//for SDN_VM_PAUSE used in migration
	//TODO: pause the processing of cloudlets and transmission from and to srcVm 
	//TODO: pause the packet transmission by update the channel's bw to 0 and change the channel to dedicated one
	private void processVmPause(Memcopying mc) {
		
		SDNVm vm = (SDNVm) this.findVm(mc.getMigVm());
		vm.setInPause(true);
		// use copy to iterate, delete on the origin
		
		//1. pause cloudlet
		List<ResCloudlet> clList = new ArrayList<>(vm.getCloudletScheduler().getCloudletExecList());

		for (ResCloudlet cl:clList) {
			vm.getCloudletScheduler().cloudletPause(cl.getCloudletId());
		}
		clList = new ArrayList<>(vm.getCloudletScheduler().getCloudletWaitingList());
		for(ResCloudlet cl:clList) {
			vm.getCloudletScheduler().cloudletPause(cl.getCloudletId());
		}
			
		long Bw = vm.getCurrentAllocatedBw();
		mc.setVmBw(Bw);
		
		//2. [DEPRECATED] pause all the transmission of migration vm
		//getNos().updateVmTransmission(mc.getMigVm(), 0, -1);
		
		//3. set allocated mips to zero
		
		List<Double> currentMips = vm.getCloudletScheduler().getCurrentMipsShare();
		List<Double> allocatedMips = new ArrayList<>(currentMips);
		
		mc.setVmMips(allocatedMips);
		mc.setVmTotalMips(vm.getTotalMips());
		Bw = 0;
		
		/*Double[] data = new Double[vm.getNumberOfPes()];
		Arrays.fill(data, new Double(0));
		List<Double> currentMips = Arrays.asList(data);*/
		
		List<Double> zeroMips = new ArrayList<>(allocatedMips);
		for(int i=0;i<zeroMips.size();i++) {
			if(zeroMips.isEmpty())
				continue;
			zeroMips.set(i, (double) 0);
		}
		
		vm.setCurrentAllocatedMips(zeroMips);
		Collections.copy(currentMips, zeroMips);
		vm.setCurrentAllocatedBw(Bw);
		
		//set the start point of downtime
		mc.setDowntime(CloudSim.clock());
		
		updateCloudletProcessing();
		checkCloudletCompletion();
	}
	
	public List<Memcopying> getMemcopyingList(){
		return this.mcList;
	}
	
	public SDNHost findHost(int hostId) {
		if(this.getHostList() != null)
		for(int i=0; i<this.getHostList().size(); i++) {
			SDNHost temp = (SDNHost) this.getHostList().get(i);
			if(temp.getId() == hostId)
				return temp;
		}
		return null;
	}
	
	public SDNVm findVm(int vmId) {
		for(int i=0;i< this.getVmList().size();i++) {
			SDNVm temp = (SDNVm) this.getVmList().get(i);
			if(vmId == temp.getId()) {
				return temp;
			}
		}
		return null;
	}
	
	private boolean isMigIndependent(Memcopying a, Memcopying b) {
		//check the network link sharing with other ongoing migration
		if(this.algSelect.equalsIgnoreCase("CQNCR") || this.algSelect.equalsIgnoreCase("EDF"))
		if(this.planning.checkHost(b, a) || this.planning.checkPath(b, a)) { // return true indicates sharing
				return false;
		}
		if(this.algSelect.equalsIgnoreCase("SLAMIG"))
		if(((VMMigrationPlanSLAGroup) this.planning).checkIndepScheduling(b, a)==false) {
				return false;
		}
		
		return true;
	}
	
	//TODO NEW check migration feasibility in migration scheduler
	private boolean isMigFeasible(Memcopying mig) {
		SDNHost dstHost = (SDNHost) mig.migrate.get("host");
		//double ramHost = dstHost.getRam();
		double ram = dstHost.getRamProvisioner().getAvailableRam();
		double bw = dstHost.getBwProvisioner().getAvailableBw();
		double disk = dstHost.getStorage();
		double totalMips = dstHost.getVmScheduler().getAvailableMips();
		if(ram < mig.getVmRAM() || bw < mig.getRequestedBw() || disk < mig.getVmDisk() || totalMips < mig.getVmTotalMips()) {
			return false;
		}
		//check the network link sharing with other ongoing migration
		if(this.algSelect.equalsIgnoreCase("SchCQNCR") || this.algSelect.equalsIgnoreCase("CQNCR")
				 || this.algSelect.equalsIgnoreCase("EDF"))
		for(Memcopying ongoing: this.inMigrationList) {
			//if(this.planning.checkHost(ongoing, mig) || this.planning.checkPath(ongoing, mig)) { // return true indicates sharing
			if(this.planning.checkHost(ongoing, mig)) { // return true indicates sharing
				return false;
			}
		}
		
		if(this.algSelect.equalsIgnoreCase("SchCQNCR") || this.algSelect.equalsIgnoreCase("SLAMIG") || this.algSelect.equalsIgnoreCase("GWIN"))
		for(Memcopying ongoing: this.inMigrationList) {
			if(this.planning.checkHost(ongoing, mig)) { // return true indicates sharing
				return false;
			}
			if(this.planning.checkIndepScheduling(ongoing, mig)==false) {
				return false;
			}
		}
		
		if(this.algSelect.equalsIgnoreCase("SLAMIG") && Configuration.MIG_POTENTIAL_IMPACT) {
			Map<Integer, List<Integer>> waitMap = ((VMMigrationPlanSLAGroup) this.planning).getPotentialWaitMigMap();
			for(Memcopying ongoing: this.inMigrationList) {
				List<Integer> waitList = waitMap.get(mig.getId());
				if(waitList!=null)
				for(Integer n: waitList) {
					if(n==ongoing.getId())
						return false;
				}
			}
		}
		if(this.algSelect.equalsIgnoreCase("edgeHeur") || this.algSelect.equalsIgnoreCase("edgeIterSingle")) {
			for(Memcopying ongoing: this.inMigrationList) {
				if(this.planning.checkHost(ongoing, mig)) { // return true indicates sharing
					return false;
				}
				if(this.planning.checkIndepScheduling(ongoing, mig)==false) {
					return false;
				}
			}
		}
		/*if(this.algSelect.equalsIgnoreCase("SLAMIG"))
		for(Memcopying ongoing: this.inMigrationList) {
			if(((VMMigrationPlanSLAGroup) this.planning).checkIndepScheduling(ongoing, mig)==false) {
				return false;
			}
		}*/
		
		
		return true;
	}
	
	
	//process pre migration overheads
	private void processMigrationPre(Memcopying mc) {
		
		SDNVm vm = (SDNVm) mc.migrate.get("vm");
		Host host = (Host) mc.migrate.get("host");
		Host src = (Host) mc.migrate.get("src");
		if(vm.getHost().getId()!=src.getId()) {
			System.out.println(CloudSim.clock()+"vm "+vm.getName()+" src "+src+" and vm getHost "+vm.getHost()+" not identical");
			System.out.println("[DEBUG]: "+mc.migrate.get("time") +" "+mc.migrate.get("vm")+ " "+ mc.migrate.get("src")+"->"+ mc.migrate.get("host"));
			List<Memcopying> copyMcList = new ArrayList<>(this.mcList);
			for(Memcopying mig:mcList) {
				if(mig.equals(mc)) {
					System.out.println(mig.hashCode());
					System.out.println("[DEBUG]: "+mig.migrate.get("time") +" "+mig.migrate.get("vm")+ " "+ mig.migrate.get("src")+"->"+ mig.migrate.get("host"));
				}
				//SDNVm vmf = (SDNVm) mig.migrate.get("vm");
				//System.out.println(CloudSim.clock()+"vm "+vmf.getName()+" src "+mig.migrate.get("src")+" -> "+mig.migrate.get("host")+" is in finished List");
			}
			System.exit(1);
		}
		
		//start the iterative memory copy
		mc.setStartTime(CloudSim.clock());
		mc.setServeTime(CloudSim.clock());
				
		//after a certain delay
		mc.setPrecopy(true);
		mc.setStopandcopy(false);
		
		//update the migration required bandwidth in arc
		if(Configuration.MIG_SHARE_BANDWIDTH==false) {
			//revise the multiple bandwidth
			//int srcVm = mc.getSrcVm();
			//int dstVm = mc.getDstVm();
			//int flowId = mc.getChId();
			//double freeBw = this.getNos().getFreeBandwidth(mc.getSrcVm(), mc.getDstVm(), mc.getChId());
			//List<Node> nRoute = this.getNos().getVmNodeRoute(mc.getSrcVm(), mc.getDstVm(), mc.getChId());
			List<Node> nRoute = this.getNos().getVmNodeRoute(mc.getSrcVm(), mc.getDstVm(), mc.getChId());
			if(nRoute==null) {
				if(this.findHost(mc.getSrcHost()).getId()==host.getId()) {
					System.err.println(CloudSim.clock()+" the src and dst are the same host");
					System.out.println("arrival time:"+mc.migrate.get("time"));
					System.out.println("src Host" + vm.getHost());
					System.out.println("dst host: "+ mc.migrate.get("host"));
					
					System.out.println("get src Host id: "+ vm.getHost().getId());
					System.out.println("get dst Host id: "+ host.getId());
					
					System.out.println("Contianer name: "+vm.getName());
					System.out.println("container id: "+mc.getMigVm());
					System.out.println("dumb src Vm:"+mc.getSrcVm());
					System.out.println("dumb dst Vm:"+mc.getDstVm());
					
					System.out.println("Host src id in mig: "+mc.getSrcHost());
					System.out.println("Host dst id in mig: "+mc.getDstHost());
					System.out.println("Ch id: "+mc.getChId());
					if(this.edgeMigRequestArrivalTable.get(vm)!=null) {
						for(Memcopying mig: this.edgeMigRequestArrivalTable.get(vm)) {
							System.out.println(mig.migrate.get("time"));
							System.out.println(mig.migrate.get("host"));
						}
					}
				}
				System.exit(1);
			}
			double freeReserved = this.getNos().getPathNonReservedBandwidth(nRoute);
			//[DEBUG] 
			System.out.println("free reserved for mig: " + freeReserved);
			//update arc reqBw
			long reqBw = (long)freeReserved;
			System.out.println("free reserved for mig (long): " + reqBw);
			this.getNos().updateArcBandwidth(mc.getSrcVm(), mc.getDstVm(), mc.getChId(), reqBw);
			
			//update channel bw after the channel is created and exist
			//this.getNos().updateChannelBandwidth(mc.getSrcVm(), mc.getDstVm(), mc.getChId(), Double.doubleToRawLongBits(freeBw));
		}else {
			double reqBw = mc.getRequestedBw();
			if(reqBw>0)
				this.getNos().updateArcBandwidth(mc.getSrcVm(), mc.getDstVm(), mc.getChId(), (long)reqBw);
		}
		//mc.setRequestedBw(freeBw);
		//if(mc.getRequestedBw()>0)
		//	this.getNos().updateBandwidthArc(mc.getSrcVm(), mc.getDstHost(), mc.getChId(), (long) mc.getRequestedBw());
		
		//send the first Memory copying
		Request req = new Request(this.getId());
		req.addActivity(mc);
		//Packet pkt = new Packet(mc.getSrcVm(), mc.getDstVm(), (long) (mc.getVmRAM()*Math.pow(10, 9)), mc.getChId(), req);
		vm.setRemainedDirtyPg((long)(mc.getVmRAM()*Math.pow(10, 9)*8*vm.getCompressRatio()));
		//System.out.println("vm ram size: "+mc.getVmRAM());
		Packet pkt = new Packet(mc.getSrcVm(), mc.getDstVm(), vm.getRemainedDirtyPg(), mc.getChId(), req);
		
		this.getNos().findAllPath(mc.getSrcVm(), mc.getDstVm(), mc.getChId());
		this.getNos().addPacketToChannel(pkt);
	}
	
	//process post migration overheads
	private void processMigrationPost(Memcopying mc) {
		//set the migration migration time by taking post migration overheads
		mc.setPostMigEnd(CloudSim.clock());
		mc.setMigTime(mc.getPostMigEnd() - mc.getPreMigStart());
		
		//2. add to migration list for statistic
		System.out.println("mig "+mc.migrate.get("vm")+" finished!");
		//if(((SDNVm)mc.migrate.get("vm")).getId()==11) {
		//	System.out.println("[DEBUG]: vm #11");
		//}
		mcList.add(mc);
		this.inMigrationList.remove(mc);
		this.plannedMigrationList.remove(mc);
		this.inMigrationNum--;
		SDNVm vm = (SDNVm) mc.migrate.get("vm");
		
		System.out.println("Migration " + ((SDNVm)mc.migrate.get("vm")).getName()+" finished!");
		
		//3. [DEPRECATED] resume VM transmission after stop-and-copy phasing
		//getNos().resumeVmTransmission(mc.getMigVm());
		
		updateCloudletProcessing();
		checkCloudletCompletion();
		
		send(
				getId(),
				0,
				CloudSimTags.VM_MIGRATE,
				mc.migrate);

		this.send(this.getId(), CloudSim.getMinTimeBetweenEvents(), Constants.SDN_VM_MIGRATION_SCHEDULE, mc);			
	}
	
	private void processMigrationFail(Memcopying mc) {
		//try to reschedule the migration request after a certain time (1 second)
		if(CloudSim.clock()-mc.getPreMigStart()<600.0) { //request is failed after 600 seconds after arrival
		SDNVm vm = (SDNVm) mc.migrate.get("vm");
		SDNHost dst = (SDNHost) mc.migrate.get("host");
		System.out.println("vm/container is migrating: "+vm.getName());
		System.out.println("reschedule migration "+vm.getName()+" "+vm.getHost()+"->"+dst);
		
		List<Memcopying> arrivalRequestList = this.edgeMigRequestArrivalTable.get(vm);
		if(arrivalRequestList == null) {
			arrivalRequestList = new ArrayList<Memcopying>();
		}
		arrivalRequestList.add(mc);
		this.edgeMigRequestArrivalTable.put(vm, arrivalRequestList);
		}
	}
	
	private Map<Integer, Boolean> schedulerTimeMap = new HashMap<>();
	private void processVmSchedule(Memcopying mc) {
		Vm vm = (Vm) mc.migrate.get("vm");
		vm.setInMigration(false);
		//System.out.println("Migration Scheduling Time: "+CloudSim.clock());
		//int clock = (int) Math.round(CloudSim.clock());
		//Boolean flag = this.schedulerTimeMap.get(clock);
		//if(flag==null || flag==false) {
			//schedulerTimeMap.put(clock, true);

		if(this.algSelect.equalsIgnoreCase("FPTAS") || this.algSelect.equalsIgnoreCase("BWFPTAS")) {
			this.planning.getMigWaitingList().remove(mc);
			this.processMigrationSchedulingRecaluate();
		}
		//}
		
		if( this.algSelect.equalsIgnoreCase("EDF") || this.algSelect.equalsIgnoreCase("SchCQNCR") || this.algSelect.equalsIgnoreCase("GWIN")
				|| this.algSelect.equalsIgnoreCase("SLAMIG")) {
			// update the migration group map and start the next migration group if possible
			//1. first check if there is any migration not start before and in the current group
			//2. check next group feasible migration at current time
			this.processMigrationScheduling();
			//this.processMigrationSchedulingAll();
		}
		if(this.algSelect.equalsIgnoreCase("CQNCR")) {
			//this.processMigrationSchedulingAll();
			this.processMigrationDelay();
		}
		if(this.algSelect.equalsIgnoreCase("OnebyOne")) {
			processMigrationSchedulingOnebyOne();
		}
		
		if(this.algSelect.equalsIgnoreCase("edgeNoSch") || this.algSelect.equalsIgnoreCase("edgeNoSch")) {
			this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_EDGE_FINISH, mc);
			//this.processEdgeMigrationFinish(mc);
		}
		
		if(this.algSelect.equalsIgnoreCase("edgeFPTAS")) {		
			this.processMigrationSchedulingRecaluate();
			this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_EDGE_FINISH, mc);
		}
		
		if(this.algSelect.equalsIgnoreCase("edgeHeur") || this.algSelect.equalsIgnoreCase("edgeIterSingle")) {
			this.processEdgeMigrationScheduling();
			//this.processEdgeMigrationSchedulingAllPossible();
			this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_EDGE_FINISH, mc);
		}
	}
	
	private void processVmResume(Memcopying mc) {
		SDNVm vm = (SDNVm) findVm(mc.getMigVm());
		vm.setInPause(false);

		//TODO 1. recover the mips and vm bw first
		vm.setCurrentAllocatedBw(mc.getVmBw());
		vm.setCurrentAllocatedMips(mc.getVmMips());
		List<Double> allocatedMips = vm.getCloudletScheduler().getCurrentMipsShare();
		Collections.copy(allocatedMips, mc.getVmMips());
		
		
		//List<Double> l2 = vm.getCloudletScheduler().getCurrentRequestedMips();
		
		List<ResCloudlet> clList = new ArrayList<>(vm.getCloudletScheduler().getCloudletPausedList());
		for (ResCloudlet cl:clList) {
			vm.getCloudletScheduler().cloudletResume(cl.getCloudletId());
		}
		
		mc.setServeTime(CloudSim.clock());
		mc.setFinishTime(CloudSim.clock());
		double downtime = CloudSim.clock() - mc.getDowntime();
		mc.setDowntime(downtime);
		
		this.send(this.getId(), mc.getPostMigTime(), Constants.SDN_VM_MIGRATION_POST, mc);
	}
	
	private void processMigrationDelay() {
		//do nothing as all migration will start based on the estimated model
	}
	
	//[NEW]
	private void processMigrationSchedulingOnebyOne() {
		List<Memcopying> migList = this.planning.getMigrationPlan();
		if(migList.size()!=0) {
			this.processMigrationStart(migList.get(0));
			migList.remove(0);
		}
	}
	
	//[NEW] recalculate the migration planning for FPTAS algorithm
	private void processMigrationSchedulingRecaluate() {
		this.planning.processMigrationPlan();
		List<Memcopying> toMigList = this.planning.getMigrationPlan();
		for(Memcopying mig:toMigList) {
			if(inMigrationList.contains(mig)) {
				//update migration bandwidth
				Arc arc = this.getNos().deployFlowIdToArcTable.get(mig.getChId());
				if(arc.getBw()< (long) mig.getRequestedBw())
					this.getNos().updateBandwidthArc(mig.getSrcVm(), mig.getDstVm(), mig.getChId(), (long) mig.getRequestedBw());
			}else {
				this.addToPlannedMigrationList(mig);
				this.processMigrationStart(mig);
				this.planning.getMigWaitingList().remove(mig);
			}
		}
	}
	
	
	
	private boolean isMigFeasible(Memcopying check, List<Memcopying> waitingList) {
		boolean flag = true;
		for(Memcopying mig:waitingList) {
			flag = this.isMigIndependent(mig, check);
			if(flag == false)
				return false;
		}
		return flag;
	}
	
	//start to schedule all feasible migration if it is not dependent on the migration from waiting list
	private void processMigrationSchedulingAll() {
		List<Memcopying> waitingList = new ArrayList<>();
		for(Entry<Integer,LinkedList<Memcopying>> en: this.migPlan.entrySet()) {
			//int groupNum = en.getKey();
			LinkedList<Memcopying> toMigGroup = en.getValue();
			LinkedList<Memcopying> toMigGroupTemp = new LinkedList<>(toMigGroup);
			if(toMigGroup.size() != 0) {
				List<Memcopying> groupwaitingList = new ArrayList<>();
				for(Memcopying mig: toMigGroupTemp) {
					//if(waitingList.size() != 0) {
						if(isMigFeasible(mig)) {
						if(isMigFeasible(mig,waitingList)) {
							if(waitingList.contains(mig))
								waitingList.remove(mig);
							this.processMigrationStart(mig);
							toMigGroup.remove(mig);
						}}else {
							groupwaitingList.add(mig);
						}
					//}
				}
				waitingList.addAll(groupwaitingList);
			}
		}
	}
	
	
	private void processEdgeMigrationSchedulingAllPossible() {
		for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
			int miglast = 0;
			int migstart = 0;
			int groupNum = entry.getKey();
			this.currentGroupNum = this.migPlan.entrySet().size()-1;
			if(groupNum<=this.currentGroupNum) {
				LinkedList<Memcopying> toMigGroup = entry.getValue();
				if(toMigGroup.size() != 0) {
					miglast += toMigGroup.size();
					LinkedList<Memcopying> toMigGroupTemp = new LinkedList<>(toMigGroup);
					for(Memcopying mig: toMigGroupTemp) {
						if(this.isMigFeasible(mig) || this.inMigrationList.contains(mig)) {
							if(!this.mcList.contains(mig))
								this.processMigrationStart(mig);
							toMigGroup.remove(mig);
							
							migstart++;
						}
					}
				}
				//System.out.println(CloudSim.clock()+": [Migration] Current Group:"+ groupNum+" left "+miglast+", starting "+ migstart);
			}		
		}
	}
	/*
	 * scheduling migration in edge computing, check feasiblity of migrationsin other planning groups
	 */
	private void processEdgeMigrationScheduling() {
		for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
			int miglast = 0;
			int migstart = 0;
			int groupNum = entry.getKey();
			if(groupNum<=this.currentGroupNum) {
				LinkedList<Memcopying> toMigGroup = entry.getValue();
				if(toMigGroup.size() != 0) {
					miglast += toMigGroup.size();
					LinkedList<Memcopying> toMigGroupTemp = new LinkedList<>(toMigGroup);
					for(Memcopying mig: toMigGroupTemp) {
						if(this.isMigFeasible(mig) || this.inMigrationList.contains(mig)) {
							if(!this.mcList.contains(mig))
								this.processMigrationStart(mig);
							toMigGroup.remove(mig);
							
							migstart++;
						}
					}
				}
				System.out.println(CloudSim.clock()+": [Migration] Current Group:"+ groupNum+" left "+miglast+", starting "+ migstart);
			}		
		}
		
		System.out.println("group Index "+this.groupIndex);
		if(groupIter.hasNext() && this.groupIndex < this.planning.getMigrationPlanList().size()-1) {
			int nextGroupNum = 0;
			nextGroupNum = this.planning.getMigrationPlanList().get(this.groupIndex+1);
			
			//Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			
			boolean flag = false;
			
			List<Memcopying> migGroup = this.migPlan.get(nextGroupNum);
			List<Memcopying> toMigGroupTemp = new LinkedList<>(migGroup);
			for(Memcopying mig:toMigGroupTemp) {
				if(this.isMigFeasible(mig)) {
					this.processMigrationStart(mig);
					migGroup.remove(mig);
					flag = true;
				}
			}
			
			if(flag) {
				this.currentGroupNum = nextGroupNum;
				this.groupIndex++;
				System.out.println(CloudSim.clock()+"[Migration] Start Mig Group:" + nextGroupNum);
			}
		}else if(this.inMigrationNum ==0 && this.inMigrationList.size()==0) {
			int miglast = 0;
			for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
				miglast += entry.getValue().size();
			}
			if(miglast ==0) {
			// all migrations are finished, get current time
			// check if there is any VM is under migration
			this.migFinishTime = CloudSim.clock();
			if(mcList.isEmpty() == false)
				System.out.println("ALL Migration:"+this.mcList.size()+"\nTotal Migration Time:"+(this.migFinishTime - this.migStartTime));
			}else {
				Log.printLine(CloudSim.clock()+":"+this.getName()+" [ERROR] "+ miglast+" Migration didn't start");
			}
		}
		else{
			int miglast = 0;
			for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
				miglast += entry.getValue().size();
			}
			System.out.println(CloudSim.clock()+": [Migration] not start: "+miglast);
			System.out.println(CloudSim.clock()+": [Migration] no other group:" + this.inMigrationList.size() +" are migrating");
		}
	}
	
	// scheduling migration in other group when it is feasible, check migration planning is finish
	private void processMigrationScheduling() {
		for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
			int miglast = 0;
			int migstart = 0;
			int groupNum = entry.getKey();
			if(groupNum<=this.currentGroupNum) {
				LinkedList<Memcopying> toMigGroup = entry.getValue();
				if(toMigGroup.size() != 0) {
					miglast += toMigGroup.size();
					LinkedList<Memcopying> toMigGroupTemp = new LinkedList<>(toMigGroup);
					for(Memcopying mig: toMigGroupTemp) {
						if(this.isMigFeasible(mig) || this.inMigrationList.contains(mig)) {
							this.processMigrationStart(mig);
							toMigGroup.remove(mig);
							
							migstart++;
						}
					}
				}
				System.out.println(CloudSim.clock()+": [Migration] Current Group:"+ groupNum+" left "+miglast+", starting "+ migstart);
			}		
		}
		
		if(groupIter.hasNext() && this.groupIndex < this.planning.getMigrationPlanList().size()-1) {
			int nextGroupNum = 0;
			nextGroupNum = this.planning.getMigrationPlanList().get(this.groupIndex+1);
			
			//Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			
			boolean flag = false;
			
			List<Memcopying> migGroup = this.migPlan.get(nextGroupNum);
			List<Memcopying> toMigGroupTemp = new LinkedList<>(migGroup);
			for(Memcopying mig:toMigGroupTemp) {
				if(this.isMigFeasible(mig)) {
					this.processMigrationStart(mig);
					migGroup.remove(mig);
					flag = true;
				}
			}
			
			if(flag) {
				this.currentGroupNum = nextGroupNum;
				this.groupIndex++;
				System.out.println(CloudSim.clock()+"[Migration] Start Mig Group:" + nextGroupNum);
			}
		}else if(this.inMigrationNum ==0 && this.inMigrationList.size()==0) {
			int miglast = 0;
			for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
				miglast += entry.getValue().size();
			}
			if(miglast ==0) {
			// all migrations are finished, get current time
			// check if there is any VM is under migration
			this.migFinishTime = CloudSim.clock();
			if(mcList.isEmpty() == false)
				System.out.println("ALL Migration:"+this.mcList.size()+"\nTotal Migration Time:"+(this.migFinishTime - this.migStartTime));
			}else {
				Log.printLine(CloudSim.clock()+":"+this.getName()+" [ERROR] "+ miglast+" Migration didn't start");
			}
		}
		else{
			int miglast = 0;
			for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
				miglast += entry.getValue().size();
			}
			System.out.println(CloudSim.clock()+": [Migration] not start: "+miglast);
			System.out.println(CloudSim.clock()+": [Migration] no other group:" + this.inMigrationList.size() +" are migrating");
		}
		
	}
	
	private void processNextActivityTransmission(Transmission tr) {
		Packet pkt = tr.getPacket();
		
		//send package to router via channel (NOS)
		pkt = getNos().addPacketToChannel(pkt);
		pkt.setPacketStartTime(CloudSim.clock());
		tr.setRequestedBW(getNos().getRequestedBandwidth(pkt));
	}
	
	private void processNextActivityProcessing(Processing proc, Request reqAfterCloudlet) {
		Cloudlet cl = proc.getCloudlet();
		proc.clearCloudlet();
		
		requestsTable.put(cl.getCloudletId(), reqAfterCloudlet);
		sendNow(getId(), CloudSimTags.CLOUDLET_SUBMIT, cl);
		
		// Set the requested MIPS for this cloudlet.
		int userId = cl.getUserId();
		int vmId = cl.getVmId();
		Host host = getVmAllocationPolicy().getHost(vmId, userId);
		if(host == null) {
			Vm orgVm = getNos().getSFForwarderOriginalVm(vmId);
			if(orgVm != null) {
				vmId = orgVm.getId();
				cl.setVmId(vmId);
				host = getVmAllocationPolicy().getHost(vmId, userId);
			}
			else {
				throw new NullPointerException("Error! cannot find a host for Workload:"+ proc+". VM="+vmId);
			}
		}
		Vm vm = host.getVm(vmId, userId);
		double mips = vm.getMips();
		proc.setVmMipsPerPE(mips);
	}

	private void processApplication(int userId, String filename) {
		getNos().deployApplication(userId,filename);
		send(userId, 0, Constants.APPLICATION_SUBMIT_ACK, filename);
	}
	
	public Map<String, Integer> getVmNameIdTable() {
		return this.getNos().getVmNameIdTable();
	}
	public Map<String, Integer> getFlowNameIdTable() {
		return this.getNos().getFlowNameIdTable();
	}
	
	public static boolean isAutoScaleEnabled = true;
	
	public void startAutoScale() {
		if(isAutoScaleEnabled) {
			getNos().startAutoScale();
		}
	}
	
	public void printDebug() {
		System.err.println(CloudSim.clock()+": # of currently processing Cloudlets: "+this.requestsTable.size());
	}

	// For results statistic
	public static int migrationAttempted = 0;
	
	// entrance function for migration planning
	public void startMigrate() {
		if(isMigrateEnabled) {
			Log.printLine(CloudSim.clock()+": Migration started..");
			System.out.println(CloudSim.clock() + "Migration started..");
			
			//initialize for edge scheduling
			this.edgeMigRequestArrivalTable = new HashMap<>();
			this.edgeMigWaitingList = new ArrayList<>();
			
			//remove the fake vm from the vm list
			List<SDNVm> toMigVms = this.getVmList();
			toMigVms.removeAll(this.migHostVmList);
			for(Host h:this.getHostList()) {
				System.out.println(h);
				for(Vm vm:h.getVmList()) {
					System.out.println(vm);
				}
			}
			
			//TODO Experiment Migration Mapping
			List<Map<String, Object>> migrationMap = new ArrayList<>();
			VmMigrationGenerator migGen = new VmMigrationGenerator();
			List<Map<String, Object>> usermobiMap = new ArrayList<>();;
			if(Configuration.DEAFULT_ROUTING_EDGE)
				usermobiMap = new ArrayList<>();
			
			
			if(Configuration.migPlanFilePath == null) {
				migrationMap = getVmAllocationPolicy().optimizeAllocation(toMigVms);
			}else {
				//migrationMap = migGen.generatePaperMigrationsExpSingle(this, toMigVms);
				// long-term dep mig experiments
				//migrationMap = migGen.generatePowerMigrationExp(this, toMigVms, Configuration.migPlanFilePath);
				// first-round comparison experiments for dep mig
				
				
				if(Configuration.DEAFULT_ROUTING_EDGE) {
					//edge experiment flag is on
					//migration item: "vm", "host", "time", "deadline" 
					migrationMap = migGen.generateEdgePaperMigrations(this, toMigVms,  Configuration.migPlanFilePath);
					//user mobility item: "user", "base"
					usermobiMap = migGen.getUserMobilityMap();
				}else {
					migrationMap = migGen.generateDepMigMigrationExp(this, toMigVms, Configuration.migPlanFilePath);
				}
				
				
			}
			
			this.getVmList().addAll(this.migHostVmList);
			//migrationMap = migGen.generatePaperMigrations(this);
			//migGen.getHostSchedulerInfo(this);
			//List<Host> sortList  = migGen.getMostFullList(this);
			//List<Host> sortList = migGen.getLeastFullList(this);
			//migrationMap = migGen.generateMostFullFirst(this);
			//migrationMap = migGen.generateMigrations(this);
			//migrationMap = migGen.generateLeastFullFirst(this);
			//migrationMap = migGen.generateConsolidate(this);
			
			//FOR TESTING THE MIGRATION AND PLANNING algorithm
			//=================================================
			//TODO test the migration
			
			for(Map<String, Object> mig:migrationMap) {
				System.out.println(mig.get("vm")+"->"+mig.get("host"));
			}
			
			//==================================================
			//create dummy vms and arcs for vm migration
			this.processMigrationNet(migrationMap);
			
			
			if (migrationMap != null && migrationMap.size() > 0 || migrationMap.size()==0 && this.algSelect.equalsIgnoreCase("DepGraph")) {
				migrationAttempted += migrationMap.size();
				
				updateCloudletProcessing();
				checkCloudletCompletion();
				
				//planning the multiple migration task based on srcHost, dstHost, R, M, Bw, etc.
				//this.migrationPlan(migrationMap);
				this.migStartTime = CloudSim.clock();
				//initialize the migration group index at each schedule intervals
				this.groupIndex = -1;
				this.currentGroupNum = 0;
				if(this.inMigrationList!=null)
					System.out.println("in migration: " + this.inMigrationList.size());
				
				switch(this.algSelect) {
				case "edgeNoUserMov":
					this.edgeMigrationNoUserMobility();
					break;
				case "edgeFPTAS":
					if(usermobiMap.size()!=0 && migrationMap.size()!=0)
						this.edgeMigrationFPTAS(usermobiMap, migrationMap);
						else {
							System.out.println("user mobility or migration input is empty");
							System.exit(0);
						}
					break;
				case "edgeEDF":
					break;
				case "edgeHeur":
					if(usermobiMap.size()!=0 && migrationMap.size()!=0)
						this.edgeMigrationHeur(usermobiMap, migrationMap);
						else {
							System.out.println("user mobility or migration input is empty");
							System.exit(0);
						}
					break;
				case "edgeIterSingle":
					if(usermobiMap.size()!=0 && migrationMap.size()!=0)
						this.edgeMigrationIterSingle(usermobiMap, migrationMap);
						else {
							System.out.println("user mobility or migration input is empty");
							System.exit(0);
						}
					break;
				case "edgeNoMig":
					if(usermobiMap.size()!=0)
						this.edgeMigrationNoMig(usermobiMap);
					else {
						System.out.println("user mobility or migration input is empty");
						System.exit(0);
					}
					break;
				case "edgeNoSch":
					if(usermobiMap.size()!=0 && migrationMap.size()!=0)
					this.edgeMigrationNoSch(usermobiMap, migrationMap);
					else {
						System.out.println("user mobility or migration input is empty");
						System.exit(0);
					}
					break;
				}
				
				
				
				if(this.algSelect.equalsIgnoreCase("EDF"))
					this.migrationPlanEDF(migrationMap); // EDF with dynamic migration scheduler
				if(this.algSelect.equalsIgnoreCase("CQNCR"))
					this.migrationPlanCQNCR(migrationMap);
				if(this.algSelect.equalsIgnoreCase("SchCQNCR"))
					this.migrationPlanSchCQNCR(migrationMap);
				if(this.algSelect.equalsIgnoreCase("none"))
					this.migrationPlanNone(migrationMap);
				if(this.algSelect.equalsIgnoreCase("FPTAS"))
					this.migrationPlanFPTA(migrationMap);
				if(this.algSelect.equalsIgnoreCase("SLAMIG"))
					this.migrationPlanSLAGroup(migrationMap);
				if(this.algSelect.equalsIgnoreCase("OnebyOne"))
					this.migrationPlanOnebyOne(migrationMap);
				if(this.algSelect.equalsIgnoreCase("DepGraph"))
					this.migrationPlanDepGraph(migrationMap);
				if(this.algSelect.equalsIgnoreCase("GWIN"))
					this.migrationPlanGWIN(migrationMap);
			}
		}
	}
	
	Iterator<Entry<Integer, LinkedList<Memcopying>>> groupIter = null;
	int groupIndex = -1;
	int currentGroupNum = 0;
	int inMigrationNum = 0;
	
	public void migrationPlanGWIN(List<Map<String, Object>> migrationMap) {
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanGWIN(vmGroups, migrationMap, this);
		
		planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		
		this.inMigrationList = new ArrayList<>();
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//scheduling migration based on the plan, Change key from chid to mig group id
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(groupIter.hasNext()) {
			// Start the first migration group
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
				
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> migGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
			
			for(Memcopying mig: migGroupTemp) {								
				this.processMigrationStart(mig);
				migGroup.remove(mig);
			}
		}
	}
	

	// example of getting the migration list and do the multiple migration planning
	public void migrationPlan(List<Map<String, Object>> migrationMap) {
		
		for (Map<String, Object> migrate:migrationMap) {
			Vm vm = (Vm) migrate.get("vm");
			Host host = (Host) migrate.get("host");
			host.addMigratingInVm(vm);
			
			int srcHost = vm.getHost().getId();
			int dstHost = host.getId();
			int srcVm = this.getMigHostIdToVmIdTable().get(srcHost);
			int dstVm = this.getMigHostIdToVmIdTable().get(dstHost);
			long amountMem = vm.getRam();
			double startTime = CloudSim.clock();
			double currentTime = startTime;
			
			//Memcopying Activity contains the migration task's info
			Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
			act.setMigVm(vm.getId());
			act.setPrecopy(true);
			act.setStopandcopy(false);
			//send the first Memory copying
			Request req = new Request(this.getId());
			req.addActivity(act);
			Packet pkt = new Packet(act.getSrcVm(), act.getDstVm(), amountMem, this.findMigFlow(srcHost, dstHost), req);
			this.getNos().addPacketToChannel(pkt);
			
		}
	}
	
	private void startUserMove(List<Map<String, Object>> usermovMapList) {
		//move end user (SDNVM type) from one base station (SDNHost type) to another without network and down time overheads
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
	}
	
	public List<Memcopying> getPlannedMigrationList() {
		return this.plannedMigrationList;
	}
	public List<Memcopying> getInMigrationList() {
		return this.inMigrationList;
	}
	
	public void edgeMigrationIterSingle(List<Map<String, Object>> usermovMapList, List<Map<String, Object>> migrationMapList) {
		//initialize migration manager
		this.inMigrationList = new ArrayList<>();
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanEdgeDepGraphMISsingleIter(vmGroups, migrationMapList, this);
		List<Memcopying> migList = this.planning.getMigrationPlan();
		//start all user movement base on start time
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
				
		//start all live container migration based on start time
		for(Memcopying mig:migList) {
			this.processEdgeMigrationStart(mig, mig.getStartTime()-CloudSim.clock());
		}
						
		this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
	}
	
	
	public void edgeMigrationFPTAS(List<Map<String, Object>> usermovMapList, List<Map<String, Object>> migrationMapList) {
		//initialize migration manager
		this.inMigrationList = new ArrayList<>();
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanEdgeFPTA(vmGroups, migrationMapList, this);
		List<Memcopying> migList = this.planning.getMigrationPlan(); //get all migration request in the simulations
		
		//start all user movement base on start time
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
				
		//start all live container migration based on start time
		for(Memcopying mig:migList) {
			this.processEdgeMigrationStart(mig, mig.getStartTime()-CloudSim.clock());
		}
						
		this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
	}
	
	public void edgeMigrationEDF(List<Map<String, Object>> usermovMapList, List<Map<String, Object>> migrationMapList) {
		
	}
	
	public void edgeMigrationHeur(List<Map<String, Object>> usermovMapList, List<Map<String, Object>> migrationMapList) {
		//initialize migration manager
		this.inMigrationList = new ArrayList<>();
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanEdgeDepGraphHeur(vmGroups, migrationMapList, this);
		List<Memcopying> migList = this.planning.getMigrationPlan();
		
		//start all user movement base on start time
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
		
		//start all live container migration based on start time
		for(Memcopying mig:migList) {
			this.processEdgeMigrationStart(mig, mig.getStartTime()-CloudSim.clock());
		}
				
		this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
		
	}
	
	
	/*
	 *  edge container migration simulation
	 */
	public void edgeMigrationNoSch(List<Map<String, Object>> usermovMapList, List<Map<String, Object>> migrationMapList) {
		//no planning and scheduling of live container migrations, triggered the live migration immediately when receive the request
		
		//update the host to host routing as the scheduling may have some extented delay when user move to the other base
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanEdge(vmGroups, migrationMapList, this);
		List<Memcopying> migList = this.planning.getMigrationPlan();
		
		//start all user movement base on start time
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
		
		//start all live container migration based on start time
		for(Memcopying mig:migList) {
			this.processEdgeMigrationStart(mig, mig.getStartTime()-CloudSim.clock());
			//this.processMigrationStartTest(mig);
			//this.processMigrationStart(mig, mig.getStartTime()-CloudSim.clock()); //memocpying, relative delay
		}
		
		this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
	
	}
	
	
	public void edgeMigrationNoUserMobility() {
		//done nothing
	}
	
	public void edgeMigrationNoMig(List<Map<String, Object>> usermovMapList) {
		//only support user mobility, the container allocated in the edge won't change its position
		//TODO need to update the host routing for larger range as container allocated on edge datacenter doesn't move
		//start all user movement base on start time
		for(Map<String, Object> usermov:usermovMapList) {
			this.processUserMobilityStart(usermov);
		}
	}
	
	public void edgeMigrationDepGraphHeur() {
		//maximum-weight MIS heuristic grouping for large-scale multiple container migrations in edge environments
	}
	
	public void edgeMigrationDepGraphMISsingleIter() {
		//sort the migration and its graph vertex based on weight and create the MIS
	}
	
	public void edgeMigrationDepGraphMISmultiIter() {
		//iteratively calculate the MIS of remaining graph 
	}
	
	public void edgeMigrationDepGraphMISClique() {
		//clique-based MIS heuristic algorithm to calculate the MIS fast (SLAMIG paper)
	}
	
	//EDF: earliest deadline first scheduling + concurrent migration scheduler
	public void migrationPlanEDF(List<Map<String, Object>> migrationMap) {
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanEDF(vmGroups, migrationMap, this);
		this.planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		this.planning.printPlan();
		this.inMigrationList = new ArrayList<>();
		
		this.migStartTime = CloudSim.clock();
		
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(groupIter.hasNext()) {
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
			
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> toMigGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(toMigGroup);
			for(Memcopying mig: migGroupTemp) {
				if(this.isMigFeasible(mig)) {
					this.processMigrationStart(mig);
					toMigGroup.remove(mig);			
				}
			}
		}
	}
	
	//None: start all feasible migrations at the same time without planning
	public void migrationPlanNone(List<Map<String, Object>> migrationMap) {
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanNone(vmGroups, migrationMap, this);
		this.planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		this.inMigrationList = new ArrayList<>();
		
		this.migStartTime = CloudSim.clock();
		
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(groupIter.hasNext()) {
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
			
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> toMigGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(toMigGroup);
			for(Memcopying mig: migGroupTemp) {
				if(this.isMigFeasible(mig)) {
					this.processMigrationStart(mig);
					toMigGroup.remove(mig);			
				}
			}
		}
	}
	
	//depGraph
	public void migrationPlanDepGraph(List<Map<String, Object>>migrationMap) {
		Map<Vm, VmGroup> vmGroups = null;
		String filePath = "internet-topo-depGraph/";
		String fileBase = this.getNos().physicalTopologyFileName.split("/")[1] + "-DepGraph.txt";
		this.planning = new VMMIgrationPlanDepGraph(filePath+fileBase, vmGroups, migrationMap, this);
		this.planning.processMigrationPlan();
	}
	
	//One-by-One: Heuristic sequencial vm planning, just return a list of migration
	public void migrationPlanOnebyOne(List<Map<String, Object>> migrationMap) {
		//Configuration.MPTCP_SUBFLOW_MAX = 10;
		Map<Vm, VmGroup> vmGroups = null;
		this.inMigrationList = new ArrayList<>();
		this.planning = new VMMigrationPlanOnebyOne(vmGroups, migrationMap, this);
		this.planning.processMigrationPlan();
		List<Memcopying> toMigList = planning.getMigrationPlan();
		if(toMigList.size()!=0) {
			this.processMigrationStart(toMigList.get(0));
			toMigList.remove(0);
		}
	}
	
	//FPTA algorithm presented in H. Wang, "VM Migration Planning in SDNs"
	//Input: G(V,E), link capacity c(e), migration requests
	//Output: Bw l_k, binary decision variable X_k, x(p) p belongs to P.
	// periodically called and call when one migration finished
	public void migrationPlanFPTA(List<Map<String, Object>> migrationMap) {
		//Configuration.MIG_SHARE_BANDWIDTH = false;
		//Configuration.MPTCP_SUBFLOW_MAX = 10;
		
		Map<Vm, VmGroup> vmGroups = null;
		this.inMigrationList = new ArrayList<>();
		this.planning = new VMMigrationPlanFPTA(vmGroups, migrationMap, this);
		
		planning.processMigrationPlan();
		
		List<Memcopying> toMigList = planning.getMigrationPlan();
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		for(Memcopying mig:toMigList) {
			this.processMigrationStart(mig);
		}
		
		
	}
	
	//[TODO] SLA-MIG: Heuristic VM planning algorithm based on deadline and migration score
	public void migrationPlanSLAGroup(List<Map<String, Object>> migrationMap) {			
		//Configuration.MPTCP_SUBFLOW_MAX = 1;
		Map<Vm, VmGroup> vmGroups = null;
		this.planning = new VMMigrationPlanSLAGroup(vmGroups, migrationMap, this);
		
		planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		planning.printPlan();
		this.inMigrationList = new ArrayList<>();
		
		System.out.print(CloudSim.clock()+": current in migration size "+this.inMigrationList.size());
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//scheduling migration based on the plan, Change key from chid to mig group id
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(groupIter.hasNext()) {
			// Start the first migration group
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
				
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> migGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
			
			for(Memcopying mig: migGroupTemp) {
				double startTime = CloudSim.clock();
				double currentTime = startTime;
				int chId = mig.getChId();
				mig.setStartTime(currentTime);
				mig.setServeTime(currentTime);
				mig.setPrecopy(true);
				mig.setStopandcopy(false);
				
				this.processMigrationStart(mig);
				inMigrationNum++;
			}
		}
			
	}
	
	//CQNCR with on-line scheduling
	public void migrationPlanSchCQNCR(List<Map<String, Object>> migrationMap) {
		boolean flagScheduler = true;
		
		Map<Vm, VmGroup> vmGroups = null;	
		this.planning = new VMMigrationPlanCQNCR(vmGroups, migrationMap, this);
		
		planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		planning.printPlan();
		this.inMigrationList = new ArrayList<>();
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//scheduling migration based on the plan, Change key from chid to mig group id
		//for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(flagScheduler == false) {
		while(groupIter.hasNext()) {
			// Start the first migration group
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
				
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> migGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
			
			for(Memcopying mig: migGroupTemp) {
				double startTime = mig.getStartTime();
				double currentTime = startTime;
				int chId = mig.getChId();
				mig.setServeTime(currentTime);
				mig.setPrecopy(true);
				mig.setStopandcopy(false);
				
				//[START MIGRATION]: 
				// reserve resources in dst host
				SDNVm vm = (SDNVm) mig.migrate.get("vm");
				Host host = (Host) mig.migrate.get("host");
				if(host != null && vm!=null) {
					host.addMigratingInVm(vm);
				}else
					throw new NullPointerException();	
				double delay = startTime - CloudSim.clock();
				this.processMigrationStart(mig, delay);
				migGroup.remove(mig);
			}
		}
		}
		if(flagScheduler == true) {
			if(groupIter.hasNext()) {
				// Start the first migration group
				Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
				int groupNum = entry.getKey();
				this.currentGroupNum = groupNum;
				this.groupIndex++;
					
				System.out.println("Start Mig Group:" + groupNum);
				List<Memcopying> migGroup = entry.getValue();
				List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
				
				for(Memcopying mig: migGroupTemp) {
					double startTime = CloudSim.clock();
					//double startTime = mig.getStartTime();
					double currentTime = startTime;
					int chId = mig.getChId();
					mig.setStartTime(currentTime);
					mig.setServeTime(currentTime);
					mig.setPrecopy(true);
					mig.setStopandcopy(false);
					
					//[START MIGRATION]: 
					// reserve resources in dst host
					SDNVm vm = (SDNVm) mig.migrate.get("vm");
					Host host = (Host) mig.migrate.get("host");
					if(host != null && vm!=null) {
						host.addMigratingInVm(vm);
					}else
						throw new NullPointerException();	
					//double delay = startTime - CloudSim.clock();
					//this.processMigrationStart(mig, delay);
					this.processMigrationStart(mig);
					migGroup.remove(mig);
				}
			}
		}
		
	}
	
	//Heuristic VM planning algorithm CQNCR 
	public void migrationPlanCQNCR(List<Map<String, Object>> migrationMap) {
		boolean flagScheduler = Configuration.CQNCR_SCHEDULER_FLAG;
		
		Map<Vm, VmGroup> vmGroups = null;	
		this.planning = new VMMigrationPlanCQNCR(vmGroups, migrationMap, this);
		
		planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		planning.printPlan();
		this.inMigrationList = new ArrayList<>();
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//scheduling migration based on the plan, Change key from chid to mig group id
		//for(Entry<Integer, LinkedList<Memcopying>> entry: this.migPlan.entrySet()) {
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(flagScheduler == false) {
		while(groupIter.hasNext()) {
			// Start the first migration group
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
				
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> migGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
			
			for(Memcopying mig: migGroupTemp) {
				double startTime = mig.getStartTime();
				double currentTime = startTime;
				int chId = mig.getChId();
				mig.setServeTime(currentTime);
				mig.setPrecopy(true);
				mig.setStopandcopy(false);
				
				//[START MIGRATION]: 
				// reserve resources in dst host
				//SDNVm vm = (SDNVm) mig.migrate.get("vm");
				//Host host = (Host) mig.migrate.get("host");
				//if(host != null && vm!=null) {
				//	host.addMigratingInVm(vm);
				//}else
				//	throw new NullPointerException();	
				double delay = startTime - CloudSim.clock();
				System.out.println("VM "+((SDNVm)mig.migrate.get("vm")).getName()+ "start");
				System.out.println("Group Start Delay: "+delay);
				this.processMigrationStart(mig, delay);
				migGroup.remove(mig);
			}
		}
		}
		if(flagScheduler == true) {
			if(groupIter.hasNext()) {
				// Start the first migration group
				Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
				int groupNum = entry.getKey();
				this.currentGroupNum = groupNum;
				this.groupIndex++;
					
				System.out.println("Start Mig Group:" + groupNum);
				List<Memcopying> migGroup = entry.getValue();
				List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
				
				for(Memcopying mig: migGroupTemp) {
					double startTime = CloudSim.clock();
					//double startTime = mig.getStartTime();
					double currentTime = startTime;
					int chId = mig.getChId();
					mig.setStartTime(currentTime);
					mig.setServeTime(currentTime);
					mig.setPrecopy(true);
					mig.setStopandcopy(false);
					
					this.processMigrationStart(mig);
					migGroup.remove(mig);
				}
			}
		}
		
	}
	
	public int findMigFlow(int srcHost, int dstHost) {
		//THE ARCNAME should be the combination of host id
		String arcName = "mig"+Integer.toString(srcHost) +"-"
				+ Integer.toString(dstHost);
		Integer chId = this.migFlowNameToIdTable.get(arcName);
		if(chId != null)
			return chId;
		else
			return -1;
	}
	
	public void processUserMobilityStart(Map<String, Object> mov) {
		//"user", "base", "time"
		Map<String, Object> mig = new HashMap<>();
		mig.put("vm", mov.get("user"));
		mig.put("host", mov.get("base"));
		double time =(double)mov.get("time");
		this.send(
				getId(),
				time-CloudSim.clock(),
				CloudSimTags.VM_MIGRATE,
				mig);
		
	}
	
	
	public void processMigrationStartTest(Memcopying mc) {
		this.send(
				getId(),
				mc.getStartTime()-CloudSim.clock()+5.0,
				CloudSimTags.VM_MIGRATE,
				mc.migrate);
	}
	
	public void processMigrationStartRetry(Memcopying mc, double delay) {
		this.send(this.getId(), delay, Constants.SDN_VM_MIGRATION_START, mc);
	}
	
	// start the migration task at specific time
	public void processMigrationStart(Memcopying mc, double delay) {
		mc.setPreMigStart(CloudSim.clock()+delay);
		this.send(this.getId(), delay, Constants.SDN_VM_MIGRATION_START, mc);
	}
	
	//HashMap for waiting future migration
	List<Memcopying> edgeMigWaitingList;
	HashMap<Vm, List<Memcopying>> edgeMigRequestArrivalTable; 
	
	public void processEdgeMigrationStart(Memcopying mc, double delay) {
		mc.setPreMigStart(CloudSim.clock()+delay);
		this.send(this.getId(), delay, Constants.SDN_VM_MIGRATION_EDGE_WAIT, mc);
	}
	
	public void processEdgeMigrationWait(Memcopying mig) {
		SDNVm vm = (SDNVm) mig.migrate.get("vm");
		if(vm.isInMigration()) {
			this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_FAIL, mig);
		}
		/*}else if(this.edgeMigRequestArrivalTable.get(vm)!=null){
			if(this.edgeMigRequestArrivalTable.get(vm).size()!=0) {
				this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_FAIL, mig);
			}else {
				vm.setInMigration(true);
				this.edgeMigWaitingList.add(mig);
			}
		}
		*/
		else{
			vm.setInMigration(true);
			this.edgeMigWaitingList.add(mig);
		}
	}
	
	int totalStartMig =0;
	int totalFinishMig = 0;
	int totalEdgePlanTimes = 0;
	public void processEdgeMigrationPlan() {
		//get all migration waiting for planning and scheduling with current schedule time interval
		if(this.edgeMigWaitingList.size()!=0) {
			this.totalEdgePlanTimes++;
		switch(this.algSelect) {
		case "edgeNoSch":
			this.processEdgeMigrationPlanNoSch();
			break;
		case "edgeFPTAS":
			this.processEdgeMigrationPlanFPTAS();
			break;
		case "edgeHeur":
			this.processEdgeMigrationPlanEdgeHeur(); //process the edgeMigWaitingList
		break;
		case "edgeIterSingle":
			this.processEdgeMigrationPlanEdgeHeur();
		break;	
		default:
			System.exit(1);
		break;
		}}
		
		totalStartMig += this.edgeMigWaitingList.size();
		this.edgeMigWaitingList.clear();
		
		//if(CloudSim.clock()>4600.0 && CloudSimEx.getNumFutureEvents()==1) {
		if(CloudSim.clock()>4600.0) {
			int waitingRequest = 0;
			int arrivalwaitRequest = 0;
			for(List<Memcopying> arrivalList:this.edgeMigRequestArrivalTable.values()) {
				arrivalwaitRequest = arrivalwaitRequest + arrivalList.size();
				//for(Memcopying mig:arrivalList) {
				//	System.out.println(mig.migrate.get("time")+" "+mig.migrate.get("src")+" "+mig.migrate.get("host"));
				//}
			}
			waitingRequest = this.edgeMigWaitingList.size();
			System.out.println("waiting request size: "+ waitingRequest + " arrival waiting: " + arrivalwaitRequest);
			System.out.println("total started migration: "+totalStartMig);
			System.out.println("total finished migration: "+totalFinishMig);
			System.out.println("total runtime: "+this.planning.getRunTime());
			System.out.println("total planTimes: "+this.totalEdgePlanTimes);
			if(waitingRequest !=0) {
				this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
			}
				
		}else {
			this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
		}	
		//this.send(this.getId(), Configuration.MIG_EDGE_PLAN_INTERVAL, Constants.SDN_VM_MIGRATION_EDGE_PLAN);
	}
	
	public void processEdgeMigrationPlanNoSch() {
		for(Memcopying mig:this.edgeMigWaitingList) {
			this.processMigrationStart(mig);
		}
	}
	
	public void processEdgeMigrationPlanFPTAS() {
		List<Memcopying> remainedList = this.planning.getMigWaitingList();
		remainedList.addAll(this.edgeMigWaitingList);
		this.planning.setMigWaitingList(remainedList);
		
		planning.processMigrationPlan();
		
		List<Memcopying> toMigList = planning.getMigrationPlan();
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//for(Memcopying mig:this.edgeMigWaitingList) {
		//	this.addToPlannedMigrationList(mig);
		//}
		
		for(Memcopying mig:toMigList) {
			this.addToPlannedMigrationList(mig);
			this.processMigrationStart(mig);
			this.planning.getMigWaitingList().remove(mig);
		}
	}
	
	/**
	 * planning and scheduling live migrations in edge computing environments
	 */
	public void processEdgeMigrationPlanEdgeHeur() {
		this.groupIndex = -1;
		this.planning.setMigWaitingList(this.edgeMigWaitingList);
				
		planning.processMigrationPlan();
		
		this.migPlan = planning.getMigrationPlanMap();
		
		
		for(Memcopying mig:this.edgeMigWaitingList) {
			this.addToPlannedMigrationList(mig);
		}
		
		// start time of multiple migration tasks
		this.migStartTime = CloudSim.clock();
		
		//scheduling migration based on the plan, Change key from chid to mig group id
		Set<Entry<Integer, LinkedList<Memcopying>>> set = this.migPlan.entrySet();
		groupIter = set.iterator();
		if(groupIter.hasNext()) {
			// Start the first migration group
			Map.Entry<Integer, LinkedList<Memcopying>> entry = (Entry<Integer, LinkedList<Memcopying>>) groupIter.next();
			int groupNum = entry.getKey();
			this.currentGroupNum = groupNum;
			this.groupIndex++;
			System.out.println("group index "+this.groupIndex);
				
			System.out.println("Start Mig Group:" + groupNum);
			List<Memcopying> migGroup = entry.getValue();
			List<Memcopying> migGroupTemp = new LinkedList<>(migGroup);
			
			for(Memcopying mig: migGroupTemp) {
				if(this.mcList.contains(mig)) {
					migGroup.remove(mig);
				}
				else if(this.isMigFeasible(mig) || this.inMigrationList.contains(mig)) {
					this.processMigrationStart(mig);
					migGroup.remove(mig);
				}
			}
		}
	}
	
	
	/*
	 * put the first migration request in arrival waiting list to the planning and scheduling waiting list
	 */
	public void processEdgeMigrationFinish(Memcopying mig) {
		this.totalFinishMig +=1;
		SDNVm vm = (SDNVm) mig.migrate.get("vm");
		if(this.edgeMigRequestArrivalTable.containsKey(vm)) {
			List<Memcopying> arrivalRequestList = edgeMigRequestArrivalTable.get(vm);
			if(arrivalRequestList.size()!=0 && !vm.isInMigration()) {
				Memcopying nextMig = arrivalRequestList.remove(0);
				//this.edgeMigWaitingList.add(nextMig);
				this.send(this.getId(), 0, Constants.SDN_VM_MIGRATION_EDGE_WAIT, nextMig);
			}
		}
	}
	
	
	//start the migration task now
	public void processMigrationStart(Memcopying mc) {
		SDNVm vm = (SDNVm) mc.migrate.get("vm");
		//if(this.inMigrationList !=null)
		if(!this.inMigrationList.contains(mc) && !this.mcList.contains(mc)) {
			Host host = (Host) mc.migrate.get("host");
			if(host != null && vm!=null) {
				host.addMigratingInVm(vm);
			}else
				throw new NullPointerException();
			
			this.addToinMigrationList(mc);
			this.inMigrationNum++;
			
			System.out.println(CloudSim.clock()+" pre mig start "+ ((SDNVm)mc.migrate.get("vm")).getName());
			mc.setPreMigStart(CloudSim.clock());
			this.send(this.getId(), mc.getPreMigTime(), Constants.SDN_VM_MIGRATION_PRE, mc);
		}
	}
	
	
	private void addToPlannedMigrationList(Memcopying mig) {
		if(!this.plannedMigrationList.contains(mig))
			this.plannedMigrationList.add(mig);
	}
	
	private void addToinMigrationList(Memcopying mig) {
		if(!this.inMigrationList.contains(mig))
			this.inMigrationList.add(mig);
	}
	
	public Hashtable<Integer, Integer> getMigHostIdToVmIdTable() {
		return migHostIdToVmIdTable;
	}

	public void setMigHostIdToVmIdTable(Hashtable<Integer, Integer> migHostIdToVmIdTable) {
		this.migHostIdToVmIdTable = migHostIdToVmIdTable;
	}

	public NetworkOperatingSystem getNos() {
		return nos;
	}

	public void setNos(NetworkOperatingSystem nos) {
		this.nos = nos;
	}

	public double getAlgTime() {
		if(this.planning != null)
			this.algTime = this.planning.getRunTime();
		return algTime;
	}

	public void setAlgTime(double algTime) {
		this.algTime = algTime;
	}
	
	public void setAlgSelect(String migAlg) {
		this.algSelect = migAlg;
	}
	
	public String getAlgSelect() {
		return this.algSelect;
	}
	
/*	public void startMigrate() {
	if (isMigrateEnabled) {
		Log.printLine(CloudSim.clock()+": Migration started..");

		List<Map<String, Object>> migrationMap = getVmAllocationPolicy().optimizeAllocation(
				getVmList());

		if (migrationMap != null && migrationMap.size() > 0) {

			migrationAttempted += migrationMap.size();
			
			// Process cloudlets before migration because cloudlets are processed during migration process..
			updateCloudletProcessing();
			checkCloudletCompletion();

			for (Map<String, Object> migrate : migrationMap) {
				Vm vm = (Vm) migrate.get("vm");
				Host targetHost = (Host) migrate.get("host");
//				Host oldHost = vm.getHost();
				
				
				Log.formatLine(
						"%.2f: Migration of %s to %s is started",
						CloudSim.clock(),
						vm,
						targetHost);
				
				// set pre-migration allocation at destination Host
				targetHost.addMigratingInVm(vm);

				// [OLD]
				*//** VM migration delay = RAM / bandwidth **//*
				// we use BW / 2 to model BW available for migration purposes, the other
				// half of BW is for VM communication
				// around 16 seconds for 1024 MB using 1 Gbit/s network
				
				// [NEW FIX]
				// [PRE-COPY MIGRATION MODEL] W_pre + W_net + W_post
				// NEED CALCULATE each round transmission time based on dirty page.
				// T_i = (R * T_(i-1)) / L 
				// -> #IF R is constant# (MEM * R^i) / L^(i+1)
				// W_net = 
				// [TO FIX] CREATE both processing and transmission for migration task
				// for processing: pre-migration workloads on origin host 
				//                 & post-migration processing workloads on both origin and destination hosts
				// for transmission: migration memory-copy data from origin to destination host
				
				send(
						getId(),
						vm.getRam() / ((double) targetHost.getBw() / (2 * 8000)),
						CloudSimTags.VM_MIGRATE,
						migrate);
			}
		}
		else {
			//Log.printLine(CloudSim.clock()+": No VM to migrate");
		}
	}		
	}
*/

}
