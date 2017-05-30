package algo.ifss.node;

import java.util.Random;

import util.Options;
import main.TDS;

import util.LamportClock;

import algo.ifss.network.Network2;
import algo.ifss.probing.Prober2;

public class NodeRunner2 implements Runnable{
    
    private final int mynode;
    private final int nnodes;
    private final NodeState2 state;
    private final Network2 network;
    private boolean mustStop = false;
    private Random random = new Random();
    private boolean started = false;
    private boolean isPassive = true;
    private Prober2 prober;
    
    
    public NodeRunner2(int mynode, int nnodes, Network2 network, boolean initiallyActive){
        this.mynode = mynode;
        this.nnodes = nnodes;
        this.isPassive = !initiallyActive;
        this.state = new NodeState2(!initiallyActive, mynode, nnodes);
        network.registerNode(this);
        this.network = network;
        Thread t = new Thread(this);
        t.start();
    }
    
    public void attachProber(Prober2 p){ this.prober = p; }
    public int getId(){ return mynode; }
    public NodeState2 getState() { return this.state.copy(); }
    public synchronized int getSeq() { return state.getSeq(); }
    public synchronized void incSeq() { state.incSeq(); }
    public synchronized int getBlack() { return this.state.getBlack(); }
    public synchronized void setBlack(int node) { this.state.setBlack(node); };
    public synchronized void setCount(int c) {this.state.setCount(c);} 
    
    public synchronized void stopRunning() {
        if(!state.isPassive()) {
            TDS.writeString(-2," [I-FSS]\tGot stopRunning message but was not passive!");
        }
        mustStop = true;
        notifyAll();
    }
    
    private void writeString(String s) {
        TDS.writeString(2, " Node " + mynode + ": \t" + s);
    }
    
    public synchronized void receiveMessage(NodeMessage2 m) {
        writeString("received message from " + m.getSenderId());
				updateClock(m.getLc());
        this.state.setPassive(false);
        this.isPassive = false;
        notifyAll();
        NodeState2 state = this.state.copy();
        if( (m.getSenderId() < mynode && m.getSeq() == state.getSeq() + 1)  ||
                (m.getSenderId() > mynode && m.getSeq() == state.getSeq())   ){
            this.state.setBlack(furthest(state.getBlack(), m.getSenderId()));
            
        }
        this.state.decCount();
        notifyAll(); //maybe not needed
    }
    
    private void sendMessage(int node) {
        writeString("send a message to " + node);
        network.sendMessage(node, new NodeMessage2(mynode, this.state.getSeq(), this.state.getLc()));
        this.state.incCount();
				incClock();
    }
    
    
    
    public int furthest(int j, int k) {
        return ((mynode <= j && j <= k) || (k < mynode && mynode <= j) || (j <= k && k < mynode))? k : j;
    }
    
    
    @Override
    public void run() {
        writeString("Thread started");
        waitUntilStarted();
        writeString("Cluster started");
        
        while(!shouldStop()){
            synchronized(this) {
                while(state.isPassive()){
                    try {wait(); }catch(InterruptedException e) {}
                    if(shouldStop()) return;
                }
            }
            
            writeString("becoming active");
            activity();
            prober.nodeRunnerStopped();
            
            synchronized(this){ isPassive = true; notifyAll(); }
            state.setPassive(true);
            writeString("becoming passive");
            network.registerPassive();
        }
    }
    
    private synchronized void waitUntilStarted() {
        while(!started)
            try { wait(); } catch(InterruptedException e){break;}//maybe remove break
    }
    
    private void activity() {
        writeString("starting activity");
        int level = Options.instance().get(Options.ACTIVITY_LEVEL);
        int nActivities = 1 + random.nextInt(level);
        for(int i = 0; i < nActivities; i++) {
            int timeToSleep = random.nextInt(1000); //computation lol
            try { Thread.sleep(timeToSleep); } catch(InterruptedException e) {}
            
            int nMessages = random.nextInt(level) + (this.mynode == 0? 1 : 0);
            
            for (int j = 0; j < nMessages && network.allowedToSend(); j++)
                sendMessage(network.selectTarget(mynode));
        }
    }
    
    private synchronized boolean shouldStop() {
        return mustStop;
    }
    
    // Called when all nodes are added to the network.
    public synchronized void start(){
        started = true;
        notifyAll();
    }
    
    public boolean isStarted() {
        return this.started;
    }
    
    public synchronized boolean isPassive(){
        return state.isPassive();
    }
    
		public synchronized void incClock() {
			this.state.incClock();
		}

		public synchronized void updateClock(LamportClock other) {
			this.state.updateClock(other);
		}
}
