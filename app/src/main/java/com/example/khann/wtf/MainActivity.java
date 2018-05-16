package com.example.khann.wtf;

import android.content.Context;
import android.graphics.SurfaceTexture;
import android.os.Bundle;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.o3dr.android.client.ControlTower;
import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.android.client.apis.solo.SoloCameraApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.services.android.lib.model.SimpleCommandListener;

import java.util.List;


public class MainActivity extends AppCompatActivity implements TowerListener, DroneListener {

    private static final String TAG = MainActivity.class.getSimpleName();

    //initialize variables
    private Drone drone;
    private int droneType = Type.TYPE_UNKNOWN;
    private ControlTower controlTower;
    private final Handler handler = new Handler();

    //may not need below
    private static final int DEFAULT_UDP_PORT = 14550;
    private static final int DEFAULT_USB_BAUD_RATE = 57600;

    private Spinner modeSelector;
    private Button startVideoStream;
    private Button stopVideoStream;



    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        //need to create a button for spinner and then continue
        this.modeSelector = (Spinner) findViewById(R.id.modeSelect);
        this.modeSelector.setOnItemSelectedListener(new Spinner.OnItemSelectedListener(){
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id){
                onFlightModeSelected(view);
            }
            @Override
            public void onNothingSelected(AdapterView<?> parent){
                //do nothing
            }
        });

        //for Go Pro camera
        final Button takePic = (Button) findViewById(R.id.take_photo_button);
        takePic.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                takePhoto();
            }
        });

        //for Go Pro video recording
        final Button toggleVideo = (Button) findViewById(R.id.toggle_video_recording);
        toggleVideo.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                toggleVideoRecording();
            }
        });

        //need to fix layout with buttons
        final TextureView videoView = (TextureView) findViewById(R.id.video_content);
        videoView.setSurfaceTextureListener(new TextureView.SurfaceTextureListener() {
            @Override
            public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
                alertUser("Video display is available");
                startVideoStream.setEnabled(true);
            }

            @Override
            public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
                //anything???
            }

            @Override
            public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
                startVideoStream.setEnabled(false);
                return true;
            }

            @Override
            public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {
                //anything???
            }
        });

        //setting up the button to activate video streaming
        startVideoStream = (Button) findViewById(R.id.start_video_stream);
        startVideoStream.setEnabled(false);
        startVideoStream.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View view){
                alertUser("start video streaming");
                startVideoStream(new Surface(videoView.getSurfaceTexture()));
            }
        });

        //setting up the button to stop video streaming
        stopVideoStream = (Button) findViewById(R.id.stop_video_stream);
        stopVideoStream.setEnabled(false);
        stopVideoStream.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                alertUser("Stopping video streaming");
                stopVideoStream();
            }
        });

    }//end onCreate

    private void startVideoStream(Surface videoSurface) {
        SoloCameraApi.getApi(drone).startVideoStream(videoSurface, "", true, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                if(stopVideoStream != null)
                    stopVideoStream.setEnabled(true);

                if(startVideoStream != null)
                    startVideoStream.setEnabled(false);
            }

            @Override
            public void onError(int executionError) {
                alertUser("Error while starting the video stream" + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Timeout while starting the video stream");
            }
        });
    }

    private void stopVideoStream(){
        SoloCameraApi.getApi(drone).stopVideoStream(new SimpleCommandListener(){
            @Override
            public void onSuccess(){
                if(stopVideoStream != null)
                    stopVideoStream.setEnabled(false);

                if(startVideoStream != null)
                    startVideoStream.setEnabled(true);
            }
        });
    }

    private void toggleVideoRecording() {
        SoloCameraApi.getApi(drone).toggleVideoRecording(new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Video recording toggled");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Error while trying to toggle video recording..." + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Timeout while trying to toggle video recording");
            }
        });
    }

    private void takePhoto() {
        SoloCameraApi.getApi(drone).takePhoto(new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Photo taken...");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Error while trying to take the photo" + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Timeout whiel trying to take take the photo");
            }
        });
    }


    @Override
    public void onStart(){
        super.onStart();
        this.controlTower.connect(this);
        updateVehicleModesForType(this.droneType);
    }

    private void updateVehicleModesForType(int droneType) {
        List<VehicleMode> vehicleModes = VehicleMode.getVehicleModePerDroneType(droneType);
        ArrayAdapter<VehicleMode> vehicleModeArrayAdapter = new ArrayAdapter<VehicleMode>(this, android.R.layout.simple_spinner_item, vehicleModes);
        vehicleModeArrayAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        this.modeSelector.setAdapter(vehicleModeArrayAdapter);
    }

    @Override
    public void onStop(){
        super.onStop();
        if(this.drone.isConnected()){
            this.drone.disconnect();
            updateConnectedButton(false);
        }

        this.controlTower.unregisterDrone(this.drone);
        this.controlTower.disconnect();
    }

    private void updateConnectedButton(boolean isConnected) {
        Button connectButton = (Button) findViewById(R.id.btnConnect); //button
        if(isConnected){
            connectButton.setText("Disconnect");
        } else{
            connectButton.setText("Connect");
        }
    }

    @Override
    public void onTowerConnected(){
        alertUser("3DR Services Connected");
        this.controlTower.registerDrone(this.drone, this.handler);
        this.drone.registerDroneListener(this);
    }

    @Override
    public void onTowerDisconnected(){
        alertUser("3DR Service Interrupted");
    }

    //@Override
    public void onDroneEvent(String event, Bundle extras){

        switch (event){
            case AttributeEvent.STATE_CONNECTED:
                alertUser("Drone Connected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                checkSoloState();
                break;
            case AttributeEvent.STATE_DISCONNECTED:
                alertUser("Drone disconnected");
                updateConnectedButton(this.drone.isConnected());
                updateArmButton();
                break;
            case AttributeEvent.STATE_UPDATED:
            case AttributeEvent.STATE_ARMING:
                updateArmButton();
                break;
            case AttributeEvent.TYPE_UPDATED:
                Type newDroneType = this.drone.getAttribute(AttributeType.TYPE);
                if(newDroneType.getDroneType() != this.droneType){
                    this.droneType = newDroneType.getDroneType();
                    updateVehicleModesForType(this.droneType);
                }
                break;
            case AttributeEvent.STATE_VEHICLE_MODE:
                updateVehicleMode();
                break;
            case AttributeEvent.SPEED_UPDATED:
                updateSpeed();
                break;
            case AttributeEvent.ALTITUDE_UPDATED:
                updateAltitude();
                break;
            case AttributeEvent.HOME_UPDATED:
                updateDistanceFromHome();
                break;
                default:
                    Log.i("DRONE_EVENT", event); //
                    break;
        }
    }

    //need to fix connection issue
    public void onBtnConnectTap(View view){
        if(this.drone.isConnected()) {
            this.drone.disconnect();
        } else{
            Spinner connectionSelector = (Spinner) findViewById(R.id.selectConnectionType);//button
            int selectedConnectionType = connectionSelector.getSelectedItemPosition();

            /*
            Bundle extraParams = new Bundle();
            if(selectedConnectionType == ConnectionType.TYPE_USB){
                extraParams.putInt(ConnectionType.EXTRA_USB_BAUD_RATE, DEFAULT_USB_BAUD_RATE);
            } else{
                extraParams.putInt(ConnectionType.EXTRA_UDP_SERVER_PORT, DEFAULT_UDP_PORT);
            }

            ConnectionParameter connectionParameter = new ConnectionParameter(selectedConnectionType, extraParams, null);
            this.drone.connect(connectionParameter);*/

            ConnectionParameter connectionParameter = selectedConnectionType == ConnectionType.TYPE_USB
                    ? ConnectionParameter.newUsbConnection(null)
                    : ConnectionParameter.newUdpConnection(null);
            this.drone.connect(connectionParameter);
        }
    }//end onBtnConnect method

    public void onFlightModeSelected(View view){
        VehicleMode vehicleMode = (VehicleMode) this.modeSelector.getSelectedItem();

        VehicleApi.getApi(this.drone).setVehicleMode(vehicleMode, new AbstractCommandListener() {
            @Override
            public void onSuccess() {
                alertUser("Vehicle mode change successful");
            }

            @Override
            public void onError(int executionError) {
                alertUser("Vehicle mode change failed: " + executionError);
            }

            @Override
            public void onTimeout() {
                alertUser("Vehicle mode change timed out");
            }
        });
    }

    public void onArmButtonTap(View view){
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);

        if(vehicleState.isFlying()){
            //land
            VehicleApi.getApi(this.drone).setVehicleMode(VehicleMode.COPTER_LAND, new SimpleCommandListener() {
                @Override
                public void onError(int executionError){
                    alertUser("Unable to land the vehicle");
                }
                @Override
                public void onTimeout(){
                    alertUser("Unable to land the vehicle");
                }
            });
        } else if(vehicleState.isArmed()){
            //take off
            ControlApi.getApi(this.drone).takeoff(10, new AbstractCommandListener() {
                @Override
                public void onSuccess() {
                    alertUser("Taking off...");
                }

                @Override
                public void onError(int executionError) {
                    alertUser("Unable to take off...");
                }

                @Override
                public void onTimeout() {
                    alertUser("Unable to take off...");
                }
            });
        } else if(!vehicleState.isConnected()){
            //connect
            alertUser("Connect to a drone first");
        } else{
            //connected but not armed
            VehicleApi.getApi(this.drone).arm(true, false, new SimpleCommandListener() {
                @Override
                public void onError(int executionError){
                    alertUser("Unable to arm vehicle...");
                }
                @Override
                public void onTimeout(){
                    alertUser("Arming operation timed out");
                }
            });
        }
    }

    public void updateConnectedButton(Boolean isConnected){
        Button connectButton = (Button) findViewById(R.id.btnConnect);//button in layout
        if(isConnected){
            connectButton.setText("Disconnect");
        } else{
            connectButton.setText("Connect");
        }
    }







    //@Override
    public void onDroneConnectionFailed(com.google.android.gms.common.ConnectionResult result){
        alertUser("Connection Failed: " + result.getErrorMessage());
    }

    //@Override
    public void onDroneServiceInterrupted(String errorMsg){

    }

    private void updateDistanceFromHome() {
        TextView distanceTextView = (TextView) findViewById(R.id.distanceValueTextView); //button
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        double vehicleAltitude = droneAltitude.getAltitude();
        Gps droneGps = this.drone.getAttribute(AttributeType.GPS);
        LatLong vehiclePosition = droneGps.getPosition();

        double distanceFromeHome = 0;

        if(droneGps.isValid()){
            LatLongAlt vehicle3DPosition = new LatLongAlt(vehiclePosition.getLatitude(), vehiclePosition.getLongitude(), vehicleAltitude);
            Home droneHome = this.drone.getAttribute(AttributeType.HOME);
            distanceFromeHome = distanceBetweenPoints(droneHome.getCoordinate(), vehicle3DPosition);
        } else{
            distanceFromeHome = 0;
        }

        distanceTextView.setText(String.format("%3.1", distanceFromeHome) + "m");

    }

    private double distanceBetweenPoints(LatLongAlt pointA, LatLongAlt pointB) {
        if(pointA == null || pointB == null){
            return 0;
        }
        double dx = pointA.getLatitude() - pointB.getLatitude();//latitude diff
        double dy = pointA.getLongitude() - pointB.getLongitude();//longitude diff
        double dz = pointA.getAltitude() - pointB.getAltitude();//altitude diff
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    private void updateAltitude() {
        TextView altitudeTextView = (TextView) findViewById(R.id.altitudeValueTextView); //button
        Altitude droneAltitude = this.drone.getAttribute(AttributeType.ALTITUDE);
        altitudeTextView.setText(String.format("%3.1f", droneAltitude.getAltitude()) + "m");
    }

    private void updateSpeed() {
        TextView speedTextView = (TextView) findViewById(R.id.speedValueTextView); //button
        Speed droneSpeed = this.drone.getAttribute(AttributeType.SPEED);
        speedTextView.setText(String.format("%3.1f", droneSpeed.getGroundSpeed()) + "m/s");

    }

    private void updateVehicleMode() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        VehicleMode vehicleMode = vehicleState.getVehicleMode();
        ArrayAdapter arrayAdapter = (ArrayAdapter) this.modeSelector.getAdapter();
        this.modeSelector.setSelection(arrayAdapter.getPosition(vehicleMode));
    }

    private void checkSoloState() {
        SoloState soloState = drone.getAttribute(SoloAttributes.SOLO_STATE);
        if(soloState == null){
            alertUser("Unable to retrieve the solo state");
        } else{
            alertUser("Solo state is up to date");
        }
    }

    private void updateArmButton() {
        State vehicleState = this.drone.getAttribute(AttributeType.STATE);
        Button armButton = (Button) findViewById(R.id.btnArmTakeOff); //button

        if(!this.drone.isConnected()){
            armButton.setVisibility(View.INVISIBLE);
        } else{
            armButton.setVisibility(View.VISIBLE);
        }

        if(vehicleState.isFlying()){
            //land
            armButton.setText("Land");
        } else if(vehicleState.isArmed()){
            //take off
            armButton.setText("Take off");
        } else if(vehicleState.isConnected()){
            //connected but not armed
            armButton.setText("Arm");
        }
    }


    //Helper methods
    public void alertUser(String message) {
        Toast.makeText(getApplication(), message, Toast.LENGTH_SHORT).show();

    }

    @Override
    public void onPointerCaptureChanged(boolean hasCapture) {

    }
}//end class
