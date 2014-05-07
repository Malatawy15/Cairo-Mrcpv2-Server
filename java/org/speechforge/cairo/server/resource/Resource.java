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

import org.speechforge.cairo.exception.ResourceUnavailableException;
import org.speechforge.cairo.sip.SdpMessage;
import org.speechforge.cairo.sip.SipResource;
import org.speechforge.cairo.sip.SipSession;

import java.rmi.Remote;
import java.rmi.RemoteException;

import org.mrcp4j.MrcpResourceType;

/**
 * Defines the methods required for establishing and managing connections to resource implementations.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public interface Resource extends Remote, SipResource {

    public void ping() throws RemoteException;

    //public SdpMessage invite(SdpMessage request, String sessionId) throws ResourceUnavailableException, RemoteException;
    
    //public void bye(String sessionId) throws  RemoteException, InterruptedException;

    /**
     * Defines whether a resource receives audio input or transmits audio output.
     * 
     * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
     *
     */
    public static enum Type {

        /**
         * Resource that receives RTP data.
         */
        RECEIVER,

        /**
         * Resource that transmits RTP data.
         */
        TRANSMITTER;

        /**
         * Converts an MRCP resource type to a Cairo resource type.
         * @param resourceType the MRCP resource type.
         * @return the resource type (RECEIVER or TRANSMITTER) corresponding to the provide MRCP resource type. 
         * @throws ResourceUnavailableException if the MRCP resource type is not supported by Cairo.
         */
        public static Resource.Type fromMrcpType(MrcpResourceType resourceType) throws ResourceUnavailableException {
            switch (resourceType) {
            case SPEECHSYNTH:
                return Resource.Type.TRANSMITTER;

            case RECORDER:
            case SPEECHRECOG:
                return Resource.Type.RECEIVER;

            default:
                throw new ResourceUnavailableException("Unsupported resource type!");
            }
        }
    }

}
