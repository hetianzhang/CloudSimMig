package org.cloudbus.cloudsim.sdn.policies;

import java.util.List;

import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.sdn.Link;
import org.cloudbus.cloudsim.sdn.Node;

public class LinkSelectionPolicyMultipath implements LinkSelectionPolicy{
	//create subflow forwarding table for the main flow in a path pool based on available BW
	@Override
	public Link selectLink(List<Link> links, int flowId, Node srcHost, Node destHost, Node prevNode) {
		if(flowId >=10000) {
			
		}else {
			
		}
		if(links.size() == 1) {
			return links.get(0);
		}
		
		int numLinks = links.size();
		int linkid = destHost.getAddress() % numLinks;
		Link link = links.get(linkid);
		
		// Choose the least full one.
		for(Link l:links) {
			int linkCn = link.getChannelCount(prevNode);
			int lCn = l.getChannelCount(prevNode);
			//double linkBw = link.getAllocatedBandwidthForDedicatedChannels(prevNode);
			//double lBw = l.getAllocatedBandwidthForDedicatedChannels(prevNode);
			if( lCn < linkCn) {
				//Log.printLine(CloudSim.clock() + ": LinkSelectionPolicyFlowCapacity: Found less crowded link: " + lBw + "<" + linkBw+". old="+l+", new="+link);
				Log.printLine(CloudSim.clock() + ": LinkSelectionPolicyFlowCapacity: Found less crowded link: " + lCn + "<" + linkCn+". old="+l+", new="+link);
				link = l; 
			}
		}
		return link;
	}

	@Override
	public boolean isDynamicRoutingEnabled() {
		return true;
	}
	

}
