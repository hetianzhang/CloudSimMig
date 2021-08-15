package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.cloudbus.cloudsim.sdn.vmallocation.HostSelectionPolicyMostFull;

public class VmMigrationPolicyGroupConnectedFirstEx extends VmMigrationPolicyGroupConnectedFirst{
	
	@Override
	protected Map<Vm, Host> buildMigrationMapOverloadedHost(List<SDNHost> hosts) {
		Map<Vm, Host> vmToHost = new HashMap<Vm, Host>();
		// Check peak VMs and reallocate them into different host
		List<SDNVm> migrationOverVMList = new ArrayList<>();;
		
		for(SDNHost h:hosts) {
			for(Vm vm:h.getVmList()) {
				if(vm.getMips()!=0)
					migrationOverVMList.add((SDNVm) vm);
			}
		}
		//consolidate all vms based on the vmgroups
		for(Entry<Vm, VmGroup> group: this.vmGroups.entrySet()) {
			Map<SDNHost, Integer> hostHitMap = new HashMap<>();
			VmGroup g = group.getValue();
			List<Vm> vmList = g.getVms();
			for(Vm vm:vmList) {
				SDNHost h = (SDNHost) vm.getHost();
				Integer hits = hostHitMap.get(h);
				if(hits == null) {
					hits = 0;
				}
				hits++;
				hostHitMap.put(h, hits);
			}
			Map<SDNHost, Integer> sortedHostList = sortByValue(hostHitMap);
			List<SDNHost> hList = new ArrayList<>();
			for(SDNHost h :sortedHostList.keySet()) {
				hList.add(h);
				//if(Configuration.MIG_FORCE_ALL==false)
				//break;
			}
			for(Vm vm: vmList) {
				List<Host> targetHosts = null;
				Host migratedHost = null;
				
				if(hList.size()>1) {
					if(!hList.get(0).equals(vm.getHost())) {
						targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm((SDNVm)vm, hList, vmAllocationPolicy);
						migratedHost = moveVmToHost((SDNVm)vm, targetHosts);
						if (Configuration.MIG_FORCE_ALL == true)
						if(migratedHost == null) {		
							targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm((SDNVm)vm, hList, vmAllocationPolicy);
							migratedHost = moveVmToHost((SDNVm)vm, targetHosts);
						}
					}
				}else {
					if(hList.size()==1) {
						//if(vmList.size()==1) {
							targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm((SDNVm)vm, hosts, vmAllocationPolicy);
							migratedHost = moveVmToHost((SDNVm)vm, targetHosts);
						//}
					}
				}
				if(migratedHost != null) {
					vmToHost.put((SDNVm)vm, migratedHost);
				}
			}	
		}
		/*
		for(SDNVm vmToMigrate:migrationOverVMList) {
			List<Host> targetHosts = null;

			// 1. Find correlated host where connected VMs are running
			VmGroup vmGroup = vmGroups.get(vmToMigrate);
			List<SDNHost> connectedHosts = getHostListVmGroup(vmGroup);
			Host migratedHost = null;

			if(connectedHosts.size() > 0) {
				// If the VM is connected to the other VMs, try to put this VM into one of the hosts
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, connectedHosts, vmAllocationPolicy);
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}
			
			if(migratedHost == null) {
				targetHosts = HostSelectionPolicyMostFull.getMostFullHostsForVm(vmToMigrate, hosts, vmAllocationPolicy);
				migratedHost = moveVmToHost(vmToMigrate, targetHosts);
			}				
				
			// 3. No host can serve this VM, do not migrate.
			if(migratedHost == null) {
				System.err.println(vmToMigrate + ": Cannot find target host to migrate");
				//System.exit(-1);
				continue;
			}
			
			vmToHost.put(vmToMigrate, migratedHost);

		}*/
		return vmToHost;
	}
	
	public static <K, V extends Comparable<? super V>> Map<K, V> sortByValue(Map<K, V> map) {
		List<Entry<K, V>> list = new ArrayList<>(map.entrySet());
		list.sort(Collections.reverseOrder(Map.Entry.comparingByValue()));

	    Map<K, V> result = new LinkedHashMap<>();
	    for (Entry<K, V> entry : list) {
	        result.put(entry.getKey(), entry.getValue());
	    }

	    return result;
	}	
	
	public LinkedHashMap<SDNHost, Integer> sortHashMapByValues(
	        HashMap<SDNHost, Integer> passedMap) {
	    List<SDNHost> mapKeys = new ArrayList<>(passedMap.keySet());
	    List<Integer> mapValues = new ArrayList<>(passedMap.values());
	    Collections.reverse(mapValues);
	    //Collections.sort(mapKeys);

	    LinkedHashMap<SDNHost, Integer> sortedMap =
	        new LinkedHashMap<>();

	    Iterator<Integer> valueIt = mapValues.iterator();
	    while (valueIt.hasNext()) {
	        Integer val = valueIt.next();
	        Iterator<SDNHost> keyIt = mapKeys.iterator();

	        while (keyIt.hasNext()) {
	            SDNHost key = keyIt.next();
	            Integer comp1 = passedMap.get(key);
	            Integer comp2 = val;

	            if (comp1.equals(comp2)) {
	                keyIt.remove();
	                sortedMap.put(key, val);
	                break;
	            }
	        }
	    }
	    return sortedMap;
	}
}
