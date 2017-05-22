package co.karibe.testui;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

public class MainActivity extends AppCompatActivity {

    private BluetoothAdapter mBluetoothAdapter;
    private final static int REQUEST_ENABLE_BT = 1;
    private Handler mHandler;
    private Handler cHandler;
    private static final long SCAN_PERIOD = 5000;
    private BluetoothLeScanner mLEScanner;
    private ScanSettings settings;
    private List<ScanFilter> filters;
    private BluetoothGatt mGatt;
    private List<BluetoothGattService> services;
    private ArrayList<ArrayList<String>> items = new ArrayList<ArrayList<String>>();
    private ListView listView;
    private ScanResult result;
    private BluetoothDevice device;
    private MenuItem itm;
    private int mBattLevel;
    private int scancount;

    private static final String batt = "00002a19-0000-1000-8000-00805f9b34fb";
    private static final String rate = "00002a37-0000-1000-8000-00805f9b34fb";
    private static final String manu = "00002a29-0000-1000-8000-00805f9b34fb";
    private static final String name = "00002a00-0000-1000-8000-00805f9b34fb";
    private static final String bps = "00002a49-0000-1000-8000-00805f9b34fb";//BPS xtic
    private static final String battService = "0000180F-0000-1000-8000-00805f9b34fb";
    private static final String HRService = "0000180D-0000-1000-8000-00805f9b34fb";
    private static final String DevService = "0000180A-0000-1000-8000-00805f9b34fb";
    private static final String GAService = "00001800-0000-1000-8000-00805f9b34fb";
    private static final String BPSService = "00001810-0000-1000-8000-00805f9b34fb";
    private static final UUID GenAccess_Service_UUID = UUID.fromString(GAService);
    private static final UUID Battery_Service_UUID = UUID.fromString(battService);
    private static final UUID Battery_Level_UUID = UUID.fromString(batt);
    private static final UUID HeartRate_Service_UUID = UUID.fromString(HRService);
    private static final UUID Heart_rate_UUID = UUID.fromString(rate);
    private static final UUID DeviceInfo_Service_UUID = UUID.fromString(DevService);
    private static final UUID DeviceManufacturer_UUID = UUID.fromString(manu);
    private static final UUID DeviceName_UUID = UUID.fromString(name);
    private static final UUID BPS_Service_UUID = UUID.fromString(BPSService);
    private static final UUID Bps_UUID = UUID.fromString(bps);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //activate our Toolbar
        Toolbar toolbar = (Toolbar)findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        //initialize some objects

        scancount = 0;
        device = null;
        mHandler = new Handler();//handler for the BLE scan thread
        cHandler = new Handler();//handler for characteristics reading
        listView = (ListView) findViewById(R.id.orodha);
        CustomAdapter cus = new CustomAdapter(this,items);
        listView.setAdapter(cus);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id){
                Toast.makeText(MainActivity.this, "You Clicked at ".concat(Integer.toString(position)),
                        Toast.LENGTH_SHORT).show();
                //refresh the view
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        //items.clear();
                        connectToDevice(device);
                    }
                });
            }
        });

        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            Toast.makeText(MainActivity.this,
                    R.string.ble_not_supported, Toast.LENGTH_SHORT).show();
            finish();
        } else {
            final BluetoothManager bluetoothManager =
                    (BluetoothManager) getSystemService(MainActivity.this.BLUETOOTH_SERVICE);
            mBluetoothAdapter = bluetoothManager.getAdapter();
            if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
                Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
            }
            if (Build.VERSION.SDK_INT >= 21) {
                mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
                settings = new ScanSettings.Builder()
                        .setScanMode(ScanSettings.SCAN_MODE_LOW_POWER)
                        .build();
                filters = new ArrayList<>();//ScanFilter
            }
        }
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu, menu);
        itm = menu.findItem(R.id.mActions);
        itm.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                Log.d("mActionstitle",item.getTitle().toString());
                if (item.getTitle().toString().equalsIgnoreCase("SCAN")) {
                    scanLeDevice(true);
                    //Log.d("Scan", "Try to initiate scan");
                    item.setTitle("Stop");
                }else if(item.getTitle().toString().equalsIgnoreCase("STOP")){
                    scanLeDevice(false);
                    item.setTitle("SCAN");
                }else if(item.getTitle().toString().equalsIgnoreCase("DISCONNECT")){
                    mGatt.disconnect();
                    mGatt=null;
                    item.setTitle("CONNECT");
                    TextView txt = (TextView)findViewById(R.id.batt_level);
                    txt.setText("---");
                }else if(item.getTitle().toString().equalsIgnoreCase("CONNECT")){
                    //TODO: implement reconnecting to same device
                    device=mBluetoothAdapter.getRemoteDevice(result.getDevice().getAddress());
                    if(!(device==null)) {
                        Log.d("mActions","CONNECT>>device: ".concat(device.getName()).concat(", ").concat(device.getAddress()));
                        mGatt=null;
                        connectToDevice(device);
                    }else{
                        Log.w("CONNECT","No device available");
                        item.setTitle("SCAN");
                    }
                }
                return true;
            }
        });
        return true;
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()) {
            Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
        }
        if (Build.VERSION.SDK_INT >= 21) {
            mLEScanner = mBluetoothAdapter.getBluetoothLeScanner();
            settings = new ScanSettings.Builder()
                    .setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY)
                    .build();
            filters = new ArrayList<ScanFilter>();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mBluetoothAdapter != null && mBluetoothAdapter.isEnabled()) {
            scanLeDevice(false);
        }
    }

    @Override
    protected void onDestroy() {
        if (mGatt == null) {
            return;
        }
        mGatt.close();
        mGatt = null;
        super.onDestroy();
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {// it was a ble enable request
            if (resultCode == Activity.RESULT_CANCELED) {
                finish();//Bluetooth not enabled, quit app
                return;
            } else {
                Log.i("onActivityResult", "Bluetooth enabled");
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void scanLeDevice(final boolean enable) {
        if (enable) {
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    mLEScanner.stopScan(mScanCallback);
                }
            }, SCAN_PERIOD);
            mLEScanner.startScan(filters, settings, mScanCallback);
            scancount++;
            Log.i("scancount",Integer.toString(scancount));
        } else {
            mLEScanner.stopScan(mScanCallback);
        }
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult res) {
            result = res;
            BluetoothDevice btDevice = result.getDevice();
            device=btDevice;
            String s = btDevice.getName();
            String s2 = btDevice.getAddress();
            ArrayList<String> row = new ArrayList<String>();
            row.add(0,s);
            row.add(1,s2);
            //items.clear();
            if (!items.contains(row)) {
                items.add(row);
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    itm.setTitle("Scan");
                    ((BaseAdapter) listView.getAdapter()).notifyDataSetChanged();
                }
            });
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            for (ScanResult sr : results) {
                Log.d("ScanResult - Results", sr.toString());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.d("Scan Failed", "Error Code: " + errorCode);
        }
    };

    public void connectToDevice(BluetoothDevice device) {
        if (mGatt == null) {
            mGatt = device.connectGatt(this, false, gattCallback);
            scanLeDevice(false);// will stop after first device detection
        }else{
            Log.d("connectToDevice","mGatt not NULL");
        }
    }
    private final BluetoothGattCallback gattCallback = new BluetoothGattCallback() {
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            Log.i("onConnectionStateChange", "Status: " + status);
            switch (newState) {
                case BluetoothProfile.STATE_CONNECTED:
                    Log.d("gattCallback", "STATE_CONNECTED");
                    mGatt.discoverServices();
                    break;
                case BluetoothProfile.STATE_DISCONNECTED:
                    Log.d("gattCallback", "STATE_DISCONNECTED");
                    break;
                default:
                    Log.d("gattCallback", "STATE_OTHER");
                break;
            }

        }
        @Override
        public void onServicesDiscovered(final BluetoothGatt gatt, int status) {
            services = gatt.getServices();
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    BluetoothGattService DevInfoService = gatt.getService(DeviceInfo_Service_UUID);
                    if(DevInfoService==null){
                        Log.d("onServicesDiscovered","Device Information Service not available");
                    }else{
                        BluetoothGattCharacteristic DeviceManu = DevInfoService.getCharacteristic(DeviceManufacturer_UUID);
                        if(DeviceManu==null){
                            Log.d("onServicesDiscovered","Device Manufacturer not available");
                        }else{
                            Log.d("onServicesDiscovered","Device Manufacturer read: "+gatt.readCharacteristic(DeviceManu));
                        }
                    }
                }
            }, 2000);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    BluetoothGattService GenAccService = gatt.getService(GenAccess_Service_UUID);
                    if(GenAccService==null){
                        Log.d("onServicesDiscovered","Generic Access Service not available");
                    }else{
                        BluetoothGattCharacteristic DeviceName = GenAccService.getCharacteristic(DeviceName_UUID);
                        if(DeviceName==null){
                            Log.d("onServicesDiscovered","Device Name not available");
                        }else{
                            Log.d("onServicesDiscovered","Device Name read: "+gatt.readCharacteristic(DeviceName));
                        }
                    }
                }
            }, 2000);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    BluetoothGattService Battservice = gatt.getService(Battery_Service_UUID);
                    if(Battservice==null){
                        Log.d("onServicesDiscovered","Battery Service not available");
                    }else{
                        BluetoothGattCharacteristic BattLevel = Battservice.getCharacteristic(Battery_Level_UUID);
                        if (BattLevel==null){
                            Log.d("onServicesDiscovered","Battery Level not available");
                        }else{
                            Log.d("onServicesDiscovered","Battery level read: "+gatt.readCharacteristic(BattLevel));
                        }
                    }
                }
            }, 4000);
            new Timer().schedule(new TimerTask() {
                @Override
                public void run() {
                    BluetoothGattService HeartRateService = gatt.getService(HeartRate_Service_UUID);
                    if(HeartRateService==null){
                        Log.d("onServicesDiscovered","Heart rate Service not available");
                    }else{
                        BluetoothGattCharacteristic HeartRate = HeartRateService.getCharacteristic(Heart_rate_UUID);
                        if (HeartRate==null){
                            Log.d("onServicesDiscovered","Heart rate  not available");
                        }else{
                            Log.d("onServicesDiscovered","Heart rate read: "+gatt.readCharacteristic(HeartRate));
                        }
                    }
                }
            }, 2000);
            items.clear();
            int i=0;
            for(BluetoothGattService service: services) {
                if (service.getUuid().toString().equalsIgnoreCase(battService)) {
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, "Battery Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                } else if (service.getUuid().toString().equalsIgnoreCase(HRService)){
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, "Heart Rate Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                } else if (service.getUuid().toString().equalsIgnoreCase(DevService)) {
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, " Device Info Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                }else if (service.getUuid().toString().equalsIgnoreCase(GAService)) {
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, "Generic Access Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                }else if(service.getUuid().toString().equalsIgnoreCase(BPSService)){
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, "Blood Pressure Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                }else {
                    ArrayList<String> it = new ArrayList<String>();
                    it.add(0, " Other Service");
                    it.add(1, service.getUuid().toString());
                    items.add(i, it);
                    i++;
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    ((BaseAdapter)listView.getAdapter()).notifyDataSetChanged();
                    listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
                        @Override
                        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                            BluetoothGattCharacteristic xtic = services.get(position).getCharacteristics().get(0);
                            Log.i("Characteristics",xtic.getUuid().toString());
                            if(xtic.getUuid().toString().equalsIgnoreCase(DeviceName_UUID.toString())){
                                Log.d("onServicesDiscovered","Device name read: "+gatt.readCharacteristic(xtic));
                            }
                            if(xtic.getUuid().toString().equalsIgnoreCase(DeviceManufacturer_UUID.toString())){
                                Log.d("onServicesDiscovered","Device Manufacturer read: "+gatt.readCharacteristic(xtic));
                            }
                            if(xtic.getUuid().toString().equalsIgnoreCase(Heart_rate_UUID.toString())){
                                Log.d("onServicesDiscovered","Heart rate read: "+gatt.readCharacteristic(xtic));
                            }
                            if(xtic.getUuid().toString().equalsIgnoreCase(Battery_Level_UUID.toString())){
                                Log.d("onServicesDiscovered","Battery level read: "+gatt.readCharacteristic(xtic));
                            }
                        }
                    });
                    itm.setTitle("DISCONNECT");
                }
            });
        }
        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         BluetoothGattCharacteristic
                                                 characteristic, int status) {
            if(status==BluetoothGatt.GATT_SUCCESS) {
                if (characteristic.getUuid().toString().equalsIgnoreCase(Battery_Level_UUID.toString())) {
                    int level = characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8, 0);
                    mBattLevel = level;
                    Log.d("onCharacteristicRead", "Battery level: " +level+"%");
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            TextView txt = (TextView)findViewById(R.id.batt_level);
                            txt.setText(Integer.toString(mBattLevel).concat("%"));
                        }
                    });
                }else if(characteristic.getUuid().toString().equalsIgnoreCase(DeviceManufacturer_UUID.toString())){
                    Log.d("onCharacteristicRead", "Device Manufacturer: " + characteristic.getStringValue(0));
                }else if(characteristic.getUuid().toString().equalsIgnoreCase(Heart_rate_UUID.toString())){
                    Log.d("onCharacteristicRead", "Heart Rate: " + characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT8,0)+"Bpm");
                }else if (characteristic.getUuid().toString().equalsIgnoreCase(DeviceName_UUID.toString())){
                    Log.d("onCharacteristicRead", "Device Name: " + characteristic.getStringValue(0));
                }
            }
        }
        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt, final BluetoothGattCharacteristic characteristic) {
            Log.d("onCharacteristicChanged",characteristic.getIntValue(BluetoothGattCharacteristic.FORMAT_UINT32,0).toString());
        }
        @Override
        public void onDescriptorRead(BluetoothGatt gatt, BluetoothGattDescriptor descriptor, int status){
            if(status==BluetoothGatt.GATT_SUCCESS){
               Log.d("onDescriptorRead",descriptor.getValue().toString());
            }
        }
    };

    class CustomAdapter extends BaseAdapter {
        private LayoutInflater mInflater;
        private Activity activity;
        private ArrayList<ArrayList<String>> data;

        public CustomAdapter(Activity a, ArrayList<ArrayList<String>> arr) {
            activity=a;
            data=arr;
            mInflater = (LayoutInflater)MainActivity.this.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            return;
        }

        @Override
        public int getCount() {
            return data.size();
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            if (convertView == null) {
                convertView = mInflater.inflate(R.layout.list_view, parent,false);
            }
            TextView tv1 = (TextView)convertView.findViewById(R.id.left_text);
            TextView tv2 = (TextView)convertView.findViewById(R.id.right_text);
            tv1.setText(data.get(position).get(0));
            tv2.setText(data.get(position).get(1));
            return convertView;
        }

        @Override
        public Object getItem(int position) {
            return position;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }
    }
}