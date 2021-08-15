/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;


/**
 * Network data packet to transfer from source to destination.
 * Payload of Packet will have a list of activities. 
 *  
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Packet {
	private static long automaticPacketId = 0;
	final long id;
	int origin;			// origin VM adress (vm.getId())
	int destination;	// destination VM adress (vm.getId())
	final long size;
	final int flowId;
	Request payload;
	
	//[NEW] for dropped packet
	private int droppedTimes=0;
	
	//[NEW] for subflow packet
	private long originflowId;		// subflowId = flowId * 10 + i
	private int totalFlowNum=-1;
	
	private double startTime=-1;
	private double finishTime=-1;

	private Packet pktEncapsulated = null;
	
	public Packet(int origin, int destination, long size, int flowId, Request payload) {
		this.origin = origin;
		this.destination = destination;
		this.size = size;
		this.flowId = flowId;
		this.payload = payload;
		this.id = automaticPacketId++;
		
		if(size < 0) {
			throw new RuntimeException("Packet size cannot be minus! Pkt="+this+", size="+size);
		}
	}
	
	public Packet(int origin, int destination, long size, int flowId, Request payload, Packet encapsulatedPkt) { 
		this(origin, destination, size, flowId, payload);
		this.pktEncapsulated = encapsulatedPkt; 
	}
	
	public Packet getPktEncapsulated() {
		return this.pktEncapsulated;
	}
	
	public int getOrigin() {
		return origin;
	}
	
	public void changeOrigin(int vmId) {
		origin = vmId;
	}

	public int getDestination() {
		return destination;
	}

	public void changeDestination(int vmId) {
		destination = vmId;
	}
	
	public long getSize() {
		return size;
	}

	public Request getPayload() {
		return payload;
	}
	
	public int getFlowId() {
		return flowId;
	}
	
	public String toString() {
		return "PKG:"+origin + "->" + destination + " - " + payload.toString();
	}
	
	@Override
	public int hashCode() {
		return Long.hashCode(id);
	}

	public void setPacketStartTime(double time) {
		this.startTime = time;
		
		if(pktEncapsulated != null && pktEncapsulated.getStartTime() == -1) {
			pktEncapsulated.setPacketStartTime(time);
		}
	}
	
	public void setPacketFinishTime(double time) {
		this.finishTime = time;
		
		if(pktEncapsulated != null) {
			pktEncapsulated.setPacketFinishTime(time);
		}
	}
	
	public void setPacketFailedTime(double currentTime) {
		setPacketFinishTime(currentTime);
		getPayload().setFailedTime(currentTime);
		if(pktEncapsulated != null) {
			pktEncapsulated.setPacketFailedTime(currentTime);
		}
	}
	
	public double getStartTime() {
		//if(pktEncapsulated != null) {
		//	return pktEncapsulated.getStartTime();
		//}
		
		return this.startTime;
	}
	
	public double getFinishTime() {
		//if(pktEncapsulated != null) {
		//	return pktEncapsulated.getFinishTime();
		//}
		
		return this.finishTime;
	}
	
	public long getPacketId() {
		return this.id;
	}

	public int getDroppedTimes() {
		return droppedTimes;
	}

	public void setDroppedTimes(int droppedTimes) {
		this.droppedTimes = droppedTimes;
	}

	public long getOriginflowId() {
		return originflowId;
	}

	public void setOriginflowId(long subflowId) {
		this.originflowId = subflowId;
	}

	public int getTotalFlowNum() {
		return totalFlowNum;
	}

	public void setTotalFlowNum(int totalFlowNum) {
		this.totalFlowNum = totalFlowNum;
	}
}
