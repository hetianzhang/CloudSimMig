/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */
package org.cloudbus.cloudsim.sdn;

import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.Consts;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.monitor.MonitoringValues;
import org.cloudbus.cloudsim.sdn.policies.CloudletSchedulerMonitor;

/**
 * Extension of VM that supports to set start and terminate time of VM in VM creation request.
 * If start time and finish time is set up, specific CloudSim Event is triggered
 * in datacenter to create and terminate the VM. 
 * 
 * @author Jungmin Son
 * @author Rodrigo N. Calheiros
 * @since CloudSimSDN 1.0
 */
public class SDNVm extends Vm {
//	private SDNHost sdnhost = null;
	private double startTime;
	private double finishTime;
	private String vmName = null;
	private String middleboxType = null;
	
	//[NEW] for live migration
	private double compressRatio = Configuration.MIG_COMPRESS_RATIO;
	private double dirtyFactor = 0.001;
	private double dirtyFactorMin = 0.0005;
	private double dirtyFactorMax = 0.0015;
	private double dirtyRate; // same unit as ram Gbits/s
	private long remainedDirtyPg;
	private double migrationStartTime;
	private double migrationUpdateTime = 0;
	private double preMigTime = Configuration.MIG_PRE_MIGRATION_TIME;
	private double postMigTime = Configuration.MIG_POST_MIGRATION_TIME;
	
	//[NEW] for SLA violation
	private double currentVio = 0.8;
	private double vioSpeed = 0.001;
	private double vioThred = 1;
	private double deadline = -1;
	
	private static int assignedVmId=0;
	
	public static int getUniqueVmId() {
		int id = SDNVm.assignedVmId;
		SDNVm.assignedVmId++;
		return id;
	}
	
	public SDNVm(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
		
		// random dirty page factor
		//this.dirtyFactor = this.dirtyFactorMin + this.dirtyFactorMax * Math.random();
		// simulate dirty memory page
		//this.dirtyRate = this.dirtyFactor * ram * 8 * compressRatio * Math.pow(10, 9);
		
		this.remainedDirtyPg = (long) (ram*8*Math.pow(10, 9)*compressRatio);
	}
	
	public SDNVm(int id, int userId, double mips, int numberOfPes, int ram,
			long bw, long size, String vmm, CloudletScheduler cloudletScheduler, double startTime, double finishTime) {
		super(id, userId, mips, numberOfPes, ram, bw, size, vmm, cloudletScheduler);
	
		this.startTime = startTime;
		this.finishTime = finishTime;
		
		// random dirty page factor
		//this.dirtyFactor = this.dirtyFactorMin + this.dirtyFactorMax * Math.random();
		// simulate dirty memory page
		//this.dirtyRate = this.dirtyFactor * ram * 8 * compressRatio * Math.pow(10, 9); //Byte to bit
		this.remainedDirtyPg = (long) (ram*Math.pow(10, 9)*compressRatio);
	}
	
	public double getStartTime() {
		return startTime;
	}
	
	public double getFinishTime() {
		return finishTime;
	}
	
//	public void setSDNHost(SDNHost host) {
//		sdnhost = host;
//	}
	
	public void setName(String name) {
		this.vmName = name;
	}
	
	public String getName() {
		return this.vmName;
	}
	
	public void setMiddleboxType(String mbType) {
		this.middleboxType = mbType;
	}
	
	public String getMiddleboxType() {
		return this.middleboxType;
	}
	
	public String toString() {
		return "VM #"+getId()+" ("+getName()+") in ("+getHost()+")";
	}
	
	@Override
	public double updateVmProcessing(double currentTime, List<Double> mipsShare) {
		double sumMips = 0;
		//TODO mipsShare Null Exception
		if(mipsShare != null) {
		for(double mips:mipsShare)
			sumMips+= mips;
		
		CloudletSchedulerMonitor cls = (CloudletSchedulerMonitor)getCloudletScheduler();
		long totalGivenPrevTime = (long)(cls.getTimeSpentPreviousMonitoredTime(currentTime) * sumMips);
		long totalProcessingPrevTime = cls.getTotalProcessingPreviousTime(currentTime, mipsShare);
		
		// Monitoring this VM
		this.increaseProcessedMIs(totalProcessingPrevTime, totalGivenPrevTime);
		
		// Monitoring the host hosting this VM
		SDNHost sdnhost = (SDNHost) getHost();
		if(sdnhost != null)
			sdnhost.increaseProcessedMIs(totalProcessingPrevTime);
		}
		return super.updateVmProcessing(currentTime, mipsShare);
	}

	public boolean isIdle() {
		CloudletSchedulerMonitor sch = (CloudletSchedulerMonitor) getCloudletScheduler();
		return sch.isVmIdle();
	}
	
	public long getTotalMips() {
		return (long) (this.getMips() * this.getNumberOfPes());
	}
	@Override
	public long getCurrentRequestedBw() {
		return getBw();
	}
	
	@Override
	public List<Double> getCurrentRequestedMips() {
		List<Double> currentRequestedMips = new ArrayList<Double>();
		for (int i = 0; i < getNumberOfPes(); i++) {
			currentRequestedMips.add(getMips());
		}
		return currentRequestedMips;
	}
	
	// For monitor
	private MonitoringValues mvCPU = new MonitoringValues(MonitoringValues.ValueType.Utilization_Percentage);
	private long monitoringProcessedMIsPerUnit = 0;
	
	private long monitoringGivenMIsPerUnit = 0;

	public void updateMonitor(double logTime, double timeUnit) {
		updateMonitorCPU(logTime, timeUnit);
		updateMonitorBW(logTime, timeUnit);
	}

	private void updateMonitorCPU(double logTime, double timeUnit) {
		//long capacity = (long) (getTotalMips() *timeUnit);
		long capacity = monitoringGivenMIsPerUnit;
		
		double utilization = 0;
		
		if(capacity != 0 ) 
			utilization = (double)monitoringProcessedMIsPerUnit / capacity / Consts.MILLION;
		
		mvCPU.add(utilization, logTime);
		monitoringProcessedMIsPerUnit = 0;
		monitoringGivenMIsPerUnit = 0;
		
		LogWriter log = LogWriter.getLogger("vm_utilization.csv");
		log.printLine(this.getName()+","+logTime+","+utilization);
	}
	public MonitoringValues getMonitoringValuesVmCPUUtilization() { 
		return mvCPU;
	}
	
	public double getMonitoredUtilizationCPU(double startTime, double endTime) {
		return getMonitoringValuesVmCPUUtilization().getAverageValue(startTime, endTime);
	}

	public void increaseProcessedMIs(long processedMIs, long totalGivenMIs) {
		this.monitoringProcessedMIsPerUnit += processedMIs;
		this.monitoringGivenMIsPerUnit += totalGivenMIs;		
	}
	
	private MonitoringValues mvBW = new MonitoringValues(MonitoringValues.ValueType.DataRate_BytesPerSecond);
	private long monitoringProcessedBytesPerUnit = 0;

	private void updateMonitorBW(double logTime, double timeUnit) {
//		long capacity = (long) (getBw() *timeUnit);
		double dataRate = (double)monitoringProcessedBytesPerUnit / timeUnit;
		
//		if(capacity != 0 ) 
//			utilization = (double)monitoringProcessedBytesPerUnit / capacity;
		
		mvBW.add(dataRate, logTime);
		monitoringProcessedBytesPerUnit = 0;
		
		LogWriter log = LogWriter.getLogger("vm_bw_utilization.csv");
		log.printLine(this.getName()+","+logTime+","+dataRate);
	}
	
	public MonitoringValues getMonitoringValuesVmBwUtilization() { 
		return mvBW;
	}
	public void increaseProcessedBytes(long processedThisRound) {
		this.monitoringProcessedBytesPerUnit += processedThisRound;
	}

	
	private ArrayList<SDNHost> migrationHistory = new ArrayList<SDNHost>(); // migration history for debugging
	public void addMigrationHistory(SDNHost host) {
		migrationHistory.add(host);
	}
	
	// Check how long this Host is overloaded (The served capacity is less than the required capacity)
	private double overloadLoggerPrevTime =0;
	private double overloadLoggerPrevScaleFactor= 1.0;
	private double overloadLoggerTotalDuration =0;
	private double overloadLoggerOverloadedDuration =0;
	private double overloadLoggerScaledOverloadedDuration =0;

	public void logOverloadLogger(double scaleFactor) {
		// scaleFactor == 1 means enough resource is served
		// scaleFactor < 1 means less resource is served (only requested * scaleFactor is served) 
		double currentTime = CloudSim.clock();
		double duration = currentTime - overloadLoggerPrevTime;
		
		if(scaleFactor > 1) {
			System.err.println("scale factor cannot be >1!");
			System.exit(1);
		}
		
		if(duration > 0) {
			if(overloadLoggerPrevScaleFactor < 1.0) {
				// Host was overloaded for the previous time period
				overloadLoggerOverloadedDuration += duration;
			}
			overloadLoggerTotalDuration += duration;
			overloadLoggerScaledOverloadedDuration += duration * overloadLoggerPrevScaleFactor;
		}				
		overloadLoggerPrevTime = currentTime;
		overloadLoggerPrevScaleFactor = scaleFactor;		
	}
	
	public double overloadLoggerGetOverloadedDuration() {
		return overloadLoggerOverloadedDuration;
	}
	public double overloadLoggerGetTotalDuration() {
		return overloadLoggerTotalDuration;
	}
	public double overloadLoggerGetScaledOverloadedDuration() {
		return overloadLoggerScaledOverloadedDuration;
	}	
	//////////////////////////////////////////////////////
		
	// For vertical scaling
	public void updatePeMips(int pe, double mips) {
		// This function changes MIPS of this VM. Proper VM scheduling must be followed to change Host settings.
		super.setNumberOfPes(pe);
		super.setMips(mips);
	}

	public long getRemainedDirtyPg() {
		return remainedDirtyPg;
	}

	public void setRemainedDirtyPg(long remainedDirtyPg) {
		this.remainedDirtyPg = remainedDirtyPg;
	}

	public double getMigrationStartTime() {
		return migrationStartTime;
	}

	public void setMigrationStartTime(double migrationStartTime) {
		this.migrationStartTime = migrationStartTime;
	}

	public double getMigrationUpdateTime() {
		return migrationUpdateTime;
	}

	public void setMigrationUpdateTime(double migrationUpdateTime) {
		this.migrationUpdateTime = migrationUpdateTime;
	}

	public double getDirtyRate() {
		return dirtyRate;
	}

	public void setDirtyRate(double dirtyRate) {
		this.dirtyRate = dirtyRate;
	}

	public double getCompressRatio() {
		return compressRatio;
	}

	public void setCompressRatio(double compresssRatio) {
		this.compressRatio = compresssRatio;
	}

	public double getCurrentVio() {
		return currentVio;
	}

	public void setCurrentVio(double currentVio) {
		this.currentVio = currentVio;
	}

	public double getVioSpeed() {
		return vioSpeed;
	}

	public void setVioSpeed(double vioSpeed) {
		this.vioSpeed = vioSpeed;
	}

	public double getVioThred() {
		return vioThred;
	}

	public void setVioThred(double vioThred) {
		this.vioThred = vioThred;
	}

	public double getDeadline() {
		return deadline;
	}

	public void setDeadline(double deadline) {
		this.deadline = deadline;
	}
	
	public void setPreMigrationTime(double preMigTime) {
		this.preMigTime = preMigTime;
	}
	
	public double getPreMigrationTime() {
		return this.preMigTime;
	}
	
	public void setPostMigrationTime(double postMigTime) {
		this.postMigTime = postMigTime;
	}
	
	public double getPostMigrationTime() {
		return this.postMigTime;
	}

	public double getDirtyFactor() {
		return dirtyFactor;
	}

	public void setDirtyFactor(double dirtyFactor) {
		this.dirtyFactor = dirtyFactor;
	}
}

