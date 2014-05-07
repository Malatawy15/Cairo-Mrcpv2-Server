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
package org.speechforge.cairo.server.tts;

import java.io.IOException;

import javax.media.CannotRealizeException;
import javax.media.Controller;
import javax.media.ControllerClosedEvent;
import javax.media.ControllerEvent;
import javax.media.ControllerListener;
import javax.media.EndOfMediaEvent;
import javax.media.Processor;
import javax.media.format.UnsupportedFormatException;
import javax.media.protocol.DataSource;
import javax.media.rtp.RTPManager;
import javax.media.rtp.SendStream;

import org.apache.log4j.Logger;

/**
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
@Deprecated public class RTPProcessorManager implements ControllerListener {

    private static Logger _logger = Logger.getLogger(RTPProcessorManager.class);

    private /*volatile*/ boolean closed = false;

    private RTPManager _rtpManager;
    private Processor _processor;

    /**
     * TODOC
     * @param manager 
     * @param processor 
     */
    public RTPProcessorManager(RTPManager manager, Processor processor) {
        _rtpManager = manager;
        _processor = processor;
        _processor.addControllerListener(this);
    }

    public synchronized void configure() throws IOException, InterruptedException {
        _processor.configure();
        while(_processor.getState() < Processor.Configured  && !closed) {
            this.wait();
        }
        if (closed) {
            throw new IOException("Processor closed unexpectedly!");
        }
    }

    public synchronized void realize() throws CannotRealizeException, InterruptedException {
       _processor.realize();
       while(_processor.getState() < Controller.Realized  && !closed) {
           this.wait();
       }
       if (closed) {
           throw new CannotRealizeException("Processor closed unexpectedly!");
       }
    }

    public synchronized void play() throws UnsupportedFormatException, IOException, InterruptedException {

        InterruptedException ie = null;

        DataSource dataOutput = _processor.getDataOutput();
        SendStream sendStream = _rtpManager.createSendStream(dataOutput, 0);
        sendStream.start();
        _processor.start();

        while(!closed) {
            try {
                this.wait();
            } catch (InterruptedException e) {
                if (ie == null) {
                    ie = e;
                    _logger.debug("play(): encountered interrupt while waiting for prompt to complete, stopping playback prematurely...");
                    _processor.close();
                } else {
                    _logger.debug("play(): encountered double interrupt, returning without waiting for ControllerClosedEvent...", ie);
                    throw e;
                }
            }
        }
        
        if (ie != null) {
            throw ie;
        }

    }

    /* (non-Javadoc)
     * @see javax.media.ControllerListener#controllerUpdate(javax.media.ControllerEvent)
     */
    public synchronized void controllerUpdate(ControllerEvent event) {
        if (_logger.isDebugEnabled()) {
            _logger.debug("ControllerEvent received: " + event);
        }

        if (event instanceof EndOfMediaEvent) {
            _processor.close();
        } else if (event instanceof ControllerClosedEvent) {
            closed = true;
        }

        this.notifyAll();
    }

}
