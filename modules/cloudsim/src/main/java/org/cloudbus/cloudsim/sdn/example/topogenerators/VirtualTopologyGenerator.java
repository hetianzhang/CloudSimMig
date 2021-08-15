/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2015, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.sdn.example.topogenerators;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

import org.cloudbus.cloudsim.sdn.Configuration;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

/**
 * Generate virtual topology Json file from pre-configured VM type sets.
 * VM types are defined in another class - VirtualTopologyGeneratorVmTypes.
 * 
 * @author Jungmin Son
 * @since CloudSimSDN 1.0
 */
public class VirtualTopologyGenerator {
	private List<VMSpec> vms = new ArrayList<VMSpec>();
	private List<LinkSpec> links = new ArrayList<LinkSpec>();
	private List<DummyWorkloadSpec> dummyWorkload = new ArrayList<DummyWorkloadSpec>();
	private List<SFCPolicySpec> policies = new ArrayList<SFCPolicySpec>();
	
	// For test //

	public static void main(String [] argv) {
		VirtualTopologyGenerator vmGenerator = new VirtualTopologyGenerator();
		vmGenerator.generateTestVMs("virtual.test.json");
	}
	
	public void generateTestVMs(String jsonFileName)
	{
		final int groupNum = 4;
		final long flowBw = 100L;

		for(int i=0; i< groupNum; i++) {
			VMSpec v1 = createTestVM(i, 0);
			VMSpec v2 = createTestVM(i, 1);
			addLinkAutoNameBoth(v1, v2, flowBw);
		}
		wrtieJSON(jsonFileName);
	}
		
	public VMSpec createTestVM(int vmGroupId, int vmGroupSubId) {
		String name = "vm";
		int pes = 1;
		long vmStorage = 10;
		long mips=100;
		int vmRam = 256;
		long vmBW=100;

		name += vmGroupId;
		if(vmGroupSubId != -1) {
			name += "-" + vmGroupSubId;
		}

		VMSpec vm = addVM(name, pes, mips, vmRam, vmStorage, vmBW, -1, -1);
		return vm;
	}
	
	// APIs //

	public VMSpec addVM(String name, VMSpec spec) {
		return addVM(name, spec.pe, spec.mips, spec.ram, spec.size, spec.bw, spec.starttime, spec.endtime);
	}
	
	public VMSpec addVM(String name, int pes, long mips, int ram, long storage, long bw, double starttime, double endtime) {
		VMSpec vm = new VMSpec(pes, mips, ram, storage, bw, starttime, endtime);
		vm.name = name;
		
		vms.add(vm);
		return vm;
	}
	
	public SFSpec addSF(String name, int pes, long mips, int ram, long storage, long bw, double starttime, double endtime, long miPerOperation, String type) {
		SFSpec vm = new SFSpec(pes, mips, ram, storage, bw, starttime, endtime, miPerOperation, type);
		vm.name = name;
		
		vms.add(vm);
		return vm;
	}
	
	public SFCPolicySpec addSFCPolicy(String policyname, VMSpec source, VMSpec dest, String linkname, List<SFSpec> sfChain, double expectedTime ) {
		SFCPolicySpec policy = new SFCPolicySpec(policyname, source.name, dest.name, linkname, sfChain, expectedTime);
		policies.add(policy);
		
		return policy;
	}
	private void validateLinkNameDuplicate(String newName) {
		if("default".equals(newName)) 
			return;
		for(LinkSpec link:this.links) {
			if(link.name.equals(newName)) {
				throw new RuntimeException("Same name!"+newName);
			}
		}
		
	}
	public LinkSpec addLink(String linkname, VMSpec source, VMSpec dest, Long bw) {
		validateLinkNameDuplicate(linkname);
		
		LinkSpec link = new LinkSpec(linkname, source.name,dest.name, bw);
		links.add(link);
		
		addWorkload(linkname, source, dest);
		return link;
	}
	
	public void addLinkAutoName(VMSpec src, VMSpec dest, Long bw) {
		String linkName = "default";
		addLink(linkName, src, dest, null);
		
		if(bw != null && bw > 0) {
			linkName = getAutoLinkName(src, dest);
			addLink(linkName, src, dest, bw);
		}
	}
	
	protected static String getAutoLinkName(VMSpec src, VMSpec dest) {
		String linkName = src.name + dest.name;
		return linkName;		
	}
	
	public void addLinkAutoNameBoth(VMSpec vm1, VMSpec vm2, Long linkBw) {
		addLinkAutoName(vm1, vm2, linkBw);
		addLinkAutoName(vm2, vm1, linkBw);
	}
	
	public void addWorkload(String linkname, VMSpec source, VMSpec dest) {
		DummyWorkloadSpec wl = new DummyWorkloadSpec(source.starttime, source.name,dest.name, linkname);
		this.dummyWorkload.add(wl);
	}
	
	public VMSpec createVmSpec(int pe, long mips, int ram, long storage, long bw, double starttime, double endtime) {
		return new VMSpec(pe, mips, ram, storage, bw, starttime, endtime);
	}

	class VMSpec {
		String name;
		String type;
		long size;
		int pe;
		long mips;
		int ram;
		long bw;
		double starttime = -1;
		double endtime = -1;
		
		//migration
		double dirty_rate;
		double mig_deadline;
		
		public VMSpec(int pe, long mips, int ram, long storage, long bw,double starttime,double endtime) {
			this.pe = pe;
			this.mips = mips;
			this.ram = ram;
			this.size = storage;
			this.bw = bw;
			this.type = "vm";
			this.starttime = starttime;
			this.endtime = endtime;
		}
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			VMSpec vm = this;
			JSONObject obj = new JSONObject();
			obj.put("name", vm.name);
			obj.put("pes", vm.pe);
			obj.put("mips", vm.mips);
			obj.put("bw", vm.bw);
			obj.put("size", vm.size);
			obj.put("ram", new Integer(vm.ram));
			obj.put("type", vm.type);
			
			obj.put("dirty_rate", vm.dirty_rate);
			obj.put("mig_deadline", vm.mig_deadline);
			
			if(vm.starttime != -1)
				obj.put("starttime", vm.starttime);
			if(vm.endtime != -1)
				obj.put("endtime", vm.endtime);

			return obj;
		}
	}
	
	class SFSpec extends VMSpec {
		long mipsPerOperation;

		public SFSpec(int pe, long mips, int ram, long storage, long bw, double starttime, double endtime, long mipOper, String type) {
			super(pe, mips, ram, storage, bw, starttime, endtime);
			this.mipsPerOperation = mipOper;
			this.type = type;
		}
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			JSONObject obj = super.toJSON();
			obj.put("mipoper", this.mipsPerOperation);
			return obj;
		}
	}
	
	class SFCPolicySpec {
		String name;
		String source;
		String destination;
		String linkname;
		List<SFSpec> sfChain;
		double expectTime;
		
		public SFCPolicySpec(String name,String source,String destination,String linkname, List<SFSpec> sfChain, double expectedTime) {
			this.name = name;
			this.source = source;
			this.destination = destination;
			this.linkname = linkname;
			this.sfChain = sfChain;
			this.expectTime = expectedTime;
		}
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			SFCPolicySpec policy = this;
			JSONObject obj = new JSONObject();
			obj.put("name", policy.name);
			obj.put("source", policy.source);
			obj.put("destination", policy.destination);
			obj.put("flowname", policy.linkname);
			obj.put("expected_time", policy.expectTime);
			
			JSONArray jsonChain = new JSONArray();
			for(SFSpec sf:sfChain) {
				jsonChain.add(sf.name);
			}
			obj.put("sfc", jsonChain);
			return obj;
		}

	}

	class DummyWorkloadSpec {
		double startTime;
		String source;
		String linkname;
		String destination;
		
		public DummyWorkloadSpec(double startTime, String source,String destination,String linkname) {
			this.linkname = linkname;
			this.source = source;
			this.destination = destination;
			this.startTime = startTime;
		}
		public String toString() {
			String st = startTime+ ","+ source + ",0,1,"+linkname+","+destination + ",1000000000000000,1";
			return st;
		}
	}
	class LinkSpec {
		String name;
		String source;
		String destination;
		Long bw;
		
		public LinkSpec(String name,String source,String destination,Long bw) {
			this.name = name;
			this.source = source;
			this.destination = destination;
			this.bw = bw;
		}
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			LinkSpec link = this;
			JSONObject obj = new JSONObject();
			obj.put("name", link.name);
			obj.put("source", link.source);
			obj.put("destination", link.destination);
			if(link.bw != null)
				obj.put("bandwidth", link.bw);
			return obj;
		}
	}
	
	int vmId = 0;
	final int SEED = 10;
	
	@SuppressWarnings("unchecked")
	public void wrtieJSON(String jsonFileName) {
		JSONObject obj = new JSONObject();

		JSONArray vmList = new JSONArray();
		JSONArray linkList = new JSONArray();
		
		// Place VM in random order
		if(Configuration.virtualTopoVmRandomOrder) {
		ArrayList<Integer> indexes = new ArrayList<Integer>();
		for(int i=0; i<vms.size();i++)
			indexes.add(i);
		Collections.shuffle(indexes, new Random(SEED));		

		for(Integer i:indexes) {
			VMSpec vm = vms.get(i);
			vmList.add(vm.toJSON());
		}
		
		
		// Shuffle virtual link order
		indexes = new ArrayList<Integer>();
		for(int i=0; i<links.size();i++)
			indexes.add(i);
		Collections.shuffle(indexes, new Random(SEED));		

		for(Integer i:indexes) {
			LinkSpec link = links.get(i);
			linkList.add(link.toJSON());
		}
		}else {
			for(int i=0; i<vms.size(); i++) {
				VMSpec vm = vms.get(i);
				vmList.add(vm.toJSON());
			}
			for(int i =0; i< links.size(); i++) {
				LinkSpec link = links.get(i);
				linkList.add(link.toJSON());
			}
		}
		
		/*
		for(LinkSpec link:links) {
			linkList.add(link.toJSON());
		}
		*/
		
		obj.put("nodes", vmList);
		obj.put("links", linkList);
		
		// Add SFC Policies to the json.
		if(policies.size() != 0) {
			JSONArray policyList = new JSONArray();
			for(SFCPolicySpec policy:policies) {
				policyList.add(policy.toJSON());
			}
			obj.put("policies", policyList);
		}
		
		try {
			if(jsonFileName.contains("\\")) {
				File jsonFile = new File(jsonFileName);
				jsonFile.getParentFile().mkdir();
				FileWriter file = new FileWriter(jsonFile);
				file.write(obj.toJSONString().replaceAll(",", ",\n"));
				file.flush();
				file.close();
			}else {
				FileWriter file = new FileWriter(jsonFileName);
				file.write(obj.toJSONString().replaceAll(",", ",\n"));
				file.flush();
				file.close();
			}
			
	 
		} catch (IOException e) {
			e.printStackTrace();
		}
	 
		System.out.println(obj);
		
		System.out.println("===============WORKLOAD=============");
		System.out.println("start, source, z, w1, link, dest, psize, w2");
		for(DummyWorkloadSpec wl:this.dummyWorkload) {
			System.out.println(wl);
		}
	}
}
