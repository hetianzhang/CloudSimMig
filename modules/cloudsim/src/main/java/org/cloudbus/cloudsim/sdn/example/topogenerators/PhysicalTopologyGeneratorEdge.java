package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGeneratorSFC.HostSpec;

public class PhysicalTopologyGeneratorEdge extends PhysicalTopologyGeneratorSFC{
	
	public static void main(String [] argv) {
		String base = "C:\\Users\\tianzhangh\\Documents\\edge-computing-network\\telecom\\edge-cloud-placement\\output_data_mig\\";
		
		try {
			start(base+"physcial_edge_server.csv", "edge-paper\\edge.physcial-basestation-5000pe-50ms-5ms-1Gbps.json");
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		}
	}
	
	public static void start(String path, String jsonFileName) throws FileNotFoundException {
		double latency = 0.1;
		//!!!!!!the latency unit is in ms!!!!!!!!
		double latencyBase = 5;//5ms //0.025; //5ms between base station and edge data center
		double latencyEdge = 50;//1s //50ms between edge data centers //debug test 10s dealy between edge data centers
		
		long iops = 1000000000L;
		
		int pe = 5000;//96; //48; //16;
		long mips = 10000; //8000;
		int ram = 10240;
		long storage = 10000000;
		long bw = 1000000000L;
		
		
		//read link connection between different edge servers
		int totaledgenum = 0;
		Hashtable<String, List<String>> linkDict = new Hashtable<String, List<String>>();
		BufferedReader csvReader = new BufferedReader(new FileReader(path));
		try {
			String row;
			while ((row = csvReader.readLine()) != null) {
			    String[] data = row.split(",");
			    // do something with the data
			    int edgeid = Integer.valueOf(data[0]);
			    if(totaledgenum < edgeid)
			    	totaledgenum = edgeid;
			    if(linkDict.containsKey(data[0])) {
			    	linkDict.get(data[0]).add(data[1]);
			    }else {
			    	List<String> dstList = new ArrayList<>();
			    	dstList.add(data[1]);
			    	linkDict.put(data[0], dstList);
			    }
			    
			}
			totaledgenum +=1;
			csvReader.close();
		}catch(Exception e) {
			e.printStackTrace();
		}
		
		PhysicalTopologyGeneratorEdge reqg = new PhysicalTopologyGeneratorEdge();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
		reqg.createEdgeTopo(hostSpec, bw, iops, latencyBase, latencyEdge, totaledgenum, linkDict);
		reqg.wrtieJSON(jsonFileName);
	}
	
	private void createEdgeTopo(HostSpec hostSpec, long bw, long swIops, double latencyBase, double latencyEdge, int edgedcnum, Hashtable<String, List<String>> linkDict) {
		Hashtable<String, SwitchSpec> dcnameDict = new Hashtable<>();
		
		for(int i=0; i<edgedcnum; i++) {
			//add edge dc and its gateway
			String edgedcname = "edge"+i;
			HostSpec h = addHost(edgedcname, hostSpec);
			SwitchSpec gw = addSwitch("gw"+i, "edge", bw, swIops);
			dcnameDict.put("gw"+i, gw);
			addLink(h, gw, latencyBase);
			
			//add base station domain for end user allocation
			String baseStationDomain = "base"+i;
			//the "Host" for base station domain should be resource unlimited to simulate the end user's own device
			HostSpec base = addHost(baseStationDomain, hostSpec);
			//add link between base station and edge data centers
			SwitchSpec sw = addSwitch("sw"+i, "edge", bw, swIops);
			addLink(h, sw, latencyBase);
			addLink(base, sw, latencyBase);
		}
		
		for(int i=0; i<edgedcnum; i++) {
			
		}
		
		for(String dc : linkDict.keySet()) {
			//add network link between gateways
			String gwname1 = "gw"+dc;
			SwitchSpec gw1 = dcnameDict.get(gwname1);
			for(String dst:linkDict.get(dc)) {
				String gwname2 = "gw"+dst;
				SwitchSpec gw2 = dcnameDict.get(gwname2);
				addLink(gw1, gw2, latencyEdge);
			}
		}
		
		
	}
	

}
