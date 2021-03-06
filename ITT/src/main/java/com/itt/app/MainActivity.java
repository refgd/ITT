package com.itt.app;
/*
    Author: Jack Conway
    Version: 0.1.1
 */

import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.GpsSatellite;
import android.location.GpsStatus;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
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
import java.util.List;


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
    private String satelliteInfo;
    private long sent;
    private int totalC;
    private int numSal;

    private String TAG = "socket thread";
    
    SocThread socketThread;

    Handler mhandler;
    Handler mhandlerSend;
    Handler mhandlerConn;
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
        totalC = 65;
        ctx = MainActivity.this;
        infoOut = (TextView) findViewById(R.id.infoTxt);
        infoSend = (TextView) findViewById(R.id.sendTxt);
        infoRecv = (TextView) findViewById(R.id.respTxt);
        infoLog = (TextView) findViewById(R.id.textView);
        autoSend = (Switch) findViewById(R.id.autoSend);

        Log.d("DEBUG","Start");

        locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
        TelephonyManager mngr = (TelephonyManager)getSystemService(Context.TELEPHONY_SERVICE);
        imei = mngr.getDeviceId();

        //boolean enabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);

        // getting GPS status
        boolean isGPSEnabled = locationManager
                .isProviderEnabled(LocationManager.GPS_PROVIDER);

        // getting network status
        boolean isNetworkEnabled = locationManager
                .isProviderEnabled(LocationManager.NETWORK_PROVIDER);

        // check if enabled and if not send user to the GSP settings
        // Better solution would be to display a dialog and suggesting to
        // go to the settings
        if (!isGPSEnabled && !isNetworkEnabled) {
            Intent intent = new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS);
            startActivity(intent);
        }

        if (isNetworkEnabled) {
            locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    100 * 1000, 500, locationListener);
            Log.d("Network", "Network");
        }

        // Define the criteria how to select the locatioin provider -> use
        // default
        Criteria criteria = new Criteria();
//        criteria.setAccuracy(Criteria.ACCURACY_FINE);
//        criteria.setAltitudeRequired(false);
//        criteria.setBearingRequired(false);
//        criteria.setCostAllowed(true);
//        criteria.setPowerRequirement(Criteria.POWER_LOW);

        provider = locationManager.getBestProvider(criteria, false);
        Location location = locationManager.getLastKnownLocation(provider);

        // Initialize the location fields
        if (location != null) {
            updateLocation(location);
        } else {
            infoOut.setText("Location not available");
        }

        //locationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 100 * 1000, 500, locationListener);

        // if GPS Enabled get lat/long using GPS Services
        /*
        if (isGPSEnabled) {
            //if (location == null) {
                locationManager.requestLocationUpdates(
                        LocationManager.GPS_PROVIDER,
                        100 * 1000,
                        500, locationListener);
                Log.d("GPS Enabled", "GPS Enabled");
            //}
        }
        */
        mhandlerConn = new Handler() {
            @Override
            public void handleMessage(Message msg) {
                try {
                    Log.i(TAG, "mhandlerCoon: msg=" + msg.what);
                    if (msg.obj != null) {
                        String s = msg.obj.toString();
                        if (s.trim().length() > 0) {
                            Log.i(TAG, "mhandler: obj=" + s);
                            infoLog.append("Server:" + s);
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
                        socketThread.close();
                        startSocket();
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
        Button btnopen = (Button) findViewById(R.id.btn_open);
        Button btnopen2 = (Button) findViewById(R.id.btn_open2);

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

        btnopen.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse("http://50.195.116.150/"));
                startActivity(intent);
            }
        });

        btnopen2.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(final View v) {
                Intent intent = new Intent(ctx, Web.class);
                startActivity(intent);
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

        locationManager.addGpsStatusListener(statusListener);

    }

    public void startSocket() {
        socketThread = new SocThread(mhandler, mhandlerSend, mhandlerConn, ctx);
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
        Double lati = ( (double)((int)(location.getLatitude()*1000000)) / 1000000 );
        Double loit = ( (double)((int)(location.getLongitude()*1000000)) / 1000000 );
        SimpleDateFormat s = new SimpleDateFormat("yyMMddHHmmss");
        Date ttt = new Date();
        String ms = s.format(ttt);

        Log.i("TIME", ms);
        String outData = imei+",AAA,31,"+lati+","+loit+","+ms+",A,10,11,0,217,1.1,37,36118,846208,310|260|7DA1|8B2B,0000,000A|0002||02D6|00FE,*A7\r\n";
        String pData = "$$" + Character.toString ((char) totalC) + outData.length() + "," + outData;
        infoSend.setText(pData);
        totalC++;
        if(totalC>122) totalC = 65;

        String str = "imei: "+imei+"\nLatitude: "+lati+"\nLongitude: "+loit+"\nDate: "+ms+"\nGPS Status"+satelliteInfo+"\n# of S: "+numSal+"\nCell signal: "+""+"\nspeed: "+"";
        infoOut.setText(str);

        long nnn = Calendar.getInstance().getTimeInMillis();
        if( (nnn-sent) > 10000){
            sent = Calendar.getInstance().getTimeInMillis();
            mMainHandler.sendEmptyMessageDelayed(REFRESH_PROGRESS, 10000);
        }
    }

    /**
     * Satellites Status Listener
     */
    private List<GpsSatellite> numSatelliteList = new ArrayList<GpsSatellite>(); // 卫星信号

    private final GpsStatus.Listener statusListener = new GpsStatus.Listener() {
        public void onGpsStatusChanged(int event) {
            LocationManager locationManager = (LocationManager) MainActivity.this.getSystemService(Context.LOCATION_SERVICE);
            GpsStatus status = locationManager.getGpsStatus(null);
            satelliteInfo = updateGpsStatus(event, status);
        }
    };

    private String updateGpsStatus(int event, GpsStatus status) {
        StringBuilder sb2 = new StringBuilder("");
        if (status == null) {
            numSal = 0;
        } else if (event == GpsStatus.GPS_EVENT_SATELLITE_STATUS) {
            int maxSatellites = status.getMaxSatellites();
            Iterator<GpsSatellite> it = status.getSatellites().iterator();
            numSatelliteList.clear();
            int count = 0;
            while (it.hasNext() && count <= maxSatellites) {
                GpsSatellite s = it.next();
                numSatelliteList.add(s);
                count++;
            }
            numSal = numSatelliteList.size();
        }

        return sb2.toString();
    }
}
