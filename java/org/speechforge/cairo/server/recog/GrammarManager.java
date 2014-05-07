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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang.Validate;

/**
 * Manages the storage and retrieval of temporary grammar files on the file system.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class GrammarManager {

    private Map<String, GrammarLocation> _grammars = new HashMap<String, GrammarLocation>();

    private File _grammarDir;
    private URL _grammarDirUrl;

    /**
     * TODOC
     * @param channelID 
     * @param baseGrammarDir 
     */
    public GrammarManager(String channelID, File baseGrammarDir) {
        Validate.isTrue(baseGrammarDir.isDirectory(), "baseGrammarDir parameter was not a directory: ", baseGrammarDir);
        _grammarDir = new File(baseGrammarDir, channelID);
        if (!_grammarDir.mkdir()) {
            throw new IllegalArgumentException("Specified directory not valid: " + _grammarDir.getAbsolutePath());
        }
        try {
            _grammarDirUrl = _grammarDir.toURL();
        } catch (MalformedURLException e) {
            throw new IllegalArgumentException("Specified directory not valid: " + _grammarDir.getAbsolutePath(), e);
        }
    }
    
    /**
     * TODOC
     * @param grammarID
     * @param grammarText
     * @return
     * @throws IOException
     */
    // NOTE: could reduce sync scope but not necessary since generally single threaded access
    public synchronized GrammarLocation saveGrammar(String grammarID, String grammarText)
      throws IOException {

        // generate grammar name and location
        String grammarName = Long.toString(System.currentTimeMillis());
        GrammarLocation location = new GrammarLocation(_grammarDirUrl, grammarName);

        // write grammar to filesystem
        File grammarFile = new File(_grammarDir, location.getFilename());
        FileWriter fw = new FileWriter(grammarFile);
        try {
            fw.write(grammarText);
        } finally {
            fw.close();
        }

        if (grammarID != null && grammarID.length() > 0) {
            // store for future reference in session
            _grammars.put(grammarID, location);
        }

        return location;
    }

    /**
     * TODOC
     * @param grammarID
     * @return
     */
    public synchronized GrammarLocation getGrammarLocation(String grammarID) {
        return _grammars.get(grammarID);
    }

}
