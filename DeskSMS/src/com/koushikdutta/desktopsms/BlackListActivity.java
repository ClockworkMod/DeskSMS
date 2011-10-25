package com.koushikdutta.desktopsms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.InputType;
import android.text.method.SingleLineTransformationMethod;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ListAdapter;
import android.widget.ListView;
import android.widget.TextView;

public class BlackListActivity extends Activity {
	private static final String LOGTAG = BlackListActivity.class.getSimpleName();
	List<String> numbersList;
	SharedPreferences blacklist;
	ListView listView;
	LinearLayout layout;
	
	@Override
	protected void onCreate(Bundle savedInstanceState)
	{
		super.onCreate(savedInstanceState);
    	blacklist = getApplicationContext().getSharedPreferences("blacklist", android.content.Context.MODE_PRIVATE);
    	listView = new ListView(this);
    	layout = new LinearLayout(this);
    	
    	TextView  blacklistTitle = new TextView(this);
        blacklistTitle.setText(R.string.DeskSMS_blacklist_title);
        updateNumbers();
        
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.addView(blacklistTitle,0);
        
        listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				final int selectedNumber = position;
              	AlertDialog.Builder numberBuilder = new AlertDialog.Builder(BlackListActivity.this);
              	numberBuilder.setTitle(R.string.blacklist_remove_number);
              	numberBuilder.setNegativeButton(android.R.string.cancel,null);
              	numberBuilder.setPositiveButton(android.R.string.ok, new OnClickListener() {
						@Override
						public void onClick(DialogInterface dialog, int which) {
							Log.i(LOGTAG,"selected number is:"+selectedNumber);
							blacklist.edit().putBoolean(numbersList.get(selectedNumber),false).commit();
							updateNumbers();
						}
              	});
              	numberBuilder.create().show();
				
			}});
        final EditText numberToAdd = new EditText(this);
//        numberToAdd.setTransformationMethod(SingleLineTransformationMethod.getInstance());
        numberToAdd.setInputType(InputType.TYPE_CLASS_NUMBER);
        numberToAdd.setMinWidth(250);
        numberToAdd.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        LinearLayout buttonAndEditText = new LinearLayout(this);
        buttonAndEditText.addView(numberToAdd);
        buttonAndEditText.setOrientation(LinearLayout.HORIZONTAL);
        Button addToBlacklist = new Button(this);
        addToBlacklist.setText(R.string.add_to_blacklist);
        buttonAndEditText.addView(addToBlacklist);
        addToBlacklist.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String numberAdded = numberToAdd.getText().toString();
				blacklist.edit().putBoolean(numberAdded,true).commit();
				numberToAdd.setText("");
				updateNumbers();
				Log.i(LOGTAG,"Added "+numberAdded + " to blacklist.");
				
			}
        	
        });
        buttonAndEditText.setMinimumHeight(100);
        layout.addView(buttonAndEditText,1);
        this.setContentView(layout);
	}
	
	private List<String> getNumbers() {
        Map<String,?> numbersMap = blacklist.getAll();
        List<String> numbersList = new ArrayList<String>();
        for(Entry<String,?> e : numbersMap.entrySet()) {
        	if((Boolean) e.getValue()) {
        		numbersList.add(e.getKey());
        	}
        }
        return numbersList;
	}
	
	private void updateNumbers() {
		numbersList = getNumbers();
	 	layout = new LinearLayout(this);
	 	ListAdapter listAdapter = listView.getAdapter();
	 	ArrayAdapter<String> listViewAdapter = null;
	 	if(listAdapter instanceof ArrayAdapter) {
	 		listViewAdapter = (ArrayAdapter<String>) listAdapter;
	 	}
	 	else {
	 		if(listAdapter != null)
	 			Log.i(LOGTAG, "Can't cast to ArrayAdapter");
	 		else
	 			Log.i(LOGTAG,"ListAdapter is null");
	 	}
	 	layout.removeView(listView);
        if(listViewAdapter  == null) {
        	listViewAdapter = new ArrayAdapter<String>(this, android.R.layout.simple_list_item_1, numbersList);
        	initListView(listViewAdapter);
        	return;
        }
    	listViewAdapter.clear();
    	for (String number : numbersList) {
			listViewAdapter.add(number);
		}
    	initListView(listViewAdapter);
        listViewAdapter.notifyDataSetChanged();
	}

	private void initListView(ArrayAdapter<String> listViewAdapter)
	{
		listView = new ListView(this);
		listView.setAdapter(listViewAdapter);
		layout.addView(listView);
	}
	
        
}