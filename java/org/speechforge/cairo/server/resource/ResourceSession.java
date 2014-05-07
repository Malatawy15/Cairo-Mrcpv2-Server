/*
 * Cairo - Open source framework for control of speech media resources.
 *
 * Copyright (C) 2005-2006 SpeechForge - http://www.speechforge.org
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA  02110-1301  USA
 *
 * Contact: ngodfredsen@users.sourceforge.net
 *
 */
package org.speechforge.cairo.server.resource;

import java.util.Hashtable;
import java.util.Map;
import org.apache.log4j.Logger;
import org.speechforge.cairo.server.recog.RTPRecogChannel;
import org.speechforge.cairo.server.recorder.RTPRecorderChannel;
import org.speechforge.cairo.server.resource.session.ChannelResources;
import org.speechforge.cairo.rtp.server.RTPStreamReplicator;
import org.speechforge.cairo.server.tts.RTPSpeechSynthChannel;
import org.speechforge.cairo.sip.SdpMessage;

/**
 * Represents a SIP session.
 * 
 * @author Spencer Lord {@literal <}<a href="mailto:salord@users.sourceforge.net">salord@users.sourceforge.net</a>{@literal >}
 */
public class ResourceSession {

    private static Logger _logger = Logger.getLogger(ResourceSession.class);

    private String Id;
    private  Map<String, ChannelResources> channels;
    private SdpMessage lastInvite;
    
    private static Map<String, ResourceSession> sessions = new Hashtable<String, ResourceSession>();
    
    public ResourceSession(){
        channels = new Hashtable<String, ChannelResources>();
    }
    
    public String getId() {
        return Id;
    }
    
    public void setID(String id) {
        this.Id = id;
    }
 
    /**
     * @return the lastRequest
     */
    public SdpMessage getlastInvite() {
        return lastInvite;
    }


    /**
     * @param lastRequest the lastRequest to set
     */
    public void setLastInvite(SdpMessage lastInvite) {
        this.lastInvite = lastInvite;
    }

    /**
     * @return the channels
     */
    public Map<String, ChannelResources> getChannels() {
        return channels;
    }

    /**
     * @param channels the channels to set
     */
    public void setChannels(Map<String, ChannelResources> channels) {
        this.channels = channels;
    }

 
    
    
    //------------------------------------------------------------------------
    //Static Methods.  Factory method and methods to manage the sessions maps. 
    //------------------------------------------------------------------------
    
    public static ResourceSession createResourceSession(String id) {
        ResourceSession s = new ResourceSession();
        s.setID(id);
        return s;
    }
    
    public static  void addSession(ResourceSession session) {
        if (session.getId() != null) {
            sessions.put(session.getId(), session);
        } else {
            // TODO: invalid session
            _logger.info("Can not add to session queue.  Invalid session.  No ID.");
        }
    }

    public static  void removeSession(ResourceSession session) {
        session.getChannels().clear();
        if (session.getId() != null) {
            sessions.remove(session.getId());
        } else {
            // TODO: invalid session
            _logger.info("Can not remove from session queue.  Invalid session.  No ID.");
        }
    }

    public static  ResourceSession getSession(String key) {
        return sessions.get(key);
    }

}
