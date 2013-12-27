package com.onscripter.plus;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.view.MotionEvent;
import android.view.KeyEvent;
import android.view.Window;
import android.view.WindowManager;
import android.media.AudioTrack;
import android.media.AudioManager;
import android.media.AudioFormat;
import java.io.*;
import java.nio.ByteBuffer;
import android.util.Log;
import java.lang.Thread;


class AudioThread {

	private Activity mParent;
	private AudioTrack mAudio;
	private byte[] mAudioBuffer;

	public AudioThread(Activity parent)
	{
		mParent = parent;
		mAudio = null;
		mAudioBuffer = null;
		nativeAudioInitJavaCallbacks();
	}
	
	public int fillBuffer()
	{
		while (mAudio.getPlayState() == mAudio.PLAYSTATE_PAUSED)
			try{
				Thread.currentThread().sleep(500);
			} catch(Exception e){};
		mAudio.write( mAudioBuffer, 0, mAudioBuffer.length );
		return 1;
	}
	
	public int initAudio(int rate, int channels, int encoding, int bufSize)
	{
		if( mAudio == null )
		{
			channels = ( channels == 1 ) ? AudioFormat.CHANNEL_CONFIGURATION_MONO : 
											AudioFormat.CHANNEL_CONFIGURATION_STEREO;
			encoding = ( encoding == 1 ) ? AudioFormat.ENCODING_PCM_16BIT :
											AudioFormat.ENCODING_PCM_8BIT;

			if( AudioTrack.getMinBufferSize( rate, channels, encoding ) > bufSize )
				bufSize = AudioTrack.getMinBufferSize( rate, channels, encoding );

			mAudioBuffer = new byte[bufSize];

			mAudio = new AudioTrack(AudioManager.STREAM_MUSIC, 
									rate,
									channels,
									encoding,
									bufSize,
									AudioTrack.MODE_STREAM );
			mAudio.play();
		}
		return mAudioBuffer.length;
	}
	
	public byte[] getBuffer()
	{
		return mAudioBuffer;
	}
	
	public int deinitAudio()
	{
		if( mAudio != null )
		{
			mAudio.stop();
			mAudio.release();
			mAudio = null;
		}
		mAudioBuffer = null;
		return 1;
	}
	
	public int initAudioThread()
	{
		// Make audio thread priority higher so audio thread won't get underrun
		Thread.currentThread().setPriority(Thread.MAX_PRIORITY);
		return 1;
	}
	
	public void onPause() {
		if( mAudio != null )
			mAudio.pause();
	}

	public void onResume() {
		if( mAudio != null )
			mAudio.play();
	}

	private native int nativeAudioInitJavaCallbacks();
}

