package main;

import java.io.IOException;

import org.apache.log4j.PropertyConfigurator;
import print.color.Ansi.Attribute;
import print.color.Ansi.BColor;
import print.color.Ansi.FColor;
import print.color.ColoredPrinter;
import util.Options;
import performance.PerformanceLogger;
public class TDS {

	private TDSOriginal tds1;
	private TDSImproved tds2;
	private TDSFaultTolerant tds3;
	private TDSStaticTree tds4;
	private TDSFTStaticTree tds5;
	private TDSDijkstraScholten tds6;
	private TDSLaiWu tds7;
	private static ColoredPrinter cp;
	private static boolean[] done;

	private static TDS instance = new TDS();



	private TDS(){
		cp = new ColoredPrinter.Builder(1, false).build();
		done = new boolean[7];
	}

	public static synchronized TDS instance(){
		return instance;
	}

	/**
	 * Uncomment when working on eclipse or generally when output is not
	 * printed in terminal.
	 */
	//    public static synchronized void writeString(int version, String s){
	//    	System.out.println("[" + version + "] " + s);
	//    }

	/**
	 * Uncomment to create the jar
	 */
	public static synchronized void writeString(int version,String s) {
		if(Options.instance().get(Options.VERBOSE) == 0) return;
		if(version==1)
			cp.print("[ O-FSS ]", Attribute.BOLD, FColor.WHITE, BColor.BLUE);
		else if(version == 2)
			cp.print("[ I-FSS ]", Attribute.BOLD, FColor.WHITE, BColor.YELLOW);
		else if(version == 3)
			cp.print("[   FTS ]", Attribute.BOLD, FColor.WHITE, BColor.BLACK);
		else if(version == 4)
			cp.print("[   STA ]", Attribute.BOLD, FColor.WHITE, BColor.MAGENTA);
		else if(version == 5)
			cp.print("[ FTSTA ]", Attribute.BOLD, FColor.WHITE, BColor.CYAN);
		else if(version == 6)
			cp.print("[    DS ]", Attribute.BOLD, FColor.WHITE, BColor.BLUE);
		else if(version == 7)
			cp.print("[    LW ]", Attribute.BOLD, FColor.WHITE, BColor.BLACK);
		else if(version == 0){//warning
			cp.print("[  INFO ]", Attribute.BOLD, FColor.YELLOW, BColor.GREEN);
			cp.clear();
			cp.println(s);
			return;
		}else if(version == -1) {
			cp.print("[TIMEOUT]", Attribute.BOLD, FColor.WHITE, BColor.RED);
			cp.clear();
			cp.println(s);
			return;
		}else if(version == -2) {
			cp.print("[WARNING]", Attribute.BOLD, FColor.YELLOW, BColor.RED);
			cp.clear();
			cp.println(s);
			return;
		}
		if(s.contains("Termination")){
			// We assume that the message is split with a tab
			int tabPos = s.indexOf('\t');
			cp.print(s.substring(0,tabPos+1), Attribute.BOLD, FColor.RED, BColor.YELLOW);
			cp.clear();
			cp.print(s.substring(tabPos+1));

			cp.print("\n");
		}else{
			cp.clear();
			cp.println(s);
		}

	}

	public static void configlog4j(){
		String log4jConfPath = "log4j.properties";
		PropertyConfigurator.configure(log4jConfPath);
	}


	public void announce(int version){
		if(version == 1)
			tds1.announce();
		else if(version == 2)
			tds2.announce();
		else if(version == 3)
			tds3.announce();
		else if(version == 4)
			tds4.announce();
		else if(version == 5)
			tds5.announce();
		else if(version == 6)
			tds6.announce();
		else if(version == 7)
			tds7.announce();
	}

	public void setDone(int version){
		synchronized(this){
			done[version - 1] = true;
			notifyAll();
		}

	}

	private void waitAllDone(){
		synchronized(this){
			while(!(done[0] && done[1] && done[2] && done[3] && done[4] && done[5] && done[6])){
				try {
					wait();
				} catch (InterruptedException e) {
					break;
				}
			}
		}
	}

	public void start(){
		if(Options.instance().get(Options.VERSION) == 0){
			tds1 = new TDSOriginal(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds2 = new TDSImproved(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds3 = new TDSFaultTolerant(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds4 = new TDSStaticTree(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds5 = new TDSFTStaticTree(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds6 = new TDSDijkstraScholten(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			tds7 = new TDSLaiWu(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
			new Thread(tds1).start();
			new Thread(tds2).start();
			new Thread(tds3).start();
			new Thread(tds4).start();
			new Thread(tds5).start();
			new Thread(tds6).start();
			new Thread(tds7).start();
		}else{//ugly but ok for now
			done[0] = done[1] = done[2] = done[3] = done[4] = done[5] = done[6] = true;
			int version = Options.instance().get(Options.VERSION);
			String versionString = String.valueOf(version);

			if(versionString.contains("1")){
				tds1 = new TDSOriginal(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[0] = false;
				new Thread(tds1).start();
			}

			if(versionString.contains("2")){
				tds2 = new TDSImproved(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[1] = false;
				new Thread(tds2).start();
			}

			if(versionString.contains("3") ){
				tds3 = new TDSFaultTolerant(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[2] = false;
				new Thread(tds3).start();
			}
			if(versionString.contains("4") ){
				tds4 = new TDSStaticTree(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[3] = false;
				new Thread(tds4).start();
			}
			if(versionString.contains("5") ){
				tds5 = new TDSFTStaticTree(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[4] = false;
				new Thread(tds5).start();
			}
			if(versionString.contains("6") ){
				tds6 = new TDSDijkstraScholten(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[5] = false;
				new Thread(tds6).start();
			}
			if(versionString.contains("7") ){
				tds7 = new TDSLaiWu(Options.instance().get(Options.NUM_OF_NODES), Options.instance().get(Options.MAX_WAIT), Options.instance().get(Options.MAX_NUM_MESSAGES));
				done[6] = false;
				new Thread(tds7).start();
			}
		}


		waitAllDone();

		if(Options.instance().get(Options.FLUSH_CSV) == 1){
			try {
				PerformanceLogger.instance().clearCSV();
			} catch (IOException e) {
				System.out.println("Error flushing CSV:\n" + e.getMessage());
			}
		}
		if(Options.instance().get(Options.CSV) == 1){
			try {
				PerformanceLogger.instance().writeToCSV();
			} catch (IOException e) {
				System.out.println("Error writting CSV:\n" + e.getMessage());
			}
		}

	}

	public static void main(String[] args) {

		configlog4j();
		Options.instance().parse(args);
		TDS.instance.start();
	}


}
