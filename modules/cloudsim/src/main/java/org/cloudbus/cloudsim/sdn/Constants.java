/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

/**
 * Constant variables to use
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class Constants {
	
	private static final int SDN_BASE = 89000000;
	
	public static final int SDN_PACKET_COMPLETE = SDN_BASE + 1;	// Deliver Cloudlet (computing workload) to VM
	public static final int SDN_PACKET_FAILED = SDN_BASE + 2;	// Deliver Cloudlet (computing workload) to VM
	public static final int SDN_INTERNAL_PACKET_PROCESS = SDN_BASE + 3; 
	public static final int SDN_VM_CREATE_IN_GROUP = SDN_BASE + 4;
	public static final int SDN_VM_CREATE_IN_GROUP_ACK = SDN_BASE + 5;
	public static final int SDN_VM_CREATE_DYNAMIC = SDN_BASE + 6;
	public static final int SDN_INTERNAL_CHANNEL_PROCESS = SDN_BASE + 7;

	public static final int REQUEST_SUBMIT = SDN_BASE + 10;
	public static final int REQUEST_COMPLETED = SDN_BASE + 11;
	public static final int REQUEST_OFFER_MORE = SDN_BASE + 12;
	public static final int REQUEST_FAILED = SDN_BASE + 13;
	
	public static final int APPLICATION_SUBMIT = SDN_BASE + 20;	// Broker -> Datacenter.
	public static final int APPLICATION_SUBMIT_ACK = SDN_BASE + 21;

	public static final int MONITOR_UPDATE_UTILIZATION = SDN_BASE + 25;
	
	public static final int SDN_VM_MIGRATION_START = SDN_BASE + 26; // allocate resources on dstHost
	public static final int SDN_VM_MIGRATION_UPDATE = SDN_BASE + 27; // Memory copy
	public static final int SDN_VM_PAUSE = SDN_BASE + 28; // Stop-and-copy phase
	public static final int SDN_VM_RESUME = SDN_BASE + 29; // resume the VM
	public static final int SDN_VM_MIGRATION_PRE = SDN_BASE + 30; //
	public static final int SDN_VM_MIGRATION_POST = SDN_BASE + 31;
//	public static final int CHECK_MIGRATION = SDN_BASE + 26;
	public static final int SDN_VM_MIGRATION_SCHEDULE = SDN_BASE + 32; //processing of on-line migration scheduler
	public static final int SDN_VM_MIGRATION_FAIL = SDN_BASE + 33; //migration failed
	public static final int SDN_VM_MIGRATION_EDGE_PLAN = SDN_BASE + 34; //event for edge migration plan and scheduling
	public static final int SDN_VM_MIGRATION_EDGE_FINISH = SDN_BASE + 35; //start the next arrival migration that is waiting
	public static final int SDN_VM_MIGRATION_EDGE_WAIT = SDN_BASE + 36; //put arrival migration in waiting list for scheduling
	
	public static final int SDN_PACKET_DROPPED = SDN_BASE + 37; //[NEW] Re-send the packet when dropped
	public static final int SDN_PACKET_SUBFLOW_COMPLETE = SDN_BASE + 38; //[NEW] processing the subflow completion
}
