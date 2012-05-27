package es.eucm.blindfaithgames.tsm.input;

import java.util.ArrayList;
import java.util.List;

import org.xml.sax.Attributes;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import es.eucm.blindfaithgames.tsm.game.NPC;
import es.eucm.blindfaithgames.tsm.game.Scene;
import es.eucm.blindfaithgames.tsm.game.SceneManager;
import es.eucm.blindfaithgames.tsm.game.SceneType;
/**
 * 
 * Class to manage events sent by a parser SAX.
 * 
 * @author Gloria Pozuelo and Javier Álvarez.
 */
public class SAXHandler extends DefaultHandler {
	// Scenes
	private List<Scene> scenes;
	private List<NPC> npcs;
	private SceneManager sceneManager;
	private SceneType type;
	private int id;
	private boolean isDescription = false, isIntroMsg = false;
	private String description, introMsg;
	private List<Integer> endCondition, transitionCondition, nextScenes; 
	
	// NPCs
	private boolean isAnswer = false;
	private String name, author;
	private List<String> dialog;
	private String transition;
	
	public SceneManager getSceneManager() {
		return sceneManager;
	}
	
// --------------------------------------------------------------------- //
/* DEFINE METHODS OF DefaultHandler */
// --------------------------------------------------------------------- //
	public void error(SAXParseException e) throws SAXParseException {
		throw e;
	}
	
	public void startDocument(){	
		endCondition = new ArrayList<Integer>();
		transitionCondition= new ArrayList<Integer>();
		nextScenes = new ArrayList<Integer>();
		scenes = new ArrayList<Scene>();
		npcs = new ArrayList<NPC>();
		dialog = new ArrayList<String>();
	}
	
	public void startElement(String uri, String localName, String qName, Attributes att){
		if (qName.equals("scene")){
			type = SceneType.valueOf(att.getValue("type").toUpperCase());
			id = Integer.parseInt(att.getValue("id"));
		}
		else if (qName.equals("introMessage")){
			isIntroMsg = true;
		}
		else if (qName.equals("description")){
			isDescription = true;
		}
		else if (qName.equals("idTransitionCondition")){
			transitionCondition.add(Integer.parseInt(att.getValue("id")));
		}
		else if (qName.equals("idEndCondition")){
			endCondition.add(Integer.parseInt(att.getValue("id")));
		}
		else if (qName.equals("idNextScenes")){
			nextScenes.add(Integer.parseInt(att.getValue("id")));
		}
		
		// NPC
		else if (qName.equals("npc")){
			name = att.getValue("name");
			transition = att.getValue("transition");
		}
		else if (qName.equals("answer")){
			author = att.getValue("author");
			isAnswer = true;
		}
	}

	public void endElement(String uri, String localName, String qName){
		if (qName.equals("scene")){ 
			scenes.add(new Scene(npcs, id, type, introMsg, description, nextScenes, transitionCondition, endCondition));
			npcs = new ArrayList<NPC>();
			transitionCondition = new ArrayList<Integer>();
			nextScenes = new ArrayList<Integer>();
			endCondition = new ArrayList<Integer>();
		}		
		else if (qName.equals("npc")){
			if(transition != null){
				if(transition.equalsIgnoreCase("true"))
					npcs.add(new NPC(dialog, name, true));
				else
					npcs.add(new NPC(dialog, name, false));
			} else {
				npcs.add(new NPC(dialog, name, false));
			}
			dialog = new ArrayList<String>();
		}
		else if (qName.equals("sceneManager")){
			sceneManager = new SceneManager(scenes);
		}
	}
	
	public void characters(char ch[], int start, int length) { 
		if (isDescription) {
			description = new String(ch, start, length);
			isDescription = false;
		}
		if (isIntroMsg) {
			introMsg = new String(ch, start, length);
			isIntroMsg = false;
		}
		if (isAnswer) {
			if(author != null)
				dialog.add(author + ": " +new String(ch, start, length));
			else
				dialog.add(new String(ch, start, length));
			author = "";
			isAnswer = false;
		}
	}
}

