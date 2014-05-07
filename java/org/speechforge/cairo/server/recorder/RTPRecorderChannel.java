package org.speechforge.cairo.server.recorder;

import java.io.File;
import java.io.IOException;

import org.speechforge.cairo.exception.ResourceUnavailableException;

public interface RTPRecorderChannel {

	public File startRecording(RecorderListener listener, long noInputTimeout, String uri)
	        throws IOException, IllegalStateException, ResourceUnavailableException;

	public File stopRecording() throws IllegalStateException;

	/**
	 * Starts the input timers which trigger no-input-timeout if speech has not started after the specified time.
	 * @param noInputTimeout the amount of time to wait, in milliseconds, before triggering a no-input-timeout. 
	 * @return {@code true} if input timers were started or {@code false} if speech has already started.
	 * @throws IllegalStateException if recognition is not in progress or if the input timers have already been started.
	 */
	public boolean startInputTimers(long noInputTimeout) throws IllegalStateException;

	
    public void closeProcessor();
	
}