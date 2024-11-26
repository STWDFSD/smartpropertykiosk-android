package com.example.smartpropertykiosk;

import android.annotation.SuppressLint;
import android.content.Context;
import android.os.Build;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.RequiresApi;

import net.steamcrafted.materialiconlib.MaterialDrawableBuilder;
import net.steamcrafted.materialiconlib.MaterialIconView;

import org.json.JSONObject;

import java.util.ArrayList;

public class GuardListAdapter extends BaseAdapter {

    private final ArrayList<JSONObject> guardlist;
    private final Context context;

    public GuardListAdapter(ArrayList<JSONObject> guardlist, Context context) {
        this.guardlist = guardlist;
        this.context = context;
    }

    @Override
    public int getCount() {
        return guardlist.size();
    }

    @Override
    public Object getItem(int position) {
        return guardlist.get(position);
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @SuppressLint("ViewHolder")
    @RequiresApi(api = Build.VERSION_CODES.R)
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(context).inflate(R.layout.guard_list_item, parent, false);

        TextView label = convertView.findViewById(R.id.guard_menu_label);
        MaterialIconView icon = convertView.findViewById(R.id.guard_menu_icon);

        label.setText(guardlist.get(position).optString("label"));
        icon.setIcon(MaterialDrawableBuilder.IconValue.ACCOUNT_BOX);

        return convertView;
    }
}
