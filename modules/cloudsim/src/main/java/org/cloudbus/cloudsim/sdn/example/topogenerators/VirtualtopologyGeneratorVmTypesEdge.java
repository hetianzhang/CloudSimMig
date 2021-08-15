package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Random;
import java.util.Set;

import org.cloudbus.cloudsim.sdn.example.topogenerators.VirtualTopologyGenerator.VMSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.VirtualTopologyGeneratorVmTypesSimple.Flavour;
import org.cloudbus.cloudsim.sdn.example.topogenerators.VirtualTopologyGeneratorVmTypesSimple.TimeGen;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * generate container virutal topology in edge container migration paper
 * @author tianzhangh
 *
 */

public class VirtualtopologyGeneratorVmTypesEdge extends VirtualTopologyGenerator {

	public static void main(String[] args) {
		// TODO Auto-generated method stub
		String inputBase = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
		String outputBase = "edge-paper\\";
		String csvFilePathMapping = "container_initial_00.csv";
		String jsonFilePathMapping = "edge.virtual-mapping-1000.json";
		String workloadPath = "edge-workload";
		
		VirtualtopologyGeneratorVmTypesEdge generator = new VirtualtopologyGeneratorVmTypesEdge();
		
		//start();
		start(4000);
		//generateInitialMapping(inputBase+csvFilePathMapping, outputBase+jsonFilePathMapping);
		//generator.generateInitialMapping(inputBase+csvFilePathMapping, outputBase+jsonFilePathMapping, 2000);
		//generator.generateEdgeContainierWorkload(inputBase+csvFilePathMapping, outputBase+workloadPath);
	}
	
	public static void start() {
		VirtualtopologyGeneratorVmTypesEdge edgeGenerator = new VirtualtopologyGeneratorVmTypesEdge();
		String inputBase = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
		String outputBase = "edge-paper\\";
		String csvFilePath = "mig_00.csv";
		String jsonFilePath = "edge.virtual-00-bw3.json";
		edgeGenerator.generateVirtualTopologyEdgeContainer(inputBase+csvFilePath, outputBase+jsonFilePath);
	}
	
	public static void start(int containerNum) {
		VirtualtopologyGeneratorVmTypesEdge edgeGenerator = new VirtualtopologyGeneratorVmTypesEdge();
		String inputBase = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
		String outputBase = "edge-paper-random\\";
		String csvFilePath = "mig_00.csv";
		String jsonFilePath = "edge.virtualrandom-"+containerNum+".json";
		String containerListCSV = "edge.containerid-"+containerNum+".csv";
		//edgeGenerator.generateVirutalTopologyEdgeContainerList(inputBase+csvFilePath, outputBase+containerListCSV, containerNum);
		edgeGenerator.generateVirtualTopologyEdgeContainer(inputBase+csvFilePath, outputBase+jsonFilePath, containerNum);
	}
	
	
	public static void generateInitialMapping(String csvFilePath, String jsonFilePath) {
		//covert mapping csv file to json
		JSONObject obj = new JSONObject();
		//read placementFile for each time interval
		List<String> vmHostMapping = new ArrayList<>();
		//create vmHostMapping json format
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(csvFilePath));
			String row = null;
			double initialTime = 0;
			JSONArray mapping = new JSONArray();
			while((row = csvReader.readLine())!=null) {
				String[] data  = row.split(",");
				//containerId, edge DC Id
				String mapItem = "c-"+data[0] + ": "+ "edge" + data[1];
				mapping.add(mapItem);
				//userId, base stations id
				mapItem = "u-"+ data[0] + ": "+ "base" + data[1];
				mapping.add(mapItem);
			}
			obj.put("mappings", mapping);
		} catch (IOException e1) {
			//Auto-generated catch block
			e1.printStackTrace();
		}
		//write in the mappingFileName		
		try(PrintWriter writer = new PrintWriter(new File(jsonFilePath))){
			writer.print(obj.toJSONString().replaceAll(",", ",\n"));
			writer.close();
			System.out.println(jsonFilePath+ " saved");
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	public Set<String> getContainerSet(String csvFilePath, int totalnum) {
		Set<String> containerSet = getContainerSet(csvFilePath);
		int i = 0;
		Set<String> set = new LinkedHashSet<>();
		for(String id:containerSet) {			
			if(i<totalnum) {
				set.add(id);
				i++;
			}
			else
				break;
		}
		return set;
	}
	
	public void generateInitialMapping(String csvFilePath, String jsonFilePath, int totalnum) {
		//covert mapping csv file to json
				JSONObject obj = new JSONObject();
				//read placementFile for each time interval
				List<String> vmHostMapping = new ArrayList<>();
				//create vmHostMapping json format
				Set<String> containerSet = getContainerSet(csvFilePath, totalnum);
				System.out.println("Total container size: "+containerSet.size());
				
				try {
					BufferedReader csvReader = new BufferedReader(new FileReader(csvFilePath));
					String row = null;
					double initialTime = 0;
					JSONArray mapping = new JSONArray();
					while((row = csvReader.readLine())!=null) {
						String[] data  = row.split(",");
						if(containerSet.contains(data[0])) {
							//containerId, edge DC Id
							String mapItem = "c-"+data[0] + ": "+ "edge" + data[1];
							mapping.add(mapItem);
							//userId, base stations id
							mapItem = "u-"+ data[0] + ": "+ "base" + data[1];
							mapping.add(mapItem);
						}
						
					}
					obj.put("mappings", mapping);
				} catch (IOException e1) {
					//Auto-generated catch block
					e1.printStackTrace();
				}
				//write in the mappingFileName		
				try(PrintWriter writer = new PrintWriter(new File(jsonFilePath))){
					writer.print(obj.toJSONString().replaceAll(",", ",\n"));
					writer.close();
					System.out.println(jsonFilePath+ " saved");
				} catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
	}
	
	public void generateVirtualTopologyEdgeContainer(String csvFilePath, String jsonFilePath) {
		//generate container json file based on the csv file (container migration file)
		//edge computing scenario: intelligent vehicles container
		
		//some configuration won't affect the live container migration experiments
		//final int BW = 1500000; // 1.5 Mbit/s
		//final int MIPS = 2000;
		//final int PES = 1;
		//final int SIZE = 1; //1GB
		final long userLinkBw = 1500000L;
		
		//container configurations affect live container migration experiments
		//final double dirtyRate = 0.001;
		//final int RAM = 2; //2GB maximum memory one container can utilize
		
		Set<String> containerSet = new HashSet<>();
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(csvFilePath));
			String row = null;
			while((row = csvReader.readLine())!=null) {
				String[] data  = row.split(",");
				//containerId, src, dst, arrival time, deadline
				if(!containerSet.contains(data[0])) {
					containerSet.add(data[0]);
					TimeGen startTime = new TimeGen(-1);
					TimeGen endTime = new TimeGen(-1);					
					VMSpec container = createContainer("c-"+data[0], Flavour.CONTAINER_1GB, startTime.getStartTime(), endTime.getEndTime());
					VMSpec user = createContainer("u-"+data[0], Flavour.EDGE_USER, startTime.getStartTime(), endTime.getEndTime());
					this.addLinkAutoNameBoth(container, user, userLinkBw);
					//VMSpec c = addVM(data[0], PES, MIPS, RAM, SIZE, BW, startTime.getStartTime(), endTime.getEndTime());
					//c.dirty_rate = dirtyRate;
				}
			}
		} catch (IOException e1) {
			//Auto-generated catch block
			e1.printStackTrace();
		}
		this.wrtieJSON(jsonFilePath);		
	}
	
	public void generateVirutalTopologyEdgeContainerList(String csvFilePath, String outFilePath, int totalnum) {
		//generate the list of container id
		Set<String> containerSet = new HashSet<>();
		containerSet = getContainerSet(csvFilePath, totalnum);
		PrintWriter writer;
		try {
			writer = new PrintWriter(new File(outFilePath));
			for(String id:containerSet) {
				writer.println(id);
			}
			writer.flush();
			writer.close();
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		
	}
	
	public void generateVirtualTopologyEdgeContainer(String csvFilePath, String jsonFilePath, int totalnum) {
		//generate first totalnum containers/users
		//generate container json file based on the csv file (container migration file)
		//edge computing scenario: intelligent vehicles container
		
		//some configuration won't affect the live container migration experiments
		//final int BW = 1500000; // 1.5 Mbit/s
		//final int MIPS = 2000;
		//final int PES = 1;
		//final int SIZE = 1; //1GB
		final long userLinkBw = 1500000L;
		
		Random r = new Random(10);
		int ramupper = 4;
		int ramlower = 1;
		
		//container configurations affect live container migration experiments
		//final double dirtyRate = 0.001;
		//final int RAM = 2; //2GB maximum memory one container can utilize
		
		Set<String> containerSet = new HashSet<>();
		containerSet = getContainerSet(csvFilePath, totalnum);
		int i = 0;
		System.out.println("container set size:"+containerSet.size());
		for(String id:containerSet) {
			TimeGen startTime = new TimeGen(-1);
			TimeGen endTime = new TimeGen(-1);					
			VMSpec container = createContainer("c-"+id, Flavour.CONTAINER_4GB, startTime.getStartTime(), endTime.getEndTime());
			container.ram = r.nextInt((ramupper - ramlower) +1) + ramlower;
			VMSpec user = createContainer("u-"+id, Flavour.EDGE_USER, startTime.getStartTime(), endTime.getEndTime());
			this.addLinkAutoNameBoth(container, user, userLinkBw);
		}
		this.wrtieJSON(jsonFilePath);		
	}
	
	public Set<String> getContainerSet(String csvFilePath) {
		Set<String> containerSet = new LinkedHashSet<>();
		try {
			BufferedReader csvReader = new BufferedReader(new FileReader(csvFilePath));
			String row = null;
			while((row = csvReader.readLine())!=null) {
				String[] data  = row.split(",");
				//containerId, src, dst, arrival time, deadline
				if(!containerSet.contains(data[0])) {
					containerSet.add(data[0]);
				}
			}
		} catch (IOException e1) {
			//Auto-generated catch block
			e1.printStackTrace();
		}
		return containerSet;
	}
	
	public void generateVirtualTopologyEdgeContainerRandom(String csvFilePath, String jsonFilePath) {
		//edge computing scenario: container application varies (memory, dirty page)
		
	}
	
	public void generateEdgeContainierWorkload(String csvFilePath, String workloadFilePath) {
		List<String[]> dataTotal = new ArrayList<>();
		
		//generate container workload for edge computing experiments
		// workload arrival speed (low frame resolution 128*128bit) 0.6Mbit/s in Poisson distribution
		// high frame resolution 256*256bit, 2.4Mbit/s in Possion distribution
		List<Long> arrivalSpeed = Arrays.asList(600000L, 2400000L);
		
		final int fps = 6; // frame per second
		final int cycleSeed = 10;
		
		// processing cycle/bit [500, 1000] (uniformly distributed)
		Integer cyclePerBitLower = 500;
		Integer cyclePerBitUpper = 1000;
		
		int startTime = 0;
		int endTime = 1800; //30 mins
		double speedRate = arrivalSpeed.get(0);

		Random r = new Random(cycleSeed);
		
		//25GHz processing power allocated for container (per vehicle) in Edge Data Center
		Double cpuPowerTotal = 25* Math.pow(10, 9);
		Double cpuPerCore = 5* Math.pow(10, 9);
		int pes = 5;
				
		//completion deadline 150ms
		int deadline = 150;
		
		int startgroup = 0; //start num of generated container group (total 6548)
		int group = 0;
		int containerNum = 1;
		
		//migration cost and downtime
		Set<String> containerNameSet = getContainerSet(csvFilePath);
		System.out.println("total container size:"+containerNameSet.size());
		for(String name:containerNameSet) {
			String containerId = "c-"+name;
			String userId = "u-"+name;
			String upLinkName = userId + containerId;
			String downLinkName = containerId + userId;
			
			
			int cyclePerBit = r.nextInt(cyclePerBitUpper + cyclePerBitLower) - cyclePerBitLower;
			
			System.out.println("generating container num: "+containerNum);
			if(group>=startgroup) {
				List<String[]> dataLines = this.generateWorkloadPoissonArrival(containerNum, name, startTime, endTime, 128*128, fps, cyclePerBitUpper, cyclePerBitLower);
				System.out.println("dataLines size: "+dataLines.size());
				dataTotal.addAll(dataLines);
				System.out.println("data total lines: "+dataTotal.size());
			}
			
			
			
			
			if(containerNum%100 == 0) {
				
				System.out.println("generating workload group: "+group);
				System.out.println("data total lines: "+dataTotal.size());
				
				if(group >=startgroup) {
					PrintWriter writer = null;
				//try (writer = new PrintWriter(new File(workloadFilePath+"-"+group+".csv"))) {
					try {
						writer = new PrintWriter(new File(workloadFilePath+"-"+group+".csv"));
					for(int i=0; i<dataTotal.size();i++) {
						String objectsCommaSeparated = String.join(",", dataTotal.get(i));
						//System.out.print(objectsCommaSeparated);
						writer.print(objectsCommaSeparated);
							
					}
					
				
				}  catch (FileNotFoundException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				} 
				finally {
					if(writer!=null) {
						writer.flush();
						writer.close();
					}
				}
				}
				dataTotal = new ArrayList<>();
				group++;
			}
			containerNum++;
		}
		try (PrintWriter writer = new PrintWriter(new File(workloadFilePath+"-"+group+".csv"))) {
			for(int i=0; i<dataTotal.size();i++) {
				String objectsCommaSeparated = String.join(",", dataTotal.get(i));
				//System.out.print(objectsCommaSeparated);
				writer.print(objectsCommaSeparated);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
	
	
	private static int getPoissonRandom(double mean) {
		int SEED = 10;
	    Random r = new Random(SEED);
	    double L = Math.exp(-mean);
	    int k = 0;
	    double p = 1.0;
	    do {
	        p = p * r.nextDouble();
	        k++;
	    } while (p > L);
	    return k - 1;
	}
	
	
	private List<String[]> generateWorkloadPoissonArrival(int seed, String id, int start, int end, int packetSize, int fps, int cyclePerBitUpper, int cyclePerBitLower){
		double totalcpuPower = 25 * Math.pow(10, 9); //25GHz
		double containerMIPS = 5* 10000;
		
		double cycleToMIPS = containerMIPS / totalcpuPower; //1 cycle = ?MIPS
		double attime = 0;
		
		String userId = "u-"+id;
		String containerId = "c-"+id;
		String linkuc = userId+containerId;
		String linkcu = containerId+userId;
		
		
		//int SEED = Integer.valueOf(id);
		Random r = new Random(seed);
		double lambda = fps;
		
		List<String[]> dataLines = new ArrayList<>();
		dataLines.add(new String[] {"atime", "userid", "zeros", "w.user", "link.uc", "containerid", "p.uc", "w.container", "link.cu"
				, "userid", "p.cu", "w.user"+"\n"});
		int totalNum = (int) Math.ceil((end-start)*fps);
		for(int i=0; i<totalNum; i++) {
			attime = attime - Math.log(1.0 - r.nextDouble()) / lambda;
			int cycle = r.nextInt(cyclePerBitUpper - cyclePerBitLower) + cyclePerBitLower;
			double workload = cycleToMIPS * cycle * packetSize;
			
			dataLines.add(new String[] {Double.toString(Math.round(attime*10000.0)/10000.0) , userId, "0", "10", linkuc, 
					containerId, Integer.toString(packetSize), Double.toString(workload), linkcu, userId, "128", "5" +"\n"});
		}
		
		return dataLines;
	}
	
	private List<String[]> generateWorkloadPoissonArrival(String name, int start, int end, double arrivalRate, double cyclePerBit) {
		double rate = arrivalRate;
		int round = (int) Math.ceil((end-start)*rate);
		List<String[]> dataLines = new ArrayList<>();
		for(int i=0;i<round;i++) {
			double attime = 0.1;
			attime = (i-1)*(double)(1/rate)+(double)getPoissonRandom((1/rate)*100.0)/100.0;
			while(attime<0) {attime = (i-1)*(double)(1/rate)+(double)getPoissonRandom((double)(1/rate)*100.0)/100.0;}
			//System.out.println(attime);
			String userId = "u-" + name;
			String containerId = "c-" + name;
			
			//attime, name.1, zeros(no previous net workload), w.1.1, link.1.2, name.2, p.1.2, w.2.1, link.2.1, name.1, p.2.1
			dataLines.add(new String[] {Double.toString(attime), userId, "0", Integer.toString(10), 
					Integer.toString((int)cyclePerBit)+"\n"});
			
			//for(int j =0; j<number; j++) {
				//String vmName = name +"-"+ Integer.toString(snumber+j);
				//double workloadVar = getGaussian(workload, 500);
				//dataLines.add(new String[] {Double.toString(attime), vmName, "0", Integer.toString((int)workload)+"\n"});
			//}
			
		}
		return dataLines;
	}
	
	public VMSpec createContainer(String name, Flavour f, double startTime, double endTime) {

		int pes =1;
		long vmSize = 1;
		long mips=10000;
		int vmRam = 2;
		long vmBW=3000000L; //1500000L; //3000000L
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
			pes =5;
			mips=10000;
			vmSize = 1;
			dirty_rate_factor = 0.02;
			//vmBW=100000;
			break;
		case CONTAINER_1GB:
			vmRam = 1;
			pes =5;
			mips=10000;
			vmSize = 1;
			dirty_rate_factor = 0.02;
			break;
		case CONTAINER_4GB:
			vmRam = 1;
			pes =5;
			mips=10000;
			vmSize = 1;
			dirty_rate_factor = 0.02;
			break;
		case EDGE_USER:
			vmRam = 1;
			pes =1;
			mips=10000;
			vmSize = 1;
			dirty_rate_factor = 0.02;
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

}
