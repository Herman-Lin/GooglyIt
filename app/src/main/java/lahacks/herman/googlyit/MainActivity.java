package lahacks.herman.googlyit;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.view.Display;
import android.view.WindowManager;
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

//import android.graphics.drawable.Drawable;
//import android.support.v4.content.res.ResourcesCompat;


public class MainActivity extends AppCompatActivity implements SensorEventListener{
    private FaceServiceClient faceServiceClient =
            new FaceServiceRestClient("a0e0302f1c514556bc1e88917e133ac2");

    private SensorManager sensorManager;
    private Sensor accelerometer;
    private long lastUpdate;

    AnimatedView animatedView = null;
    Bitmap bitmap;

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {

        int width = bm.getWidth();
        int height = bm.getHeight();
        Matrix matrix = new Matrix();
        matrix.postScale(((float) newWidth) / width, ((float) newHeight) / height);
        Bitmap resizedBitmap = Bitmap.createBitmap(bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    private final int PICK_IMAGE = 1;
    private ProgressDialog detectionProgressDialog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager
                .getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        lastUpdate = System.currentTimeMillis();

        animatedView = new AnimatedView(this);

        setContentView(R.layout.activity_main);
        Button button1 = (Button) findViewById(R.id.button1);
        button1.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Global.all != null) Arrays.fill(Global.all, null);
                Global.firstCall = true;
                Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
                gallIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE);
            }
        });

        detectionProgressDialog = new ProgressDialog(this);
    }
    @Override
    protected void onResume() {
        super.onResume();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onPause() {
        super.onPause();
        sensorManager.unregisterListener(this);
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        //// Inflate the menu; this adds items to the action bar if it is present.
       // getMenuInflater().inflate(R.menu.activity_main, menu);
        return true;
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    public void onSensorChanged(SensorEvent event) {
        if (Global.all != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
           if (event.values[0] > 0.123) { // to right
               Global.time = 1;
               if (Global.r > 0){
                   Global.rv -= event.values[0] *800;
               }
               else Global.rv += event.values[0] *800;
              //if (Global.d == 1 && (Global.r > 90 || Global.r < -90))
               //    Global.rv = event.values[0] * 1000;
               //else if (Global.r < 90 && Global.r > -90)
                //  Global.rv = event.values[0] * -1000;
           }
           else if (event.values[0] < -0.123) { // to left
               Global.time = 1;
               if (Global.r > 0){
                   Global.rv += event.values[0] *1000;
               }
               else Global.rv -= event.values[0] *1000;

               //if (Global.d == -1 && (Global.r > 90 || Global.r < -90))
                 //  Global.rv = event.values[0] * 1000;
               //else if(Global.r < 90 && Global.r > -90)
                 //  Global.rv = event.values[0] * -1000;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                Bitmap temp_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);

                detectAndFrame(temp_bitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void detectAndFrame(final Bitmap imageBitmap) {
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());
        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            publishProgress("Detecting...");
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    true,        // returnFaceLandmarks
                                    null           // returnFaceAttributes: a string like "age, gender"
                            );
                            if (result == null) {
                                publishProgress("Detection Finished. No face detected");
                                return null;
                            }
                            publishProgress(
                                    String.format("Detection Finished. %d face(s) detected",
                                            result.length));
                            return result;
                        } catch (Exception e) {
                            publishProgress("Detection failed");
                            return null;
                        }
                    }

                    @Override
                    protected void onPreExecute() {
                        detectionProgressDialog.show();
                    }

                    @Override
                    protected void onProgressUpdate(String... progress) {
                        detectionProgressDialog.setMessage(progress[0]);
                    }

                    @Override
                    protected void onPostExecute(Face[] result) {
                        detectionProgressDialog.dismiss();
                        if (result == null) return;
                        Global.all = result;
                        setContentView(animatedView);
                        //ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                        //imageView.setImageBitmap(drawGooglyEyeFrames(imageBitmap, result));
                        //imageBitmap.recycle();
                        //
                        //Button button1 = (Button) findViewById(R.id.button1);
                    }
                };
        detectTask.execute(inputStream);
    }

    private Bitmap drawGooglyEyeFrames(Bitmap originalBitmap, Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        int stokeWidth = 2;
        paint.setStrokeWidth(stokeWidth);
        if (faces != null) {
            Bitmap eye = BitmapFactory.decodeResource(getResources(),R.drawable.eye);
            Bitmap m_eye = eye.copy(Bitmap.Config.ARGB_8888, true);
            for (Face face : faces) {
                FaceLandmarks faceLandmarks = face.faceLandmarks;
                m_eye = getResizedBitmap(m_eye,(int)(faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15),(int)(faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15));
                canvas.drawBitmap(m_eye,(float)faceLandmarks.eyeLeftOuter.x - 10,(float)faceLandmarks.eyeLeftTop.y - 10,paint);
                m_eye = getResizedBitmap(m_eye,(int)(faceLandmarks.eyeRightOuter.x - faceLandmarks.eyeRightInner.x + 15),(int)(faceLandmarks.eyeRightOuter.x - faceLandmarks.eyeRightInner.x + 15));
                canvas.drawBitmap(m_eye,(float)faceLandmarks.eyeRightInner.x - 5,(float)faceLandmarks.eyeRightTop.y - 10,paint);
            }
        }
        return bitmap;
    }

    public class AnimatedView extends ImageView {

        Paint paint = new Paint();
        Bitmap pupil = BitmapFactory.decodeResource(getResources(),R.drawable.pupil);
        Bitmap eye = BitmapFactory.decodeResource(getResources(),R.drawable.eye);
        Bitmap m_eye = eye.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap m_pupil = pupil.copy(Bitmap.Config.ARGB_8888, true);
        Bitmap m_bitmap;
        FaceLandmarks faceLandmarks;
        double radius;
        double w;
        double h;
        double a;
        double b;
        double scale = 1;
        public AnimatedView(Context context) {
            super(context);
            setWillNotDraw(false);
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            w = size.x;
            h = size.y;
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            if (Global.all != null) {
                if (Global.r > 0)
                    Global.rv = Global.rv - 120 * Math.abs(Global.r)/45;
                else if (Global.r < 0)
                    Global.rv = Global.rv + 120 * Math.abs(Global.r)/45;
                Global.time++;
                if (Global.rv > -15 && Global.rv < 15 && Global.r < 10 && Global.r > -10) {
                   Global.rv = 0;
                    Global.r = 0;
                }
                if (Global.r > 180) Global.r = -180;
                else if (Global.r < -180) Global.r = 180;
                Global.r = Math.pow(2.71828, -0.000125 * Global.time) * (Global.r + Global.rv / 60);
                canvas.drawBitmap(bitmap,0,0,paint);
                if (Global.firstCall) {
                    m_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                }
                for (Face face : Global.all) {
                    faceLandmarks = face.faceLandmarks;
                    radius = scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)/4;

                    if (Global.firstCall) {
                        a = m_bitmap.getWidth();
                        b = m_bitmap.getHeight();

                        if (a / b >= 1){
                            scale = (w - 100) / m_bitmap.getWidth();
                            if (m_bitmap.getHeight() * scale > h - 300) {
                                scale = (h - 300) / m_bitmap.getHeight();
                            }
                        }
                        else {
                            scale = (h - 300) / m_bitmap.getHeight();
                            if (m_bitmap.getWidth() * scale > w) {
                                scale = (w - 100) / m_bitmap.getWidth();
                            }
                        }

                        m_bitmap = getResizedBitmap(m_bitmap, (int)(scale * m_bitmap.getWidth()), (int)(scale * m_bitmap.getHeight()));
                        m_pupil = getResizedBitmap(m_pupil,(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)),(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)));
                        m_eye = getResizedBitmap(m_eye,(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)),(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)));
                        Global.firstCall = false;
                    }

                    //canvas.drawBitmap(m_bitmap,(float)(h / 2 - scale * m_bitmap.getHeight() / 2),(float)(w / 2 - scale * m_bitmap.getWidth() / 2),paint);
                    canvas.drawBitmap(m_bitmap,0,0,paint);

                    canvas.drawBitmap(m_eye,(float)(faceLandmarks.eyeLeftOuter.x - 10),(float)(faceLandmarks.eyeLeftTop.y - 10),paint);
                    if (Global.r >= 0) canvas.drawBitmap(m_pupil,(float)(faceLandmarks.eyeLeftOuter.x - 10 - radius * (Math.sin(Global.r * 3.14159265/ 180))),(float)(faceLandmarks.eyeLeftTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180))),paint);
                    else canvas.drawBitmap(m_pupil,(float)(faceLandmarks.eyeLeftOuter.x - 10 + radius * (Math.sin(-Global.r * 3.14159265/ 180))),(float)(faceLandmarks.eyeLeftTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180))),paint);

                    canvas.drawBitmap(m_eye,(float)(faceLandmarks.eyeRightInner.x - 5),(float)(faceLandmarks.eyeRightTop.y - 10),paint);
                    if (Global.r >= 0) canvas.drawBitmap(m_pupil,(float)(faceLandmarks.eyeRightInner.x - 5 - radius * (Math.sin(Global.r * 3.14159265/ 180))),(float)(faceLandmarks.eyeRightTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180))),paint);
                    else canvas.drawBitmap(m_pupil,(float)(faceLandmarks.eyeRightInner.x - 5 + radius * (Math.sin(-Global.r * 3.14159265/ 180))),(float)(faceLandmarks.eyeRightTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180))),paint);

                }
                invalidate();
            }
        }
    }
}
