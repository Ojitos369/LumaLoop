package com.ojitos369.lumaloop.utilities;

import android.content.Context;
import android.graphics.Bitmap;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.opengl.GLUtils;
import android.util.Log;
import android.view.SurfaceHolder;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;

import javax.microedition.khronos.egl.EGL10;
import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.egl.EGLContext;
import javax.microedition.khronos.egl.EGLDisplay;
import javax.microedition.khronos.egl.EGLSurface;

public class GLWallpaperRenderer {
    private static final String TAG = "GLWallpaperRenderer";

    private static final int EGL_CONTEXT_CLIENT_VERSION = 0x3098;
    private static final int EGL_OPENGL_ES2_BIT = 4;

    private EGL10 egl;
    private EGLDisplay eglDisplay;
    private EGLContext eglContext;
    private EGLSurface eglSurface;
    private EGLConfig eglConfig;

    private int programImage;
    private int programVideo;
    private int textureIdImage = -1;
    private int textureIdImageNext = -1;
    private int textureIdVideo = -1;

    private FloatBuffer vertexBuffer;
    private FloatBuffer textureBuffer;

    private static final float[] VERTICES = {
            -1.0f, -1.0f,
            1.0f, -1.0f,
            -1.0f, 1.0f,
            1.0f, 1.0f
    };

    private static final float[] TEX_COORDS = {
            0.0f, 1.0f,
            1.0f, 1.0f,
            0.0f, 0.0f,
            1.0f, 0.0f
    };

    private static final String VERTEX_SHADER = "attribute vec4 aPosition;\n" +
            "attribute vec2 aTexCoord;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_Position = aPosition;\n" +
            "  vTexCoord = aTexCoord;\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_IMAGE = "precision mediump float;\n" +
            "uniform sampler2D uTexture;\n" +
            "uniform sampler2D uTextureNext;\n" +
            "uniform float uMix;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  vec4 color1 = texture2D(uTexture, vTexCoord);\n" +
            "  vec4 color2 = texture2D(uTextureNext, vTexCoord);\n" +
            "  gl_FragColor = mix(color1, color2, uMix);\n" +
            "}\n";

    private static final String FRAGMENT_SHADER_VIDEO = "#extension GL_OES_EGL_image_external : require\n" +
            "precision mediump float;\n" +
            "uniform samplerExternalOES uTexture;\n" +
            "varying vec2 vTexCoord;\n" +
            "void main() {\n" +
            "  gl_FragColor = texture2D(uTexture, vTexCoord);\n" +
            "}\n";

    public GLWallpaperRenderer(Context context) {
        vertexBuffer = ByteBuffer.allocateDirect(VERTICES.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        vertexBuffer.put(VERTICES).position(0);

        textureBuffer = ByteBuffer.allocateDirect(TEX_COORDS.length * 4)
                .order(ByteOrder.nativeOrder()).asFloatBuffer();
        textureBuffer.put(TEX_COORDS).position(0);
    }

    public void setSurface(SurfaceHolder holder) {
        if (eglSurface != null) {
            egl.eglDestroySurface(eglDisplay, eglSurface);
        }

        if (eglContext == null) {
            initEGL();
        }

        eglSurface = egl.eglCreateWindowSurface(eglDisplay, eglConfig, holder, null);
        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            throw new RuntimeException("eglCreateWindowSurface failed");
        }

        if (!egl.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new RuntimeException("eglMakeCurrent failed");
        }

        initGL();
    }

    private void initEGL() {
        egl = (EGL10) EGLContext.getEGL();
        eglDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY);

        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw new RuntimeException("eglGetDisplay failed");
        }

        int[] version = new int[2];
        if (!egl.eglInitialize(eglDisplay, version)) {
            throw new RuntimeException("eglInitialize failed");
        }

        int[] configSpec = {
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
                EGL10.EGL_NONE
        };

        EGLConfig[] configs = new EGLConfig[1];
        int[] numConfig = new int[1];
        if (!egl.eglChooseConfig(eglDisplay, configSpec, configs, 1, numConfig)) {
            throw new IllegalArgumentException("eglChooseConfig failed");
        }
        eglConfig = configs[0];

        int[] attrib_list = { EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE };
        eglContext = egl.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrib_list);
    }

    private void initGL() {
        programImage = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_IMAGE);
        programVideo = createProgram(VERTEX_SHADER, FRAGMENT_SHADER_VIDEO);

        int[] textures = new int[3];
        GLES20.glGenTextures(3, textures, 0);
        textureIdImage = textures[0];
        textureIdImageNext = textures[1];
        textureIdVideo = textures[2];

        // Setup Image Textures (2D)
        setupTexture2D(textureIdImage);
        setupTexture2D(textureIdImageNext);

        // Setup Video Texture (OES)
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIdVideo);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private void setupTexture2D(int textureId) {
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureId);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    public int getVideoTextureId() {
        return textureIdVideo;
    }

    public void uploadImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled())
            return;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdImage);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
    }

    public void uploadNextImage(Bitmap bitmap) {
        if (bitmap == null || bitmap.isRecycled())
            return;
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdImageNext);
        GLUtils.texImage2D(GLES20.GL_TEXTURE_2D, 0, bitmap, 0);
    }
    
    public void swapTextures() {
        int temp = textureIdImage;
        textureIdImage = textureIdImageNext;
        textureIdImageNext = temp;
    }

    public void drawVideo() {
        if (eglSurface == null)
            return;

        GLES20.glUseProgram(programVideo);

        int positionHandle = GLES20.glGetAttribLocation(programVideo, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(programVideo, "aTexCoord");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, textureIdVideo);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programVideo, "uTexture"), 0);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);

        egl.eglSwapBuffers(eglDisplay, eglSurface);
    }

    public void drawImage() {
        drawImageInternal(0.0f);
    }

    public void drawImageTransition(float mix) {
        drawImageInternal(mix);
    }

    private void drawImageInternal(float mix) {
        if (eglSurface == null)
            return;

        GLES20.glUseProgram(programImage);

        int positionHandle = GLES20.glGetAttribLocation(programImage, "aPosition");
        GLES20.glEnableVertexAttribArray(positionHandle);
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer);

        int texCoordHandle = GLES20.glGetAttribLocation(programImage, "aTexCoord");
        GLES20.glEnableVertexAttribArray(texCoordHandle);
        GLES20.glVertexAttribPointer(texCoordHandle, 2, GLES20.GL_FLOAT, false, 0, textureBuffer);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdImage);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programImage, "uTexture"), 0);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE1);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, textureIdImageNext);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(programImage, "uTextureNext"), 1);

        GLES20.glUniform1f(GLES20.glGetUniformLocation(programImage, "uMix"), mix);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);

        GLES20.glDisableVertexAttribArray(positionHandle);
        GLES20.glDisableVertexAttribArray(texCoordHandle);

        egl.eglSwapBuffers(eglDisplay, eglSurface);
    }

    public void release() {
        if (eglSurface != null) {
            egl.eglDestroySurface(eglDisplay, eglSurface);
            eglSurface = null;
        }
        if (eglContext != null) {
            egl.eglDestroyContext(eglDisplay, eglContext);
            eglContext = null;
        }
        if (eglDisplay != null) {
            egl.eglTerminate(eglDisplay);
            eglDisplay = null;
        }
    }

    private int createProgram(String vertexSource, String fragmentSource) {
        int vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertexShader);
        GLES20.glAttachShader(program, fragmentShader);
        GLES20.glLinkProgram(program);
        return program;
    }

    private int loadShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        return shader;
    }
}
