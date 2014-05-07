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

import java.io.File;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;
import org.apache.log4j.Logger;

/**
 * Base class for specific resource type configurations.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 *
 */
public abstract class ResourceConfig {

    static Logger _logger = Logger.getLogger(ResourceConfig.class);

    private int _mrcpPort;
    private int _rtpBasePort;
    private int _maxConnects;
    private int _engines;

	private String _ipAddress = null;

    /**
     * TODOC
     * @param index 
     * @param config 
     */
    public ResourceConfig(int index, XMLConfiguration config) {
        _mrcpPort = config.getInt("resources.resource(" + index + ").mrcpPort");
        _rtpBasePort = config.getInt("resources.resource(" + index + ").rtpBasePort");
        _maxConnects = config.getInt("resources.resource(" + index + ").maxConnects");
        _engines = config.getInt("resources.resource(" + index + ").engines");
        _ipAddress = config.getString("resources.resource(" + index + ").ipAddress");
    }

	public String getIpAddress() {
		return _ipAddress;
	}

	public void setIpAddress(String ipAddress) {
		this._ipAddress = ipAddress;
	}

    
    /**
     * TODOC
     * @return Returns the maxConnects.
     */
    public int getMaxConnects() {
        return _maxConnects;
    }

    /**
     * TODOC
     * @return Returns the mrcpPort.
     */
    public int getMrcpPort() {
        return _mrcpPort;
    }

    /**
     * TODOC
     * @return Returns the rtpBasePort.
     */
    public int getRtpBasePort() {
        return _rtpBasePort;
    }

    /**
     * TODOC
     * @return Returns the recEngines.
     */
    public int getEngines() {
        return _engines;
    }

    /**
     * TODOC
     * @param config
     * @param key
     * @return
     * @throws ConfigurationException
     */
    public static File getConfigDir(XMLConfiguration config, String key) throws ConfigurationException {
        try {
            File dir = new File(config.getString(key));
            ensureDir(dir);
            return dir;
        } catch (RuntimeException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }

    /**
     * TODOC
     * @param dir
     * @throws ConfigurationException
     */
    public static void ensureDir(File dir) throws ConfigurationException {
        
        // try to create directory if it does not exist
        if (!dir.exists() && !dir.mkdirs()) {
            throw new ConfigurationException(
                "Could not create directory: " + dir.getAbsolutePath());
        }

        // make sure dir is actually a directory
        if (!dir.isDirectory()) {
            throw new ConfigurationException(
                "The specified path is not a directory: " + dir.getAbsolutePath());
        }
    }

}
