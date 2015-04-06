/**
 * Client.java
 * @author Devon Lehman, Bethany Waldmann
 * Created May 6th, 2010
 * 
 * Adapted from original source code from ConsoleClient.java 
 */

/**
 * Copyright 2009 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */


package jwaveidev2.util;

import java.io.IOException;

import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;

import jline.ANSIBuffer;
import jline.Completor;
import jline.ConsoleReader;
import jwaveidev2.handlers.JWaveHandler;

import org.eclipse.ui.texteditor.ITextEditor;
import org.waveprotocol.wave.examples.fedone.common.DocumentConstants;
import org.waveprotocol.wave.examples.fedone.common.HashedVersion;
import org.waveprotocol.wave.examples.fedone.util.BlockingSuccessFailCallback;
import org.waveprotocol.wave.examples.fedone.waveclient.common.ClientBackend;
import org.waveprotocol.wave.examples.fedone.waveclient.common.ClientUtils;
import org.waveprotocol.wave.examples.fedone.waveclient.common.ClientWaveView;
import org.waveprotocol.wave.examples.fedone.waveclient.common.IndexEntry;
import org.waveprotocol.wave.examples.fedone.waveclient.common.WaveletOperationListener;
import org.waveprotocol.wave.examples.fedone.waveclient.console.ScrollableInbox;
import org.waveprotocol.wave.examples.fedone.waveclient.console.ScrollableWaveView;
import org.waveprotocol.wave.examples.fedone.waveserver.WaveClientRpc.ProtocolSubmitResponse;
import org.waveprotocol.wave.model.document.operation.BufferedDocOp;
import org.waveprotocol.wave.model.document.operation.impl.DocOpBuilder;
import org.waveprotocol.wave.model.operation.wave.AddParticipant;
import org.waveprotocol.wave.model.operation.wave.RemoveParticipant;
import org.waveprotocol.wave.model.operation.wave.WaveletDocumentOperation;
import org.waveprotocol.wave.model.operation.wave.WaveletOperation;
import org.waveprotocol.wave.model.wave.ParticipantId;
import org.waveprotocol.wave.model.wave.data.WaveletData;

import java.io.PrintStream;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
	 * User interface for the console client using the JLine library.
	 */
	@SuppressWarnings("unused")
	public class Client implements WaveletOperationListener {

		/**
		 * Single active client-server interface, or null when not connected to a
		 * server.
		 */
		private ClientBackend backend = null;

		/**
		 * Currently open wave.
		 */
		private ScrollableWaveView openWave;

		/**
		 * Inbox we are rendering.
		 */
		private ScrollableInbox inbox;

		/**
		 * Number of lines to scroll by with { and }.
		 */
		private AtomicInteger scrollLines = new AtomicInteger(1);

		/**
		 * PrintStream to use for output.  We don't use ConsoleReader's functionality
		 * because it's too verbose and doesn't really give us anything in return.
		 */
		private final PrintStream out = System.out;
		
		/**
		 * The JWaveEditor this Client is associated with
		 */
		private final ITextEditor editor;
		
		private static JWaveHandler jwaveHandler;

		/**
		 * Create new console client.
		 */
		public Client(ITextEditor e, JWaveHandler h) throws IOException {
			editor=e;
			jwaveHandler=h;
		}

		/**
		 * Opens the wave that contains the Tile <code>title</code> 
		 * @param title The title of the wave to return
		 */
		public void setOpenWave(String title){
			boolean createNew=true;
			//this can be done a better, different way
			//by using the inbox, but i this should work too.
			List<IndexEntry> index = ClientUtils.getIndexEntries(backend.getIndexWave());
			for(int x = 0; x<index.size();x++){
				System.out.println("Looking through x waves: "+x+" of "+index.size());
				doOpenWave(x);
				sleep();
				System.out.println(getWaveTitle()+ " | "+title);
				if(getWaveTitle().equals(title)){
					//now its open! break out
					System.out.println("Found wave with title: "+title);
					createNew=false;
					break;
				}
			}
			if(createNew){
				System.out.println("Couldn't find:"+title+". Creating it.");
				createSetNewWave(title);
			}
		}

		/**
		 * Creates a new wave, opens it, sets its title to <code>title</code>
		 * @param title The title of the new wave
		 */
		public void createSetNewWave(String title){
			//create a new wave
			boolean createNew=false;
			do{
				createNew=!createNew;
				//open it- the one with no text in it.
				List<IndexEntry> index = ClientUtils.getIndexEntries(backend.getIndexWave());
				System.out.println(index.size()+"");
				for(int x = 0; x<index.size();x++){
					System.out.println("Looking through waves: "+(x+1)+" of "+index.size());
					doOpenWave(x);
					try{
						String temp=getWaveText();
						if(temp.equals("")){
							//if the text is empty... <a>
							System.out.println("Found an empty wave");
							break;
						}
					}catch(NullPointerException e){
						//<a> or the text is null, set as open, set the title
						System.out.println("Null pointer in createSetNewWave");
						break;
					}
				}
				if(createNew){
					System.out.println("Creating a new wave");
					newWave();
					sleep();
				}
			}while(createNew);
			//set the body text to title
			setWaveText("",title);
		}

		/**
		 * returns the entire contents, title included.
		 */
		public String getWaveText(){
			try{
				return getOpenWavelet().getDocuments().get(DocumentConstants.BODY).getCharactersString(0);
			}catch(NullPointerException e){
				return "";
			}
		}

		/**
		 * Returns the title of the wave (found in the first line of the \<body\> of the wave
		 * @return single line of text with extension
		 */
		public String getWaveTitle(){
			String temp = getWaveText();
			if(!temp.equals("")){
				temp = temp.substring(0,temp.indexOf("\n"));
				return temp;
			}else{
				return "";
			}
		}

		/** 
		 * returns the text without the title
		 */
		public String getWaveBodyText(){
			String temp = getWaveText();
			return temp.substring(temp.indexOf("\n")+1,temp.length());
		}

		private boolean updatedWave = false;
		/**
		 * Replace the existing text with <code>text</code>
		 * @param text Text to replace old text
		 */
		public void setWaveText(String text, String title){
			System.out.println("setWaveText");
			String originalText="";
			updatedWave = true;

			try{
				originalText=getWaveText();

				if(originalText.equals("")){
					insertText(title+"\n"+text,0,0);
				}
				else{
					String newText=title+"\n"+text;
					
				    DocOpBuilder builder = new DocOpBuilder();
				    if (!text.isEmpty()) {
				      builder.deleteCharacters(originalText);
				      builder.characters(newText);
				    }
				    
					BufferedDocOp insertOp =  builder.build();
					WaveletOperation insertWaveOp = new WaveletDocumentOperation(DocumentConstants.BODY, insertOp);
					backend.sendAndAwaitWaveletOperation(getOpenWavelet().getWaveletName(), insertWaveOp, 1, TimeUnit.MINUTES);
				}
			}catch(NullPointerException e){
				insertText(title+"\n"+text,0,0);
			}
		}

		/**
		 * Set the body of the wave to <code>text</code>
		 * @param text The text to set
		 * @param index where to put it
		 * @param the length before adding this text
		 */
		public void insertText(String text, int index, int previousTotalLength){
			BufferedDocOp insertOp = ClientUtils.createTextInsertion(text, index, previousTotalLength);
			WaveletOperation insertWaveOp = new WaveletDocumentOperation(DocumentConstants.BODY, insertOp);
			backend.sendAndAwaitWaveletOperation(getOpenWavelet().getWaveletName(), insertWaveOp, 1, TimeUnit.MINUTES);
		}

		/**
		 * Create and send a mutation that creates a new blip containing the given text, places it in a
		 * new blip, then adds a referece to the blip in the document manifest.
		 *
		 * @param text the text to include in the new blip
		 */
		private void sendAppendBlipDelta(String text) {
			if (isWaveOpen()) {
				backend.sendAndAwaitWaveletDelta(getOpenWavelet().getWaveletName(),
						ClientUtils.createAppendBlipDelta(getManifestDocument(), backend.getUserId(),
								backend.getIdGenerator().newDocumentId(), text),
								1,
								TimeUnit.MINUTES);
			} else {
				errorNoWaveOpen();
			}
		}

		/**
		 * Called when a wavelet document is updated. Updates the editor.
		 */
		@Override
		public void waveletDocumentUpdated(String author, WaveletData wavelet,
				WaveletDocumentOperation docOp) {
			
			if(!updatedWave){
				//update the editor from wave (class JWaveEditor)
				System.out.println("Wave updated from afar.");
				jwaveHandler.updateEditorFromWave();
			}
			else{
				System.out.println("The wave was just updated by the document.");
				updatedWave = false;
			}
		}

		/**
		 * Entry point for the user interface, receives user input, terminates on
		 * EOF.
		 *
		 * @param args command line arguments
		 */
		public void run(String[] args) throws IOException {
			// Initialise screen and move cursor to bottom left corner
			//			reader.clearScreen();
			//			reader.setDefaultPrompt("(not connected) ");
			//			out.println(ANSIBuffer.ANSICodes.gotoxy(reader.getTermheight(), 1));
			System.out.println(args[0]);
			System.out.println(args[1]);
			System.out.println(args[2]);

			// Immediately establish connection if desired, otherwise the user will need to use "/connect"
			if (args.length == 3) {
				connect(args[0], args[1], args[2]);
			} else if (args.length != 0) {
				System.out.println("Usage: java ConsoleClient [user@domain server port]");
				System.exit(1);
			}
		}

		/**
		 * Register a user and server with a new {@link ClientBackend}.
		 */
		private void connect(String userAtDomain, String server, String portString) {
			// We can only connect to one server at a time (at least, in this simple UI)
			if (isConnected()) {
				out.println("Warning: already connected");
				backend.shutdown();
				backend = null;
				openWave = null;
				inbox = null;
			}

			int port;
			try {
				port = Integer.parseInt(portString);
			} catch (NumberFormatException e) {
				out.println("Error: must provide valid port");
				return;
			}

			try {
				backend = new ClientBackend(userAtDomain, server, port);
			} catch (IOException e) {
				out.println("Error: failed to connect, " + e.getMessage());
				return;
			}
			backend.addWaveletOperationListener(this);
			inbox = new ScrollableInbox(backend, backend.getIndexWave());
		}


		public boolean inboxEmpty(){
			List<IndexEntry> index = ClientUtils.getIndexEntries(backend.getIndexWave());
			return index.isEmpty();

		}

		/**
		 * Add a new wave.
		 */
		public void newWave() {
			if (isConnected()) {
				BlockingSuccessFailCallback<ProtocolSubmitResponse, String> callback =
					BlockingSuccessFailCallback.create();
				backend.createConversationWave(callback);
				callback.await(1, TimeUnit.MINUTES);
			} else {
				errorNotConnected();
			}
		}

		/**
		 * Open a wave with a given entry (index in the inbox).
		 *
		 * @param entry into the inbox
		 */
		public void doOpenWave(int entry) {
			if (isConnected()) {
				List<IndexEntry> index = ClientUtils.getIndexEntries(backend.getIndexWave());
		
				if (entry >= index.size()) {
					out.print("Error: entry is out of range, ");
					if (index.isEmpty()) {
						out.println("there are no available waves (try \"/new\")");
					} else {
						out.println("expecting [0.." + (index.size() - 1) + "] (for example, \"/open 0\")");
					}
				} else {
					setOpenWave(backend.getWave(index.get(entry).getWaveId()));
				}
			} else {
				errorNotConnected();
			}
		}

		/**
		 * Set a wave as the open wave.
		 *
		 * @param wave to set as open
		 */
		private void setOpenWave(ClientWaveView wave) {
			if (ClientUtils.getConversationRoot(wave) == null) {
				wave.createWavelet(ClientUtils.getConversationRootId(wave));
			}
			openWave = new ScrollableWaveView(wave);
//			render();
		}

		/**
		 * @return the open wavelet of the open wave, or null if no wave is open
		 */
		private WaveletData getOpenWavelet() {
			return (openWave == null) ? null : ClientUtils
					.getConversationRoot(openWave.getWave());
		}

		/**
		 * @return open document, or null if no wave is open or main document doesn't exist
		 */
		private BufferedDocOp getManifestDocument() {
			return getOpenWavelet() == null ? null : getOpenWavelet().getDocuments().get(
					DocumentConstants.MANIFEST_DOCUMENT_ID);
		}

		/**
		 * Add a participant to the currently open wave(let).
		 *
		 * @param name name of the participant to add
		 */
		public void addParticipant(String name) {
			if (isWaveOpen()) {
				ParticipantId addId = new ParticipantId(name);

				// Don't send an invalid op, although the server should be robust enough to deal with it
				if (!getOpenWavelet().getParticipants().contains(addId)) {
					backend.sendAndAwaitWaveletOperation(getOpenWavelet().getWaveletName(),
							new AddParticipant(addId), 1, TimeUnit.MINUTES);
				} else {
					out.println("Error: " + name + " is already a participant on this wave");
				}
			} else {
				errorNoWaveOpen();
			}
		}

		/**
		 * Remove a participant from the currently open wave(let).
		 *
		 * @param name name of the participant to remove
		 */
		private void removeParticipant(String name) {
			if (isWaveOpen()) {
				ParticipantId removeId = new ParticipantId(name);

				if (getOpenWavelet().getParticipants().contains(removeId)) {
					backend.sendAndAwaitWaveletOperation(getOpenWavelet().getWaveletName(),
							new RemoveParticipant(removeId), 1, TimeUnit.MINUTES);
				} else {
					out.println("Error: " + name + " is not a participant on this wave");
				}
			} else {
				errorNoWaveOpen();
			}
		}

		/**
		 * Set all waves as read.
		 */
		private void readAllWaves() {
			if (isConnected()) {
				inbox.updateHashedVersions();
			} else {
				errorNotConnected();
			}
		}


		/**
		 * Set the number of lines to scroll by.
		 *
		 * @param lines to scroll by
		 */
		public void setScrollLines(String lines) {
			try {
				scrollLines.set(Integer.parseInt(lines));
			} catch (NumberFormatException e) {
				out.println("Error: lines must be a valid integer");
			}
		}

		/**
		 * Print error message if user is not connected to a server.
		 */
		private void errorNotConnected() {
			out.println("Error: not connected, run \"/connect user@domain server port\"");
		}

		/**
		 * Print error message if user does not have a wave open.
		 */
		private void errorNoWaveOpen() {
			out.println("Error: no wave is open, run \"/open index\"");
		}

		/**
		 * @return whether the client is connected to any server
		 */
		public boolean isConnected() {
			return backend != null;
		}

		/**
		 * @return whether the client has a wave open
		 */
		private boolean isWaveOpen() {
			return isConnected() && openWave != null;
		}

		@Override
		public void participantAdded(String author, WaveletData wavelet, ParticipantId participantId) {
		}

		@Override
		public void participantRemoved(String author, WaveletData wavelet, ParticipantId participantId) {
			if (isWaveOpen() && participantId.equals(backend.getUserId())) {
				// We might have been removed from our open wave (an impressively verbose check...)
				if (wavelet.getWaveletName().waveId.equals(openWave.getWave().getWaveId())) {
					openWave = null;
				}
			}
		}

		@Override
		public void noOp(String author, WaveletData wavelet) {
		}

		@Override
		public void onDeltaSequenceStart(WaveletData wavelet) {
		}

		@Override
		public void onDeltaSequenceEnd(WaveletData wavelet) {
		}

		@Override
		public void onCommitNotice(WaveletData wavelet, HashedVersion version) {
		}


		/**
		 * Sleep for <code>x</code> millis
		 * @param x time to sleep in millis
		 */
		public void sleep(int x){
			try {
				Thread.sleep(x);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
		}

		/**
		 * Sleep for 1 second. Useful when waiting for changes to take effect
		 */
		public void sleep(){
			sleep(1000);
		}

	}
