/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn.policies;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Pe;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.VmAllocationPolicy;
import org.cloudbus.cloudsim.VmScheduler;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.provisioners.BwProvisioner;
import org.cloudbus.cloudsim.provisioners.BwProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.PeProvisioner;
import org.cloudbus.cloudsim.provisioners.PeProvisionerSimple;
import org.cloudbus.cloudsim.provisioners.RamProvisioner;
import org.cloudbus.cloudsim.provisioners.RamProvisionerSimple;
import org.cloudbus.cloudsim.sdn.Arc;
import org.cloudbus.cloudsim.sdn.Middlebox;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.sfc.ServiceFunctionChainPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyEx;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyGroupConnectedFirst;
import org.cloudbus.cloudsim.sdn.vmallocation.VmAllocationPolicyManual;
import org.cloudbus.cloudsim.sdn.vmallocation.VmMigrationPolicy;
import org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VmMigrationPolicyGroupConnectedFirst;

/**
 * Simple network operating system class for the example. 
 * In this example, network operating system (aka SDN controller) finds shortest path
 * when deploying the application onto the cloud. 
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class NetworkOperatingSystemSimple extends NetworkOperatingSystem {

	public NetworkOperatingSystemSimple(String physicalTopologyFilename) {
		super(physicalTopologyFilename);
	}

	@Override
	protected boolean deployApplication(List<Vm> vms,
			List<Middlebox> middleboxes, List<Arc> links,
			List<ServiceFunctionChainPolicy> sfcPolicy) {
		Log.printLine(CloudSim.clock() + ": " + getName() + ": Starting deploying application..");

		// Sort VMs in decending order of the required MIPS
		Collections.sort(vms, new Comparator<Vm>() {
		    public int compare(Vm o1, Vm o2) {
		        return (int) (o2.getMips()*o2.getNumberOfPes() - o1.getMips()*o1.getNumberOfPes());
		    }
		});
		
		//[NEW] create groups of vm
		VmAllocationPolicy vmPolicy = this.datacenter.getVmAllocationPolicy();
		
		if(vmPolicy instanceof VmAllocationPolicyManual) {
			((VmAllocationPolicyManual) vmPolicy).setNos(this);
		}
		
		if(vmPolicy instanceof VmAllocationPolicyEx) {
			VmMigrationPolicy vmMigrationPolicy = ((VmAllocationPolicyEx) vmPolicy).getVmMigrationPolicy();
			if(vmMigrationPolicy instanceof VmMigrationPolicyGroupConnectedFirst) {
				List<VmGroup> groups = createVmGroup(vms,links);
				for(VmGroup g:groups) {
					for(Vm vm:g.getVms()) {
						((VmMigrationPolicyGroupConnectedFirst) vmMigrationPolicy).addVmInVmGroup(vm, g);
					}
				}
				
			}
		}
		
		/*if(vmPolicy instanceof  VmAllocationPolicyGroupConnectedFirst) {
			VmMigrationPolicy vmMigrationPolicy =((VmAllocationPolicyGroupConnectedFirst) vmPolicy).getVmMigrationPolicy();
			if(vmMigrationPolicy instanceof VmMigrationPolicyGroupConnectedFirst) {
				List<VmGroup> groups = createVmGroup(vms,links);
				for(VmGroup g:groups) {
					for(Vm vm:g.getVms()) {
						((VmMigrationPolicyGroupConnectedFirst) vmMigrationPolicy).addVmInVmGroup(vm, g);
					}
				}
				
			}
		}*/
		
		
		for(Vm vm:vms)
		{
			SDNVm tvm = (SDNVm)vm;
			Log.printLine(CloudSim.clock() + ": " + getName() + ": Trying to Create VM #" + tvm.getId()
					+ " in " + datacenter.getName() + ", (" + tvm.getStartTime() + "~" +tvm.getFinishTime() + ")");
			send(datacenter.getId(), tvm.getStartTime(), CloudSimTags.VM_CREATE_ACK, tvm);
			
			if(tvm.getFinishTime() != Double.POSITIVE_INFINITY) {
				//System.err.println("VM will be terminated at: "+tvm.getFinishTime());
				send(datacenter.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
				send(this.getId(), tvm.getFinishTime(), CloudSimTags.VM_DESTROY, tvm);
			}
		}
		return true;
	}
	
	@Override
	protected Middlebox deployMiddlebox(String type,Vm vm) {
		return null;
	}
	
	@Override
	public void processVmCreateAck(SimEvent ev) {
		super.processVmCreateAck(ev);
		
		// print the created VM info
		SDNVm vm = (SDNVm) ev.getData();
		Log.printLine(CloudSim.clock() + ": " + getName() + ": VM Created: " +  vm + " in " + vm.getHost());
		deployFlow(this.allArcs);
	}
	
	private List<VmGroup> createVmGroup(Collection<Vm> vms, Collection<Arc> links) {
		// Put VMs into separate group according to their connections 
		List<VmGroup> groups = new ArrayList<VmGroup>();
		List<Vm> vmPool = new ArrayList<Vm>(vms);
		List<Arc> arcPool = new ArrayList<Arc>(links);
		
		// Sort links by their bandwidth 
		Collections.sort(arcPool, new Comparator<Arc>() {
		    public int compare(Arc o1, Arc o2) {
		        return (int) (o1.getBw() - o2.getBw());
		    }
		});
		
		// Separate groups by link bandwidth order
		VmGroup.classifyGroupByArcList(arcPool, vmPool, groups);
		
		// Put all other VMs into separate group
		VmGroup.putEachVmIntoEachGroup(vmPool, groups);
		
		// Sort VmGroup by their mips request
		Collections.sort(groups);
		
		return groups;
	}
	
	private boolean deployFlow(List<Arc> arcs) {
		for(Arc arc:arcs) {
			buildForwardingTable(arc.getSrcId(), arc.getDstId(), arc.getFlowId());			
		}
		
		/*/ Print all routing tables.
		for(Node node:this.topology.getAllNodes()) {
			node.printVMRoute();
		}
		//*/
		return true;
	}
	
	@Override
	public SDNHost createHost(int ram, long bw, long storage, long pes, double mips) {
		LinkedList<Pe> peList = new LinkedList<Pe>();
		int peId=0;
		for(int i=0;i<pes;i++) {
			PeProvisioner pp =  new PeProvisionerSimple(mips);
			peList.add(new Pe(peId++, pp));
		}
		
		RamProvisioner ramPro = new RamProvisionerSimple(ram);
		BwProvisioner bwPro = new BwProvisionerSimple(bw);
		VmScheduler vmScheduler = new VmSchedulerTimeSharedEnergy(peList);		
		SDNHost newHost = new SDNHost(ramPro, bwPro, storage, peList, vmScheduler, this);
		
		return newHost;		
	}
}
