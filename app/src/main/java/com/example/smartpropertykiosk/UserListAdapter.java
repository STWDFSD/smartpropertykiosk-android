package com.example.smartpropertykiosk;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.util.Base64;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseExpandableListAdapter;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.SectionIndexer;
import android.widget.TextView;

//import com.amulyakhare.textdrawable.TextDrawable;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Set;

public class UserListAdapter extends BaseExpandableListAdapter implements SectionIndexer {

    Button callBtn;
    ArrayList<JSONObject> listGroup;

    HashMap<String, Integer> alphaIndexer;
    String[] sections;

    public UserListAdapter(ArrayList<JSONObject> listGroup) {
        this.listGroup = listGroup;

        alphaIndexer = new HashMap<>();
        int size = listGroup.size();

        for (int x = 0; x < size; x++) {
            String s = listGroup.get(x).optString("displayName");

            // get the first letter of the store
            String ch = s.length()>0 ? s.substring(0, 1) : "";
            // convert to uppercase otherwise lowercase a -z will be sorted
            // after upper A-Z
            ch = ch.toUpperCase();

            // put only if the key does not exist
            if (!alphaIndexer.containsKey(ch))
                alphaIndexer.put(ch, x);
        }

        Set<String> sectionLetters = alphaIndexer.keySet();

        // create a list from the set to sort
        ArrayList<String> sectionList = new ArrayList<>(
                sectionLetters);

        Collections.sort(sectionList);

        sections = new String[sectionList.size()];

        sectionList.toArray(sections);
    }

    @Override
    public int getGroupCount() {
        return listGroup.size();
    }

    @Override
    public int getChildrenCount(int groupPosition) {
        return 1;
    }

    @Override
    public Object getGroup(int groupPosition) {
        return listGroup.get(groupPosition);
    }

    @Override
    public Object getChild(int groupPosition, int childPosition) {
        return null;
    }

    @Override
    public long getGroupId(int groupPosition) {
        return 0;
    }

    @Override
    public long getChildId(int groupPosition, int childPosition) {
        return childPosition;
    }

    @Override
    public boolean hasStableIds() {
        return false;
    }

    @Override
    public View getGroupView(int groupPosition, boolean isExpanded, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.users_list_item, parent, false);
        ImageView img = convertView.findViewById(R.id.userImage);
        TextView nameText = convertView.findViewById(R.id.nameText);
        TextView codeText = convertView.findViewById(R.id.codeText);
        String userName = null;
        String userCode = null;
        String userImage = null;
        userName = listGroup.get(groupPosition).optString("displayName");
        userCode = listGroup.get(groupPosition).optString("dialCode");
        userImage = listGroup.get(groupPosition).optString("userImage");
        nameText.setText(userName);
        codeText.setText(userCode);
        if (userImage.length() > 0) {
            img.setImageBitmap(getCallerImage(userImage));
        }
//        else {
//            TextDrawable drawable = TextDrawable.builder()
//                    .beginConfig()
//                    .textColor(Color.BLUE)
//                    .bold()
//                    .toUpperCase()
//                    .fontSize(30)
//                    .endConfig()
//                    .buildRect(userName.length()>0 ? userName.substring(0, 1) : "", Color.parseColor("#DCDCDC"));
//            img.setImageDrawable(drawable);
//        }
        return convertView;
    }

    public Bitmap getCallerImage(String myImg) {
        if (myImg.length() > 0) {
            String base64Image = myImg.split(",")[1];
            byte[] decodedString = Base64.decode(base64Image, Base64.DEFAULT);
            return BitmapFactory.decodeByteArray(decodedString, 0, decodedString.length);
        } else {
//            return BitmapFactory.decodeResource(getApplicationContext().getResources(), R.mipmap.guard_avatar);
            return null;
        }
    }

    @Override
    public View getChildView(int groupPosition, int childPosition, boolean isLastChild, View convertView, ViewGroup parent) {
        convertView = LayoutInflater.from(parent.getContext()).inflate(R.layout.call_button, parent, false);
        callBtn = convertView.findViewById(R.id.callbtn);
        return convertView;
    }

    @Override
    public boolean isChildSelectable(int groupPosition, int childPosition) {
        return false;
    }

    @Override
    public Object[] getSections() {
        return sections;
    }

    @Override
    public int getPositionForSection(int sectionIndex) {
        return alphaIndexer.get(sections[sectionIndex]);
    }

    @Override
    public int getSectionForPosition(int position) {
        return 0;
    }
}
