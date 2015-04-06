/**
 * @author Devon Lehman, Bethany Waldmann
 * 
 * 
 * 
 * Licensed under GNU GNU General Public License version 3 (GPLv3)
 * 
 * - freedom to use the software for any purpose,
 * - freedom to change the software to suit your needs,
 * - freedom to share the software with your friends and neighbors, and
 * - freedom to share the changes you make.
 *
 * Icons from twotiny icon set:
 * 			http://code.google.com/p/twotiny/
 * 
 * String comparison using diff_match_patch:
 * 			http://code.google.com/p/google-diff-match-patch/
 */



package jwaveidev2.handlers;


import java.io.IOException;
import java.util.LinkedList;

import org.eclipse.core.commands.AbstractHandler;
import org.eclipse.core.commands.ExecutionEvent;
import org.eclipse.core.commands.ExecutionException;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.text.DocumentEvent;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.IDocumentListener;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IEditorInput;
import org.eclipse.ui.IEditorPart;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPage;
import org.eclipse.ui.IWorkbenchWindow;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.handlers.HandlerUtil;
import org.eclipse.ui.texteditor.IDocumentProvider;
import org.eclipse.ui.texteditor.ITextEditor;

import jwaveidev2.diff.diff_match_patch;
import jwaveidev2.diff.diff_match_patch.Diff;
import jwaveidev2.diff.diff_match_patch.Patch;
import jwaveidev2.util.Client;

/**
 * For handling all the JWave actions within Eclipse
 */
public class JWaveHandler extends AbstractHandler{

	private static Client con;

	private String host="jwaveide.com";
	//  private String login="jwave";
	private String port="9876";
	private String[] loginInfo=new String[3];

	/**
	 * Listener for changes within the editor that reflects changes
	 * on the wave
	 */
	private JWaveDocumentListener documentListener;

	/**
	 * The editor currently active
	 */
	private static ITextEditor editor;

	/**
	 * Creates a new JWaveHandler object
	 */
	public JWaveHandler(){
		System.out.println("New handler");
	}


	/**
	 * Called whenever a menu item is selected or toolbar button
	 * is pressed
	 */
	public Object execute(ExecutionEvent event) throws ExecutionException {
		String cmd = event.getCommand().getId();
		System.out.println("execute: "+cmd);

		setActiveEditor();

		if(con!=null){
			String openEditorTitle = editor.getTitle();
			String openWaveTitle = getWaveTitle();
			if(!openEditorTitle.equals(openWaveTitle)){
				con.setOpenWave(editor.getTitle());
			}
		}

		if(cmd.equals("JWaveIDEv2.commands.connectCommand")){
			setActiveEditor();

			IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

			InputDialog dialog = new InputDialog(window.getShell(),
					"Connect",
					"Host name:",
					"jwaveide.com",
					new IInputValidator(){
				@Override
				public String isValid(String arg0) {
					if(arg0!="")
						return null;
					return arg0;
				}}
			);
			dialog.open(); 
			host=dialog.getValue();
			InputDialog dialog2 = new InputDialog(window.getShell(),
					"Connect",
					"Log in as:",
					"jwave",
					new IInputValidator(){
				@Override
				public String isValid(String arg0) {
					if(arg0!="")
						return null;
					return arg0;
				}}
			);
			dialog2.open();              
			serverConnect(dialog2.getValue());


		}
		else if (cmd.equals("JWaveIDEv2.commands.updateRateCommand")){IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

		InputDialog dialog = new InputDialog(window.getShell(),
				"Set Update Rate",
				"Add the username of the participant:",
				"20",
				new IInputValidator(){
			@Override
			public String isValid(String arg0) {
				if(arg0!="")
					return null;
				return arg0;
			}}
		);
		dialog.open();
		setUpdateRate(Integer.parseInt(dialog.getValue()));
		}
		else if (cmd.equals("JWaveIDEv2.commands.addParticipantCommand")){
			System.out.println("addpart");
			IWorkbenchWindow window = HandlerUtil.getActiveWorkbenchWindowChecked(event);

			InputDialog dialog = new InputDialog(window.getShell(),
					"Add Participant",
					"Add the username of the participant:",
					"jwave2",
					new IInputValidator(){
				@Override
				public String isValid(String arg0) {
					if(arg0!="")
						return null;
					return arg0;
				}}
			);
			dialog.open();
			addParticipant(dialog.getValue());
		}
		else if (cmd.equals("JWaveIDEv2.commands.updateCommand")){
			System.out.println("update");
			setActiveEditor();
			updateEditorFromWave();
		}
		else if (cmd.equals("JWaveIDEv2.commands.saveCommand")){
			System.out.println("save");
			updateWaveFromEditor();
			editor.doSave(new IProgressMonitor(){
				@Override
				public void beginTask(String arg0, int arg1) {}
				@Override
				public void done() {}
				@Override
				public void internalWorked(double arg0) {}
				@Override
				public boolean isCanceled() {return false;}
				@Override
				public void setCanceled(boolean arg0) {}
				@Override
				public void setTaskName(String arg0) {}
				@Override
				public void subTask(String arg0) {}
				@Override
				public void worked(int arg0) {}});
		}
		else if (cmd.equals("JWaveIDEv2.commands.openCommand")){
			System.out.println("open");
			openWave(editor.getTitle());
		}

		return null;

	}

	/**
	 * Sets the editor to be the active editor
	 */
	public void setActiveEditor(){
		IWorkbench wb = PlatformUI.getWorkbench();
		IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
		IWorkbenchWindow[] windows = wb.getWorkbenchWindows();
		for (IWorkbenchWindow ww : windows) {
			try {
				win = ww;
			} catch (Exception e) {
				System.out.println("setActiveEditor: exception getting window");
			}
		}
		IWorkbenchPage page = win.getActivePage();
		IEditorPart part = page.getActiveEditor();
		editor =(ITextEditor)part;
	}

	/**
	 * Adds participant to the wave, using the same host name.
	 * @param participant name of participant to add.
	 */
	public void addParticipant(String participant){
		System.out.println("Adding Participant: "+participant);
		con.addParticipant(participant+"@"+host);
	}

	/**
	 * Sets the text on the editor to <code>text</code>
	 * @param text The text to set
	 */
	public void setFileText(String text){
		final String body = text;
		//      setActiveEditor();
		System.out.println("setFileText: editor "+editor.getTitle());
		System.out.println("setFileText: getting document provider");
		IDocumentProvider dp = editor.getDocumentProvider();
		System.out.println("setFileText: getting editor input");
		IEditorInput ei = editor.getEditorInput();
		System.out.println("setFileText: getting document");
		final IDocument doc = dp.getDocument(ei);
		System.out.println("setFileText: wave: "+text);
		try {
			IWorkbench wb = PlatformUI.getWorkbench();
			IWorkbenchWindow win = wb.getActiveWorkbenchWindow();
			IWorkbenchWindow[] windows = wb.getWorkbenchWindows();
			for (IWorkbenchWindow ww : windows) {
				try {
					win = ww;
				} catch (Exception e) {
					System.out.println("setFileText: exception getting window");
				}
			}
			//System.out.println("setFileText: doc: "+docText);
			System.out.println("setFileText: setting text");
			try {
				//              LinkedList<Diff> diffs = new LinkedList<Diff>();
				//              diffs = dmp.diff_main(docText,text);

				Shell sh = win.getShell();
				Display disp = sh.getDisplay();
				disp.syncExec(
						new Runnable() {
							public void run(){
								diff_match_patch dmp;
								diff_match_patch.Operation DELETE = diff_match_patch.Operation.DELETE;
								diff_match_patch.Operation EQUAL = diff_match_patch.Operation.EQUAL;
								diff_match_patch.Operation INSERT = diff_match_patch.Operation.INSERT;
								dmp = new diff_match_patch();
								String docText = doc.get();
								LinkedList<Patch> patches = dmp.patch_make(docText,body);
								//doc.set(body);
								try {
									for (Patch p : patches) {
										System.out.println("start1: "+p.start1);
										System.out.println("start2: "+p.start2);
										System.out.println("length1: "+p.length1);
										System.out.println("length2: "+p.length2);
										System.out.println("diffs:");
										int current = p.start1;
										for (Diff d : p.diffs) {
											System.out.println("  operation: "+d.operation);
											System.out.println("  text: "+d.text);
											if (d.operation.equals(EQUAL)) {
												current += d.text.length();
											}
											else {if (d.operation.equals(INSERT)) {
												doc.replace(current,0,d.text);
												current += d.text.length();
											} else if (d.operation.equals(DELETE)) {
												doc.replace(current,d.text.length(),"");
												current -= d.text.length();
											}}
										}
									}
									//                          doc.replace(15,5,"Goodbye");
									//                          System.out.println("setActiveEditor: 15-20: "+doc.get(15, 5)) ;
								} catch (Exception e) {
									System.out.println("setActiveEditor: doc.get exception");
									e.printStackTrace();
								}
								//                      doc.replace(replace(int 5, int length, String text) )
							}
						});
			} catch (Exception e) {
				//              System.out.println("setFileText: no changes");
				e.printStackTrace();
			}
		} catch (Exception e) {
			System.out.println("setFileText: exception");
			e.printStackTrace();
		}
		System.out.println("setFileText: text set");
	}

	/**
	 * Returns all the text within the file on the editor
	 */
	public String getFileText(){
		return editor.getDocumentProvider().getDocument(editor.getEditorInput()).get();
	}

	private boolean waveUpdateEditor=false;
	/**
	 * Takes text in wave, puts it in the editor
	 */
	public void updateEditorFromWave(){
		//      String title = getWaveTitle();
		waveUpdateEditor=true;
		try{
			System.out.println("updateEditorFromWave: setting active editor");
			setActiveEditor();
			System.out.println("told it to update the text in the editor.");
			setFileText(getWaveBodyText());
			System.out.println("updated the text in the editor.");
		}catch(NullPointerException e){
			System.out.println("Null pointer caught.");
		}
	}

	/**
	 * Takes text in editor, puts it in the wave
	 */
	public void updateWaveFromEditor(){
		if(con!=null)
			if(con.isConnected())
				setWaveText(getFileText());
	}

	/**
	 * Replaces existing text in the wave with <code>text</code>
	 * @param text Text to set
	 */
	public void setWaveText(String text){
		System.out.println("Saving text for "+editor.getTitle());
		con.setWaveText(text,editor.getTitle());
	}

	/**
	 * Returns the title of the wave
	 */
	public String getWaveTitle(){
		return con.getWaveTitle();
	}

	/**
	 * Returns the body of the wave
	 */
	public String getWaveBodyText(){
		if (con==null) System.out.println("CON IS NULL!!!!!");
		try {
			String body = con.getWaveBodyText();
			System.out.println("getWaveBodyText: got wave body");
			return body;
		} catch (Exception e) {
			System.out.println("getWaveBodyText: exception: con.getWaveBodyText");
			return "";
		}
	}

	/**
	 * Returns all the wave text, title and body
	 */
	public String getWaveText(){
		try{
			String s = con.getWaveText();
			return s;
		}catch(NullPointerException e){
			System.out.println("Null Pointer");
		}
		return "";
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
	 * Sleep for 1 second. Useful when waiting for changes to take effect.
	 * This method should be replaced with one that waits until the wave
	 * sends a reply to a request.
	 */
	public void sleep(){
		sleep(1000);
	}

	/**
	 * Opens the wave with title <code>title</code>, creates it if
	 * it doesn't exist.
	 * @param title The title of the wave to open
	 */
	public void openWave(String title){
		System.out.println("Open wave: "+title);
		if(con.inboxEmpty()){
			System.out.println("No waves in inbox, creating new wave");
			con.newWave();
			sleep();
		}
		con.setOpenWave(title);
	}

	/**
	 * Connect the Client using the default login name "jwave".
	 */
	public void serverConnect(){
		serverConnect("jwave");
	}

	/**
	 * Connect the Client using the login name <code>user<code>.
	 */
	public void serverConnect(String user){
		try {
			if(con==null){
				System.out.println("serverConnect");
				con = new Client(editor,this);
				System.out.println("new Client made");
				loginInfo[0]=user+"@"+host;
				loginInfo[1]=host;
				loginInfo[2]=port;
			}
			System.out.println("Loggin in as "+loginInfo[0]);
			con.run(loginInfo);
			sleep();
			con.setOpenWave(editor.getTitle());
		} catch (IOException e) {
			System.out.println("serverConnect ioexception");
			e.printStackTrace();
		}
		addDocumentListener();
	}

	/**
	 * Adds a document listener, called whenever anything is typed.
	 */
	public void addDocumentListener(){
		documentListener=new JWaveDocumentListener();
		editor.getDocumentProvider().getDocument(editor.getEditorInput()).addDocumentListener(documentListener);
	}

	/**
	 * Adds a document listener, called whenever anything is typed.
	 * @param rate How many changes must be made for the editor to update the wave automatically
	 */
	public void setUpdateRate(int rate){
		documentListener.setUpdateRate(rate);
	}

	private class JWaveDocumentListener implements IDocumentListener{

		private int charactersTyped=5;
		private int updateRate=5;

		@Override
		public void documentAboutToBeChanged(DocumentEvent arg0) {
		}

		/**
		 * Sets the update rate
		 * @param rate how many changes must happen before an automatic update
		 */
		public void setUpdateRate(int rate){
			this.updateRate=rate;
		}

		/**
		 * Called whenever a document is changed.  Auto-updates every
		 * <code>update rate</code> times it changes
		 */
		@Override
		public void documentChanged(DocumentEvent arg0) {
			if(!waveUpdateEditor){
				if(charactersTyped==0){
					updateWaveFromEditor();
					charactersTyped=this.updateRate;
				}
				charactersTyped--;
			}else{
				waveUpdateEditor=false;
			}

		}
	}


} 