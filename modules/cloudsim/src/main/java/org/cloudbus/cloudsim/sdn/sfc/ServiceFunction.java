/*
 * Title:        CloudSimSFC
 * Description:  SFC extension for CloudSimSDN
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2018, The University of Melbourne, Australia
 */


package org.cloudbus.cloudsim.sdn.sfc;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.sdn.SDNVm;

/**
 * ServiceFunction is a class to implement a VNF. It extends SDNVm including ServiceFunctionType. 
 *
 * @author Jungmin Jay Son
 * @since CloudSimSDN 3.0
 */

public class ServiceFunction extends SDNVm {
	private final double initMips;
	private final int initNumberOfPes; 
	private long mipOper=0;

	public ServiceFunction(int id, int userId, double mips, int numberOfPes, int ram, long bw, long size, String vmm,
			CloudletScheduler cloudletScheduler, double startTime, double finishTime) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler, startTime, finishTime);
		initMips = mips;
		initNumberOfPes = numberOfPes;
	}

	public void setMIperOperation(long mipOperation) {
		this.mipOper = mipOperation; // MI per operation.
	}

	public long getMIperOperation() {
		return this.mipOper;
	}
	
	public double getInitialMips() {
		return initMips;
	}
	public int getInitialNumberOfPes() {
		return initNumberOfPes;
	}
}
