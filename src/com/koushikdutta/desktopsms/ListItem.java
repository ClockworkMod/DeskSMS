package com.koushikdutta.desktopsms;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.ImageView;
import android.widget.TextView;

public class ListItem {
    public String Title;
    public String Summary;
    public ActivityBase Context;
    public boolean Enabled = true;

    public int Icon;
    
    public void setEnabled(boolean enabled) {
        Enabled = enabled;
        Context.mAdapter.notifyDataSetChanged();
    }
    
    public void setSummary(int summary) {
        if (summary == 0)
            setSummary(null);
        else
            setSummary(Context.getString(summary));
    }
    
    public void setSummary(String summary) {
        Summary = summary;
        Context.mAdapter.notifyDataSetChanged();
    }
    
    public ListItem(ActivityBase context, int title, int summary) {
        if (title != 0)
            Title = context.getString(title);
        if (summary != 0)
            Summary = context.getString(summary);
        Context = context;
    }
    
    public ListItem(ActivityBase context, String title, String summary) {
        Title = title;
        Summary = summary;
        Context = context;
    }
    
    public ListItem(ActivityBase context, int title, int summary, int icon) {
        this(context, title, summary);
        Icon = icon;
    }
    
    public ListItem(ActivityBase context, String title, String summary, int icon) {
        this(context, title, summary);
        Icon = icon;
    }
    
    public boolean CheckboxVisible = false;
    boolean checked = false;
    public boolean getIsChecked() {
        return checked;
    }
    
    public void setIsChecked(boolean isChecked) {
        checked = isChecked;
        Context.mAdapter.notifyDataSetChanged();
    }
    
    public View getView(Context context, View convertView) {
        if (convertView == null)
            convertView = LayoutInflater.from(context).inflate(Context.getListItemResource(), null);
        
        
        TextView title = (TextView)convertView.findViewById(R.id.title);
        TextView summary = (TextView)convertView.findViewById(R.id.summary);
        CheckBox cb = (CheckBox)convertView.findViewById(R.id.checkbox);
        cb.setOnCheckedChangeListener(null);
        cb.setChecked(checked);
        cb.setOnCheckedChangeListener(new OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                checked = isChecked;
            }
        });
        cb.setVisibility(CheckboxVisible ? View.VISIBLE : View.GONE);
        cb.setChecked(checked);
        
        title.setEnabled(Enabled);
        summary.setEnabled(Enabled);

        
        title.setText(Title);
        if (Summary != null) {
            summary.setVisibility(View.VISIBLE);
            summary.setText(Summary);
        }
        else
            summary.setVisibility(View.GONE);
        
        ImageView iv = (ImageView)convertView.findViewById(R.id.image);
        if (iv != null) {
            if (Icon != 0) {
                iv.setVisibility(View.VISIBLE);
                iv.setImageResource(Icon);
            }
            else {
                iv.setVisibility(View.GONE);
            }
        }
        
        return convertView;
    }
    
    public void onClick(View view) {
        if (CheckboxVisible) {
            CheckBox cb = (CheckBox)view.findViewById(R.id.checkbox);
            cb.setChecked(!cb.isChecked());
        }
    }
    
    public boolean onLongClick() {
        return false;
    }
}
