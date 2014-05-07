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
import java.net.MalformedURLException;
import java.net.URL;
import java.util.NoSuchElementException;

import org.apache.commons.configuration.ConfigurationException;
import org.apache.commons.configuration.XMLConfiguration;

/**
 * Class encapsulating configuration information for a receiver resource.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 *
 */
public class ReceiverConfig extends ResourceConfig {

    private File _baseGrammarDir;
    private URL _sphinxConfigURL;
    private File _baseRecordingDir;


	private URL _sphinxRecorderConfigURL;
    private int _recorderEngines;

    /**
     * TODOC
     * @param index
     * @param config
     * @throws ConfigurationException 
     */
    public ReceiverConfig(int index, XMLConfiguration config) throws ConfigurationException {
        super(index, config);
        try {
            try {
                String sphinxConfigURL = config.getString("resources.resource(" + index + ").sphinxConfigURL");
                if (sphinxConfigURL != null && sphinxConfigURL.length() > 0) {
                    _sphinxConfigURL = new URL(sphinxConfigURL);
                }
            } catch (NoSuchElementException e) {
                // ignore
            }
            if (_sphinxConfigURL == null) {
                _sphinxConfigURL = this.getClass().getResource("/config/sphinx-config.xml");
                if (_sphinxConfigURL == null) {
                    throw new ConfigurationException("Sphinx config URL not found in either cairo config file or cairo classpath!");
                } else if (_logger.isDebugEnabled()) {
                    _logger.debug("SphinxConfigURL: " + _sphinxConfigURL);
                }
            }
            
            try {
                String sphinxRecorderConfigURL = config.getString("resources.resource(" + index + ").sphinxRecorderConfigURL");
                if (sphinxRecorderConfigURL != null && sphinxRecorderConfigURL.length() > 0) {
                	_sphinxRecorderConfigURL = new URL(sphinxRecorderConfigURL);
                }
            } catch (NoSuchElementException e) {
                // ignore
            }
            if (_sphinxRecorderConfigURL == null) {
            	_sphinxRecorderConfigURL = this.getClass().getResource("/config/sphinx-recorder-config.xml");
                if (_sphinxRecorderConfigURL == null) {
                    throw new ConfigurationException("Sphinx recorder config URL not found in either cairo config file or cairo classpath!");
                } else if (_logger.isDebugEnabled()) {
                    _logger.debug("SphinxRecorderConfigURL: " + _sphinxRecorderConfigURL);
                }
            }
            
            _baseGrammarDir = new File(config.getString("resources.resource(" + index + ").baseGrammarDir"));
            ensureDir(_baseGrammarDir);
            _baseRecordingDir = new File(config.getString("resources.resource(" + index + ").baseRecordingDir"));
            ensureDir(_baseRecordingDir);
            _recorderEngines = config.getInt("resources.resource(" + index + ").recorderEngines");
            
        } catch (RuntimeException e) {
            throw new ConfigurationException(e.getMessage(), e);
        } catch (MalformedURLException e) {
            throw new ConfigurationException(e.getMessage(), e);
        }
    }


    /**
     * TODOC
     * @return Returns the baseGrammarDir.
     */
    public File getBaseGrammarDir() {
        return _baseGrammarDir;
    }

    /**
     * TODOC
     * @return Returns the baseRecordingDir.
     */
    public File getBaseRecordingDir() {
        return _baseRecordingDir;
    }

    /**
     * TODOC
     * @return Returns the sphinxConfigURL.
     */
    public URL getSphinxConfigURL() {
        return _sphinxConfigURL;
    }

    /**
     * @return the _sphinxRecorderConfigURL
     */
    public URL getSphinxRecorderConfigURL() {
    	return _sphinxRecorderConfigURL;
    }


	/**
     * @return the _recorderEngines
     */
    public int getRecorderEngines() {
    	return _recorderEngines;
    }
    
}
