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

import org.mrcp4j.MrcpResourceType;

/**
 * Encapsulates the parameters required for establishing and managing connections to resource implementations.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ResourceChannel implements Serializable {
    
    private MrcpResourceType _resourceType = null;
    private String _channelID = null;
    private int _port = -1;

    /**
     * @return Returns the resourceType.
     */
    public MrcpResourceType getResourceType() {
        return _resourceType;
    }

    /**
     * @param resourceType The resourceType to set.
     */
    public void setResourceType(MrcpResourceType resourceType) {
        _resourceType = resourceType;
    }

    /**
     * @return Returns the channelID.
     */
    public String getChannelID() {
        return _channelID;
    }

    /**
     * @param channelID The channelID to set.
     */
    public void setChannelID(String channelID) {
        _channelID = channelID;
    }

    /**
     * @return Returns the port.
     */
    public int getMrcpPort() {
        return _port;
    }

    /**
     * @param port The port to set.
     */
    public void setMrcpPort(int port) {
        _port = port;
    }

}
