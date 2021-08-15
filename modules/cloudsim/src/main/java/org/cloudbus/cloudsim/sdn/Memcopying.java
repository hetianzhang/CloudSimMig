/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;

/**
 *  New class implements Activity for migration packet Request payload,
 *  in order to let processNextActivity in SDNDatacneter create the next
 *  round pre-copy migration packet. It contains the current required 
 *  threshold for live migration: Downtime Threshold, Deadline, and bandwidth.
 * 
 *  @author TianZhang He
 *   
 */
public class Memcopying implements Activity,Comparable<Memcopying>{
	
	//for compatibility to the old version processVMMigrate(SimEvent, boolean)
	public Map<String, Object> migrate;
	
	//identify the migration
	private int id;
	private int chId; //migration channel id
	
	//private Packet pkt = null;
	private int srcVm;
	private int srcHost;
	private int dstVm;
	private int dstHost;
	private double dirtyRate;
	
	private int migVm;
	private boolean precopy = false;
	private boolean stopandcopy = false;
	
	//start and finish time for live migration
	private double arrivalTime = -1;
	private double startTime = -1;
	private double finishTime = -1;
	private double severedTime = -1;
	
	private double estimateMigTime = -1;
	
	//Amount of Memory already copied
	private long amountMemCopied = 0;
	
	//downtime threshold Default: 0.5s
	private double downtimeThres = 0.5;
	private int iterationThres = 30;
	
	//downtime
	private int iterationNum;
	private double downtime;
	private double migTime;

	// requested Bw for new pre-copy iteration
	private double requestedBw = 0;
	
	//reserved allocated VM resource for stop and copy
	private long vmDisk;
	private int vmRAM;
	private double vmTotalMips;
	private List<Double> vmMips;
	private long vmBw;
	
	//priority based scheduling
	private int priority;
	private double slackTime;
	private double slaThreshold;
	private double slaVioSpeed;
	
	//pre/post migration overheads
	private double preMigStart;
	private double postMigEnd;
	private double preMigTime;
	private double postMigTime;
	
	//deadline
	private double deadline;
	
	//feasible score for simple heuristic sequence planning
	private double score = 0;
	
	public Memcopying(int srcVm, int dstVm, int srcHost, int dstHost, long amountMemCopied, double startTime, double currentTime) {
		this.setSrcVm(srcVm);
		this.setDstVm(dstVm);
		this.setSrcHost(srcHost);
		this.setDstHost(dstHost);
		this.amountMemCopied = amountMemCopied;
		this.startTime = startTime;
		this.severedTime = currentTime;
		this.iterationNum = 0;
		
		// random priority assignment
		int randomNum = ThreadLocalRandom.current().nextInt(0, 10 + 1);
		this.setPriority(randomNum);
		
		this.preMigTime = Configuration.MIG_PRE_MIGRATION_TIME; // by default 1 sec computing overhead for pre and post migration
		this.postMigTime = Configuration.MIG_POST_MIGRATION_TIME;
	}
	
	public long getAmountMemCopied() {
		return amountMemCopied;
	}
	
	public void setAmountMemCopied(long amountMemCopied) {
		this.amountMemCopied = amountMemCopied;
	}
	
	public double getRequestedBw() {
		return this.requestedBw;
	}
	
	public boolean setRequestedBw(double nextRoundBw) {
		if(nextRoundBw < 0) {
			return false;
		} else {
			this.requestedBw = nextRoundBw;
			return true;
		}
	}
	
	@Override
	public double getExpectedTime() {
		// TODO Auto-generated method stub
		//double migTime = this.getVmRAM()/this.requestedBw+(1-())
		return 0;
	}

	@Override
	public double getServeTime() {
		// TODO Auto-generated method stub
		return this.severedTime;
	}
	
	public void setServeTime(double currentTime) {
		this.severedTime = currentTime;
	}

	@Override
	public double getStartTime() {
		// TODO Auto-generated method stub
		return this.startTime;
	}

	@Override
	public double getFinishTime() {
		// TODO Auto-generated method stub
		return this.finishTime;
	}

	@Override
	public void setStartTime(double currentTime) {
		// TODO Auto-generated method stub
		this.startTime = currentTime;
	}

	@Override
	public void setFinishTime(double currentTime) {
		// TODO Auto-generated method stub
		this.finishTime = currentTime;
	}

	@Override
	public void setFailedTime(double currentTime) {
		// TODO Auto-generated method stub
		
	}

	public int getSrcVm() {
		return srcVm;
	}

	public void setSrcVm(int srcVm) {
		this.srcVm = srcVm;
	}

	public int getSrcHost() {
		return srcHost;
	}

	public void setSrcHost(int srcHost) {
		this.srcHost = srcHost;
	}

	public int getDstVm() {
		return dstVm;
	}

	public void setDstVm(int dstVm) {
		this.dstVm = dstVm;
	}

	public int getDstHost() {
		return dstHost;
	}

	public void setDstHost(int dstHost) {
		this.dstHost = dstHost;
	}

	public double getDowntime() {
		return downtime;
	}

	public void setDowntime(double downtime) {
		this.downtime = downtime;
	}

	public int getMigVm() {
		return migVm;
	}

	public void setMigVm(int migVm) {
		this.migVm = migVm;
	}

	public boolean isPrecopy() {
		return precopy;
	}

	public void setPrecopy(boolean precopy) {
		this.precopy = precopy;
	}

	public boolean isStopandcopy() {
		return stopandcopy;
	}

	public void setStopandcopy(boolean stopandcopy) {
		this.stopandcopy = stopandcopy;
	}

	public List<Double> getVmMips() {
		return vmMips;
	}

	public void setVmMips(List<Double> vmMips) {
		this.vmMips = vmMips;
	}

	public long getVmBw() {
		return vmBw;
	}

	public void setVmBw(long vmBw) {
		this.vmBw = vmBw;
	}

	public double getMigTime() {
		return migTime;
	}

	public void setMigTime(double migTime) {
		this.migTime = migTime;
	}

	public double getDowntimeThres() {
		return downtimeThres;
	}

	public void setDowntimeThres(double downtimeThres) {
		this.downtimeThres = downtimeThres;
	}

	public double getScore() {
		return score;
	}

	public void setScore(double score) {
		this.score = score;
	}

	public long getVmDisk() {
		return vmDisk;
	}

	public void setVmDisk(long vmDisk) {
		this.vmDisk = vmDisk;
	}

	public int getVmRAM() {
		return vmRAM;
	}

	public void setVmRAM(int vmRAM) {
		this.vmRAM = vmRAM;
	}
	public boolean setId(int id) {
		if(id>0) {this.id = id; return true;}
		else return false;
	}
	public int getId() {
		return this.id;
	}
	
	public boolean equals(Object obj) {
	    if (obj == null) return false;
	    if (obj == this) return true;
	    if (!(obj instanceof Memcopying)) return false;
	    Memcopying o = (Memcopying) obj;
	    return o.id == this.id;
	}

	public int getChId() {
		return chId;
	}

	public void setChId(int chId) {
		this.chId = chId;
	}

	public double getVmTotalMips() {
		return vmTotalMips;
	}

	public void setVmTotalMips(double vmTotalMips) {
		this.vmTotalMips = vmTotalMips;
	}

	public int getIterationNum() {
		return iterationNum;
	}

	public void setIterationNum(int iterationNum) {
		this.iterationNum = iterationNum;
	}
	
	public void increaseIterationNum() {
		this.iterationNum++;
	}

	public int getIterationThres() {
		return iterationThres;
	}

	public void setIterationThres(int iterationThres) {
		this.iterationThres = iterationThres;
	}

	public double getEstimateMigTime() {
		return estimateMigTime;
	}

	public void setEstimateMigTime(double estimateMigTime) {
		this.estimateMigTime = estimateMigTime;
	}

	public int getPriority() {
		return priority;
	}

	public void setPriority(int priority) {
		this.priority = priority;
	}

	public double getSlackTime() {
		return slackTime;
	}

	public void setSlackTime(double slackTime) {
		this.slackTime = slackTime;
	}

	public double getDirtyRate() {
		return dirtyRate;
	}

	public void setDirtyRate(double dirtyRate) {
		this.dirtyRate = dirtyRate;
	}

	public double getSlaThreshold() {
		return slaThreshold;
	}

	public void setSlaThreshold(double slaThreshold) {
		this.slaThreshold = slaThreshold;
	}

	public double getSlaVioSpeed() {
		return slaVioSpeed;
	}

	public void setSlaVioSpeed(double slaVioSpeed) {
		this.slaVioSpeed = slaVioSpeed;
	}

	public double getPreMigTime() {
		return preMigTime;
	}

	public void setPreMigTime(double preMigTime) {
		this.preMigTime = preMigTime;
	}

	public double getPostMigTime() {
		return postMigTime;
	}

	public void setPostMigTime(double postMigTime) {
		this.postMigTime = postMigTime;
	}

	public double getDeadline() {
		return deadline;
	}

	public void setDeadline(double deadline) {
		this.deadline = deadline;
	}

	@Override
	public int compareTo(Memcopying arg0) {
		if(this.getScore()>arg0.getScore())
			return 1;
		if(this.getScore()<arg0.getScore())
			return -1;
		else
			return 0;
	}

	public double getPreMigStart() {
		return preMigStart;
	}

	public void setPreMigStart(double preMigStart) {
		this.preMigStart = preMigStart;
	}

	public double getPostMigEnd() {
		return postMigEnd;
	}

	public void setPostMigEnd(double postMigEnd) {
		this.postMigEnd = postMigEnd;
	}

	public double getArrivalTime() {
		return arrivalTime;
	}

	public void setArrivalTime(double arrivalTime) {
		this.arrivalTime = arrivalTime;
	}
}
