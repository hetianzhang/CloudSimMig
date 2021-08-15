package org.cloudbus.cloudsim.sdn.sfc;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelFull;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Channel;
import org.cloudbus.cloudsim.sdn.NetworkOperatingSystem;
import org.cloudbus.cloudsim.sdn.Packet;
import org.cloudbus.cloudsim.sdn.Processing;
import org.cloudbus.cloudsim.sdn.Request;
import org.cloudbus.cloudsim.sdn.Transmission;

public class ServiceFunctionForwarder {
	protected NetworkOperatingSystem nos;
	
	// SFC policies
	protected HashMap<String, ServiceFunctionChainPolicy> policyTable = new HashMap<String, ServiceFunctionChainPolicy>();	// getKey(srcVM, dstVM, flowID) -> ServiceFunctionChainPolicy
	
	protected Map<ServiceFunction, List<ServiceFunction>> sfPool; // Service Function Pool for Auto-Scaling
	protected Map<ServiceFunction, Long> sfLoadbalanceCounter;
	
	protected Map<Integer, ServiceFunction> sfOriginalMap; // new extra SF --> original SF.	
	
	public ServiceFunctionForwarder(NetworkOperatingSystem netOS) {
		this.nos = netOS;
		sfPool = new HashMap<ServiceFunction, List<ServiceFunction>>();
		sfLoadbalanceCounter = new HashMap<ServiceFunction, Long>();
		sfOriginalMap = new HashMap<Integer, ServiceFunction>();
	}

	public Packet enforceSFC(Packet orgPkt) {
		Packet pkt = orgPkt;
		int src = pkt.getOrigin();
		int dst = pkt.getDestination();
		int flowId = pkt.getFlowId();
		
		// Encapsulate a packet, if it needs to go through SFC.
		if(policyTable.containsKey(NetworkOperatingSystem.getKey(src,dst,flowId))) {
			// SFC Policy should be enforced!
			ServiceFunctionChainPolicy policy = policyTable.get(NetworkOperatingSystem.getKey(src,dst,flowId));
			pkt = encapsulatePacket(orgPkt, policy);
		}
		return pkt;
	}

	public void processVmCreateDyanmicAck(ServiceFunction newSf) {
		ServiceFunction orgSf = sfOriginalMap.get(newSf.getId());
		addDuplicatedPath(orgSf, newSf);
	}
	
	private Packet encapsulatePacket(Packet orgPacket, ServiceFunctionChainPolicy policy) {
		List<Integer> sfc = policy.getServiceFunctionChain();
		//Log.printLine(CloudSim.clock() + ": ServiceFunctionForwarder.enforceSFC(): "+ orgPacket + " going through the chain of " + sfc);
		
		policy.addMonitoredDelayDataPacket(orgPacket);
		Packet pkt = orgPacket;
		
		for(int i = sfc.size() -1 ; i >= 0; i--){
			int sfId = sfc.get(i);
			pkt = redirectRequest(pkt, sfId);
		}
		return pkt;
	}

	private Packet redirectRequest(Packet orgPacket, int sfId) {
		int srcVmId = orgPacket.getOrigin();
		int dstVmId = orgPacket.getDestination();
		int flowId = orgPacket.getFlowId();
		long packetSize = orgPacket.getSize();
		Request orgPayload = orgPacket.getPayload();
		
		int userId = orgPayload.getUserId();
		
		Request encapsulatedReq = new Request(userId);
		
		ServiceFunction orgSF = (ServiceFunction)nos.findVm(sfId);
		
		// Load balancing
		ServiceFunction sf = loadbalanceSF(orgSF);

		// Create the latter request (SF -> DestVM)
		long sfProcessingSize = sf.getMIperOperation();
		
		Processing sfProc = createRequestActivityProcessing((int)encapsulatedReq.getRequestId(), sf.getId(), userId, sfProcessingSize);
		Transmission sfToDst = createRequestActivityTransmission(sf.getId(), dstVmId, flowId,
				packetSize, orgPayload, orgPacket);
		
		encapsulatedReq.addActivity(sfProc);
		encapsulatedReq.addActivity(sfToDst);
		
		// Create the former request (SourceVM -> SF), which includes the latter request processing afterwards.
		Transmission srcToSf = createRequestActivityTransmission(srcVmId, sf.getId(), flowId,
				packetSize, encapsulatedReq, null);
		
		//Log.printLine(CloudSim.clock() + ": ServiceFunctionForwarder.redirectRequest(): "+ orgPacket + ": redirected to: " + orgSF + " (LB to "+sf+")");
		
		return srcToSf.getPacket();
	}
	
	private ServiceFunction loadbalanceSF(ServiceFunction orgSF) {
		// Get a SF for load balacing with Round robin method.
		List<ServiceFunction> sfList = sfPool.get(orgSF);
		if(sfList == null || sfList.size() == 0)
			return orgSF;

		Long count = sfLoadbalanceCounter.get(orgSF);
		if(count == null)
			count = 0L;
		
		int index = (int) (count % sfList.size()); // Round robin.
		ServiceFunction selectedSF = sfList.get(index);
		sfLoadbalanceCounter.put(orgSF, count+1);
		return selectedSF;
	}

	private Processing createRequestActivityProcessing(int cloudletId, int vmId, int userId, long workloadSize) {
		int peNum=1;
		long fileSize = 300;
		long outputSize = 300;
		UtilizationModel utilizationModel = new UtilizationModelFull();
		
		Cloudlet cloudlet= new Cloudlet(cloudletId, workloadSize, peNum, 
				fileSize, outputSize, utilizationModel, utilizationModel, utilizationModel);
		
		cloudlet.setUserId(userId);
		cloudlet.setVmId(vmId);

		Processing proc = new Processing(cloudlet);

		return proc;
	}
	
	private Transmission createRequestActivityTransmission(int fromVmId, int toVmId, int flowId,
			long packetSize, Request payload, Packet orgPacket) {
		Transmission trans = new Transmission(fromVmId, toVmId, packetSize, flowId, payload, orgPacket);
		
		return trans;
	}

	public void addPolicy(ServiceFunctionChainPolicy policy) {
		policyTable.put(NetworkOperatingSystem.getKey(policy.getSrcId(),policy.getDstId(),policy.getFlowId()), policy);
	}

	public void addDuplicatedSF(ServiceFunction sf, ServiceFunction newSf) {
		sfOriginalMap.put(sf.getId(), sf);
		sfOriginalMap.put(newSf.getId(), sf);
		
		List<ServiceFunction> sfList = sfPool.get(sf);
		if(sfList == null) {
			sfList = new ArrayList<ServiceFunction>();
			sfList.add(sf); // add original SF to the first element
		}
		sfList.add(newSf);
		sfPool.put(sf, sfList);
		
		if(sfLoadbalanceCounter.get(sf) == null) {
			sfLoadbalanceCounter.put(sf, 0L);
		}
		
		nos.addExtraVm(newSf); // This function will create a VM and get a callback, which will call this.processVmCreateDyanmicAck() at last. 
	}

	public boolean removeDuplicatedSF(ServiceFunction sf) {
		List<ServiceFunction> sfList = sfPool.get(sf);
		if(sfList == null || sfList.size() <= 1) {
			// Cannot remove more SF
			return false;
		}
		ServiceFunction removedSF = sfList.remove(sfList.size()-1);	//Always remove the last one.
		sfPool.put(sf, sfList);
		
		nos.removeExtraVm(removedSF);
		
		return true;
	}
	
	public List<ServiceFunction> getAllDuplicatedSF(ServiceFunction orgSf) {
		List<ServiceFunction> allSf = sfPool.get(orgSf);
		if(allSf == null) {
			allSf = new ArrayList<ServiceFunction>();
			allSf.add(orgSf);
			sfPool.put(orgSf, allSf);
		}
		return allSf;
	}
	
	private void addDuplicatedPath(ServiceFunction orgSf, ServiceFunction newSf) {
		int orgSfId = orgSf.getId();
		int newSfId = newSf.getId();
		nos.addExtraPath(orgSfId, newSfId);
	}
	
	public ServiceFunction getOriginalSF(int vmId) {
		return this.sfOriginalMap.get(vmId);
	}
	
	private List<ServiceFunction> findDuplicatedVmList(int vmId) {
		// returns the number of duplicated VMs
		ServiceFunction orgSf = getOriginalSF(vmId);
		if(orgSf != null)
			return sfPool.get(orgSf);
		return null;
	}

	private long getDuplicatedPathBandwidth(ServiceFunctionChainPolicy policy, int fromId, int toId) {
		// Duplicated SF is managed by the forwarder. Policy class only contains the master information of the policy.
		// Specific information of implemented policy is maintained by Forwarder class.
		int fromNum = 1;
		int toNum = 1;
		
		List<ServiceFunction> fromList = findDuplicatedVmList(fromId);
		if(fromList != null)
			fromNum = fromList.size();

		List<ServiceFunction> toList = findDuplicatedVmList(toId);
		if(toList != null)
			toNum = toList.size();

		int numCombination = fromNum * toNum;
		long distBw = policy.getTotalBandwidth() / numCombination;
		
		return distBw;
	}
	
	private List<Integer> generateDuplicatedVmIdList(int vmId) {
		// Generate a list of duplicated Source / Destination VMs
		List<Integer> list = new ArrayList<Integer>();
		{
			List<ServiceFunction> sfList = findDuplicatedVmList(vmId);
			if(sfList == null) {
				list.add(vmId);
			}
			else {
				for(ServiceFunction dupSf:sfList)
					list.add(dupSf.getId());
			}
		}
		return list;
	}
	
	private void updateDuplicatedSectionBandwidth(ServiceFunctionChainPolicy policy, int fromId, int toId) {
		long sectionBw = getDuplicatedPathBandwidth(policy, fromId, toId);
		policy.setSectionBandwidth(fromId, sectionBw);
		Log.printLine(CloudSim.clock() + ": ServiceFunctionForwarder.updateDuplicatedSectionBandwidth(): "+ policy 
				+ ", totalBw="+policy.getTotalBandwidth()+", sectionBW="+sectionBw + "(fromId =" +fromId+")");

		List<Integer> fromList = generateDuplicatedVmIdList(fromId);
		List<Integer> toList = generateDuplicatedVmIdList(toId);
		
		for(int dupFromId:fromList) {
			for(int dupToId:toList) {
				// Update currently working Channels
				nos.updateChannelBandwidth(dupFromId, dupToId, policy.getFlowId(), sectionBw);
				// Update ARC for future channel creation
				nos.updateBandwidthArc(dupFromId, dupToId, policy.getFlowId(), sectionBw);
			}
		}
	}

	public void redistributeDuplicatedPathBandwidth(ServiceFunctionChainPolicy policy) {
		List<Integer> vmIds = policy.getServiceFunctionChainIncludeVM();
		for(int i=0; i < vmIds.size()-1; i++) {
			int fromId = vmIds.get(i);
			int toId = vmIds.get(i+1);
			
			updateDuplicatedSectionBandwidth(policy, fromId, toId);
		}
	}
	public void redistributeDuplicatedPathBandwidthAllChain(ServiceFunction orgSf) {
		// Find all policies including this SF
		// Update all policies bandwidth, as the number of duplicated SFs in the policy is changed.
		for(ServiceFunctionChainPolicy policy:getAllPolicies()) {
			if(policy.isSFIncludedInChain(orgSf.getId())) {
				redistributeDuplicatedPathBandwidth(policy);
			}
		}
	}

	
	
	public Collection<ServiceFunctionChainPolicy> getAllPolicies() {
		return policyTable.values();
	}

	public void resetSFCMonitor() {
		for(ServiceFunctionChainPolicy policy:getAllPolicies()) {
			policy.resetMonitoredDelayData();
			policy.resetMonitoredBwData();					
		}
	}
	
	public void updateSFCMonitor(Channel ch, long processedBytes) {
		int fromId = ch.getSrcId();
		int toId = ch.getDstId();
		int flowId = ch.getChId();
		
		/*
		ServiceFunction orgSf;
		if((orgSf = sfOriginalMap.get(fromId)) != null)
			fromId = orgSf.getId();
		
		if((orgSf = sfOriginalMap.get(toId)) != null)
			toId = orgSf.getId();
		*/
		
		for(ServiceFunctionChainPolicy policy:getAllPolicies()) {
			// Find a policy with the same flow ID.
			if(policy.getFlowId() == flowId) {
				boolean success = policy.addMonitoredBwData(fromId, toId, processedBytes);
				if(success) {
					//Log.printLine(CloudSim.clock() + ": ServiceFunctionForwarder.updateMonitor(): "+ policy + ", network_bytes=" + processedBytes);
				}
			}
		}
	}
}
