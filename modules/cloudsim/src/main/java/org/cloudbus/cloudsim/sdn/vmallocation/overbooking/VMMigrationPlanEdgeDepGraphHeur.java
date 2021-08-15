package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

public class VMMigrationPlanEdgeDepGraphHeur extends VMMigrationPlanEdge{
	
	private List<Memcopying> inMigrationList;
	private Map<String, Double> depGraphSrcDstNodeWeightMap = new HashMap<>();
	private Graph<String, DefaultEdge> depGraphSrcDstCopy;
	
	public VMMigrationPlanEdgeDepGraphHeur(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping,
			SDNDatacenter d) {
		super(vmGroups, migrationMapping, d);
		// TODO Auto-generated constructor stub
		setInMigrationList(new ArrayList<Memcopying>());
		setInMigrationList(d.getPlannedMigrationList());
		
	}
	
	@Override
	public void processMigrationPlan() {
		//initialize depGraph
		depGraphSrcDst = new DefaultUndirectedGraph<>(DefaultEdge.class);
		//GWIN algorithm
		//1. build migration dependency graph
		this.migFeasible = new LinkedList<>(this.migWaiting);
		this.migFeasible.addAll(getInMigrationList());
		
		long startT = System.nanoTime();
		this.createDepGraphSrcDst(this.migFeasible);
		
		this.depGraphSrcDstCopy = this.copyGraphSrcDst();
		this.planTimes++;
		
		
		//2. calculate the MIS
		int groupId = 0;
		this.updateSrcDstNodeWeight();
		while(this.depGraphSrcDst.vertexSet().size()!=0) {
			List<String> mis = getMISDepGraphSrcDst();
			//3.1 do it again based on estimate execution time
					
				
			//3.2 OR do it again by deducting the previous MIS group (migration nodes remain if there are
			//    still some migration in the src-dst pair list)
			//depGraphSrcDst is updated in getMigrationFromMIS()
			List<Memcopying> migGroup = this.getMigrationFromMIS(mis);
			this.migFeasible.removeAll(migGroup);
			this.updateSrcDstNodeWeight();
					
			//4. get the scheduling sequence
			LinkedList<Memcopying> linkList = new LinkedList<>(migGroup);
			this.migPlanMap.put(groupId, linkList);
			//the sequence of group Id only list
			getMigrationPlanList().add(groupId);
			groupId +=1;
		}
		
		long endT = System.nanoTime();
		long runT = (long) this.getRunTime();
		this.setRunTime(runT + endT- startT);
	}
	
	/*
	 * (non-Javadoc)
	 * @see org.cloudbus.cloudsim.sdn.vmallocation.overbooking.VMMigrationPlanIterMIS#updateMigrationWeight(java.util.List)
	 */
	@Override
	public void updateMigrationWeight(List<Memcopying> feasibleMigList) {
		for(Memcopying mig:feasibleMigList) {
			double bwSrc = dataCenter.findHost(mig.getSrcHost()).getAvailableBandwidth();
			double bwDst = dataCenter.findHost(mig.getDstHost()).getAvailableBandwidth();
			double migTime = this.getMigrationTime(mig, Math.min(bwSrc, bwDst));
			migWeightMap.put(mig, migTime);
		}
	}
	
	public double getMigrationWeight(Memcopying mig) {
		double weight;
		double atime = mig.getArrivalTime();
		double deadline = mig.getDeadline();
		double slackTime = atime+deadline - CloudSim.clock();
		if(deadline>1000)
			deadline = 1000;
		if(Math.abs(slackTime) <1) {
			weight = 100;
		}else {
			if(slackTime >1)
				weight = 100/slackTime;
			else {
				weight = 100*Math.abs(slackTime);
			}
		}
		return weight;
	}
	
	private void updateSrcDstNodeWeight() {
		this.updateMigrationWeight(this.migFeasible);
		for(String srcdstNode:this.depGraphSrcDst.vertexSet()) {
			//Memcopying minMig = getMinMigrationFromSrcDstList(srcdstNode);
			Memcopying minMig = getLeastSlackTimeMigrationFromSrcDstList(srcdstNode);
			double migTime = this.migWeightMap.get(minMig);
			//slack time
			double weight = this.getMigrationWeight(minMig);
			this.depGraphSrcDstNodeWeightMap.put(srcdstNode, weight);
			//double slackTime = minMig.getArrivalTime()+minMig.getDeadline()+minMig.getArrivalTime() - (CloudSim.clock()+migTime);
			
			//if(slackTime < 0)
			//	this.depGraphSrcDstNodeWeightMap.put(srcdstNode, 1000.0);
			//else
			//	this.depGraphSrcDstNodeWeightMap.put(srcdstNode, 100/slackTime);
			//this.depGraphSrcDstNodeWeightMap.put(srcdstNode, 1/migTime);
		}
	}
	
	private Memcopying getLeastSlackTimeMigrationFromSrcDstList(String srcdstNode) {
		List<Memcopying> srcdstList = this.srcDstPairListMap.get(srcdstNode);
		double minWeight = Double.MAX_VALUE;
		Memcopying minMig = null;
		for(Memcopying mig:srcdstList) {
			double migTime = migWeightMap.get(mig);
			double weight = mig.getArrivalTime()+mig.getDeadline() - (CloudSim.clock()+migTime);
			if(weight < minWeight) {
				minWeight = weight;
				minMig = mig;
			}
		}
		return minMig;
	}
	
	private Memcopying getEarlistDeadlineMigrationFromSrcDstList(String srcdstNode) {
		List<Memcopying> srcdstList = this.srcDstPairListMap.get(srcdstNode);
		double minWeight = Double.MAX_VALUE;
		Memcopying minMig = null;
		for(Memcopying mig:srcdstList) {
			double weight = mig.getDeadline();
			if(weight < minWeight) {
				minWeight = weight;
				minMig = mig;
			}
		}
		return minMig;
	}
	
	private String selectSrcDstNodeGGWMIN(Graph<String, DefaultEdge> graph){
		String selectNode = null;
		for(String node:graph.vertexSet()) {
			List<String> neighborList = Graphs.neighborListOf(graph,  node);
			double score = 0;
			for(String nei:neighborList) {
				double weight = this.depGraphSrcDstNodeWeightMap.get(nei);
				double degree = Graphs.neighborListOf(graph, nei).size();
				score += weight / (degree +1);
			}
			double vweight = this.depGraphSrcDstNodeWeightMap.get(node);
			if(score <= vweight) {
				selectNode = node;
				break;
			}
		}
		return selectNode;
	}
	
	private String selectSrcDstNodeWMIN(Graph<String, DefaultEdge> graph) {
		String selectNode = null;
		double maxScore = Double.NEGATIVE_INFINITY;
		for(String node:graph.vertexSet()) {
			double weight = this.depGraphSrcDstNodeWeightMap.get(node); 
			double neighDegree = Graphs.neighborListOf(graph, node).size();
			double score = weight/(neighDegree+1);
			if(score > maxScore) {
				maxScore = score;
				selectNode = node;
			}
		}
		return selectNode;
	}
	
	private List<String> getMISDepGraphSrcDst(){
		List<String> mis = new ArrayList<>();
		Graph<String, DefaultEdge> copy = this.copyGraphSrcDst();
		int i =0;
		while(copy.vertexSet().size()!=0) {
			String selectNode = selectSrcDstNodeWMIN(copy);
			mis.add(selectNode);
			List<String> neighborList = Graphs.neighborListOf(copy, selectNode);
			neighborList.add(selectNode);
			copy.removeAllVertices(neighborList);
			i = i + 1;
		}
		return mis;
	}
	
	
	public List<Set<String>> getAllIterMISDepGraphSrcDst(){
		List<Set<String>> misList = new ArrayList<>();
		return misList;
	}
	
	
	public List<Memcopying> getMigrationFromMIS(List<String> mis){
        List<Memcopying> migMIS = new ArrayList<>();
       for(String node:mis) {
    	   Memcopying mig = this.getMinMigrationFromSrcDstList(node);
    	   migMIS.add(mig);
    	   List<Memcopying> pairMigList = this.srcDstPairListMap.get(node);
    	   pairMigList.remove(mig);
    	   if(pairMigList.size()==0)
    		   this.depGraphSrcDst.removeVertex(node);
       }
       
       //update pair Map and depGraphSrcDst
       return migMIS;
    }
	
	
	
	public boolean checkIndepScheduling(Memcopying a, Memcopying b) {
		boolean flag = true;
		String nodea = a.getSrcHost()+"-"+a.getDstHost();
		String nodeb = b.getSrcHost()+"-"+b.getDstHost();
		if(this.depGraphSrcDstCopy.containsEdge(nodea, nodeb))
			flag = false;
		return flag;
	}
	
	public List<Memcopying> getInMigrationList() {
		return inMigrationList;
	}

	public void setInMigrationList(List<Memcopying> inMigrationList) {
		this.inMigrationList = inMigrationList;
	}
	
	

}
