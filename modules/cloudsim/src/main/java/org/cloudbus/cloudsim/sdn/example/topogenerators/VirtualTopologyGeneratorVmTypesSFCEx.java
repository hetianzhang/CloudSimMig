package org.cloudbus.cloudsim.sdn.example.topogenerators;

public class VirtualTopologyGeneratorVmTypesSFCEx extends VirtualTopologyGeneratorVmTypesSFC{
	//add SLA violation speed or deadline for SLA-related migration scheduling
	
	/*
	 *
	 *flavor = [(64,12,120,0.02,0),(64,12,120,0.01,0),
          (16,8,60,0.05,0),(16,8,60,0.02,0),
          (8,4,40,0.1,0),(8,4,40,0.02,0),
          (4,2,10,0.12,0),(4,2,10,0.02,0),
          (2,1,2,0.12,0),(2,1,2,0.02,0),
          (1,1,1,0.12,0),(1,1,1,0.02,0)]
	 */
	// flavour: xlarge, large, medium, small, tiny, micro
	int [] falvor = new int[] {0,1,5};
	double [] mem = new double[] {64,16,8,4,2,1}; //GB Byte to bits multiple 8
	int [] pe = new int[] {12,8,4,2,1,1}; //cpu core
	double [] disk = new double[] {120,60,20,10,2,1}; //disk GB
	double [] dirty_r_h = new double[] {0.02,0.05,0.1,0.12,0.12,0.12};//dirty_page per sec = dirty_r * mem
	double [] dirty_r_l = new double[] {0.01,0.02,0.02,0.02,0.02,0.02};
	double [] deadline_h = new double[] {400,0,0,0,0};//deadline for sla-related and non-sla-realted migration
	double [] deadline_l = new double[] {100,80,0,0,400}; //deadline for low dirty_rate vm
	final Long[] linkBW = new Long[] {(long) ((long)1*Math.pow(10, 9)), (long) ((long)0.2*Math.pow(10, 9))};	
	
	public void generateVmGroupExperiment() {
	}
}
