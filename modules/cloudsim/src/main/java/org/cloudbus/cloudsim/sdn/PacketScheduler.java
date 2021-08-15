package org.cloudbus.cloudsim.sdn;

import java.util.LinkedList;

public interface PacketScheduler {

	public double addTransmission(Transmission transmission);
	public void removeTransmission(Transmission transmission);

	/* This function processes network transmission for the past time period.
	 * Return: True if any transmission is completed in this round.
	 *         False if no transmission is completed in this round.
	 */
	public long updatePacketProcessing();

	/* This function returns the earliest nest finish time of any transmission. */
	public double nextFinishTime();
	
	public int getInTransmissionNum();
	
	public void setTimeOut(double timeoutSecond);
	public LinkedList<Transmission> getTimedOutTransmission();
	public void resetTimedOutTransmission();

	public LinkedList<Transmission> getCompletedTransmission();
	public void resetCompletedTransmission();
	
	public double estimateFinishTime(Transmission t);
}
