package org.speechforge.cairo.server.resource.session;

import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.server.recog.RTPRecogChannel;
import org.speechforge.cairo.server.recorder.RTPRecorderChannel;
import org.speechforge.cairo.server.tts.RTPSpeechSynthChannel;

public class TransmitterResources extends ChannelResources {
  
    //resources needed to be cleaned up for synth channels
    private int port;
    private RTPSpeechSynthChannel rtpssc;
    
    /**
     * @return the port
     */
    public int getPort() {
        return port;
    }
    /**
     * @param port the port to set
     */
    public void setPort(int port) {
        this.port = port;
    }

    /**
     * @return the rtpssc
     */
    public RTPSpeechSynthChannel getRtpssc() {
        return rtpssc;
    }
    /**
     * @param rtpssc the rtpssc to set
     */
    public void setRtpssc(RTPSpeechSynthChannel rtpssc) {
        this.rtpssc = rtpssc;
    }
}