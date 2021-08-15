package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.VmGroup;

import org.jgrapht.Graph;
import org.jgrapht.Graphs;
import org.jgrapht.alg.clique.BronKerboschCliqueFinder;
import org.jgrapht.graph.AbstractBaseGraph;
import org.jgrapht.graph.DefaultEdge;
import org.jgrapht.graph.DefaultUndirectedGraph;

import com.google.common.graph.EndpointPair;

public class VMMigrationPlanIterMIS extends VMMigrationPlanSLAGroup{
	
	//depGraph based on src-dst not migId
	protected int planTimes;
	protected Graph<String, DefaultEdge> depGraphSrcDst;
	//the migration list of same src-dst pair
	protected HashMap<String, List<Memcopying>> srcDstPairListMap;
	
	VMMigrationPlanIterMIS(Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter d) {
		super(vmGroups, migrationMapping, d);
		
		this.planTimes = 0;
		this.migNum = 0; //initialize migration number for id once here
		depGraphSrcDst = new DefaultUndirectedGraph<>(DefaultEdge.class);
		srcDstPairListMap = new HashMap<>();
	}

	@Override
	public void processMigrationPlan() {
		this.planTimes++;
		long startT = System.nanoTime();
		// TODO Auto-generated method stub
		//1. create depGraph
		this.migFeasible = new LinkedList<>(this.migWaiting);
		this.createDepGraphSrcDst(migFeasible);
		
		//2. calculate all Cliques
		List<Set<String>> allCliquesList = this.getAllCliquesDepGraphSrcDst();
		//3. TODO calculate Maximum Independent Set in both iter and single version based on all maximal cliques
		
		
		//4.1 calculae the Maximum Independent Set again with current running migration src-dst pair
		//4.2 OR iteratively calculate the Maximum Independent Set of remaing migrations
		long endT = System.nanoTime();
		long runT = (long) this.getRunTime();
		this.setRunTime(runT + endT- startT);
	}
	
	/*
	 * get all MIS of depgraph based on all maximal cliques list iteratively
	 */
	public List<Set<String>> getAllMISIter(List<Set<String>> allCliquesList){
		List<Set<String>> allMIS = new ArrayList<>();
		return allMIS;
	}
	
	/*
	 * get all MIS of depgraph based on all maximal cliques list in single round
	 */
	public List<Set<String>> getAllMISSingle(List<Set<String>> allCliquesList){
		List<Set<String>> allMIS = new ArrayList<>();
		return allMIS;
	}
	
	public List<Map<String, Object>> readVmPlacement(String filePath) {
		//time, vmId, srcHostId (placement)
		List<Map<String, Object>> placementList = new ArrayList<>();
		try(BufferedReader r = new BufferedReader(new FileReader(filePath))){
			String line;
			while((line = r.readLine())!=null) {
				line.replace("\n", "");
				String[] values = line.split(",");
				double time = Double.valueOf(values[0]);
				int vmId = Integer.valueOf(values[1]);
				int srcHostId = Integer.valueOf(values[2]);
				System.out.println("time: "+time+" vmId: "+ vmId + " srcId: "+srcHostId);
				Map<String, Object> placement = new HashMap<>();
				placement.put("vm", this.dataCenter.findVm(vmId));
				placement.put("src", this.dataCenter.findHost(srcHostId));
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return placementList;
	}
	
	public List<Map<String, Object>> readMigrationDestination(String filePath, double currentTime){
		List<Map<String, Object>> migrationList = new ArrayList<>();
		try(BufferedReader r = new BufferedReader(new FileReader(filePath))){
			String line;
			while((line = r.readLine())!=null) {
				String[] values = line.split(",");
				int vmId = Integer.valueOf(values[0]);
				double time = Double.valueOf(values[1]);
				if(time != currentTime)
					break;
				int srcId = Integer.valueOf(values[2]);
				int dstId = Integer.valueOf(values[3]);
		        //records.add(Arrays.asList(values));			
		        Map<String, Object> migration = new HashMap<>();
		        migration.put("vm", this.dataCenter.findVm(vmId));
		        migration.put("host", this.dataCenter.findHost(dstId));
		        migrationList.add(migration);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return migrationList;
	}
	
	public List<Map<String, Object>> readMigrationDestination(String filePath) {
		List<Map<String, Object>> migrationList = new ArrayList<>();
		//vmId, time, srcHostId, dstHostId, MemorySize
		//List<List<String>> records = new ArrayList<>();
		try(BufferedReader r = new BufferedReader(new FileReader(filePath))){
			String line;
			while((line = r.readLine())!=null) {
				String[] values = line.split(",");
				int vmId = Integer.valueOf(values[0]);
				double time = Double.valueOf(values[1]);
				int srcId = Integer.valueOf(values[2]);
				int dstId = Integer.valueOf(values[3]);
		        //records.add(Arrays.asList(values));
		        Map<String, Object> migration = new HashMap<>();
		        migration.put("vm", this.dataCenter.findVm(vmId));
		        migration.put("host", this.dataCenter.findHost(dstId));
		        migrationList.add(migration);
			}
		} catch (FileNotFoundException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		return migrationList;
	}
	
	public boolean checkDependencyGroup(String checkSrcDst, Set<Memcopying> migMISList) {
		for(Memcopying mig:migMISList) {
			String migSrcDst = mig.getSrcHost() + "-"+ mig.getDstHost();
			if(depGraphSrcDst.containsEdge(checkSrcDst, migSrcDst)) {
				return false;
			}
		}
		return true;
	}
			
	public boolean checkDependencyGroup(String checkSrcDst, List<Memcopying> migMISList) {
		for(Memcopying mig:migMISList) {
			String migSrcDst = mig.getSrcHost() + "-"+ mig.getDstHost();
			if(depGraphSrcDst.containsEdge(checkSrcDst, migSrcDst)) {
				return false;
			}
		}
		return true;
	}
	
	public boolean checkDependencyGroup(Memcopying migCheck, List<Memcopying> migMISList) {
		String checkSrcDst = migCheck.getSrcHost() +"-"+ migCheck.getDstHost();
		for(Memcopying mig:migMISList) {
			String migSrcDst = mig.getSrcHost() + "-"+ mig.getDstHost();
			if(depGraphSrcDst.containsEdge(checkSrcDst, migSrcDst)) {
				return false;
			}
		}
		return true;
	}
	
	
	public Graph<Integer, DefaultEdge> copyGraph(){
		Graph<Integer, DefaultEdge> copyGraph = new DefaultUndirectedGraph<>(DefaultEdge.class);
		for(Integer node:depGraph.nodes()) {
			copyGraph.addVertex(node);
		}
		for(EndpointPair<Integer> e:depGraph.edges()) {
			copyGraph.addEdge(e.source(), e.target());
		}
		return copyGraph;
	}
	
	public Graph<String, DefaultEdge> copyGraphSrcDst(){
		Graph<String, DefaultEdge> copyGraph = new DefaultUndirectedGraph<>(DefaultEdge.class);
		for(String node:depGraphSrcDst.vertexSet()) {
			copyGraph.addVertex(node);
		}
		for(DefaultEdge e:depGraphSrcDst.edgeSet()) {
			String src = depGraphSrcDst.getEdgeSource(e);
			String dst = depGraphSrcDst.getEdgeTarget(e);
			copyGraph.addEdge(src, dst);
		}
		return copyGraph;
	}
	
	Map<Memcopying, Double> migWeightMap = new HashMap<>();
	
	public void updateMigrationWeight(List<Memcopying> feasibleMigList) {
		for(Memcopying mig:feasibleMigList) {
			double bwSrc = dataCenter.findHost(mig.getSrcHost()).getAvailableBandwidth();
			double bwDst = dataCenter.findHost(mig.getDstHost()).getAvailableBandwidth();
			double migTime = this.getMigrationTime(mig, Math.min(bwSrc, bwDst));
			migWeightMap.put(mig, migTime);
		}
	}
	
	protected Memcopying getMinMigrationFromSrcDstList(String srcdstNode) {
		List<Memcopying> srcdstList = this.srcDstPairListMap.get(srcdstNode);
		double minWeight = Double.MAX_VALUE;
		Memcopying minMig = null;
		for(Memcopying mig:srcdstList) {
			double weight = migWeightMap.get(mig);
			if(weight < minWeight) {
				minWeight = weight;
				minMig = mig;
			}
		}
		return minMig;
	}
	
	/**
	 * get the map of id to memcopying
	 * @param migFeasibleList
	 * @return
	 */
	public Map<Integer, Memcopying> getIdtoMemcopyingMap(List<Memcopying> migFeasibleList){
		Map<Integer, Memcopying> idtoMemcopyingMap = new HashMap<>();
		for(Memcopying mig:migFeasibleList) {
			idtoMemcopyingMap.put(mig.getId(), mig);
		}
		return idtoMemcopyingMap;
	}
	
	/**
	 * get the maximum independent set based on the heuristic algorithm from depGraph
	 * @return
	 */
	public Set<Memcopying> getSingleMISHeuristicGWIN(){
		Map<Integer,Memcopying> idtoMemcopyingMap = getIdtoMemcopyingMap(this.migFeasible);
		Set<Memcopying> maximumIndepSet = new HashSet();
		updateMigrationWeight(this.migFeasible);
		Graph<Integer, DefaultEdge> copyGraph = copyGraph();
		int nodeLeft = depGraph.nodes().size();
		while(nodeLeft != 0) {
			double maxScore = 0;
			int selectMigId = -1;
			Memcopying selectMig = null;
			for(Integer n:depGraph.nodes()) {
				Memcopying mig = idtoMemcopyingMap.get(n);
				double weight = this.migWeightMap.get(mig);
				List<Integer> neighborList = Graphs.neighborListOf(copyGraph, n);
				double score = 1/((neighborList.size()+1)*weight);
				if(score > maxScore) {
					maxScore = score;
					selectMigId = n;
					selectMig = mig;
				}
			}
			if(selectMig != null) {
				maximumIndepSet.add(selectMig);
				copyGraph.removeVertex(selectMigId);
				List<Integer> neighborList = Graphs.neighborListOf(copyGraph,selectMigId);
				copyGraph.removeAllVertices(neighborList);
				nodeLeft = copyGraph.vertexSet().size();
			}
			
		}
		return maximumIndepSet;
	}
	
	/**
	 * get the maximum independent set based on the heuristic GWIN algorithm from depGraphSrcDst
	 * @return
	 */
	public Set<Memcopying> getSingleMISHeuristicGWINSrcDst(){
		updateMigrationWeight(this.migFeasible);
		Set<Memcopying> maximumIndepSet = new HashSet<>();
		Graph<String, DefaultEdge> copyGraph = copyGraphSrcDst();
		int nodeLeft = depGraphSrcDst.vertexSet().size();
		while(nodeLeft!=0) {
			double maxScore = 0;
			String selectSrcDstNode = null;
			Memcopying selectMig = null;
			for(String node:copyGraph.vertexSet()) {
				Memcopying minMig = this.getMinMigrationFromSrcDstList(node);
				double weight = this.migWeightMap.get(minMig);
				List<String> neighborList = Graphs.neighborListOf(copyGraph, node);
				double score = 1/((neighborList.size()+1)*weight);
				if(score > maxScore) {
					selectSrcDstNode = node;
					selectMig = minMig;
					maxScore = score;
				}
			}
			if(selectMig != null && selectSrcDstNode != null) {
				maximumIndepSet.add(selectMig);
				copyGraph.removeVertex(selectSrcDstNode);
				List<String> srcdstNeighborList = Graphs.neighborListOf(copyGraph, selectSrcDstNode);
				copyGraph.removeAllVertices(srcdstNeighborList);
			}
			nodeLeft = copyGraph.vertexSet().size();
		}
		return maximumIndepSet;
	}
	
	
	/**
	 * get the maximum independent set of the remaining migrations (memcopying) at current time
	 * @param allCliquesList for the remaining maximum cliques
	 * @return
	 */
	public Set<Memcopying> getSingleMISFromCliques(List<Set<String>> allCliquesList){
		Set<Memcopying> maximumIndepSet = new HashSet<>();
		for(Set<String> clique:allCliquesList) {
			Map<String, Integer> nodeDegreeMap = new HashMap<>();
			for(String srcdstNode:clique) {
				//add degree of the node
				//int degree = depGraphSrcDst.degreeOf(srcdstNode);
				int degree = 0;
				//or out degree of the clique for the node
				for(String neighbor: Graphs.neighborListOf(depGraphSrcDst, srcdstNode)) {
					if(!clique.contains(neighbor)) {
						degree++;
					}
				}
				nodeDegreeMap.put(srcdstNode, degree);
			}
			Map<String, Integer> sortedDegreeMap = MapUtil.sortByValue(nodeDegreeMap);
			clique = sortedDegreeMap.keySet();
		}
		
		for(Set<String> sortClique:allCliquesList) {				
			String selectedSrcDst = null;
			for(String node:sortClique) {
				if(maximumIndepSet.size()==0) {
					selectedSrcDst = node;
				}else {
					 if(checkDependencyGroup(node, maximumIndepSet)) {
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
					 Memcopying selectedMig = srcdstMigList.get(0);
					 maximumIndepSet.add(selectedMig);
					 srcdstMigList.remove(0);
				 }
				 //delete srcdstNode in depGraphSrcDst if its list is empty
				 if(srcdstMigList.size()==0) {
					 depGraphSrcDst.removeVertex(selectedSrcDst);
				 }
			}
		}		
		return maximumIndepSet;
	}
	
	
	/**
	 * get all maximum independent of migration iteratively at once
	 * @param allCliquesList
	 * @return
	 */
	public List<Set<Memcopying>> getIterMISFromCliques(List<Set<String>> allCliquesList){
		List<Set<Memcopying>> allIndepSetList = new ArrayList<>();
		//1. sort all nodes in the cliques first
		for(Set<String> clique:allCliquesList) {
			Map<String, Integer> nodeDegreeMap = new HashMap<>();
			for(String srcdstNode:clique) {
				//add degree of the node
				//int degree = depGraphSrcDst.degreeOf(srcdstNode);
				int degree = 0;
				//or out degree of the clique for the node
				for(String neighbor: Graphs.neighborListOf(depGraphSrcDst, srcdstNode)) {
					if(!clique.contains(neighbor)) {
						degree++;
					}
				}
				nodeDegreeMap.put(srcdstNode, degree);
			}
			Map<String, Integer> sortedDegreeMap = MapUtil.sortByValue(nodeDegreeMap);
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
						 Memcopying selectedMig = srcdstMigList.get(0);
						 migMIS.add(selectedMig);
						 totalMigNum --;
						 srcdstMigList.remove(0);
					 }
					 //delete srcdstNode in depGraphSrcDst if its list is empty
					 if(srcdstMigList.size()==0) {
						 depGraphSrcDst.removeVertex(selectedSrcDst);
					 }
				}	
			}
			//migMIS for this round is finished
			allIndepSetList.add(migMIS);
			
		}
		return allIndepSetList;
	}
	
	/**
	 * get all cliques of depgraphSrcDst based on src-dst pair node
	 * @return
	 */
	public List<Set<String>> getAllCliquesDepGraphSrcDst(){
		//all maximal cliques of current graph
        List<Set<String>> maxcliqList = new ArrayList<>();
        BronKerboschCliqueFinder<String, DefaultEdge> finder = new BronKerboschCliqueFinder<>(depGraphSrcDst);
        finder.iterator();
        for(Iterator<Set<String>> iter = finder.iterator();iter.hasNext();){
            Set<String> clique = iter.next();
            maxcliqList.add(clique);
            /*
            for(String srcdst: clique){
            for (String srcdst1 : clique) {
                if(!srcdst.equals(srcdst1))
                if(!depGraphSrcDst.containsEdge(srcdst, srcdst1)){
                    System.out.print("error clique");
                }
            }
            }*/
        }
        return maxcliqList;
    }
	
	/**
	 * get all cliques of depgraph based on migration id graph
	 * @return
	 */
	public List<Set<Integer>> getAllCliques(){
		List<Set<Integer>> maxcliqList = new ArrayList<>();
        BronKerboschCliqueFinder<Integer, DefaultEdge> finder = new BronKerboschCliqueFinder<Integer, DefaultEdge>((Graph<Integer, DefaultEdge>) depGraph);
        finder.iterator();
        for(Iterator<Set<Integer>> iter = finder.iterator();iter.hasNext();){
            Set<Integer> clique = iter.next();
            maxcliqList.add(clique);
            for(Integer migId: clique){
            for (Integer migId1 : clique) {
                if(migId != migId1)
                if(!depGraph.hasEdgeConnecting(migId, migId1)){
                    System.out.print("error clique");
                }
            }
            }
        }
        return maxcliqList;
	}
	
	
	/**
	 * create memcopying based on src and dst only
	 * @param src
	 * @param dst
	 * @return
	 */
	private Memcopying createMemcopying(Host src, Host dst) {
		//SDNVm vm = (SDNVm) migrate.get("vm");
		Host host = dst;
		
		int srcHost = src.getId();
		int dstHost = host.getId();
		
		int srcVm = this.dataCenter.getMigHostIdToVmIdTable().get(srcHost);
		int dstVm = this.dataCenter.getMigHostIdToVmIdTable().get(dstHost);
		
		Vm vm = this.dataCenter.findVm(srcVm);
		
		long amountMem = (long) (1*Math.pow(10,9));
		double startTime = -1;
		double currentTime = CloudSim.clock();
		Memcopying act = new Memcopying(srcVm, dstVm, srcHost, dstHost, amountMem, startTime, currentTime);
		//act.setMigVm(vm.getId());
		act.setPrecopy(false);
		act.setStopandcopy(false);
		
		//set VM/VNF resources requirement
		act.setVmRAM(1);
		act.setVmBw(1);
		act.setVmDisk(1);
		
		int chId = this.dataCenter.findMigFlow(act.getSrcHost(), act.getDstHost());
		act.setChId(chId);
		
		Map<String, Object> migrate = new HashMap<>();
		migrate.put("vm", vm);
		migrate.put("host", dst);
		
		// all version compatibility
		act.migrate = migrate;
		
		//delay-aware migration
		act.setDeadline(0);
		return act;
	}

	
	/**
	 * create DepGraph based on the src-dst pair
	 * @param feasibleMigList
	 */
	
	protected void createDepGraphSrcDst(List<Memcopying> feasibleMigList) {
		
		//add node first
		for(int i =0; i < feasibleMigList.size(); i++) {
			//this.migNum++;
			Memcopying mig = feasibleMigList.get(i);
			//mig.setId(this.migNum);
			String srcdstNode = mig.getSrcHost() + "-" + mig.getDstHost();
			if(!depGraphSrcDst.vertexSet().contains(srcdstNode)) {
				depGraphSrcDst.addVertex(srcdstNode);
			}
			List<Memcopying> nodeList = null;
			if(!this.srcDstPairListMap.containsKey(srcdstNode)) {
				 nodeList = new ArrayList<>();
			}else {
				nodeList = this.srcDstPairListMap.get(srcdstNode);
			}
			nodeList.add(mig);
			this.srcDstPairListMap.put(srcdstNode, nodeList);
		}
		addEdgeSrcDstFatTree();
	}
	
	/**
	 * add dependency edge based on pre-descripted file of all topology
	 * suits for the WAN inter-datacenter topologies
	 * @param depGraphFile
	 */
	public void addEdge_depGraphSrcDst(String depGraphFile) {
		
	}
	
	/**
	 * simple method to add dependency edge to depGraphSrcDst
	 */
	public void addEdge_depGraphSrcDst() {
        for(String n: depGraphSrcDst.vertexSet()){
            for(String m: depGraphSrcDst.vertexSet()){
                if (!n.equals(m)) {
                    String[] nlist = n.split("-", 0);
                    String[] mlist = m.split("-", 0);
                    Integer ns = Integer.valueOf(nlist[0]);
                    Integer nd = Integer.valueOf(nlist[1]);
                    Integer ms = Integer.valueOf(mlist[0]);
                    Integer md = Integer.valueOf(mlist[1]);
                    if (ns.equals(ms) || nd.equals(md)) {
                        //check src-dst pair dependency
                        depGraphSrcDst.addEdge(n, m);
                    }
                }
            }
        }
    }
	
	protected void addEdgeSrcDstFatTree() {
		for(String n: depGraphSrcDst.vertexSet()){
            for(String m: depGraphSrcDst.vertexSet()){
                if (!n.equals(m)) {
                    String[] nlist = n.split("-", 0);
                    String[] mlist = m.split("-", 0);
                    Integer ns = Integer.valueOf(nlist[0]);
                    Integer nd = Integer.valueOf(nlist[1]);
                    Integer ms = Integer.valueOf(mlist[0]);
                    Integer md = Integer.valueOf(mlist[1]);
                    if (ns.equals(ms) || nd.equals(md)) {
                        //check src-dst pair dependency
                        depGraphSrcDst.addEdge(n, m);
                    }
                }
            }
        }
	}
	
	/**
	 * create DepGraph based on the migration id
	 */
	protected void createDepGraph(List<Memcopying> feasibleMigList) {
		this.migNum =0;
		for(int i =0; i<feasibleMigList.size();i++) {
			this.migNum++;			
			Memcopying mig = feasibleMigList.get(i);
			mig.setId(this.migNum);
			System.out.println("mig id: "+mig.getId());
			this.depGraph.addNode(mig.getId());
		}
		
		for(int i =0; i<feasibleMigList.size()-1;i++) {
			Memcopying check = feasibleMigList.get(i);
			//System.out.println("[DEBUG]: migVm" + check.getMigVm());
			//System.out.println("[DEBug]: ChId" + check.getChId());
			for(int j = i+1; j<feasibleMigList.size(); j++) {				
				//System.out.println("[DEBUG]: migVm" + feasibleMigList.get(j).getMigVm());
				//System.out.println("[DEBug]: ChId" + feasibleMigList.get(j).getChId());
				boolean indep = false;
				if(Configuration.MIG_SIMPLE_INDEPENDENT==false) {
					indep = checkIndep(check, feasibleMigList.get(j));
				}else {
					indep = this.checkIndependent(check, feasibleMigList.get(j));
				}//simple independent check
				if(indep == false) 
					this.depGraph.putEdge(check.getId(), feasibleMigList.get(j).getId());
			}
		}
	}
	
	public int getPlanTimes() {
		return this.planTimes;
	}
	//test csv file reading
	//public static void main(String[] args) {
	//	String path = "C:\\Users\\tianzhangh\\Documents\\cloudsim-minimize-peak-temperature-algorithm-master\\output\\migration";
	//	VMMigrationPlanIterMIS.readVmPlacement(path+"\\20110303_camig_mmt_1.2VmPlacement.csv");
	//}

}
