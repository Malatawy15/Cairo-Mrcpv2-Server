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
package org.speechforge.cairo.demo.standalone;

import static org.speechforge.cairo.rtp.server.sphinx.SourceAudioFormat.PREFERRED_MEDIA_FORMATS;
import static org.speechforge.cairo.server.resource.ResourceImpl.HELP_OPTION;
import static org.speechforge.cairo.jmf.JMFUtil.MICROPHONE;

import org.speechforge.cairo.server.recog.GrammarLocation;
import org.speechforge.cairo.server.recog.RecogListenerDecorator;
import org.speechforge.cairo.server.recog.RecognitionResult;
import org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine;
import org.speechforge.cairo.rtp.server.PBDSReplicator;
import org.speechforge.cairo.jmf.JMFUtil;
import org.speechforge.cairo.jmf.ProcessorStarter;

import java.awt.Toolkit;
import java.io.IOException;
import java.net.URL;

import javax.media.CannotRealizeException;
import javax.media.MediaLocator;
import javax.media.NoDataSourceException;
import javax.media.NoProcessorException;
import javax.media.Processor;
import javax.media.protocol.PushBufferDataSource;

import edu.cmu.sphinx.util.props.ConfigurationManager;

import org.apache.commons.cli.CommandLine;
import org.apache.commons.cli.CommandLineParser;
import org.apache.commons.cli.GnuParser;
import org.apache.commons.cli.HelpFormatter;
import org.apache.commons.cli.Option;
import org.apache.commons.cli.Options;
import org.apache.log4j.Logger;

/**
 * Demo application that performs speech recognition on microphone input using the
 * {@link org.speechforge.cairo.server.recog.sphinx.SphinxRecEngine} directly
 * (rather than via MRCPv2 using a recognition resource).
 *
 * @author Niels Godfredsen {@literal <}<a href="mailto:ngodfredsen@users.sourceforge.net">ngodfredsen@users.sourceforge.net</a>{@literal >}
 */
public class StandaloneRecogClient extends RecogListenerDecorator {

    private static Logger _logger = Logger.getLogger(StandaloneRecogClient.class);

    private static final String BEEP_OPTION = "beep";

    private static boolean _beep = false;
    private static Toolkit _toolkit = null;


    private SphinxRecEngine _engine;
    private RecognitionResult _result;
    private PBDSReplicator _replicator;


    /**
     * TODOC
     * @param engine 
     */
    public StandaloneRecogClient(SphinxRecEngine engine) {
        super(null);
        _engine = engine;
    }

    /* (non-Javadoc)
     * @see org.speechforge.cairo.server.recog.RecogListener#recognitionComplete(org.speechforge.cairo.server.recog.RecognitionResult)
     */
    @Override
    public synchronized void recognitionComplete(RecognitionResult result) {
        _result = result;
        if (_beep) {
            _toolkit.beep();
        }
        this.notify();
    }

    /**
     * @param mediaLocator
     * @return
     * @throws IOException
     * @throws NoProcessorException
     * @throws CannotRealizeException
     * @throws InterruptedException
     * @throws NoDataSourceException
     */
    public synchronized RecognitionResult doRecognize(MediaLocator mediaLocator, String examplePhrase)
      throws IOException, NoProcessorException, CannotRealizeException, InterruptedException, NoDataSourceException {

        Processor processor1 = JMFUtil.createRealizedProcessor(mediaLocator, PREFERRED_MEDIA_FORMATS);
        processor1.addControllerListener(new ProcessorStarter());
        PushBufferDataSource pbds1 = (PushBufferDataSource) processor1.getDataOutput();
        _replicator = new PBDSReplicator(pbds1);

        _result = null;
        _engine.activate();

        Processor processor2 = JMFUtil.createRealizedProcessor(_replicator.replicate(),PREFERRED_MEDIA_FORMATS);
        processor2.addControllerListener(new ProcessorStarter());

        PushBufferDataSource pbds2 = (PushBufferDataSource) processor2.getDataOutput();
        _engine.startRecognition(pbds2, this);
        processor2.start();
        Thread.sleep(1000);  // give processor2 a chance to start
        // TODO: find better solution for timing processor starting
        processor1.start();

        _logger.debug("Starting recog thread...");
        _engine.startRecogThread();

        if (_logger.isInfoEnabled()) {
            if (examplePhrase == null) {
                _logger.info("\nStart speaking now...");
            } else {
                _logger.info("\nStart speaking now... (e.g. \"" + examplePhrase + "\")");
            }
        }

        if (_beep) {
            _toolkit.beep();
        }

        // wait for result
        while (_result == null) {
            this.wait();
        }
 
        _engine.passivate();

        return _result;
    }


////////////////////////////////////
//static methods
////////////////////////////////////


    public static Options getOptions() {
        Options options = new Options();

        Option option = new Option(HELP_OPTION, "print this message");
        options.addOption(option);

        option = new Option(BEEP_OPTION, "play response/event timing beep");
        options.addOption(option);

        return options;
    }


////////////////////////////////////
//  main method
////////////////////////////////////

    public static void main(String[] args) throws Exception {

        CommandLineParser parser = new GnuParser();
        Options options = getOptions();
        CommandLine line = parser.parse(options, args, true);
        args = line.getArgs();

        if (args.length < 1 || args.length > 2 || line.hasOption(HELP_OPTION)) {
            HelpFormatter formatter = new HelpFormatter();
            formatter.printHelp("StandaloneRecogClient [options] <grammar-URL> <example-phrase>", options);
            return;
        }

        _beep = line.hasOption(BEEP_OPTION);
        if (_beep) {
            _toolkit = Toolkit.getDefaultToolkit();
        }

        GrammarLocation grammarLocation = new GrammarLocation(new URL(args[0]));
        String examplePhrase = (args.length > 1) ? args[1] : null;

        URL sphinxConfigUrl = SphinxRecEngine.class.getResource("/config/sphinx-config.xml");
        if (sphinxConfigUrl == null) {
            throw new RuntimeException("Sphinx config file not found!");
        }

        try {

            _logger.info("Loading Sphinx recognition engine...");
            ConfigurationManager cm = new ConfigurationManager(sphinxConfigUrl);
            SphinxRecEngine engine = new SphinxRecEngine(cm,1);

            _logger.info("Loading grammar file...");
            engine.loadJSGF(grammarLocation);

            StandaloneRecogClient client = new StandaloneRecogClient(engine);
            RecognitionResult result = client.doRecognize(MICROPHONE, examplePhrase);

            if (_logger.isInfoEnabled()) {
                StringBuilder sb = new StringBuilder();
                sb.append("\n**************************************************************");
                sb.append("\nRecognition result: ").append(result);
                sb.append("\n**************************************************************\n");
                _logger.info(sb);
            }

        } catch (Exception e){
            _logger.warn(e, e);
            System.exit(1);
        }

        System.exit(0);
    }

}
