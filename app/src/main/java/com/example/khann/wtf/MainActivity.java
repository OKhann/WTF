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

import java.util.List;
import java.lang.Object;


import com.o3dr.android.client.Drone;
import com.o3dr.android.client.apis.VehicleApi;
import com.o3dr.services.android.lib.drone.connection.ConnectionParameter;
import com.o3dr.services.android.lib.drone.connection.ConnectionType;
import com.o3dr.services.android.lib.drone.connection.ConnectionResult;
import com.o3dr.services.android.lib.model.SimpleCommandListener;
import com.o3dr.services.android.lib.drone.property.Altitude;
import com.o3dr.services.android.lib.drone.property.Gps;
import com.o3dr.services.android.lib.drone.property.Home;
import com.o3dr.services.android.lib.drone.property.Speed;
import com.o3dr.services.android.lib.drone.property.State;
import com.o3dr.services.android.lib.drone.property.Type;
import com.o3dr.services.android.lib.drone.property.VehicleMode;
import com.o3dr.services.android.lib.model.AbstractCommandListener;
import com.o3dr.android.client.apis.solo.SoloCameraApi;
import com.o3dr.android.client.interfaces.DroneListener;
import com.o3dr.android.client.interfaces.TowerListener;
import com.o3dr.services.android.lib.coordinate.LatLong;
import com.o3dr.services.android.lib.coordinate.LatLongAlt;
import com.o3dr.services.android.lib.drone.attribute.AttributeEvent;
import com.o3dr.services.android.lib.drone.attribute.AttributeType;
import com.o3dr.services.android.lib.drone.companion.solo.SoloAttributes;
import com.o3dr.services.android.lib.drone.companion.solo.SoloState;
import com.o3dr.android.client.apis.ControlApi;
import com.o3dr.android.client.ControlTower;

import static com.o3dr.services.android.lib.drone.property.VehicleMode.getVehicleModePerDroneType;


public class MainActivity extends AppCompatActivity implements TowerListener {

    //initialize variables
    Drone drone;
    int droneType = Type.TYPE_UNKNOWN;
    ControlTower controlTower;
    Handler handler = new Handler();

    //may not need below
    int DEFAULT_UDP_PORT = 14550;
    int DEFAULT_USB_BAUD_RATE = 57600;

    Spinner modeSelector;
    Button startVideoStream;
    Button stopVideoStream;

    String TAG = MainActivity.class.getSimpleName();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Context context = getApplicationContext();
        this.controlTower = new ControlTower(context);
        this.drone = new Drone(context);

        //need to create a button for spinner and then continue
        //this.modeSelector = (Spinner) findViewById(R.id.modeSelect);

        //need to fix layout with buttons
        //TextureView videoView = (TextureView) findViewById(R.id.video_content);

    }//end onCreate



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
        this.drone.registerDroneListener((DroneListener) this);
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

}//end class
