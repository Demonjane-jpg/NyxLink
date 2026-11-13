package com.limelight.bedmirror;

public final class ViewportMapper {
    /** View x/y include translation; scale pivots at the view center, matching Android defaults. */
    public static float[] map(float x,float y,float left,float top,float width,float height,float zoom){
        if(width<=0||height<=0||zoom<1)return null;
        float localX=(x-left-width*(1-zoom)/2)/zoom;
        float localY=(y-top-height*(1-zoom)/2)/zoom;
        if(localX<0||localY<0||localX>=width||localY>=height)return null;
        return new float[]{localX/width,localY/height};
    }

    /** The encoded frame can contain letterboxing after a live switch to a different aspect ratio. */
    public static boolean insideCapture(float x,float y,int frameWidth,int frameHeight,int captureWidth,int captureHeight){
        if(frameWidth<=0||frameHeight<=0||captureWidth<=0||captureHeight<=0)return false;
        float fit=Math.min((float)frameWidth/captureWidth,(float)frameHeight/captureHeight);
        float insetX=(1-fit*captureWidth/frameWidth)/2;
        float insetY=(1-fit*captureHeight/frameHeight)/2;
        return x>=insetX&&x<1-insetX&&y>=insetY&&y<1-insetY;
    }
}
