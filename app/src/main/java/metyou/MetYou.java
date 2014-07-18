package metyou;

import android.app.Activity;
import android.net.wifi.p2p.nsd.WifiP2pDnsSdServiceRequest;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import java.util.ArrayList;

import acs.metyou.R;
import metyou.p2p.P2pServiceManager;


public class MetYou extends Activity {

    private boolean isWifiP2pEnabled;
    private ArrayList<String> buddies;
    private WifiP2pDnsSdServiceRequest serviceRequest;
    private ListView listView;
    private ArrayAdapter<String> arrayAdapter;
    private P2pServiceManager mP2pServiceManager;


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_share_contacts);

        mP2pServiceManager = new P2pServiceManager(this);
        buddies = new ArrayList<String>();
        arrayAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, buddies);
        listView = (ListView)findViewById(R.id.peer_list);
        listView.setAdapter(arrayAdapter);

    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.share_contacts, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        } else if (id == R.id.refresh) {
            mP2pServiceManager.discoverServices();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }


    public void setIsWifiP2pEnabled(boolean isWifiP2pEnabled) {
        this.isWifiP2pEnabled = isWifiP2pEnabled;
    }

    @Override
    public void onResume() {
        super.onResume();
        mP2pServiceManager.registerService();
        mP2pServiceManager.discoverServices();
    }


    @Override
    public void onPause() {
        super.onPause();
        mP2pServiceManager.unregisterReceiver();
    }

    public void addBuddy(String buddyname) {
        buddies.add(buddyname);
    }

    public void updateList() {
        arrayAdapter.notifyDataSetChanged();
    }
}