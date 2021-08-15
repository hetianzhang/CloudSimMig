package org.cloudbus.cloudsim.sdn.example.topogenerators;
/**
 * parse graphml file in internet topology zoo
 * transfer it to cloudsimsdn physcial topology JSON file
 * @author tianzhangh
 */

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGenerator.HostSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGenerator.NodeSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGenerator.SwitchSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.PhysicalTopologyGeneratorSFC.LinkSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.VirtualTopologyGenerator.VMSpec;
import org.cloudbus.cloudsim.sdn.example.topogenerators.VirtualTopologyGeneratorVmTypesSimple.Flavour;
import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.w3c.dom.Attr;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Node;
import org.w3c.dom.Element;


public class GraphMLParser extends VirtualTopologyGenerator{
	
	@SuppressWarnings("unchecked")
	public static void main(String[] args) {
		/*String jsonFileName = "aarnet-bw.json";
		long iops = 1000000000L;
		double latency = 0.1; //default latency
		
		int hNum = 1024;
		int pe = 48*hNum;
		long mips = 10000;//8000;
		int ram = 10240*hNum;
		long storage = 10000000*hNum;
		//long bw = 125000000;
		long bw = (long)(40*Math.pow(10, 9)); //40Gbps by default
		
		GraphMLParser reqg = new GraphMLParser();
		HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
		generatePhysicalTopo(reqg, hostSpec, bw, iops, latency);
		reqg.wrtieJSON(jsonFileName);*/
		long iops = 1000000000L;
		double latency = 0.1; //default latency
		
		int hNum = 1024;
		int pe = 48*hNum;
		long mips = 10000;//8000;
		int ram = 10240*hNum;
		long storage = 10000000*hNum;
		//long bw = 125000000;
		long reqbw = (long) (10*Math.pow(10, 9)); //10Gbps by default
		long bw = (long)(40*Math.pow(10, 9)); //40Gbps by default
		
		final File dir = new File("C:\\Users\\tianzhangh\\Documents\\internet zoo topology\\archive\\");
		for(final File fileEntry: dir.listFiles()) {
			if(!fileEntry.isDirectory()) {
				if(fileEntry.getName().indexOf("json")!=-1) {
					fileEntry.delete();
				}
			}
		}
		for(final File fileEntry: dir.listFiles()) {
			if(!fileEntry.isDirectory()) {
				if(fileEntry.getName().indexOf("graphml")!=-1) {
					GraphMLParser reqg = new GraphMLParser();
					HostSpec hostSpec = reqg.createHostSpec(pe, mips, ram, storage, bw);
					generatePhysicalTopo(fileEntry.getName(), reqg, hostSpec, reqbw, bw, iops, latency);
					reqg.writeJSON("C:\\Users\\tianzhangh\\Documents\\internet zoo topology\\archive\\"+fileEntry.getName()+".json");
					String mappingFileName = fileEntry.getName()+"-mapping.json";
					JSONArray mapping = new JSONArray();
					JSONObject obj = new JSONObject();
					
					int hostSize = vmHostMapping.size();
					/*for(int i =0; i < hostSize; i++) {
						for(int n =0; n< hostSize; n++) {
							String str = "";
							if(i!=n) {
								str = "v-"+i+"-"+n+": "+i;
								vmHostMapping.add(str);
							}
						}
					}*/
					
					GraphMLParser gparser = new GraphMLParser();
					String virtualFile = "C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\internet-topo-virtual\\";
					virtualFile += fileEntry.getName()+"-virtual.json";
					int i =0;
					for(String s:vmHostMapping) {
						mapping.add(s);
						String str[] = s.split(":");
						String vm_name = str[0];
						gparser.createVM(vm_name, Flavour.MICRO, -1, -1);
						i++;
					}
					gparser.wrtieJSON(virtualFile);
					
					
					obj.put("mappings", mapping);
					vmHostMapping = new ArrayList<>();
					try(PrintWriter writer = new PrintWriter(
							new File("C:\\Users\\tianzhangh\\Documents\\cloudsim-sfc\\modules\\cloudsim\\"
									+ "internet-topo-mapping\\"+mappingFileName))){
						writer.print(obj.toJSONString().replaceAll(",", ",\n"));
						writer.flush();
						writer.close();
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}
			}
		}	
	}
	
	static public Map<Integer, NodeSpec> dcNameIdMap = new HashMap<>();
	static public Map<Integer, List<HostSpec>> dcNameHostMap = new HashMap<>();
	static public List<String> vmHostMapping = new ArrayList<>();
	static int hostNum = 0;
	
	static public List<HostSpec> readNodeKey(int id, NodeList nList, GraphMLParser reqg, HostSpec hostSpec, long bw, long iops) {
		List<HostSpec> hList = new ArrayList<>();;
		int dcs=0;
		String name=null;
		boolean internalFlag = false;
		for(int i =0; i<nList.getLength(); i++) {
			Node node = nList.item(i);
			if(node.getNodeType() == Node.ELEMENT_NODE) {
				Element eNode = (Element) node;
				String key = eNode.getAttribute("key");
				if(key.equalsIgnoreCase(internalId)) {
					//internal nodes		
					 dcs = Integer.valueOf(node.getTextContent());
					 System.out.println("internal: "+dcs);
					 internalFlag = true;
				}else if(key.equalsIgnoreCase(nodeidId)) {
					name = node.getTextContent();
					System.out.println("id: "+id);
				}
				else if(key.equalsIgnoreCase(nodelabelId)) {
					//name = node.getTextContent();
					System.out.println("name: "+name);
				}
			}
		}
		if(name!=null && !name.equalsIgnoreCase("external")) {
			SwitchSpec c = reqg.addSwitch("gateway-"+name, "edge", bw, iops);
			dcNameIdMap.put(id,c);
			if(internalFlag == true) {
			for(int i = 0; i<dcs; i++) {
				HostSpec h = reqg.addHost(Integer.toString(i)+name, hostSpec);
				reqg.addLink(h, c, 0.1);
				hList.add(h);
				vmHostMapping.add("v-"+id+": "+Integer.toString(i)+name);
				hostNum++;
			}}
			}
		return hList;
	}	
	
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
		MICRO_BUSY
	}
	
	public VMSpec createVM(String name, Flavour f, double startTime, double endTime) {
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
	
    static String internalId = "";
    static String longitudeId = "";
    static String nodelabelId = "";
    static String graphlabelId = "";
    static String latitudeId = "";
    static String nodeidId = "";
	
	static public void generatePhysicalTopo(String fileName, GraphMLParser reqg, HostSpec hostSpec, long interbw, long hostbw, long iops, double latency) {
		String fileBase = "C:\\Users\\tianzhangh\\Documents\\internet zoo topology\\archive\\";
		String fullPath = fileBase+fileName;
		try {
		       File inputFile = new File(fullPath);
		       DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
		       DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
		       Document doc = dBuilder.parse(inputFile);
		       doc.getDocumentElement().normalize();
		       //root graphml
		       

		       
		    		   
		       System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
		       NodeList keyList = doc.getElementsByTagName("key");
		       System.out.println("----------------------------");
		       for(int temp =0; temp < keyList.getLength(); temp++) {
		    	   Element nNode = (Element) keyList.item(temp);
		    	   Attr aname = nNode.getAttributeNode("attr.name");
		    	   Attr aid =nNode.getAttributeNode("id");
		    	   switch(aname.getValue()){
		    	   case "Internal":
		    		   internalId = aid.getValue();
	    			   break;
		    	   case "Longitude":
		    		   longitudeId = aid.getValue();
		    		   break;
		    	   case "Latitude":
		    		   latitudeId = aid.getValue();
		    		   break;
		    	   case "id":
		    		   String str = nNode.getAttributeNode("for").getValue();
		    		   if(str.equalsIgnoreCase("node")) {
		    			   nodeidId = aid.getValue();
		    		   }
		    		   break;
		    	   case "label":
		    		   str = nNode.getAttributeNode("for").getValue();
		    		   if(str.equalsIgnoreCase("node")) {
		    			   nodelabelId = aid.getValue();
		    		   }else if(str.equalsIgnoreCase("graph")) {
		    			   graphlabelId = aid.getValue();
		    		   }			   
		    		   break;
		    	   }
		    		   
		    			   
		    	   
		    	   Attr b = nNode.getAttributeNode("attr.type");
		    	   System.out.println(b.getValue());
		    	   Attr c = nNode.getAttributeNode("for");
		    	   System.out.println(c.getValue());
		    	   
		       }
		       
		       NodeList graph = doc.getElementsByTagName("graph");
		       System.out.println("----------------------------");
		       
		       for (int temp = 0; temp < graph.getLength(); temp++) {
		          Element nNode = (Element) graph.item(temp);
		          NodeList nList = nNode.getElementsByTagName("node");
		          NodeList eList = nNode.getElementsByTagName("edge");
		          
		          
		          for(int i=0; i< nList.getLength(); i++) {
		        	 Node n = nList.item(i);
		             if(n.getNodeType() == Node.ELEMENT_NODE) {
		            		Element nElement = (Element) n;
		            		int id = Integer.valueOf(nElement.getAttribute("id"));
		            		System.out.println("id : " 
		      	                  + nElement.getAttribute("id"));
		            		NodeList dataList = nElement.getElementsByTagName("data");
		            		List<HostSpec> dcHostList = readNodeKey(id, dataList, reqg, hostSpec, hostbw, iops);
		            		dcNameHostMap.put(id, dcHostList);
		            		
		            	}
		            }
		            
		            for(int i = 0; i<eList.getLength(); i++) {
		            	Node n = eList.item(i);
		            	if(n.getNodeType() == Node.ELEMENT_NODE) {
		            		Element eElement = (Element) n;
		            		int source = Integer.valueOf(eElement.getAttribute("source"));
		            		int target = Integer.valueOf(eElement.getAttribute("target"));
		            		System.out.println("source "
		            				+eElement.getAttribute("source"));
		            		System.out.println("target "
		            				+eElement.getAttribute("target"));
		            		NodeSpec src = dcNameIdMap.get(source);
		            		NodeSpec dest = dcNameIdMap.get(target);
		            		System.out.println(src.name+"<->"+dest.name);
		            		Double reqbw = (double) interbw; // by default
		            		/*NodeList dataList = eElement.getElementsByTagName("data");
		            		for(int j =0; j<dataList.getLength();j++) {
		            			Node node = dataList.item(j);
		            			if(node.getNodeType() == Node.ELEMENT_NODE) {
		            				Element eNode = (Element) node;
		            				String key = eNode.getAttribute("key");     				
		            				switch(key) {
		            					case "d34":
		            						//internal nodes
		            						String bwstring = node.getTextContent();
		            						bwstring = bwstring.replace("<", "");
		            						bwstring = bwstring.replace(" ", "");
		            						if(bwstring.indexOf("Gbps")!=-1) {
		            							bwstring =bwstring.replace("Gbps", "");
			            						reqbw = Double.valueOf(bwstring);
			            						reqbw = reqbw * Math.pow(10, 9);
		            						}else {
		            							if(bwstring.indexOf("Mbps")!=-1) {
		            								bwstring =bwstring.replace("Mbps", "");
				            						reqbw = Double.valueOf(bwstring);
				            						reqbw = reqbw * Math.pow(10, 6);
		            							}
		            						}
		            						
		            						
		            						break;
		            				}
		            			}
		            		}*/
		            		double lat = latency;
		            		System.out.println("bw: "+ Math.round(reqbw) +" bps");
		            		reqg.addLink(src, dest, lat, Math.round(reqbw));
		            	}
		            }
		         }
		      } catch (Exception e) {
		         e.printStackTrace();
		      }
	}
	
	static public void generatePhysicalTopo(GraphMLParser reqg, HostSpec hostSpec, long bw, long iops, double latency) {
	    try {
	       File inputFile = new File("C:\\Users\\tianzhangh\\Documents\\internet zoo topology\\archive\\aarnet-bw.graphml");
	       DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
	       DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
	       Document doc = dBuilder.parse(inputFile);
	       doc.getDocumentElement().normalize();
	       //root graphml
	       System.out.println("Root element :" + doc.getDocumentElement().getNodeName());
	       NodeList graph = doc.getElementsByTagName("graph");
	       System.out.println("----------------------------");
	       
	       for (int temp = 0; temp < graph.getLength(); temp++) {
	          Element nNode = (Element) graph.item(temp);
	          NodeList nList = nNode.getElementsByTagName("node");
	          NodeList eList = nNode.getElementsByTagName("edge");
	          
	          for(int i=0; i< nList.getLength(); i++) {
	        	 Node n = nList.item(i);
	             if(n.getNodeType() == Node.ELEMENT_NODE) {
	            		Element nElement = (Element) n;
	            		int id = Integer.valueOf(nElement.getAttribute("id"));
	            		System.out.println("id : " 
	      	                  + nElement.getAttribute("id"));
	            		NodeList dataList = nElement.getElementsByTagName("data");
	            		List<HostSpec> dcHostList = readNodeKey(id, dataList, reqg, hostSpec, bw, iops);
	            		dcNameHostMap.put(id, dcHostList);
	            	}
	            }
	            
	            for(int i = 0; i<eList.getLength(); i++) {
	            	Node n = eList.item(i);
	            	if(n.getNodeType() == Node.ELEMENT_NODE) {
	            		Element eElement = (Element) n;
	            		int source = Integer.valueOf(eElement.getAttribute("source"));
	            		int target = Integer.valueOf(eElement.getAttribute("target"));
	            		System.out.println("source "
	            				+eElement.getAttribute("source"));
	            		System.out.println("target "
	            				+eElement.getAttribute("target"));
	            		NodeSpec src = dcNameIdMap.get(source);
	            		NodeSpec dest = dcNameIdMap.get(target);
	            		System.out.println(src.name+"<->"+dest.name);
	            		Double reqbw = (double) bw; // by default
	            		NodeList dataList = eElement.getElementsByTagName("data");
	            		for(int j =0; j<dataList.getLength();j++) {
	            			Node node = dataList.item(j);
	            			if(node.getNodeType() == Node.ELEMENT_NODE) {
	            				Element eNode = (Element) node;
	            				String key = eNode.getAttribute("key");     				
	            				switch(key) {
	            					case "d34":
	            						//internal nodes
	            						String bwstring = node.getTextContent();
	            						bwstring = bwstring.replace("<", "");
	            						bwstring = bwstring.replace(" ", "");
	            						if(bwstring.indexOf("Gbps")!=-1) {
	            							bwstring =bwstring.replace("Gbps", "");
		            						reqbw = Double.valueOf(bwstring);
		            						reqbw = reqbw * Math.pow(10, 9);
	            						}else {
	            							if(bwstring.indexOf("Mbps")!=-1) {
	            								bwstring =bwstring.replace("Mbps", "");
			            						reqbw = Double.valueOf(bwstring);
			            						reqbw = reqbw * Math.pow(10, 6);
	            							}
	            						}
	            						
	            						
	            						break;
	            				}
	            			}
	            		}
	            		double lat = latency;
	            		System.out.println("bw: "+ Math.round(reqbw) +" bps");
	            		reqg.addLink(src, dest, lat, Math.round(reqbw));
	            	}
	            }
	         }
	      } catch (Exception e) {
	         e.printStackTrace();
	      }
	   }
	
	
	class LinkSpec {
		String source;
		String destination;
		double latency;
		long upBw;
		long downBw;
		
		public LinkSpec(String source,String destination,double latency2) {
			this.source = source;
			this.destination = destination;
			this.latency = latency2;
		}
		
		public LinkSpec(String source,String destination,double latency2, long bw) {
			this.source = source;
			this.destination = destination;
			this.latency = latency2;
			upBw = bw;
			downBw = bw;
		}
		
		public LinkSpec(String source,String destination,double latency2, long upbw, long downbw) {
			this.source = source;
			this.destination = destination;
			this.latency = latency2;
			upBw = upbw;
			downBw = downbw;
		}
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			LinkSpec link = this;
			JSONObject obj = new JSONObject();
			obj.put("source", link.source);
			obj.put("destination", link.destination);
			obj.put("latency", link.latency);
			obj.put("upbw", link.upBw);
			obj.put("downbw", link.downBw);
			return obj;
		}
	}
	
	protected List<HostSpec> hosts = new ArrayList<HostSpec>();
	protected List<SwitchSpec> switches = new ArrayList<SwitchSpec>();
	protected List<LinkSpec> links = new ArrayList<LinkSpec>();

	public HostSpec addHost(String name, HostSpec spec) {
		HostSpec host = new HostSpec(spec.pe, spec.mips, spec.ram, spec.storage, spec.bw);
		
		host.name = name;
		host.type = "host";
		
		hosts.add(host);
		return host;
	}
	public HostSpec addHost(String name, int pes, long mips, int ram, long storage, long bw) {
		HostSpec host = new HostSpec(pes, mips, ram, storage, bw);
		return addHost(name, host);
	}
	
	public SwitchSpec addSwitch(String name, String type, long bw, long iops) {
		SwitchSpec sw = new SwitchSpec();
		
		sw.name = name;
		sw.type = type;		// core, aggregation, edge
		sw.bw = bw;
		sw.iops = iops;
		
		switches.add(sw);
		return sw;
	}
	
	
	protected void addLink(NodeSpec source, NodeSpec dest, double latency, long upbw, long downbw) {
		links.add(new LinkSpec(source.name, dest.name, latency, upbw, downbw));
	}
	
	protected void addLink(NodeSpec source, NodeSpec dest, double latency, long bw) {
		links.add(new LinkSpec(source.name, dest.name, latency, bw));
	}
	
	protected void addLink(NodeSpec source, NodeSpec dest, double latency) {
		links.add(new LinkSpec(source.name,dest.name, latency));
	}
	
	public HostSpec createHostSpec(int pe, long mips, int ram, long storage, long bw) {
		return new HostSpec(pe, mips, ram, storage, bw);
	}

	class NodeSpec {
		String name;
		String type;
		long bw;
	}
	class HostSpec extends NodeSpec {
		int pe;
		long mips;
		int ram;
		long storage;
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			HostSpec o = this;
			JSONObject obj = new JSONObject();
			obj.put("name", o.name);
			obj.put("type", o.type);
			obj.put("storage", o.storage);
			obj.put("pes", o.pe);
			obj.put("mips", o.mips);
			obj.put("ram", new Integer(o.ram));
			obj.put("bw", o.bw);
			return obj;
		}
		public HostSpec(int pe, long mips, int ram, long storage, long bw) {
			this.pe = pe;
			this.mips = mips;
			this.ram = ram;
			this.storage = storage;
			this.bw = bw;
			this.type = "host";
		}
	}

	class SwitchSpec extends NodeSpec {
		long iops;
		
		@SuppressWarnings("unchecked")
		JSONObject toJSON() {
			SwitchSpec o = this;
			JSONObject obj = new JSONObject();
			obj.put("name", o.name);
			obj.put("type", o.type);
			obj.put("iops", o.iops);
			obj.put("bw", o.bw);
			return obj;
		}
	}

	
	int vmId = 0;
	
	public void writeJSON(String jsonFileName) {
		JSONObject obj = new JSONObject();

		JSONArray nodeList = new JSONArray();
		JSONArray linkList = new JSONArray();
		
		for(HostSpec o:hosts) {
			nodeList.add(o.toJSON());
		}
		for(SwitchSpec o:switches) {
			nodeList.add(o.toJSON());
		}
		
		for(LinkSpec link:links) {
			linkList.add(link.toJSON());
		}
		
		obj.put("nodes", nodeList);
		obj.put("links", linkList);
	 
		try {
	 
			FileWriter file = new FileWriter(jsonFileName);
			file.write(obj.toJSONString().replaceAll(",", ",\n"));
			file.flush();
			file.close();
	 
		} catch (IOException e) {
			e.printStackTrace();
		}
	 
		System.out.println(obj);
	}
	
		
}
