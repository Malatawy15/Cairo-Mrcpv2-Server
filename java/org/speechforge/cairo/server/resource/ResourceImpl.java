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


import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;

import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;
import org.speechforge.cairo.sip.SipResource;

/**
 * Base class for resource implementations.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public abstract class ResourceImpl extends UnicastRemoteObject implements Resource {

    private static Logger _logger = Logger.getLogger(ResourceImpl.class);

    public static final String HELP_OPTION = "help";
    public static final String RSERVERHOST_OPTION = "rserverhost";
    public static final String LOCALHOST_OPTION = "localhost";

    private Type _type;
    
    /**
     * TODOC
     * @param type whether the resource is to receive audio input or transmit audio output
     * @throws RemoteException
     */
    public ResourceImpl(Type type) throws RemoteException {
        _type = type;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.manager.Resource#hello(java.lang.String)
     */
    public void ping() {
        _logger.debug("Resource received ping() request.");
    }

    /*protected boolean supports(MrcpResourceType resourceType) throws ResourceUnavailableException {
        Type type = translateType(resourceType);
        return type.equals(_type);
    }*/

    public static Options getOptions() {
        Options options = new Options();

        Option option = new Option(HELP_OPTION, "print this message");
        options.addOption(option);

        option = new Option(RSERVERHOST_OPTION, true, "location of resource server (defaults to localhost)");
        option.setArgName("host");
        options.addOption(option);
        
        option = new Option(LOCALHOST_OPTION, true, "location of local server (defaults to localhost)");
        option.setArgName("lhost");
        options.addOption(option);

        return options;
    }

}
