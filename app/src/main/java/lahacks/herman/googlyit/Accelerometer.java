package lahacks.herman.googlyit;

import android.app.Activity;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.ShapeDrawable;
import android.graphics.drawable.shapes.OvalShape;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.View;

import com.microsoft.projectoxford.face.*;
import com.microsoft.projectoxford.face.contract.*;

public class Accelerometer extends Activity implements SensorEventListener
{
    /** Called when the activity is first created. */
    CustomDrawableView mCustomDrawableView = null;
    ShapeDrawable mDrawable = new ShapeDrawable();
    public static int x;
    public static int y;

    private SensorManager sensorManager = null;

    public Bitmap getResizedBitmap(Bitmap bm, int newWidth, int newHeight) {
        int width = bm.getWidth();
        int height = bm.getHeight();
        float scaleWidth = ((float) newWidth) / width;
        float scaleHeight = ((float) newHeight) / height;
        // CREATE A MATRIX FOR THE MANIPULATION
        Matrix matrix = new Matrix();
        // RESIZE THE BIT MAP
        matrix.postScale(scaleWidth, scaleHeight);

        // "RECREATE" THE NEW BITMAP
        Bitmap resizedBitmap = Bitmap.createBitmap(
                bm, 0, 0, width, height, matrix, false);
        bm.recycle();
        return resizedBitmap;
    }

    @Override
    public void onCreate(Bundle savedInstanceState)
    {

        super.onCreate(savedInstanceState);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        mCustomDrawableView = new CustomDrawableView(this);
        setContentView(mCustomDrawableView);

    }

    // This method will update the UI on new sensor events
    public void onSensorChanged(SensorEvent sensorEvent)
    {
        {
            if (sensorEvent.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

                x = (int) Math.pow(sensorEvent.values[1], 2);
                y = (int) Math.pow(sensorEvent.values[2], 2);

            }
        }
    }

    public void onAccuracyChanged(Sensor arg0, int arg1) {}

    @Override
    protected void onResume()
    {
        super.onResume();
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER),
                SensorManager.SENSOR_DELAY_NORMAL);
        sensorManager.registerListener(this, sensorManager.getDefaultSensor(Sensor.TYPE_ORIENTATION),
                SensorManager.SENSOR_DELAY_NORMAL);
    }

    @Override
    protected void onStop()
    {
        // Unregister the listener
        sensorManager.unregisterListener(this);
        super.onStop();
    }

    public class CustomDrawableView extends View
    {
        static final int width = 50;
        static final int height = 50;

        Paint paint = new Paint();
        Bitmap pupil = BitmapFactory.decodeResource(getResources(),R.drawable.pupil);
        Bitmap m_pupil = pupil.copy(Bitmap.Config.ARGB_8888, true);

        public CustomDrawableView(Context context)
        {
            super(context);

            mDrawable = new ShapeDrawable(new OvalShape());
            mDrawable.getPaint().setColor(0xff74AC23);
            mDrawable.setBounds(x, y, x + width, y + height);
        }

        protected void onDraw(Canvas canvas)
        {
            if (Global.all != null) {
                for (Face face : Global.all) {
                    FaceLandmarks faceLandmarks = face.faceLandmarks;
                    m_pupil = getResizedBitmap(m_pupil,(int)(faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15),(int)(faceLandmarks.eyeLeftInner.x - faceLandmarks.eyeLeftOuter.x + 15));
                    canvas.drawBitmap(m_pupil,(float)faceLandmarks.eyeLeftOuter.x - 10,(float)faceLandmarks.eyeLeftTop.y - 10,paint);
                    m_pupil = getResizedBitmap(m_pupil,(int)(faceLandmarks.eyeRightOuter.x - faceLandmarks.eyeRightInner.x + 15),(int)(faceLandmarks.eyeRightOuter.x - faceLandmarks.eyeRightInner.x + 15));
                    canvas.drawBitmap(m_pupil,(float)faceLandmarks.eyeRightInner.x - 5,(float)faceLandmarks.eyeRightTop.y - 10,paint);
                }
            }
        }
    }
}