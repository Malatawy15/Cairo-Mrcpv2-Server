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
package org.speechforge.cairo.server.recog;

import java.net.MalformedURLException;
import java.net.URL;

/**
 * Encapsulates the parameters that specify the location of a grammar file.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class GrammarLocation {

    public static final String DEFAULT_EXTENSION = "gram";

    URL _baseURL;
    String _grammarName;
    String _extension;

    public GrammarLocation(URL baseURL, String grammarName) {
        this(baseURL, grammarName, DEFAULT_EXTENSION);
    }

    public GrammarLocation(URL baseURL, String grammarName, String extension) {
        _baseURL = baseURL;
        _grammarName = grammarName;
        _extension = extension;
    }

    public GrammarLocation(URL grammarURL) {
        String location = grammarURL.toExternalForm().replace('\\', '/');
        int indexExtension = location.lastIndexOf('.');
        int indexName = location.lastIndexOf('/');
        if (indexExtension < 1 || indexExtension <= indexName) {
            throw new IllegalArgumentException("Improperly specified grammar url: " + location);
        }

        try {
            _baseURL = new URL(location.substring(0, indexName));
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Improperly specified grammar url: " + location, e);
        }
        _grammarName = location.substring(indexName+1, indexExtension);
        _extension = location.substring(+1);
    }

    /**
     * TODOC
     * @return Returns the baseURL.
     */
    public URL getBaseURL() {
        return _baseURL;
    }

    /**
     * TODOC
     * @return Returns the grammarName.
     */
    public String getGrammarName() {
        return _grammarName;
    }

    /**
     * TODOC
     * @return Returns the extension.
     */
    public String getExtension() {
        return _extension;
    }

    /**
     * TODOC
     * @return Returns the extension.
     */
    public String getFilename() {
        return new StringBuilder(_grammarName).append('.').append(_extension).toString();
    }


}