package jp.yohhoy.heifreader;

import android.app.Activity;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.ColorDrawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.RelativeLayout;

import java.io.IOException;
import java.net.URL;

public class MainActivity extends Activity {
    private static final String TAG = "MainActivity";

    private static final String URL_BASE = "https://github.com/nokiatech/heif/raw/gh-pages/content/images/";
    private static final String URL_IMAGES[] = {
            "lena [local]",  // internal resource
            "autumn_1440x960.heic",
            "cheers_1440x960.heic",
            "crowd_1440x960.heic",
            "old_bridge_1440x960.heic",
            "random_collection_1440x960.heic",
            "season_collection_1440x960.heic",
            "ski_jump_1440x960.heic",
            "spring_1440x960.heic",
            "summer_1440x960.heic",
            "surfer_1440x960.heic",
            "winter_1440x960.heic"
    };

    ListView mListView;
    ArrayAdapter<String> mArrayAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        mListView = findViewById(R.id.file_list);
        mArrayAdapter = new ArrayAdapter<>(this, R.layout.listrow, URL_IMAGES);
        mListView.setAdapter(mArrayAdapter);
        mListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                final Context context = MainActivity.this;
                if (position == 0) {
                    // show internal resource
                    Bitmap bitmap = HeifReader.decodeResource(context.getResources(), R.raw.lena_std);
                    showImage(bitmap);
                    return;
                }

                String imageName = URL_IMAGES[position];
                final ProgressDialog progress = ProgressDialog.show(context, imageName, "", true, false);
                new AsyncTask<String, Void, Bitmap>() {
                    @Override
                    protected Bitmap doInBackground(String... strings) {
                        try {
                            return HeifReader.decodeStream(new URL(strings[0]).openStream());
                        } catch (IOException ex) {
                            Log.e(TAG, "invalid URL", ex);
                            return null;
                        }
                    }
                    @Override
                    protected void onPostExecute(Bitmap result) {
                        progress.dismiss();
                        showImage(result);
                    }
                }.execute(URL_BASE + imageName);
            }
        });

        // initialize HefiReader module
        HeifReader.initialize(this);
    }

    private void showImage(Bitmap bitmap) {
        Dialog builder = new Dialog(this);
        builder.requestWindowFeature(Window.FEATURE_NO_TITLE);
        builder.getWindow().setBackgroundDrawable(new ColorDrawable(android.graphics.Color.TRANSPARENT));

        ImageView imageView = new ImageView(this);
        if (bitmap != null) {
            imageView.setImageBitmap(bitmap);
        } else {
            // fallback image
            imageView.setImageResource(android.R.drawable.ic_delete);
        }
        builder.addContentView(imageView, new RelativeLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        builder.show();
    }
}
