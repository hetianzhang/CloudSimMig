package org.cloudbus.cloudsim.sdn.vmallocation.autoscale;

import java.util.List;

import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNVm;

public class AutoScalePolicy {

	public void startAutoScale(List<SDNVm> allVms) {
		for(SDNVm vm:allVms) {
			if(isVmOverloaded(vm)) {
				scaleUp(vm);
			}
			else if(isVmUnderloaded(vm)) {
				scaleDown(vm);
			}
		}
	}

	private void scaleUp(SDNVm vm) {
		// TODO Auto-generated method stub
		
	}
	
	private void scaleDown(SDNVm vm) {
		// TODO Auto-generated method stub
		
	}

	public boolean isVmOverloaded(SDNVm vm) {
		double currentTime = CloudSim.clock();
		double startTime = currentTime - Configuration.migrationTimeInterval;
		if( vm.getMonitoredUtilizationCPU(startTime, currentTime) > Configuration.OVERLOAD_THRESHOLD )
			return true;

		return false;
	}
	
	public boolean isVmUnderloaded(SDNVm vm) {
		double currentTime = CloudSim.clock();
		double startTime = currentTime - Configuration.migrationTimeInterval;
		if( vm.getMonitoredUtilizationCPU(startTime, currentTime) < Configuration.UNDERLOAD_THRESHOLD_VM )
			return true;

		return false;
	}
	
}
