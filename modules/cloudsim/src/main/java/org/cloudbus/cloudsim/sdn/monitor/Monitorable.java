package org.cloudbus.cloudsim.sdn.monitor;

public interface Monitorable {
	public void accumulateMonitoringData(long increaseAmount);	//
	public void updateMonitoringPoint(double monitoringInterval);
	
	public MonitoringValues getMonitoringValuesRaw();
	public double getMonitoringValuesAverage(double startTime, double endTime);
}
