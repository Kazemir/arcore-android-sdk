package com.google.ar.core.examples.java.helloar;

import android.content.Context;
import android.util.Log;
import android.view.MotionEvent;
import android.view.animation.LinearInterpolator;

import com.google.ar.core.Frame;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PlaneHitResult;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.helloar.rendering.PlaneAttachment;

import org.rajawali3d.Object3D;
import org.rajawali3d.animation.Animation;
import org.rajawali3d.animation.Animation3D;
import org.rajawali3d.animation.EllipticalOrbitAnimation3D;
import org.rajawali3d.animation.RotateOnAxisAnimation;
import org.rajawali3d.lights.DirectionalLight;
import org.rajawali3d.materials.Material;
import org.rajawali3d.materials.methods.DiffuseMethod;
import org.rajawali3d.materials.textures.ATexture;
import org.rajawali3d.materials.textures.StreamingTexture;
import org.rajawali3d.materials.textures.Texture;
import org.rajawali3d.math.Matrix4;
import org.rajawali3d.math.Quaternion;
import org.rajawali3d.math.vector.Vector3;
import org.rajawali3d.primitives.ScreenQuad;
import org.rajawali3d.primitives.Sphere;
import org.rajawali3d.renderer.Renderer;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.Collections;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.opengles.GL10;

public class RajaRenderer extends Renderer {

	private static final String TAG = RajaRenderer.class.getSimpleName();

	private static final int COORDS_PER_VERTEX = 3;
	private static final int TEXCOORDS_PER_VERTEX = 2;
	private static final int FLOAT_SIZE = 4;

	public static final float[] QUAD_TEXCOORDS = new float[]{
			0.0f, 1.0f,
			0.0f, 0.0f,
			1.0f, 1.0f,
			1.0f, 0.0f,
	};

	// Temporary matrix allocated here to reduce number of allocations for each frame.
	private final float[] mAnchorMatrix = new float[16];

	private FloatBuffer mQuadTexCoord;
	private FloatBuffer mQuadTexCoordTransformed;

	private final ArrayList<PlaneAttachment> touches = new ArrayList<>();
	private final Session session;
	private final ArrayBlockingQueue<MotionEvent> queuedSingleTaps;
	private final Callback callback;

	// Rajawali texture used to render the Tango color camera.
	private ATexture cameraTexture;
	private ScreenQuad backgroundQuad;
	private DirectionalLight light;

	Object3D earth;
	Object3D moon;

	public RajaRenderer(
			Context context,
			Session session,
			ArrayBlockingQueue<MotionEvent> queuedSingleTaps,
			Callback callback
	) {
		super(context);
		//setFrameRate(60);
		this.session = session;
		this.queuedSingleTaps = queuedSingleTaps;
		this.callback = callback;
	}

	@Override
	protected void initScene() {
		// Create a quad covering the whole background and assign a texture to it where the
		// Tango color camera contents will be rendered.
		Material cameraMaterial = new Material();
		cameraMaterial.setColorInfluence(0);

		if (backgroundQuad == null) {
			backgroundQuad = new ScreenQuad();
			backgroundQuad.getGeometry().setTextureCoords(QUAD_TEXCOORDS);
		}
		// We need to use Rajawali's {@code StreamingTexture} since it sets up the texture
		// for GL_TEXTURE_EXTERNAL_OES rendering.
		cameraTexture =
				new StreamingTexture("camera", (StreamingTexture.ISurfaceListener) null);
		try {
			cameraMaterial.addTexture(cameraTexture);
			backgroundQuad.setMaterial(cameraMaterial);
		} catch (ATexture.TextureException e) {
			Log.e(TAG, "Exception creating texture for RGB camera contents", e);
		}
		getCurrentScene().addChildAt(backgroundQuad, 0);

		int numVertices = 4;
		ByteBuffer bbTexCoords = ByteBuffer.allocateDirect(
				numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
		bbTexCoords.order(ByteOrder.nativeOrder());
		mQuadTexCoord = bbTexCoords.asFloatBuffer();
		mQuadTexCoord.put(QUAD_TEXCOORDS);
		mQuadTexCoord.position(0);

		ByteBuffer bbTexCoordsTransformed = ByteBuffer.allocateDirect(
				numVertices * TEXCOORDS_PER_VERTEX * FLOAT_SIZE);
		bbTexCoordsTransformed.order(ByteOrder.nativeOrder());
		mQuadTexCoordTransformed = bbTexCoordsTransformed.asFloatBuffer();

		// Add a directional light in an arbitrary direction.
		light = new DirectionalLight(1, 0.2, -1);
		light.setColor(1, 1, 1);
		light.setPower(0.8f);
		light.setPosition(3, 2, 4);
		getCurrentScene().addLight(light);

		// Create sphere with earth texture and place it in space 3m forward from the origin.
		Material earthMaterial = new Material();
		try {
			Texture t = new Texture("earth", R.drawable.earth);
			earthMaterial.addTexture(t);
		} catch (ATexture.TextureException e) {
			Log.e(TAG, "Exception generating earth texture", e);
		}
		earthMaterial.setColorInfluence(0);
		earthMaterial.enableLighting(true);
		earthMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
		earth = new Sphere(0.4f, 20, 20);
		earth.setMaterial(earthMaterial);
		earth.setPosition(0, 0, -3);
		getCurrentScene().addChild(earth);

		// Rotate around its Y axis
		Animation3D animEarth = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
		animEarth.setInterpolator(new LinearInterpolator());
		animEarth.setDurationMilliseconds(60000);
		animEarth.setRepeatMode(Animation.RepeatMode.INFINITE);
		animEarth.setTransformable3D(earth);
		getCurrentScene().registerAnimation(animEarth);
		animEarth.play();

		// Create sphere with moon texture.
		Material moonMaterial = new Material();
		try {
			Texture t = new Texture("moon", R.drawable.moon);
			moonMaterial.addTexture(t);
		} catch (ATexture.TextureException e) {
			Log.e(TAG, "Exception generating moon texture", e);
		}
		moonMaterial.setColorInfluence(0);
		moonMaterial.enableLighting(true);
		moonMaterial.setDiffuseMethod(new DiffuseMethod.Lambert());
		moon = new Sphere(0.1f, 20, 20);
		moon.setMaterial(moonMaterial);
		moon.setPosition(0, 0, -1);
		getCurrentScene().addChild(moon);

		// Rotate the moon around its Y axis.
		Animation3D animMoon = new RotateOnAxisAnimation(Vector3.Axis.Y, 0, -360);
		animMoon.setInterpolator(new LinearInterpolator());
		animMoon.setDurationMilliseconds(60000);
		animMoon.setRepeatMode(Animation.RepeatMode.INFINITE);
		animMoon.setTransformable3D(moon);
		getCurrentScene().registerAnimation(animMoon);
		animMoon.play();

		// Make the moon orbit around the earth. The first two parameters are the focal point and
		// periapsis of the orbit.
		Animation3D translationMoon = new EllipticalOrbitAnimation3D(new Vector3(0, 0, -5),
				new Vector3(0, 0, -1), Vector3.getAxisVector(Vector3.Axis.Y), 0,
				360, EllipticalOrbitAnimation3D.OrbitDirection.COUNTERCLOCKWISE);
		translationMoon.setDurationMilliseconds(60000);
		translationMoon.setRepeatMode(Animation.RepeatMode.INFINITE);
		translationMoon.setTransformable3D(moon);
		getCurrentScene().registerAnimation(translationMoon);
		translationMoon.play();
	}

	@Override
	public void onRenderSurfaceSizeChanged(GL10 gl, int width, int height) {
		super.onRenderSurfaceSizeChanged(gl, width, height);
		session.setDisplayGeometry(width, height);
	}

	@Override
	protected void onRender(long ellapsedRealtime, double deltaTime) {
		super.onRender(ellapsedRealtime, deltaTime);
		// Clear screen to notify driver it should not load any pixels from previous frame.
		//GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

		session.setCameraTextureName(cameraTexture.getTextureId());

		try {
			// Obtain the current frame from ARSession. When the configuration is set to
			// UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
			// camera framerate.
			Frame frame = session.update();

			// If display rotation changed (also includes view size change), we need to re-query the uv
			// coordinates for the screen rect, as they may have changed as well.
			if (frame.isDisplayRotationChanged()) {
				if (backgroundQuad == null) {
					backgroundQuad = new ScreenQuad();
				}

				frame.transformDisplayUvCoords(mQuadTexCoord, mQuadTexCoordTransformed);

				float[] dest = new float[8];
				mQuadTexCoordTransformed.get(dest);
				mQuadTexCoordTransformed.position(0);

				backgroundQuad.getGeometry().setTextureCoords(dest, true);
				backgroundQuad.getGeometry().reload();
			}


			// Handle taps. Handling only one tap per frame, as taps are usually low frequency
			// compared to frame rate.
			MotionEvent tap = queuedSingleTaps.poll();
			if (tap != null && frame.getTrackingState() == Frame.TrackingState.TRACKING) {
				for (HitResult hit : frame.hitTest(tap)) {
					// Check if any plane was hit, and if it was hit inside the plane polygon.
					if (hit instanceof PlaneHitResult && ((PlaneHitResult) hit).isHitInPolygon()) {
						// Cap the number of objects created. This avoids overloading both the
						// rendering system and ARCore.
						if (touches.size() >= 1) {
							session.removeAnchors(Collections.singletonList(touches.get(0).getAnchor()));
							touches.remove(0);
						}
						// Adding an Anchor tells ARCore that it should track this position in
						// space. This anchor will be used in PlaneAttachment to place the 3d model
						// in the correct position relative both to the world and to the plane.
						touches.add(
								new PlaneAttachment(
										((PlaneHitResult) hit).getPlane(),
										session.addAnchor(hit.getHitPose())
								)
						);

						// Hits are sorted by depth. Consider only closest hit on a plane.
						break;
					}
				}
			}

			// Draw background.
			//backgroundRenderer.draw(frame);

			// If not tracking, don't draw 3d objects.
			if (frame.getTrackingState() == Frame.TrackingState.NOT_TRACKING) {
				return;
			}

			// Get projection matrix.
			float[] projmtx = new float[16];
			session.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

			// Get camera matrix and draw.
			float[] viewmtx = new float[16];
			frame.getViewMatrix(viewmtx, 0);

			updateCameraPos(frame, projmtx);

			// Compute lighting from average intensity of the image.
			final float lightIntensity = frame.getLightEstimate().getPixelIntensity();
			//light.setPower(lightIntensity);

			// Visualize tracked points.
			//mPointCloud.update(frame.getPointCloud());
			//mPointCloud.draw(frame.getPointCloudPose(), viewmtx, projmtx);

			// Check if we detected at least one plane. If so, hide the loading message.
			if (callback.isLoadingMessageShown()) {
				for (Plane plane : session.getAllPlanes()) {
					if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
							plane.getTrackingState() == Plane.TrackingState.TRACKING) {
						callback.hideLoadingMessage();
						break;
					}
				}
			}

			// Visualize planes.
			//mPlaneRenderer.drawPlanes(session.getAllPlanes(), frame.getPose(), projmtx);

			// Visualize anchors created by touch.
			float scaleFactor = 0.3f;
			for (PlaneAttachment planeAttachment : touches) {
				if (!planeAttachment.isTracking()) {
					continue;
				}

				// Get the current combined pose of an Anchor and Plane in world space. The Anchor
				// and Plane poses are updated during calls to session.update() as ARCore refines
				// its estimate of the world.
				planeAttachment.getPose().toMatrix(mAnchorMatrix, 0);
				Matrix4 poseMatrix = new Matrix4(mAnchorMatrix);

				earth.setPosition(poseMatrix.getTranslation());
				//moon.setPosition(poseMatrix.getTranslation());
			}

		} catch (Throwable t) {
			// Avoid crashing the application due to unhandled exceptions.
			Log.e(TAG, "Exception on the OpenGL thread", t);
		}
	}

	private void updateCameraPos(Frame frame, float[] projmtx) {
		float[] rotation = new float[4];
		frame.getPose().getRotationQuaternion(rotation, 0);
		float[] translation = new float[3];
		frame.getPose().getTranslation(translation, 0);
		Quaternion quaternion = new Quaternion(rotation[3], rotation[0], rotation[1], rotation[2]);
		// Conjugating the Quaternion is needed because Rajawali uses left-handed convention for
		// quaternions.
		getCurrentCamera().setRotation(quaternion.conjugate());
		getCurrentCamera().setPosition(translation[0], translation[1], translation[2]);
		getCurrentCamera().setProjectionMatrix(new Matrix4(projmtx));
	}

	@Override
	public void onOffsetsChanged(float xOffset, float yOffset, float xOffsetStep, float yOffsetStep, int xPixelOffset, int yPixelOffset) {}

	@Override
	public void onTouchEvent(MotionEvent event) {}

	public interface Callback {
		boolean isLoadingMessageShown();

		void hideLoadingMessage();
	}
}