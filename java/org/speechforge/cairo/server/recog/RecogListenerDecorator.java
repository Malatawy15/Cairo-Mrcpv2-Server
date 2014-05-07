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

import org.apache.log4j.Logger;

/**
 * Delegates method calls to an underlying {@link org.speechforge.cairo.server.recog.RecogListener} implementation.
 * Can be subclassed to intercept calls to the decorated object.
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class RecogListenerDecorator implements RecogListener {

    private static Logger _logger = Logger.getLogger(RecogListenerDecorator.class);

    private RecogListener _recogListener;

    /**
     * TODOC
     * @param recogListener 
     */
    public RecogListenerDecorator(RecogListener recogListener) {
        _recogListener = recogListener;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.RecogListener#speechStarted()
     */
    public void speechStarted() {
        _logger.debug("speechStarted()");
        if (_recogListener != null) {
            _recogListener.speechStarted();
        }
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.RecogListener#recognitionComplete()
     */
    public void recognitionComplete(RecognitionResult result) {
        _logger.debug("recognitionComplete()");
        if (_recogListener != null) {
            _recogListener.recognitionComplete(result);
        }
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.RecogListener#noInputTimeout()
     */
    public void noInputTimeout() {
        _logger.debug("noInputTimeout()");
        if (_recogListener != null) {
            _recogListener.noInputTimeout();
        }
    }

}
