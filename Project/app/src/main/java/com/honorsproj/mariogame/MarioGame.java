package com.honorsproj.mariogame;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.os.Build;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.MotionEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.Iterator;

public class MarioGame extends AppCompatActivity
{
    Model model;
    GameView view;
    GameController controller;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        // Sets game to fullscreen
        View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE
                | View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                // | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN);

        model = new Model();
        view = new GameView(this, model);
        controller = new GameController(model, view);

        setContentView(view);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState)
    {
        super.onPostCreate(savedInstanceState);
    }

    @Override
    protected void onResume()
    {
        super.onResume();
        controller.resume();
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        controller.pause();
    }

    // SPRITE CLASS
    static abstract class Sprite
    {
        int x, y;   // Position Coordinates
        int w, h;   // Size of bitmap (and thus sprite)
        boolean flip;   // True if sprite faces left
        boolean kill;   // Marks sprite to be removed from game

        abstract void update();

        boolean isMario()
        {
            return false;
        }

        boolean isTube()
        {
            return false;
        }

        boolean isFireball()
        {
            return false;
        }

        boolean isGoomba()
        {
            return false;
        }

        // Returns true iff the two sprites collide together
        boolean collidesWith(Sprite s)
        {
            if (!(x + w < s.x)
                && !(x > s.x + s.w)
                && !(y + h < s.y)
                && !(y > s.y + s.h))
            {
                return true;
            }
            else
                return false;
        }
    }

    // GOOMBA CLASS
    static class Goomba extends Sprite
    {
        Model model;

        // When goomba gets hit by a fireball...
        boolean dying;
        int deathTimer;
        // Vertical velocity
        float velY;

        Goomba(Model m, int posX, int posY)
        {
            model = m;

            x = posX;
            y = posY;
            w = -1;
            h = -1;

            dying = false;
            deathTimer = 8;
            kill = false;

            velY = 0.0f;
        }

        @Override
        void update() {
            if (flip && !dying)   // Move to right. Goomba orientation is backwards, I know.
                x += 10;                        // Sorry about it.
            else if (!dying)
                x -= 10;

            // Time until death
            if (dying && deathTimer < 1)
            {
                kill = true;
            }
            else if (dying)
            {
                deathTimer--;
            }

            Iterator<Sprite> sprites = model.sprites.iterator();

            while (sprites.hasNext())
            {
                Sprite s = sprites.next();

                if ( !dying && s.isFireball() && collidesWith(s))
                {
                    dying = true;   // Mark for death
                    s.kill = true;
                }
                else if (s.isTube() && collidesWith(s))
                {
                    flip = !flip;   // Flip goomba if they collide with a tube
                }
            }

            // Keeps goomba from briefly clipping through the ground
            if (model.ground - y - h < velY)
            {
                y = model.ground - h;
                velY = 0;
            }
            // Goomba falling normally
            else if (y + h < model.ground)
            {
                velY += 1.4;
            }
            else
            {
                velY = 0;
            }

            // Update vertical position
            y += velY;
        }

        @Override
        boolean isGoomba()
        {
            return true;
        }
    }

    // FIREBALL CLASS
    static class Fireball extends Sprite
    {
        Model model;
        float velY;
        int speed;  // Horizontal speed

        Fireball(Model m, int posX, int posY, boolean flip)
        {
            model = m;

            x = posX;
            y = posY;
            w = -1;
            h = -1;

            this.flip = flip;
            kill = false;

            velY = 0.0f;
            speed = 25;
        }

        @Override
        void update()
        {
            // Horizontal movement
            if (flip)
                x -= speed;
            else
                x += speed;

            // Remove fireball from game once it has left the screen + some safety
            //              <----- TO DO ----->

            // Vertical motion
            if (y + h < model.ground)
            {
                velY += 2.0f;
            }

            y += velY;

            if (y + h > model.ground)
            {
                y = model.ground - h;
                velY = -velY;
            }
        }

        @Override
        boolean isFireball()
        {
            return true;
        }
    }

    // TUBE CLASS
    static class Tube extends Sprite
    {
        Tube(int posX, int posY)
        {
            x = posX;
            y = posY;
            // This is hacky
            w = -1;
            h = -1;
        }

        @Override
        void update()
        {
        }

        @Override
        boolean isTube()
        {
            return true;
        }
    }

    // MARIO CLASS
    static class Mario extends Sprite
    {
        Model model;    // Reference to game Model for collision detection

        int prevX, prevY;   // Previous position coordinates (from last frame)

        float velY; // Vertical velocity
        int frame;  // Animation frame
        boolean fireReady;   // Keeps fireball action semi-automatic

        boolean jumping;
        int cooldownTime;
        int jumpCooldown;
        int maxLimit;
        int jumpLimit;

        Mario(Model m, int posX, int posY)
        {
            model = m;
            x = posX;
            y = posY;

            flip = false;

            velY = 0.0f;
            frame = 0;
            fireReady = true;

            jumping = false;
            cooldownTime = 4;
            jumpCooldown = 0;
            maxLimit = 9;
            jumpLimit = maxLimit;
        }

        @Override
        void update()
        {
            if (    jumping
                    && jumpCooldown == cooldownTime
                    && jumpLimit > 0
                    && velY <= 0)
            {
                velY = -23;
                jumpLimit--;
            }
            // Keeps Mario from briefly clipping through the ground
            else if (model.ground - y - h < velY)
            {
                y = model.ground - h;
                velY = 0;
            }
            // Mario falling normally
            else if (y + h < model.ground)
            {
                velY += 1.4;
            }
            else
            {
                velY = 0;
            }

            // Update vertical position
            y += velY;

            if (!jumping && jumpLimit < maxLimit && jumpCooldown == cooldownTime)
            {
                jumpCooldown = 0;
                jumpLimit = maxLimit;
            }

            // Mario-Tube collision detection
            Iterator<Sprite> sprites = model.sprites.iterator();

            while (sprites.hasNext())
            {
                Sprite s = sprites.next();

                if (s.isTube() && this.collidesWith(s))
                {
                    fixCollision(s);
                }
            }

            // Prevents Mario from jumping midair
            if (jumpCooldown == cooldownTime && velY == 0)
            {
                jumpCooldown = 0;
            }

            // jumpCooldown timer
            if (jumpCooldown < cooldownTime && velY == 0)
            {
                jumpCooldown++;
            }

            // Store position to be referenced next frame during collisions
            prevX = x;
            prevY = y;
        }

        void fire()
        {
            if (flip)
            {
                model.sprites.add(new Fireball(model, x - (int)(w*0.1),
                        y + (int)(h*0.2), true));
            }
            else
            {
                model.sprites.add(new Fireball(model, x + (int)(w*0.75),
                        y + (int)(h*0.2), false));
            }
        }

        // Fixes collisions between Mario and tubes
        void fixCollision(Sprite s)
        {
            // Mario coming from above
            if (prevY + h < s.y)
            {
                // Corrects Mario's position
                y = s.y - h - 1;
                // Stops Mario's fall
                velY = 0;
            }
            // Mario coming from the right
            else if (prevX > s.x + s.w)
            {
                int viewCorrect = x;

                // Corrects Mario's position
                x = s.x + s.w + 1;

                // Corrects scroll position
                viewCorrect -= x;
                model.scrollVal -= viewCorrect;
            }
            // Mario coming from the left
            else
            {
                int viewCorrect = x;

                // Corrects Mario's position
                x = s.x - w - 1;

                // Corrects scroll position
                viewCorrect -= x;
                model.scrollVal -= viewCorrect;
            }
        }

        @Override
        boolean isMario()
        {
            return true;
        }
    }

    static class Model
    {
        ArrayList<Sprite> sprites;
        Mario mario;

        int ground;     // y coordinate of top of ground
        int scrollVal;  // Screen offset that changes as Mario moves

        Model()
        {
            sprites = new ArrayList<Sprite>();  // ArrayList of all sprites in game
            mario = new Mario(this, 500, 200);  // Extra reference to Mario
            sprites.add(mario);

            // Add tubes
            sprites.add(new Tube(100, ground + 600));
            sprites.add(new Tube(1100, ground + 700));
            sprites.add(new Tube(1700, ground + 400));

            // Add goomba
            sprites.add(new Goomba(this, 1400, ground + 550));
            sprites.add(new Goomba(this, 1500, ground + 550));
            sprites.add(new Goomba(this, 500, ground + 550));
            sprites.add(new Goomba(this, 1600, ground + 550));
        }

        void update()
        {
            Iterator<Sprite> allSprites = sprites.iterator();

            while (allSprites.hasNext())
            {
                Sprite s = allSprites.next();

                // Kills sprite if necessary
                if (s.kill)
                    allSprites.remove();
                // Updates sprite otherwise
                else
                    s.update();
            }
        }
    }

    class GameView extends SurfaceView
    {   // Note: I made this class non-static so I could get screen size
        SurfaceHolder ourHolder;
        Canvas canvas;
        Paint paint;
        Paint paintAlpha;
        Model model;
        GameController controller;

        int sWidth;     // Screen width
        int sHeight;    // Screen height

        // Ground image
        Bitmap groundImg;

        // Button images
        Bitmap moveRight;
        Bitmap moveLeft;
        Bitmap jumpButton;
        Bitmap fireButton;

        // Mario images (normal orientation and reversed)
        ArrayList<Bitmap> marioImg = new ArrayList<Bitmap>();
        ArrayList<Bitmap> marioImg_flip = new ArrayList<Bitmap>();

        // Tube image
        Bitmap tubeImg;

        // Fireball image
        Bitmap fireImg;
        Bitmap fireImg_flip;

        // Goomba images (normal orientation and reversed)
        ArrayList<Bitmap> goombaImg = new ArrayList<Bitmap>();
        ArrayList<Bitmap> goombaImg_flip = new ArrayList<Bitmap>();

        public GameView(Context context, Model m)
        {
            super(context);
            model = m;

            // Initialize ourHolder and paint objects
            ourHolder = getHolder();
            paint = new Paint();
            // Paint used for buttons to make them translucent
            paintAlpha = new Paint();
            paintAlpha.setAlpha(90);

            // Get screen specs
            DisplayMetrics screenSpec = new DisplayMetrics();
            getWindowManager().getDefaultDisplay().getMetrics(screenSpec);
            sWidth = screenSpec.widthPixels;
            sHeight = screenSpec.heightPixels;

            // Matrix used to flip sprites
            Matrix matrix = new Matrix();
            matrix.preScale(-1, 1);

            // Get ground image
            Bitmap groundResize = BitmapFactory.decodeResource(this.getResources(),
                                                          R.drawable.mario_ground);
            // Resize ground image and store
            groundImg = Bitmap.createScaledBitmap(groundResize, sWidth,
                    groundResize.getHeight(), false);
            // Set model's ground level
            model.ground = sHeight - groundImg.getHeight();

            // Get move button
            moveRight = BitmapFactory.decodeResource(this.getResources(),
                                             R.drawable.movement_button);
            // Scale move button
            moveRight = Bitmap.createScaledBitmap(
                    moveRight,
                    (int)(sWidth * 0.22),
                    (int)(sHeight * 0.5),
                    false);
            // Reverse move button
            moveLeft = Bitmap.createBitmap(
                    moveRight,
                    0,
                    0,
                    moveRight.getWidth(),
                    moveRight.getHeight(),
                    matrix,
                    false);

            // Get jump button
            jumpButton = BitmapFactory.decodeResource(this.getResources(),
                                                  R.drawable.jump_button);
            // Scale jump button
            jumpButton = Bitmap.createScaledBitmap(
                    jumpButton,
                    (int)(sWidth * 0.11),
                    (int)(sHeight * 0.5),
                    false);

            // Get fireball button
            fireButton = BitmapFactory.decodeResource(this.getResources(),
                                                  R.drawable.fire_button);
            // Scale fireball button
            fireButton = Bitmap.createScaledBitmap(
                    fireButton,
                    (int)(sWidth * 0.11),
                    (int)(sHeight * 0.5),
                    false);

            // Get tube image
            tubeImg = BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.tube);

            // Get Mario images
            marioImg.add(BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.mario1));
            marioImg.add(BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.mario2));
            marioImg.add(BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.mario3));
            marioImg.add(BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.mario4));
            marioImg.add(BitmapFactory.decodeResource(this.getResources(),
                                                      R.drawable.mario5));

            // Creates ArrayList of reversed Mario bitmaps
            for (int i = 0; i < 5; i++)
            {
                marioImg_flip.add(Bitmap.createBitmap(
                        marioImg.get(i),
                        0,
                        0,
                        marioImg.get(i).getWidth(),
                        marioImg.get(i).getHeight(),
                        matrix,
                        false));

                marioImg_flip.get(i).setDensity(DisplayMetrics.DENSITY_DEFAULT);
            }

            // Get normal goomba image
            Bitmap goombaResize = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.goomba);
            // Resize and store normal goomba image
            goombaImg.add(Bitmap.createScaledBitmap(goombaResize,
                    (int)(marioImg.get(0).getWidth() * 0.8),
                    (int)(marioImg.get(0).getHeight() * 0.6),
                    false));

            // Get dying goomba image
            goombaResize = BitmapFactory.decodeResource(this.getResources(),
                    R.drawable.goomba_fire);
            // Resize and store dying goomba image
            goombaImg.add(Bitmap.createScaledBitmap(goombaResize,
                    (int)(marioImg.get(0).getWidth() * 0.8),
                    (int)(marioImg.get(0).getHeight() * 0.6),
                    false));

            // Create ArrayList of reversed Goomba bitmaps
            for (int i = 0; i < 2; i++)
            {
                goombaImg_flip.add(Bitmap.createBitmap(
                        goombaImg.get(i),
                        0,
                        0,
                        goombaImg.get(i).getWidth(),
                        goombaImg.get(i).getHeight(),
                        matrix,
                        false));

                goombaImg_flip.get(i).setDensity(DisplayMetrics.DENSITY_DEFAULT);
            }

            // Sets dimensions of Mario sprite (since bitmap size can vary)
            model.mario.w = marioImg.get(0).getWidth();
            model.mario.h = marioImg.get(0).getHeight();

            // Get fireball image
            Bitmap fireResize = BitmapFactory.decodeResource(this.getResources(),
                                                            R.drawable.fireball);
            // Resize fireball image and store
            fireImg = Bitmap.createScaledBitmap(fireResize,
                    (int)(model.mario.h * 0.4),
                    (int)(model.mario.h * 0.4),
                    false);

            // Store reversed fireball bitmap
            fireImg_flip = Bitmap.createBitmap(
                    fireImg,
                    0,
                    0,
                    fireImg.getWidth(),
                    fireImg.getHeight(),
                    matrix,
                    false);

            fireImg_flip.setDensity(DisplayMetrics.DENSITY_DEFAULT);
        }

        void setController(GameController c)
        {
            controller = c;
        }

        public void update()
        {
            if (!ourHolder.getSurface().isValid())
                return;
            canvas = ourHolder.lockCanvas();

            // Draw the background color
            canvas.drawColor(Color.argb(255, 0, 232, 252));

            // Draw the ground
            canvas.drawBitmap(groundImg, 0, sHeight - groundImg.getHeight(), paint);

            // DRAW BUTTONS
            // Draw right move
            canvas.drawBitmap(
                    moveRight,
                    (float)(sWidth * 0.78),
                    (float)(sHeight * 0.5),
                    paintAlpha);

            // Draw left move
            canvas.drawBitmap(
                    moveLeft,
                    0,
                    (float)(sHeight * 0.5),
                    paintAlpha);

            // Draw jump button (right)
            canvas.drawBitmap(
                    jumpButton,
                    (float)(sWidth * 0.89),
                    0,
                    paintAlpha);

            // Draw jump button (left)
            canvas.drawBitmap(
                    jumpButton,
                    0,
                    0,
                    paintAlpha);

            // Draw fireball button (right)
            canvas.drawBitmap(
                    fireButton,
                    (float)(sWidth * 0.78),
                    0,
                    paintAlpha);

            // Draw fireball button (left)
            canvas.drawBitmap(
                    fireButton,
                    (float)(sWidth * 0.11),
                    0,
                    paintAlpha);

            // Draw all sprites
            Iterator<Sprite> sprites = model.sprites.iterator();

            while (sprites.hasNext())
            {
                Sprite s = sprites.next();

                // Draws Mario
                if (s.isMario())
                {
                    int frame = model.mario.frame;  // Store animation frame

                    if (s.flip)
                    {
                        canvas.drawBitmap(marioImg_flip.get(frame),
                                s.x - model.scrollVal, s.y, paint);
                    }
                    else
                    {
                        canvas.drawBitmap(marioImg.get(frame),
                                s.x - model.scrollVal, s.y, paint);
                    }
                }
                // Draws tubes
                else if (s.isTube())
                {
                    // Hacky solution to problem that is not relevant to View
                    if (s.w == -1)
                    {
                        s.w = tubeImg.getWidth();
                        s.h = tubeImg.getHeight();
                    }

                    canvas.drawBitmap(tubeImg, s.x - model.scrollVal, s.y, paint);
                }
                // Draws goombas
                else if (s.isGoomba())
                {
                    /*
                    Again, hacky solution like above when drawing tubes.
                    I didn't want model having a reference to view, so this was
                    the way I set the widths and heights of certain sprites.
                     */
                    if (s.w == -1)
                    {
                        s.w = goombaImg.get(0).getWidth();
                        s.h = goombaImg.get(0).getHeight();
                    }

                    Goomba goomba = (Goomba)s;
                    int frame = goomba.dying ? 1 : 0;   // Determines which bitmap to draw

                    if (s.flip)
                    {
                        canvas.drawBitmap(goombaImg_flip.get(frame), s.x - model.scrollVal,
                                s.y, paint);
                    }
                    else
                    {
                        canvas.drawBitmap(goombaImg.get(frame), s.x - model.scrollVal,
                                s.y, paint);
                    }
                }
                // Draws fireballs
                else if (s.isFireball())
                {
                    // Refer to previous hack-jobs
                    if (s.w == -1)
                    {
                        s.w = fireImg.getWidth();
                        s.h = fireImg.getHeight();
                    }

                    if (s.flip)
                    {
                        canvas.drawBitmap(fireImg_flip, s.x - model.scrollVal,
                                s.y, paint);
                    }
                    else
                    {
                        canvas.drawBitmap(fireImg, s.x - model.scrollVal,
                                s.y, paint);
                    }
                }
            }



            ourHolder.unlockCanvasAndPost(canvas);
        }

        // The SurfaceView class (which GameView extends) already
        // implements onTouchListener, so we override this method
        // and pass the event to the controller.
        @Override
        public boolean onTouchEvent(MotionEvent motionEvent)
        {
            controller.onTouchEvent(motionEvent);
            return true;
        }
    }

    static class GameController implements Runnable
    {
        volatile boolean playing;
        Thread gameThread = null;
        Model model;
        GameView view;

        float x, y;     // Coordinates of tap events

        // Speed at which Mario moves and the screen scrolls
        final static int speed = 18;

        GameController(Model m, GameView v)
        {
            model = m;
            view = v;
            view.setController(this);
            playing = true;

            x = -1f;
            y = -1f;
        }

        // Uses coordinates from touch event to do things
        void update()
        {
            // Tap bottom right corner
            if (x > view.sWidth * 0.78 && y > view.sHeight / 2)
            {
                // Move to right
                model.mario.x += speed;
                // Animate Mario
                model.mario.frame++;
                model.mario.frame %= 5;
                // Don't flip Mario
                model.mario.flip = false;

                // Scroll to right
                model.scrollVal += speed;
            }
            // Tap bottom left corner
            else if (x < view.sWidth * 0.22 && y > view.sHeight / 2 && x >= 0)
            {
                // Move to left
                model.mario.x -= speed;
                // Animate Mario
                model.mario.frame++;
                model.mario.frame %= 5;
                // Flip Mario
                model.mario.flip = true;

                // Scroll to left
                model.scrollVal -= speed;
            }

            // Tap one of the two jump buttons
            if((x < view.sWidth * 0.11 && y < view.sHeight / 2 && x > 0)
                    || (x > view.sWidth * 0.89 && y < view.sHeight / 2))
            {
                model.mario.jumping = true;
            }
            else
            {
                model.mario.jumping = false;
            }

            // Tap one of the two fireball buttons
            if (    (x > view.sWidth * 0.11
                    && x < view.sWidth * 0.22   // Left fire button
                    && y < view.sHeight / 2)
                    ||
                    (x < view.sWidth * 0.89
                    && x > view.sWidth * 0.78   // Right fire button
                    && y < view.sHeight / 2))
            {
                if (model.mario.fireReady)
                {
                    model.mario.fire();
                    model.mario.fireReady = false;
                }
            }
            else
            {
                model.mario.fireReady = true;   // Keeps fireballs semi-automatic
            }
        }

        @Override
        public void run()
        {
            while(playing)
            {
                //long time = System.currentTimeMillis();
                this.update();
                model.update();
                view.update();

                try
                {
                    Thread.sleep(10);
                }
                catch(Exception e)
                {
                    Log.e("Error:", "sleeping");
                    System.exit(1);
                }
            }
        }

        void onTouchEvent(MotionEvent e)
        {
            switch (e.getAction() & MotionEvent.ACTION_MASK)
            {
                case MotionEvent.ACTION_DOWN: // Player touched the screen
                    x = e.getX();
                    y = e.getY();
                    break;

                case MotionEvent.ACTION_UP: // Player withdrew finger
                    x = -1f;
                    y = -1f;
                    break;
            }
        }

        // Shut down the game thread.
        public void pause()
        {
            playing = false;
            try
            {
                gameThread.join();
            }
            catch (InterruptedException e)
            {
                Log.e("Error:", "joining thread");
                System.exit(1);
            }

        }

        // Restart the game thread.
        public void resume()
        {
            playing = true;
            gameThread = new Thread(this);
            gameThread.start();
        }
    }
}