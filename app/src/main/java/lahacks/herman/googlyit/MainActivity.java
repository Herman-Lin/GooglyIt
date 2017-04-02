package lahacks.herman.googlyit;

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
import android.os.Bundle;
import android.provider.MediaStore;
import android.support.v7.app.AppCompatActivity;
import android.view.Display;
import android.view.Menu;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.graphics.*;

import com.microsoft.projectoxford.face.FaceServiceClient;
import com.microsoft.projectoxford.face.FaceServiceRestClient;
import com.microsoft.projectoxford.face.contract.Face;
import com.microsoft.projectoxford.face.contract.FaceLandmarks;
import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Vector;
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

    private final int PICK_IMAGE_FOR_GOOGLY = 1;
    private final int PICK_IMAGE_FOR_SWAP = 2;

    private ProgressDialog detectionProgressDialog;

    private static Vector<FaceCoordinates> faceCoordinatesVector = new Vector<FaceCoordinates>();

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
                Global.mode = 1;
                Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
                gallIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE_FOR_GOOGLY);
            }
        });
        Button button2 = (Button) findViewById(R.id.button2);
        button2.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (Global.all != null) Arrays.fill(Global.all, null);
                Global.firstCall = true;
                Global.mode = 2;
                Intent gallIntent = new Intent(Intent.ACTION_GET_CONTENT);
                gallIntent.setType("image/*");
                startActivityForResult(Intent.createChooser(gallIntent, "Select Picture"), PICK_IMAGE_FOR_SWAP);
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
        return true;
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    public void onSensorChanged(SensorEvent event) {
        if (Global.all != null && event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
           if (event.values[0] > 0.132) { // to right
               Global.time = 1;
               if (Global.r > 0){
                   Global.rv -= event.values[0] * 200;
               }
               else Global.rv += event.values[0] * 200;
           }
           else if (event.values[0] < -0.132) { // to left
               Global.time = 1;
               if (Global.r > 0){
                   Global.rv += event.values[0] * 700;
               }
               else Global.rv -= event.values[0] * 700;
            }
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_IMAGE_FOR_GOOGLY && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                Bitmap temp_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);

                detectAndFrameForGoogly(temp_bitmap);

            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        if (requestCode == PICK_IMAGE_FOR_SWAP && resultCode == RESULT_OK && data != null && data.getData() != null) {
            Uri uri = data.getData();
            try {
                Bitmap bitmap = MediaStore.Images.Media.getBitmap(getContentResolver(), uri);
                ImageView imageView = (ImageView) findViewById(R.id.imageView1);
                imageView.setImageBitmap(bitmap);
//added
                detectAndFrameForSwap(bitmap);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void detectAndFrameForGoogly(final Bitmap imageBitmap) {
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
                if (Global.r >= 180) Global.r = -180;
                else if (Global.r < -180) Global.r = 180;
                Global.r = Math.pow(2.71828, -0.005 * Global.time) * (Global.r + Global.rv / 60);

                if (Global.firstCall) {
                    m_bitmap = bitmap.copy(Bitmap.Config.ARGB_8888, true);
                    a = m_bitmap.getWidth();
                    b = m_bitmap.getHeight();

                    if (a / b >= 1){
                        scale = (w - 1) / m_bitmap.getWidth();
                        if (m_bitmap.getHeight() * scale > h - 300) {
                            scale = (h - 300) / m_bitmap.getHeight();
                        }
                    }
                    else {
                        scale = (h - 300) / m_bitmap.getHeight();
                        if (m_bitmap.getWidth() * scale > w) {
                            scale = (w - 1) / m_bitmap.getWidth();
                        }
                    }
                }

                canvas.drawBitmap(m_bitmap,0, 0,paint);

                for (Face face : Global.all) {
                    faceLandmarks = face.faceLandmarks;
                    radius = (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)/4;

                    if (Global.firstCall) {

                        m_bitmap = getResizedBitmap(m_bitmap, (int)(scale * m_bitmap.getWidth()), (int)(scale * m_bitmap.getHeight()));
                        m_pupil = getResizedBitmap(m_pupil,(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)),(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)));
                        m_eye = getResizedBitmap(m_eye,(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)),(int)(scale * (faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15)));
                        Global.firstCall = false;
                    }

                    canvas.drawBitmap(m_eye,(float)(scale * (faceLandmarks.eyeLeftOuter.x - 10)),(float)(scale * (faceLandmarks.eyeLeftTop.y - 10)),paint);
                    if (Global.r >= 0) canvas.drawBitmap(m_pupil,(float)(scale *(faceLandmarks.eyeLeftOuter.x - 10 - radius * (Math.sin(Global.r * 3.14159265/ 180)))),(float)(scale *(faceLandmarks.eyeLeftTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180)))),paint);
                    else canvas.drawBitmap(m_pupil,(float)(scale *(faceLandmarks.eyeLeftOuter.x - 10 + radius * (Math.sin(-Global.r * 3.14159265/ 180)))),(float)(scale *(faceLandmarks.eyeLeftTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180)))),paint);

                    canvas.drawBitmap(m_eye,(float)(scale * (faceLandmarks.eyeRightInner.x - 5)),(float)(scale * (faceLandmarks.eyeRightTop.y - 10)),paint);
                    if (Global.r >= 0) canvas.drawBitmap(m_pupil,(float)(scale *(faceLandmarks.eyeRightInner.x - 5 - radius * (Math.sin(Global.r * 3.14159265/ 180)))),(float)(scale *(faceLandmarks.eyeRightTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180)))),paint);
                    else canvas.drawBitmap(m_pupil,(float)(scale *(faceLandmarks.eyeRightInner.x - 5 + radius * (Math.sin(-Global.r * 3.14159265/ 180)))),(float)(scale *(faceLandmarks.eyeRightTop.y - 10 - radius * (1-Math.cos(Global.r*3.14159265/180)))),paint);

                }
                invalidate();
            }
        }
    }

    private void detectAndFrameForSwap(final Bitmap imageBitmap)
    {
        faceCoordinatesVector.clear();
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        imageBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream);
        ByteArrayInputStream inputStream =
                new ByteArrayInputStream(outputStream.toByteArray());
        AsyncTask<InputStream, String, Face[]> detectTask =
                new AsyncTask<InputStream, String, Face[]>() {
                    @Override
                    protected Face[] doInBackground(InputStream... params) {
                        try {
                            java.lang.String random = "age";
                            publishProgress("Detecting...");
                            FaceServiceClient.FaceAttributeType[] attributess = {FaceServiceClient.FaceAttributeType.Age, FaceServiceClient.FaceAttributeType.Gender};
                            Face[] result = faceServiceClient.detect(
                                    params[0],
                                    true,         // returnFaceId
                                    true,        // returnFaceLandmarks
                                    attributess      // returnFaceAttributes: a string like "age, gender"
                            );
                            if (result == null)
                            {
                                publishProgress("Detection Finished. Nothing detected");
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
                        ImageView imageView = (ImageView)findViewById(R.id.imageView1);
                        imageView.setImageBitmap(drawFaceRectanglesOnBitmap(imageBitmap, result));
                        imageBitmap.recycle();
                    }
                };
        detectTask.execute(inputStream);
    }

    private static Bitmap drawFaceRectanglesOnBitmap(Bitmap originalBitmap, Face[] faces) {
        Bitmap bitmap = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);

        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint();
        paint.setAntiAlias(true);
//        paint.setStyle(Paint.Style.STROKE);
//        paint.setColor(Color.RED);
//
//
//        int stokeWidth = 4;
//        paint.setStrokeWidth(stokeWidth);
//        if (faces != null) {
//            for (Face face : faces) {
//                FaceRectangle faceRectangle = face.faceRectangle;
//                canvas.drawRect(
//                        faceRectangle.left,
//                        faceRectangle.top,
//                        faceRectangle.left + faceRectangle.width,
//                        faceRectangle.top + faceRectangle.height,
//                        paint);
//            }
//        }
        paint.setColor(Color.WHITE);
        paint.setStyle(Paint.Style.FILL);
        paint.setAntiAlias(true);
        paint.setTextSize(60);

        if (faces != null) {
            for (Face face : faces) {
                FaceRectangle faceRectangle = face.faceRectangle;
                FaceAttribute faceAttributes = face.faceAttributes;

                canvas.drawText("Age: "+faceAttributes.age, faceRectangle.left,
                        faceRectangle.top, paint);
            }
        }


        Vector <Path> facePaths = new Vector<>();

        if (faces != null) {
            for (Face face : faces) {

                paint.setColor(Color.WHITE);
                paint.setStyle(Paint.Style.FILL);
                paint.setAntiAlias(true);
                paint.setTextSize(60);

//                FaceRectangle faceRectangle = face.faceRectangle;
//                FaceAttribute faceAttributes = face.faceAttributes;
//                canvas.drawText("Gender: "+faceAttributes.gender, faceRectangle.left,
//                        faceRectangle.top+faceRectangle.height, paint);

                FaceCoordinates thisFaceCoordinate = new FaceCoordinates();

                thisFaceCoordinate.eyebrowLeftInnerX = face.faceLandmarks.eyebrowLeftInner.x;
                thisFaceCoordinate.eyebrowLeftInnerY = face.faceLandmarks.eyebrowLeftInner.y;
                thisFaceCoordinate.eyebrowRightInnerX = face.faceLandmarks.eyebrowRightInner.x;
                thisFaceCoordinate.eyebrowRightInnerY = face.faceLandmarks.eyebrowLeftInner.y;

                thisFaceCoordinate.eyebrowLeftOuterX = face.faceLandmarks.eyebrowLeftOuter.x;
                thisFaceCoordinate.eyebrowLeftOuterY = face.faceLandmarks.eyebrowLeftOuter.y;
                thisFaceCoordinate.eyebrowRightOuterX = face.faceLandmarks.eyebrowRightOuter.x;
                thisFaceCoordinate.eyebrowRightOuterY = face.faceLandmarks.eyebrowLeftOuter.y;


                thisFaceCoordinate.mouthLeftX = face.faceLandmarks.mouthLeft.x;
                thisFaceCoordinate.mouthLeftY = face.faceLandmarks.mouthLeft.y;
                thisFaceCoordinate.mouthRightX = face.faceLandmarks.mouthRight.x;
                thisFaceCoordinate.mouthRightY = face.faceLandmarks.mouthRight.y;
                thisFaceCoordinate.noseTipX = face.faceLandmarks.noseTip.x;
                thisFaceCoordinate.noseTipY = face.faceLandmarks.noseTip.y;

                thisFaceCoordinate.underLipBottomX = face.faceLandmarks.underLipBottom.x;
                thisFaceCoordinate.underLipBottomY = face.faceLandmarks.underLipBottom.y;

                // add facecoordinates, the index is face
                faceCoordinatesVector.add(thisFaceCoordinate);
                Path path = new Path();
                /*Chop out the face*/
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftOuterX ),
                        Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftOuterY));
                path.lineTo((Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftInnerX)+Float.parseFloat(""+thisFaceCoordinate.eyebrowRightInnerX))/2,
                        (Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftInnerY)+Float.parseFloat(""+thisFaceCoordinate.eyebrowRightInnerY))/2-
                                (Float.parseFloat(""+thisFaceCoordinate.noseTipY)-Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftInnerY))/2);
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.eyebrowRightOuterX ),
                        Float.parseFloat(""+thisFaceCoordinate.eyebrowRightOuterY));
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.mouthRightX ),
                        Float.parseFloat(""+thisFaceCoordinate.mouthRightY));
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.underLipBottomX ),
                        Float.parseFloat(""+thisFaceCoordinate.underLipBottomY));
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.mouthLeftX ),
                        Float.parseFloat(""+thisFaceCoordinate.mouthLeftY));
                path.lineTo(Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftOuterX ),
                        Float.parseFloat(""+thisFaceCoordinate.eyebrowLeftOuterY));
                facePaths.add(path);
            }
        }

        if (facePaths.size()>1) {
            Path FacePath1 = new Path();
            FacePath1 = facePaths.get(0); //TO_DO

            int one_to_two_x = (int) (faceCoordinatesVector.get(1).eyebrowLeftOuterX - faceCoordinatesVector.get(0).eyebrowLeftOuterX);
            int one_to_two_y = (int) (faceCoordinatesVector.get(1).eyebrowLeftOuterY - faceCoordinatesVector.get(0).eyebrowLeftOuterY);

            FacePath1.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            Bitmap cropped_bitmap_1 = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas cropped_canvas_1 = new Canvas(cropped_bitmap_1);
            paint.setColor(Color.BLACK);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
            paint.setAntiAlias(true);
            cropped_canvas_1.drawPath(FacePath1, paint);
            int[] all_old_pixels = new int[cropped_bitmap_1.getHeight() * cropped_bitmap_1.getWidth()];
            int[] all_background_pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
            cropped_bitmap_1.getPixels(all_old_pixels, 0, cropped_bitmap_1.getWidth(), 0, 0, cropped_bitmap_1.getWidth(), cropped_bitmap_1.getHeight());
            bitmap.getPixels(all_background_pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            for (int i = 0; i < all_old_pixels.length; i++) {
                int oldLocation = (int) (i + one_to_two_y * bitmap.getWidth() + one_to_two_x);
                if (all_old_pixels[i] == Color.BLACK) {
                    if (oldLocation < all_background_pixels.length && oldLocation >= 0) {
                        all_old_pixels[i] = all_background_pixels[oldLocation];
                    }
                }
            }
            cropped_bitmap_1.setPixels(all_old_pixels, 0, cropped_bitmap_1.getWidth(), 0, 0, cropped_bitmap_1.getWidth(), cropped_bitmap_1.getHeight());

            canvas.drawBitmap(cropped_bitmap_1, one_to_two_x, one_to_two_y, null);

            Path FacePath2 = new Path();
            FacePath2 = facePaths.get(1); //TO_DO

            FacePath2.setFillType(Path.FillType.INVERSE_EVEN_ODD);
            Bitmap cropped_bitmap_2 = originalBitmap.copy(Bitmap.Config.ARGB_8888, true);
            Canvas cropped_canvas_2 = new Canvas(cropped_bitmap_2);
            paint.setColor(Color.BLACK);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_OUT));
            paint.setAntiAlias(true);
            cropped_canvas_2.drawPath(FacePath2, paint);
            int[] all_old_pixels_2 = new int[cropped_bitmap_2.getHeight() * cropped_bitmap_2.getWidth()];
            int[] all_background_pixels_2 = new int[bitmap.getHeight() * bitmap.getWidth()];
            cropped_bitmap_2.getPixels(all_old_pixels_2, 0, cropped_bitmap_2.getWidth(), 0, 0, cropped_bitmap_2.getWidth(), cropped_bitmap_2.getHeight());
            bitmap.getPixels(all_background_pixels_2, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());
            for (int i = 0; i < all_old_pixels_2.length; i++) {
                int oldLocation = (int) (i - one_to_two_y * bitmap.getWidth() - one_to_two_x);
                if (all_old_pixels_2[i] == Color.BLACK) {
                    if (oldLocation < all_background_pixels_2.length && oldLocation >= 0) {
                        all_old_pixels_2[i] = all_background_pixels_2[oldLocation];
                    }
                }
            }
            cropped_bitmap_2.setPixels(all_old_pixels_2, 0, cropped_bitmap_2.getWidth(), 0, 0, cropped_bitmap_2.getWidth(), cropped_bitmap_2.getHeight());

            //twoFaceSwap(cropped_bitmap_1,cropped_bitmap_2,bitmap,0,1);


            canvas.drawBitmap(cropped_bitmap_2, -one_to_two_x, -one_to_two_y, null);
            // Now cropped_bitmap is a transparent version


            canvas.translate(0, 200);



        }


        return bitmap;
    }

    private static void twoFaceSwap (Bitmap face1, Bitmap face2, Bitmap background,int p1, int p2){
        //face1 coordinate
        // p1 -> person1 p2 -> person2
        FaceCoordinates face1C =  faceCoordinatesVector.get(p1);
        FaceCoordinates face2C =  faceCoordinatesVector.get(p2);
        // 1 move to 2
        double x1 = face2C.noseTipX - face1C.noseTipX;
        double y1 = face2C.noseTipY - face1C.noseTipY;
        //2 move to 1
        double x2 = face1C.noseTipX - face2C.noseTipX;
        double y2 = face1C.noseTipY - face2C.noseTipY;

        movePic(face1,background,x1,y1);
        movePic(face2,background,x2,y2);

    }

    /*Move the picture to (current location +x , curr loc +y)*/
    private static void movePic(Bitmap face, Bitmap background,double x, double y){
        // Converting from black to alpha
        int [] all_old_pixels = new int[face.getHeight()*face.getWidth()];
        int [] all_background_pixels = new int[background.getHeight()*background.getWidth()];
        face.getPixels(all_old_pixels, 0, face.getWidth(), 0, 0, face.getWidth(), face.getHeight());
        background.getPixels(all_background_pixels, 0,  background.getWidth(), 0, 0, background.getWidth(), background.getHeight());
        for (int i = 0; i < all_old_pixels.length; i++)
        {
            int  oldLocation = (int)(i+y*background.getWidth()+x);
            if(all_old_pixels[i] == Color.BLACK) {
                if (oldLocation < all_background_pixels.length) {
                    all_old_pixels[i] = all_background_pixels[oldLocation];
                }
            }
        }
        face.setPixels(all_old_pixels, 0, face.getWidth(), 0, 0, face.getWidth(), face.getHeight());


    }
}
