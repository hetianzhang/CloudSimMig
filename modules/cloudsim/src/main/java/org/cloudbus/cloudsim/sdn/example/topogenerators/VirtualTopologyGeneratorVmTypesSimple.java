/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.io.BufferedReader;

/**
 * This class generate the virtual topology and simple workload for sequential and parallel migration experiments
 * the sequence of VM is shuffled
 * dummy workload generator based on Gaussian and Poisson distribution.
 * @author TianZhang He
 * @since CloudSimSDN v2.0
 * 
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.cloudbus.cloudsim.Cloudlet;
import org.cloudbus.cloudsim.UtilizationModel;
import org.cloudbus.cloudsim.UtilizationModelNull;
import org.cloudbus.cloudsim.UtilizationModelPlanetLabInMemory;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGeneratorSFC.HostSpec;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;


public class VirtualTopologyGeneratorVmTypesSimple extends VirtualTopologyGenerator{
	public static void main(String[] argv) {
		//VirtualTopologyGeneratorVmTypesSimple vmGenerator = new VirtualTopologyGeneratorVmTypesSimple();
		//vmGenerator.generateSeqParTopology("seqpar.virtual4-2000.json");
		//vmGenerator.generateSeqParTopologyStarConnection("seqpar.virtual4-500-1-49-star.json");
		//vmGenerator.generateStarWorkload("star200.csv",50);
		//vmGenerator.generateStarWorkloadEach("star200.csv",50);
		//vmGenerator.generateDummyWorkload("whatever.csv", 10);
		//vmGenerator.generatePaperMotivationVirtualTopo("paper.virtual.json");
		//vmGenerator.generatePaperWorkloadPacket("paper.csv",1000000000L);
		//vmGenerator.generateVmHostMapping("paper-mapping.json");
		//vmGenerator.generateExperimentVirtualTopo("exp-startoslave-sfc-10-deadline.json","exp.csv");
		//input folder of planetlab data
		/*[TODO]: 
		 * 1. reading the output file of planetlab experiment for mapping json file similar to the generateVmHostMapping Function
		 * 2. the migration mapping for every interval can also based on it.
		 * 3. check the interval updating configuration
		 */
		String planetLabFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\planetLab\\";
		String planetLabDataFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-minimize-peak-temperature-algorithm-master\\examples\\workload\\planetlab\\20110303";
		String workloadFileBase = "workload.csv"; //csv file for workload assignment attime(arrival time), vm0, link.vm0, workload0
		Configuration.virtualTopoVmRandomOrder = false;
		//vmGenerator.generatePowerExperimentVirtualTopo(planetLabFolder+"planetlab.virtual-pe2.json");
		//vmGenerator.generatePowerExperimentWorkload(planetLabDataFolder, planetLabFolder+workloadFileBase);
		String placementFileBase = "C:\\Users\\tianzhangh\\Documents\\cloudsim-minimize-peak-temperature-algorithm-master\\output\\migration\\";
		String placementFile = "20110303_camig_mmt_1.2VmPlacement.csv";
		String mappingFileName = "vmHostMapping.json";
		
		/*try {
			vmGenerator.generatePowerExperimentMapping(placementFileBase+placementFile, planetLabFolder+mappingFileName);
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}*/
		
		
		//generate depmig experiment for optimal, camig, hosthits, iaware, ffd, and sandpipper
		String depmigalg = "ffd";
		int multi = 4;
		int vmTotalDataNum = 10;
		//String depmigFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\depmig-"+depmigalg+"\\"+String.valueOf(multi)+"\\";
		//String depmigFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\depmig-"+depmigalg+"-mem\\"+String.valueOf(multi)+"\\";
		String depmigFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\depmig-random-vm\\";
		String depmigVirtualTopo = "depmig.virtual.json";
		String depmigmappingFileName = "vmHostMapping.json";
		String depmigworkloadFileBase = "workload.csv";
		
		String vmDataFolder = "C:\\Users\\tianzhangh\\Documents\\iter-max-indep\\random-mem-data\\";
		for(int i =0; i< vmTotalDataNum; i++) {
			String vmDataFile = "mem_data"+String.valueOf(i)+".csv";
			depmigFolder = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\depmig-random-vm\\";
			depmigFolder = depmigFolder + "data"+String.valueOf(i)+"\\";
			for(int j =1; j<5; j++) {
				VirtualTopologyGeneratorVmTypesSimple vmGenerator = new VirtualTopologyGeneratorVmTypesSimple();
				vmGenerator.generateLoadBalanceExperimentVirtualTopo(vmDataFolder+vmDataFile,depmigFolder+ String.valueOf(j)+"\\"+depmigVirtualTopo, j);	
				vmGenerator.generateLoadBalanceExperimentWorkload(depmigFolder+ String.valueOf(j)+"\\"+depmigworkloadFileBase, j);
				try {
					vmGenerator.generateLoadBalanceExperimentMapping(depmigFolder+ String.valueOf(j)+"\\"+depmigmappingFileName, j);
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
		}
		
		
		
		
	}
	
	public static int vmCount;
	enum Flavour{
		XLARGE,
		LARGE,
		MEDIUM,
		SMALL,
		TINY,
		MICRO,
		XLARGE_BUSY,
		LARGE_BUSY,
		MEDIUM_BUSY,
		SMALL_BUSY,
		TINY_BUSY,
		MICRO_BUSY,
		CONTAINER_1GB,
		CONTAINER_4GB,
		EDGE_USER
	}
	
	public VMSpec createVM(Flavour f, double startTime, double endTime) {
		String name = f.name() +"-"+ String.valueOf(vmCount);
		int pes =12;
		long vmSize = 1000;
		long mips=10000;
		int vmRam = 256;
		long vmBW=1500000L;
		
		switch(f) {
		case XLARGE:
			vmRam = 64;
			pes =12;
			mips=10000;
			vmSize = 120;
			//vmBW=100000;
			break;
		case LARGE:
			vmRam = 16;
			pes =8;
			mips=10000;
			vmSize = 60;
			//vmBW=100000;
			break;
		case MEDIUM:
			vmRam = 8;
			pes =4;
			mips=10000;
			vmSize = 20;
			//vmBW=100000;
			break;
		case SMALL:
			vmRam = 4;
			pes =2;
			mips=10000;
			vmSize = 10;
			//vmBW=100000;
			break;
		case TINY:
			vmRam = 2;
			pes =1;
			mips=10000;
			vmSize = 2;
			//vmBW=100000;
			break;
		case MICRO:
			vmRam = 1;
			pes =1;
			mips=10000;
			vmSize = 1;
			//vmBW=100000;
			break;
		}
		
		
		VMSpec vm = addVM(name, pes, mips, vmRam, vmSize, vmBW, startTime, endTime); //add VM to the ArrayList
		return vm;
	}
	
	public VMSpec createVM(String name, Flavour f, double startTime, double endTime) {

		int pes =12;
		long vmSize = 1000;
		long mips=10000;
		int vmRam = 256;
		long vmBW=1500000L;
		double dirty_rate_factor = 0;
		
		switch(f) {
		case XLARGE:
		case XLARGE_BUSY:
			vmRam = 64;
			pes =12;
			mips=10000;
			vmSize = 120;
			dirty_rate_factor = 0.01;
			//vmBW=100000;
			break;
		case LARGE:
		case LARGE_BUSY:
			vmRam = 16;
			pes =8;
			mips=10000;
			vmSize = 60;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		case MEDIUM:
		case MEDIUM_BUSY:
			vmRam = 8;
			pes =4;
			mips=10000;
			vmSize = 20;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		case SMALL:
		case SMALL_BUSY:
			vmRam = 4;
			pes =2;
			mips=10000;
			vmSize = 10;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		case TINY:
		case TINY_BUSY:
			vmRam = 2;
			pes =1;
			mips=10000;
			vmSize = 2;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		case MICRO:
		case MICRO_BUSY:
			vmRam = 1;
			pes =1;
			mips=10000;
			vmSize = 1;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		}
		
		
		//for dirty page rate factor
		switch(f) {
		case XLARGE_BUSY:
			dirty_rate_factor = 0.02;
			break;
		case LARGE_BUSY:
			dirty_rate_factor = 0.05;
			break;
		case MEDIUM_BUSY:
			dirty_rate_factor = 0.1;
			break;
		case SMALL_BUSY:
			dirty_rate_factor = 0.12;
			break;
		case TINY_BUSY:
			dirty_rate_factor = 0.12;
			break;
		case MICRO_BUSY:
			dirty_rate_factor = 0.12;
			break;
		}
		
		VMSpec vm = addVM(name, pes, mips, vmRam, vmSize, vmBW, startTime, endTime); //add VM to the ArrayList
		vm.dirty_rate = dirty_rate_factor;
		return vm;
	}
	
	private final static int PLACES = 10;
	
	private static double round(double value) {
	    int factor = PLACES;
	    long tmp = Math.round(value * factor);
	    return (double) tmp / factor;
	}
	
	static class TimeGen {
		boolean isRandom;
		double setTime;
		private Random rand = null;
		private double startrange, endrange;
		
		private double _prev_start = 0;
		double maxEndTime;
		
		TimeGen(double time) {
			isRandom=false;
			setTime = time;
			maxEndTime = -1;
		}
		public TimeGen(double time, boolean isrand, Random random) {
			this.setTime = time;
			this.isRandom = isrand;
			startrange=endrange=1;
			maxEndTime = -1;
			if(isrand)
				rand = random;
		}
		public TimeGen(double time, boolean isrand, Random random, double startScale, double endScale) {
			this(time, isrand, random);
			this.startrange = startScale;			
			this.endrange = endScale;
		}
		double getStartTime() {
			if(isRandom) {
				_prev_start = round(setTime + rand.nextDouble()*startrange);
				return _prev_start;
			}
			return setTime;
		}
		double getEndTime() {
			if(isRandom) {
				double endTime= round(_prev_start + rand.nextDouble()*endrange);
				if(maxEndTime != -1) {
					endTime = Double.min(endTime, maxEndTime);
				}
				return endTime;
			}
			return setTime;
		}
		public void setMaxEndTime(double d) {
			this.maxEndTime = d;
		}
	}
	
	private static double getGaussian(double aMean, double aVariance){
		Random fRandom = new Random();
	    return aMean + fRandom.nextGaussian() * aVariance;
	  }

	
	private static int getPoissonRandom(double mean) {
	    Random r = new Random();
	    double L = Math.exp(-mean);
	    int k = 0;
	    double p = 1.0;
	    do {
	        p = p * r.nextDouble();
	        k++;
	    } while (p > L);
	    return k - 1;
	}

	
	private List<String[]> generateWorkloadPoissonArrival(String name, int snumber, int number, int start, int end, double rate, double workload) {
		int round = (int) Math.ceil((end-start)*rate);
		List<String[]> dataLines = new ArrayList<>();
		for(int i=0;i<round;i++) {
			double attime = 0.1;
			attime = (i-1)*(double)(1/rate)+(double)getPoissonRandom((1/rate)*100.0)/100.0;
			while(attime<0) {attime = (i-1)*(double)(1/rate)+(double)getPoissonRandom((double)(1/rate)*100.0)/100.0;}
			//System.out.println(attime);
			
			for(int j =0; j<number; j++) {
				String vmName = name +"-"+ Integer.toString(snumber+j);
				//double workloadVar = getGaussian(workload, 500);
				dataLines.add(new String[] {Double.toString(attime), vmName, "0", Integer.toString((int)workload)+"\n"});
			}
			
		}
		return dataLines;
	}
	
	public void generateDummyWorkload(String csvFileName, int num) {
		//generate workload(cloudlet) based on normal distribution mean and variance for a certain period
		//attime, name, packet size, workload size, ...
		// start time, end time, arrival per second, average MIs
		//List<Integer> arrivalSpeed = Arrays.asList(2*12,2*8,2*4,2*2,2,2);
		List<Integer> arrivalSpeed = Arrays.asList(2*8,2*4,2*4,2*2,2,2);
		int[] flavourNumList = {0,0,25,25,875,875};
		int[] flavourStartNum = {0,0,0,25,50,925};
		String[] fname = csvFileName.split("\\.",2);
		for(String n:fname) {
			System.out.println(n);
		}
		for(int x=2;x<Flavour.values().length;x++) {
			Flavour f = Flavour.values()[x];
			List<String[]> dataLines = this.generateWorkloadPoissonArrival(f.name(), flavourStartNum[x], flavourNumList[x], 0, 1000, arrivalSpeed.get(x), 3000);
			try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+f.name()+".csv"))) {
				for(int i=0; i<dataLines.size();i++) {
					String objectsCommaSeparated = String.join(",", dataLines.get(i));
					//System.out.print(objectsCommaSeparated);
					writer.print(objectsCommaSeparated);
				}			
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	
	int podNum = 5;
	int slaveNum = 49;
	
	private List<String[]> generatePacketArrival(ArrayList<VMSpec> vmList, String name, int flavourIndex, int snumber, int number, int start, int end, double rate, double workload, double pSize){
		
		int[] flavourNumList = {0,0,podNum,podNum,slaveNum*podNum,slaveNum*podNum};
		

		//snumber: start number of the flavour; number: how many vms of this flavor
		int round = (int) Math.ceil((end-start)*rate);
		List<String[]> dataLines = new ArrayList<>();
		
		for(int i =0; i<round; i++) {
			double attime = 0.1;
			attime = i*(double)(1/rate)+(double)getPoissonRandom((1/rate)*100.0)/100.0;
			while(attime<0) {attime = i*(double)(1/rate)+(double)getPoissonRandom((double)(1/rate)*100.0)/100.0;}
			
			
			for(int j = 0; j<number; j++) {
				String masterName = name + "-" + Integer.toString(snumber+j);
				VMSpec master = vmList.get(snumber+j);
				int startSlave = this.getIndexNum(flavourNumList, flavourIndex+2);
				for(int n = startSlave +slaveNum*j; n < startSlave+slaveNum*(j+1); n++) {
					VMSpec slave = vmList.get(n);
					String linkName1 = getAutoLinkName(master, slave);
					String linkName2 = getAutoLinkName(slave, master);
					double pSize1 = getGaussian(pSize, pSize*0.3);
					double pSize2 = getGaussian(pSize, pSize*0.1);
					double workload1 = getGaussian(100, 100*0.2);
					double workload2 = getGaussian(workload, workload*0.2);
					dataLines.add(new String[] {Double.toString(attime), masterName, "0", Integer.toString((int)workload1),
							linkName1, slave.name, String.format("%.0f", pSize1), Integer.toString((int)workload2), linkName2, masterName,
							String.format("%.0f", pSize2), Integer.toString((int)workload1)+"\n"});
				}
			}
		}
		return dataLines;
	}
	
	public void generateStarWorkload(String csvFileName, int pod) {
		long linkBw = 100000000L;
		double linkutiRatio = 0.07;
		List<Double> arrivalSpeed = Arrays.asList(2*8.0/pod,2*2.0/pod,2*2.0/pod,2*2.0/pod,2.0,2.0);
		int totalVm = 2000;
		int podSize = pod;
		int[] flavourNumList = {0,0,podNum,podNum,slaveNum*podNum,slaveNum*podNum};
		String[] fname = csvFileName.split("\\.",2);
		
		ArrayList<VMSpec> vmList = new ArrayList<>();
		Flavour[] flist = Flavour.values();
		for(int i=0; i<6; i++) {
			Flavour f = flist[i];
			int num = flavourNumList[i];
			for(int j=0; j<num; j++) {
				TimeGen startTime = new TimeGen(-1);
				TimeGen endTime = new TimeGen(-1);
				VMSpec vm = createVM(f, startTime.getStartTime(), endTime.getEndTime());
				vmList.add(vm);
				vmCount++;
			}
		}
		
		for(int f = 2; f<flavourNumList.length-2; f++) {
			Flavour flavor = Flavour.values()[f];
			double psize = linkBw * linkutiRatio / arrivalSpeed.get(f);
			List<String[]> dataLines = this.generatePacketArrival(vmList, flavor.name(), f, this.getIndexNum(flavourNumList, f), 
					flavourNumList[f], 0, 500, 
					arrivalSpeed.get(f), 3000, 65000);
			try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+flavor.name()+".csv"))) {
				for(int i=0; i<dataLines.size();i++) {
					String objectsCommaSeparated = String.join(",", dataLines.get(i));
					//System.out.print(objectsCommaSeparated);
					writer.print(objectsCommaSeparated);
				}			
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
		
	}
	
	public void generateStarWorkloadEach(String csvFileName, int pod) {
		long linkBw = 100000000L;
		double linkutiRatio = 0.07;
		List<Double> arrivalSpeed = Arrays.asList(2*8.0/pod,1500.0/2,1500.0/2,1500.0/2,2.0,2.0);
		int totalVm = 2000;
		int podSize = pod;
		int[] flavourNumList = {0,0,podNum,podNum,slaveNum*podNum,slaveNum*podNum};
		String[] fname = csvFileName.split("\\.",2);
		
		int start = 0;
		int end = 150;
		
		List<List<VMSpec>> vmflavorList = new ArrayList<>();
		Flavour[] flist = Flavour.values();
		for(int i=0; i<6; i++) {
			ArrayList<VMSpec> vmList = new ArrayList<>();
			Flavour f = flist[i];
			int num = flavourNumList[i];
			for(int j=0; j<num; j++) {
				TimeGen startTime = new TimeGen(-1);
				TimeGen endTime = new TimeGen(-1);
				VMSpec vm = createVM(f, startTime.getStartTime(), endTime.getEndTime());
				vmList.add(vm);
				vmCount++;
			}
			vmflavorList.add(vmList);
		}
		
		for(int i=2; i< vmflavorList.size()-2; i++) {
			Flavour flavor = Flavour.values()[i];
			List<VMSpec> masterList = vmflavorList.get(i);
			List<VMSpec> slaveList = vmflavorList.get(i+2);
			int j = 0;
			for(VMSpec m:masterList) {
				List<VMSpec> subslaveList = slaveList.subList(j*slaveNum, j*slaveNum+slaveNum);
				List<String[]> dataLines = this.generateStarPacket(m, subslaveList, start, end, arrivalSpeed.get(i), 3000, 65000*2);
				try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+flavor.name()+ "-" +m.name+".csv"))) {
					for(int n=0; n<dataLines.size();n++) {
						String objectsCommaSeparated = String.join(",", dataLines.get(n));
						writer.print(objectsCommaSeparated);
					}
					writer.close();
					j++;
				} catch (FileNotFoundException e) {
					e.printStackTrace();
				}
				
			}
			
		}
		
	}
	
	private List<String[]> generateStarPacket(VMSpec master, List<VMSpec> slaveList, int start, int end, double rate, double workload, double pSize) {
		String masterName = master.name;
		int round = (int) Math.ceil((end-start)*rate);
		List<String[]> dataLines = new ArrayList<>();
		
		for(int i =0; i<round; i++) {
			double attime = 0.1;
			attime = i*(double)(1/rate)+(double)getPoissonRandom((1/rate)*100.0)/100.0;
			while(attime<0) {attime = i*(double)(1/rate)+(double)getPoissonRandom((double)(1/rate)*100.0)/100.0;}
			for(VMSpec s:slaveList) {
				String slaveName = s.name;
				String linkName1 = getAutoLinkName(master, s);
				String linkName2 = getAutoLinkName(s, master);
				double pSize1 = getGaussian(pSize, pSize*0.3);
				double pSize2 = getGaussian(pSize, pSize*0.1);
				double workload1 = getGaussian(100, 100*0.2);
				double workload2 = getGaussian(workload, workload*0.2);
				System.out.println("Generating: " + attime +" "+ masterName + "-" + slaveName);
				dataLines.add(new String[] {String.format("%.4f", attime), masterName, "0", Integer.toString((int)workload1),
						linkName1, slaveName, String.format("%.0f", pSize1), Integer.toString((int)workload2), linkName2, masterName,
						String.format("%.0f", pSize2), Integer.toString((int)workload1)+"\n"});
			}
		}
		
		return dataLines;
	}
	
	public void generateSeqParTopologyStarConnection(String jsonFileName) {
		//generate slave and master in one pod: master connects serveral slaves vms
		ArrayList<VMSpec> vmList = new ArrayList<>();
		int totalVm = 2000;
		int podSize = 200;
		int[] flavourNumList = {0,0,podNum, podNum,slaveNum*podNum,slaveNum*podNum};
		Flavour[] flist = Flavour.values();
		for(int i=0; i<flavourNumList.length; i++) {
			Flavour f = flist[i];
			int num = flavourNumList[i];
			for(int j=0; j<num; j++) {
				TimeGen startTime = new TimeGen(-1);
				TimeGen endTime = new TimeGen(-1);
				VMSpec vm = createVM(f, startTime.getStartTime(), endTime.getEndTime());
				vmList.add(vm);
				vmCount++;
			}
		}
		
		System.out.println("totalVm: "+ vmCount);
		
		//add links
		long linkBw = 100000000L; //100Mbps
		int start = this.getIndexNum(flavourNumList, 2+2);
		for(int i =2; i< flavourNumList.length-2; i++) {
			int vmIndex = this.getIndexNum(flavourNumList, i);
			for(int j = vmIndex; j < vmIndex+flavourNumList[i]; j++) {
				VMSpec master = vmList.get(j);
				for(int n = start+slaveNum*j; n< start + slaveNum*(j+1); n++) {
					VMSpec slave = vmList.get(n);
					this.addLinkAutoNameBoth(master, slave, linkBw);
					//add link auto create default link without required bw, and autoname with bw
				}
			}
		}
		this.wrtieJSON(jsonFileName);
	}
	
	private int getIndexNum(int[] flavourNumList, int flavorIndex) {
		int total=0;
		if(flavorIndex < flavourNumList.length)
		for(int i =0; i< flavorIndex; i++) {
			total += flavourNumList[i];
		}
		return total;
	}
	
	public void generateSeqParTopologyFullConnection(String jsonFileName) {
		//connect the vms of one group as a mesh network
		
	}
	
	
	public void generateSeqParTopology(String jsonFileName) {
		int n =10;
		int[] flavourNumList = {0,0,25,25,875,875};
		Flavour[] flist = Flavour.values();
		for(int i=0; i<6; i++) {
			Flavour f = flist[i];
			int num = flavourNumList[i];
			for(int j=0; j<num; j++) {
				TimeGen startTime = new TimeGen(-1);
				TimeGen endTime = new TimeGen(-1);
				VMSpec vm = createVM(f, startTime.getStartTime(), endTime.getEndTime());
				vmCount++;
			}
			
		}
		
		wrtieJSON(jsonFileName);
	}
	
	public void generatePaperMotivationVirtualTopo(String jsonFileName) {
		List<String> g1_name = Arrays.asList("v11","v12","v13","v14");
		List<String> g2_name = Arrays.asList("v21","v22","v23");
		List<String> micro_name = Arrays.asList("v31","v41","v51","v61");
		
		
		List<Flavour> g1_flavor = Arrays.asList(Flavour.LARGE,Flavour.XLARGE,Flavour.XLARGE,Flavour.XLARGE);
		List<Flavour> g2_flavor = Arrays.asList(Flavour.XLARGE,Flavour.LARGE,Flavour.LARGE);
		List<Flavour> micro_flavor = Arrays.asList(Flavour.MICRO,Flavour.MICRO,Flavour.MICRO,Flavour.MICRO);
		
		List<Double> g1_dirtyRate = Arrays.asList(0.05,0.15,0.15,0.01);
		List<Double> g2_dirtyRate = Arrays.asList(0.02,0.05,0.12);
		List<Double> micro_dirtyRate = Arrays.asList(0.02,0.02,0.02,0.02);
		
		// deadline = 0 if there is no migration
		List<Double> g1_deadline = Arrays.asList(80.0,0.0,0.0,100.0);
		List<Double> g2_deadline = Arrays.asList(400.0,80.0,0.0);
		List<Double> micro_deadline = Arrays.asList(400.0,400.0,400.0,400.0);
		
		Map<String,VMSpec> g1 = new HashMap<>();
		Map<String,VMSpec> g2 = new HashMap<>();
		
		for(int i =0; i<g1_name.size(); i++) {
			VMSpec vm = this.createVM(g1_name.get(i),g1_flavor.get(i), -1, -1);
			vm.dirty_rate = g1_dirtyRate.get(i);
			vm.mig_deadline = g1_deadline.get(i);
			g1.put(g1_name.get(i), vm);
		}
		for(int i =0; i<g2_name.size(); i++) {
			VMSpec vm = this.createVM(g2_name.get(i),g2_flavor.get(i), -1, -1);
			vm.dirty_rate = g2_dirtyRate.get(i);
			vm.mig_deadline = g2_deadline.get(i);
			g2.put(g2_name.get(i), vm);
		}
		for(int i =0; i<micro_name.size(); i++) {
			VMSpec vm = this.createVM(micro_name.get(i),micro_flavor.get(i), -1, -1);
			vm.dirty_rate = micro_dirtyRate.get(i);
			vm.mig_deadline = micro_deadline.get(i);
		}
		
		//add link
		long bw = 1000000000L;
		this.addLinkAutoName(g1.get("v11"), g1.get("v12"), bw);
		this.addLinkAutoName(g1.get("v11"), g1.get("v13"), bw);
		this.addLinkAutoName(g1.get("v12"), g1.get("v14"), bw);
		this.addLinkAutoName(g1.get("v13"), g1.get("v14"), bw);
		
		bw = bw/5;
		this.addLinkAutoNameBoth(g2.get("v21"), g2.get("v22"), bw);
		this.addLinkAutoNameBoth(g2.get("v22"), g2.get("v23"), bw);
		
		this.wrtieJSON(jsonFileName);
	}
	
	private List<String[]> generatePacket(VMSpec src, VMSpec dst, double workload, Long bw, double rate, int start, int end){
		List<String[]> dataLines = new ArrayList<>();
		Double pSize = bw/rate;
		//Double pSize = 5000.0;
		int round = (int) Math.ceil((end-start)*rate);
		
		for(int i =0; i<round; i++) {
			double attime = 0.1;
			attime = i*(double)(1/rate)+(double)getPoissonRandom((1/rate)*100.0)/100.0;
			while(attime<0) {attime = i*(double)(1/rate)+(double)getPoissonRandom((double)(1/rate)*100.0)/100.0;}
			String linkName = getAutoLinkName(src, dst);
			//double pSize1 = getGaussian(pSize, pSize*0.1);
			double pSize1 = pSize;
			double workload1 = getGaussian(100, 100*0.2);
			double workload2 = getGaussian(workload, workload*0.2);
			System.out.println("Generating: " + attime +" "+ src.name + "-" + dst.name);
			dataLines.add(new String[] {String.format("%.4f", attime), src.name, Integer.toString(0), 
					String.format("%.0f", workload1), linkName,
					dst.name,
					String.format("%.0f", pSize1), String.format("%.0f", workload2)+"\n"
					});
		}
		return dataLines;
	}
	
	
	private List<String[]> generatePacketBoth(VMSpec src, VMSpec dst, long bw, double rate, int start, int end){
		List<String[]> dataLines = new ArrayList<>();
		Long pSize = bw/Double.doubleToLongBits(rate);
		
		return dataLines;
	}
	
	public void generateAarnetWorkload(String csvFileName, Long bw) {
		String[] fname = csvFileName.split("\\.",2);
		
		int hostNum = 19;
		for(int i =0; i< hostNum; i++) {
			
		}
	}
	
	public void generatePaperWorkloadPacket(String csvFileName, Long bw) {
		String[] fname = csvFileName.split("\\.",2);
		
		List<Long> bwList = Arrays.asList(bw,bw/2,0L);
		
		List<String> g1_name = Arrays.asList("v11","v12","v13","v14");
		List<String> g2_name = Arrays.asList("v21","v22","v23");
		List<String> micro_name = Arrays.asList("v31","v41","v51","v61");
		
		
		List<Flavour> g1_flavor = Arrays.asList(Flavour.LARGE,Flavour.XLARGE,Flavour.XLARGE,Flavour.XLARGE);
		List<Flavour> g2_flavor = Arrays.asList(Flavour.XLARGE,Flavour.LARGE,Flavour.LARGE);
		List<Flavour> micro_flavor = Arrays.asList(Flavour.MICRO,Flavour.MICRO,Flavour.MICRO,Flavour.MICRO);
		
		List<Double> g1_dirtyRate = Arrays.asList(0.05,0.15,0.15,0.01);
		List<Double> g2_dirtyRate = Arrays.asList(0.02,0.05,0.12);
		List<Double> micro_dirtyRate = Arrays.asList(0.02,0.02,0.02,0.02);
		
		// deadline = 0 if there is no migration
		List<Double> g1_deadline = Arrays.asList(80.0,0.0,0.0,100.0);
		List<Double> g2_deadline = Arrays.asList(400.0,80.0,0.0);
		List<Double> micro_deadline = Arrays.asList(400.0,400.0,400.0,400.0);
		
		Map<String,VMSpec> g1 = new HashMap<>();
		Map<String,VMSpec> g2 = new HashMap<>();
		
		for(int i =0; i<g1_name.size(); i++) {
			VMSpec vm = this.createVM(g1_name.get(i),g1_flavor.get(i), -1, -1);
			vm.dirty_rate = g1_dirtyRate.get(i);
			vm.mig_deadline = g1_deadline.get(i);
			g1.put(g1_name.get(i), vm);
		}
		for(int i =0; i<g2_name.size(); i++) {
			VMSpec vm = this.createVM(g2_name.get(i),g2_flavor.get(i), -1, -1);
			vm.dirty_rate = g2_dirtyRate.get(i);
			vm.mig_deadline = g2_deadline.get(i);
			g2.put(g2_name.get(i), vm);
		}
		for(int i =0; i<micro_name.size(); i++) {
			VMSpec vm = this.createVM(micro_name.get(i),micro_flavor.get(i), -1, -1);
			vm.dirty_rate = micro_dirtyRate.get(i);
			vm.mig_deadline = micro_deadline.get(i);
		}
		
		//add link
		//long bw = 1000000000L;
		//this.addLinkAutoName(g1.get("v11"), g1.get("v12"), bw);
		//this.addLinkAutoName(g1.get("v11"), g1.get("v13"), bw);
		//this.addLinkAutoName(g1.get("v12"), g1.get("v14"), bw);
		//this.addLinkAutoName(g1.get("v13"), g1.get("v14"), bw);
		
		//bw = bw/5;
		//this.addLinkAutoNameBoth(g2.get("v21"), g2.get("v22"), bw);
		//this.addLinkAutoNameBoth(g2.get("v22"), g2.get("v23"), bw);
		
		List<String[]> dataLines = new ArrayList<>();
		
		
		dataLines.addAll( this.generatePacket(g1.get("v11"), g1.get("v12"), 1000, bwList.get(0), 200, 0, 500));
		dataLines.addAll( this.generatePacket(g1.get("v11"), g1.get("v13"), 1000, bwList.get(0), 200, 0, 500));
		dataLines.addAll( this.generatePacket(g1.get("v12"), g1.get("v14"), 1000, bwList.get(0), 200, 0, 500));
		dataLines.addAll( this.generatePacket(g1.get("v13"), g1.get("v14"), 1000, bwList.get(0), 200, 0, 500));
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"g1"+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			
			}
			writer.close();
		}catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
		dataLines = new ArrayList<>();
		
		dataLines.addAll( this.generatePacket(g2.get("v21"), g2.get("v22"), 1000, bwList.get(1), 200, 0, 500));
		
		dataLines.addAll( this.generatePacket(g2.get("v22"), g2.get("v23"), 1000, bwList.get(1), 200, 0, 500));
		dataLines.addAll( this.generatePacket(g2.get("v22"), g2.get("v21"), 1000, bwList.get(1), 200, 0, 500));
		
		dataLines.addAll( this.generatePacket(g2.get("v22"), g2.get("v21"), 1000, bwList.get(1), 200, 0, 500));
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"g2"+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			
			}
			writer.close();
		}catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	private VMSpec findVm(List<VMSpec> vmList, String vmName) {
		VMSpec vm = null;
		for(int i = 0; i< vmList.size(); i++) {
			if(vmList.get(i).name.equalsIgnoreCase(vmName)) {
				return vmList.get(i);
			}
		}
		return vm;
	}
	
	private HostSpec findHost(List<HostSpec> hostList, String hostName) {
		HostSpec host = null;
		for(int i =0; i<hostList.size();i++) {
			if(hostList.get(i).name.equalsIgnoreCase(hostName)) {
				return hostList.get(i);
			}
		}
		return host;
	}
	
	
	
	public void generateVmHostMapping(String mappingFileName) {
		List<String> hostNameList = Arrays.asList("h_0_0_0","h_0_0_1","h_0_1_0","h_0_1_1");
		JSONObject obj = new JSONObject();
		
		List<String> vmHostMapping = Arrays.asList("v11: h_0_0_0",
				"v12: h_0_0_1",
				"v13: h_0_0_1",
				"v14: h_0_1_1",
				"v21: h_0_0_0",
				"v22: h_0_1_0",
				"v23: h_0_1_1",
				"v31: h_0_1_0",
				"v41: h_0_1_0",
				"v51: h_0_1_0",
				"v61: h_0_1_0");
		
		JSONArray mapping = new JSONArray();
		for(String s:vmHostMapping) {
			mapping.add(s);
		}
		obj.put("mappings", mapping);
		
		
		try(PrintWriter writer = new PrintWriter(new File(mappingFileName))){
			writer.print(obj.toJSONString().replaceAll(",", ",\n"));
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	private void generateVirtualRandomVms(int totalNode, double minDirtyFactor, double maxDirtyFactor, Flavour minf, Flavour maxf, double minDeadline, double maxDeadline) {
		int minFlavorIndex = 0;
		int maxFlavorIndex = Flavour.values().length-1;
		for(int j =0; j<Flavour.values().length; j++) {
			if(Flavour.values()[j]==minf) {
				minFlavorIndex = j;
			}
			if(Flavour.values()[j]==maxf) {
				maxFlavorIndex = j;
			}
		}
		
		for(int i =0; i<totalNode; i++) {
			String vmName = "v-"+Integer.toString(i);
			int f = (int)(Math.random()*((maxFlavorIndex-minFlavorIndex)+1))+minFlavorIndex;
			VMSpec vm = this.createVM(vmName, Flavour.values()[f], -1, -1);
			double dirtyFactor = (Math.random()*((maxDirtyFactor*1000-minDirtyFactor*1000)+1))+minDirtyFactor*1000;
			vm.dirty_rate = dirtyFactor/1000;
			vm.mig_deadline = (Math.random()*((maxDeadline-minDeadline)+1))+minDeadline;
		}
		
	}
	
	private void genreateVirtualMasterStarBoth(int totalNode, int groupNum, Flavour masterf, Flavour slavef, double deadline, long reqBw) {
		String groupName = "v"+Integer.toString(groupNum);
		List<VMSpec> vmList = new ArrayList<>();
		String masterName = groupName +"-"+Integer.toString(0);
		VMSpec masterVm = this.createVM(masterName, masterf, -1, -1);
		masterVm.mig_deadline = deadline;
		for(int i =1; i<totalNode; i++) {
			String slaveName = groupName + "-"+Integer.toString(i);
			VMSpec vm = this.createVM(slaveName, slavef, -1,-1);
			vm.mig_deadline = deadline;
			vmList.add(vm);
		}
		
		//add link from slave to  master
		for(int i =0; i<vmList.size(); i++) {
			VMSpec slaveVm = vmList.get(i);
			this.addLinkAutoNameBoth(masterVm,slaveVm, reqBw);
			this.dataLines.addAll(this.generatePacket(masterVm, slaveVm, 1000, reqBw, 200, start, end));
			this.dataLines.addAll(this.generatePacket(slaveVm, masterVm, 1000, reqBw, 200, start, end));
		}
	}
	
	private void generateVirtualStartoSlave(int totalNode, int groupNum, Flavour masterf, Flavour slavef, double deadline, long reqBw) {
		String groupName = "v"+Integer.toString(groupNum);
		List<VMSpec> vmList = new ArrayList<>();
		String masterName = groupName +"-"+Integer.toString(0);
		VMSpec masterVm = this.createVM(masterName, masterf, -1, -1);
		masterVm.mig_deadline = deadline;
		for(int i =1; i<totalNode; i++) {
			String slaveName = groupName + "-"+Integer.toString(i);
			VMSpec vm = this.createVM(slaveName, slavef, -1,-1);
			vm.mig_deadline = deadline;
			vmList.add(vm);
		}
		
		//add link from slave to  master
		for(int i =0; i<vmList.size(); i++) {
			VMSpec slaveVm = vmList.get(i);
			this.addLinkAutoName(masterVm,slaveVm, reqBw);
			//this.dataLines.addAll(this.generatePacket(masterVm, slaveVm, 1000, reqBw, 200, start, end));
			//VMSpec src, VMSpec dst, double workload, Long bw, double rate, int start, int end
			this.dataLines.addAll(this.generatePacket(masterVm, slaveVm, 50, reqBw, 20, start, end));
		}
	}
	
	private void generateVirtualStartoMaster(int totalNode, int groupNum, Flavour masterf, Flavour slavef, double deadline, long reqBw) {
		String groupName = "v"+Integer.toString(groupNum);
		List<VMSpec> vmList = new ArrayList<>();
		String masterName = groupName +"-"+Integer.toString(0);
		VMSpec masterVm = this.createVM(masterName, masterf, -1, -1);
		masterVm.mig_deadline = deadline;
		for(int i =1; i<totalNode; i++) {
			String slaveName = groupName + "-"+Integer.toString(i);
			VMSpec vm = this.createVM(slaveName, slavef, -1,-1);
			vm.mig_deadline = deadline;
			vmList.add(vm);
		}
		
		//add link from slave to  master
		for(int i =0; i<vmList.size(); i++) {
			VMSpec slaveVm = vmList.get(i);
			this.addLinkAutoName(slaveVm, masterVm, reqBw);
			this.dataLines.addAll(this.generatePacket(slaveVm, masterVm, 1000, reqBw, 200, start, end));
		}
	}
	
	private void generateVirtualFullMeshTopo(int totalNode, int groupNum, Flavour f, double deadline, long reqBw) {
		String groupName = "v"+Integer.toString(groupNum);
		List<VMSpec> vmList = new ArrayList<>();
		Flavour flavor = f;
		for(int i=0;i<totalNode;i++) {
			String nodeName = groupName + "-"+Integer.toString(i);
			VMSpec vm = this.createVM(nodeName, flavor, -1,-1);
			vm.mig_deadline = deadline;
		}
		
		for(int i = 0; i<vmList.size();i++) {
			for(int j=i+1; j<vmList.size();j++) {
				this.addLinkAutoNameBoth(vmList.get(i), vmList.get(j), reqBw);
				this.dataLines.addAll(this.generatePacket(vmList.get(i), vmList.get(j), 1000, reqBw, 200, start, end));
				this.dataLines.addAll(this.generatePacket(vmList.get(j), vmList.get(i), 1000, reqBw, 200, start, end));
			}
		}
	}
	
	private void generateVirtualTierTopo(int totalTier, int groupNum, Flavour f, double deadline, long reqBw, int maxVMNum) {
		//randomly generate same VM in the chaining
		String groupName = "v"+Integer.toString(groupNum);
		List<List<VMSpec>> eachTierList = new ArrayList<>();
		int totalNode = 0;
		for(int i =0; i<totalTier; i++) {
			int tierVNF = 1;
			if(i!=0 && i!=totalTier-1) {
				tierVNF = (int)(Math.random()*((maxVMNum-1)+1))+1;
			}
			List<VMSpec> vmList = new ArrayList<>();
			for(int j =0; j<tierVNF; j++) {
				String nodeName = groupName +"-"+ Integer.toString(totalNode);
				VMSpec vm = this.createVM(nodeName, f, -1, -1);
				vm.mig_deadline = deadline;
				vmList.add(vm);
				totalNode++;
			}
			eachTierList.add(vmList);
		}
		
		//add links between nodes
		for(int i =0; i < eachTierList.size()-1; i++) {
			List<VMSpec> vmList1 = eachTierList.get(i);
			List<VMSpec> vmList2 = eachTierList.get(i+1);
			for(int m = 0; m<vmList1.size();m++) {
				for(int n =0; n<vmList2.size(); n++) {
					this.addLinkAutoNameBoth(vmList1.get(m), vmList2.get(n), reqBw);
					this.dataLines.addAll(this.generatePacket(vmList1.get(m), vmList2.get(n), 1000, reqBw, 200, start, end));
					this.dataLines.addAll(this.generatePacket(vmList2.get(n), vmList1.get(m), 1000, reqBw, 200, start, end));
				}
			}
		}
	}
	
	private void generateVirtualSFCTopo(int totalTier, int groupNum, Flavour f, double deadline, long reqBw, int maxVNFNum) {
		//randomly generate VNFs in the chaining
		String groupName = "v"+Integer.toString(groupNum);
		List<List<VMSpec>> eachTierList = new ArrayList<>();
		int totalNode = 0;
		for(int i =0; i<totalTier; i++) {
			int tierVNF = 1;
			if(i!=0 || i!=totalTier-1) {
				tierVNF = (int)(Math.random()*((maxVNFNum-1)+1))+1;
			}
			List<VMSpec> vmList = new ArrayList<>();
			for(int j =0; j<tierVNF; j++) {
				String nodeName = groupName +"-"+ Integer.toString(totalNode);
				VMSpec vm = this.createVM(nodeName, f, -1, -1);
				vm.mig_deadline = deadline;
				vmList.add(vm);
				totalNode++;
			}
			eachTierList.add(vmList);
		}
		
		//add links between nodes
		for(int i =0; i < eachTierList.size()-1; i++) {
			List<VMSpec> vmList1 = eachTierList.get(i);
			List<VMSpec> vmList2 = eachTierList.get(i+1);
			for(int m = 0; m<vmList1.size();m++) {
				for(int n =0; n<vmList2.size(); n++) {
					this.addLinkAutoName(vmList1.get(m), vmList2.get(n), reqBw);
					this.dataLines.addAll(this.generatePacket(vmList1.get(m), vmList2.get(n), 50, reqBw, 200, start, end));
				}
			}
		}
	}
	
	
	public void generateExperimentVmHostMapping(String mappingFileName) {
		int numPods = 4;
		int hostNum = (int)Math.pow(numPods, 3)/4;
		
	}
	
	List<String[]> dataLines = new ArrayList<>();
	int start = 0;
	int end = 500;
	
	public void generateExperimentVirtualTopo(String jsonFileName, String csvFileName) {
		String[] fname = csvFileName.split("\\.",2);
		
		int randomNum = 0;
		int starToSlave = 10;
		int starToMaster = 0;
		int starBoth = 0;
		int sfcGroupNum = 10;
		int multiTierGroupNum = 0;
		
		double deadline_star = 100.0;
		double deadline_sfc = deadline_star + 400;
		
		int groupNum = 1;
		
		//random
		this.generateVirtualRandomVms(randomNum, 0.01, 0.05, Flavour.MEDIUM, Flavour.MICRO, 200.0, 600.0);
		
		//star to slave
		for(int i =0 ; i<starToSlave; i++) {
			long reqBw = (long)Math.pow(10, 8);
			this.generateVirtualStartoSlave(5, groupNum, Flavour.SMALL, Flavour.TINY, deadline_star, reqBw);
			//this.generateVirtualStartoSlave(5, groupNum, Flavour.SMALL_BUSY, Flavour.TINY, 300*(i+1), reqBw);
			groupNum++;
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"startoslave-"+i+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			}
			writer.close();
			this.dataLines = new ArrayList<>();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}
		}
		
		this.dataLines = new ArrayList<>();
		
		//star to master
		for(int i = 0; i<starToMaster; i++) {
			long reqBw = (long)Math.pow(10, 8);
			this.generateVirtualStartoMaster(5, groupNum, Flavour.SMALL_BUSY, Flavour.TINY, 300*(i+1), reqBw);
			groupNum++;
		
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"startomaster-"+i+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			}
			writer.close();
			this.dataLines = new ArrayList<>();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}}
		
		this.dataLines = new ArrayList<>();
		
		//star between master and slaves
		for(int i =0; i<starBoth; i++) {
			long reqBw = (long)Math.pow(10, 8);
			this.genreateVirtualMasterStarBoth(5, groupNum, Flavour.SMALL_BUSY, Flavour.TINY, 300*(i+1), reqBw);
			groupNum++;
		
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"starboth-"+i+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			}
			writer.close();
			this.dataLines = new ArrayList<>();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}}
		
		this.dataLines = new ArrayList<>();
		
		//sfc
		for(int i =0; i<sfcGroupNum; i++) {
			long reqBw = (long)Math.pow(10, 9);
			this.generateVirtualSFCTopo(3, groupNum, Flavour.LARGE, deadline_sfc, reqBw, 2);
			groupNum++;
		
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"sfc-"+i+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			}
			writer.close();
			this.dataLines = new ArrayList<>();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}}
		
		this.dataLines = new ArrayList<>();
		
		//multi-tier
		for(int i =0; i<multiTierGroupNum; i++) {
			long reqBw = (long)(5*Math.pow(10, 8));
			this.generateVirtualTierTopo(3, groupNum, Flavour.MEDIUM, 300*(i+1), reqBw, 3);
			groupNum++;
		
		try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+"multitier-"+i+".csv"))) {
			for(int n=0; n<dataLines.size();n++) {
				String objectsCommaSeparated = String.join(",", dataLines.get(n));
				writer.print(objectsCommaSeparated);
			}
			writer.close();
			this.dataLines = new ArrayList<>();
		}catch (FileNotFoundException e) {
			e.printStackTrace();
		}}
		
		this.dataLines = new ArrayList<>();
		
		this.wrtieJSON(jsonFileName);
	}
	
	/**
	 * generate Power Experiment VmList based on 4 Types from PlanetLab experiment in cloudsim.example.power.constants
	 * dataLine for workload writer
	 * @param jsonFileName
	 */
	public void generatePowerExperimentVirtualTopo(String jsonFileName) {
		//write in jsonFile, read from csv File
		final int vmsNumber = 1052;
		final int VM_TYPES	= 3;
		final int[] VM_MIPS	= { 2500, 2000, 1000, 500 };
		final int[] VM_PES	= { 2, 2, 2, 2 };
		final double[] VM_RAM	= { 2,  4, 4, 1 };
		final int VM_BW		= 1500000; // 1.5 Mbit/s
		final int VM_SIZE		= 4; // 4 GB
		for(int i=0;i<vmsNumber;i++) {
			int vmType = i / (int) Math.ceil((double) vmsNumber / VM_TYPES);
			TimeGen startTime = new TimeGen(-1);
			TimeGen endTime = new TimeGen(-1);
			VMSpec vm = addVM(Integer.toString(i), VM_PES[vmType], VM_MIPS[vmType], (int) VM_RAM[vmType], VM_SIZE, VM_BW, startTime.getStartTime(), endTime.getEndTime());
			//can be randomly generated during the simulation
			vm.dirty_rate = 0.001;
			vmCount++;
		}
		this.wrtieJSON(jsonFileName);		
	}
	
	
	public void generateLoadBalanceExperimentWorkload(String csvFileName, int multi) {
		List<String[]> dataLines = new ArrayList<>();
		String[] fname = csvFileName.split("\\.",2);
		
		List<Integer> loadRateList = Arrays.asList(5, 15, 10, 4, 6, 20, 4, 6, 10, 5, 5, 20, 4, 6, 20, 15, 15, 10, 10, 20, 15, 15, 10, 10, 5, 
				5, 20, 15, 15, 10, 10, 5, 5, 4, 6, 20, 15, 5);
		List<Integer> full_loadRateList = new ArrayList<>();
		for(int i =0; i<multi; i++) {
			for(Integer load:loadRateList)
				full_loadRateList.add(load);
		}
		
		//List<Integer> full_loadRateList = Stream.of(loadRateList, loadRateList)
        //        .flatMap(Collection::stream)
        //        .collect(Collectors.toList());

		int VM_MIPS = 2000;
		int VM_PES =2;
		double endTime = 6000;
		int workloadInterval = 60;
		
		for(int vmid=0; vmid<full_loadRateList.size(); vmid++) {
			double cpuUtilization = full_loadRateList.get(vmid);
			for(double time =0; time < endTime; time+=workloadInterval) {
				double workload = cpuUtilization * VM_MIPS * VM_PES * workloadInterval;
				dataLines.add(new String[] {Double.toString(time), Integer.toString(vmid), "0", Integer.toString((int)workload)+"\n"});
			}
			try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+vmid+".csv"))) {
				for(int i=0; i<dataLines.size();i++) {
					String objectsCommaSeparated = String.join(",", dataLines.get(i));
					//System.out.print(objectsCommaSeparated);
					writer.print(objectsCommaSeparated);
				}			
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			dataLines.clear();
		}

	}
	
	public void generateLoadBalanceExperimentVirtualTopo(String vmDataCSVFile, String jsonFileName, int multi) {
		List<Integer> memList = new ArrayList<>();
		try{
			BufferedReader reader = new BufferedReader(new FileReader(vmDataCSVFile));
			String row;
			//vmId, Time, srcHostId, dstHostId, memorySize
			//double currentTime = CloudSim.clock();
			while((row = reader.readLine())!=null) {
				String[] data = row.split(",");
				Integer mem= Integer.valueOf(data[0]);
				memList.add(mem);
			}
		}catch(Exception e) {
			System.out.println(e);
		}
		List<Integer> full_memList = new ArrayList<>();
		final int VM_BW		= 1500000; // 1.5 Mbit/s
		final int VM_MIPS = 2000;
		final int VM_PES = 2;
		final double dirtyRate = 0.001;
		final int VM_SIZE = 2; //2GB
		
		for(int i =0; i<multi; i++) {
			for(Integer mem:memList) {
				full_memList.add(mem);
			}
		}
		int vmNum = full_memList.size();
		for(int vmid =0; vmid < vmNum; vmid++) {
			TimeGen startTime = new TimeGen(-1);
			TimeGen endTime = new TimeGen(-1);
			VMSpec vm = addVM(Integer.toString(vmid), VM_PES, VM_MIPS, full_memList.get(vmid), VM_SIZE, VM_BW, startTime.getStartTime(), endTime.getEndTime());
			//can be randomly generated during the simulation
			vm.dirty_rate = dirtyRate;
			vmCount++;
		}
		this.wrtieJSON(jsonFileName);
	}
	
	public void generateLoadBalanceExperimentVirtualTopo(String jsonFileName, int multi) {
		//List<Integer> memList = Arrays.asList(16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16, 16);
		List<Integer> memList = Arrays.asList(16, 4, 2, 4, 8, 16, 4, 8, 2, 16, 16, 16, 4, 8, 16, 4, 4, 2, 2, 16, 4, 4, 2, 2, 16, 16, 16, 4, 4, 2, 2, 16, 16, 4, 8, 16, 4, 16);
		List<Integer> full_memList = new ArrayList<>();
		final int VM_BW		= 1500000; // 1.5 Mbit/s
		final int VM_MIPS = 2000;
		final int VM_PES = 2;
		final double dirtyRate = 0.001;
		final int VM_SIZE = 2; //2GB
		
		for(int i =0; i<multi; i++) {
			for(Integer mem:memList) {
				full_memList.add(mem);
			}
		}
		int vmNum = full_memList.size();
		for(int vmid =0; vmid < vmNum; vmid++) {
			TimeGen startTime = new TimeGen(-1);
			TimeGen endTime = new TimeGen(-1);
			VMSpec vm = addVM(Integer.toString(vmid), VM_PES, VM_MIPS, full_memList.get(vmid), VM_SIZE, VM_BW, startTime.getStartTime(), endTime.getEndTime());
			//can be randomly generated during the simulation
			vm.dirty_rate = dirtyRate;
			vmCount++;
		}
		this.wrtieJSON(jsonFileName);
	}
	
	public void generateLoadBalanceExperimentMapping (String mappingFileName, int multi) throws FileNotFoundException {
		List<Integer> p_init = Arrays.asList(0, 0, 0, 0, 0, 0, 1, 1, 1, 1, 1, 2, 3, 3, 4, 4, 4, 4, 4, 5, 5, 5, 5, 5, 5, 5, 6, 6, 6, 6, 6, 6, 6, 6, 6, 7, 7, 7);
		List<Integer> full_p_init = new ArrayList<>();
		for(int i=0; i<multi; i++) {
			for(Integer p:p_init) {
				full_p_init.add(p+8*i);
			}
		}
		JSONObject obj = new JSONObject();
		JSONArray mapping = new JSONArray();

		int vmNum = full_p_init.size();
		for(int vmid =0; vmid < vmNum; vmid++) {
			int src = full_p_init.get(vmid);
			// vmid: srchostId
			String mapItem = Integer.toString(vmid) +": "+Integer.toString(src);
			mapping.add(mapItem);
		}
		obj.put("mappings", mapping);
		
		//write in the mappingFileName		
		try(PrintWriter writer = new PrintWriter(new File(mappingFileName))){
			writer.print(obj.toJSONString().replaceAll(",", ",\n"));
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	/**
	 * generate initial vm placement mapping for the start of the simulation based on the placementFile
	 * @param placementFile
	 * @param mappingFileName
	 * @throws FileNotFoundException
	 */
	public void generatePowerExperimentMapping(String placementFile, String mappingFileName) throws FileNotFoundException {
		//int totalHostNum = 600;
		//int totalVmNum = 1052;
		
		JSONObject obj = new JSONObject();
		//read placementFile for each time interval
		List<String> vmHostMapping = new ArrayList<>();
		//create vmHostMapping json format
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(placementFile));
			String row = null;
			double initialTime = 600;
			JSONArray mapping = new JSONArray();
			while((row = csvReader.readLine())!=null) {
				String[] data  = row.split(",");
				//time, vmId, hostId
				double currentTime = Double.valueOf(data[0]);
				if(currentTime != initialTime)
					break;
				
				String mapItem = data[1]+": "+data[2];			
				mapping.add(mapItem);
			}
			obj.put("mappings", mapping);
		} catch (IOException e1) {
			//Auto-generated catch block
			e1.printStackTrace();
		}
		//write in the mappingFileName		
		try(PrintWriter writer = new PrintWriter(new File(mappingFileName))){
			writer.print(obj.toJSONString().replaceAll(",", ",\n"));
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	
	/**
	 * generate workload for generated VMs based on CPU utilization and VM MIPS and cores
	 * inputFolderName for planetLab CPU utilization data folder
	 * csvFileName for workload file of total cloudletList size VMs
	 * @param inputFolderName
	 * @param csvFileName
	 */
	public void generatePowerExperimentWorkload(String inputFolderName, String csvFileName) {
		final int vmsNumber = 1052;
		final int VM_TYPES	= 3;
		final int[] VM_MIPS	= { 2500, 2000, 1000, 500 };
		final int[] VM_PES	= { 1, 1, 1, 1 };
		final double[] VM_RAM	= { 2,  4, 4, 1 };
		final int VM_BW		= 1500000; // 1.5 Mbit/s
		final int VM_SIZE		= 4; // 4 GB
		
		List<Cloudlet> cloudletList = new ArrayList<>();
		
		long fileSize = 300;
		long outputSize = 300;
		UtilizationModel utilizationModelNull = new UtilizationModelNull();

		File inputFolder = new File(inputFolderName);
		File[] files = inputFolder.listFiles();
		
		final double SCHEDULING_INTERVAL = 600;
		final double SIMULATION_LIMIT = 24 * 60 * 60;

		final int CLOUDLET_LENGTH	=  2500 * (int) SIMULATION_LIMIT; // 2500 * (int) SIMULATION_LIMIT;
		final int CLOUDLET_PES	= 1;
		
		for (int i = 0; i < files.length; i++) {
			Cloudlet cloudlet = null;
			try {
				cloudlet = new Cloudlet(
						i,
						CLOUDLET_LENGTH,
						CLOUDLET_PES,
						fileSize,
						outputSize,
						new UtilizationModelPlanetLabInMemory(
								files[i].getAbsolutePath(),
								SCHEDULING_INTERVAL), utilizationModelNull, utilizationModelNull);
			} catch (Exception e) {
				e.printStackTrace();
				System.exit(0);
			}
			cloudlet.setUserId(2);
			cloudlet.setVmId(i);
			cloudletList.add(cloudlet);
		}
		
		List<String[]> dataLines = new ArrayList<>();
		
		String[] fname = csvFileName.split("\\.",2);
		
		int vmId = 0;
		for(Cloudlet cl : cloudletList) {
			int vmType = vmId / (int) Math.ceil((double) vmsNumber / VM_TYPES);
			System.out.println("cloudlet length " + cl.getCloudletLength());
			cl.getCloudletFileSize();
			cl.getActualCPUTime();
			//24hrs 86400s interval: 600s
			int workloadInterval = 60;
			for(double time=0; time<86400; time +=workloadInterval) {
				double cpuUtilization = cl.getUtilizationModelCpu().getUtilization(time);
				System.out.println("time "+time+"cpu util: "+cpuUtilization);
				double workload = cpuUtilization * VM_MIPS[vmType] * VM_PES[vmType] * workloadInterval;
				dataLines.add(new String[] {Double.toString(time), Integer.toString(cl.getVmId()), "0", Integer.toString((int)workload)+"\n"});
			}
			//write workload dataLines to the csv file for each VM
			try (PrintWriter writer = new PrintWriter(new File(fname[0]+"-"+vmId+".csv"))) {
				for(int i=0; i<dataLines.size();i++) {
					String objectsCommaSeparated = String.join(",", dataLines.get(i));
					//System.out.print(objectsCommaSeparated);
					writer.print(objectsCommaSeparated);
				}			
			} catch (FileNotFoundException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			
			dataLines.clear();
			vmId +=1;
			//if(vmId == 1)
			//	break;
		}
	}
	
	
	
	
	
	
	
	
	
	
	
	
	
}
