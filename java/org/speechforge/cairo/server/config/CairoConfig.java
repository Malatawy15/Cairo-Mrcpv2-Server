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
package org.speechforge.cairo.server.config;

import java.net.URL;
import java.util.List;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

/**
 * Class encapsulating configuration information for a Cairo deployment.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 *
 */
public class CairoConfig {

    XMLConfiguration _config;

    /**
     * TODOC
     * @param cairoConfigURL 
     * @throws ConfigurationException 
     */
    public CairoConfig(URL cairoConfigURL) throws ConfigurationException {
        _config = new XMLConfiguration(cairoConfigURL);
    }

    /**
     * TODOC
     * @param name
     * @return
     * @throws ConfigurationException
     */
    public ReceiverConfig getReceiverConfig(String name) throws ConfigurationException {
        return new ReceiverConfig(this.getConfigIndex(name), _config);
    }

    /**
     * TODOC
     * @param name
     * @return
     * @throws ConfigurationException
     */
    public TransmitterConfig getTransmitterConfig(String name) throws ConfigurationException {
        return new TransmitterConfig(this.getConfigIndex(name), _config);
    }

    private int getConfigIndex(String name) throws ConfigurationException {
        List<String> resourceNames = _config.getList("resources.resource.name");
        for (int i = 0; i < resourceNames.size(); i++) {
            String resourceName = resourceNames.get(i);
            if (resourceName.equalsIgnoreCase(name)) {
                return i;
            }
        }
        throw new ConfigurationException("Specified name \"" + name + "\" not found in configuration!");
    }
}
