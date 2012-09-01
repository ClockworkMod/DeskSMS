package com.koushikdutta.tabletsms;

import android.content.res.Resources;
import android.graphics.Canvas;
import android.graphics.Shader.TileMode;
import android.graphics.drawable.BitmapDrawable;
import android.view.Gravity;

public class TileableBackgroundDrawable extends BitmapDrawable {
    public TileableBackgroundDrawable(Resources res, int resId) {
        super(res, ((BitmapDrawable) res.getDrawable(resId)).getBitmap());
//        tile.setGravity(Gravity.FILL);
        setTileModeXY(TileMode.REPEAT, TileMode.REPEAT);
     }

    @Override
    public int getMinimumHeight() {
        return 0;
    }

//    @Override
//    public int getMinimumWidth() {
//        return 0;
//    }
    
    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
        canvas.drawBitmap(getBitmap(), getBounds(), canvas.getClipBounds(), getPaint());
    }
}
