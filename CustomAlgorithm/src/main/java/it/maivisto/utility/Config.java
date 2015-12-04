package it.maivisto.utility;

import java.io.File;

public class Config {
	
	public static String dirData = "data"+File.separator;
	public static String dirSerialModel = Config.dirData +"models"+File.separator;
	public static String dirConfigItemContent="lib"+File.separator+"TextualSimilarity"+File.separator+"config"+File.separator+configItemContent();
	public static String dirStackingItemContent="lib"+File.separator+"TextualSimilarity"+File.separator+"config"+File.separator+"stacking.xml";

	public static String configItemContent(){
		String OS = System.getProperty("os.name").toLowerCase();
		if(OS.indexOf("win") >= 0)
			return "config_win.properties";
		 return "config_linux.properties";
	}
}
