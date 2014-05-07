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

/**
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
@Deprecated public class SpeechState {

    public static final short IDLE = 0;
    public static final short SPEAKING = 1;
    public static final short PAUSED = 2;

    private short _state = IDLE;

    /**
     * TODOC
     */
    public SpeechState() {
        super();
    }

    /**
     * TODOC
     * @param state The state to set.
     */
    public synchronized void setState(short state) {
        _state = state;
    }

    /**
     * TODOC
     * @return Returns the state.
     */
    public synchronized short getState() {
        return _state;
    }

    /**
     * TODOC
     * @return
     */
    public synchronized boolean isIdle() {
        return _state == IDLE;
    }

}
