package jp.yohhoy.heifreader;

import android.app.Activity;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Bundle;
import android.widget.ImageView;

import java.io.IOException;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    ImageView mImageView;
    AsyncTask<String, Void, Void> mTask;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mImageView = new ImageView(this);
        setContentView(mImageView);

        String url = "";
        //url = "https://github.com/nokiatech/heif/raw/gh-pages/content/images/autumn_1440x960.heic";
        //url = "https://github.com/nokiatech/heif/raw/gh-pages/content/images/cheers_1440x960.heic";
        //url = "https://github.com/nokiatech/heif/raw/gh-pages/content/images/crowd_1440x960.heic";
        url = "https://github.com/nokiatech/heif/raw/gh-pages/content/images/old_bridge_1440x960.heic";

        // initialize HefiReader module
        HeifReader.initialize(this);

        mTask = new AsyncTask<String, Void, Void>() {
            Bitmap mBitmap;

            @Override
            protected Void doInBackground(String... strings) {
                try {
                    mBitmap = HeifReader.decodeStream(new URL(strings[0]).openStream());
                } catch (IOException ex) {
                    // fallback to internal resource
                    mBitmap = HeifReader.decodeResource(MainActivity.this.getResources(), R.raw.lena_std);
                }
                return null;
            }

            @Override
            protected void onPostExecute(Void result) {
                mImageView.setImageBitmap(mBitmap);
            }
        };
        mTask.execute(url);
    }
}
