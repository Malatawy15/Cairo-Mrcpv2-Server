package org.speechforge.cairo.server.resource.session;

import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.server.recorder.RTPRecorderChannel;

public class RecorderResources extends ChannelResources {

    //resource to be cleaned up for a recorder channels
    private RTPStreamReplicator recorderReplicator;
	private RTPRecorderChannel recorder;        
    
    
    
    /**
     * @return the recorder
     */
    public RTPRecorderChannel getRecorder() {
    	return recorder;
    }
	/**
     * @param recorder the recorder to set
     */
    public void setRecorder(RTPRecorderChannel recorder) {
    	this.recorder = recorder;
    }
    
    /**
     * @return the recorderReplicator
     */
    public RTPStreamReplicator getRecorderReplicator() {
    	return recorderReplicator;
    }
	/**
     * @param recorderReplicator the recorderReplicator to set
     */
    public void setRecorderReplicator(RTPStreamReplicator recorderReplicator) {
    	this.recorderReplicator = recorderReplicator;
    }

  
}