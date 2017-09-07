package com.google.ar.core.examples.java.helloar;

import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import com.google.ar.core.Config;
import com.google.ar.core.Session;

import org.rajawali3d.renderer.Renderer;
import org.rajawali3d.view.ISurface;
import org.rajawali3d.view.SurfaceView;

import java.util.concurrent.ArrayBlockingQueue;

public class RajaActivity extends AppCompatActivity implements RajaRenderer.Callback {

	private Config config;
	private Session session;
	private GestureDetector gestureDetector;
	private Snackbar loadingMessageSnackbar = null;

	// Tap handling and UI.
	private ArrayBlockingQueue<MotionEvent> queuedSingleTaps = new ArrayBlockingQueue<>(16);

	SurfaceView surface;
	Renderer renderer;

	@Override
	protected void onCreate(@Nullable Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		session = new Session(/*context=*/this);

		// Create default config, check is supported, create session from that config.
		config = Config.createDefaultConfig();
		if (!session.isSupported(config)) {
			Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
			finish();
			return;
		}

		// Set up tap listener.
		gestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
			@Override
			public boolean onSingleTapUp(MotionEvent e) {
				onSingleTap(e);
				return true;
			}

			@Override
			public boolean onDown(MotionEvent e) {
				return true;
			}
		});

		surface = new SurfaceView(this);
		surface.setId(R.id.surfaceview);
		surface.setPreserveEGLContextOnPause(true);
		//surface.setFrameRate(60.0);
		surface.setTransparent(true);
		surface.setRenderMode(ISurface.RENDERMODE_WHEN_DIRTY);
		surface.setOnTouchListener(new View.OnTouchListener() {
			@Override
			public boolean onTouch(View v, MotionEvent event) {
				return gestureDetector.onTouchEvent(event);
			}
		});
		surface.setSurfaceRenderer(
				renderer = new RajaRenderer(this, session, queuedSingleTaps, this)
		);

		setContentView(surface);
	}

	@Override
	protected void onResume() {
		super.onResume();

		// ARCore requires camera permissions to operate. If we did not yet obtain runtime
		// permission on Android M and above, now is a good time to ask the user for it.
		if (CameraPermissionHelper.hasCameraPermission(this)) {
			showLoadingMessage();
			// Note that order matters - see the note in onPause(), the reverse applies here.
			session.resume(config);
			surface.onResume();
		} else {
			CameraPermissionHelper.requestCameraPermission(this);
		}
	}

	@Override
	public void onPause() {
		super.onPause();
		// Note that the order matters - GLSurfaceView is paused first so that it does not try
		// to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
		// still call session.update() and get a SessionPausedException.
		surface.onPause();
		session.pause();
	}

	@Override
	public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] results) {
		if (!CameraPermissionHelper.hasCameraPermission(this)) {
			Toast.makeText(this,
					"Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
			finish();
		}
	}

	@Override
	public void onWindowFocusChanged(boolean hasFocus) {
		super.onWindowFocusChanged(hasFocus);
		if (hasFocus) {
			// Standard Android full-screen functionality.
			getWindow().getDecorView().setSystemUiVisibility(
					View.SYSTEM_UI_FLAG_LAYOUT_STABLE
							| View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
							| View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
							| View.SYSTEM_UI_FLAG_FULLSCREEN
							| View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
			getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		}
	}

	private void onSingleTap(MotionEvent e) {
		// Queue tap if there is space. Tap is lost if queue is full.
		queuedSingleTaps.offer(e);
	}

	private void showLoadingMessage() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				loadingMessageSnackbar = Snackbar.make(
						findViewById(R.id.surfaceview),
						"Searching for surfaces...",
						Snackbar.LENGTH_INDEFINITE
				);
				loadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
				loadingMessageSnackbar.show();
			}
		});
	}

	@Override
	public boolean isLoadingMessageShown() {
		return loadingMessageSnackbar != null;
	}

	@Override
	public void hideLoadingMessage() {
		runOnUiThread(new Runnable() {
			@Override
			public void run() {
				loadingMessageSnackbar.dismiss();
				loadingMessageSnackbar = null;
			}
		});
	}
}