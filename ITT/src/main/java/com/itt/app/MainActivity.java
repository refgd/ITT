package com.itt.app;

import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Handler;
import android.os.Message;
import android.provider.Settings;
import android.support.v7.app.ActionBarActivity;
import android.os.Bundle;
import android.telephony.TelephonyManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import java.io.BufferedWriter;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.Iterator;


public class MainActivity extends ActionBarActivity {

    private static final int REFRESH_PROGRESS = 1;

    private LocationManager locationManager;
    private TextView infoOut;
    private TextView infoSend;
    private TextView infoRecv;
    private TextView infoLog;
    private Switch autoSend;

    private String provider;

    private String imei;
    private long sent;

    private String TAG = "socket thread";
    
    SocThread socketThread;

    Handler mhandler;
    Handler mhandlerSend;
    private Context ctx;

    private Handler mMainHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
                case REFRESH_PROGRESS:

                    Location location = locationManager.getLastKnownLocation(provider);
                    updateLocation(location);
                    if(autoSend.isChecked()) {
                        String str = infoSend.getText().toString();
                        socketThread.Send(str);
                    }
                    break;
                default:
                    break;
            }
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        ctx = MainActivity.this;
        infoOut = (TextView) findViewById(R.id.infoTxt);
        infoSend = (TextView) findViewById(R.id.sendTxt);
        infoRecv = (TextView) findViewById(R.id.respTxt);
        infoLog = (TextView) findViewById(R.id.textView);
        autoSend = (Switch) findViewById(R.id.autoSend);

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        TelephonyManager mngr = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        imei = mngr.getDeviceId();

        boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // check if enabled and if not send user to the GSP settings
        // Better solution would be to display a dialog and suggesting to
        // go to the settings
        if (!enabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        // Define the criteria how to select the locatioin provider -> use
        // default
        Criteria criteria = new Criteria();
        criteria.setAccuracy(Criteria.ACCURACY_FINE);
        criteria.setAltitudeRequired(false);
        criteria.setBearingRequired(false);
        criteria.setCostAllowed(true);
        criteria.setPowerRequirement(Criteria.POWER_LOW);

        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);

        // Initialize the location fields
        if (location != null) {
            updateLocation(location);



        } else {
            infoOut.setText("Location not available");
        }

        locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100 * 1000, 500, locationListener);

        mhandler = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.i(TAG, "mhandler: msg=" + msg.what);
                    if (msg.obj != null) {
                        String s = msg.obj.toString();
                        if (s.trim().length() > 0) {
                            Log.i(TAG, "mhandler: obj=" + s);
                            infoRecv.setText("Server:" + s);
                        } else {
                            Log.i(TAG, "no data");
                        }
                    }
                } catch (Exception ee) {
                    Log.i(TAG, "loading error");
                    ee.printStackTrace();
                }
            }
        };
        mhandlerSend = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.i(TAG, "mhandlerSend: msg.what=" + msg.what);
                    String s = msg.obj.toString();
                    if (msg.what == 1) {
                        Log.i(TAG, "ME: " + s + "\nsend success");
                        //infoLog.append("\nsend success");
                    } else {
                        Log.i(TAG, "ME: " + s + "\nsend fail");
                        infoLog.append("\nsend fail");
                    }
                } catch (Exception ee) {
                    Log.i(TAG, "loading error");
                    infoLog.append("\nloading error");
                    ee.printStackTrace();
                }
            }
        };
        startSocket();

        Button btnsend = (Button) findViewById(R.id.btn_send);

        btnsend.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(final View v) {
                //Log.i(TAG, "Sending data");
                Location location = locationManager.getLastKnownLocation(provider);
                updateLocation(location);
                
                String str = infoSend.getText().toString();

                socketThread.Send(str);

            }
        });

        autoSend.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if (isChecked) {
                    Location location = locationManager.getLastKnownLocation(provider);
                    if (location != null) {
                        updateLocation(location);
                    }
                } else {
                }
            }
        });

    }

    public void startSocket() {
        socketThread = new SocThread(mhandler, mhandlerSend, ctx);
        socketThread.start();
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private final LocationListener locationListener = new LocationListener() {

        public void onLocationChanged(Location location) {

            updateLocation(location);

        }

        public void onProviderDisabled(String provider){

            updateLocation(null);

            Log.i(TAG, "Provider now is disabled..");

        }

        public void onProviderEnabled(String provider){

            Log.i(TAG, "Provider now is enabled..");

        }

        public void onStatusChanged(String provider, int status,Bundle extras){ }

    };


    public void updateLocation(Location location) {
        Double lati = location.getLatitude();
        Double loit = location.getLongitude();
        SimpleDateFormat s = new SimpleDateFormat("yyMMddHHmmss");
        Date ttt = new Date();
        String ms = s.format(ttt);

        Log.i("TIME", ms);
        String outData = imei+",AAA,35,"+lati+","+loit+","+ms+",A,10,11,0,217,1.1,37,36118,846208,310|260|7DA1|8B2B,0000,000A|0002||02D6|00FE,*A7\r\n";
        String pData = "$$g" + outData.length() + "," + outData;
        infoSend.setText(pData);

        GpsStatus gpsStatus = locationManager.getGpsStatus(null);

        Iterable<GpsSatellite> iterable=gpsStatus.getSatellites();
        Iterator<GpsSatellite> itrator=iterable.iterator();
        ArrayList<GpsSatellite> satelliteList=new ArrayList<GpsSatellite>();
        int count=0;
        int maxSatellites=gpsStatus.getMaxSatellites();
        while (itrator.hasNext() && count <= maxSatellites) {
            GpsSatellite satellite = itrator.next();
            satelliteList.add(satellite);
            count++;
        }

        String str = "imei: "+imei+"\nLatitude: "+lati+"\nLongitude: "+loit+"\nDate: "+ms+"\n# of S: "+count+"\nCell signal: "+""+"\nspeed: "+"";
        infoOut.setText(str);

        long nnn = Calendar.getInstance().getTimeInMillis();
        if( (nnn-sent) > 10000){
            sent = Calendar.getInstance().getTimeInMillis();
            mMainHandler.sendEmptyMessageDelayed(REFRESH_PROGRESS, 10000);
        }
    }
}
