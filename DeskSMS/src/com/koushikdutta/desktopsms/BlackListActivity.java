package com.koushikdutta.desktopsms;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;

public class BlackListActivity extends Activity {
    private static final String LOGTAG = BlackListActivity.class.getSimpleName();
    SharedPreferences blacklist;
    ListView listView;
    ArrayAdapter<String> listViewAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.setContentView(R.layout.blacklist);
        
        setTitle(R.string.manage_blacklist);

        blacklist = getApplicationContext().getSharedPreferences("blacklist", android.content.Context.MODE_PRIVATE);
        listView = (ListView) findViewById(R.id.list);
        listViewAdapter = new ArrayAdapter<String>(this, 0) {
            @Override
            public View getView(int position, View convertView, ViewGroup parent) {
                if (convertView == null)
                    convertView = getLayoutInflater().inflate(R.layout.blacklist_item, null);
                ((TextView)convertView).setText(listViewAdapter.getItem(position));
                return convertView;
            }
        };

        listView.setAdapter(listViewAdapter);
        updateNumbers();

        listView.setOnItemClickListener(new OnItemClickListener() {

            @Override
            public void onItemClick(AdapterView<?> a, View v, int position, long id) {
                final String number = listViewAdapter.getItem(position);
                AlertDialog.Builder numberBuilder = new AlertDialog.Builder(BlackListActivity.this);
                numberBuilder.setTitle(getString(R.string.blacklist_remove_number, number));
                numberBuilder.setNegativeButton(android.R.string.cancel, null);
                numberBuilder.setPositiveButton(android.R.string.ok, new OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        blacklist.edit().remove(number).commit();
                        updateNumbers();
                    }
                });
                numberBuilder.create().show();

            }
        });
        final EditText numberToAdd = (EditText) findViewById(R.id.edit);
        Button addToBlacklist = (Button) findViewById(R.id.add);
        addToBlacklist.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                String numberAdded = numberToAdd.getText().toString();
                if (!"".equals(numberAdded))
                    blacklist.edit().putBoolean(numberAdded, true).commit();
                numberToAdd.setText("");
                updateNumbers();
                Log.i(LOGTAG, "Added " + numberAdded + " to blacklist.");
            }
        });
    }

    private void updateNumbers() {
        listViewAdapter.clear();
        for (String number : blacklist.getAll().keySet()) {
            listViewAdapter.add(number);
        }
        listViewAdapter.notifyDataSetChanged();
    }
}