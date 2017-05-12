package algo.ifss.network;


import java.util.Random;

import ibis.util.ThreadPool;
import main.TDS;
import performance.PerformanceLogger;
import algo.ifss.node.NodeMessage2;
import algo.ifss.node.NodeRunner2;
import algo.ifss.probing.ProbeMessage2;
import algo.ifss.probing.Prober2;

public class Network2 {
    
    private final int nnodes;
		private final int max_messages;
    private final NodeRunner2[] nodeRunners;
    private final Prober2[] probers;
    private Random random;
    private int nodeCount;
    protected long lastPassive;
    
		private int nrBMessages;
		private int nrCMessages;
    
    public Network2(int nnodes, int max_messages){
        this.nnodes = nnodes;
        this.max_messages = max_messages;
        nodeCount = 0;
        random = new Random();
        nodeRunners = new NodeRunner2[nnodes];
        probers = new Prober2[nnodes];
				this.nrBMessages = 0;
				this.nrCMessages = 0;
    }
    
    public synchronized void waitForAllNodes() {
        while(nodeCount < nnodes) {
            try {
                wait();
            } catch (InterruptedException e){
                break;
            }
        }
        
        for(NodeRunner2 r: nodeRunners) {
            r.start();
        }
    }
    
    public synchronized void registerNode(NodeRunner2 nodeRunner) {
        nodeRunners[nodeRunner.getId()] = nodeRunner;
        probers[nodeRunner.getId()] = new Prober2(nodeRunner.getId(), nnodes, this, nodeRunner);
        nodeRunner.attachProber(probers[nodeRunner.getId()]);
        nodeCount++;
        if(nodeCount == nnodes) {
            notifyAll();
        }
    }
    
    // Send message with random delay. Execute in separate thread to not delay the sender with it.
    public void sendMessage(final int dest, final NodeMessage2 nodeMessage) {
        nrBMessages += 1;
        ThreadPool.createNew(() -> {
            int delay = random.nextInt(50);
            try { Thread.sleep(delay); } catch (InterruptedException e){}
            nodeRunners[dest].receiveMessage(nodeMessage);
        }, "Sender5");
    }
    //To be called when termination is detected
    public void killNodes() {
        for(NodeRunner2 nr : nodeRunners )
            nr.stopRunning();
    }
    
    
    // Send the token and receive it after random delay. Execute in seperate thread to not delay the sender.
    public void sendProbeMessage(final int dest, final ProbeMessage2 probeMessage) {
        nrCMessages += 1;
        ThreadPool.createNew(() -> {
            int delay = random.nextInt(50); //Maybe increase that delay as token is larger
            try { Thread.sleep(delay); } catch (InterruptedException e){}
            probers[dest].receiveMessage(probeMessage);
        }, "ProbeSender5");
    }
    
    public void sendFirstProbeMessage(final int dest, final ProbeMessage2 probeMessage) {
        ThreadPool.createNew(() -> {
            //int delay = random.nextInt(50); //Maybe increase that delay as token is larger
            //try { Thread.sleep(delay); } catch (InterruptedException e){}
            probers[dest].receiveFirstMessage(probeMessage);
        }, "ProbeSender5");
    }
    
    public int selectTarget(int mynode) {
        if(nnodes == 1) {
            System.out.println("WARNING: nnodes = 1, node 0 about to send msg to itself!");
            return mynode;
        }
        
        for(;;){
            int dest = random.nextInt(nnodes);
            if(dest != mynode) return dest;
        }
    }
    
		public boolean allowedToSend() {
			return max_messages == -1 || nrBMessages < max_messages;
		}
    
    public void registerPassive() {
        ThreadPool.createNew(() -> {
            synchronized (Network2.class) {
                lastPassive = System.currentTimeMillis();
                PerformanceLogger.instance().setTokensUpToTerm(2);
                //PerformanceLogger.instance().setBackupTokensUpToTerm(5);
            }
        }, "PassiveRegister5");
    }

    public long getLastPassive() {
        return this.lastPassive;
    }

		public void printStatistics() {
			TDS.writeString(2, " Network:\tNumber of basic messages:\t" + nrBMessages);
			TDS.writeString(2, " Network:\tNumber of control messages:\t" + nrCMessages);
		}

}
