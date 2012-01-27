package org.example.golf;

import java.util.List;

import org.example.R;
import org.example.activities.SettingsActivity;
import org.example.others.RuntimeConfig;
import org.example.tinyEngineClasses.CustomBitmap;
import org.example.tinyEngineClasses.Entity;
import org.example.tinyEngineClasses.Game;
import org.example.tinyEngineClasses.Input;
import org.example.tinyEngineClasses.Input.EventType;
import org.example.tinyEngineClasses.Mask;
import org.example.tinyEngineClasses.Music;
import org.example.tinyEngineClasses.SoundConfig;
import org.example.tinyEngineClasses.SoundConfig.Distance;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Point;
import android.os.Vibrator;
import android.view.MotionEvent;

/**
 * Represents the ball
 * 
 * */

public class Dot extends Entity{
	
	private static final int previous_shot_feedback_sound = R.raw.previous_shoot_feedback_sound;
	private static final int alternative_previous_shot_feedback_sound  = R.raw.water_bubbles;
	private static final int success_feedback_sound = R.raw.win_sound;
	private static final int hit_feedback_sound = R.raw.hit_ball;
	private static final int doppler_sound = R.raw.sound_shot;
	private static final int alternative_doppler_sound = R.raw.storm;
	private static final int originX = GolfGame.SCREEN_WIDTH/2;
	private static final int originY = GolfGame.SCREEN_HEIGHT - GolfGame.SCREEN_HEIGHT/3;
	private static final int MAX_SHOTS_FAILED = 4;
	private boolean launched;
	
	private float incr;
	private float param;
	private Point v = null;
	private float initialX = 0;
	private float initialY = 0;
	private Point targetPos; // target Position
	
	private ScoreBoard scoreBoard;
	
	private Vibrator mVibrator;
	private float leftVol,rightVol;
	
	private float dotCenterX;
	private float dotCenterY;
	
	private int outOfShotAreaCounter;
	
	private GolfGame game; // To know if stageMode is active
	
	
	// Debug
	private float scrollX;
	private float scrollY;
	
	private SoundConfig soundConfig;
	private boolean headPhonesMode;
	
	private EventType shotEvent; // Event that generate a shot

	/**
	 * It creates the entity scoreboard to refresh its content and uses the vibrator service.
	 * 
	 * */
	public Dot(int x, int y, int record, Bitmap img, Game game, List<Mask> mask, Point targetPos, Context context) {
		super(x, y, img, game, mask, true, 5);
		soundConfig =  new SoundConfig(context, game);
		launched = false;
		param = 0;
		this.stopAnim();
		
		this.game = (GolfGame) game;
		
		dotCenterX = this.x + this.getImgWidth()/2;
		dotCenterY = this.y + this.getImgHeight()/2;
		
		this.mVibrator = (Vibrator) this.game.getContext().getSystemService(Context.VIBRATOR_SERVICE);

		leftVol = 100;
		rightVol = leftVol;
		headPhonesMode = soundConfig.isWiredHeadsetOn();
		
		this.targetPos = targetPos;
		
		Bitmap scoreBoardImg = BitmapFactory.decodeResource(this.game.getView().getResources(), R.drawable.scoreboard);
		scoreBoardImg = CustomBitmap.getResizedBitmap(scoreBoardImg, scoreBoardImg.getWidth()*2, scoreBoardImg.getHeight()*2);
		scoreBoard = new ScoreBoard(0,500,record,scoreBoardImg, game, null, false, 0);
		this.game.addEntity(scoreBoard);
		
		Music.getInstanceMusic().play(this.game.getContext(),previous_shot_feedback_sound,true);
		Music.getInstanceMusic().setVolume(0, 0,previous_shot_feedback_sound);
		if(!headPhonesMode){
			Music.getInstanceMusic().play(this.game.getContext(),alternative_previous_shot_feedback_sound,true);
			Music.getInstanceMusic().setVolume(0, 0,alternative_previous_shot_feedback_sound);
		}
	}
	
	/**
	 * Use a parametric equation to generate the points of the dot's trajectory
	 * */
	@Override
	public void onUpdate() {
		if (SettingsActivity.getOnUp(this.game.getContext()))
			upModeManagement();
		else
			normalModeShotManagement();

		dopplerEffectManagement();
		
		onScrollManagement();
	
		if(launched){
			this.playAnim();
			// parametric equation defined by initial event and final event associated to onFling event
			float auxX = initialX + param * v.x; 
			float auxY = initialY + param * v.y;

			param = param + incr;
			
			this.x = (int) auxX;
			this.y = (int) auxY;
			
			if (this.x <= -this.getImgWidth() || this.x > game.getView().getWidth() || this.y <= Game.SCREEN_HEIGHT/6 -this.getImgHeight()){
				// move	s ball to origin position
				Distance dist = soundConfig.playSound(targetPos.x, this.x);
				this.resetBall();
				if(this.game.isStageMode()){
					manageScoreBoard(dist);
					this.game.nextStage(scoreBoard.getCounter());
				}
				else{
					scoreBoard.resetCounter();
				}
			}
		}
		super.onUpdate();
	}

	private void manageScoreBoard(Distance dist) {
    	switch (dist){
			case CLOSE: 
						scoreBoard.incrementCounter(5);
						break;
			case V_CLOSE: 	
						scoreBoard.decrementCounter(5);
						break;
			case FAR: 		
						scoreBoard.incrementCounter(5);
						break;
			case V_FAR: 
						scoreBoard.decrementCounter(5);
						break;
		}
	}

	/**
	 * Manages all actions related with scroll events on the dot.
	 * 
	 * */
	private void onScrollManagement() {
		EventType e  = Input.getInput().removeEvent("onScroll");
		if(!launched && e != null){
			this.playAnim();
			
			if(RuntimeConfig.IS_DEBUG_MODE){
				scrollX = e.getMotionEventE2().getX();
				scrollY = e.getMotionEventE2().getY();
			}
		
			feedBackManagement(e.getMotionEventE2().getX(),e.getMotionEventE2().getY());
			
			// if tap event outside the shoot area has been received it play a sound effect.
			if(!inShotArea(e.getMotionEventE2().getY()) || !inShotArea(e.getMotionEventE1().getY())){
				Music.getInstanceMusic().play(this.game.getContext(), R.raw.bip, false);
				Music.getInstanceMusic().play(this.game.getContext(), previous_shot_feedback_sound, true);
				Music.getInstanceMusic().setVolume(0, 0, previous_shot_feedback_sound);
				if(!headPhonesMode){
					Music.getInstanceMusic().play(this.game.getContext(), alternative_previous_shot_feedback_sound, true);
					Music.getInstanceMusic().setVolume(0, 0, alternative_previous_shot_feedback_sound);
				}
				outOfShotAreaCounter++;
				if(outOfShotAreaCounter == MAX_SHOTS_FAILED){
					this.game.getTTS().speak(this.game.getContext().getString(R.string.alertOutsideShotArea));
					outOfShotAreaCounter = 0;
				}
			}
		}
		else{ 
			this.stopAnim();
		}
	}

	/**
	 * It calls father's onDraw and draws trajectory lines if debug mode is enabled.
	 * 
	 * @param canvas surface which will be drawn
	 * 
	 * */
	protected void onDraw(Canvas canvas){
		super.onDraw(canvas);
		
		if(RuntimeConfig.IS_DEBUG_MODE){
			Paint brush = new Paint();
			brush.setColor(Color.BLACK);
			brush.setStrokeWidth(3);
			canvas.drawLine(dotCenterX, dotCenterY, targetPos.x, 0, brush);
			canvas.drawLine(scrollX, scrollY, dotCenterX, dotCenterY, brush);
		}
	}
	
	
	/**
	 * If onFling event occurs initializes the shot 
	 * In case of onScroll event manage the vibration or plays stereo sound and the animation. Also it plays a sound effect 
	 * if the event isn't on the shot area.
	 * 
	 * */
	private void normalModeShotManagement() {
		EventType e  = Input.getInput().removeEvent("onFling");
		if (!launched &&  e != null){
			if (e.getDvy() > 0){
				v = new Point((int)(dotCenterX - e.getMotionEventE2().getX()),
						      (int)(dotCenterY - e.getMotionEventE2().getY()));
				if (inShotArea(e.getMotionEventE1().getY())&& inShotArea(e.getMotionEventE2().getY())){
					launched = true;
					outOfShotAreaCounter = 0;
					this.playAnim();
					shotEvent = e;
					param = 0.5f;
					incr = 0.05f;
					initialX = this.x;
					initialY = this.y;
					Music.getInstanceMusic().play(this.game.getContext(), hit_feedback_sound,false);
					Music.getInstanceMusic().stop(this.game.getContext(), previous_shot_feedback_sound);
					Music.getInstanceMusic().play(this.game.getContext(), doppler_sound, true);
					if(!headPhonesMode){
						Music.getInstanceMusic().stop(this.game.getContext(), alternative_previous_shot_feedback_sound);
						Music.getInstanceMusic().play(this.game.getContext(), alternative_doppler_sound, true);
					}
				}
			}
		}
		// If onDown event occurs it create a onDownTarget event
		EventType onDown  = Input.getInput().removeEvent("onDown");
		if(onDown != null)
			Input.getInput().addEvent("onDownTarget", onDown.getMotionEventE1(), null, -1, -1);
	}

	/**
	 * checks if y is in the shot area. It's supposed that x is out of there
	 * 
	 * */
	private boolean inShotArea(float y) {
		int height = this.game.getView().getHeight(); 
		return (y < height-5) && (y > (height - height/3));
	}

	private void upModeManagement() {
		EventType eu = Input.getInput().getEvent("onUp");
		EventType ed  = Input.getInput().getEvent("onDown");
		
		if (!launched &&  eu != null && ed != null){
			MotionEvent e1 = ed.getMotionEventE1();
			MotionEvent e2 = eu.getMotionEventE1();
			// Si hay desplazamiento en y (accion tirachinas)
			if (e2.getRawY() - e1.getRawY() > 0){
				Input.getInput().remove("onUp");
				v = new Point((int)(dotCenterX- e2.getX()),
						      (int)(dotCenterY - e2.getY()));
				if (inShotArea(e1.getY())&& inShotArea(e2.getY())){
					Input.getInput().remove("onDown");
					launched = true;
					this.playAnim();
					shotEvent = eu;
					param = 0.5f;
					incr = 0.05f;
					initialX = this.x;
					initialY = this.y;
					Music.getInstanceMusic().play(this.game.getContext(), hit_feedback_sound,false);
					Music.getInstanceMusic().stop(this.game.getContext(), previous_shot_feedback_sound);
					Music.getInstanceMusic().play(this.game.getContext(), doppler_sound, true);
					if(!headPhonesMode){
						Music.getInstanceMusic().stop(this.game.getContext(), alternative_previous_shot_feedback_sound);
						Music.getInstanceMusic().play(this.game.getContext(), alternative_doppler_sound, true);
					}
					
				}
			}	
		}
		if(eu == null && ed != null){
			// If onDown event occurs it create a onDownTarget event
			MotionEvent e = ed.getMotionEventE1();
			if(!inShotArea(e.getY())){
				EventType onDown  = Input.getInput().removeEvent("onDown");
				Input.getInput().addEvent("onDownTarget", onDown.getMotionEventE1(), null, -1, -1);
			}
		}
		 
	}

	/**
	 * Creates a new Target and increment scoreboard counter
	 * */
	@Override
	public void onCollision(Entity e) {
		// Hole and ball collides
		if (e instanceof Target){
				Music.getInstanceMusic().play(this.game.getContext(), success_feedback_sound, false); // win sound
				// if we win it creates a new Target
				Target t = (Target) e;
				targetPos = t.changePosition();
				
				// increments scoreboard
				if(game.isStageMode())
					scoreBoard.incrementCounter(25);
				else
					scoreBoard.incrementCounter();
				
				// moves ball to origin position
				this.resetBall();
				
				if(this.game.isStageMode()){
					this.game.nextStage(scoreBoard.getCounter());
				}
		}
	}

	/**
	 * 	Moves ball to origin position
	 * 
	 * */
	private void resetBall(){
		Music.getInstanceMusic().play(this.game.getContext(),previous_shot_feedback_sound,true);
		Music.getInstanceMusic().setVolume(0, 0, previous_shot_feedback_sound);
		Music.getInstanceMusic().stop(this.game.getContext(), doppler_sound);
		if(!headPhonesMode){
			Music.getInstanceMusic().play(this.game.getContext(),alternative_previous_shot_feedback_sound,true);
			Music.getInstanceMusic().setVolume(0, 0, alternative_previous_shot_feedback_sound);
			Music.getInstanceMusic().stop(this.game.getContext(), alternative_doppler_sound);
			Music.getInstanceMusic().stop(this.game.getContext(), alternative_doppler_sound);
		}

		this.setX(originX);
		this.setY(originY);
		launched = false;
		this.stopAnim();
	}
	
	@Override
	public void onTimer(int timer) {}

	@Override
	public void onInit() {}

	private void feedBackManagement(float shotX, float shotY) {
		double angRadiansTarget = Math.atan2(dotCenterY, targetPos.x - dotCenterX);
		double angRadiansMovement = Math.atan2(dotCenterY - shotY, dotCenterX - shotX);
	
		double angTarget = Math.toDegrees(angRadiansTarget);
		double angMovement = Math.abs(Math.toDegrees(angRadiansMovement));
		
		float diffAng = (float) Math.abs(angMovement -angTarget);
		
		// Vibration feedback
		if(SettingsActivity.getVibrationFeedback(this.game.getContext()) && !launched){
			if(diffAng < 5){
				mVibrator.vibrate(100); 
			}
		}
		
		// Before shooting sound
		if(SettingsActivity.getSoundFeedBack(this.game.getContext()) && !launched){
			if(diffAng < 5)
				Music.getInstanceMusic().setVolume(leftVol/100, rightVol/100,previous_shot_feedback_sound);
			else{
					if(angMovement < angTarget){	
						if(headPhonesMode)
							Music.getInstanceMusic().setVolume(0,(rightVol - diffAng*(3/2))/100,previous_shot_feedback_sound);
						else{
							Music.getInstanceMusic().setVolume(0,0,previous_shot_feedback_sound);
							Music.getInstanceMusic().setVolume(0, (rightVol - diffAng*(3/2))/100, alternative_previous_shot_feedback_sound);
						}
					}
					else{
						if(headPhonesMode)
							Music.getInstanceMusic().setVolume((leftVol - diffAng*(3/2))/100, 0, previous_shot_feedback_sound);
						else{
							Music.getInstanceMusic().setVolume(0,0,alternative_previous_shot_feedback_sound);
							Music.getInstanceMusic().setVolume((leftVol - diffAng*(3/2))/100, 0,previous_shot_feedback_sound);
						}
					}
			}
		}
	}
	
	private void dopplerEffectManagement(){

		if(SettingsActivity.getDopplerEffect(this.game.getContext())&& launched){
				double angRadiansTarget = Math.atan2(dotCenterY, targetPos.x - dotCenterX);
				double angRadiansMovement;
				if(SettingsActivity.getOnUp(this.game.getContext())){
					angRadiansMovement = Math.atan2(dotCenterY - shotEvent.getMotionEventE1().getY(), 
															dotCenterX - shotEvent.getMotionEventE1().getX());
				}
				else{
					angRadiansMovement = Math.atan2(dotCenterY - shotEvent.getMotionEventE2().getY(), 
							dotCenterX - shotEvent.getMotionEventE2().getX());
				}
				double angTarget = Math.toDegrees(angRadiansTarget);
				double angMovement = Math.abs(Math.toDegrees(angRadiansMovement));
				float diffAng = (float) Math.abs(angMovement -angTarget);
				
				float aux = Math.abs(- initialY) + 1;
				float componentY = (Math.abs(initialY - this.y)*1)/aux; 
				if(diffAng < 5)
					Music.getInstanceMusic().setVolume(leftVol/100, rightVol/100, doppler_sound);
				else{
					if(angMovement < angTarget){
						if(headPhonesMode)
							Music.getInstanceMusic().setVolume(componentY, 0, doppler_sound);
						else{
							Music.getInstanceMusic().setVolume(0, 0, doppler_sound);
							Music.getInstanceMusic().setVolume(componentY, 0, alternative_doppler_sound);
						}
					}
					else{
						if(headPhonesMode)
							Music.getInstanceMusic().setVolume(0, componentY, doppler_sound);
						else{
							Music.getInstanceMusic().setVolume(0,0,alternative_doppler_sound);
							Music.getInstanceMusic().setVolume(0, componentY, doppler_sound);
						}
					}
				}
		}
	}	
}
