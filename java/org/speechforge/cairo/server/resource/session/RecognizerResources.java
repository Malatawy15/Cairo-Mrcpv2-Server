package org.speechforge.cairo.server.resource.session;

import java.rmi.RemoteException;

import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.server.recog.RTPRecogChannel;


public class RecognizerResources extends ChannelResources {

     
	//resource to be cleaned up for a recog channels
    private RTPStreamReplicator replicator;
    private RTPRecogChannel recog;
    

    /**
     * @return the rep
     */
    public RTPStreamReplicator getReplicator() {
        return replicator;
    }
    /**
     * @param rep the rep to set
     */
    public void setReplicator(RTPStreamReplicator rep) {
        this.replicator = rep;
    }

    /**
     * @return the recog
     */
    public RTPRecogChannel getRecog() {
        return recog;
    }
    /**
     * @param recog the recog to set
     */
    public void setRecog(RTPRecogChannel recog) {
        this.recog = recog;
    }

	
}