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
import org.speechforge.cairo.util.CairoUtil;

import java.util.List;
import java.net.InetAddress;
import java.rmi.Naming;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.rmi.server.RMIClientSocketFactory;
import java.rmi.server.RMIServerSocketFactory;
import java.rmi.server.UnicastRemoteObject;
import java.util.ArrayList;

import org.apache.log4j.Logger;

/**
 * Implements a {@link org.speechforge.cairo.server.resource.ResourceRegistry} that can be
 * used to register resources with the resource server.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class ResourceRegistryImpl extends UnicastRemoteObject implements ResourceRegistry {

    private static Logger _logger = Logger.getLogger(ResourceRegistryImpl.class);

    private ResourceList _receivers = new ResourceList();
    private ResourceList _transmitters = new ResourceList();

    /**
     * TODOC
     * @throws RemoteException
     */
    public ResourceRegistryImpl() throws RemoteException {
        super();
        // TODO Auto-generated constructor stub
    }

    /**
     * TODOC
     * @param port
     * @throws RemoteException
     */
    public ResourceRegistryImpl(int port) throws RemoteException {
        super(port);
        // TODO Auto-generated constructor stub
    }

    /**
     * TODOC
     * @param port
     * @param csf
     * @param ssf
     * @throws RemoteException
     */
    public ResourceRegistryImpl(int port, RMIClientSocketFactory csf,
            RMIServerSocketFactory ssf) throws RemoteException {
        super(port, csf, ssf);
        // TODO Auto-generated constructor stub
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.manager.ResourceRegistry#hello(java.lang.String)
     */
    public String hello(String name) throws RemoteException {
        String greeting = "Hello " + name;
        _logger.debug(greeting);
        //if (_resource != null) {
            //_logger.debug(_resource.hello("registry"));
        //}
        return greeting;
    }
    
    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.manager.ResourceRegistry#bind(org.speechforge.cairo.server.resource.Resource)
     */
    public synchronized void register(Resource resource, Resource.Type type) throws RemoteException {

        if (_logger.isDebugEnabled()) {
            _logger.debug("register(): registering resource of type " + type);
        }

        switch (type) {
        case RECEIVER:
            _receivers.register(resource);
            break;

        case TRANSMITTER:
            _transmitters.register(resource);
            break;

        default:
            throw new IllegalArgumentException("Invalid type or type not specified!");
        }
    }

    public Resource getResource(Resource.Type type) throws ResourceUnavailableException {
        switch (type) {
        case RECEIVER:
            return _receivers.getResource();

        case TRANSMITTER:
            return _transmitters.getResource();

        default:
            throw new IllegalArgumentException("Invalid type or type not specified!");
        }
    }
    
    private static class ResourceList {

        private List<Resource> _resources = new ArrayList<Resource>();
        private int _index = 0;

        public synchronized void register(Resource resource) {
            _resources.add(resource);
        }

        public synchronized Resource getResource() throws ResourceUnavailableException {
            int size;
            while ((size = _resources.size()) > 0) {
                if (_index >= size) {
                    _index = 0;
                }
                Resource resource = _resources.get(_index);
                try {
                    resource.ping();
                    _index++;
                    return resource;
                } catch (RemoteException e) {
                    _logger.debug(e, e);
                    _resources.remove(_index);
                }
            }
            throw new ResourceUnavailableException("No resource available for the specified type!");
        }
        
    }

    public static void main(String[] args) throws Exception {
        ResourceRegistryImpl impl = new ResourceRegistryImpl();

        /*InetAddress host = CairoUtil.getLocalHost();
        String url = "rmi://" + host.getHostName() + '/' + NAME;
        _logger.info("(re)binding to: " + url);
        Naming.rebind(url, impl);*/

        Registry registry = LocateRegistry.createRegistry(Registry.REGISTRY_PORT);
        registry.rebind(NAME, impl);

        _logger.info("ResourceRegistry bound and waiting...");
        Thread.sleep(90000);
    }

    /**
     * Provides a client for testing {@link org.speechforge.cairo.server.resource.ResourceRegistryImpl}.
     *
     * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
     */
    public static class TestClient {

        public static void main(String[] args) throws Exception {
            InetAddress host = CairoUtil.getLocalHost();
            String url = "rmi://" + host.getHostName() + '/' + NAME;
            _logger.info("looking up: " + url);
            ResourceRegistry rr = (ResourceRegistry) Naming.lookup(url);
            _logger.info(rr.hello("Niels"));
        }

    }

}
