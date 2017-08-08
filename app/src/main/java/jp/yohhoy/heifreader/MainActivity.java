package jp.yohhoy.heifreader;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;

import java.io.IOException;
import java.io.InputStream;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static byte[] loadRawResource(Context ctx, int resId) {
        int size = (int) ctx.getResources().openRawResourceFd(resId).getLength();
        try {
            byte data[] = new byte[size];
            InputStream is = ctx.getResources().openRawResource(resId);
            is.read(data);
            return data;
        } catch (IOException ex) {
            return null;
        }
    }

    private SurfaceView mSurfaceView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mSurfaceView = new SurfaceView(this);
        setContentView(mSurfaceView);

        final byte[] heif = loadRawResource(this, R.raw.lena_std);

        mSurfaceView.getHolder().addCallback(new SurfaceHolder.Callback() {
            @Override
            public void surfaceCreated(SurfaceHolder holder) {}

            @Override
            public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
                Log.d(TAG, "surfaceChanged format=" + format + " size=" + width + "x" + height);
                Size sz;
                try {
                    sz = HeifReader.loadHeif(heif, holder.getSurface());
                    if (sz != null) {
                        // fit SurfaceView to decoded image
                        ViewGroup.LayoutParams lp = mSurfaceView.getLayoutParams();
                        lp.width = sz.getWidth();
                        lp.height = sz.getHeight();
                        mSurfaceView.setLayoutParams(lp);
                    }
                } catch (IOException ex) {
                    Log.e(TAG, "HeifReader#loadHeif", ex);
                }
            }

            @Override
            public void surfaceDestroyed(SurfaceHolder holder) {}
        });
    }
}
