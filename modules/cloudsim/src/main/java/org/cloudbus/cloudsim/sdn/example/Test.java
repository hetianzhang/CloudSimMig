package org.cloudbus.cloudsim.sdn.example;

import java.text.DecimalFormat;

public class Test {

	public static void main(String[] args) {
		float f=(float) 123456.78992345;
		DecimalFormat df = new DecimalFormat();
		df.setGroupingUsed(false);
		df.setMaximumFractionDigits(3);
		System.out.println(df.format(f));
	}
}
