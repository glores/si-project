package com.minesweeper;



import android.app.Activity;
import android.os.Bundle;

import com.accgames.others.AnalyticsManager;
import com.accgames.others.Log;
import com.minesweeper.R;
import com.minesweeper.game.MinesweeperAnalytics;
import com.minesweeper.game.TTS;

/**
 * @author Gloria Pozuelo, Gonzalo Benito and Javier �lvarez
 * This class implements the about activity, where is shown a description of minesweeper
 */

public class AboutActivity extends Activity{

	private static String TAG = "About";
	
	private TTS textToSpeech;
	
	/** Called when the activity is first created. */
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.about);	
		
		// Initialize TTS engine
		textToSpeech = (TTS) getIntent().getParcelableExtra(MinesweeperActivity.KEY_TTS);
		textToSpeech.setContext(this);
		textToSpeech.setInitialSpeech(getString(R.string.about_title) + " " + getString(R.string.about_text));
	
		Log.getLog().addEntry(AboutActivity.TAG,
				PrefsActivity.configurationToString(this),
				Log.NONE,Thread.currentThread().getStackTrace()[2].getMethodName(),"");
		
		AnalyticsManager.getAnalyticsManager(this).registerPage(MinesweeperAnalytics.ABOUT_ACTIVITY);
	}
	
	/**
	 *  Turns off TTS engine
	 */
	@Override
	protected void onDestroy() {
		 super.onDestroy();
	     textToSpeech.stop();
	}
}