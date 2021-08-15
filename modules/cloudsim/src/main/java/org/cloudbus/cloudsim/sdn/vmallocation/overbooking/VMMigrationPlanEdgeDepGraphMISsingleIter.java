package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

public class VMMigrationPlanEdgeDepGraphMISsingleIter extends VMMigrationPlanEdge{
	private List<Memcopying> inMigrationList;
	private Map<String, Double> depGraphSrcDstNodeWeightMap = new HashMap<>();
	private Graph<String, DefaultEdge> depGraphSrcDstCopy;
	
	public VMMigrationPlanEdgeDepGraphMISsingleIter(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping,
			SDNDatacenter d) {
		super(vmGroups, migrationMapping, d);
		// TODO Auto-generated constructor stub
		setInMigrationList(new ArrayList<Memcopying>());
		setInMigrationList(d.getPlannedMigrationList());
		
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
	
	@Override
	public void processMigrationPlan() {
		//initialize depGraph
		
		depGraphSrcDst = new DefaultUndirectedGraph<>(DefaultEdge.class);
		//GWIN algorithm
		//1. build migration dependency graph
		this.migFeasible = new LinkedList<>(this.migWaiting);
		this.migFeasible.addAll(getInMigrationList());
		this.createDepGraphSrcDst(this.migFeasible);
		
		this.depGraphSrcDstCopy = this.copyGraphSrcDst();
		this.planTimes++;
		long startT = System.nanoTime();
		
		//2. calculate the MIS
		int groupId = 0;
		List<Set<String>> allCliquesList = this.getAllCliquesDepGraphSrcDst();
		List<Set<Memcopying>> allIndepSetList = this.getIterMISFromCliques(allCliquesList);
		for(Set<Memcopying> mis:allIndepSetList) {
			LinkedList<Memcopying> linkList = new LinkedList<>(mis);
			this.migPlanMap.put(groupId, linkList);
			//the sequence of group Id only list
			getMigrationPlanList().add(groupId);
			groupId +=1;
		}
		
		/*
		groupId = 0;
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
		*/
		
		long endT = System.nanoTime();
		long runT = (long) this.getRunTime();
		this.setRunTime(runT + endT- startT);
	}
	
	private Memcopying getLeastSlackTimeMigration(List<Memcopying> migList) {
		List<Memcopying> srcdstList = migList;
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
	
	
	private Set<String> getMaxClique(List<Set<String>> allCliquesList){
		int maxSize = 0;
		Set<String> maxC = null;
		for(Set<String> c:allCliquesList) {
			if(c.size()>maxSize) {
				maxSize = c.size();
				maxC = c;
			}
		}
		return maxC;
	}
	
	private void removeClique(List<Set<String>> allCliquesList, Set<String> maxClique) {
		List<Set<String>> allCliquesListTemp = new ArrayList<>(allCliquesList);
		for(Set<String> c:allCliquesListTemp) {
			c.removeAll(maxClique);
			if(c.size() == 0)
				allCliquesList.remove(c);
		}
		//for(String node:maxClique) {
			//List<Memcopying> pairMigList = this.srcDstPairListMap.get(node);
		//}
	}
	
	public List<Set<Memcopying>> getIterMISFromCliques_v1(List<Set<String>> allCliquesList){
		List<Set<Memcopying>> allIndepSetList = new ArrayList<>();
		List<Set<String>> allIterCliquesList = new ArrayList<>();
		while(allCliquesList.size() != 0) {
			Set<String> maxC = this.getMaxClique(allCliquesList);
			allIterCliquesList.add(maxC);
			this.removeClique(allCliquesList, maxC);
		}
		
		for(Set<String> clique:allIterCliquesList) {
			Map<String, Double> nodeDegreeMap = new HashMap<>();
			for(String srcdstNode:clique) {
				//add degree of the node
				//int degree = depGraphSrcDst.degreeOf(srcdstNode);
				int degree = 0;
				//or out degree of the clique for the node
				Memcopying minMig = getLeastSlackTimeMigrationFromSrcDstList(srcdstNode);
				
				for(String neighbor: Graphs.neighborListOf(depGraphSrcDst, srcdstNode)) {
					if(!clique.contains(neighbor)) {
						degree++;
					}
				}
				
				nodeDegreeMap.put(srcdstNode, this.getMigrationWeight(minMig)*degree);
			}
			Map<String, Double> sortedDegreeMap = MapUtil.sortByValue(nodeDegreeMap);
			clique = sortedDegreeMap.keySet();
		}
		//2. get MIS from cliques
		int totalMigNum = this.migFeasible.size();
		while(totalMigNum !=0) {
			Set<Memcopying> migMIS = new HashSet<>();
			for(Set<String> sortClique:allCliquesList) {				
				String selectedSrcDst = null;
				for(String node:sortClique) {
					if(migMIS.size()==0) {
						selectedSrcDst = node;
						break;
					}else {
						 if(checkDependencyGroup(node, migMIS)) {
							 selectedSrcDst = node;
							 break;
						 }else {
							 continue;
						 }
					}
				}
				//there is srcdst-pair node suitable for the indep set
				if(selectedSrcDst != null) {
					List<Memcopying> srcdstMigList = this.srcDstPairListMap.get(selectedSrcDst);
					 if(srcdstMigList.size()!=0) {
						 //Memcopying selectedMig = srcdstMigList.get(0);
						 Memcopying selectedMig = this.getLeastSlackTimeMigrationFromSrcDstList(selectedSrcDst);
						 migMIS.add(selectedMig);
						 totalMigNum --;
						 srcdstMigList.remove(0);
					 }
					 //delete srcdstNode in depGraphSrcDst if its list is empty
					 if(srcdstMigList.size()==0) {
						 depGraphSrcDst.removeVertex(selectedSrcDst);
						 //remove vertex from cliques 
						 sortClique.remove(selectedSrcDst);
					 }
				}	
			}
			//migMIS for this round is finished
			allIndepSetList.add(migMIS);
			
		}
		
		return allIndepSetList;
	}
	
	/**
	 * get all maximum independent of migration iteratively at once
	 * @param allCliquesList
	 * @return
	 */
	public List<Set<Memcopying>> getIterMISFromCliques(List<Set<String>> allCliquesList){
		List<Set<Memcopying>> allIndepSetList = new ArrayList<>();
		//1. sort all nodes in the cliques first
		this.updateSrcDstNodeWeight();
		for(Set<String> clique:allCliquesList) {
			Map<String, Double> nodeDegreeMap = new HashMap<>();
			for(String srcdstNode:clique) {
				//add degree of the node
				//int degree = depGraphSrcDst.degreeOf(srcdstNode);
				int degree = 0;
				//or out degree of the clique for the node
				Memcopying minMig = getLeastSlackTimeMigrationFromSrcDstList(srcdstNode);
				
				for(String neighbor: Graphs.neighborListOf(depGraphSrcDst, srcdstNode)) {
					if(!clique.contains(neighbor)) {
						degree++;
					}
				}
				
				nodeDegreeMap.put(srcdstNode, this.getMigrationWeight(minMig)*degree);
			}
			Map<String, Double> sortedDegreeMap = MapUtil.sortByValue(nodeDegreeMap);
			clique = sortedDegreeMap.keySet();
		}
		//2. get MIS from cliques
		int totalMigNum = this.migFeasible.size();
		while(totalMigNum !=0) {
			Set<Memcopying> migMIS = new HashSet<>();
			for(Set<String> sortClique:allCliquesList) {				
				String selectedSrcDst = null;
				for(String node:sortClique) {
					if(migMIS.size()==0) {
						selectedSrcDst = node;
						break;
					}else {
						 if(checkDependencyGroup(node, migMIS)) {
							 selectedSrcDst = node;
							 break;
						 }else {
							 continue;
						 }
					}
				}
				//there is srcdst-pair node suitable for the indep set
				if(selectedSrcDst != null) {
					List<Memcopying> srcdstMigList = this.srcDstPairListMap.get(selectedSrcDst);
					 if(srcdstMigList.size()!=0) {
						 //Memcopying selectedMig = srcdstMigList.get(0);
						 Memcopying selectedMig = this.getLeastSlackTimeMigrationFromSrcDstList(selectedSrcDst);
						 migMIS.add(selectedMig);
						 totalMigNum --;
						 srcdstMigList.remove(0);
					 }
					 //delete srcdstNode in depGraphSrcDst if its list is empty
					 if(srcdstMigList.size()==0) {
						 depGraphSrcDst.removeVertex(selectedSrcDst);
						 //remove vertex from cliques 
						 sortClique.remove(selectedSrcDst);
					 }
				}	
			}
			//migMIS for this round is finished
			allIndepSetList.add(migMIS);
			
		}
		return allIndepSetList;
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
	
	private void updateSrcDstNodeWeight() {
		this.updateMigrationWeight(this.migFeasible);
		for(String srcdstNode:this.depGraphSrcDst.vertexSet()) {
			//Memcopying minMig = getMinMigrationFromSrcDstList(srcdstNode);
			Memcopying minMig = getLeastSlackTimeMigrationFromSrcDstList(srcdstNode);
			//double migTime = this.migWeightMap.get(minMig);
			//slack time
			//double slackTime = minMig.getArrivalTime()+minMig.getDeadline() - (CloudSim.clock()+migTime);
			this.depGraphSrcDstNodeWeightMap.put(srcdstNode, this.getMigrationWeight(minMig));
			//this.depGraphSrcDstNodeWeightMap.put(srcdstNode, 1/migTime);
		}
	}
	
	private Memcopying getEarlistDeadlineMigrationFromSrcDstList(String srcdstNode) {
		List<Memcopying> srcdstList = this.srcDstPairListMap.get(srcdstNode);
		double minWeight = Double.MAX_VALUE;
		Memcopying minMig = null;
		for(Memcopying mig:srcdstList) {
			double weight = mig.getArrivalTime()+mig.getDeadline();
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
