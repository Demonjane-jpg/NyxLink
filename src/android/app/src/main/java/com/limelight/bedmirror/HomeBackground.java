package com.limelight.bedmirror;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.graphics.ColorFilter;
import android.graphics.PixelFormat;
import android.graphics.drawable.Drawable;

/** The background scales with the screen and stays behind the scrolling content. */
final class HomeBackground extends Drawable {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);

    @Override public void draw(Canvas canvas) {
        float w = getBounds().width(), h = getBounds().height();
        canvas.drawColor(Color.rgb(2, 5, 25));
        glow(canvas, w * .89f, h * .04f, w * .72f, 0x703000A0);
        glow(canvas, w * .03f, h * .88f, w * .65f, 0x600070A0);
        paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(1.5f);
        paint.setColor(0xAA803FFF);
        Path top = new Path();
        top.moveTo(w * .6f, 0);
        top.cubicTo(w * .82f, h * .04f, w * .95f, h * .10f, w, h * .18f);
        canvas.drawPath(top, paint);
        paint.setColor(0xAA00E5FF);
        Path bottom = new Path();
        bottom.moveTo(0, h * .77f);
        bottom.cubicTo(w * .12f, h * .89f, w * .29f, h * .97f, w * .48f, h);
        canvas.drawPath(bottom, paint);
        paint.setStyle(Paint.Style.FILL);
    }

    private void glow(Canvas canvas, float x, float y, float radius, int color) {
        paint.setShader(new RadialGradient(x, y, radius, color, Color.TRANSPARENT, Shader.TileMode.CLAMP));
        canvas.drawCircle(x, y, radius, paint);
    }
    @Override public void setAlpha(int alpha) {}
    @Override public void setColorFilter(ColorFilter filter) {}
    @Override public int getOpacity() { return PixelFormat.OPAQUE; }
}
