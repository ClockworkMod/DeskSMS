package com.koushikdutta.desktopsms;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.DialogInterface.OnClickListener;
import android.content.SharedPreferences;
import android.text.InputFilter;
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

public class BlackListActivity extends ListItem {
	private static final String LOGTAG = BlackListActivity.class.getSimpleName();
	List<String> numbersList;
	SharedPreferences blacklist;
	ListView listView;
	LinearLayout layout;
	private final ActivityBase context;
	
	public BlackListActivity(ActivityBase context, int title, int summary) {
    	super(context,title,summary);
		this.context = context;
    	blacklist = context.getApplicationContext().getSharedPreferences("blacklist", android.content.Context.MODE_PRIVATE);
    	listView = new ListView(context);
    	layout = new LinearLayout(context);
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
	 	layout = new LinearLayout(context);
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
		layout.removeAllViews();
        if(listViewAdapter  == null) {
        	listViewAdapter = new ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, numbersList);
        	listView = new ListView(context);
        	listView.setAdapter(listViewAdapter);
        	layout.addView(listView);
        	return;
        }
    	listViewAdapter.clear();
    	for (String number : numbersList) {
			listViewAdapter.add(number);
		}
    	listView = new ListView(context);
    	listView.setAdapter(listViewAdapter);
		layout.removeView(listView);
        layout.addView(listView);
        listViewAdapter.notifyDataSetChanged();
//		listView.setAdapter(listViewAdapter);
//		listView.refreshDrawableState();
//		layout.refreshDrawableState();
	}
	
	@Override
    public void onClick(View view) {
        super.onClick(view);
        TextView  blacklistTitle = new TextView(context);
        blacklistTitle.setText(R.string.DeskSMS_blacklist_title);
        layout.addView(blacklistTitle);
        updateNumbers();
        
        layout.setOrientation(LinearLayout.VERTICAL);
        
        listView.setOnItemClickListener(new OnItemClickListener() {

			@Override
			public void onItemClick(AdapterView<?> a, View v, int position, long id) {
				final int selectedNumber = position;
              	AlertDialog.Builder numberBuilder = new AlertDialog.Builder(context);
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
        final EditText numberToAdd = new EditText(context);
        numberToAdd.setTransformationMethod(new SingleLineTransformationMethod());
        numberToAdd.setMinWidth(250);
        numberToAdd.setFilters(new InputFilter[]{new InputFilter.LengthFilter(10)});
        LinearLayout buttonAndEditText = new LinearLayout(context);
        buttonAndEditText.addView(numberToAdd);
        buttonAndEditText.setOrientation(LinearLayout.HORIZONTAL);
        Button addToBlacklist = new Button(context);
        addToBlacklist.setText(R.string.add_to_blacklist);
        buttonAndEditText.addView(addToBlacklist);
        addToBlacklist.setOnClickListener(new View.OnClickListener() {

			@Override
			public void onClick(View v) {
				String numberAdded = numberToAdd.getText().toString();
				blacklist.edit().putBoolean(numberAdded,true).commit();
				updateNumbers();
				Log.i(LOGTAG,"Added "+numberAdded + " to blacklist.");
				
			}
        	
        });
        layout.addView(buttonAndEditText);
        AlertDialog.Builder builder = new AlertDialog.Builder(context);
        builder.setView(layout);
        builder.setInverseBackgroundForced(true);
        builder.create().show();
    }
}
