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

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Encapsulates the parameters required for establishing the connections between MRCPv2 clients and MRCPv2 resources.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ResourceMessage implements Serializable {
    
    private String _callId = null;
    private List<ResourceChannel> _channels = new ArrayList<ResourceChannel>();
    private ResourceMediaStream _mediaStream;
    /**
     * TODOC
     * @return Returns the callId.
     */
    public String getCallId() {
        return _callId;
    }
    /**
     * TODOC
     * @param callId The callId to set.
     */
    public void setCallId(String callId) {
        _callId = callId;
    }
    /**
     * TODOC
     * @return Returns the channels.
     */
    public List<ResourceChannel> getChannels() {
        return _channels;
    }
    /**
     * TODOC
     * @param channels The channels to set.
     */
    public void setChannels(List<ResourceChannel> channels) {
        _channels = channels;
    }
    /**
     * TODOC
     * @return Returns the mediaStream.
     */
    public ResourceMediaStream getMediaStream() {
        return _mediaStream;
    }
    /**
     * TODOC
     * @param mediaStream The mediaStream to set.
     */
    public void setMediaStream(ResourceMediaStream mediaStream) {
        _mediaStream = mediaStream;
    }

}
