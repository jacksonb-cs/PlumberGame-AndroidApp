package com.honorsproj.mariogame;

import androidx.appcompat.app.AppCompatActivity;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;

public class MainActivity extends AppCompatActivity
{
    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
    }

    // Starts the game
    public void startGame(View view)
    {
        Intent intent = new Intent(this, MarioGame.class);
        startActivity(intent);
    }
}