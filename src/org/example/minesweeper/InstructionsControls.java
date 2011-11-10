package org.example.minesweeper;

import android.app.Activity;
import android.os.Bundle;
import android.speech.tts.TextToSpeech;

public class InstructionsControls extends Activity{
	
	private TextToSpeech mTts;
	
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.instructions_controls);
		// This initialize TTS engine
		OnInitTTS initialize = new OnInitTTS(mTts,getString(R.string.instructions_controls_title) + " " + getString(R.string.instructions_controls_text));
		// Checking if TTS is installed on device
		if(OnInitTTS.isInstalled(this)&& Prefs.getTTS(this)){
			mTts = new TextToSpeech(this,initialize);
			initialize.setmTts(mTts);
		}
	}
	
	/**
	 *  Turns off TTS engine
	 */
	@Override
	protected void onDestroy() {
		 super.onDestroy();
        if (mTts != null) {
            mTts.stop();
            mTts.shutdown();
        }
	}
}