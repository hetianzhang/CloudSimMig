package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.VmGroup;
import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.clique.BronKerboschCliqueFinder;
import org.jgrapht.graph.DefaultEdge;

/*
 * heuristic algorithm to find the maximum (weighted) independent set
 * of the dependency Graph of migration src-dst nodes
 * 
 */
public class VMMigrationPlanGWIN extends VMMigrationPlanIterMIS{
	
	private Map<String, Double> depGraphSrcDstNodeWeightMap = new HashMap<>();
	private Graph<String, DefaultEdge> depGraphSrcDstCopy;
	
	public VMMigrationPlanGWIN(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter d) {
		super(vmGroups, migrationMapping, d);
	}

	@Override
	public void processMigrationPlan() {
		// TODO Auto-generated method stub
		//1. create dependency graph
		this.migFeasible = new LinkedList<>(this.migWaiting);
		this.createDepGraphSrcDst(migFeasible);
		this.depGraphSrcDstCopy = this.copyGraphSrcDst();
		//2. calculate the MIS
		int groupId = 0;
		this.updateSrcDstNodeWeight();
		while(this.depGraphSrcDst.vertexSet().size()!=0) {
			List<String> mis = getMISDepGraphSrcDst();
			//3.1 do it again based on estimate execution time
			
		
			//3.2 OR do it again by deducting the previous MIS group (migration nodes remain if there are
			//    still some migration in the src-dst pair list)
			//depGraphSrcDst is updated in this function
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
		
		
	}
	
	private void updateSrcDstNodeWeight() {
		this.updateMigrationWeight(this.migFeasible);
		for(String srcdstNode:this.depGraphSrcDst.vertexSet()) {
			Memcopying minMig = getMinMigrationFromSrcDstList(srcdstNode);
			double migTime = this.migWeightMap.get(minMig);
			this.depGraphSrcDstNodeWeightMap.put(srcdstNode, 1/migTime);
		}
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
		double maxScore = 0;
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
	
	@Override
	public boolean checkHost(Memcopying finish, Memcopying check) {
		// return true if sharing hosts
		if(finish.getSrcHost() == check.getSrcHost() || finish.getDstHost() == check.getDstHost())
			return true;
		return false;
	}

	@Override
	public boolean checkPath(Memcopying finish, Memcopying check) {
		// TODO Auto-generated method stub
		return false;
	}

	@Override
	public void printPlan() {
		// TODO Auto-generated method stub
		
	}
	
}
