/*===============================================================================
Copyright (c) 2016 PTC Inc. All Rights Reserved.

Copyright (c) 2012-2014 Qualcomm Connected Experiences, Inc. All Rights Reserved.

Vuforia is a trademark of PTC Inc., registered in the United States and other 
countries.
===============================================================================*/

package com.ray.ar;

import android.app.Activity;
import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.util.Log;

import com.vuforia.Renderer;
import com.vuforia.State;
import com.vuforia.Trackable;
import com.vuforia.TrackableResult;
import com.vuforia.VIDEO_BACKGROUND_REFLECTION;
import com.vuforia.Vuforia;

import java.io.IOException;
import java.util.Vector;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import SampleApplication.SampleApplicationSession;
import SampleApplication.utils.CubeShaders;
import SampleApplication.utils.LoadingDialogHandler;
import SampleApplication.utils.SampleApplication3DModel;
import SampleApplication.utils.SampleUtils;
import SampleApplication.utils.Teapot;
import SampleApplication.utils.Texture;


// The renderer class for the ImageTargets sample. 
public class BunnyTargetRenderer implements GLSurfaceView.Renderer {
    private static final String LOGTAG = "BunnyTargetRenderer";

    private SampleApplicationSession vuforiaAppSession;
    private BunnyTargets mActivity;

    private Vector<Texture> mTextures;

    private int shaderProgramID;

    private int vertexHandle;

    private int normalHandle;

    private int textureCoordHandle;

    private int mvpMatrixHandle;

    private int texSampler2DHandle;

    private Teapot mTeapot;

    private float kBuildingScale = 12.0f;
    private SampleApplication3DModel mBuildingsModel;

    private Renderer mRenderer;

    boolean mIsActive = false;

    private static final float OBJECT_SCALE_FLOAT = 3.0f;

    private RendererListener mRendererListener;
    private boolean isRendereSuccess = false;

    public BunnyTargetRenderer(Activity activity,
                               SampleApplicationSession session,
                               RendererListener rendererListener) {
        mActivity = (BunnyTargets) activity;
        vuforiaAppSession = session;
        mRendererListener = rendererListener;
    }


    // Called to draw the current frame.
    @Override
    public void onDrawFrame(GL10 gl) {
        if (!mIsActive) {
            return;
        }

        // Call our function to render content
        renderFrame();
    }


    // Called when the surface is created or recreated.
    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceCreated");

        initRendering();

        // Call Vuforia function to (re)initialize rendering after first use
        // or after OpenGL ES context was lost (e.g. after onPause/onResume):
        vuforiaAppSession.onSurfaceCreated();
    }


    // Called when the surface changed size.
    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        Log.d(LOGTAG, "GLRenderer.onSurfaceChanged");

        // Call Vuforia function to handle render surface size changes:
        vuforiaAppSession.onSurfaceChanged(width, height);
    }


    // Function for initializing the renderer.
    private void initRendering() {
        mTeapot = new Teapot();

        mRenderer = Renderer.getInstance();

        GLES20.glClearColor(0.0f, 0.0f, 0.0f, Vuforia.requiresAlpha() ? 0.0f
                : 1.0f);

        for (Texture t : mTextures) {
            GLES20.glGenTextures(1, t.mTextureID, 0);
            GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, t.mTextureID[0]);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameterf(GLES20.GL_TEXTURE_2D,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexImage2D(GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                    t.mWidth, t.mHeight, 0, GLES20.GL_RGBA,
                    GLES20.GL_UNSIGNED_BYTE, t.mData);
        }

        shaderProgramID = SampleUtils.createProgramFromShaderSrc(
                CubeShaders.CUBE_MESH_VERTEX_SHADER,
                CubeShaders.CUBE_MESH_FRAGMENT_SHADER);

        vertexHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexPosition");
        normalHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexNormal");
        textureCoordHandle = GLES20.glGetAttribLocation(shaderProgramID,
                "vertexTexCoord");
        mvpMatrixHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "modelViewProjectionMatrix");
        texSampler2DHandle = GLES20.glGetUniformLocation(shaderProgramID,
                "texSampler2D");

        try {
            mBuildingsModel = new SampleApplication3DModel();
            mBuildingsModel.loadModel(mActivity.getResources().getAssets(),
                    "ImageTargets/Buildings.txt");
        } catch (IOException e) {
            Log.e(LOGTAG, "Unable to load buildings");
        }

        // Hide the Loading Dialog
        mActivity.loadingDialogHandler
                .sendEmptyMessage(LoadingDialogHandler.HIDE_LOADING_DIALOG);

    }


    // The render function.
    private void renderFrame() {


        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        State state = mRenderer.begin();
        mRenderer.drawVideoBackground();

        GLES20.glEnable(GLES20.GL_DEPTH_TEST);

        // Set the viewport
        int[] viewport = vuforiaAppSession.getViewport();
        GLES20.glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);

        // handle face culling, we need to detect if we are using reflection
        // to determine the direction of the culling
        GLES20.glEnable(GLES20.GL_CULL_FACE);
        GLES20.glCullFace(GLES20.GL_BACK);
        if (Renderer.getInstance().getVideoBackgroundConfig().getReflection() ==
                VIDEO_BACKGROUND_REFLECTION.VIDEO_BACKGROUND_REFLECTION_ON)
            GLES20.glFrontFace(GLES20.GL_CW); // Front camera
        else
            GLES20.glFrontFace(GLES20.GL_CCW); // Back camera

        if (state.getNumTrackableResults() > 0) {
            TrackableResult result = state.getTrackableResult(0);
            Trackable trackable = result.getTrackable();
            printUserData(trackable);
        } else {
            isRendereSuccess = false;
            mRendererListener.rendererFinish();
        }

        mRenderer.end();
    }


    private void printUserData(Trackable trackable) {
        if (!isRendereSuccess) {
            mRendererListener.rendererSuccess(trackable.getName());
            isRendereSuccess = true;
            String userData = (String) trackable.getUserData();
            Log.d(LOGTAG, "UserData:Retreived User Data	\"" + userData + "\"");
        }

    }


    public void setTextures(Vector<Texture> textures) {
        mTextures = textures;

    }

}