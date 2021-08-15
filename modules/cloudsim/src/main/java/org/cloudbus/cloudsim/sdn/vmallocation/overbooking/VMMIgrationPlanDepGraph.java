package org.cloudbus.cloudsim.sdn.vmallocation.overbooking;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Configuration;
import org.cloudbus.cloudsim.sdn.Memcopying;
import org.cloudbus.cloudsim.sdn.SDNDatacenter;
import org.cloudbus.cloudsim.sdn.SDNHost;
import org.cloudbus.cloudsim.sdn.SDNVm;
import org.cloudbus.cloudsim.sdn.VmGroup;

import com.google.common.graph.EndpointPair;

public class VMMIgrationPlanDepGraph extends VMMigrationPlanSLAGroup{
	public String filePath = "";
		public VMMIgrationPlanDepGraph(String filePath, Map<Vm, VmGroup> vmGroups, List<Map<String, Object>> migrationMapping, SDNDatacenter dataCenter) {
			super(vmGroups, migrationMapping, dataCenter);
			this.filePath = filePath;
		}
		
		@Override
		public void processMigrationPlan() {
			this.migFeasible = new LinkedList<>();
			for(Host src:this.dataCenter.getHostList()) {
				for(Host dst:this.dataCenter.getHostList()) {
					if(src.getId()!=dst.getId()) {
						Memcopying mig = createMemcopying(src, dst);
						this.migFeasible.add(mig);
					}
				}
			}
			this.createDepGraph(this.migFeasible);
			this.writeDepGraph(filePath);
		}
		
		
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
		
		
		public void writeDepGraph(String filepath) {
			try {
				FileWriter f = new FileWriter(filepath);
				PrintWriter p = new PrintWriter(f);
				for(Integer n:this.depGraph.nodes()) {				
					p.println("n: "+n);
					System.out.println("n: "+n);
				}
				for(int i=0; i<this.migFeasible.size()-1;i++) {
					for(int j=i+1; j<this.migFeasible.size();j++) {
						int src = this.migFeasible.get(i).getId();
						int dst = this.migFeasible.get(j).getId();
						if(this.depGraph.hasEdgeConnecting(src, dst)) {
							p.println("src: "+src+"->"+"dst: "+dst);
							System.out.println("src: "+src+"->"+"dst: "+dst);
						}
					}
				}
				p.flush();
				p.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
}
