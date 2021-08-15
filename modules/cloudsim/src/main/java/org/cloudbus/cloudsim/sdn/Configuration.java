/*
 * Title:        CloudSimSDN
 * Description:  SDN extension for CloudSim
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2017, The University of Melbourne, Australia
 */
 
 package org.cloudbus.cloudsim.sdn;

public class Configuration {
	public static String workingDirectory = "./";
	public static String experimentName="";
	
	//public static double minTimeBetweenEvents = 0.01;//0.01;	// in sec
	//public static int resolutionPlaces = 1;
	//public static int timeUnit = 1;	// 1: sec, 1000: msec\

	// Monitoring setup
	
	/*
	/////////////////////////////////FOR TEST ONLY
	public static final double monitoringTimeInterval = 1; // every 60 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = 5;	// How many points to track
	
	public static final double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.

	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 0.1;	// Guarantee 10%
	public static final double OVERBOOKING_RATIO_INIT = 0.5;
	
	public static final double OVERLOAD_THRESHOLD = 0.70;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 0.9;

	public static final double UNDERLOAD_THRESHOLD_HOST = 0.5;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0.5;
	public static final double UNDERLOAD_THRESHOLD_VM = 0.1;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.03; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = 0.5;	//Give 10% more than historical utilization
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.3;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 0.20;	// 20% of CPU resource is required to process 1 workload
	
	///*
	
	//////////////////////////// FOR Overbooking EXPERIMENT
	public static final double monitoringTimeInterval = 180; // every 60 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = 10;	// How many points to track
	
	public static final double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.

	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 0.9;
	//public static final double OVERBOOKING_RATIO_MIN = 0.4;
	public static double OVERBOOKING_RATIO_INIT = 0.7;
	
	public static final double OVERLOAD_THRESHOLD = 0.70;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 0.7;

	public static final double UNDERLOAD_THRESHOLD_HOST = 0.5;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0.5;
	public static final double UNDERLOAD_THRESHOLD_VM = 0.1;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.03; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = (OVERBOOKING_RATIO_MAX - OVERBOOKING_RATIO_MIN)*0.2;	
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.3;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 0.2;//0.05;	// 20% of CPU resource is required to process 1 workload
	
	public static final double HOST_ACTIVE_AVERAGE_UTIL_THRESHOLD = 0.1;

	//*/
	//////////////////////////// Default value
	public static final double CPU_SIZE_MULTIPLY = 1;	// Multiply all the CPU size for scale. Default =1 (No amplify) 
	public static final double NETWORK_PACKET_SIZE_MULTIPLY = 1;	// Multiply all the network packet size. Default =1 (No amplify) 
	
	public static double monitoringTimeInterval = 60; //60; // every 1800 seconds, polling utilization.
	
	public static final double overbookingTimeWindowInterval = monitoringTimeInterval;	// Time interval between points 
	public static final double overbookingTimeWindowNumPoints = 1;//Double.POSITIVE_INFINITY;	// No migration. How many points to track
//	public static final double overbookingTimeWindowNumPoints = 10;	// How many points to track
	
	//public static double migrationTimeInterval = overbookingTimeWindowInterval*overbookingTimeWindowNumPoints; // every 1 seconds, polling utilization.
	public static double migrationTimeInterval = 600; // powerlab dependency migration configuration
	
	public static final double OVERBOOKING_RATIO_MAX = 1.0; 
	public static final double OVERBOOKING_RATIO_MIN = 1.0;	// No overbooking
	public static double OVERBOOKING_RATIO_INIT = 1.0; // No overbooking
	
	public static final double OVERLOAD_THRESHOLD = 1.0;
	public static final double OVERLOAD_THRESHOLD_ERROR = 1.0 - OVERLOAD_THRESHOLD;
	public static final double OVERLOAD_THRESHOLD_BW_UTIL = 1.0;
	
	public static final double UNDERLOAD_THRESHOLD_HOST = 0;
	public static final double UNDERLOAD_THRESHOLD_HOST_BW = 0.3;
	public static final double UNDERLOAD_THRESHOLD_VM = 0.3;
	
	public static final double DECIDE_SLA_VIOLATION_GRACE_ERROR = 1.0; // Expected time + 5% is accepted as SLA provided
	
	public static final double OVERBOOKING_RATIO_UTIL_PORTION = (OVERBOOKING_RATIO_MAX - OVERBOOKING_RATIO_MIN)*0.2;	
	public static final double OVERLOAD_HOST_PERCENTILE_THRESHOLD = 0.0;	// If 5% of time is overloaded, host is overloaded
	
	public static final double CPU_REQUIRED_MIPS_PER_WORKLOAD_PERCENT = 1.0;//0.05;	// 20% of CPU resource is required to process 1 workload
	
	public static final double HOST_ACTIVE_AVERAGE_UTIL_THRESHOLD = 0;
	
	public static final double SFC_OVERLOAD_THRESHOLD_VM = 0.7;
	public static final double SFC_OVERLOAD_THRESHOLD_BW = 0.7;
	public static final double SFC_UNDERLOAD_THRESHOLD_BW = 0.4;
	public static final double SFC_UNDERLOAD_THRESHOLD_VM = 0.4;
	
	public static double TIME_OUT = Double.POSITIVE_INFINITY; // 3seconds
	public static double PACKET_TIME_OUT = Double.POSITIVE_INFINITY;
	public static final double RETRANSMISSION_TIMEOUT = 0.200; // TCP retransmission interval
	
	public static boolean SFC_AUTOSCALE_ENABLE = true;
	public static boolean SFC_AUTOSCALE_ENABLE_BW = true;
	public static boolean SFC_AUTOSCALE_ENABLE_VM = true;
	public static boolean SFC_AUTOSCALE_ENABLE_SCALE_DOWN_BW = true;
	public static boolean SFC_AUTOSCALE_ENABLE_SCALE_DOWN_VM = true;
	public static boolean SFC_AUTOSCALE_ENABLE_VM_VERTICAL = true;
	
	public static boolean MIG_ENABLE_FLAG = true;
	public static boolean MIG_ONLY_FIRST_ROUND = true;
	
	public static int MPTCP_SUBFLOW_MAX = 1; // for multiple path transmission (migration)
	//*/	
	public static double FLOW_MULTIPLER = 1;
	public static double MIG_FLOW_REQUIREMENT = 5; ////0.2*5=1Gbps
	public static double MIG_PRE_MIGRATION_TIME = 0.05;//1.0;
	public static double MIG_POST_MIGRATION_TIME = 0.05;//1.0;
	public static double MIG_EDGE_PLAN_INTERVAL = 1.0; //plan and schedule migration interval
	
	public static double DIRTY_PAGE_RATE_HIGH = 0.1; //ram>64:0.02, ram 16: 0.05, 4:0.12, <4:0.12
	public static double DIRTY_PAGE_RATE_LOW = 0.002; //ram>64:0.001, <16:0.002
	public static double DIRTY_PAGE_RATE_LARGE_RAM_HIGH = 0.02;
	public static double DIRTY_PAGE_RATE_LARGE_RAM_LOW = 0.001;
	public static double MIG_COMPRESS_RATIO = 0.8; //compress ratio for memory transmission
	
	public static String deadlinefilePath;
	
	public static String migPlanFilePath;
	public static boolean virtualTopoVmRandomOrder = false;
	public static Long WITHINHOST_BW = 40000000000L;
	
	//For channel update and delete
	public static boolean CHANNEL_UPDATE_NO_TRANSMISSION_DELTE = true;
	public static int MIG_CHANNEL_START_ID = 10000;
	
	public static int DEFAULT_ROUTING_PATH_K = 1;
	
	public static boolean MIG_FORCE_ALL = true;
	
	public static boolean OVERLOAD_HOST_RETURN = false;
	public static boolean DEFAULT_ROUTING_FATTREE = true;
	public static boolean DEAFULT_ROUTING_WAN = false;
	public static boolean DEAFULT_ROUTING_EDGE = false;
	public static boolean DEAFULT_ROUTING_EDGE_ALL = false; //calculate routing for all nodes in edge computing environments
	public static boolean MIG_SIMPLE_INDEPENDENT = true;
	public static boolean MIG_POTENTIAL_IMPACT =false;
	
	public static boolean CQNCR_SCHEDULER_FLAG = true;
	public static String MIG_FLOW_BANDWIDTH_SCHEME = "free"; //free, reserved, ratio
	public static boolean MIG_SHARE_BANDWIDTH = false; //false migration doesn't share vm reserved bw
}